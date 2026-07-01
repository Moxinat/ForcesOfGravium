package dev.moxinat.forcesofgravium.connectable.propagation;

import com.hypixel.hytale.server.core.universe.world.World;
import dev.moxinat.forcesofgravium.block.gravity.GravityPowderSpecialStateStore;
import dev.moxinat.forcesofgravium.block.inverter.InverterSpecialStateStore;
import dev.moxinat.forcesofgravium.block.inverter.InverterSpecialStateStore.InverterData;
import dev.moxinat.forcesofgravium.connectable.core.ConnectableRuntimeAccessor;
import dev.moxinat.forcesofgravium.connectable.spatial.ConnectableNeighborResolver;
import dev.moxinat.forcesofgravium.connectable.spatial.ConnectableNeighborResolver.WorldSide;
import org.joml.Vector3i;

import javax.annotation.Nonnull;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class ConnectableSignalRecalculator {

    private ConnectableSignalRecalculator() {
    }

    public static void recompute(@Nonnull World world, @Nonnull Set<Vector3i> affectedPositions) {
        recompute(new WorldNodeAdapter(world, affectedPositions));
    }

    public static void recompute(@Nonnull NodeAdapter adapter) {
        Objects.requireNonNull(adapter, "adapter");
        Map<Vector3i, Boolean> invertEnabled = new LinkedHashMap<>();
        Map<Vector3i, SignalState> controlInputMemory = new LinkedHashMap<>();
        Set<Vector3i> controlNodes = new LinkedHashSet<>();
        for (Vector3i position : adapter.nodePositions()) {
            ConnectableNode node = adapter.nodeAt(position).orElse(null);
            if (node == null || node.controlInputSides() == 0) {
                continue;
            }
            controlNodes.add(position);
            if (node.invertCapable()) {
                invertEnabled.put(position, node.invertEnabled());
                controlInputMemory.put(position, adapter.readControlInputMemory(node));
            }
        }

        PropagationResult propagation = propagate(adapter, invertEnabled);
        for (int remainingPasses = controlNodes.size(); remainingPasses >= 0; remainingPasses--) {
            boolean toggled = false;
            Map<Vector3i, SignalState> nextControlInputMemory = new LinkedHashMap<>();
            for (Vector3i position : controlNodes) {
                ConnectableNode node = adapter.nodeAt(position).orElse(null);
                if (node == null || node.controlInputSides() == 0) {
                    continue;
                }
                SignalState controlInput = controlInputSignal(adapter, node, propagation.nodeSignals());
                SignalState previousControlInput = controlInputMemory.getOrDefault(position, SignalState.OFF);
                if (node.invertCapable()) {
                    nextControlInputMemory.put(position, controlInput);
                }
                if (shouldToggleInvert(node, previousControlInput, controlInput)) {
                    invertEnabled.put(position, !invertEnabled.getOrDefault(position, node.invertEnabled()));
                    toggled = true;
                }
            }
            controlInputMemory = nextControlInputMemory;
            if (!toggled || remainingPasses == 0) {
                break;
            }
            propagation = propagate(adapter, invertEnabled);
        }

        for (Map.Entry<Vector3i, SignalState> entry : propagation.nodeSignals().entrySet()) {
            ConnectableNode node = adapter.nodeAt(entry.getKey()).orElse(null);
            if (node == null || !node.shouldStorePropagatedInstantState()) {
                continue;
            }
            adapter.writeInstantState(node, entry.getValue());
        }
        for (Map.Entry<Vector3i, Boolean> entry : invertEnabled.entrySet()) {
            ConnectableNode node = adapter.nodeAt(entry.getKey()).orElse(null);
            if (node == null || !node.invertCapable()) {
                continue;
            }
            SignalState memory = controlInputMemory.getOrDefault(entry.getKey(), SignalState.OFF);
            adapter.writeInvertRuntime(node, entry.getValue(), memory);
        }
    }

    private static @Nonnull PropagationResult propagate(@Nonnull NodeAdapter adapter, @Nonnull Map<Vector3i, Boolean> invertEnabled) {
        Map<Vector3i, SignalState> nodeSignals = new LinkedHashMap<>();
        for (Vector3i position : adapter.nodePositions()) {
            ConnectableNode node = adapter.nodeAt(position).orElse(null);
            if (node != null && node.shouldStorePropagatedInstantState()) {
                nodeSignals.put(position, SignalState.OFF);
            }
        }

        ArrayDeque<SignalStep> queue = new ArrayDeque<>();
        Set<SignalStep> visited = new LinkedHashSet<>();
        seedSources(adapter, queue, visited);

        while (!queue.isEmpty()) {
            SignalStep step = queue.removeFirst();
            ConnectableNode node = adapter.nodeAt(step.position()).orElse(null);
            if (node == null) {
                continue;
            }

            SignalState outputSignal = outputSignalState(
                    step.signalState(),
                    node.invertCapable() && invertEnabled.getOrDefault(step.position(), node.invertEnabled())
            );
            if (node.shouldStorePropagatedInstantState()) {
                boolean changed = setSignal(nodeSignals, step.position(), outputSignal);
                if (!changed) {
                    continue;
                }
            }
            if (outputSignal == SignalState.OFF) {
                continue;
            }
            addNodeOutputs(adapter, node, outputSignal, queue, visited);
        }

        return new PropagationResult(nodeSignals);
    }

    private static void seedSources(NodeAdapter adapter, ArrayDeque<SignalStep> queue, Set<SignalStep> visited) {
        for (Vector3i targetPosition : adapter.nodePositions()) {
            ConnectableNode target = adapter.nodeAt(targetPosition).orElse(null);
            if (target == null) {
                continue;
            }
            for (Vector3i sourcePosition : adapter.positionsAround(targetPosition)) {
                if (sourcePosition.equals(targetPosition) || !adapter.hasActiveSourceSignalTo(sourcePosition, target)) {
                    continue;
                }
                enqueue(queue, visited, targetPosition, SignalState.PUSH);
            }
        }
    }

    private static void addNodeOutputs(
            NodeAdapter adapter,
            ConnectableNode source,
            SignalState signal,
            ArrayDeque<SignalStep> queue,
            Set<SignalStep> visited
    ) {
        for (Vector3i neighborPosition : adapter.positionsAround(source.position())) {
            if (neighborPosition.equals(source.position())) {
                continue;
            }
            ConnectableNode target = adapter.nodeAt(neighborPosition).orElse(null);
            if (target == null || !canPropagateSignal(source, target)) {
                continue;
            }
            enqueue(queue, visited, target.position(), signal);
        }
    }

    private static @Nonnull SignalState controlInputSignal(
            NodeAdapter adapter,
            ConnectableNode target,
            Map<Vector3i, SignalState> propagatedSignals
    ) {
        SignalState result = SignalState.OFF;
        for (Vector3i sourcePosition : adapter.positionsAround(target.position())) {
            if (sourcePosition.equals(target.position())) {
                continue;
            }
            if (adapter.hasActiveSourceControlTo(sourcePosition, target)) {
                result = merge(result, SignalState.PUSH);
            }

            ConnectableNode source = adapter.nodeAt(sourcePosition).orElse(null);
            if (source == null || !canPropagateControl(source, target)) {
                continue;
            }
            SignalState signal = controlSignalFrom(source, propagatedSignals);
            if (signal != SignalState.OFF) {
                result = merge(result, signal);
            }
        }
        return result;
    }

    private static @Nonnull SignalState controlSignalFrom(
            ConnectableNode source,
            Map<Vector3i, SignalState> propagatedSignals
    ) {
        return propagatedSignals.getOrDefault(source.position(), SignalState.OFF);
    }

    private static boolean shouldToggleInvert(
            @Nonnull ConnectableNode node,
            @Nonnull SignalState previousControlInput,
            @Nonnull SignalState controlInput
    ) {
        return node.invertCapable()
                && controlInput != SignalState.OFF
                && controlInput != previousControlInput;
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

    private static boolean canPropagateControl(@Nonnull ConnectableNode source, @Nonnull ConnectableNode target) {
        WorldSide sourceToTarget = ConnectableNeighborResolver.worldSideFromSourceToTarget(source.position(), target.position());
        if (sourceToTarget == null) {
            return false;
        }
        int sourceLocalSide = ConnectableNeighborResolver.localSideForWorldSide(source.rotation(), sourceToTarget);
        int targetLocalSide = ConnectableNeighborResolver.localSideForWorldSide(target.rotation(), sourceToTarget.opposite());
        return source.canOutputSignalTo(sourceLocalSide) && target.canReceiveControlFrom(targetLocalSide);
    }

    private static boolean canReceiveControlFrom(@Nonnull ConnectableNode target, @Nonnull Vector3i sourcePosition) {
        WorldSide sourceToTarget = ConnectableNeighborResolver.worldSideFromSourceToTarget(sourcePosition, target.position());
        if (sourceToTarget == null) {
            return false;
        }
        int targetLocalSide = ConnectableNeighborResolver.localSideForWorldSide(target.rotation(), sourceToTarget.opposite());
        return target.canReceiveControlFrom(targetLocalSide);
    }

    private static void enqueue(ArrayDeque<SignalStep> queue, Set<SignalStep> visited, Vector3i position, SignalState mode) {
        SignalStep step = new SignalStep(position, mode);
        if (visited.add(step)) {
            queue.addLast(step);
        }
    }

    private static boolean setSignal(Map<Vector3i, SignalState> signals, Vector3i position, SignalState mode) {
        SignalState current = signals.getOrDefault(position, SignalState.OFF);
        SignalState next = merge(current, mode);
        signals.put(position, next);
        return !next.equals(current);
    }

    private static @Nonnull SignalState merge(@Nonnull SignalState current, @Nonnull SignalState incoming) {
        if (current == SignalState.PUSH || incoming == SignalState.PUSH) {
            return SignalState.PUSH;
        }
        if (current == SignalState.PULL || incoming == SignalState.PULL) {
            return SignalState.PULL;
        }
        return SignalState.OFF;
    }

    private static @Nonnull SignalState outputSignalState(@Nonnull SignalState signalState, boolean invertEnabled) {
        return invertEnabled ? signalState.inverted() : signalState;
    }

    static @Nonnull String stateForSignal(@Nonnull SignalState mode) {
        return switch (mode) {
            case PUSH -> GravityPowderSpecialStateStore.STATE_PUSH;
            case PULL -> GravityPowderSpecialStateStore.STATE_PULL;
            case OFF -> GravityPowderSpecialStateStore.STATE_OFF;
        };
    }

    static @Nonnull SignalState signalForState(@Nonnull String state) {
        return switch (GravityPowderSpecialStateStore.normalizeState(state)) {
            case GravityPowderSpecialStateStore.STATE_PUSH -> SignalState.PUSH;
            case GravityPowderSpecialStateStore.STATE_PULL -> SignalState.PULL;
            default -> SignalState.OFF;
        };
    }

    public interface NodeAdapter {
        @Nonnull Set<Vector3i> nodePositions();

        @Nonnull Optional<ConnectableNode> nodeAt(@Nonnull Vector3i position);

        @Nonnull List<Vector3i> positionsAround(@Nonnull Vector3i position);

        boolean hasActiveSourceSignalTo(@Nonnull Vector3i sourcePosition, @Nonnull ConnectableNode target);

        boolean hasActiveSourceControlTo(@Nonnull Vector3i sourcePosition, @Nonnull ConnectableNode target);

        @Nonnull SignalState readControlInputMemory(@Nonnull ConnectableNode node);

        void writeInstantState(@Nonnull ConnectableNode node, @Nonnull SignalState signal);

        void writeInvertRuntime(@Nonnull ConnectableNode node, boolean invertEnabled, @Nonnull SignalState controlInputMemory);
    }

    private record SignalStep(@Nonnull Vector3i position, @Nonnull SignalState signalState) {
        private SignalStep {
            position = new Vector3i(Objects.requireNonNull(position, "position"));
            Objects.requireNonNull(signalState, "signalState");
        }
    }

    private record PropagationResult(@Nonnull Map<Vector3i, SignalState> nodeSignals) {
        private PropagationResult {
            nodeSignals = Map.copyOf(Objects.requireNonNull(nodeSignals, "nodeSignals"));
        }
    }

    private static final class WorldNodeAdapter implements NodeAdapter {
        private final World world;
        private final Set<Vector3i> nodes;

        private WorldNodeAdapter(World world, Set<Vector3i> affectedPositions) {
            this.world = Objects.requireNonNull(world, "world");
            Objects.requireNonNull(affectedPositions, "affectedPositions");
            this.nodes = new LinkedHashSet<>();
            for (ConnectableNode node : ConnectableNodeProvider.nodesForWorld(world).values()) {
                if (node.isSignalRuntimeNode()) {
                    this.nodes.add(node.position());
                }
            }
        }

        @Override
        public @Nonnull Set<Vector3i> nodePositions() {
            return Set.copyOf(nodes);
        }

        @Override
        public @Nonnull Optional<ConnectableNode> nodeAt(@Nonnull Vector3i position) {
            if (!nodes.contains(position)) {
                return Optional.empty();
            }
            return ConnectableNodeProvider.nodeAt(world, position);
        }

        @Override
        public @Nonnull List<Vector3i> positionsAround(@Nonnull Vector3i position) {
            return ConnectableNeighborResolver.positionsAround(position);
        }

        @Override
        public boolean hasActiveSourceSignalTo(@Nonnull Vector3i sourcePosition, @Nonnull ConnectableNode target) {
            if (!ConnectableNeighborResolver.isSourceNeighborOf(world, sourcePosition, target.position())) {
                return false;
            }
            return canReceiveSignalFromSource(target, sourcePosition);
        }

        @Override
        public boolean hasActiveSourceControlTo(@Nonnull Vector3i sourcePosition, @Nonnull ConnectableNode target) {
            if (!ConnectableNeighborResolver.isSourceNeighborOf(world, sourcePosition, target.position())) {
                return false;
            }
            return canReceiveControlFrom(target, sourcePosition);
        }

        private boolean canReceiveSignalFromSource(@Nonnull ConnectableNode target, @Nonnull Vector3i sourcePosition) {
            WorldSide sourceToTarget = ConnectableNeighborResolver.worldSideFromSourceToTarget(sourcePosition, target.position());
            if (sourceToTarget == null) {
                return false;
            }
            int targetLocalSide = ConnectableNeighborResolver.localSideForWorldSide(target.rotation(), sourceToTarget.opposite());
            return target.canReceiveSignalFrom(targetLocalSide);
        }

        @Override
        public @Nonnull SignalState readControlInputMemory(@Nonnull ConnectableNode node) {
            return readControlInputMemory(world, node.position());
        }

        @Override
        public void writeInstantState(@Nonnull ConnectableNode node, @Nonnull SignalState signal) {
            String previousInstantState = ConnectableRuntimeAccessor.instantState(world, node.position());
            ConnectableRuntimeAccessor.setInstantState(world, node.position(), stateForSignal(signal));
            if (!ConnectableRuntimeAccessor.instantState(world, node.position()).equals(previousInstantState)) {
                ConnectableRuntimeAccessor.setDirty(world, node.position(), true);
            }
        }

        @Override
        public void writeInvertRuntime(@Nonnull ConnectableNode node, boolean invertEnabled, @Nonnull SignalState controlInputMemory) {
            if (!node.invertCapable()) {
                return;
            }
            String previousInstantState = ConnectableRuntimeAccessor.instantState(world, node.position());
            String currentInstantState = ConnectableRuntimeAccessor.instantState(world, node.position());
            ConnectableRuntimeAccessor.setInverterState(
                    world,
                    node.position(),
                    currentInstantState,
                    currentInstantState,
                    invertEnabled,
                    stateForSignal(controlInputMemory)
            );
            if (!ConnectableRuntimeAccessor.instantState(world, node.position()).equals(previousInstantState)) {
                ConnectableRuntimeAccessor.setDirty(world, node.position(), true);
            }
        }

        private static @Nonnull SignalState readControlInputMemory(@Nonnull World world, @Nonnull Vector3i position) {
            InverterData data = InverterSpecialStateStore.get(world, position);
            if (data == null) {
                return SignalState.OFF;
            }
            return signalForState(data.lastToggleInputMode());
        }
    }
}
