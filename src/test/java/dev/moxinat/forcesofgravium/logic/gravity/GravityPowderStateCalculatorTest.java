package dev.moxinat.forcesofgravium.logic.gravity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class GravityPowderStateCalculatorTest {

    @Test
    void resolvesPushWhenSourceIsVisible() {
        assertEquals(
                GravityPowderStateCalculator.MODE_PUSH,
                GravityPowderStateCalculator.resolveDrivenMode(false, true, false, false)
        );
    }

    @Test
    void resolvesPushWhenPushDriverIsVisible() {
        assertEquals(
                GravityPowderStateCalculator.MODE_PUSH,
                GravityPowderStateCalculator.resolveDrivenMode(false, false, true, false)
        );
    }

    @Test
    void resolvesPullWhenOnlyPullDriverIsVisible() {
        assertEquals(
                GravityPowderStateCalculator.MODE_PULL,
                GravityPowderStateCalculator.resolveDrivenMode(false, false, false, true)
        );
    }

    @Test
    void keepsOffWavePriorityAboveOtherDrivers() {
        assertEquals(
                GravityPowderStateCalculator.MODE_OFF,
                GravityPowderStateCalculator.resolveDrivenMode(true, true, true, true)
        );
    }

    @Test
    void returnsNullWithoutAnyDriver() {
        assertNull(GravityPowderStateCalculator.resolveDrivenMode(false, false, false, false));
    }

    @Test
    void upgradesPullWaveToPushWaveWhenPushDriverAppears() {
        assertEquals(
                "push_wave",
                GravityPowderStateCalculator.overrideWaveState("pull_wave", GravityPowderStateCalculator.MODE_PUSH)
        );
    }
}
