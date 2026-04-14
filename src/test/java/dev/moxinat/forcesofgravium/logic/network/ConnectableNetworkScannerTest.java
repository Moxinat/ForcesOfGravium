package dev.moxinat.forcesofgravium.logic.network;

import com.hypixel.hytale.math.vector.Vector3i;
import org.junit.jupiter.api.Test;

import javax.annotation.Nonnull;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConnectableNetworkScannerTest {

    @Test
    void scansWholeNetworkAndCollectsAllSources() {
        Vector3i first = new Vector3i(0, 0, 0);
        Vector3i second = new Vector3i(1, 0, 0);
        Vector3i third = new Vector3i(2, 0, 0);
        Vector3i firstSource = new Vector3i(0, 1, 0);
        Vector3i secondSource = new Vector3i(2, 1, 0);
        TestAdapter adapter = new TestAdapter()
                .withCable(first, SignalMode.PUSH)
                .withCable(second, SignalMode.PUSH)
                .withCable(third, SignalMode.PUSH)
                .withSourceNeighbor(first, firstSource)
                .withSourceNeighbor(third, secondSource);

        NetworkScanResult result = ConnectableNetworkScanner.scanFrom(adapter, first, SignalMode.PUSH);

        assertEquals(Set.of(first, second, third), result.carriers());
        assertEquals(Set.of(firstSource, secondSource), result.sources());
        assertTrue(result.hasAnySource());
    }

    @Test
    void inverterFlipsPushToPullNetwork() {
        Vector3i input = new Vector3i(0, 0, 0);
        Vector3i inverter = new Vector3i(1, 0, 0);
        Vector3i output = new Vector3i(2, 0, 0);
        Vector3i source = new Vector3i(-1, 0, 0);
        TestAdapter adapter = new TestAdapter()
                .withCable(input, SignalMode.PUSH)
                .withCable(output, SignalMode.PULL)
                .withInverter(inverter, input, output, true)
                .withSourceNeighbor(input, source);

        NetworkScanResult result = ConnectableNetworkScanner.scanFrom(adapter, input, SignalMode.PUSH);

        assertEquals(Set.of(input, output), result.carriers());
        assertEquals(Set.of(inverter), result.inverters());
        assertEquals(Set.of(source), result.sources());
    }

    @Test
    void pullScanCanFindSourceBehindInverter() {
        Vector3i input = new Vector3i(0, 0, 0);
        Vector3i inverter = new Vector3i(1, 0, 0);
        Vector3i output = new Vector3i(2, 0, 0);
        Vector3i source = new Vector3i(-1, 0, 0);
        TestAdapter adapter = new TestAdapter()
                .withCable(input, SignalMode.PUSH)
                .withCable(output, SignalMode.PULL)
                .withInverter(inverter, input, output, true)
                .withSourceNeighbor(input, source);

        NetworkScanResult result = ConnectableNetworkScanner.scanFrom(adapter, output, SignalMode.PULL);

        assertEquals(Set.of(input, output), result.carriers());
        assertEquals(Set.of(inverter), result.inverters());
        assertEquals(Set.of(source), result.sources());
    }

    @Test
    void collectsConsumersTouchingCarrier() {
        Vector3i cable = new Vector3i(0, 0, 0);
        Vector3i consumer = new Vector3i(0, 1, 0);
        TestAdapter adapter = new TestAdapter()
                .withCable(cable, SignalMode.PULL)
                .withConsumerNeighbor(cable, consumer);

        NetworkScanResult result = ConnectableNetworkScanner.scanFrom(adapter, cable, SignalMode.PULL);

        assertEquals(Set.of(consumer), result.consumers());
    }

    private static final class TestAdapter implements ConnectableNetworkScanner.NetworkScanAdapter {
        private final Map<Vector3i, Set<SignalMode>> cableSignals = new LinkedHashMap<>();
        private final Set<Vector3i> inverters = new LinkedHashSet<>();
        private final Map<Vector3i, Boolean> invertEnabled = new LinkedHashMap<>();
        private final Map<Vector3i, Vector3i> inverterBacks = new LinkedHashMap<>();
        private final Map<Vector3i, Vector3i> inverterFronts = new LinkedHashMap<>();
        private final Map<Vector3i, Set<Vector3i>> sourceNeighbors = new LinkedHashMap<>();
        private final Map<Vector3i, Set<Vector3i>> consumerNeighbors = new LinkedHashMap<>();

        private TestAdapter withCable(Vector3i cable, SignalMode mode) {
            cableSignals.computeIfAbsent(cable, ignored -> new LinkedHashSet<>()).add(mode);
            return this;
        }

        private TestAdapter withInverter(Vector3i inverter, Vector3i back, Vector3i front, boolean enabled) {
            inverters.add(inverter);
            inverterBacks.put(inverter, back);
            inverterFronts.put(inverter, front);
            invertEnabled.put(inverter, enabled);
            return this;
        }

        private TestAdapter withSourceNeighbor(Vector3i carrier, Vector3i source) {
            sourceNeighbors.computeIfAbsent(carrier, ignored -> new LinkedHashSet<>()).add(source);
            return this;
        }

        private TestAdapter withConsumerNeighbor(Vector3i carrier, Vector3i consumer) {
            consumerNeighbors.computeIfAbsent(carrier, ignored -> new LinkedHashSet<>()).add(consumer);
            return this;
        }

        @Override
        public boolean isCable(@Nonnull Vector3i position) {
            return cableSignals.containsKey(position);
        }

        @Override
        public boolean cableHasSignal(@Nonnull Vector3i position, @Nonnull SignalMode mode) {
            return cableSignals.getOrDefault(position, Set.of()).contains(mode);
        }

        @Override
        public boolean isInverter(@Nonnull Vector3i position) {
            return inverters.contains(position);
        }

        @Override
        public boolean isInvertEnabled(@Nonnull Vector3i inverter) {
            return invertEnabled.getOrDefault(inverter, true);
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
        public @Nonnull Set<Vector3i> sourceNeighbors(@Nonnull Vector3i position) {
            return Set.copyOf(sourceNeighbors.getOrDefault(position, Set.of()));
        }

        @Override
        public @Nonnull Set<Vector3i> consumerNeighbors(@Nonnull Vector3i position) {
            return Set.copyOf(consumerNeighbors.getOrDefault(position, Set.of()));
        }
    }
}
