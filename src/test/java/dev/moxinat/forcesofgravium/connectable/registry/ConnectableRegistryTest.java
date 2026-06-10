package dev.moxinat.forcesofgravium.connectable.registry;

import dev.moxinat.forcesofgravium.connectable.registry.ConnectableRegistry;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConnectableRegistryTest {

    @Test
    void recognizesGravityPowderStateIdsAsConnectable() {
        assertTrue(ConnectableRegistry.isGravityPowderId("*Gravity_Powder_Default_State_StraightPush"));
        assertTrue(ConnectableRegistry.isConnectable("*Gravity_Powder_Default_State_StraightPush"));
    }

    @Test
    void returnsAllSidesMaskForInverter() {
        assertEquals(ConnectableRegistry.ALL_SIDES_MASK, ConnectableRegistry.getConnectableSidesMask(ConnectableRegistry.INVERTER_BLOCK_ID));
    }

    @Test
    void graviumSiphonConnectsOnEverySideExceptFrontAndBack() {
        assertTrue(ConnectableRegistry.isConnectable(ConnectableRegistry.GRAVIUM_SIPHON_BLOCK_ID));
        assertFalse(ConnectableRegistry.isConnectableOnSide(ConnectableRegistry.GRAVIUM_SIPHON_BLOCK_ID, ConnectableRegistry.SIDE_FRONT));
        assertFalse(ConnectableRegistry.isConnectableOnSide(ConnectableRegistry.GRAVIUM_SIPHON_BLOCK_ID, ConnectableRegistry.SIDE_BACK));
        assertTrue(ConnectableRegistry.isConnectableOnSide(ConnectableRegistry.GRAVIUM_SIPHON_BLOCK_ID, ConnectableRegistry.SIDE_RIGHT));
        assertTrue(ConnectableRegistry.isConnectableOnSide(ConnectableRegistry.GRAVIUM_SIPHON_BLOCK_ID, ConnectableRegistry.SIDE_LEFT));
        assertTrue(ConnectableRegistry.isConnectableOnSide(ConnectableRegistry.GRAVIUM_SIPHON_BLOCK_ID, ConnectableRegistry.SIDE_TOP));
        assertTrue(ConnectableRegistry.isConnectableOnSide(ConnectableRegistry.GRAVIUM_SIPHON_BLOCK_ID, ConnectableRegistry.SIDE_BOTTOM));
        assertEquals(ConnectableRegistry.SIDES_EXCEPT_FRONT_BACK_MASK, ConnectableRegistry.getConnectableSidesMask(ConnectableRegistry.GRAVIUM_SIPHON_BLOCK_ID));
    }

    @Test
    void recognizesWoodenButtonStateIdsAsConnectable() {
        assertTrue(ConnectableRegistry.isWoodenButtonId("*Wooden_Button_Block_State_Pressed"));
        assertTrue(ConnectableRegistry.isWoodenButtonId("*Wooden_Button_Block_State_PressedAlt"));
        assertTrue(ConnectableRegistry.isConnectable("*Wooden_Button_Block_State_Pressed"));
        assertEquals(ConnectableRegistry.ALL_SIDES_MASK, ConnectableRegistry.getConnectableSidesMask(ConnectableRegistry.WOODEN_BUTTON_BLOCK_ID));
    }

    @Test
    void treatsUnknownBlocksAsNotConnectable() {
        assertFalse(ConnectableRegistry.isConnectable("Unknown_Block"));
        assertEquals(0, ConnectableRegistry.getConnectableSidesMask("Unknown_Block"));
    }
}
