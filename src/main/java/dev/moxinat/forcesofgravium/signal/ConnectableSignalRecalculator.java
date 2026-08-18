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
}
