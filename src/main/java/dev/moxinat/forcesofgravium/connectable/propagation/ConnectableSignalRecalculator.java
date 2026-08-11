package dev.moxinat.forcesofgravium.connectable.propagation;

import com.hypixel.hytale.server.core.universe.world.World;
import dev.moxinat.forcesofgravium.block.gravity.GravityPowderSpecialStateStore;
import dev.moxinat.forcesofgravium.block.inverter.InverterSpecialStateStore;
import dev.moxinat.forcesofgravium.block.inverter.InverterSpecialStateStore.InverterData;
import dev.moxinat.forcesofgravium.connectable.SignalState;
import dev.moxinat.forcesofgravium.connectable.spatial.ConnectableNeighborResolver;
import dev.moxinat.forcesofgravium.connectable.spatial.ConnectableNeighborResolver.WorldSide;
import dev.moxinat.forcesofgravium.data.Nodes;
import dev.moxinat.forcesofgravium.data.Nodes.Node;
import org.joml.Vector3i;

import javax.annotation.Nonnull;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class ConnectableSignalRecalculator {

    private ConnectableSignalRecalculator() {
    }

    public static void recompute(
            @Nonnull World world,
            @Nonnull Vector3i position
    ) {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(position, "position");

        Node startNode = Nodes.get(world, position);
        if (startNode == null) {
            return;
        }

        // -------------------------
        // BACKWARDS
        // Find the current instant state at this position.
        // -------------------------

        ArrayDeque<Vector3i> backwardsStack = new ArrayDeque<>();
        Set<Vector3i> backwardsVisited = new LinkedHashSet<>();

        backwardsStack.push(position);

        SignalState resolvedState = SignalState.OFF;

        while (!backwardsStack.isEmpty()) {
            Vector3i currentPosition = backwardsStack.pop();

            if (!backwardsVisited.add(currentPosition)) {
                continue;
            }

            Node currentNode = Nodes.get(world, currentPosition);
            if (currentNode == null) {
                continue;
            }

            // The start node itself is never treated as a boundary.
            if (!currentPosition.equals(position)) {

                // An enabled inverter is a known boundary.
                // Its instant state represents its input,
                // so its output is the inverted instant state.
                if (currentNode.invertEnabled()) {
                    SignalState output = currentNode.instantState().inverted();

                    if (output == SignalState.PUSH) {
                        resolvedState = SignalState.PUSH;
                        break;
                    }

                    if (output == SignalState.PULL) {
                        resolvedState = SignalState.PULL;
                    }

                    // Never search behind an enabled inverter.
                    continue;
                }

                // Powered nodes are sources and therefore known boundaries.
                if (currentNode.energyDelta() > 0) {
                    SignalState output = currentNode.instantState();

                    if (output == SignalState.PUSH) {
                        resolvedState = SignalState.PUSH;
                        break;
                    }

                    if (output == SignalState.PULL) {
                        resolvedState = SignalState.PULL;
                    }

                }
            }

            for (Vector3i backwardNeighbor :
                    ConnectableNeighborResolver.allBackwardSignalNeighbors(
                            world,
                            currentPosition
                    )) {

                if (!backwardsVisited.contains(backwardNeighbor)) {
                    backwardsStack.push(backwardNeighbor);
                }
            }
        }

        // -------------------------
        // SET START NODE
        // -------------------------

        startNode = Nodes.get(world, position);
        if (startNode == null) {
            return;
        }

        if (startNode.instantState() != resolvedState) {
            startNode = startNode
                    .withInstantState(resolvedState)
                    .withDirty(true);

            Nodes.put(world, startNode);
        }

        // The instant state is the state at the node's input.
        // If this node actively inverts, its outgoing signal is inverted.
        SignalState forwardState = startNode.invertEnabled()
                ? resolvedState.inverted()
                : resolvedState;

        // -------------------------
        // FORWARDS
        // Spread the resolved state until enabled inverters.
        // -------------------------

        ArrayDeque<Vector3i> forwardStack = new ArrayDeque<>();
        Set<Vector3i> forwardVisited = new LinkedHashSet<>();

        forwardVisited.add(position);

        for (Vector3i forwardNeighbor :
                ConnectableNeighborResolver.allForwardSignalNeighbors(
                        world,
                        position
                )) {

            forwardStack.push(forwardNeighbor);
        }

        while (!forwardStack.isEmpty()) {
            Vector3i currentPosition = forwardStack.pop();

            if (!forwardVisited.add(currentPosition)) {
                continue;
            }

            Node currentNode = Nodes.get(world, currentPosition);
            if (currentNode == null) {
                continue;
            }

            if (currentNode.instantState() != forwardState) {
                currentNode = currentNode
                        .withInstantState(forwardState)
                        .withDirty(true);

                Nodes.put(world, currentNode);
            }

            // The inverter itself receives the current signal,
            // but its inverted output belongs to the next section.
            if (currentNode.invertEnabled()) {
                continue;
            }

            for (Vector3i forwardNeighbor :
                    ConnectableNeighborResolver.allForwardSignalNeighbors(
                            world,
                            currentPosition
                    )) {

                if (!forwardVisited.contains(forwardNeighbor)) {
                    forwardStack.push(forwardNeighbor);
                }
            }
        }
    }

    private static @Nonnull PropagationResult propagate(
            @Nonnull World world,
            @Nonnull Map<Vector3i, Boolean> invertEnabled
    ) {
        Map<Vector3i, SignalState> nodeSignals = new LinkedHashMap<>();
        for (Node node : Nodes.snapshotForWorld(world).values()) {
            if (node.shouldStorePropagatedInstantState()) {
                nodeSignals.put(node.position(), SignalState.OFF);
            }
        }

        ArrayDeque<SignalStep> queue = new ArrayDeque<>();
        Set<SignalStep> visited = new LinkedHashSet<>();
        seedSources(world, queue, visited);

        while (!queue.isEmpty()) {
            SignalStep step = queue.removeFirst();
            Node node = Nodes.get(world, step.position());
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
            addNodeOutputs(world, node, outputSignal, queue, visited);
        }

        return new PropagationResult(nodeSignals);
    }

    private static void seedSources(
            @Nonnull World world,
            @Nonnull ArrayDeque<SignalStep> queue,
            @Nonnull Set<SignalStep> visited
    ) {
        for (Node node : Nodes.snapshotForWorld(world).values()) {
            if (node.signalInputSides() != 0 || node.signalOutputSides() == 0 || node.instantState() == SignalState.OFF) {
                continue;
            }
            enqueue(queue, visited, node.position(), node.instantState());
        }
    }

    private static void addNodeOutputs(
            @Nonnull World world,
            @Nonnull Node source,
            @Nonnull SignalState signal,
            @Nonnull ArrayDeque<SignalStep> queue,
            @Nonnull Set<SignalStep> visited
    ) {
        for (Vector3i neighborPosition : ConnectableNeighborResolver.positionsAround(source.position())) {
            if (neighborPosition.equals(source.position())) {
                continue;
            }
            Node target = Nodes.get(world, neighborPosition);
            if (target == null || !canPropagateSignal(source, target)) {
                continue;
            }
            enqueue(queue, visited, target.position(), signal);
        }
    }

    private static @Nonnull SignalState controlInputSignal(
            @Nonnull World world,
            @Nonnull Node target,
            @Nonnull Map<Vector3i, SignalState> propagatedSignals
    ) {
        SignalState result = SignalState.OFF;
        for (Vector3i sourcePosition : ConnectableNeighborResolver.positionsAround(target.position())) {
            if (sourcePosition.equals(target.position())) {
                continue;
            }

            Node source = Nodes.get(world, sourcePosition);
            if (source == null || !canPropagateControl(source, target)) {
                continue;
            }

            SignalState signal = source.signalInputSides() == 0
                    ? source.instantState()
                    : controlSignalFrom(source, propagatedSignals);
            if (signal != SignalState.OFF) {
                result = merge(result, signal);
            }
        }
        return result;
    }

    private static @Nonnull SignalState controlSignalFrom(
            @Nonnull Node source,
            @Nonnull Map<Vector3i, SignalState> propagatedSignals
    ) {
        return propagatedSignals.getOrDefault(source.position(), SignalState.OFF);
    }

    private static boolean shouldToggleInvert(
            @Nonnull Node node,
            @Nonnull SignalState previousControlInput,
            @Nonnull SignalState controlInput
    ) {
        return node.invertCapable()
                && controlInput != SignalState.OFF
                && controlInput != previousControlInput;
    }

    private static boolean canPropagateSignal(@Nonnull Node source, @Nonnull Node target) {
        WorldSide sourceToTarget = ConnectableNeighborResolver.worldSideFromSourceToTarget(source.position(), target.position());
        if (sourceToTarget == null) {
            return false;
        }
        int sourceLocalSide = ConnectableNeighborResolver.localSideForWorldSide(source.rotation(), sourceToTarget);
        int targetLocalSide = ConnectableNeighborResolver.localSideForWorldSide(target.rotation(), sourceToTarget.opposite());
        return source.canOutputSignalTo(sourceLocalSide) && target.canReceiveSignalFrom(targetLocalSide);
    }

    private static boolean canPropagateControl(@Nonnull Node source, @Nonnull Node target) {
        WorldSide sourceToTarget = ConnectableNeighborResolver.worldSideFromSourceToTarget(source.position(), target.position());
        if (sourceToTarget == null) {
            return false;
        }
        int sourceLocalSide = ConnectableNeighborResolver.localSideForWorldSide(source.rotation(), sourceToTarget);
        int targetLocalSide = ConnectableNeighborResolver.localSideForWorldSide(target.rotation(), sourceToTarget.opposite());
        return source.canOutputSignalTo(sourceLocalSide) && target.canReceiveControlFrom(targetLocalSide);
    }

    private static void enqueue(
            @Nonnull ArrayDeque<SignalStep> queue,
            @Nonnull Set<SignalStep> visited,
            @Nonnull Vector3i position,
            @Nonnull SignalState mode
    ) {
        SignalStep step = new SignalStep(position, mode);
        if (visited.add(step)) {
            queue.addLast(step);
        }
    }

    private static boolean setSignal(
            @Nonnull Map<Vector3i, SignalState> signals,
            @Nonnull Vector3i position,
            @Nonnull SignalState mode
    ) {
        SignalState current = signals.getOrDefault(position, SignalState.OFF);
        SignalState next = merge(current, mode);
        signals.put(position, next);
        return next != current;
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

    private static @Nonnull SignalState readControlInputMemory(@Nonnull World world, @Nonnull Vector3i position) {
        InverterData data = InverterSpecialStateStore.get(world, position);
        if (data == null) {
            return SignalState.OFF;
        }
        return signalForState(data.lastToggleInputMode());
    }

    private static void writeInstantState(@Nonnull World world, @Nonnull Node node, @Nonnull SignalState signal) {
        if (node.instantState() == signal) {
            return;
        }
        Nodes.put(world, node.withInstantState(signal).withDirty(true));
    }

    private static void writeInvertRuntime(
            @Nonnull World world,
            @Nonnull Node node,
            boolean invertEnabled,
            @Nonnull SignalState controlInputMemory
    ) {
        if (!node.invertCapable()) {
            return;
        }

        Node current = Nodes.get(world, node.position());
        if (current == null) {
            return;
        }

        if (current.invertEnabled() != invertEnabled) {
            current = current.withInvertEnabled(invertEnabled);
            Nodes.put(world, current);
        }

        String currentState = stateForSignal(current.instantState());
        InverterSpecialStateStore.setState(
                world,
                current.position(),
                currentState,
                currentState,
                invertEnabled,
                stateForSignal(controlInputMemory)
        );
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
}
