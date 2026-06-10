package com.fullsteam.model;

import lombok.Data;

/**
 * Encapsulates every per-player scoring contribution in one place so that scores
 * can be tracked, summed, and rendered consistently.
 *
 * <p>All scoring mechanisms credit the player who earned them here; team and FFA
 * totals are then derived simply by summing players' {@link #total(Rules)}.
 */
@Data
public class Scoring {
    private int kills = 0;
    private int deaths = 0;
    private int flagCaptures = 0;
    private double kingOfTheHillPoints = 0;
    private double oddball = 0;
    private double headquarterDamage = 0;     // cumulative raw damage dealt to enemy HQs
    private int headquartersDestroyed = 0;     // enemy HQs destroyed by this player
    private int vipKills = 0;                   // enemy VIPs killed by this player

    public void addKill() {
        kills++;
    }

    public void addDeath() {
        deaths++;
    }

    public void addCapture() {
        flagCaptures++;
    }

    public void addKingOfTheHillPoints(double points) {
        kingOfTheHillPoints += points;
    }

    public void addOddball(double points) {
        oddball += points;
    }

    public void addHeadquarterDamage(double damage) {
        headquarterDamage += damage;
    }

    public void addHeadquartersDestroyed() {
        headquartersDestroyed++;
    }

    public void addVipKill() {
        vipKills++;
    }

    /**
     * Reset all scoring back to zero (e.g. at the start of a new round).
     */
    public void reset() {
        kills = 0;
        deaths = 0;
        flagCaptures = 0;
        kingOfTheHillPoints = 0;
        oddball = 0;
        headquarterDamage = 0;
        headquartersDestroyed = 0;
        vipKills = 0;
    }

    /**
     * The kills/captures component, weighted by the active {@link ScoreStyle}.
     * This value is always a whole number.
     */
    private int baseScore(Rules rules) {
        int capturePoints = flagCaptures * rules.getPointsPerFlagCapture();
        return switch (rules.getScoreStyle()) {
            case TOTAL_KILLS -> kills;
            case OBJECTIVE -> capturePoints;
            case TOTAL -> kills + capturePoints;
        };
    }

    /**
     * The objective/bonus component (KOTH, oddball, VIP kills, HQ damage and
     * destruction), gated by the active {@link ScoreStyle} and enabled rules.
     * Returned rounded to the nearest whole point.
     */
    public int bonusPoints(Rules rules) {
        boolean objectiveScoring = rules.getScoreStyle() == ScoreStyle.OBJECTIVE
                || rules.getScoreStyle() == ScoreStyle.TOTAL;

        double bonus = 0;

        // Oddball points count whenever the mode is active (legacy behavior).
        if (rules.hasOddball()) {
            bonus += oddball;
        }

        // KOTH and VIP kills only count toward objective-based score styles.
        if (objectiveScoring) {
            if (rules.hasKothZones()) {
                bonus += kingOfTheHillPoints;
            }
            if (rules.hasVip()) {
                bonus += vipKills;
            }
        }

        // Headquarters points always count (independent of score style), matching
        // the previous "bonus team points" behavior.
        bonus += headquarterDamage * rules.getHeadquartersPointsPerDamage();
        bonus += (double) headquartersDestroyed * rules.getHeadquartersDestructionBonus();

        return (int) Math.round(bonus);
    }

    /**
     * The player's total score under the given rules: kills/captures (by score
     * style) plus all applicable objective and bonus points.
     */
    public int total(Rules rules) {
        return baseScore(rules) + bonusPoints(rules);
    }
}
