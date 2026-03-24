package dev.moxinat.forcesofgravium.registry;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConnectableBlockRolesTest {

    @Test
    void recognizesConfiguredSourceBlocks() {
        assertTrue(ConnectableBlockRoles.isSource("Rock_Crystal_Blue_Block"));
    }

    @Test
    void rejectsUnknownSourceAndMachineBlocks() {
        assertFalse(ConnectableBlockRoles.isSource("Unknown_Block"));
        assertFalse(ConnectableBlockRoles.isMachine("Unknown_Block"));
    }
}
