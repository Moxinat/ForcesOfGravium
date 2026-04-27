package dev.moxinat.forcesofgravium.logic.inverter;

import dev.moxinat.forcesofgravium.logic.gravity.GravityPowderStateCalculator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InverterStateCalculatorTest {

    @Test
    void invertsPushInputToPullOutput() {
        assertEquals(
                GravityPowderStateCalculator.MODE_PULL,
                InverterStateCalculator.invertMode(GravityPowderStateCalculator.MODE_PUSH)
        );
    }

    @Test
    void invertsPullInputToPushOutput() {
        assertEquals(
                GravityPowderStateCalculator.MODE_PUSH,
                InverterStateCalculator.invertMode(GravityPowderStateCalculator.MODE_PULL)
        );
    }
}
