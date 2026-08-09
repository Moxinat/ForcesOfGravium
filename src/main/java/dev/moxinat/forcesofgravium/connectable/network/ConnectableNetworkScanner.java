package dev.moxinat.forcesofgravium.connectable.network;

import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import dev.moxinat.forcesofgravium.block.gravity.GravityPowderSpecialStateStore;
import dev.moxinat.forcesofgravium.connectable.core.ConnectableRuntimeAccessor;
import dev.moxinat.forcesofgravium.connectable.propagation.NetworkStep;
import dev.moxinat.forcesofgravium.connectable.propagation.SignalState;
import dev.moxinat.forcesofgravium.connectable.registry.ConnectableBlockRoles;
import dev.moxinat.forcesofgravium.connectable.spatial.ConnectableNeighborResolver;
import dev.moxinat.forcesofgravium.connectable.spatial.ConnectableNeighborResolver.WorldSide;
import org.joml.Vector3i;

import javax.annotation.Nonnull;
import java.util.ArrayDeque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class ConnectableNetworkScanner {

    private ConnectableNetworkScanner() {
    }

    public static @Nonnull NetworkScanResult scanFrom(@Nonnull World world, @Nonnull Vector3i start, @Nonnull SignalState mode) {
        return scanFrom(new WorldNetworkScanAdapter(world), start, mode);
    }

    public static @Nonnull NetworkScanResult scanFrom(@Nonnull NetworkScanAdapter adapter, @Nonnull Vector3i start, @Nonnull SignalState mode) {
        Objects.requireNonNull(adapter, "adapter");
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(mode, "mode");

        LinkedHashSet<Vector3i> nodes = new LinkedHashSet<>();
        LinkedHashSet<Vector3i> sources = new LinkedHashSet<>();
        LinkedHashSet<Vector3i> consumers = new LinkedHashSet<>();
        ArrayDeque<NetworkStep> queue = new ArrayDeque<>();
        LinkedHashSet<NetworkStep> visited = new LinkedHashSet<>();

        enqueueIfMatchingNode(adapter, queue, visited, start, mode);

        while (!queue.isEmpty()) {
            NetworkStep step = queue.removeFirst();
            ConnectableNode current = adapter.nodeAt(step.position()).orElse(null);
            if (current == null || !isEffectiveSignal(current, step.signalState())) {
                continue;
            }

            nodes.add(current.position());
            sources.addAll(adapter.sourceNeighbors(current));
            consumers.addAll(adapter.consumerNeighbors(current));
            addAdjacentNodes(adapter, queue, visited, current, step.signalState());
        }

        return new NetworkScanResult(
                mode,
                Set.copyOf(nodes),
                Set.copyOf(sources),
                Set.copyOf(consumers)
        );
    }

    private static void addAdjacentNodes(
            NetworkScanAdapter adapter,
            ArrayDeque<NetworkStep> queue,
            Set<NetworkStep> visited,
            ConnectableNode current,
            SignalState currentMode
    ) {
        for (Vector3i neighborPosition : adapter.positionsAround(current.position())) {
            if (neighborPosition.equals(current.position())) {
                continue;
            }
            ConnectableNode neighbor = adapter.nodeAt(neighborPosition).orElse(null);
            if (neighbor == null || !neighbor.isSignalRuntimeNode()) {
                continue;
            }
            enqueueForwardIfMatching(adapter, queue, visited, current, neighbor, currentMode);
            enqueueReverseIfMatching(adapter, queue, visited, current, neighbor, currentMode);
        }
    }

    private static void enqueueForwardIfMatching(
            NetworkScanAdapter adapter,
            ArrayDeque<NetworkStep> queue,
            Set<NetworkStep> visited,
            ConnectableNode current,
            ConnectableNode neighbor,
            SignalState currentMode
    ) {
        if (!canPropagateSignal(current, neighbor)) {
            return;
        }
        if (neighbor.invertCapable() && current.passBehaviorCapable() && adapter.effectiveSignal(current) != currentMode) {
            return;
        }
        SignalState neighborMode = outputSignalState(currentMode, neighbor.invertCapable() && neighbor.invertEnabled());
        enqueueIfMatchingNode(adapter, queue, visited, neighbor.position(), neighborMode);
    }

    private static void enqueueReverseIfMatching(
            NetworkScanAdapter adapter,
            ArrayDeque<NetworkStep> queue,
            Set<NetworkStep> visited,
            ConnectableNode current,
            ConnectableNode neighbor,
            SignalState currentMode
    ) {
        if (!canPropagateSignal(neighbor, current)) {
            return;
        }
        SignalState neighborMode = outputSignalState(currentMode, current.invertCapable() && current.invertEnabled());
        if (current.invertCapable() && neighbor.passBehaviorCapable() && adapter.effectiveSignal(neighbor) != neighborMode) {
            return;
        }
        enqueueIfMatchingNode(adapter, queue, visited, neighbor.position(), neighborMode);
    }

    private static void enqueueIfMatchingNode(
            NetworkScanAdapter adapter,
            ArrayDeque<NetworkStep> queue,
            Set<NetworkStep> visited,
            Vector3i position,
            SignalState mode
    ) {
        ConnectableNode node = adapter.nodeAt(position).orElse(null);
        if (node == null || !node.isSignalRuntimeNode() || !isEffectiveSignal(node, mode)) {
            return;
        }
        NetworkStep step = new NetworkStep(position, mode);
        if (visited.add(step)) {
            queue.addLast(step);
        }
    }

    private static boolean canPropagateSignal(@Nonnull ConnectableNode source, @Nonnull ConnectableNode target) {
        WorldSide sourceToTarget = ConnectableNeighborResolver.worldSideFromSourceToTarget(source.position(), target.position());
        if (sourceToTarget == null) {
            return false;
        }
        int sourceLocalSide = ConnectableNeighborResolver.localSideForWorldSide(source.rotation(), sourceToTarget);
        int targetLocalSide = ConnectableNeighborResolver.localSideForWorldSide(target.rotation(), sourceToTarget.opposite());
        return source.canOutputSignalTo(sourceLocalSide) && target.canReceiveSignalFrom(targetLocalSide);
    }

    private static boolean isEffectiveSignal(@Nonnull ConnectableNode node, @Nonnull SignalState mode) {
        return signalForState(node.effectiveState()) == mode && mode != SignalState.OFF;
    }

    private static @Nonnull SignalState outputSignalState(@Nonnull SignalState signalState, boolean invertEnabled) {
        return invertEnabled ? signalState.inverted() : signalState;
    }

    private static @Nonnull SignalState signalForState(@Nonnull String state) {
        return switch (GravityPowderSpecialStateStore.normalizeState(state)) {
            case GravityPowderSpecialStateStore.STATE_PUSH -> SignalState.PUSH;
            case GravityPowderSpecialStateStore.STATE_PULL -> SignalState.PULL;
            default -> SignalState.OFF;
        };
    }

    public interface NetworkScanAdapter {
        @Nonnull Optional<ConnectableNode> nodeAt(@Nonnull Vector3i position);

        @Nonnull List<Vector3i> positionsAround(@Nonnull Vector3i position);

        default @Nonnull SignalState effectiveSignal(@Nonnull ConnectableNode node) {
            return signalForState(node.effectiveState());
        }

        @Nonnull Set<Vector3i> sourceNeighbors(@Nonnull ConnectableNode node);

        @Nonnull Set<Vector3i> consumerNeighbors(@Nonnull ConnectableNode node);
    }

    private record WorldNetworkScanAdapter(@Nonnull World world) implements NetworkScanAdapter {

        private WorldNetworkScanAdapter {
            Objects.requireNonNull(world, "world");
        }

        @Override
        public @Nonnull Optional<ConnectableNode> nodeAt(@Nonnull Vector3i position) {
            return ConnectableNodeProvider.nodeAt(world, position);
        }

        @Override
        public @Nonnull List<Vector3i> positionsAround(@Nonnull Vector3i position) {
            return ConnectableNeighborResolver.positionsAround(position);
        }

        @Override
        public @Nonnull Set<Vector3i> sourceNeighbors(@Nonnull ConnectableNode node) {
            LinkedHashSet<Vector3i> result = new LinkedHashSet<>();
            for (Vector3i source : ConnectableNeighborResolver.sourceNeighbors(world, node.position(), null)) {
                if (canReceiveSignalFromSource(node, source)) {
                    result.add(source);
                }
            }
            return Set.copyOf(result);
        }

        @Override
        public @Nonnull Set<Vector3i> consumerNeighbors(@Nonnull ConnectableNode node) {
            LinkedHashSet<Vector3i> result = new LinkedHashSet<>();
            for (Vector3i candidate : ConnectableNeighborResolver.positionsAround(node.position())) {
                if (candidate.equals(node.position())) {
                    continue;
                }
                BlockType blockType = world.getBlockType(candidate.x(), candidate.y(), candidate.z());
                if (blockType != null
                        && ConnectableBlockRoles.isConsumer(blockType.getId())
                        && ConnectableNeighborResolver.hasConnectableSideFacing(world, node.position(), candidate)
                        && ConnectableNeighborResolver.hasConnectableSideFacing(world, candidate, node.position())) {
                    result.add(candidate);
                }
            }
            return Set.copyOf(result);
        }

        private boolean canReceiveSignalFromSource(@Nonnull ConnectableNode target, @Nonnull Vector3i sourcePosition) {
            if (!ConnectableNeighborResolver.isSourceNeighborOf(world, sourcePosition, target.position())) {
                return false;
            }
            WorldSide sourceToTarget = ConnectableNeighborResolver.worldSideFromSourceToTarget(sourcePosition, target.position());
            if (sourceToTarget == null) {
                return false;
            }
            int targetLocalSide = ConnectableNeighborResolver.localSideForWorldSide(target.rotation(), sourceToTarget.opposite());
            return target.canReceiveSignalFrom(targetLocalSide);
        }
    }
}
