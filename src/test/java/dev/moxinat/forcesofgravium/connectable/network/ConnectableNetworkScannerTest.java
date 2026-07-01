package dev.moxinat.forcesofgravium.connectable.network;

import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import dev.moxinat.forcesofgravium.block.gravity.GravityPowderSpecialStateStore;
import dev.moxinat.forcesofgravium.connectable.core.ConnectableDefinition;
import dev.moxinat.forcesofgravium.connectable.core.ConnectableDefinitions;
import dev.moxinat.forcesofgravium.connectable.core.ConnectableRuntimeData;
import dev.moxinat.forcesofgravium.connectable.propagation.ConnectableNode;
import dev.moxinat.forcesofgravium.connectable.propagation.SignalState;
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

class ConnectableNetworkScannerTest {

    @Test
    void scansWholeEffectiveNodeNetworkAndCollectsAllSources() {
        Vector3i first = new Vector3i(0, 0, 0);
        Vector3i second = new Vector3i(1, 0, 0);
        Vector3i third = new Vector3i(2, 0, 0);
        Vector3i firstSource = new Vector3i(0, 1, 0);
        Vector3i secondSource = new Vector3i(2, 1, 0);
        TestAdapter adapter = new TestAdapter()
                .withNode(first, ConnectableRegistry.GRAVITY_POWDER_BLOCK_ID, SignalState.PUSH)
                .withNode(second, ConnectableRegistry.GRAVITY_POWDER_BLOCK_ID, SignalState.PUSH)
                .withNode(third, ConnectableRegistry.GRAVITY_POWDER_BLOCK_ID, SignalState.PUSH)
                .withSourceNeighbor(first, firstSource)
                .withSourceNeighbor(third, secondSource);

        NetworkScanResult result = ConnectableNetworkScanner.scanFrom(adapter, first, SignalState.PUSH);

        assertEquals(Set.of(first, second, third), result.nodes());
        assertEquals(Set.of(firstSource, secondSource), result.sources());
        assertTrue(result.hasAnySource());
    }

    @Test
    void effectiveScanCrossesInvertCapableNodeUsingInvertEnabled() {
        Vector3i input = new Vector3i(0, 0, -1);
        Vector3i inverter = new Vector3i(0, 0, 0);
        Vector3i output = new Vector3i(0, 0, 1);
        Vector3i source = new Vector3i(0, 0, -2);
        TestAdapter adapter = new TestAdapter()
                .withNode(input, ConnectableRegistry.GRAVITY_POWDER_BLOCK_ID, SignalState.PUSH)
                .withNode(inverter, ConnectableRegistry.INVERTER_BLOCK_ID, runtime(true, true, SignalState.PULL))
                .withNode(output, ConnectableRegistry.GRAVITY_POWDER_BLOCK_ID, SignalState.PULL)
                .withSourceNeighbor(input, source);

        NetworkScanResult fromInput = ConnectableNetworkScanner.scanFrom(adapter, input, SignalState.PUSH);
        NetworkScanResult fromOutput = ConnectableNetworkScanner.scanFrom(adapter, output, SignalState.PULL);

        assertEquals(Set.of(input, inverter, output), fromInput.nodes());
        assertEquals(Set.of(input, inverter, output), fromOutput.nodes());
        assertEquals(Set.of(source), fromOutput.sources());
    }

    @Test
    void collectsConsumersTouchingNode() {
        Vector3i node = new Vector3i(0, 0, 0);
        Vector3i consumer = new Vector3i(0, 1, 0);
        TestAdapter adapter = new TestAdapter()
                .withNode(node, ConnectableRegistry.GRAVITY_POWDER_BLOCK_ID, SignalState.PULL)
                .withConsumerNeighbor(node, consumer);

        NetworkScanResult result = ConnectableNetworkScanner.scanFrom(adapter, node, SignalState.PULL);

        assertEquals(Set.of(consumer), result.consumers());
    }

    @Test
    void scanOnlyTraversesNodeWhenRequestedModeIsEffective() {
        Vector3i node = new Vector3i(0, 0, 0);
        Vector3i source = new Vector3i(0, 1, 0);
        TestAdapter adapter = new TestAdapter()
                .withNode(node, ConnectableRegistry.GRAVITY_POWDER_BLOCK_ID, SignalState.PUSH)
                .withSourceNeighbor(node, source);

        NetworkScanResult result = ConnectableNetworkScanner.scanFrom(adapter, node, SignalState.PULL);

        assertEquals(Set.of(), result.nodes());
        assertEquals(Set.of(), result.sources());
    }

    @Test
    void dirtyInstantPushEffectiveOffIsNotEffectivePush() {
        Vector3i node = new Vector3i(0, 0, 0);
        TestAdapter adapter = new TestAdapter()
                .withNode(node, ConnectableRegistry.GRAVITY_POWDER_BLOCK_ID, runtime(false, true, SignalState.PUSH, SignalState.OFF));

        NetworkScanResult result = ConnectableNetworkScanner.scanFrom(adapter, node, SignalState.PUSH);

        assertEquals(Set.of(), result.nodes());
    }

    @Test
    void siphonSignalInputDoesNotMakeItEffectiveNetworkMember() {
        Vector3i siphon = new Vector3i(0, 0, 0);
        TestAdapter adapter = new TestAdapter()
                .withNode(siphon, ConnectableRegistry.GRAVIUM_SIPHON_BLOCK_ID, SignalState.PUSH);

        NetworkScanResult result = ConnectableNetworkScanner.scanFrom(adapter, siphon, SignalState.PUSH);

        assertEquals(Set.of(), result.nodes());
    }

    @Test
    void scannerNoLongerExposesOldCableInverterCategoryApi() throws Exception {
        String source = Files.readString(Path.of("src/main/java/dev/moxinat/forcesofgravium/connectable/network/ConnectableNetworkScanner.java"));

        assertFalse(source.contains("isCable"));
        assertFalse(source.contains("isInverter"));
        assertFalse(source.contains("addCableNeighbors"));
        assertFalse(source.contains("addInverterConnections"));
        assertFalse(source.contains("inverterBack"));
        assertFalse(source.contains("inverterFront"));
        assertFalse(source.contains("cableHasSignal"));
    }

    private static ConnectableRuntimeData runtime(boolean invertEnabled, boolean passing, SignalState state) {
        return runtime(invertEnabled, passing, state, state);
    }

    private static ConnectableRuntimeData runtime(boolean invertEnabled, boolean passing, SignalState instant, SignalState effective) {
        return new ConnectableRuntimeData(
                RotationTuple.NONE,
                stateForSignal(instant),
                stateForSignal(instant),
                stateForSignal(effective),
                stateForSignal(effective),
                !instant.equals(effective),
                invertEnabled,
                passing,
                0,
                ConnectableRuntimeData.NO_NETWORK
        );
    }

    private static String stateForSignal(SignalState mode) {
        return ConnectableSignalStateNames.stateForSignal(mode);
    }

    private static final class TestAdapter implements ConnectableNetworkScanner.NetworkScanAdapter {
        private final Map<Vector3i, ConnectableNode> nodes = new LinkedHashMap<>();
        private final Map<Vector3i, Set<Vector3i>> sourceNeighbors = new LinkedHashMap<>();
        private final Map<Vector3i, Set<Vector3i>> consumerNeighbors = new LinkedHashMap<>();

        private TestAdapter withNode(Vector3i position, String blockId, SignalState effectiveState) {
            boolean invertEnabled = ConnectableRegistry.INVERTER_BLOCK_ID.equals(blockId);
            boolean passing = !ConnectableRegistry.GRAVIUM_SIPHON_BLOCK_ID.equals(blockId);
            return withNode(position, blockId, runtime(invertEnabled, passing, effectiveState));
        }

        private TestAdapter withNode(Vector3i position, String blockId, ConnectableRuntimeData runtimeData) {
            ConnectableDefinition definition = ConnectableDefinitions.findByBlockId(blockId).orElseThrow();
            nodes.put(new Vector3i(position), new ConnectableNode(position, blockId, definition, runtimeData));
            return this;
        }

        private TestAdapter withSourceNeighbor(Vector3i node, Vector3i source) {
            sourceNeighbors.computeIfAbsent(node, ignored -> new LinkedHashSet<>()).add(source);
            return this;
        }

        private TestAdapter withConsumerNeighbor(Vector3i node, Vector3i consumer) {
            consumerNeighbors.computeIfAbsent(node, ignored -> new LinkedHashSet<>()).add(consumer);
            return this;
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
        public @Nonnull Set<Vector3i> sourceNeighbors(@Nonnull ConnectableNode node) {
            return Set.copyOf(sourceNeighbors.getOrDefault(node.position(), Set.of()));
        }

        @Override
        public @Nonnull Set<Vector3i> consumerNeighbors(@Nonnull ConnectableNode node) {
            return Set.copyOf(consumerNeighbors.getOrDefault(node.position(), Set.of()));
        }
    }

    private static final class ConnectableSignalStateNames {
        private ConnectableSignalStateNames() {
        }

        private static String stateForSignal(SignalState mode) {
            return switch (mode) {
                case PUSH -> GravityPowderSpecialStateStore.STATE_PUSH;
                case PULL -> GravityPowderSpecialStateStore.STATE_PULL;
                case OFF -> GravityPowderSpecialStateStore.STATE_OFF;
            };
        }
    }
}
