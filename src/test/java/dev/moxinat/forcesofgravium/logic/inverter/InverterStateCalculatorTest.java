package dev.moxinat.forcesofgravium.logic.inverter;

import dev.moxinat.forcesofgravium.logic.gravity.GravityPowderStateCalculator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InverterStateCalculatorTest {

    @Test
    void treatsDirectSourceInputAsPullOutput() {
        assertEquals(
                GravityPowderStateCalculator.MODE_PUSH,
                InverterStateCalculator.directSourceInputMode("Rock_Crystal_Blue_Block")
        );
    }

    @Test
    void ignoresUnknownDirectInputBlocks() {
        assertEquals(
                GravityPowderStateCalculator.MODE_OFF,
                InverterStateCalculator.directSourceInputMode("Unknown_Block")
        );
    }

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
