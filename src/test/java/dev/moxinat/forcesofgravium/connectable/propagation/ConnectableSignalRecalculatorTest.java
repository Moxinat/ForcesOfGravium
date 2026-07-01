package dev.moxinat.forcesofgravium.connectable.propagation;

import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import dev.moxinat.forcesofgravium.connectable.core.ConnectableDefinition;
import dev.moxinat.forcesofgravium.connectable.core.ConnectableDefinitions;
import dev.moxinat.forcesofgravium.connectable.core.ConnectableRuntimeData;
import dev.moxinat.forcesofgravium.connectable.registry.ConnectableRegistry;
import dev.moxinat.forcesofgravium.connectable.spatial.ConnectableNeighborResolver;
import org.joml.Vector3i;
import org.junit.jupiter.api.Test;

import javax.annotation.Nonnull;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConnectableSignalRecalculatorTest {

    @Test
    void buttonProducesPushThroughGravityPowderChain() {
        Vector3i first = new Vector3i(0, 0, 0);
        Vector3i second = new Vector3i(1, 0, 0);
        TestAdapter adapter = new TestAdapter()
                .withNode(first, ConnectableRegistry.GRAVITY_POWDER_BLOCK_ID)
                .withNode(second, ConnectableRegistry.GRAVITY_POWDER_BLOCK_ID)
                .withSourceSignal(new Vector3i(-1, 0, 0), first);

        ConnectableSignalRecalculator.recompute(adapter);

        assertEquals(SignalState.PUSH, adapter.signal(first));
        assertEquals(SignalState.PUSH, adapter.signal(second));
    }

    @Test
    void windGeneratorProducesPushThroughGravityPowder() {
        Vector3i powder = new Vector3i(0, 0, 0);
        TestAdapter adapter = new TestAdapter()
                .withNode(powder, ConnectableRegistry.GRAVITY_POWDER_BLOCK_ID)
                .withSourceSignal(new Vector3i(0, 0, -1), powder);

        ConnectableSignalRecalculator.recompute(adapter);

        assertEquals(SignalState.PUSH, adapter.signal(powder));
    }

    @Test
    void straightCasedCableOnlyPropagatesThroughDefinedSides() {
        Vector3i cased = new Vector3i(0, 0, 0);
        Vector3i front = new Vector3i(0, 0, 1);
        Vector3i side = new Vector3i(1, 0, 0);
        TestAdapter adapter = new TestAdapter()
                .withNode(cased, ConnectableRegistry.STRAIGHT_CASED_GRAVITY_POWDER_BLOCK_ID)
                .withNode(front, ConnectableRegistry.GRAVITY_POWDER_BLOCK_ID)
                .withNode(side, ConnectableRegistry.GRAVITY_POWDER_BLOCK_ID)
                .withSourceSignal(new Vector3i(0, 0, -1), cased);

        ConnectableSignalRecalculator.recompute(adapter);

        assertEquals(SignalState.PUSH, adapter.signal(cased));
        assertEquals(SignalState.PUSH, adapter.signal(front));
        assertEquals(SignalState.OFF, adapter.signal(side));
    }

    @Test
    void curveCasedCableOnlyPropagatesThroughDefinedSides() {
        Vector3i cased = new Vector3i(0, 0, 0);
        Vector3i bottom = new Vector3i(0, -1, 0);
        Vector3i side = new Vector3i(1, 0, 0);
        TestAdapter adapter = new TestAdapter()
                .withNode(cased, ConnectableRegistry.CURVE_CASED_GRAVITY_POWDER_BLOCK_ID)
                .withNode(bottom, ConnectableRegistry.GRAVITY_POWDER_BLOCK_ID)
                .withNode(side, ConnectableRegistry.GRAVITY_POWDER_BLOCK_ID)
                .withSourceSignal(new Vector3i(0, 0, -1), cased);

        ConnectableSignalRecalculator.recompute(adapter);

        assertEquals(SignalState.PUSH, adapter.signal(cased));
        assertEquals(SignalState.PUSH, adapter.signal(bottom));
        assertEquals(SignalState.OFF, adapter.signal(side));
    }

    @Test
    void inverterBackInputOutputsFrontThroughGenericSides() {
        Vector3i input = new Vector3i(0, 0, -1);
        Vector3i inverter = new Vector3i(0, 0, 0);
        Vector3i output = new Vector3i(0, 0, 1);
        TestAdapter adapter = new TestAdapter()
                .withNode(input, ConnectableRegistry.GRAVITY_POWDER_BLOCK_ID, runtime(false, true, SignalState.OFF, SignalState.PUSH))
                .withNode(inverter, ConnectableRegistry.INVERTER_BLOCK_ID)
                .withNode(output, ConnectableRegistry.GRAVITY_POWDER_BLOCK_ID)
                .withSourceSignal(new Vector3i(0, 0, -2), input);

        ConnectableSignalRecalculator.recompute(adapter);

        assertEquals(SignalState.PUSH, adapter.signal(input));
        assertEquals(SignalState.PULL, adapter.signal(inverter));
        assertEquals(SignalState.PULL, adapter.signal(output));
    }

    @Test
    void newlyPlacedBackInputActivatesInverterFrontOnNextRecompute() {
        Vector3i input = new Vector3i(0, 0, -1);
        Vector3i inverter = new Vector3i(0, 0, 0);
        Vector3i output = new Vector3i(0, 0, 1);
        TestAdapter adapter = new TestAdapter()
                .withNode(inverter, ConnectableRegistry.INVERTER_BLOCK_ID)
                .withNode(output, ConnectableRegistry.GRAVITY_POWDER_BLOCK_ID);

        ConnectableSignalRecalculator.recompute(adapter);
        assertEquals(SignalState.OFF, adapter.signal(inverter));
        assertEquals(SignalState.OFF, adapter.signal(output));

        adapter.withNode(input, ConnectableRegistry.GRAVITY_POWDER_BLOCK_ID, runtime(false, true, SignalState.OFF, SignalState.PUSH))
                .withSourceSignal(new Vector3i(0, 0, -2), input);
        ConnectableSignalRecalculator.recompute(adapter);

        assertEquals(SignalState.PUSH, adapter.signal(input));
        assertEquals(SignalState.PULL, adapter.signal(inverter));
        assertEquals(SignalState.PULL, adapter.signal(output));
        assertTrue(adapter.dirtyMarked(input));
        assertTrue(adapter.dirtyMarked(inverter));
        assertTrue(adapter.dirtyMarked(output));
    }

    @Test
    void brokenBackNodeRecomputesInverterOutputThroughGenericPropagation() {
        Vector3i input = new Vector3i(0, 0, -1);
        Vector3i inverter = new Vector3i(0, 0, 0);
        Vector3i output = new Vector3i(0, 0, 1);
        TestAdapter adapter = new TestAdapter()
                .withNode(input, ConnectableRegistry.GRAVITY_POWDER_BLOCK_ID, runtime(false, true, SignalState.OFF, SignalState.PUSH))
                .withNode(inverter, ConnectableRegistry.INVERTER_BLOCK_ID)
                .withNode(output, ConnectableRegistry.GRAVITY_POWDER_BLOCK_ID)
                .withSourceSignal(new Vector3i(0, 0, -2), input);

        ConnectableSignalRecalculator.recompute(adapter);
        assertEquals(SignalState.PULL, adapter.signal(inverter));
        assertEquals(SignalState.PULL, adapter.signal(output));

        adapter.removeNode(input);
        ConnectableSignalRecalculator.recompute(adapter);

        assertEquals(SignalState.OFF, adapter.signal(inverter));
        assertEquals(SignalState.OFF, adapter.signal(output));
    }

    @Test
    void invertEnabledControlsGenericSignalTransform() {
        Vector3i input = new Vector3i(0, 0, -1);
        Vector3i inverter = new Vector3i(0, 0, 0);
        Vector3i output = new Vector3i(0, 0, 1);
        TestAdapter adapter = new TestAdapter()
                .withNode(input, ConnectableRegistry.GRAVITY_POWDER_BLOCK_ID, runtime(false, true, SignalState.OFF, SignalState.PUSH))
                .withNode(inverter, ConnectableRegistry.INVERTER_BLOCK_ID, runtime(true, true, SignalState.OFF, SignalState.OFF))
                .withNode(output, ConnectableRegistry.GRAVITY_POWDER_BLOCK_ID)
                .withSourceSignal(new Vector3i(0, 0, -2), input);

        ConnectableSignalRecalculator.recompute(adapter);
        assertEquals(SignalState.PULL, adapter.signal(output));

        adapter.withNode(inverter, ConnectableRegistry.INVERTER_BLOCK_ID, runtime(false, true, SignalState.OFF, SignalState.OFF));
        ConnectableSignalRecalculator.recompute(adapter);
        assertEquals(SignalState.PUSH, adapter.signal(inverter));
        assertEquals(SignalState.PUSH, adapter.signal(output));
    }

    @Test
    void sideControlTogglesInvertEnabledButDoesNotPassSignalThroughSide() {
        Vector3i input = new Vector3i(0, 0, -1);
        Vector3i inverter = new Vector3i(0, 0, 0);
        Vector3i output = new Vector3i(0, 0, 1);
        Vector3i control = new Vector3i(1, 0, 0);
        TestAdapter adapter = new TestAdapter()
                .withNode(input, ConnectableRegistry.GRAVITY_POWDER_BLOCK_ID, runtime(false, true, SignalState.OFF, SignalState.PUSH))
                .withNode(inverter, ConnectableRegistry.INVERTER_BLOCK_ID)
                .withNode(output, ConnectableRegistry.GRAVITY_POWDER_BLOCK_ID)
                .withNode(control, ConnectableRegistry.GRAVITY_POWDER_BLOCK_ID, runtime(false, true, SignalState.PUSH, SignalState.PUSH))
                .withSourceSignal(new Vector3i(0, 0, -2), input)
                .withSourceSignal(new Vector3i(2, 0, 0), control);

        ConnectableSignalRecalculator.recompute(adapter);

        assertFalse(adapter.invertEnabled(inverter));
        assertEquals(SignalState.PUSH, adapter.controlMemory(inverter));
        assertEquals(SignalState.PUSH, adapter.signal(output));

        ConnectableSignalRecalculator.recompute(adapter);
        assertFalse(adapter.invertEnabled(inverter));

        adapter.withNode(control, ConnectableRegistry.GRAVITY_POWDER_BLOCK_ID, runtime(false, true, SignalState.OFF, SignalState.OFF))
                .withoutSourceSignal(new Vector3i(2, 0, 0), control);
        ConnectableSignalRecalculator.recompute(adapter);
        assertFalse(adapter.invertEnabled(inverter));
        assertEquals(SignalState.OFF, adapter.controlMemory(inverter));

        adapter.withNode(control, ConnectableRegistry.GRAVITY_POWDER_BLOCK_ID, runtime(false, true, SignalState.OFF, SignalState.PUSH))
                .withSourceSignal(new Vector3i(2, 0, 0), control);
        ConnectableSignalRecalculator.recompute(adapter);
        assertTrue(adapter.invertEnabled(inverter));
        assertEquals(SignalState.PUSH, adapter.controlMemory(inverter));
    }

    @Test
    void directSourceControlStillTogglesInvertEnabled() {
        Vector3i input = new Vector3i(0, 0, -1);
        Vector3i inverter = new Vector3i(0, 0, 0);
        Vector3i output = new Vector3i(0, 0, 1);
        TestAdapter adapter = new TestAdapter()
                .withNode(input, ConnectableRegistry.GRAVITY_POWDER_BLOCK_ID, runtime(false, true, SignalState.OFF, SignalState.PUSH))
                .withNode(inverter, ConnectableRegistry.INVERTER_BLOCK_ID)
                .withNode(output, ConnectableRegistry.GRAVITY_POWDER_BLOCK_ID)
                .withSourceSignal(new Vector3i(0, 0, -2), input)
                .withSourceSignal(new Vector3i(1, 0, 0), inverter);

        ConnectableSignalRecalculator.recompute(adapter);

        assertFalse(adapter.invertEnabled(inverter));
        assertEquals(SignalState.PUSH, adapter.controlMemory(inverter));
        assertEquals(SignalState.PUSH, adapter.signal(output));
    }

    @Test
    void worldAdapterDoesNotRestrictNodesToAffectedPositions() throws Exception {
        String source = Files.readString(Path.of("src/main/java/dev/moxinat/forcesofgravium/connectable/propagation/ConnectableSignalRecalculator.java"));

        assertFalse(source.contains("retainAll(affectedPositions)"));
        assertFalse(source.contains("nodes.retainAll"));
    }

    @Test
    void instantBfsDoesNotGateSignalTraversalOnEffectiveState() throws Exception {
        String source = Files.readString(Path.of("src/main/java/dev/moxinat/forcesofgravium/connectable/propagation/ConnectableSignalRecalculator.java"));

        assertFalse(source.contains("adapter.effectiveSignal(source) != signal"));
    }

    @Test
    void recalculatorDoesNotScheduleWaveAdoptionForControlChanges() throws Exception {
        String source = Files.readString(Path.of("src/main/java/dev/moxinat/forcesofgravium/connectable/propagation/ConnectableSignalRecalculator.java"));

        assertFalse(source.contains("enqueueWaveAdoption"));
    }

    @Test
    void dirtyInstantSignalFeedsInvertCapableInputAndControl() {
        Vector3i input = new Vector3i(0, 0, -1);
        Vector3i inverter = new Vector3i(0, 0, 0);
        Vector3i output = new Vector3i(0, 0, 1);
        Vector3i control = new Vector3i(1, 0, 0);
        TestAdapter adapter = new TestAdapter()
                .withNode(input, ConnectableRegistry.GRAVITY_POWDER_BLOCK_ID, runtime(false, true, SignalState.PUSH, SignalState.OFF))
                .withNode(inverter, ConnectableRegistry.INVERTER_BLOCK_ID)
                .withNode(output, ConnectableRegistry.GRAVITY_POWDER_BLOCK_ID)
                .withNode(control, ConnectableRegistry.GRAVITY_POWDER_BLOCK_ID, runtime(false, true, SignalState.PUSH, SignalState.OFF))
                .withSourceSignal(new Vector3i(0, 0, -2), input)
                .withSourceSignal(new Vector3i(2, 0, 0), control);

        ConnectableSignalRecalculator.recompute(adapter);

        assertEquals(SignalState.PUSH, adapter.signal(input));
        assertEquals(SignalState.PUSH, adapter.signal(inverter));
        assertEquals(SignalState.PUSH, adapter.signal(output));
        assertFalse(adapter.invertEnabled(inverter));
        assertEquals(SignalState.PUSH, adapter.controlMemory(inverter));
        assertTrue(adapter.dirtyMarked(inverter));
        assertTrue(adapter.dirtyMarked(output));
    }

    @Test
    void siphonSignalInputDoesNotMakeItCarrier() {
        Vector3i siphon = new Vector3i(0, 0, 0);
        Vector3i output = new Vector3i(1, 0, 0);
        TestAdapter adapter = new TestAdapter()
                .withNode(siphon, ConnectableRegistry.GRAVIUM_SIPHON_BLOCK_ID)
                .withNode(output, ConnectableRegistry.GRAVITY_POWDER_BLOCK_ID)
                .withSourceSignal(new Vector3i(-1, 0, 0), siphon);

        ConnectableSignalRecalculator.recompute(adapter);

        assertEquals(SignalState.OFF, adapter.signal(siphon));
        assertEquals(SignalState.OFF, adapter.signal(output));
    }

    @Test
    void recalculatorNoLongerExposesOldCableInverterCategoryApi() throws Exception {
        String source = Files.readString(Path.of("src/main/java/dev/moxinat/forcesofgravium/connectable/propagation/ConnectableSignalRecalculator.java"));

        assertFalse(source.contains("cablePositions"));
        assertFalse(source.contains("inverterPositions"));
        assertFalse(source.contains("isCable"));
        assertFalse(source.contains("isInverter"));
        assertFalse(source.contains("addCableOutputs"));
        assertFalse(source.contains("addInverterOutput"));
    }

    private static ConnectableRuntimeData runtime(boolean invertEnabled, boolean passing, SignalState instant, SignalState effective) {
        return new ConnectableRuntimeData(
                RotationTuple.NONE,
                ConnectableSignalRecalculator.stateForSignal(instant),
                ConnectableSignalRecalculator.stateForSignal(instant),
                ConnectableSignalRecalculator.stateForSignal(effective),
                ConnectableSignalRecalculator.stateForSignal(effective),
                !instant.equals(effective),
                invertEnabled,
                passing,
                0,
                ConnectableRuntimeData.NO_NETWORK
        );
    }

    private static final class TestAdapter implements ConnectableSignalRecalculator.NodeAdapter {
        private final Map<Vector3i, ConnectableNode> nodes = new LinkedHashMap<>();
        private final Set<Edge> sourceSignals = new LinkedHashSet<>();
        private final Map<Vector3i, SignalState> signals = new LinkedHashMap<>();
        private final Map<Vector3i, Boolean> invertEnabled = new LinkedHashMap<>();
        private final Map<Vector3i, SignalState> controlMemory = new LinkedHashMap<>();
        private final Set<Vector3i> dirtyMarks = new LinkedHashSet<>();

        private TestAdapter withNode(Vector3i position, String blockId) {
            boolean defaultInvert = ConnectableRegistry.INVERTER_BLOCK_ID.equals(blockId);
            boolean defaultPassing = !ConnectableRegistry.GRAVIUM_SIPHON_BLOCK_ID.equals(blockId);
            return withNode(position, blockId, runtime(defaultInvert, defaultPassing, SignalState.OFF, SignalState.OFF));
        }

        private TestAdapter withNode(Vector3i position, String blockId, ConnectableRuntimeData runtimeData) {
            ConnectableDefinition definition = ConnectableDefinitions.findByBlockId(blockId).orElseThrow();
            ConnectableNode node = new ConnectableNode(position, blockId, definition, runtimeData);
            nodes.put(new Vector3i(position), node);
            signals.put(new Vector3i(position), ConnectableSignalRecalculator.signalForState(runtimeData.instantState()));
            invertEnabled.put(new Vector3i(position), runtimeData.invertEnabled());
            return this;
        }

        private TestAdapter removeNode(Vector3i position) {
            nodes.remove(position);
            signals.remove(position);
            invertEnabled.remove(position);
            controlMemory.remove(position);
            sourceSignals.removeIf(edge -> edge.source().equals(position) || edge.target().equals(position));
            return this;
        }

        private TestAdapter withSourceSignal(Vector3i source, Vector3i target) {
            sourceSignals.add(new Edge(source, target));
            return this;
        }

        private TestAdapter withoutSourceSignal(Vector3i source, Vector3i target) {
            sourceSignals.remove(new Edge(source, target));
            return this;
        }

        private SignalState signal(Vector3i position) {
            return signals.getOrDefault(position, SignalState.OFF);
        }

        private boolean invertEnabled(Vector3i position) {
            return invertEnabled.getOrDefault(position, false);
        }

        private SignalState controlMemory(Vector3i position) {
            return controlMemory.getOrDefault(position, SignalState.OFF);
        }

        private boolean dirtyMarked(Vector3i position) {
            return dirtyMarks.contains(position);
        }

        @Override
        public @Nonnull Set<Vector3i> nodePositions() {
            return Set.copyOf(nodes.keySet());
        }

        @Override
        public @Nonnull Optional<ConnectableNode> nodeAt(@Nonnull Vector3i position) {
            return Optional.ofNullable(nodes.get(position));
        }

        @Override
        public @Nonnull List<Vector3i> positionsAround(@Nonnull Vector3i position) {
            return ConnectableNeighborResolver.positionsAround(position);
        }

        @Override
        public boolean hasActiveSourceSignalTo(@Nonnull Vector3i sourcePosition, @Nonnull ConnectableNode target) {
            return sourceSignals.contains(new Edge(sourcePosition, target.position())) && canReceiveSignal(target, sourcePosition);
        }

        @Override
        public boolean hasActiveSourceControlTo(@Nonnull Vector3i sourcePosition, @Nonnull ConnectableNode target) {
            return sourceSignals.contains(new Edge(sourcePosition, target.position())) && canReceiveControl(target, sourcePosition);
        }

        @Override
        public @Nonnull SignalState readControlInputMemory(@Nonnull ConnectableNode node) {
            return controlMemory(node.position());
        }

        @Override
        public void writeInstantState(@Nonnull ConnectableNode node, @Nonnull SignalState signal) {
            SignalState previous = signal(node.position());
            signals.put(node.position(), signal);
            if (previous != signal) {
                dirtyMarks.add(node.position());
            }
            updateNodeRuntime(node.position(), signal, invertEnabled(node.position()), controlMemory(node.position()));
        }

        @Override
        public void writeInvertRuntime(@Nonnull ConnectableNode node, boolean nextInvertEnabled, @Nonnull SignalState nextControlInputMemory) {
            invertEnabled.put(node.position(), nextInvertEnabled);
            controlMemory.put(node.position(), nextControlInputMemory);
            updateNodeRuntime(node.position(), signal(node.position()), nextInvertEnabled, nextControlInputMemory);
        }

        private void updateNodeRuntime(Vector3i position, SignalState instant, boolean nextInvertEnabled, SignalState ignoredControlInputMemory) {
            ConnectableNode current = nodes.get(position);
            ConnectableRuntimeData runtime = current.runtimeData();
            ConnectableRuntimeData updated = new ConnectableRuntimeData(
                    runtime.rotation(),
                    runtime.instantState(),
                    ConnectableSignalRecalculator.stateForSignal(instant),
                    runtime.previousEffectiveState(),
                    runtime.effectiveState(),
                    runtime.dirty(),
                    nextInvertEnabled,
                    runtime.passing(),
                    runtime.energyDelta(),
                    runtime.networkId()
            );
            nodes.put(position, new ConnectableNode(position, current.blockId(), current.definition(), updated));
        }

        private boolean canReceiveSignal(ConnectableNode target, Vector3i sourcePosition) {
            ConnectableNeighborResolver.WorldSide sourceToTarget = ConnectableNeighborResolver.worldSideFromSourceToTarget(sourcePosition, target.position());
            if (sourceToTarget == null) {
                return false;
            }
            int targetLocalSide = ConnectableNeighborResolver.localSideForWorldSide(target.rotation(), sourceToTarget.opposite());
            return target.canReceiveSignalFrom(targetLocalSide);
        }

        private boolean canReceiveControl(ConnectableNode target, Vector3i sourcePosition) {
            ConnectableNeighborResolver.WorldSide sourceToTarget = ConnectableNeighborResolver.worldSideFromSourceToTarget(sourcePosition, target.position());
            if (sourceToTarget == null) {
                return false;
            }
            int targetLocalSide = ConnectableNeighborResolver.localSideForWorldSide(target.rotation(), sourceToTarget.opposite());
            return target.canReceiveControlFrom(targetLocalSide);
        }
    }

    private record Edge(@Nonnull Vector3i source, @Nonnull Vector3i target) {
        private Edge {
            source = new Vector3i(source);
            target = new Vector3i(target);
        }
    }
}
