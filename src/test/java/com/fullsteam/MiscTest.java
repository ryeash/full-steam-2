package com.fullsteam;

import com.fullsteam.model.BulletEffect;
import com.fullsteam.model.WeaponAttribute;
import com.fullsteam.model.WeaponConfig;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.fail;

@MicronautTest(startApplication = false)
public class MiscTest extends BaseTestClass {

    @Test
    public void validatePresets() throws IllegalAccessException {
        // Get all static final WeaponConfig fields from WeaponConfig class
        Field[] fields = WeaponConfig.class.getDeclaredFields();

        System.out.println("Validating weapon preset point budgets:");
        System.out.println("=====================================");

        for (Field field : fields) {
            if (field.getType().getSimpleName().equals("WeaponConfig") && field.getName().endsWith("_PRESET")) {
                WeaponConfig config = (WeaponConfig) field.get(null);
                System.out.println(config);

                WeaponAttribute.DAMAGE.validate(config.bulletsPerShot);
                WeaponAttribute.FIRE_RATE.validate(config.fireRate);
                WeaponAttribute.ACCURACY.validate(config.accuracy);
                WeaponAttribute.MAGAZINE_SIZE.validate(config.magazineSize);
                WeaponAttribute.LINEAR_DAMPING.validate(config.linearDamping);
                WeaponAttribute.RANGE.validate(config.range);
                WeaponAttribute.RELOAD_TIME.validate(config.reloadTime);
                WeaponAttribute.PROJECTILE_SPEED.validate(config.projectileSpeed);
                WeaponAttribute.BULLETS_PER_SHOT.validate(config.bulletsPerShot);

                int effectPoints = config.getBulletEffects().stream().mapToInt(BulletEffect::getPointCost).sum();
                int ordinancePoints = config.getOrdinance().getPointCost();
                int attributePoints = config.getAttributePoints();
                int totalPoints = attributePoints + effectPoints + ordinancePoints;
                if (totalPoints < 100) {
                    fail("under allocated on " + field.getName() + " " + totalPoints + "/100");
                }
                if (totalPoints > 100) {
                    fail("over allocated on " + field.getName() + " " + totalPoints + "/100");
                }
            }
        }
    }
}