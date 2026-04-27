package dev.moxinat.forcesofgravium.data;

import dev.moxinat.forcesofgravium.registry.ConnectableRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SourceBlockDataStoreTest {

    @Test
    void windGeneratorDefaultsToActive() {
        assertTrue(SourceBlockDataStore.defaultActiveForBlockId(ConnectableRegistry.WIND_GENERATOR_BLOCK_ID));
    }

    @Test
    void woodenButtonDefaultsToInactive() {
        assertFalse(SourceBlockDataStore.defaultActiveForBlockId(ConnectableRegistry.WOODEN_BUTTON_BLOCK_ID));
        assertFalse(SourceBlockDataStore.defaultActiveForBlockId("*Wooden_Button_Block_State_Pressed"));
    }

    @Test
    void unknownSourcesDefaultToInactive() {
        assertFalse(SourceBlockDataStore.defaultActiveForBlockId("Unknown_Block"));
    }
}
