package dev.moxinat.forcesofgravium.connectable.registry;

import dev.moxinat.forcesofgravium.connectable.registry.ConnectableBlockRoles;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConnectableBlockRolesTest {

    @Test
    void recognizesConfiguredSourceBlocks() {
        assertTrue(ConnectableBlockRoles.isSource("WindGenerator_Block"));
        assertTrue(ConnectableBlockRoles.isSource("Wooden_Button_Block"));
        assertTrue(ConnectableBlockRoles.isSource("*Wooden_Button_Block_State_Pressed"));
        assertTrue(ConnectableBlockRoles.isSource("*Wooden_Button_Block_State_PressedAlt"));
    }

    @Test
    void rejectsUnknownSourceAndMachineBlocks() {
        assertFalse(ConnectableBlockRoles.isSource("Unknown_Block"));
        assertFalse(ConnectableBlockRoles.isConsumer("Unknown_Block"));
    }
}
