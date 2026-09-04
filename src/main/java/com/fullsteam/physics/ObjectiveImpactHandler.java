package com.fullsteam.physics;

import com.fullsteam.games.GameManager;
import com.fullsteam.model.GameEvent;
import com.fullsteam.model.Rules;
import com.fullsteam.model.ScoreStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Handles collisions related to match objectives: Flags (CTF) and King-of-the-Hill (KOTH) zones.
 */
public class ObjectiveImpactHandler {

    private static final Logger log = LoggerFactory.getLogger(ObjectiveImpactHandler.class);

    private final GameManager gameManager;
    private final GameEntities gameEntities;

    public ObjectiveImpactHandler(GameManager gameManager, GameEntities gameEntities) {
        this.gameManager = gameManager;
        this.gameEntities = gameEntities;
    }

    /**
     * Handle player touching a flag - pickup or capture logic.
     */
    public boolean handlePlayerFlagCollision(Player player, Flag flag) {
        int playerTeam = player.getTeam();
        int flagTeam = flag.getOwnerTeam();

        boolean alreadyCarrying = gameEntities.getAllFlags()
                .stream()
                .anyMatch(f -> f.isCarried() && f.getCarriedByPlayerId() == player.getId());

        if (alreadyCarrying) {
            if (playerTeam == flagTeam && flag.isAtHome()) {
                captureFlag(player, flag);
            }
            return true;
        }

        if (flag.canBeCapturedBy(playerTeam)) {
            pickUpFlag(player, flag);
        } else if (playerTeam == flagTeam && !flag.isAtHome()) {
            returnFlag(flag);
        }
        return true;
    }

    /**
     * Handle player entering/staying in a KOTH zone.
     */
    public boolean handlePlayerKothZoneCollision(Player player, KothZone zone) {
        zone.addPlayer(player);
        return true;
    }

    /**
     * Update all KOTH zones - called once per physics step with proper deltaTime.
     * This ensures scoring is frame-rate independent.
     */
    public void updateKothZones(double deltaTime) {
        for (KothZone zone : gameEntities.getAllKothZones()) {
            if (zone.shouldAwardPoints()) {
                double points = zone.getPointsPerSecond() * deltaTime;
                if (points > 0) {
                    awardZonePoints(zone, points);
                }
            }
            zone.clearPlayers();
        }
    }

    /**
     * Credit a controlled zone's points to the player(s) who earned them.
     * FFA: the sole controlling player. Team mode: split equally among the living
     * controlling-team players in the zone, so the team total still equals the
     * zone's points-per-second.
     */
    private void awardZonePoints(KothZone zone, double points) {
        if (zone.getControllingPlayerId() >= 0) {
            Player controller = gameEntities.getPlayer(zone.getControllingPlayerId());
            if (controller != null) {
                controller.getScoring().addKingOfTheHillPoints(points);
            }
            return;
        }

        if (zone.getControllingTeam() >= 0) {
            var holders = zone.getPlayersInZone().stream()
                    .filter(p -> p.isActive() && p.getHealth() > 0)
                    .filter(p -> p.getTeam() == zone.getControllingTeam())
                    .toList();
            if (!holders.isEmpty()) {
                double share = points / holders.size();
                for (Player holder : holders) {
                    holder.getScoring().addKingOfTheHillPoints(share);
                }
            }
        }
    }

    private void pickUpFlag(Player player, Flag flag) {
        flag.pickUp(player.getId());

        log.debug("Player {} (team {}) picked up flag {} (team {})",
                player.getId(), player.getTeam(), flag.getId(), flag.getOwnerTeam());

        gameManager.broadcastGameEvent(
                String.format("%s picked up %s flag!",
                        player.getPlayerName(),
                        getTeamName(flag.getOwnerTeam())),
                GameEvent.EventCategory.INFO,
                "#ffaa00"
        );
    }

    private void captureFlag(Player player, Flag homeFlag) {
        Optional<Flag> carriedFlagOpt = gameEntities.getAllFlags().stream()
                .filter(f -> f.isCarried() && f.getCarriedByPlayerId() == player.getId())
                .findFirst();

        if (carriedFlagOpt.isEmpty()) {
            return;
        }

        Flag carriedFlag = carriedFlagOpt.get();
        carriedFlag.capture();

        gameManager.awardCapture(player);

        Rules rules = gameManager.getGameConfig().getRules();
        ScoreStyle style = rules.getScoreStyle();
        boolean stylePointsCaptures = style == ScoreStyle.OBJECTIVE || style == ScoreStyle.TOTAL;
        int capturePoints = rules.getPointsPerFlagCapture();
        String captureText = (stylePointsCaptures && capturePoints > 1)
                ? String.format("+1 CAPTURE (+%d pts)", capturePoints)
                : "+1 CAPTURE";
        gameManager.broadcastGameEvent(
                String.format("%s captured the %s flag! %s",
                        player.getPlayerName(),
                        getTeamName(carriedFlag.getOwnerTeam()),
                        captureText),
                GameEvent.EventCategory.CAPTURE,
                "#00ff00"
        );
    }

    private void returnFlag(Flag flag) {
        flag.returnToHome();
        log.debug("Flag {} (team {}) returned to home", flag.getId(), flag.getOwnerTeam());

        gameManager.broadcastGameEvent(
                String.format("%s flag returned!", getTeamName(flag.getOwnerTeam())),
                GameEvent.EventCategory.INFO,
                "#4444ff"
        );
    }

    private String getTeamName(int team) {
        return "Team " + team;
    }
}
