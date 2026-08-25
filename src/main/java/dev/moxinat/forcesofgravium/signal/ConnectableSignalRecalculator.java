package dev.moxinat.forcesofgravium.signal;

import com.hypixel.hytale.server.core.universe.world.World;
import dev.moxinat.forcesofgravium.spatial.ConnectableNeighborResolver;
import dev.moxinat.forcesofgravium.data.Nodes;
import dev.moxinat.forcesofgravium.data.Nodes.Node;
import org.joml.Vector3i;

import javax.annotation.Nonnull;
import java.util.ArrayDeque;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

public final class ConnectableSignalRecalculator {

    private ConnectableSignalRecalculator() {
    }

    private static final class RecomputeFrame {
        private final Vector3i startPosition;
        private final ArrayDeque<Vector3i> backwardsStack = new ArrayDeque<>();
        private final Set<Vector3i> backwardsVisited = new LinkedHashSet<>();

        private SignalState resolvedState = SignalState.OFF;
        private Vector3i waitingForInverter;

        private RecomputeFrame(Vector3i startPosition) {
            this.startPosition = new Vector3i(startPosition);
            this.backwardsStack.push(new Vector3i(startPosition));
        }
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

        ArrayDeque<RecomputeFrame> recomputeStack = new ArrayDeque<>();
        Set<Vector3i> recomputing = new LinkedHashSet<>();

        RecomputeFrame rootFrame = new RecomputeFrame(position);

        recomputeStack.push(rootFrame);
        recomputing.add(position);

        SignalState resolvedState = SignalState.OFF;

        while (!recomputeStack.isEmpty()) {
            RecomputeFrame frame = recomputeStack.peek();

            // We previously paused this frame because an inverter first had to
            // recompute its own input. That inverter is now resolved.
            if (frame.waitingForInverter != null) {
                Vector3i inverterPosition = frame.waitingForInverter;
                frame.waitingForInverter = null;

                Node inverterNode = Nodes.get(world, inverterPosition);
                if (inverterNode == null) {
                    continue;
                }

                SignalState output = inverterNode.instantState().inverted();

                if (output == SignalState.PUSH) {
                    frame.resolvedState = SignalState.PUSH;
                    frame.backwardsStack.clear();
                } else if (output == SignalState.PULL) {
                    frame.resolvedState = SignalState.PULL;
                }

                continue;
            }

            // This frame has completely searched backwards.
            if (frame.backwardsStack.isEmpty()) {
                recomputeStack.pop();
                recomputing.remove(frame.startPosition);

                // Child frames represent inverter dependencies.
                // Their instant state must be updated before the parent can use them.
                if (!frame.startPosition.equals(position)) {
                    Node dependencyNode = Nodes.get(world, frame.startPosition);

                    if (dependencyNode != null
                            && dependencyNode.instantState() != frame.resolvedState) {

                        dependencyNode = dependencyNode
                                .withInstantState(frame.resolvedState)
                                .withDirty(true);

                        Nodes.put(world, dependencyNode);
                    }
                } else {
                    // Root result is handled by the existing SET START NODE section.
                    resolvedState = frame.resolvedState;
                }

                continue;
            }

            Vector3i currentPosition = frame.backwardsStack.pop();

            if (!frame.backwardsVisited.add(currentPosition)) {
                continue;
            }

            Node currentNode = Nodes.get(world, currentPosition);
            if (currentNode == null) {
                continue;
            }

            // The start node of this frame itself is never treated as a boundary.
            if (!currentPosition.equals(frame.startPosition)) {

                if (currentNode.invertEnabled()) {

                    // If this inverter is already being recomputed higher in the
                    // dependency stack, following it would create a recompute cycle.
                    // It therefore cannot act as a valid boundary for this path.
                    if (recomputing.contains(currentPosition)) {
                        continue;
                    }

                    // Pause the current frame and resolve the inverter first.
                    frame.waitingForInverter = currentPosition;

                    RecomputeFrame dependencyFrame =
                            new RecomputeFrame(currentPosition);

                    recomputeStack.push(dependencyFrame);
                    recomputing.add(currentPosition);

                    continue;
                }

                // Powered nodes are sources and therefore known boundaries.
                if (currentNode.energyDelta() > 0) {
                    SignalState output = currentNode.instantState();

                    if (output == SignalState.PUSH) {
                        frame.resolvedState = SignalState.PUSH;
                        frame.backwardsStack.clear();
                        continue;
                    }

                    if (output == SignalState.PULL) {
                        frame.resolvedState = SignalState.PULL;
                    }
                }
            }

            for (Vector3i backwardNeighbor :
                    ConnectableNeighborResolver.allBackwardSignalNeighbors(
                            world,
                            currentPosition
                    )) {

                if (!frame.backwardsVisited.contains(backwardNeighbor)) {
                    frame.backwardsStack.push(backwardNeighbor);
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
}
