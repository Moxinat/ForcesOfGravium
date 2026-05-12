package dev.moxinat.forcesofgravium.logic.inverter;

import dev.moxinat.forcesofgravium.data.GravityPowderBlockDataStore;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InverterStateCalculatorTest {

    @Test
    void invertsPushInputToPullOutput() {
        assertEquals(
                GravityPowderBlockDataStore.STATE_PULL,
                InverterStateCalculator.invertMode(GravityPowderBlockDataStore.STATE_PUSH)
        );
    }

    @Test
    void invertsPullInputToPushOutput() {
        assertEquals(
                GravityPowderBlockDataStore.STATE_PUSH,
                InverterStateCalculator.invertMode(GravityPowderBlockDataStore.STATE_PULL)
        );
    }
}
