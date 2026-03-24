package dev.moxinat.forcesofgravium.registry;

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
    void treatsUnknownBlocksAsNotConnectable() {
        assertFalse(ConnectableRegistry.isConnectable("Unknown_Block"));
        assertEquals(0, ConnectableRegistry.getConnectableSidesMask("Unknown_Block"));
    }
}
