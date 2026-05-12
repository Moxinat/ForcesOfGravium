package dev.moxinat.forcesofgravium.data;

import dev.moxinat.forcesofgravium.data.GravityPowderBlockDataStore.GravityPowderBlockData;
import dev.moxinat.forcesofgravium.data.InverterDataStore.InverterData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HybridStateInitializationTest {

    @Test
    void gravityPowderDefaultInitializesAllStatesFromInstantState() {
        GravityPowderBlockData data = GravityPowderBlockData.defaultData();

        assertEquals(GravityPowderBlockDataStore.STATE_OFF, data.instantState());
        assertEquals(GravityPowderBlockDataStore.STATE_OFF, data.waveState());
        assertEquals(GravityPowderBlockDataStore.STATE_OFF, data.effectiveState());
        assertEquals(GravityPowderBlockDataStore.STATE_OFF, data.previousState());
    }

    @Test
    void gravityPowderStateFactoryInitializesAllStatesFromInstantState() {
        GravityPowderBlockData data = GravityPowderBlockDataStore.fromState(7, GravityPowderBlockDataStore.STATE_PULL);

        assertEquals(GravityPowderBlockDataStore.STATE_PULL, data.instantState());
        assertEquals(GravityPowderBlockDataStore.STATE_PULL, data.waveState());
        assertEquals(GravityPowderBlockDataStore.STATE_PULL, data.effectiveState());
        assertEquals(GravityPowderBlockDataStore.STATE_PULL, data.previousState());
    }

    @Test
    void gravityPowderInstantStateTransitionUpdatesPreviousWaveAndEffectiveStates() {
        GravityPowderBlockData push = GravityPowderBlockData.defaultData().withInstantState(GravityPowderBlockDataStore.STATE_PUSH);
        GravityPowderBlockData pull = push.withInstantState(GravityPowderBlockDataStore.STATE_PULL);

        assertEquals(GravityPowderBlockDataStore.STATE_PUSH, push.instantState());
        assertEquals(GravityPowderBlockDataStore.STATE_OFF, push.waveState());
        assertEquals(GravityPowderBlockDataStore.STATE_OFF, push.effectiveState());
        assertEquals(GravityPowderBlockDataStore.STATE_OFF, push.previousState());
        assertTrue(push.hasWaveMismatch());

        assertEquals(GravityPowderBlockDataStore.STATE_PULL, pull.instantState());
        assertEquals(GravityPowderBlockDataStore.STATE_OFF, pull.waveState());
        assertEquals(GravityPowderBlockDataStore.STATE_OFF, pull.effectiveState());
        assertEquals(GravityPowderBlockDataStore.STATE_OFF, pull.previousState());
        assertTrue(pull.hasWaveMismatch());
    }

    @Test
    void gravityPowderCanAdoptInstantStateIntoWaveState() {
        GravityPowderBlockData transitioned = GravityPowderBlockData.defaultData().withInstantState(GravityPowderBlockDataStore.STATE_PUSH);
        GravityPowderBlockData adopted = transitioned.withWaveStateFromInstantState();

        assertEquals(GravityPowderBlockDataStore.STATE_PUSH, adopted.instantState());
        assertEquals(GravityPowderBlockDataStore.STATE_PUSH, adopted.waveState());
        assertEquals(GravityPowderBlockDataStore.STATE_PUSH, adopted.effectiveState());
        assertEquals(GravityPowderBlockDataStore.STATE_PUSH, adopted.previousState());
        assertFalse(adopted.hasWaveMismatch());
    }

    @Test
    void gravityPowderEffectiveStateIsDerivedFromWaveInstantAndPrevious() {
        GravityPowderBlockData data = new GravityPowderBlockData(
                0,
                new StateTimeline(
                        GravityPowderBlockDataStore.STATE_PUSH,
                        GravityPowderBlockDataStore.STATE_OFF,
                        GravityPowderBlockDataStore.STATE_OFF
                )
        );

        assertEquals(GravityPowderBlockDataStore.STATE_OFF, data.effectiveState());
    }

    @Test
    void inverterDefaultInitializesAllStatesFromCurrentMode() {
        InverterData data = InverterDataStore.InverterData.defaultData();

        assertEquals("off", data.currentMode());
        assertEquals("off", data.waveState());
        assertEquals("off", data.effectiveState());
        assertEquals("off", data.previousState());
    }

    @Test
    void inverterTransitionKeepsPreviousStateUntilCurrentModeChanges() {
        InverterData initialized = InverterData.initialize("off", "off", true, false);
        InverterData unchanged = InverterData.transition(initialized, "off", "pull", true, true);
        InverterData changed = InverterData.transition(unchanged, "push", "push", false, true);

        assertEquals("off", unchanged.currentMode());
        assertEquals("off", unchanged.waveState());
        assertEquals("off", unchanged.effectiveState());
        assertEquals("off", unchanged.previousState());

        assertEquals("push", changed.currentMode());
        assertEquals("push", changed.waveState());
        assertEquals("push", changed.effectiveState());
        assertEquals("off", changed.previousState());
    }

    @Test
    void inverterCanAdoptCurrentModeIntoWaveState() {
        InverterData transitioned = InverterData.transition(InverterData.defaultData(), "pull", "push", false, true);
        InverterData adopted = transitioned.withWaveStateFromCurrentMode();

        assertEquals("pull", adopted.currentMode());
        assertEquals("push", adopted.nextMode());
        assertEquals("pull", adopted.waveState());
        assertEquals("pull", adopted.effectiveState());
        assertEquals("pull", adopted.previousState());
    }

    @Test
    void inverterEffectiveStateIsDerivedFromWaveCurrentAndPrevious() {
        InverterData data = new InverterData(
                "push",
                "pull",
                true,
                false,
                new StateTimeline("push", "off", "off")
        );

        assertEquals("off", data.effectiveState());
    }
}
