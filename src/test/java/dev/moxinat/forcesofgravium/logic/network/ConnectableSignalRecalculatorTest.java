package dev.moxinat.forcesofgravium.logic.network;

import org.joml.Vector3i;
import dev.moxinat.forcesofgravium.data.GravityPowderBlockDataStore;
import org.junit.jupiter.api.Test;

import javax.annotation.Nonnull;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConnectableSignalRecalculatorTest {

    @Test
    void propagatesPushFromSourceThroughCableChain() {
        Vector3i first = new Vector3i(0, 0, 0);
        Vector3i second = new Vector3i(1, 0, 0);
        TestAdapter adapter = new TestAdapter(Set.of(first, second), Set.of())
                .withCableSource(first);

        ConnectableSignalRecalculator.recompute(adapter);

        assertEquals(SignalState.PUSH, adapter.cableSignal(first));
        assertEquals(SignalState.PUSH, adapter.cableSignal(second));
    }

    @Test
    void inverterFlipsPushInputToPullOutput() {
        Vector3i inverter = new Vector3i(0, 0, 0);
        Vector3i outputCable = new Vector3i(1, 0, 0);
        TestAdapter adapter = new TestAdapter(Set.of(outputCable), Set.of(inverter))
                .withInverterSides(inverter, new Vector3i(-1, 0, 0), outputCable)
                .withInverterBackSource(inverter);

        ConnectableSignalRecalculator.recompute(adapter);

        assertEquals(GravityPowderBlockDataStore.STATE_PULL, adapter.inverterMode(inverter));
        assertEquals(SignalState.PULL, adapter.cableSignal(outputCable));
    }

    @Test
    void disabledInverterPassesSignalWithoutFlipping() {
        Vector3i inverter = new Vector3i(0, 0, 0);
        Vector3i outputCable = new Vector3i(1, 0, 0);
        TestAdapter adapter = new TestAdapter(Set.of(outputCable), Set.of(inverter))
                .withInverterSides(inverter, new Vector3i(-1, 0, 0), outputCable)
                .withInverterBackSource(inverter)
                .withInvertEnabled(inverter, false);

        ConnectableSignalRecalculator.recompute(adapter);

        assertEquals(GravityPowderBlockDataStore.STATE_PUSH, adapter.inverterMode(inverter));
        assertEquals(SignalState.PUSH, adapter.cableSignal(outputCable));
    }

    @Test
    void sideSignalTogglesInverterOnlyOnRisingEdge() {
        Vector3i inputCable = new Vector3i(0, 0, 0);
        Vector3i inverter = new Vector3i(1, 0, 0);
        Vector3i outputCable = new Vector3i(2, 0, 0);
        Vector3i sideCable = new Vector3i(1, 1, 0);
        TestAdapter adapter = new TestAdapter(Set.of(inputCable, outputCable, sideCable), Set.of(inverter))
                .withCableSource(inputCable)
                .withCableSource(sideCable)
                .withInverterSides(inverter, inputCable, outputCable);

        ConnectableSignalRecalculator.recompute(adapter);

        assertFalse(adapter.invertEnabled(inverter));
        assertTrue(adapter.toggleInputActive(inverter));
        assertEquals(GravityPowderBlockDataStore.STATE_PUSH, adapter.inverterMode(inverter));
        assertEquals(SignalState.PUSH, adapter.cableSignal(outputCable));

        ConnectableSignalRecalculator.recompute(adapter);

        assertFalse(adapter.invertEnabled(inverter));
        assertTrue(adapter.toggleInputActive(inverter));
        assertEquals(GravityPowderBlockDataStore.STATE_PUSH, adapter.inverterMode(inverter));

        adapter.withoutCableSource(sideCable);
        ConnectableSignalRecalculator.recompute(adapter);

        assertFalse(adapter.invertEnabled(inverter));
        assertFalse(adapter.toggleInputActive(inverter));
        assertEquals(GravityPowderBlockDataStore.STATE_PUSH, adapter.inverterMode(inverter));

        adapter.withCableSource(sideCable);
        ConnectableSignalRecalculator.recompute(adapter);

        assertTrue(adapter.invertEnabled(inverter));
        assertTrue(adapter.toggleInputActive(inverter));
        assertEquals(GravityPowderBlockDataStore.STATE_PULL, adapter.inverterMode(inverter));
        assertEquals(SignalState.PULL, adapter.cableSignal(outputCable));
    }

    @Test
    void cableStoresPushWhenPushAndPullConflict() {
        Vector3i pushCable = new Vector3i(0, 0, 0);
        Vector3i shared = new Vector3i(1, 0, 0);
        Vector3i inverter = new Vector3i(2, 0, 0);
        TestAdapter adapter = new TestAdapter(Set.of(pushCable, shared), Set.of(inverter))
                .withCableSource(pushCable)
                .withInverterSides(inverter, new Vector3i(2, 0, -1), shared)
                .withInverterBackSource(inverter);

        ConnectableSignalRecalculator.recompute(adapter);

        assertEquals(SignalState.PUSH, adapter.cableSignal(shared));
        assertEquals(GravityPowderBlockDataStore.STATE_PUSH, adapter.cableMode(shared));
    }

    @Test
    void recomputeWithoutSourcesResetsStoredSignals() {
        Vector3i cable = new Vector3i(0, 0, 0);
        TestAdapter adapter = new TestAdapter(Set.of(cable), Set.of());
        adapter.setCableSignal(cable, SignalState.PUSH);

        ConnectableSignalRecalculator.recompute(adapter);

        assertEquals(SignalState.OFF, adapter.cableSignal(cable));
        assertEquals(GravityPowderBlockDataStore.STATE_OFF, adapter.cableMode(cable));
    }

    @Test
    void placingBridgeCableActivatesNewlyConnectedNetworkOnRecompute() {
        Vector3i sourceCable = new Vector3i(0, 0, 0);
        Vector3i bridgeCable = new Vector3i(1, 0, 0);
        Vector3i networkCable = new Vector3i(2, 0, 0);
        TestAdapter adapter = new TestAdapter(Set.of(sourceCable, networkCable), Set.of())
                .withCableSource(sourceCable);

        ConnectableSignalRecalculator.recompute(adapter);

        assertEquals(SignalState.OFF, adapter.cableSignal(networkCable));

        adapter.addCable(bridgeCable);
        ConnectableSignalRecalculator.recompute(adapter);

        assertEquals(SignalState.PUSH, adapter.cableSignal(sourceCable));
        assertEquals(SignalState.PUSH, adapter.cableSignal(bridgeCable));
        assertEquals(SignalState.PUSH, adapter.cableSignal(networkCable));
    }

    @Test
    void inverterIgnoresSignalFromFrontSide() {
        Vector3i inputCable = new Vector3i(0, 0, 0);
        Vector3i inverter = new Vector3i(1, 0, 0);
        Vector3i outputCable = new Vector3i(2, 0, 0);
        TestAdapter adapter = new TestAdapter(Set.of(inputCable, outputCable), Set.of(inverter))
                .withCableSource(inputCable)
                .withInverterSides(inverter, inputCable, outputCable);

        ConnectableSignalRecalculator.recompute(adapter);

        assertEquals(SignalState.PUSH, adapter.cableSignal(inputCable));
        assertEquals(GravityPowderBlockDataStore.STATE_PULL, adapter.inverterMode(inverter));
        assertEquals(SignalState.PULL, adapter.cableSignal(outputCable));

        adapter.withInverterSides(inverter, outputCable, inputCable);
        ConnectableSignalRecalculator.recompute(adapter);

        assertEquals(SignalState.PUSH, adapter.cableSignal(inputCable));
        assertEquals(GravityPowderBlockDataStore.STATE_OFF, adapter.inverterMode(inverter));
        assertEquals(SignalState.OFF, adapter.cableSignal(outputCable));
    }

    private static final class TestAdapter implements ConnectableSignalRecalculator.SignalAdapter {
        private final Set<Vector3i> cables;
        private final Set<Vector3i> inverters;
        private final Set<Vector3i> cableSources = new LinkedHashSet<>();
        private final Set<Vector3i> inverterBackSources = new LinkedHashSet<>();
        private final Map<Vector3i, Vector3i> inverterBacks = new LinkedHashMap<>();
        private final Map<Vector3i, Vector3i> inverterFronts = new LinkedHashMap<>();
        private final Map<Vector3i, SignalState> cableSignals = new LinkedHashMap<>();
        private final Map<Vector3i, String> inverterModes = new LinkedHashMap<>();
        private final Map<Vector3i, Boolean> invertEnabled = new LinkedHashMap<>();
        private final Map<Vector3i, Boolean> toggleInputActive = new LinkedHashMap<>();

        private TestAdapter(Set<Vector3i> cables, Set<Vector3i> inverters) {
            this.cables = new LinkedHashSet<>(cables);
            this.inverters = new LinkedHashSet<>(inverters);
        }

        private TestAdapter addCable(Vector3i cable) {
            cables.add(cable);
            return this;
        }

        private TestAdapter withCableSource(Vector3i cable) {
            cableSources.add(cable);
            return this;
        }

        private TestAdapter withoutCableSource(Vector3i cable) {
            cableSources.remove(cable);
            return this;
        }

        private TestAdapter withInverterBackSource(Vector3i inverter) {
            inverterBackSources.add(inverter);
            return this;
        }

        private TestAdapter withInvertEnabled(Vector3i inverter, boolean value) {
            invertEnabled.put(inverter, value);
            return this;
        }

        private TestAdapter withInverterSides(Vector3i inverter, Vector3i back, Vector3i front) {
            inverterBacks.put(inverter, back);
            inverterFronts.put(inverter, front);
            return this;
        }

        private SignalState cableSignal(Vector3i cable) {
            return cableSignals.getOrDefault(cable, SignalState.OFF);
        }

        private String cableMode(Vector3i cable) {
            return switch (cableSignal(cable)) {
                case PUSH -> GravityPowderBlockDataStore.STATE_PUSH;
                case PULL -> GravityPowderBlockDataStore.STATE_PULL;
                case OFF -> GravityPowderBlockDataStore.STATE_OFF;
            };
        }

        private String inverterMode(Vector3i inverter) {
            return inverterModes.getOrDefault(inverter, GravityPowderBlockDataStore.STATE_OFF);
        }

        private boolean invertEnabled(Vector3i inverter) {
            return invertEnabled.getOrDefault(inverter, true);
        }

        private boolean toggleInputActive(Vector3i inverter) {
            return toggleInputActive.getOrDefault(inverter, false);
        }

        @Override
        public @Nonnull Set<Vector3i> cablePositions() {
            return Set.copyOf(cables);
        }

        @Override
        public @Nonnull Set<Vector3i> inverterPositions() {
            return Set.copyOf(inverters);
        }

        @Override
        public boolean isCable(@Nonnull Vector3i position) {
            return cables.contains(position);
        }

        @Override
        public boolean isInverter(@Nonnull Vector3i position) {
            return inverters.contains(position);
        }

        @Override
        public boolean hasAdjacentSourceForCable(@Nonnull Vector3i cable) {
            return cableSources.contains(cable);
        }

        @Override
        public boolean hasSourceAtInverterBack(@Nonnull Vector3i inverter) {
            return inverterBackSources.contains(inverter);
        }

        @Override
        public boolean hasSourceFacingPosition(@Nonnull Vector3i sourcePosition, @Nonnull Vector3i targetPosition) {
            return false;
        }

        @Override
        public boolean isInvertEnabled(@Nonnull Vector3i inverter) {
            return invertEnabled(inverter);
        }

        @Override
        public boolean isToggleInputActive(@Nonnull Vector3i inverter) {
            return toggleInputActive(inverter);
        }

        @Override
        public @Nonnull Vector3i inverterBack(@Nonnull Vector3i inverter) {
            return inverterBacks.getOrDefault(inverter, inverter);
        }

        @Override
        public @Nonnull Vector3i inverterFront(@Nonnull Vector3i inverter) {
            return inverterFronts.getOrDefault(inverter, inverter);
        }

        @Override
        public @Nonnull List<Vector3i> positionsAround(@Nonnull Vector3i position) {
            return ConnectableNeighborResolver.positionsAround(position);
        }

        @Override
        public void setCableSignal(@Nonnull Vector3i position, @Nonnull SignalState mode) {
            cableSignals.put(position, mode);
        }

        @Override
        public void setInverterState(@Nonnull Vector3i position, @Nonnull String mode, boolean nextInvertEnabled, boolean nextToggleInputActive) {
            inverterModes.put(position, mode);
            invertEnabled.put(position, nextInvertEnabled);
            toggleInputActive.put(position, nextToggleInputActive);
        }
    }
}
