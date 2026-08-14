package com.fullsteam.games;

import com.fullsteam.model.AttributeModification;
import com.fullsteam.model.DamageHit;
import com.fullsteam.model.FieldEffect;
import com.fullsteam.model.FieldEffectType;
import com.fullsteam.model.VictoryCondition;
import com.fullsteam.physics.DefenseLaser;
import com.fullsteam.physics.Flag;
import com.fullsteam.physics.GameEntities;
import com.fullsteam.physics.Headquarters;
import com.fullsteam.physics.KothZone;
import com.fullsteam.physics.NetProjectile;
import com.fullsteam.physics.Oddball;
import com.fullsteam.physics.Player;
import com.fullsteam.physics.Projectile;
import com.fullsteam.physics.Turret;
import lombok.Setter;
import org.dyn4j.dynamics.Body;
import org.dyn4j.geometry.Circle;
import org.dyn4j.geometry.Convex;
import org.dyn4j.geometry.Polygon;
import org.dyn4j.geometry.Vector2;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;

/**
 * High-efficiency packed binary serializer for outbound GameState broadcasts.
 *
 * <p>Supports full game state, smoke-blinded state, and lobby preview state serialization.
 * Protocol FSB1 (Full Steam Binary v1).
 */
public class BinaryGameStateSerializer {

    private static final DecimalFormat DOUBLE_SHORTFORM = new DecimalFormat("#.##");

    private final GameConfig gameConfig;
    private final GameEntities gameEntities;
    private final RuleSystem ruleSystem;
    @Setter
    private GameManager gameManager;

    public BinaryGameStateSerializer(GameConfig gameConfig, GameEntities gameEntities, RuleSystem ruleSystem) {
        this.gameConfig = gameConfig;
        this.gameEntities = gameEntities;
        this.ruleSystem = ruleSystem;
    }

    public byte[] serializeGameState() {
        List<Player> players = gameEntities.getAllPlayers().stream()
                .filter(p -> !p.isVisionObscured())
                .toList();

        List<DamageHit> hits = (gameManager != null) ? gameManager.getAndClearDamageHits() : List.of();

        return serializeInternal(
                players,
                gameEntities.getAllProjectiles(),
                gameEntities.getAllFieldEffects().stream().filter(FieldEffect::isArmed).toList(),
                gameEntities.getAllTurrets(),
                gameEntities.getAllNetProjectiles(),
                gameEntities.getAllDefenseLasers(),
                gameConfig.getRules().hasKothZones() ? gameEntities.getAllKothZones() : List.of(),
                gameConfig.getRules().hasHeadquarters() ? gameEntities.getAllHeadquarters() : List.of(),
                gameConfig.getRules().hasFlags() ? gameEntities.getAllFlags() : List.of(),
                gameConfig.getRules().hasOddballNpcs() ? gameEntities.getAllOddballNpcs().stream().filter(Oddball::isActive).toList() : List.of(),
                hits,
                false, // visionObscured
                false  // awaitingSpawn
        );
    }

    public byte[] serializeBlindedGameState(Player blindedPlayer) {
        List<DamageHit> allHits = (gameManager != null) ? gameManager.getAndClearDamageHits() : List.of();
        List<DamageHit> playerHits = allHits.stream()
                .filter(h -> h.getAttackerId() == blindedPlayer.getId() || h.getVictimId() == blindedPlayer.getId())
                .toList();

        List<FieldEffect> smokeEffects = gameEntities.getAllFieldEffects().stream()
                .filter(e -> e.getType() == FieldEffectType.SMOKE && e.isArmed())
                .toList();

        return serializeInternal(
                List.of(blindedPlayer),
                List.of(), // projectiles stripped
                smokeEffects,
                List.of(), // turrets stripped
                List.of(), // nets stripped
                List.of(), // lasers stripped
                List.of(), // koth stripped
                List.of(), // hq stripped
                List.of(), // flags stripped
                List.of(), // oddballs stripped
                playerHits,
                true,  // visionObscured
                false  // awaitingSpawn
        );
    }

    public byte[] serializeLobbyGameState() {
        return serializeInternal(
                List.of(), // players stripped
                List.of(), // projectiles stripped
                List.of(), // fieldEffects stripped
                List.of(), // turrets stripped
                List.of(), // nets stripped
                List.of(), // lasers stripped
                gameConfig.getRules().hasKothZones() ? gameEntities.getAllKothZones() : List.of(),
                gameConfig.getRules().hasHeadquarters() ? gameEntities.getAllHeadquarters() : List.of(),
                gameConfig.getRules().hasFlags() ? gameEntities.getAllFlags() : List.of(),
                List.of(), // oddballs stripped
                List.of(), // hits stripped
                false, // visionObscured
                true   // awaitingSpawn
        );
    }

    private byte[] serializeInternal(
            Collection<Player> players,
            Collection<Projectile> projectiles,
            Collection<FieldEffect> fieldEffects,
            Collection<Turret> turrets,
            Collection<NetProjectile> nets,
            Collection<DefenseLaser> lasers,
            Collection<KothZone> kothZones,
            Collection<Headquarters> hqs,
            Collection<Flag> flags,
            Collection<Oddball> oddballs,
            List<DamageHit> hits,
            boolean visionObscured,
            boolean awaitingSpawn
    ) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(1024);
        DataOutputStream out = new DataOutputStream(baos);

        try {
            // Magic Header: 'F' 'S' 'B' '1' (Full Steam Binary v1)
            out.write('F');
            out.write('S');
            out.write('B');
            out.write(1);

            String stateStr = ruleSystem.getGameState().name();
            boolean isCountdown = ruleSystem.isCountdown();
            boolean isGameOver = ruleSystem.isGameOver();
            boolean gameTimed = ruleSystem.getRules().hasTimeLimit();

            // Header Flags (1 byte bitmask)
            int headerFlags = 0;
            if (visionObscured) {
                headerFlags |= 1;
            }
            if (awaitingSpawn) {
                headerFlags |= 2;
            }
            if (isCountdown) {
                headerFlags |= 4;
            }
            if (isGameOver) {
                headerFlags |= 8;
            }
            if (gameTimed) {
                headerFlags |= 16;
            }
            out.writeByte(headerFlags);

            // Game state enum code (1 byte)
            out.writeByte("COUNTDOWN".equals(stateStr) ? 2 : ("PLAYING".equals(stateStr) ? 1 : 0));

            // Timestamp (8 bytes)
            out.writeLong(System.currentTimeMillis());

            // Rule System Details
            long endTime = ruleSystem.getMatchStartTime() + (long) (ruleSystem.getRules().getTimeLimit() * 1000);
            out.writeFloat(((Number) Math.max(0, (endTime - System.currentTimeMillis()) / 1000)).floatValue());
            out.writeFloat(((Number) ruleSystem.getStartCountdownRemaining()).floatValue());
            out.writeByte(Optional.ofNullable(ruleSystem.getWinningTeam()).orElse(-1));
            out.writeShort(Optional.ofNullable(ruleSystem.getWinningPlayerId()).orElse(-1));

            // Score Style & Scoring Config
            writeString8(out, ruleSystem.getRules().getScoreStyle().name());
            String sortByStr = ruleSystem.getRules().getVictoryCondition() == VictoryCondition.ELIMINATION ? "placement" : "score";
            writeString8(out, sortByStr);

            List<String> components = ruleSystem.getRules().getActiveScoreComponents();
            out.writeByte(components.size());
            for (String comp : components) {
                writeString8(out, comp);
            }

            // Team Scores Map
            Map<Integer, Integer> teamScores = ruleSystem.calculateTeamScores();
            out.writeByte(teamScores.size());
            for (Map.Entry<Integer, Integer> entry : teamScores.entrySet()) {
                out.writeByte(entry.getKey());
                out.writeInt(entry.getValue());
            }

            // 1. Players
            out.writeShort(players.size());
            for (Player p : players) {
                out.writeShort(p.getId());
                out.writeByte(p.getTeam());

                int playerFlags = 0;
                if (p.isActive()) {
                    playerFlags |= 1;
                }
                if (p.isReloading()) {
                    playerFlags |= 2;
                }
                if (p.isEliminated()) {
                    playerFlags |= 4;
                }
                boolean respawnWaiting = gameConfig.getRules().usesLastStanding()
                        && !p.isActive()
                        && !p.isEliminated()
                        && p.getRespawnTime() > System.currentTimeMillis();
                if (respawnWaiting) {
                    playerFlags |= 8;
                }
                if (gameConfig.getRules().hasVip() && StatusEffectManager.isVip(p)) {
                    playerFlags |= 16;
                }
                out.writeByte(playerFlags);

                writeString8(out, p.getPlayerName());

                out.writeFloat((float) p.getPosition().x);
                out.writeFloat((float) p.getPosition().y);

                out.writeShort((short) Math.round(p.getBody().getLinearVelocity().x * 10.0));
                out.writeShort((short) Math.round(p.getBody().getLinearVelocity().y * 10.0));

                out.writeShort((short) Math.round((p.getRotation() % (2 * Math.PI)) * 1000.0));

                out.writeByte((int) Math.round(p.healthPercent() * 100.0));
                out.writeByte(p.getCurrentWeapon().getCurrentAmmo());
                out.writeByte(p.getCurrentWeapon().getMagazineSize());
                out.writeByte((int) Math.round(p.getReloadPercent() * 100.0));
                out.writeByte((int) Math.round(p.getUtilityCooldownProgress() * 100.0));
                out.writeShort((int) Math.round(p.getCurrentWeapon().getRange()));

                double respawnTime = Math.max(0, ((double) p.getRespawnTime() - System.currentTimeMillis()) / 1000.0);
                out.writeFloat((float) respawnTime);
                out.writeByte(p.getLivesRemaining());

                // Scoring (10 shorts)
                var scoring = p.getScoring();
                out.writeShort(scoring.getKills());
                out.writeShort(scoring.getDeaths());
                out.writeShort(scoring.getFlagCaptures());
                out.writeShort((int) Math.round(scoring.getKingOfTheHillPoints()));
                out.writeShort((int) Math.round(scoring.getOddball()));
                out.writeShort((int) Math.round(scoring.getHeadquarterDamage()));
                out.writeShort(scoring.getHeadquartersDestroyed());
                out.writeShort(scoring.getVipKills());
                out.writeShort(scoring.bonusPoints(gameConfig.getRules()));
                out.writeShort(scoring.total(gameConfig.getRules()));

                // Active PowerUps
                List<String> activePowerUps = new ArrayList<>();
                if (!visionObscured) {
                    for (AttributeModification mod : p.getAttributeModifications()) {
                        String hint = mod.renderHint();
                        if (hint != null && !hint.isEmpty()) {
                            activePowerUps.add(hint);
                        }
                    }
                }
                out.writeByte(activePowerUps.size());
                for (String hint : activePowerUps) {
                    writeString8(out, hint);
                }
            }

            // 2. Projectiles
            out.writeShort(projectiles.size());
            for (Projectile proj : projectiles) {
                out.writeInt(proj.getId());
                out.writeFloat((float) proj.getPosition().x);
                out.writeFloat((float) proj.getPosition().y);
                out.writeFloat((float) proj.getBody().getLinearVelocity().x);
                out.writeFloat((float) proj.getBody().getLinearVelocity().y);
                out.writeFloat((float) proj.getCaliber());

                int effectMask = 0;
                for (var effect : proj.getBulletEffects()) {
                    effectMask |= (1 << effect.ordinal());
                }
                out.writeShort(effectMask);
            }

            // 3. Field Effects
            out.writeShort(fieldEffects.size());
            for (FieldEffect fe : fieldEffects) {
                out.writeShort(fe.getId());
                out.writeByte(fe.getType().ordinal());
                out.writeByte(fe.getOwnerTeam());
                out.writeFloat((float) fe.getPosition().x);
                out.writeFloat((float) fe.getPosition().y);
                out.writeFloat((float) fe.getBody().getTransform().getRotation().toRadians());
                out.writeFloat((float) fe.getRadius());
                out.writeFloat((float) fe.getProgress());

                int feFlags = 0;
                if (fe.isActive()) {
                    feFlags |= 1;
                }
                if (fe.isArmed()) {
                    feFlags |= 2;
                }
                out.writeByte(feFlags);

                writeString16(out, verticesShorthand(fe.getBody()));
            }

            // 4. Turrets
            out.writeShort(turrets.size());
            for (Turret t : turrets) {
                out.writeShort(t.getId());
                out.writeByte(t.getOwnerTeam());
                out.writeFloat((float) t.getPosition().x);
                out.writeFloat((float) t.getPosition().y);
                out.writeFloat((float) t.getBody().getTransform().getRotation().toRadians());
                out.writeByte((int) Math.round(t.healthPercent() * 100.0));
                out.writeBoolean(t.isActive());
            }

            // 5. Nets
            out.writeShort(nets.size());
            for (NetProjectile net : nets) {
                out.writeShort(net.getId());
                out.writeFloat((float) net.getPosition().x);
                out.writeFloat((float) net.getPosition().y);
                out.writeFloat((float) net.getBody().getTransform().getRotation().toRadians());
                out.writeBoolean(net.isActive());
            }

            // 6. Defense Lasers
            out.writeShort(lasers.size());
            for (DefenseLaser l : lasers) {
                out.writeShort(l.getId());
                out.writeByte(l.getOwnerTeam());
                out.writeFloat((float) l.getPosition().x);
                out.writeFloat((float) l.getPosition().y);
                out.writeFloat((float) l.getCurrentRotation());
                out.writeBoolean(l.isActive());
            }

            // 7. KOTH Zones
            out.writeShort(kothZones.size());
            for (KothZone z : kothZones) {
                out.writeShort(z.getId());
                out.writeByte(z.getZoneNumber());
                out.writeFloat((float) z.getPosition().x);
                out.writeFloat((float) z.getPosition().y);
                out.writeFloat((float) z.getRadius());
                out.writeByte(z.getControllingTeam());
                out.writeByte(z.getState().ordinal());
                out.writeByte(z.getTotalPlayerCount());
            }

            // 8. Headquarters
            out.writeShort(hqs.size());
            for (Headquarters hq : hqs) {
                out.writeShort(hq.getId());
                out.writeByte(hq.getOwnerTeam());
                out.writeFloat((float) hq.getPosition().x);
                out.writeFloat((float) hq.getPosition().y);
                out.writeByte((int) Math.round(hq.healthPercent() * 100.0));
                out.writeBoolean(!hq.isActive());
                writeString16(out, verticesShorthand(hq.getBody()));
            }

            // 9. Flags
            out.writeShort(flags.size());
            for (Flag f : flags) {
                out.writeShort(f.getId());
                out.writeByte(f.getOwnerTeam());
                out.writeFloat((float) f.getPosition().x);
                out.writeFloat((float) f.getPosition().y);
                out.writeShort(f.getCarriedByPlayerId());
                out.writeBoolean(f.isAtHome());
            }

            // 10. Oddball NPCs
            out.writeShort(oddballs.size());
            for (Oddball ob : oddballs) {
                out.writeShort(ob.getId());
                out.writeByte(ob.getPersonality().ordinal());
                out.writeFloat((float) ob.getPosition().x);
                out.writeFloat((float) ob.getPosition().y);
                out.writeFloat((float) ob.getVelocity().x);
                out.writeFloat((float) ob.getVelocity().y);
                out.writeFloat((float) ob.getRadius());
                out.writeByte((int) Math.round(ob.healthPercent() * 100.0));
            }

            // 11. Hits
            out.writeShort(hits.size());
            for (DamageHit hit : hits) {
                out.writeFloat((float) hit.getX());
                out.writeFloat((float) hit.getY());
                out.writeFloat((float) hit.getDamage());
                out.writeShort(hit.getAttackerId());
                out.writeShort(hit.getVictimId());
                int hitFlags = 0;
                if (hit.isKill()) {
                    hitFlags |= 1;
                }
                out.writeByte(hitFlags);
            }

            out.flush();
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Binary serialization failed", e);
        }
    }

    private static void writeString8(DataOutputStream out, String s) throws IOException {
        if (s == null || s.isEmpty()) {
            out.writeByte(0);
            return;
        }
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        int len = Math.min(255, bytes.length);
        out.writeByte(len);
        out.write(bytes, 0, len);
    }

    private static void writeString16(DataOutputStream out, String s) throws IOException {
        if (s == null || s.isEmpty()) {
            out.writeShort(0);
            return;
        }
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        int len = Math.min(65535, bytes.length);
        out.writeShort(len);
        out.write(bytes, 0, len);
    }

    private String verticesShorthand(Body body) {
        if (body.getFixtureCount() == 0) {
            return "";
        }
        StringJoiner outer = new StringJoiner(";");
        for (int i = 0; i < body.getFixtureCount(); i++) {
            Convex convex = body.getFixture(i).getShape();
            StringJoiner joiner = new StringJoiner("/");
            if (convex instanceof Polygon polygon) {
                Vector2[] polyVertices = polygon.getVertices();
                for (Vector2 vertex : polyVertices) {
                    joiner.add("(" + DOUBLE_SHORTFORM.format(vertex.x) +
                            "," + DOUBLE_SHORTFORM.format(vertex.y) + ")");
                }
            } else if (convex instanceof Circle circle) {
                double radius = circle.getRadius();
                Vector2 center = circle.getCenter();
                joiner.add("(" + DOUBLE_SHORTFORM.format(center.x) +
                        "," + DOUBLE_SHORTFORM.format(center.y) +
                        "," + DOUBLE_SHORTFORM.format(radius) + ")");
            }
            outer.add(joiner.toString());
        }
        return outer.toString();
    }
}
