package dev.moxinat.forcesofgravium.data;

import dev.moxinat.forcesofgravium.block.inverter.InverterSpecialStateStore;

import dev.moxinat.forcesofgravium.block.gravity.GravityPowderSpecialStateStore;

import dev.moxinat.forcesofgravium.block.gravity.GravityPowderSpecialStateStore.GravityPowderBlockData;
import dev.moxinat.forcesofgravium.block.inverter.InverterSpecialStateStore.InverterData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HybridStateInitializationTest {

    @Test
    void gravityPowderDefaultInitializesAllStatesFromInstantState() {
        GravityPowderBlockData data = GravityPowderBlockData.defaultData();

        assertEquals(GravityPowderSpecialStateStore.STATE_OFF, data.instantState());
        assertEquals(GravityPowderSpecialStateStore.STATE_OFF, data.waveState());
        assertEquals(GravityPowderSpecialStateStore.STATE_OFF, data.effectiveState());
        assertEquals(GravityPowderSpecialStateStore.STATE_OFF, data.previousState());
    }

    @Test
    void gravityPowderStateFactoryInitializesAllStatesFromInstantState() {
        GravityPowderBlockData data = GravityPowderSpecialStateStore.fromState(7, GravityPowderSpecialStateStore.STATE_PULL);

        assertEquals(GravityPowderSpecialStateStore.STATE_PULL, data.instantState());
        assertEquals(GravityPowderSpecialStateStore.STATE_PULL, data.waveState());
        assertEquals(GravityPowderSpecialStateStore.STATE_PULL, data.effectiveState());
        assertEquals(GravityPowderSpecialStateStore.STATE_PULL, data.previousState());
    }

    @Test
    void gravityPowderInstantStateTransitionUpdatesPreviousWaveAndEffectiveStates() {
        GravityPowderBlockData push = GravityPowderBlockData.defaultData().withInstantState(GravityPowderSpecialStateStore.STATE_PUSH);
        GravityPowderBlockData pull = push.withInstantState(GravityPowderSpecialStateStore.STATE_PULL);

        assertEquals(GravityPowderSpecialStateStore.STATE_PUSH, push.instantState());
        assertEquals(GravityPowderSpecialStateStore.STATE_OFF, push.waveState());
        assertEquals(GravityPowderSpecialStateStore.STATE_OFF, push.effectiveState());
        assertEquals(GravityPowderSpecialStateStore.STATE_OFF, push.previousState());
        assertTrue(push.hasWaveMismatch());

        assertEquals(GravityPowderSpecialStateStore.STATE_PULL, pull.instantState());
        assertEquals(GravityPowderSpecialStateStore.STATE_OFF, pull.waveState());
        assertEquals(GravityPowderSpecialStateStore.STATE_OFF, pull.effectiveState());
        assertEquals(GravityPowderSpecialStateStore.STATE_OFF, pull.previousState());
        assertTrue(pull.hasWaveMismatch());
    }

    @Test
    void gravityPowderCanAdoptInstantStateIntoWaveState() {
        GravityPowderBlockData transitioned = GravityPowderBlockData.defaultData().withInstantState(GravityPowderSpecialStateStore.STATE_PUSH);
        GravityPowderBlockData adopted = transitioned.withWaveStateFromInstantState();

        assertEquals(GravityPowderSpecialStateStore.STATE_PUSH, adopted.instantState());
        assertEquals(GravityPowderSpecialStateStore.STATE_PUSH, adopted.waveState());
        assertEquals(GravityPowderSpecialStateStore.STATE_PUSH, adopted.effectiveState());
        assertEquals(GravityPowderSpecialStateStore.STATE_PUSH, adopted.previousState());
        assertFalse(adopted.hasWaveMismatch());
    }

    @Test
    void gravityPowderEffectiveStateIsDerivedFromWaveInstantAndPrevious() {
        GravityPowderBlockData data = new GravityPowderBlockData(
                0,
                new StateTimeline(
                        GravityPowderSpecialStateStore.STATE_PUSH,
                        GravityPowderSpecialStateStore.STATE_OFF,
                        GravityPowderSpecialStateStore.STATE_OFF
                )
        );

        assertEquals(GravityPowderSpecialStateStore.STATE_OFF, data.effectiveState());
    }

    @Test
    void inverterDefaultInitializesAllStatesFromCurrentMode() {
        InverterData data = InverterSpecialStateStore.InverterData.defaultData();

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
