package com.fullsteam.controller;

import com.fullsteam.Config;
import com.fullsteam.GameLobby;
import com.fullsteam.RandomNames;
import com.fullsteam.games.GameConfig;
import com.fullsteam.games.GameManager;
import com.fullsteam.model.BulletEffect;
import com.fullsteam.model.LobbyInfo;
import com.fullsteam.model.Ordinance;
import com.fullsteam.model.UtilityWeapon;
import com.fullsteam.model.Weapon;
import com.fullsteam.model.WeaponAttribute;
import com.fullsteam.model.WeaponConfig;
import io.micronaut.context.annotation.Context;
import io.micronaut.core.io.ResourceResolver;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.http.server.types.files.StreamedFile;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.validation.Valid;
import org.apache.commons.lang3.StringUtils;
import org.dyn4j.Epsilon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.net.URL;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Singleton
@Controller(produces = MediaType.APPLICATION_JSON, consumes = MediaType.APPLICATION_JSON)
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
    public LobbyInfo getGames() {
        return new LobbyInfo(
                gameLobby.getGlobalPlayerCount(),
                Config.MAX_GLOBAL_PLAYERS,
                gameLobby.getActiveGames()
        );
    }

    @Get("/api/names")
    public List<String> getNames() {
        return RandomNames.getNames();
    }

    @Get("/api/game-config/default")
    public GameConfig getDefaultGameConfig() {
        return GameConfig.builder().build();
    }

    @Post("/api/games")
    public Map<String, String> createGame(@Valid @Body GameConfig gameConfig) {
        try {
            GameManager game = gameConfig != null
                    ? gameLobby.createGameWithConfig(gameConfig)
                    : gameLobby.createGame();
            return Map.of(
                    "gameId", game.getGameId(),
                    "status", "created"
            );
        } catch (IllegalStateException e) {
            throw new HttpStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Failed to create game: " + e.getMessage());
        }
    }

    @Get("/api/weapon-customization")
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
            // Lets the customizer disable projectile-only attributes (KNOCKBACK)
            // when a beam ordnance is selected.
            attrData.put("validForBeams", attr.appliesToBeams());
            attributes.put(attr.name(), attrData);
        }
        data.put("attributes", attributes);

        // Bullet effects with costs and descriptions (utility-only effects like
        // STRIKE are not selectable, so they're excluded from the customizer).
        List<Map<String, Object>> effects = Arrays.stream(BulletEffect.values())
                .filter(BulletEffect::isSelectable)
                .map(effect -> {
                    Map<String, Object> effectData = new HashMap<>();
                    effectData.put("name", effect.name());
                    effectData.put("displayName", formatDisplayName(effect.name()));
                    effectData.put("cost", effect.getPointCost());
                    effectData.put("description", effect.getDescription());
                    // Lets the customizer disable flight-only effects (HOMING, etc.)
                    // when a beam ordnance is selected.
                    effectData.put("validForBeams", effect.isValidForBeams());
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
                    ordData.put("speedMultiplier", ord.getSpeedMultiplier());
                    ordData.put("beam", ord.isBeamType());
                    return ordData;
                })
                .collect(Collectors.toList());
        data.put("ordinances", ordinances);

        // Preset weapons
        Map<String, Map<String, Object>> presets = new HashMap<>();

        // basics
        presets.put("ASSAULT_RIFLE", createPresetData(WeaponConfig.ASSAULT_RIFLE_PRESET));
        presets.put("HAND_CANNON", createPresetData(WeaponConfig.HAND_CANNON_PRESET));
        presets.put("SNIPER_RIFLE", createPresetData(WeaponConfig.SNIPER_RIFLE_PRESET));
        presets.put("TWIN_SIXES", createPresetData(WeaponConfig.TWIN_SIXES_PRESET));
        presets.put("MINIGUN", createPresetData(WeaponConfig.MINIGUN_PRESET));
        presets.put("SHOTGUN", createPresetData(WeaponConfig.SHOTGUN_PRESET));
        presets.put("CONCUSSION_CANNON", createPresetData(WeaponConfig.CONCUSSION_CANNON_PRESET));

        // Explosive weapons (ordinance + effects)
        presets.put("ROCKET_LAUNCHER", createPresetData(WeaponConfig.ROCKET_LAUNCHER_PRESET));
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
        presets.put("RAILGUN", createPresetData(WeaponConfig.RAILGUN_PRESET));
        presets.put("RICOCHET_LASER", createPresetData(WeaponConfig.RICOCHET_LASER_PRESET));

        // Advanced combination weapons
        presets.put("STORM_CALLER", createPresetData(WeaponConfig.STORM_CALLER_PRESET));
        presets.put("NAPALM_LAUNCHER", createPresetData(WeaponConfig.NAPALM_LAUNCHER_PRESET));
        presets.put("VENOM_NEEDLER", createPresetData(WeaponConfig.VENOM_NEEDLER_PRESET));
        presets.put("FROST_LANCE", createPresetData(WeaponConfig.FROST_LANCE_PRESET));
        presets.put("SHRAPNEL_CANNON", createPresetData(WeaponConfig.SHRAPNEL_CANNON_PRESET));
        presets.put("PHANTOM_NEEDLES", createPresetData(WeaponConfig.PHANTOM_NEEDLES_PRESET));

        data.put("presets", presets);

        // Point budget
        data.put("maxPoints", 100);

        data.put("utilityWeapons", Arrays.stream(UtilityWeapon.values())
                .sorted(Comparator.comparing(String::valueOf))
                .map(utility -> {
                    Map<String, Object> utilityData = new HashMap<>();
                    utilityData.put("name", utility.name());
                    utilityData.put("displayName", utility.getDisplayName());
                    utilityData.put("description", utility.getDescription());
                    utilityData.put("category", utility.getCategory().getDisplayName());
                    utilityData.put("cooldown", utility.getCooldown());
                    utilityData.put("range", utility.getRange());
                    utilityData.put("damage", utility.getDamage());
                    return utilityData;
                })
                .collect(Collectors.toList()));
        return data;
    }

    /**
     * Resolve a customization config into the full set of end-result stats the
     * weapon will actually have — final values, the un-coupled base, each
     * coupling's signed point contribution, the point budget, and derived combat
     * stats. The client renders this instead of recomputing weapon math, so all
     * attribute/coupling logic lives server-side (one source of truth).
     */
    @Post("/api/weapon-customization/resolve")
    public HttpResponse<Map<String, Object>> resolveCustomization(@Body WeaponConfig config) {
        Map<WeaponAttribute, Integer> allocated = new EnumMap<>(WeaponAttribute.class);
        allocated.put(WeaponAttribute.DAMAGE, config.damage);
        allocated.put(WeaponAttribute.FIRE_RATE, config.fireRate);
        allocated.put(WeaponAttribute.RANGE, config.range);
        allocated.put(WeaponAttribute.ACCURACY, config.accuracy);
        allocated.put(WeaponAttribute.MAGAZINE_SIZE, config.magazineSize);
        allocated.put(WeaponAttribute.RELOAD_TIME, config.reloadTime);
        allocated.put(WeaponAttribute.PROJECTILE_SPEED, config.projectileSpeed);
        allocated.put(WeaponAttribute.BULLETS_PER_SHOT, config.bulletsPerShot);
        allocated.put(WeaponAttribute.LINEAR_DAMPING, config.linearDamping);
        allocated.put(WeaponAttribute.HANDLING, config.handling);
        allocated.put(WeaponAttribute.CALIBER, config.caliber);
        allocated.put(WeaponAttribute.KNOCKBACK, config.knockback);

        WeaponAttribute.Resolution res;
        try {
            res = WeaponAttribute.resolveDetailed(allocated);
        } catch (IllegalArgumentException e) {
            return HttpResponse.badRequest(Map.of("error", e.getMessage()));
        }

        Ordinance ordinance = config.ordinance != null ? config.ordinance : Ordinance.PROJECTILE;

        Map<String, Object> attributes = new HashMap<>();
        for (WeaponAttribute a : WeaponAttribute.values()) {
            double value = res.values().get(a);
            double base = res.baseValues().get(a);
            Map<String, Object> attr = new HashMap<>();
            attr.put("label", formatDisplayName(a.name()));
            attr.put("points", allocated.get(a));
            attr.put("value", value);
            attr.put("baseValue", base);
            attr.put("display", formatStat(a, value, ordinance));
            attr.put("baseDisplay", formatStat(a, base, ordinance));
            // Coupled if the final value differs from the un-coupled base — works
            // uniformly for point-space and stat-space couplings.
            attr.put("coupled", Math.abs(value - base) > Epsilon.E);
            attributes.put(a.name(), attr);
        }

        List<Map<String, Object>> couplings = res.appliedCouplings().stream().map(c -> {
            Map<String, Object> m = new HashMap<>();
            m.put("source", c.source().name());
            m.put("target", c.target().name());
            m.put("label", formatDisplayName(c.source().name()) + " → " + formatDisplayName(c.target().name()));
            m.put("delta", c.delta());
            m.put("space", c.space().name());
            m.put("display", formatCouplingDelta(c));
            return m;
        }).collect(Collectors.toList());

        int attrPoints = config.getAttributePoints();
        int effectPoints = config.getBulletEffects().stream().mapToInt(BulletEffect::getPointCost).sum();
        int ordinancePoints = ordinance.getPointCost();
        int total = attrPoints + effectPoints + ordinancePoints;
        Map<String, Object> budget = new HashMap<>();
        budget.put("attributePoints", attrPoints);
        budget.put("effectPoints", effectPoints);
        budget.put("ordinancePoints", ordinancePoints);
        budget.put("total", total);
        budget.put("max", 100);
        budget.put("remaining", 100 - total);

        double damage = res.values().get(WeaponAttribute.DAMAGE);
        int bullets = (int) Math.round(res.values().get(WeaponAttribute.BULLETS_PER_SHOT));
        double fireRate = res.values().get(WeaponAttribute.FIRE_RATE);
        double dpb = Weapon.damagePerBullet(damage, bullets);
        Map<String, Object> derived = new HashMap<>();
        derived.put("damagePerBullet", dpb);
        derived.put("burstDamage", dpb * bullets);
        derived.put("dps", dpb * bullets * fireRate);
        derived.put("effectiveProjectileSpeed", res.values().get(WeaponAttribute.PROJECTILE_SPEED) * ordinance.getSpeedMultiplier());
        derived.put("moveSpeedMultiplier", res.values().get(WeaponAttribute.HANDLING));

        Map<String, Object> out = new HashMap<>();
        out.put("valid", total <= 100);
        out.put("budget", budget);
        out.put("attributes", attributes);
        out.put("couplings", couplings);
        out.put("derived", derived);
        return HttpResponse.ok(out);
    }

    /**
     * Human-readable signed delta for one coupling, in its space's units.
     */
    private String formatCouplingDelta(WeaponAttribute.AppliedCoupling c) {
        if (c.space() == WeaponAttribute.CouplingSpace.STAT) {
            // STAT deltas are in the target's own units; only RELOAD_TIME uses STAT today (seconds).
            String unit = c.target() == WeaponAttribute.RELOAD_TIME ? "s" : "";
            return String.format("%+.2f%s", c.delta(), unit);
        }
        return String.format("%+.1f pts", c.delta());
    }

    /**
     * Human-readable end-result string for a resolved attribute value.
     */
    private String formatStat(WeaponAttribute a, double v, Ordinance ordinance) {
        return switch (a) {
            case ACCURACY -> Math.round(v * 100) + "%";
            case HANDLING -> Math.round(v * 100) + "% move speed";
            case FIRE_RATE -> String.format("%.1f shots/s", v);
            case RELOAD_TIME -> String.format("%.2f s", v);
            case LINEAR_DAMPING -> String.format("%.2f drag", v);
            case RANGE -> Math.round(v) + " units";
            case PROJECTILE_SPEED -> Math.round(v * ordinance.getSpeedMultiplier()) + " units/s";
            case MAGAZINE_SIZE -> (int) Math.round(v) + " rounds";
            case BULLETS_PER_SHOT -> (int) Math.round(v) + (Math.round(v) == 1 ? " bullet" : " bullets");
            case DAMAGE -> String.valueOf((int) Math.round(v));
            case CALIBER -> String.format("×%.2f size", v);
            case KNOCKBACK -> v <= 0 ? "none" : String.format("%.0fk impulse", v / 1000.0);
        };
    }

    private String formatDisplayName(String name) {
        return Arrays.stream(name.split("_"))
                .map(word -> word.charAt(0) + word.substring(1).toLowerCase())
                .collect(Collectors.joining(" "));
    }

    private Map<String, Object> createPresetData(WeaponConfig weapon) {
        Map<String, Object> preset = new HashMap<>();
        preset.put("displayName", weapon.getType());
        // Map.of caps at 10 pairs; the attribute count exceeds it, so build explicitly.
        Map<String, Integer> attributes = new HashMap<>();
        attributes.put(WeaponAttribute.DAMAGE.name(), weapon.getDamage());
        attributes.put(WeaponAttribute.FIRE_RATE.name(), weapon.getFireRate());
        attributes.put(WeaponAttribute.RANGE.name(), weapon.getRange());
        attributes.put(WeaponAttribute.ACCURACY.name(), weapon.getAccuracy());
        attributes.put(WeaponAttribute.MAGAZINE_SIZE.name(), weapon.getMagazineSize());
        attributes.put(WeaponAttribute.RELOAD_TIME.name(), weapon.getReloadTime());
        attributes.put(WeaponAttribute.PROJECTILE_SPEED.name(), weapon.getProjectileSpeed());
        attributes.put(WeaponAttribute.BULLETS_PER_SHOT.name(), weapon.getBulletsPerShot());
        attributes.put(WeaponAttribute.LINEAR_DAMPING.name(), weapon.getLinearDamping());
        attributes.put(WeaponAttribute.HANDLING.name(), weapon.getHandling());
        attributes.put(WeaponAttribute.CALIBER.name(), weapon.getCaliber());
        attributes.put(WeaponAttribute.KNOCKBACK.name(), weapon.getKnockback());
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