package com.fullsteam.ai;

import com.fullsteam.model.PlayerInput;
import com.fullsteam.physics.Flag;
import com.fullsteam.physics.GameEntities;
import com.fullsteam.physics.Player;
import org.dyn4j.geometry.Vector2;

import java.util.List;

/**
 * Behavior for Capture the Flag gameplay.
 * AI will capture enemy flags, defend own flags, and return dropped flags.
 */
public class FlagBehavior implements AIBehavior {
    private enum FlagRole {
        ATTACKER,  // Capture enemy flags
        DEFENDER,  // Protect own flags
        RETRIEVER  // Return dropped friendly flags
    }

    private FlagRole currentRole = FlagRole.ATTACKER;
    private int targetFlagId = -1;
    private double roleChangeTime = 0;
    private static final double ROLE_CHANGE_INTERVAL = 10.0; // Re-evaluate role every 10 seconds

    @Override
    public PlayerInput generateInput(AIPlayer aiPlayer, GameEntities gameEntities, double deltaTime) {
        PlayerInput input = new PlayerInput();

        roleChangeTime += deltaTime;
        if (roleChangeTime >= ROLE_CHANGE_INTERVAL) {
            evaluateRole(aiPlayer, gameEntities);
            roleChangeTime = 0;
        }

        switch (currentRole) {
            case ATTACKER:
                executeAttackerBehavior(aiPlayer, gameEntities, input, deltaTime);
                break;
            case DEFENDER:
                executeDefenderBehavior(aiPlayer, gameEntities, input, deltaTime);
                break;
            case RETRIEVER:
                executeRetrieverBehavior(aiPlayer, gameEntities, input, deltaTime);
                break;
        }

        return input;
    }

    /**
     * Evaluate and assign the best role for this AI based on game state.
     */
    private void evaluateRole(AIPlayer aiPlayer, GameEntities gameEntities) {
        int myTeam = aiPlayer.getTeam();
        Vector2 myPos = aiPlayer.getPosition();

        // Check if we're already carrying a flag
        if (isCarryingFlag(aiPlayer, gameEntities)) {
            currentRole = FlagRole.ATTACKER; // Continue to capture
            return;
        }

        // Check for dropped friendly flags (high priority)
        Flag droppedFriendlyFlag = findNearestDroppedFriendlyFlag(aiPlayer, gameEntities);
        if (droppedFriendlyFlag != null) {
            double distance = myPos.distance(droppedFriendlyFlag.getPosition());
            if (distance < 400) { // Within reasonable distance
                currentRole = FlagRole.RETRIEVER;
                targetFlagId = droppedFriendlyFlag.getId();
                return;
            }
        }

        // Check if own flags are under threat
        List<Flag> ownFlags = getTeamFlags(myTeam, gameEntities);
        for (Flag flag : ownFlags) {
            if (flag.isCarried()) {
                // Our flag is being carried - become retriever
                currentRole = FlagRole.RETRIEVER;
                targetFlagId = flag.getId();
                return;
            }

            // Check if enemies are near our flag
            if (areEnemiesNearFlag(aiPlayer, flag, gameEntities, 200)) {
                currentRole = FlagRole.DEFENDER;
                targetFlagId = flag.getId();
                return;
            }
        }

        // Personality-based role assignment
        double aggressiveness = aiPlayer.getPersonality().getAggressiveness();
        double teamwork = aiPlayer.getPersonality().getTeamwork();

        if (aggressiveness > 0.6 || teamwork < 0.4) {
            // Aggressive or lone wolf - attack
            currentRole = FlagRole.ATTACKER;
            targetFlagId = -1; // Will find target in execute method
        } else {
            // Defensive or team player - defend
            currentRole = FlagRole.DEFENDER;
            targetFlagId = -1;
        }
    }

    /**
     * Attacker behavior: Capture enemy flags and return them to base.
     */
    private void executeAttackerBehavior(AIPlayer aiPlayer, GameEntities gameEntities, PlayerInput input, double deltaTime) {
        Vector2 myPos = aiPlayer.getPosition();
        int myTeam = aiPlayer.getTeam();

        // Check if we're carrying a flag
        Flag carriedFlag = getCarriedFlag(aiPlayer, gameEntities);
        if (carriedFlag != null) {
            // Return flag to home position
            returnFlagToBase(aiPlayer, carriedFlag, input);
            
            // Still defend ourselves while carrying
            engageNearbyEnemies(aiPlayer, gameEntities, input, 300);
            return;
        }

        // Find an enemy flag to capture
        Flag targetFlag = findBestEnemyFlagToCapture(aiPlayer, gameEntities);
        if (targetFlag == null) {
            // No flags available, help with combat
            currentRole = FlagRole.DEFENDER;
            return;
        }

        targetFlagId = targetFlag.getId();
        Vector2 flagPos = targetFlag.getPosition();
        double distance = myPos.distance(flagPos);

        // Move towards flag
        Vector2 direction = flagPos.copy().subtract(myPos);
        direction.normalize();

        // Tactical movement - avoid straight lines
        double moveIntensity = 0.8 + (aiPlayer.getPersonality().getMobility() * 0.2);
        
        // Add some weaving to avoid fire
        double weaveFactor = Math.sin(System.currentTimeMillis() / 500.0) * 0.3;
        Vector2 perpendicular = new Vector2(-direction.y, direction.x);
        direction.add(perpendicular.multiply(weaveFactor));
        direction.normalize();

        input.setMoveX(direction.x * moveIntensity);
        input.setMoveY(direction.y * moveIntensity);

        // Engage enemies along the way
        engageNearbyEnemies(aiPlayer, gameEntities, input, 400);

        // Reload when safe and low on ammo
        smartReload(aiPlayer, input, distance > 200);
    }

    /**
     * Defender behavior: Protect own flags from enemies.
     */
    private void executeDefenderBehavior(AIPlayer aiPlayer, GameEntities gameEntities, PlayerInput input, double deltaTime) {
        Vector2 myPos = aiPlayer.getPosition();
        int myTeam = aiPlayer.getTeam();

        // Find flag to defend
        Flag flagToDefend = null;
        if (targetFlagId != -1) {
            flagToDefend = gameEntities.getFlag(targetFlagId);
        }

        if (flagToDefend == null || flagToDefend.getOwnerTeam() != myTeam) {
            // Find nearest own flag
            flagToDefend = findNearestTeamFlag(aiPlayer, gameEntities);
        }

        if (flagToDefend == null) {
            currentRole = FlagRole.ATTACKER;
            return;
        }

        Vector2 flagPos = flagToDefend.getPosition();
        double distance = myPos.distance(flagPos);

        // Maintain defensive position around flag
        double optimalDefenseRadius = 150.0;

        if (distance > optimalDefenseRadius + 50) {
            // Move closer to flag
            Vector2 direction = flagPos.copy().subtract(myPos);
            direction.normalize();
            input.setMoveX(direction.x * 0.7);
            input.setMoveY(direction.y * 0.7);
        } else if (distance < optimalDefenseRadius - 50) {
            // Too close, back away slightly
            Vector2 direction = myPos.copy().subtract(flagPos);
            direction.normalize();
            input.setMoveX(direction.x * 0.4);
            input.setMoveY(direction.y * 0.4);
        } else {
            // Good position, patrol around flag
            double patrolAngle = (System.currentTimeMillis() / 3000.0) % (Math.PI * 2);
            Vector2 patrolOffset = new Vector2(
                Math.cos(patrolAngle) * optimalDefenseRadius,
                Math.sin(patrolAngle) * optimalDefenseRadius
            );
            Vector2 patrolTarget = flagPos.copy().add(patrolOffset);
            Vector2 direction = patrolTarget.copy().subtract(myPos);
            direction.normalize();
            input.setMoveX(direction.x * 0.5);
            input.setMoveY(direction.y * 0.5);
        }

        // Prioritize enemies near flag
        engageNearbyEnemies(aiPlayer, gameEntities, input, 500);

        // Reload when safe
        smartReload(aiPlayer, input, distance < optimalDefenseRadius);
    }

    /**
     * Retriever behavior: Return dropped friendly flags or chase flag carriers.
     */
    private void executeRetrieverBehavior(AIPlayer aiPlayer, GameEntities gameEntities, PlayerInput input, double deltaTime) {
        Vector2 myPos = aiPlayer.getPosition();
        int myTeam = aiPlayer.getTeam();

        Flag targetFlag = gameEntities.getFlag(targetFlagId);
        if (targetFlag == null || targetFlag.getOwnerTeam() != myTeam) {
            currentRole = FlagRole.ATTACKER;
            return;
        }

        if (targetFlag.isCarried()) {
            // Chase the flag carrier
            int carrierId = targetFlag.getCarriedByPlayerId();
            Player carrier = gameEntities.getPlayer(carrierId);
            
            if (carrier != null && carrier.isActive()) {
                Vector2 carrierPos = carrier.getPosition();
                double distance = myPos.distance(carrierPos);

                // Aggressive pursuit
                Vector2 direction = carrierPos.copy().subtract(myPos);
                
                // Predict carrier movement
                Vector2 carrierVel = carrier.getVelocity();
                double timeToIntercept = distance / (aiPlayer.getVelocity().getMagnitude() + 100);
                Vector2 predictedPos = carrierPos.copy().add(carrierVel.copy().multiply(timeToIntercept));
                
                direction = predictedPos.copy().subtract(myPos);
                direction.normalize();

                input.setMoveX(direction.x);
                input.setMoveY(direction.y);

                // Aim at carrier
                input.setWorldX(predictedPos.x);
                input.setWorldY(predictedPos.y);

                // Shoot if in range
                if (distance < aiPlayer.getCurrentWeapon().getRange() && aiPlayer.canShoot()) {
                    input.setLeft(true);
                }

                // Use utility to slow/stop carrier
                if (aiPlayer.canUseUtility() && distance < 200) {
                    input.setAltFire(true);
                }
            } else {
                // Carrier died, flag should be dropped
                currentRole = FlagRole.ATTACKER;
            }
        } else if (targetFlag.getState() == Flag.FlagState.DROPPED) {
            // Move to dropped flag
            Vector2 flagPos = targetFlag.getPosition();
            double distance = myPos.distance(flagPos);

            Vector2 direction = flagPos.copy().subtract(myPos);
            direction.normalize();

            input.setMoveX(direction.x * 0.9);
            input.setMoveY(direction.y * 0.9);

            // Clear path of enemies
            engageNearbyEnemies(aiPlayer, gameEntities, input, 350);
        } else {
            // Flag is home, switch role
            currentRole = FlagRole.ATTACKER;
        }

        smartReload(aiPlayer, input, false);
    }

    /**
     * Return carried flag to home base.
     */
    private void returnFlagToBase(AIPlayer aiPlayer, Flag flag, PlayerInput input) {
        Vector2 myPos = aiPlayer.getPosition();
        Vector2 homePos = flag.getHomePosition();
        double distance = myPos.distance(homePos);

        // Sprint home
        Vector2 direction = homePos.copy().subtract(myPos);
        direction.normalize();

        // Add evasive movement when far from home
        if (distance > 200) {
            double evasion = Math.sin(System.currentTimeMillis() / 300.0) * 0.4;
            Vector2 perpendicular = new Vector2(-direction.y, direction.x);
            direction.add(perpendicular.multiply(evasion));
            direction.normalize();
        }

        input.setMoveX(direction.x);
        input.setMoveY(direction.y);
    }

    /**
     * Engage nearby enemies while performing flag objectives.
     */
    private void engageNearbyEnemies(AIPlayer aiPlayer, GameEntities gameEntities, PlayerInput input, double maxRange) {
        Vector2 myPos = aiPlayer.getPosition();
        Player nearestEnemy = null;
        double nearestDistance = maxRange;

        for (Player player : gameEntities.getAllPlayers()) {
            if (player.getId() == aiPlayer.getId() || !player.isActive()) {
                continue;
            }

            if (aiPlayer.isTeammate(player)) {
                continue;
            }

            double distance = myPos.distance(player.getPosition());
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearestEnemy = player;
            }
        }

        if (nearestEnemy != null) {
            // Aim at enemy
            Vector2 enemyPos = nearestEnemy.getPosition();
            Vector2 enemyVel = nearestEnemy.getVelocity();
            
            // Lead target
            double projectileSpeed = aiPlayer.getCurrentWeapon().getProjectileSpeed();
            double timeToTarget = nearestDistance / projectileSpeed;
            Vector2 predictedPos = enemyPos.copy().add(enemyVel.copy().multiply(timeToTarget));

            input.setWorldX(predictedPos.x);
            input.setWorldY(predictedPos.y);

            // Shoot if in good range
            double weaponRange = aiPlayer.getCurrentWeapon().getRange();
            if (nearestDistance < weaponRange * 0.8 && aiPlayer.canShoot()) {
                input.setLeft(true);
            }
        }
    }

    /**
     * Smart reload - only reload when safe.
     */
    private void smartReload(AIPlayer aiPlayer, PlayerInput input, boolean isSafe) {
        int currentAmmo = aiPlayer.getCurrentWeapon().getCurrentAmmo();
        int magazineSize = aiPlayer.getCurrentWeapon().getMagazineSize();

        if (currentAmmo == 0) {
            input.setReload(true);
        } else if (isSafe && currentAmmo < magazineSize * 0.3) {
            input.setReload(true);
        }
    }

    @Override
    public void onEnter(AIPlayer aiPlayer) {
        currentRole = FlagRole.ATTACKER;
        targetFlagId = -1;
        roleChangeTime = 0;
    }

    @Override
    public boolean shouldContinue(AIPlayer aiPlayer, GameEntities gameEntities) {
        // Continue as long as flags exist in the game
        return !gameEntities.getAllFlags().isEmpty();
    }

    @Override
    public int getPriority(AIPlayer aiPlayer, GameEntities gameEntities) {
        // No flags in game = no priority
        if (gameEntities.getAllFlags().isEmpty()) {
            return 0;
        }

        int myTeam = aiPlayer.getTeam();
        Vector2 myPos = aiPlayer.getPosition();

        // Very high priority if carrying a flag
        if (isCarryingFlag(aiPlayer, gameEntities)) {
            return 95;
        }

        // High priority if friendly flag is dropped or carried
        for (Flag flag : gameEntities.getAllFlags()) {
            if (flag.getOwnerTeam() == myTeam) {
                if (flag.isCarried() || flag.getState() == Flag.FlagState.DROPPED) {
                    return 90; // Defend/retrieve is critical
                }
            }
        }

        // High priority if near an enemy flag
        Flag nearestEnemyFlag = findNearestEnemyFlag(aiPlayer, gameEntities);
        if (nearestEnemyFlag != null) {
            double distance = myPos.distance(nearestEnemyFlag.getPosition());
            if (distance < 300) {
                return 85; // Close to objective
            }
        }

        // Moderate priority based on personality
        double aggressiveness = aiPlayer.getPersonality().getAggressiveness();
        double strategicThinking = aiPlayer.getPersonality().getStrategicThinking();
        
        return (int) (60 + (strategicThinking * 20) + (aggressiveness * 10));
    }

    @Override
    public String getName() {
        return "Flag (" + currentRole + ")";
    }

    // Helper methods

    private boolean isCarryingFlag(AIPlayer aiPlayer, GameEntities gameEntities) {
        for (Flag flag : gameEntities.getAllFlags()) {
            if (flag.isCarried() && flag.getCarriedByPlayerId() == aiPlayer.getId()) {
                return true;
            }
        }
        return false;
    }

    private Flag getCarriedFlag(AIPlayer aiPlayer, GameEntities gameEntities) {
        for (Flag flag : gameEntities.getAllFlags()) {
            if (flag.isCarried() && flag.getCarriedByPlayerId() == aiPlayer.getId()) {
                return flag;
            }
        }
        return null;
    }

    private List<Flag> getTeamFlags(int team, GameEntities gameEntities) {
        return gameEntities.getAllFlags().stream()
                .filter(flag -> flag.getOwnerTeam() == team)
                .toList();
    }

    private Flag findNearestTeamFlag(AIPlayer aiPlayer, GameEntities gameEntities) {
        Vector2 myPos = aiPlayer.getPosition();
        int myTeam = aiPlayer.getTeam();
        
        return gameEntities.getAllFlags().stream()
                .filter(flag -> flag.getOwnerTeam() == myTeam)
                .min((f1, f2) -> Double.compare(
                        myPos.distance(f1.getPosition()),
                        myPos.distance(f2.getPosition())
                ))
                .orElse(null);
    }

    private Flag findNearestEnemyFlag(AIPlayer aiPlayer, GameEntities gameEntities) {
        Vector2 myPos = aiPlayer.getPosition();
        int myTeam = aiPlayer.getTeam();
        
        return gameEntities.getAllFlags().stream()
                .filter(flag -> flag.getOwnerTeam() != myTeam && flag.getOwnerTeam() != 0)
                .min((f1, f2) -> Double.compare(
                        myPos.distance(f1.getPosition()),
                        myPos.distance(f2.getPosition())
                ))
                .orElse(null);
    }

    private Flag findBestEnemyFlagToCapture(AIPlayer aiPlayer, GameEntities gameEntities) {
        Vector2 myPos = aiPlayer.getPosition();
        int myTeam = aiPlayer.getTeam();
        
        // Prefer flags that are at home (not carried or dropped)
        return gameEntities.getAllFlags().stream()
                .filter(flag -> flag.getOwnerTeam() != myTeam && flag.getOwnerTeam() != 0)
                .filter(flag -> flag.getState() == Flag.FlagState.AT_HOME)
                .min((f1, f2) -> Double.compare(
                        myPos.distance(f1.getPosition()),
                        myPos.distance(f2.getPosition())
                ))
                .orElse(null);
    }

    private Flag findNearestDroppedFriendlyFlag(AIPlayer aiPlayer, GameEntities gameEntities) {
        Vector2 myPos = aiPlayer.getPosition();
        int myTeam = aiPlayer.getTeam();
        
        return gameEntities.getAllFlags().stream()
                .filter(flag -> flag.getOwnerTeam() == myTeam)
                .filter(flag -> flag.getState() == Flag.FlagState.DROPPED)
                .min((f1, f2) -> Double.compare(
                        myPos.distance(f1.getPosition()),
                        myPos.distance(f2.getPosition())
                ))
                .orElse(null);
    }

    private boolean areEnemiesNearFlag(AIPlayer aiPlayer, Flag flag, GameEntities gameEntities, double radius) {
        Vector2 flagPos = flag.getPosition();
        int myTeam = aiPlayer.getTeam();

        for (Player player : gameEntities.getAllPlayers()) {
            if (player.getTeam() == myTeam || !player.isActive()) {
                continue;
            }

            double distance = flagPos.distance(player.getPosition());
            if (distance < radius) {
                return true;
            }
        }

        return false;
    }
}

