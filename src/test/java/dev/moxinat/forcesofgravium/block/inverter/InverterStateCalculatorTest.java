package dev.moxinat.forcesofgravium.block.inverter;

import dev.moxinat.forcesofgravium.block.inverter.InverterStateCalculator;

import dev.moxinat.forcesofgravium.block.gravity.GravityPowderSpecialStateStore;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InverterStateCalculatorTest {

    @Test
    void invertsPushInputToPullOutput() {
        assertEquals(
                GravityPowderSpecialStateStore.STATE_PULL,
                InverterStateCalculator.invertMode(GravityPowderSpecialStateStore.STATE_PUSH)
        );
    }

    @Test
    void invertsPullInputToPushOutput() {
        assertEquals(
                GravityPowderSpecialStateStore.STATE_PUSH,
                InverterStateCalculator.invertMode(GravityPowderSpecialStateStore.STATE_PULL)
        );
    }
}
