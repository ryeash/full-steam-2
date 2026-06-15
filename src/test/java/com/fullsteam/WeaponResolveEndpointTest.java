package com.fullsteam;

import com.fullsteam.controller.GameController;
import com.fullsteam.model.Ordinance;
import com.fullsteam.model.WeaponConfig;
import io.micronaut.http.HttpResponse;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the /api/weapon-customization/resolve endpoint logic: that it reports
 * coupled stats, the coupling breakdown, the budget, and rejects bad input.
 */
class WeaponResolveEndpointTest extends BaseTestClass {

    @Inject
    GameController controller;

    @SuppressWarnings("unchecked")
    @Test
    void reportsCoupledStatsBudgetAndDerived() {
        WeaponConfig cfg = new WeaponConfig();
        cfg.type = "Custom";
        cfg.fireRate = 30; // max → FIRE_RATE→ACCURACY recoil coupling
        cfg.damage = 40;   // → DAMAGE→HANDLING coupling
        cfg.ordinance = Ordinance.PROJECTILE;

        HttpResponse<Map<String, Object>> resp = controller.resolveCustomization(cfg);
        Map<String, Object> body = resp.body();
        assertNotNull(body);
        assertEquals(Boolean.TRUE, body.get("valid"));

        Map<String, Object> attrs = (Map<String, Object>) body.get("attributes");
        Map<String, Object> acc = (Map<String, Object>) attrs.get("ACCURACY");
        assertEquals(Boolean.TRUE, acc.get("coupled"));
        assertTrue(((Number) acc.get("value")).doubleValue() < ((Number) acc.get("baseValue")).doubleValue(),
                "high fire rate should drag accuracy below its base");
        Map<String, Object> hand = (Map<String, Object>) attrs.get("HANDLING");
        assertTrue(((Number) hand.get("value")).doubleValue() < ((Number) hand.get("baseValue")).doubleValue(),
                "high damage should reduce handling below its base");

        assertFalse(((List<?>) body.get("couplings")).isEmpty(), "coupling breakdown should be reported");

        Map<String, Object> budget = (Map<String, Object>) body.get("budget");
        assertEquals(70, ((Number) budget.get("total")).intValue()); // 40 + 30, BULLET = 0

        assertNotNull(((Map<String, Object>) body.get("derived")).get("dps"));
    }

    @Test
    void rejectsOutOfRangePoints() {
        WeaponConfig cfg = new WeaponConfig();
        cfg.damage = 999; // outside DAMAGE [0,40]
        HttpResponse<Map<String, Object>> resp = controller.resolveCustomization(cfg);
        assertEquals(400, resp.code());
    }
}
