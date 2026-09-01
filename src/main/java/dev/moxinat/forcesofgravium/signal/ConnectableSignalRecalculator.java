package dev.moxinat.forcesofgravium.signal;

import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.universe.world.World;
import dev.moxinat.forcesofgravium.ForcesOfGraviumPlugin;
import dev.moxinat.forcesofgravium.data.NodeComponent;
import dev.moxinat.forcesofgravium.spatial.ConnectableNeighborResolver;
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

        NodeComponent startNode = nodeAt(world, position);
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

                NodeComponent inverterNode = nodeAt(world, inverterPosition);
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
                    NodeComponent dependencyNode =
                            nodeAt(world, frame.startPosition);

                    if (dependencyNode != null
                            && dependencyNode.instantState() != frame.resolvedState) {

                        ConnectablePropagationScheduler.cancelPendingAdoption(
                                world,
                                frame.startPosition
                        );

                        dependencyNode.setInstantState(frame.resolvedState);
                        dependencyNode.setDirty(true);
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

            NodeComponent currentNode = nodeAt(world, currentPosition);
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

        startNode = nodeAt(world, position);
        if (startNode == null) {
            return;
        }

        if (startNode.instantState() != resolvedState) {

            ConnectablePropagationScheduler.cancelPendingAdoption(
                    world,
                    position
            );

            startNode.setInstantState(resolvedState);
            startNode.setDirty(true);
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

            NodeComponent currentNode = nodeAt(world, currentPosition);
            if (currentNode == null) {
                continue;
            }

            if (currentNode.instantState() != forwardState) {

                ConnectablePropagationScheduler.cancelPendingAdoption(
                        world,
                        currentPosition
                );

                currentNode.setInstantState(forwardState);
                currentNode.setDirty(true);
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

    private static NodeComponent nodeAt(
            World world,
            Vector3i position
    ) {
        return BlockModule.getComponent(
                ForcesOfGraviumPlugin.NODE_COMPONENT_TYPE,
                world,
                position.x(),
                position.y(),
                position.z()
        );
    }
}
