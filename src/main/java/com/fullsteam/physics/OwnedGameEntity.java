package com.fullsteam.physics;

import com.fullsteam.model.Owned;
import lombok.Getter;
import org.dyn4j.dynamics.Body;

@Getter
public abstract class OwnedGameEntity extends GameEntity implements Owned {
    protected final int ownerId;
    protected final int ownerTeam;

    public OwnedGameEntity(int id, Body body, double health, int ownerId, int ownerTeam) {
        super(id, body, health);
        this.ownerId = ownerId;
        this.ownerTeam = ownerTeam;
    }

    public boolean isFriendy(OwnedGameEntity gameEntity) {
        // team 0 is special indicating "no teams"
        return (ownerTeam != 0 && ownerTeam == gameEntity.ownerTeam) || ownerId == gameEntity.ownerId;
    }
}
