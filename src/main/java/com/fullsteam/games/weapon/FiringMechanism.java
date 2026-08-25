package com.fullsteam.games.weapon;

import com.fullsteam.model.PlayerInput;
import com.fullsteam.model.Weapon;
import com.fullsteam.physics.GameEntity;
import com.fullsteam.physics.Player;
import org.dyn4j.geometry.Vector2;

import java.util.List;

/**
 * Strategy interface for ordinance-specific weapon firing mechanisms.
 */
public interface FiringMechanism {

    /**
     * Process primary weapon input for a player.
     */
    void handlePlayerFire(Player player, PlayerInput input);

    /**
     * Generate fired game entities for non-player entities (Turrets, Oddball, etc.)
     * or single-shot firings.
     */
    List<GameEntity> fire(int ownerId, int ownerTeam, Weapon weapon, Vector2 position, Vector2 direction);
}
