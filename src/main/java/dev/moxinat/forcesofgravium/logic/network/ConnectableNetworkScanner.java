package dev.moxinat.forcesofgravium.logic.network;

import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import dev.moxinat.forcesofgravium.data.GravityPowderBlockDataStore;
import dev.moxinat.forcesofgravium.data.GravityPowderBlockDataStore.GravityPowderBlockData;
import dev.moxinat.forcesofgravium.data.InverterDataStore;
import dev.moxinat.forcesofgravium.data.InverterDataStore.InverterData;
import dev.moxinat.forcesofgravium.registry.ConnectableBlockRoles;
import dev.moxinat.forcesofgravium.registry.ConnectableRegistry;

import javax.annotation.Nonnull;
import java.util.ArrayDeque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class ConnectableNetworkScanner {

    private ConnectableNetworkScanner() {
    }

    public static @Nonnull NetworkScanResult scanFrom(@Nonnull World world, @Nonnull Vector3i start, @Nonnull SignalMode mode) {
        return scanFrom(new WorldNetworkScanAdapter(world), start, mode);
    }

    public static @Nonnull NetworkScanResult scanFrom(@Nonnull NetworkScanAdapter adapter, @Nonnull Vector3i start, @Nonnull SignalMode mode) {
        Objects.requireNonNull(adapter, "adapter");
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(mode, "mode");

        LinkedHashSet<Vector3i> carriers = new LinkedHashSet<>();
        LinkedHashSet<Vector3i> inverters = new LinkedHashSet<>();
        LinkedHashSet<Vector3i> sources = new LinkedHashSet<>();
        LinkedHashSet<Vector3i> consumers = new LinkedHashSet<>();
        ArrayDeque<NetworkStep> queue = new ArrayDeque<>();
        LinkedHashSet<NetworkStep> visited = new LinkedHashSet<>();

        enqueueIfCarrier(adapter, queue, visited, start, mode);

        while (!queue.isEmpty()) {
            NetworkStep step = queue.removeFirst();
            Vector3i position = step.position();
            SignalMode signalMode = step.mode();

            if (adapter.isCable(position) && adapter.cableHasSignal(position, signalMode)) {
                carriers.add(position);
                sources.addAll(adapter.sourceNeighbors(position));
                consumers.addAll(adapter.consumerNeighbors(position));
                addCableNeighbors(adapter, queue, visited, position, signalMode);
                addInverterConnections(adapter, queue, visited, position, signalMode, inverters, sources, consumers);
            }
        }

        return new NetworkScanResult(
                mode,
                Set.copyOf(carriers),
                Set.copyOf(inverters),
                Set.copyOf(sources),
                Set.copyOf(consumers)
        );
    }

    private static void addCableNeighbors(
            NetworkScanAdapter adapter,
            ArrayDeque<NetworkStep> queue,
            Set<NetworkStep> visited,
            Vector3i position,
            SignalMode mode
    ) {
        for (Vector3i neighbor : adapter.positionsAround(position)) {
            if (neighbor.equals(position) || !adapter.isCable(neighbor)) {
                continue;
            }
            enqueueIfCarrier(adapter, queue, visited, neighbor, mode);
        }
    }

    private static void addInverterConnections(
            NetworkScanAdapter adapter,
            ArrayDeque<NetworkStep> queue,
            Set<NetworkStep> visited,
            Vector3i cable,
            SignalMode mode,
            Set<Vector3i> inverters,
            Set<Vector3i> sources,
            Set<Vector3i> consumers
    ) {
        for (Vector3i neighbor : adapter.positionsAround(cable)) {
            if (!adapter.isInverter(neighbor)) {
                continue;
            }

            Vector3i back = adapter.inverterBack(neighbor);
            Vector3i front = adapter.inverterFront(neighbor);
            if (!cable.equals(back) && !cable.equals(front)) {
                continue;
            }

            inverters.add(neighbor);
            sources.addAll(adapter.sourceNeighbors(neighbor));
            consumers.addAll(adapter.consumerNeighbors(neighbor));

            SignalMode otherSideMode = adapter.isInvertEnabled(neighbor) ? mode.inverted() : mode;
            Vector3i otherSide = cable.equals(back) ? front : back;
            enqueueIfCarrier(adapter, queue, visited, otherSide, otherSideMode);
        }
    }

    private static void enqueueIfCarrier(
            NetworkScanAdapter adapter,
            ArrayDeque<NetworkStep> queue,
            Set<NetworkStep> visited,
            Vector3i position,
            SignalMode mode
    ) {
        if (adapter.isCable(position) && adapter.cableHasSignal(position, mode)) {
            NetworkStep step = new NetworkStep(position, mode);
            if (visited.add(step)) {
                queue.addLast(step);
            }
        }
    }

    public interface NetworkScanAdapter {
        boolean isCable(@Nonnull Vector3i position);

        boolean cableHasSignal(@Nonnull Vector3i position, @Nonnull SignalMode mode);

        boolean isInverter(@Nonnull Vector3i position);

        boolean isInvertEnabled(@Nonnull Vector3i inverter);

        @Nonnull Vector3i inverterBack(@Nonnull Vector3i inverter);

        @Nonnull Vector3i inverterFront(@Nonnull Vector3i inverter);

        @Nonnull List<Vector3i> positionsAround(@Nonnull Vector3i position);

        @Nonnull Set<Vector3i> sourceNeighbors(@Nonnull Vector3i position);

        @Nonnull Set<Vector3i> consumerNeighbors(@Nonnull Vector3i position);
    }

    private record WorldNetworkScanAdapter(@Nonnull World world) implements NetworkScanAdapter {

        private WorldNetworkScanAdapter {
            Objects.requireNonNull(world, "world");
        }

        @Override
        public boolean isCable(@Nonnull Vector3i position) {
            BlockType blockType = world.getBlockType(position.getX(), position.getY(), position.getZ());
            return blockType != null && ConnectableRegistry.isGravityPowderId(blockType.getId());
        }

        @Override
        public boolean cableHasSignal(@Nonnull Vector3i position, @Nonnull SignalMode mode) {
            GravityPowderBlockData data = GravityPowderBlockDataStore.get(world, position);
            if (data == null) {
                return false;
            }
            return mode == SignalMode.PUSH ? data.push() : data.pull();
        }

        @Override
        public boolean isInverter(@Nonnull Vector3i position) {
            BlockType blockType = world.getBlockType(position.getX(), position.getY(), position.getZ());
            return blockType != null && ConnectableRegistry.isInverterId(blockType.getId());
        }

        @Override
        public boolean isInvertEnabled(@Nonnull Vector3i inverter) {
            InverterData data = InverterDataStore.get(world, inverter);
            return data == null || data.invertEnabled();
        }

        @Override
        public @Nonnull Vector3i inverterBack(@Nonnull Vector3i inverter) {
            return ConnectableNeighborResolver.adjacentPositionForLocalSide(world, inverter, ConnectableRegistry.SIDE_BACK);
        }

        @Override
        public @Nonnull Vector3i inverterFront(@Nonnull Vector3i inverter) {
            return ConnectableNeighborResolver.adjacentPositionForLocalSide(world, inverter, ConnectableRegistry.SIDE_FRONT);
        }

        @Override
        public @Nonnull List<Vector3i> positionsAround(@Nonnull Vector3i position) {
            return ConnectableNeighborResolver.positionsAround(position);
        }

        @Override
        public @Nonnull Set<Vector3i> sourceNeighbors(@Nonnull Vector3i position) {
            return Set.copyOf(ConnectableNeighborResolver.sourceNeighbors(world, position, null));
        }

        @Override
        public @Nonnull Set<Vector3i> consumerNeighbors(@Nonnull Vector3i position) {
            LinkedHashSet<Vector3i> result = new LinkedHashSet<>();
            for (Vector3i candidate : ConnectableNeighborResolver.positionsAround(position)) {
                if (candidate.equals(position)) {
                    continue;
                }
                BlockType blockType = world.getBlockType(candidate.getX(), candidate.getY(), candidate.getZ());
                if (blockType != null && ConnectableBlockRoles.isConsumer(blockType.getId())) {
                    result.add(candidate);
                }
            }
            return Set.copyOf(result);
        }
    }
}
