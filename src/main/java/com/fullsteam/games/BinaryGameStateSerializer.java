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
import com.fullsteam.physics.Zombie;
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
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * High-efficiency packed binary serializer for outbound GameState broadcasts.
 *
 * <p>Supports full game state, smoke-blinded state, and lobby preview state serialization.
 * Protocol FSB1 (Full Steam Binary v1).
 */
public class BinaryGameStateSerializer {

    private final GameConfig gameConfig;
    private final GameEntities gameEntities;
    private final RuleSystem ruleSystem;
    @Setter
    private GameManager gameManager;

    private long tickCount = 0;
    private volatile boolean forceLowFreq = false;

    // Reusable output stream and data writer to avoid per-tick buffer and wrapper allocations
    private final ByteArrayOutputStream baos = new ByteArrayOutputStream(4096);
    private final DataOutputStream out = new DataOutputStream(baos);

    public BinaryGameStateSerializer(GameConfig gameConfig, GameEntities gameEntities, RuleSystem ruleSystem) {
        this.gameConfig = gameConfig;
        this.gameEntities = gameEntities;
        this.ruleSystem = ruleSystem;
    }

    public void triggerLowFreqSync() {
        this.forceLowFreq = true;
    }

    public byte[] serializeGameState() {
        return serializeGameState(false);
    }

    public synchronized byte[] serializeGameState(boolean forceLowFreqSync) {
        boolean hasLowFreq = (tickCount++ % 24 == 0) || forceLowFreq || forceLowFreqSync;
        if (hasLowFreq) {
            this.forceLowFreq = false;
        }

        List<DamageHit> hits = gameEntities.getAndClearDamageHits();

        return serializeInternal(
                gameEntities.getAllPlayers(),
                true, // filter out vision-obscured players
                gameEntities.getAllProjectiles(),
                gameEntities.getAllFieldEffects(),
                true,  // armed field effects only
                false, // all types
                gameEntities.getAllTurrets(),
                gameEntities.getAllNetProjectiles(),
                gameEntities.getAllDefenseLasers(),
                gameConfig.getRules().hasKothZones() ? gameEntities.getAllKothZones() : List.of(),
                gameConfig.getRules().hasHeadquarters() ? gameEntities.getAllHeadquarters() : List.of(),
                gameConfig.getRules().hasFlags() ? gameEntities.getAllFlags() : List.of(),
                gameConfig.getRules().hasOddballNpcs() ? gameEntities.getAllOddballNpcs() : List.of(),
                gameConfig.getRules().hasZombies() ? gameEntities.getAllZombies() : List.of(),
                hits,
                null,
                false, // visionObscured
                false, // awaitingSpawn
                hasLowFreq
        );
    }

    public synchronized byte[] serializeBlindedGameState(Player blindedPlayer) {
        List<DamageHit> allHits = gameEntities.getAndClearDamageHits();

        return serializeInternal(
                List.of(blindedPlayer),
                false,
                List.of(), // projectiles stripped
                gameEntities.getAllFieldEffects(),
                true, // armed only
                true, // smoke only
                List.of(), // turrets stripped
                List.of(), // nets stripped
                List.of(), // lasers stripped
                List.of(), // koth stripped
                List.of(), // hq stripped
                List.of(), // flags stripped
                List.of(), // oddballs stripped
                List.of(), // zombies stripped
                allHits,
                blindedPlayer.getId(),
                true,  // visionObscured
                false, // awaitingSpawn
                true   // hasLowFreq (blinded players always get full low-freq player info)
        );
    }

    public synchronized byte[] serializeLobbyGameState() {
        return serializeInternal(
                List.of(), // players stripped
                false,
                List.of(), // projectiles stripped
                List.of(), // fieldEffects stripped
                false,
                false,
                List.of(), // turrets stripped
                List.of(), // nets stripped
                List.of(), // lasers stripped
                gameConfig.getRules().hasKothZones() ? gameEntities.getAllKothZones() : List.of(),
                gameConfig.getRules().hasHeadquarters() ? gameEntities.getAllHeadquarters() : List.of(),
                gameConfig.getRules().hasFlags() ? gameEntities.getAllFlags() : List.of(),
                List.of(), // oddballs stripped
                List.of(), // zombies stripped
                List.of(), // hits stripped
                null,
                false, // visionObscured
                true,  // awaitingSpawn
                true   // hasLowFreq
        );
    }

    private byte[] serializeInternal(
            Collection<Player> players,
            boolean filterPlayerVision,
            Collection<Projectile> projectiles,
            Collection<FieldEffect> fieldEffects,
            boolean filterFieldEffectsArmed,
            boolean filterFieldEffectsSmokeOnly,
            Collection<Turret> turrets,
            Collection<NetProjectile> nets,
            Collection<DefenseLaser> lasers,
            Collection<KothZone> kothZones,
            Collection<Headquarters> hqs,
            Collection<Flag> flags,
            Collection<Oddball> oddballs,
            Collection<Zombie> zombies,
            List<DamageHit> hits,
            Integer hitPlayerIdFilter,
            boolean visionObscured,
            boolean awaitingSpawn,
            boolean hasLowFreq
    ) {
        baos.reset();

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
            if (hasLowFreq) {
                headerFlags |= 32;
            }
            out.writeByte(headerFlags);

            // Game state enum code (1 byte)
            out.writeByte("COUNTDOWN".equals(stateStr) ? 2 : ("PLAYING".equals(stateStr) ? 1 : 0));

            // Timestamp (8 bytes)
            out.writeLong(System.currentTimeMillis());

            // Start countdown remaining (only present when in countdown state)
            if (isCountdown) {
                out.writeFloat(((Number) ruleSystem.getStartCountdownRemaining()).floatValue());
            }

            // Victory status
            out.writeByte(Optional.ofNullable(ruleSystem.getWinningTeam()).orElse(-1));
            out.writeShort(Optional.ofNullable(ruleSystem.getWinningPlayerId()).orElse(-1));

            // Low-Frequency Header Data
            if (hasLowFreq) {
                out.writeFloat(((Number) Math.max(0, (ruleSystem.getMatchEndTime() - System.currentTimeMillis()) / 1000)).floatValue());

                // Team Scores Map
                Map<Integer, Integer> teamScores = ruleSystem.calculateTeamScores();
                out.writeByte(teamScores.size());
                for (Map.Entry<Integer, Integer> entry : teamScores.entrySet()) {
                    out.writeByte(entry.getKey());
                    out.writeInt(entry.getValue());
                }
            }

            // 1. Players
            int playerCount = 0;
            if (players != null && !players.isEmpty()) {
                if (filterPlayerVision) {
                    for (Player p : players) {
                        if (!p.isVisionObscured()) {
                            playerCount++;
                        }
                    }
                } else {
                    playerCount = players.size();
                }
            }
            out.writeShort(playerCount);
            if (playerCount > 0) {
                for (Player p : players) {
                    if (filterPlayerVision && p.isVisionObscured()) {
                        continue;
                    }
                    writePlayer(out, p, hasLowFreq, visionObscured);
                }
            }

            // 2. Projectiles
            int projCount = (projectiles != null) ? projectiles.size() : 0;
            out.writeShort(projCount);
            if (projCount > 0) {
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
            }

            // 3. Field Effects
            int feCount = 0;
            if (fieldEffects != null && !fieldEffects.isEmpty()) {
                for (FieldEffect fe : fieldEffects) {
                    if (filterFieldEffectsSmokeOnly) {
                        if (fe.getType() == FieldEffectType.SMOKE && fe.isArmed()) feCount++;
                    } else if (filterFieldEffectsArmed) {
                        if (fe.isArmed()) feCount++;
                    } else {
                        feCount++;
                    }
                }
            }
            out.writeShort(feCount);
            if (feCount > 0) {
                for (FieldEffect fe : fieldEffects) {
                    if (filterFieldEffectsSmokeOnly) {
                        if (fe.getType() != FieldEffectType.SMOKE || !fe.isArmed()) continue;
                    } else if (filterFieldEffectsArmed) {
                        if (!fe.isArmed()) continue;
                    }

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

                    writeBodyShapes(out, fe.getBody());
                }
            }

            // 4. Turrets
            int turretCount = (turrets != null) ? turrets.size() : 0;
            out.writeShort(turretCount);
            if (turretCount > 0) {
                for (Turret t : turrets) {
                    out.writeShort(t.getId());
                    out.writeByte(t.getOwnerTeam());
                    out.writeFloat((float) t.getPosition().x);
                    out.writeFloat((float) t.getPosition().y);
                    out.writeFloat((float) t.getBody().getTransform().getRotation().toRadians());
                    out.writeByte((int) Math.round(t.healthPercent() * 100.0));
                    out.writeBoolean(t.isActive());
                }
            }

            // 5. Nets
            int netCount = (nets != null) ? nets.size() : 0;
            out.writeShort(netCount);
            if (netCount > 0) {
                for (NetProjectile net : nets) {
                    out.writeShort(net.getId());
                    out.writeFloat((float) net.getPosition().x);
                    out.writeFloat((float) net.getPosition().y);
                    out.writeFloat((float) net.getBody().getTransform().getRotation().toRadians());
                    out.writeBoolean(net.isActive());
                }
            }

            // 6. Defense Lasers
            int laserCount = (lasers != null) ? lasers.size() : 0;
            out.writeShort(laserCount);
            if (laserCount > 0) {
                for (DefenseLaser l : lasers) {
                    out.writeShort(l.getId());
                    out.writeByte(l.getOwnerTeam());
                    out.writeFloat((float) l.getPosition().x);
                    out.writeFloat((float) l.getPosition().y);
                    out.writeFloat((float) l.getCurrentRotation());
                    out.writeBoolean(l.isActive());
                }
            }

            // 7. KOTH Zones
            int kothCount = (kothZones != null) ? kothZones.size() : 0;
            out.writeShort(kothCount);
            if (kothCount > 0) {
                for (KothZone z : kothZones) {
                    out.writeShort(z.getId());
                    out.writeByte(z.getControllingTeam());
                    out.writeByte(z.getState().ordinal());
                    out.writeByte(z.getTotalPlayerCount());
                }
            }

            // 8. Headquarters
            int hqCount = (hqs != null) ? hqs.size() : 0;
            out.writeShort(hqCount);
            if (hqCount > 0) {
                for (Headquarters hq : hqs) {
                    out.writeShort(hq.getId());
                    out.writeByte(hq.getOwnerTeam());
                    out.writeFloat((float) hq.getPosition().x);
                    out.writeFloat((float) hq.getPosition().y);
                    out.writeByte((int) Math.round(hq.healthPercent() * 100.0));
                    out.writeBoolean(!hq.isActive());
                }
            }

            // 9. Flags
            int flagCount = (flags != null) ? flags.size() : 0;
            out.writeShort(flagCount);
            if (flagCount > 0) {
                for (Flag f : flags) {
                    out.writeShort(f.getId());
                    out.writeByte(f.getOwnerTeam());
                    out.writeFloat((float) f.getPosition().x);
                    out.writeFloat((float) f.getPosition().y);
                    out.writeShort(f.getCarriedByPlayerId());
                    out.writeBoolean(f.isAtHome());
                }
            }

            // 10. NPCs (Unified: Oddballs + Zombies)
            int oddballCount = 0;
            if (oddballs != null && !oddballs.isEmpty()) {
                for (Oddball ob : oddballs) {
                    if (ob.isActive()) oddballCount++;
                }
            }
            int zombieCount = 0;
            if (zombies != null && !zombies.isEmpty()) {
                for (Zombie z : zombies) {
                    if (z.isActive()) zombieCount++;
                }
            }
            out.writeShort(oddballCount + zombieCount);
            if (oddballCount > 0) {
                for (Oddball ob : oddballs) {
                    if (!ob.isActive()) continue;
                    out.writeShort(ob.getId());
                    out.writeByte(0); // category: 0 = ODDBALL
                    out.writeByte(ob.getPersonality().ordinal());
                    out.writeFloat((float) ob.getPosition().x);
                    out.writeFloat((float) ob.getPosition().y);
                    out.writeFloat((float) ob.getVelocity().x);
                    out.writeFloat((float) ob.getVelocity().y);
                    out.writeFloat((float) ob.getRadius());
                    out.writeFloat((float) ob.getRotation());
                    out.writeByte((int) Math.round(ob.healthPercent() * 100.0));
                    out.writeByte(0); // flags
                }
            }
            if (zombieCount > 0) {
                for (Zombie z : zombies) {
                    if (!z.isActive()) continue;
                    out.writeShort(z.getId());
                    out.writeByte(1); // category: 1 = ZOMBIE
                    out.writeByte(z.getType().ordinal());
                    out.writeFloat((float) z.getPosition().x);
                    out.writeFloat((float) z.getPosition().y);
                    out.writeFloat((float) z.getVelocity().x);
                    out.writeFloat((float) z.getVelocity().y);
                    out.writeFloat((float) z.getRadius());
                    out.writeFloat((float) z.getRotation());
                    out.writeByte((int) Math.round(z.healthPercent() * 100.0));
                    out.writeByte(z.isLunging() ? 1 : 0); // bit 0 = isLunging
                }
            }

            // 11. Hits
            int hitCount = 0;
            if (hits != null && !hits.isEmpty()) {
                if (hitPlayerIdFilter != null) {
                    int filterId = hitPlayerIdFilter;
                    for (DamageHit hit : hits) {
                        if (hit.getAttackerId() == filterId || hit.getVictimId() == filterId) {
                            hitCount++;
                        }
                    }
                } else {
                    hitCount = hits.size();
                }
            }
            out.writeShort(hitCount);
            if (hitCount > 0) {
                for (DamageHit hit : hits) {
                    if (hitPlayerIdFilter != null) {
                        int filterId = hitPlayerIdFilter;
                        if (hit.getAttackerId() != filterId && hit.getVictimId() != filterId) {
                            continue;
                        }
                    }
                    out.writeFloat((float) hit.getX());
                    out.writeFloat((float) hit.getY());
                    out.writeFloat((float) hit.getDamage());
                    out.writeShort(hit.getAttackerId());
                    out.writeShort(hit.getVictimId());
                    int hitFlags = 0;
                    if (hit.isKill()) {
                        hitFlags |= 1;
                    }
                    if (hit.isArmorMitigated()) {
                        hitFlags |= 2;
                    }
                    out.writeByte(hitFlags);
                }
            }

            out.flush();
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Binary serialization failed", e);
        }
    }

    private void writePlayer(DataOutputStream out, Player p, boolean hasLowFreq, boolean visionObscured) throws IOException {
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
        if (p.isRiotShieldActive()) {
            playerFlags |= 32;
        }
        out.writeByte(playerFlags);

        out.writeFloat((float) p.getPosition().x);
        out.writeFloat((float) p.getPosition().y);

        out.writeShort((short) Math.round(p.getBody().getLinearVelocity().x * 10.0));
        out.writeShort((short) Math.round(p.getBody().getLinearVelocity().y * 10.0));

        out.writeShort((short) Math.round((p.getRotation() % (2 * Math.PI)) * 1000.0));

        out.writeByte((int) Math.round(p.healthPercent() * 100.0));
        out.writeByte((int) Math.round(p.armorPercent() * 100.0));
        out.writeByte(p.getCurrentWeapon().getCurrentAmmo());
        out.writeByte(p.getCurrentWeapon().getMagazineSize());
        out.writeByte((int) Math.round(p.getReloadPercent() * 100.0));
        out.writeByte((int) Math.round(p.getUtilityCooldownProgress() * 100.0));

        double respawnTime = Math.max(0, ((double) p.getRespawnTime() - System.currentTimeMillis()) / 1000.0);
        out.writeFloat((float) respawnTime);
        out.writeByte(p.getLivesRemaining());

        if (hasLowFreq) {
            writeString8(out, p.getPlayerName());
            out.writeShort((int) Math.round(p.getCurrentWeapon().getRange()));

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
            int powerUpCount = 0;
            if (!visionObscured) {
                for (AttributeModification mod : p.getAttributeModifications()) {
                    String hint = mod.renderHint();
                    if (hint != null && !hint.isEmpty()) {
                        powerUpCount++;
                    }
                }
            }
            out.writeByte(powerUpCount);
            if (powerUpCount > 0) {
                for (AttributeModification mod : p.getAttributeModifications()) {
                    String hint = mod.renderHint();
                    if (hint != null && !hint.isEmpty()) {
                        writeString8(out, hint);
                    }
                }
            }
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

    private static void writeBodyShapes(DataOutputStream out, Body body) throws IOException {
        if (body == null || body.getFixtureCount() == 0) {
            out.writeByte(0);
            return;
        }
        int fixtureCount = body.getFixtureCount();
        out.writeByte(Math.min(255, fixtureCount));
        for (int i = 0; i < fixtureCount; i++) {
            Convex convex = body.getFixture(i).getShape();
            if (convex instanceof Polygon polygon) {
                out.writeByte(0); // 0 = Polygon
                Vector2[] polyVertices = polygon.getVertices();
                out.writeByte(Math.min(255, polyVertices.length));
                for (Vector2 vertex : polyVertices) {
                    out.writeFloat((float) vertex.x);
                    out.writeFloat((float) vertex.y);
                }
            } else if (convex instanceof Circle circle) {
                out.writeByte(1); // 1 = Circle
                Vector2 center = circle.getCenter();
                out.writeFloat((float) center.x);
                out.writeFloat((float) center.y);
                out.writeFloat((float) circle.getRadius());
            } else {
                out.writeByte(0); // fallback empty polygon
                out.writeByte(0);
            }
        }
    }
}
