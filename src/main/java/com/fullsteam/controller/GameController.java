package com.fullsteam.controller;

import com.fullsteam.Config;
import com.fullsteam.GameLobby;
import com.fullsteam.model.BulletEffect;
import com.fullsteam.model.LobbyInfo;
import com.fullsteam.model.Ordinance;
import com.fullsteam.model.Weapon;
import io.micronaut.context.annotation.Context;
import io.micronaut.core.io.ResourceResolver;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.server.types.files.StreamedFile;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;

@Singleton
@Controller
public class GameController {

    private static final Logger log = LoggerFactory.getLogger(GameController.class);

    private final GameLobby gameLobby;
    private final ResourceResolver resourceResolver;

    @Inject
    public GameController(GameLobby gameLobby, ResourceResolver resourceResolver) {
        this.gameLobby = gameLobby;
        this.resourceResolver = resourceResolver;
    }

    @Get("/api/games")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public LobbyInfo getGames() {
        return new LobbyInfo(
                gameLobby.getGlobalPlayerCount(),
                Config.MAX_GLOBAL_PLAYERS,
                gameLobby.getGameTypes(),
                gameLobby.getActiveGames()
        );
    }

    @Get("/api/weapon-customization")
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Object> getWeaponCustomizationData() {
        Map<String, Object> data = new HashMap<>();
        
        // Weapon attributes with min/max values
        Map<String, Map<String, Object>> attributes = new HashMap<>();
        for (Weapon.WeaponAttribute attr : Weapon.WeaponAttribute.values()) {
            Map<String, Object> attrData = new HashMap<>();
            attrData.put("min", attr.getMin());
            attrData.put("max", attr.getMax());
            attrData.put("name", attr.name());
            attrData.put("displayName", formatDisplayName(attr.name()));
            attributes.put(attr.name(), attrData);
        }
        data.put("attributes", attributes);
        
        // Bullet effects with costs and descriptions
        List<Map<String, Object>> effects = Arrays.stream(BulletEffect.values())
            .map(effect -> {
                Map<String, Object> effectData = new HashMap<>();
                effectData.put("name", effect.name());
                effectData.put("displayName", formatDisplayName(effect.name()));
                effectData.put("cost", effect.getPointCost());
                effectData.put("description", effect.getDescription());
                return effectData;
            })
            .collect(Collectors.toList());
        data.put("effects", effects);
        
        // Ordinance types with costs and properties
        List<Map<String, Object>> ordinances = Arrays.stream(Ordinance.values())
            .map(ord -> {
                Map<String, Object> ordData = new HashMap<>();
                ordData.put("name", ord.name());
                ordData.put("displayName", formatDisplayName(ord.name()));
                ordData.put("cost", ord.getPointCost());
                ordData.put("description", ord.getDescription());
                ordData.put("size", ord.getSize());
                ordData.put("speedMultiplier", ord.getSpeedMultiplier());
                ordData.put("hasTrail", ord.hasTrail());
                return ordData;
            })
            .collect(Collectors.toList());
        data.put("ordinances", ordinances);
        
        // Preset weapons
        Map<String, Map<String, Object>> presets = new HashMap<>();
        
        // Assault Rifle preset
        presets.put("ASSAULT_RIFLE", createPresetData("Assault Rifle", 
            Weapon.ASSAULT_RIFLE_PRESET, new HashSet<>(), Ordinance.BULLET));
        
        // Hand Cannon preset
        presets.put("HAND_CANNON", createPresetData("Hand Cannon", 
            Weapon.HAND_CANNON_PRESET, new HashSet<>(), Ordinance.BULLET));
        
        // Explosive Sniper preset
        presets.put("EXPLOSIVE_SNIPER", createPresetData("Explosive Sniper", 
            Weapon.EXPLOSIVE_SNIPER_PRESET, Set.of(BulletEffect.EXPLODES_ON_IMPACT), Ordinance.BULLET));
        
        // Rocket Launcher preset
        presets.put("ROCKET_LAUNCHER", createPresetData("Rocket Launcher", 
            Weapon.ROCKET_LAUNCHER_PRESET, Set.of(BulletEffect.EXPLODES_ON_IMPACT), Ordinance.ROCKET));
        
        // Plasma Rifle preset
        presets.put("PLASMA_RIFLE", createPresetData("Plasma Rifle", 
            Weapon.PLASMA_RIFLE_PRESET, Set.of(BulletEffect.PIERCING), Ordinance.PLASMA));
        
        // Grenade Launcher preset
        presets.put("GRENADE_LAUNCHER", createPresetData("Grenade Launcher", 
            Weapon.GRENADE_LAUNCHER_PRESET, Set.of(BulletEffect.FRAGMENTING), Ordinance.GRENADE));
        
        data.put("presets", presets);
        
        // Point budget
        data.put("maxPoints", 100);
        
        return data;
    }
    
    private String formatDisplayName(String name) {
        return Arrays.stream(name.split("_"))
            .map(word -> word.charAt(0) + word.substring(1).toLowerCase())
            .collect(Collectors.joining(" "));
    }
    
    private Map<String, Object> createPresetData(String displayName, 
                                               Map<Weapon.WeaponAttribute, Integer> attributes,
                                               Set<BulletEffect> effects,
                                               Ordinance ordinance) {
        Map<String, Object> preset = new HashMap<>();
        preset.put("displayName", displayName);
        preset.put("attributes", attributes.entrySet().stream()
            .collect(Collectors.toMap(
                entry -> entry.getKey().name(),
                Map.Entry::getValue
            )));
        preset.put("effects", effects.stream()
            .map(Enum::name)
            .collect(Collectors.toList()));
        preset.put("ordinance", ordinance.name());
        
        // Calculate total points
        int attrPoints = attributes.values().stream().mapToInt(Integer::intValue).sum();
        int effectPoints = effects.stream().mapToInt(BulletEffect::getPointCost).sum();
        int ordPoints = ordinance.getPointCost();
        preset.put("totalPoints", attrPoints + effectPoints + ordPoints);
        
        return preset;
    }

    @Get(uris = {
            "/",
            "/lobby.html",
            "/game.html",
            "/config.html",
            "/color-palette.js",
            "/game-engine.js",
            "/unified.css",
            "/favicon.ico",
            "/robots.txt"
    }, produces = MediaType.ALL)
    public HttpResponse<StreamedFile> staticFiles(@Context HttpRequest<?> request) {
        String path = request.getPath();
        if (path.equals("/")) {
            path = "lobby.html";
        }
        while (path.startsWith("/")) {
            path = path.substring(1);
        }
        String extension = StringUtils.substringAfter(path, '.');
        MediaType type = MediaType.forExtension(extension)
                .orElse(MediaType.TEXT_HTML_TYPE);

        return serveStaticFile(path, type);
    }

    private HttpResponse<StreamedFile> serveStaticFile(String path, MediaType contentType) {
        try {
            Optional<URL> resource = resourceResolver.getResource("classpath:" + path);
            if (resource.isPresent()) {
                InputStream inputStream = resource.get().openStream();
                return HttpResponse.ok(new StreamedFile(inputStream, contentType));
            } else {
                log.warn("Resource not found: {}", path);
                return HttpResponse.notFound();
            }
        } catch (Exception e) {
            log.error("Error serving static file: {}", path, e);
            return HttpResponse.serverError();
        }
    }
}