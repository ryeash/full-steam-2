package com.fullsteam.controller;

import com.fullsteam.util.GameConstants;
import com.fullsteam.GameLobby;
import com.fullsteam.games.GameConfig;
import com.fullsteam.games.GameManager;
import com.fullsteam.model.BulletEffect;
import com.fullsteam.model.LobbyInfo;
import com.fullsteam.model.Ordinance;
import com.fullsteam.model.UtilityWeapon;
import com.fullsteam.model.WeaponAttribute;
import com.fullsteam.model.WeaponConfig;
import io.micronaut.context.annotation.Context;
import io.micronaut.core.io.ResourceResolver;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.http.server.types.files.StreamedFile;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.validation.Valid;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
                GameConstants.MAX_GLOBAL_PLAYERS,
                gameLobby.getActiveGames()
        );
    }

    @Get("/api/game-config/default")
    @Produces(MediaType.APPLICATION_JSON)
    public GameConfig getDefaultGameConfig() {
        return GameConfig.builder().build(); // Returns default values from @Builder.Default
    }

    @Post("/api/games")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Map<String, String> createGame(@Valid @Body GameConfig gameConfig) {
        try {
            GameManager game;
            if (gameConfig != null) {
                game = gameLobby.createGameWithConfig(gameConfig);
            } else {
                game = gameLobby.createGame();
            }
            return Map.of(
                    "gameId", game.getGameId(),
                    "status", "created"
            );
        } catch (IllegalStateException e) {
            throw new HttpStatusException(io.micronaut.http.HttpStatus.SERVICE_UNAVAILABLE,
                    "Failed to create game: " + e.getMessage());
        }
    }

    @Get("/api/weapon-customization")
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Object> getWeaponCustomizationData() {
        Map<String, Object> data = new HashMap<>();

        // Weapon attributes with min/max values
        Map<String, Map<String, Object>> attributes = new HashMap<>();
        for (WeaponAttribute attr : WeaponAttribute.values()) {
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

        // Basic ordinance showcases
        presets.put("ASSAULT_RIFLE", createPresetData(WeaponConfig.ASSAULT_RIFLE_PRESET));
        presets.put("HAND_CANNON", createPresetData(WeaponConfig.HAND_CANNON_PRESET));
        presets.put("SNIPER_RIFLE", createPresetData(WeaponConfig.SNIPER_RIFLE_PRESET));
        presets.put("PLASMA_RIFLE", createPresetData(WeaponConfig.PLASMA_RIFLE_PRESET));
        presets.put("TWIN_SIXES", createPresetData(WeaponConfig.TWIN_SIXES_PRESET));

        // New ordinance showcases
        presets.put("PRECISION_DART_GUN", createPresetData(WeaponConfig.PRECISION_DART_GUN_PRESET));
        presets.put("FLAME_PROJECTOR", createPresetData(WeaponConfig.FLAME_PROJECTOR_PRESET));

        // Explosive weapons (ordinance + effects)
        presets.put("EXPLOSIVE_SNIPER", createPresetData(WeaponConfig.EXPLOSIVE_SNIPER_PRESET));
        presets.put("ROCKET_LAUNCHER", createPresetData(WeaponConfig.ROCKET_LAUNCHER_PRESET));
        presets.put("GRENADE_LAUNCHER", createPresetData(WeaponConfig.GRENADE_LAUNCHER_PRESET));
        presets.put("CLUSTER_MORTAR", createPresetData(WeaponConfig.CLUSTER_MORTAR_PRESET));

        // Special effect showcases
        presets.put("BOUNCY_SMG", createPresetData(WeaponConfig.BOUNCY_SMG_PRESET));
        presets.put("PIERCING_RIFLE", createPresetData(WeaponConfig.PIERCING_RIFLE_PRESET));
        presets.put("INCENDIARY_SHOTGUN", createPresetData(WeaponConfig.INCENDIARY_SHOTGUN_PRESET));
        presets.put("SEEKER_DART", createPresetData(WeaponConfig.SEEKER_DART_PRESET));
        presets.put("ARC_PISTOL", createPresetData(WeaponConfig.ARC_PISTOL_PRESET));
        presets.put("TOXIC_SPRAYER", createPresetData(WeaponConfig.TOXIC_SPRAYER_PRESET));
        presets.put("ICE_CANNON", createPresetData(WeaponConfig.ICE_CANNON_PRESET));


        // Beam weapon presets
        presets.put("LASER_RIFLE", createPresetData(WeaponConfig.LASER_RIFLE_PRESET));
        presets.put("PLASMA_CANNON", createPresetData(WeaponConfig.PLASMA_CANNON_PRESET));
        presets.put("MEDIC_BEAM", createPresetData(WeaponConfig.MEDIC_BEAM_PRESET));
        presets.put("RAIL_CANNON", createPresetData(WeaponConfig.RAIL_CANNON_PRESET));

        // Advanced combination weapons
        presets.put("STORM_CALLER", createPresetData(WeaponConfig.STORM_CALLER_PRESET));
        presets.put("NAPALM_LAUNCHER", createPresetData(WeaponConfig.NAPALM_LAUNCHER_PRESET));
        presets.put("CRYO_SHOTGUN", createPresetData(WeaponConfig.CRYO_SHOTGUN_PRESET));
        presets.put("VENOM_NEEDLER", createPresetData(WeaponConfig.VENOM_NEEDLER_PRESET));
        presets.put("THUNDERBOLT_CANNON", createPresetData(WeaponConfig.THUNDERBOLT_CANNON_PRESET));
        presets.put("RICOCHET_RIFLE", createPresetData(WeaponConfig.RICOCHET_RIFLE_PRESET));
        presets.put("PLAGUE_MORTAR", createPresetData(WeaponConfig.PLAGUE_MORTAR_PRESET));
        presets.put("WILDFIRE_SPRAYER", createPresetData(WeaponConfig.WILDFIRE_SPRAYER_PRESET));
        presets.put("FROST_LANCE", createPresetData(WeaponConfig.FROST_LANCE_PRESET));
        presets.put("SHRAPNEL_CANNON", createPresetData(WeaponConfig.SHRAPNEL_CANNON_PRESET));
        presets.put("SEEKING_INFERNO", createPresetData(WeaponConfig.SEEKING_INFERNO_PRESET));
        presets.put("EMP_BURST_GUN", createPresetData(WeaponConfig.EMP_BURST_GUN_PRESET));
        presets.put("GLACIAL_MORTAR", createPresetData(WeaponConfig.GLACIAL_MORTAR_PRESET));
        presets.put("PHANTOM_NEEDLES", createPresetData(WeaponConfig.PHANTOM_NEEDLES_PRESET));
        presets.put("CORROSIVE_CANNON", createPresetData(WeaponConfig.CORROSIVE_CANNON_PRESET));

        data.put("presets", presets);

        // Point budget
        data.put("maxPoints", 100);

        data.put("utilityWeapons", Arrays.stream(UtilityWeapon.values())
                .map(utility -> {
                    Map<String, Object> utilityData = new HashMap<>();
                    utilityData.put("name", utility.name());
                    utilityData.put("displayName", utility.getDisplayName());
                    utilityData.put("description", utility.getDescription());
                    utilityData.put("category", utility.getCategory().getDisplayName());
                    utilityData.put("cooldown", utility.getCooldown());
                    utilityData.put("range", utility.getRange());
                    utilityData.put("damage", utility.getDamage());
                    utilityData.put("isFieldEffectBased", utility.isFieldEffectBased());
                    utilityData.put("isEntityBased", utility.isEntityBased());
                    return utilityData;
                })
                .collect(Collectors.toList()));
        return data;
    }

    private String formatDisplayName(String name) {
        return Arrays.stream(name.split("_"))
                .map(word -> word.charAt(0) + word.substring(1).toLowerCase())
                .collect(Collectors.joining(" "));
    }

    private Map<String, Object> createPresetData(WeaponConfig weapon) {
        Map<String, Object> preset = new HashMap<>();
        preset.put("displayName", weapon.getType());
        Map<String, Integer> attributes = Map.of(
                WeaponAttribute.DAMAGE.name(), weapon.getDamage(),
                WeaponAttribute.FIRE_RATE.name(), weapon.getFireRate(),
                WeaponAttribute.RANGE.name(), weapon.getRange(),
                WeaponAttribute.ACCURACY.name(), weapon.getAccuracy(),
                WeaponAttribute.MAGAZINE_SIZE.name(), weapon.getMagazineSize(),
                WeaponAttribute.RELOAD_TIME.name(), weapon.getReloadTime(),
                WeaponAttribute.PROJECTILE_SPEED.name(), weapon.getProjectileSpeed(),
                WeaponAttribute.BULLETS_PER_SHOT.name(), weapon.getBulletsPerShot(),
                WeaponAttribute.LINEAR_DAMPING.name(), weapon.getLinearDamping()
        );
        preset.put("attributes", attributes);
        preset.put("effects", weapon.getBulletEffects()
                .stream()
                .map(Enum::name)
                .collect(Collectors.toList()));
        preset.put("ordinance", weapon.getOrdinance().name());

        // Calculate total points
        int effectPoints = weapon.getBulletEffects().stream().mapToInt(BulletEffect::getPointCost).sum();
        int ordPoints = weapon.getOrdinance().getPointCost();
        preset.put("totalPoints", weapon.getAttributePoints() + effectPoints + ordPoints);

        return preset;
    }

    @Get(uris = {
            "/",
            "/lobby.html",
            "/game.html",
            "/config.html",
            "/js/{file}",
            "/js/spectator/{file}",
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