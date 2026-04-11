package dev.moxinat.forcesofgravium.logic.network;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ConnectablePropagationSchedulerTest {

    @Test
    void treatsNullBlockTypeAsNotGravityPowder() {
        assertTrue(ConnectablePropagationScheduler.isNotGravityPowder(null));
    }

    @Test
    void treatsNullBlockTypeAsNotInverter() {
        assertTrue(ConnectablePropagationScheduler.isNotInverter(null));
    }
}
