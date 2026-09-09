package dev.moxinat.forcesofgravium.signal;

import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.universe.world.World;
import dev.moxinat.forcesofgravium.ForcesOfGraviumPlugin;
import dev.moxinat.forcesofgravium.data.NetworkResource;
import dev.moxinat.forcesofgravium.data.NodeComponent;
import dev.moxinat.forcesofgravium.data.Nodes;
import dev.moxinat.forcesofgravium.spatial.ConnectableNeighborResolver;
import org.joml.Vector3i;

import javax.annotation.Nonnull;
import java.util.ArrayDeque;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

public final class ConnectableSignalRecalculator {

    private static final Vector3i DEBUG_TARGET =
            new Vector3i(543, 123, 64);

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
            if (isDebugTarget(position)) {
                debugTarget("RECOMPUTE start node=null");
            }
            return;
        }

        if (isDebugTarget(position)) {
            trackAndLog(
                    startNode,
                    "RECOMPUTE START"
                            + " instant=" + startNode.instantState()
                            + " effective=" + startNode.effectiveState()
                            + " dirty=" + startNode.dirty()
            );
        }

        NetworkResource networks = networkResource(world);

        ArrayDeque<RecomputeFrame> recomputeStack = new ArrayDeque<>();
        Set<Vector3i> recomputing = new LinkedHashSet<>();

        RecomputeFrame rootFrame = new RecomputeFrame(position);

        recomputeStack.push(rootFrame);
        recomputing.add(position);

        SignalState resolvedState = SignalState.OFF;

        while (!recomputeStack.isEmpty()) {
            RecomputeFrame frame = recomputeStack.peek();

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

            if (frame.backwardsStack.isEmpty()) {
                recomputeStack.pop();
                recomputing.remove(frame.startPosition);

                if (!frame.startPosition.equals(position)) {
                    NodeComponent dependencyNode =
                            nodeAt(world, frame.startPosition);

                    if (dependencyNode != null
                            && dependencyNode.instantState() != frame.resolvedState) {

                        if (isDebugTarget(frame.startPosition)) {
                            trackAndLog(
                                    dependencyNode,
                                    "DEPENDENCY SET"
                                            + " from=" + dependencyNode.instantState()
                                            + " to=" + frame.resolvedState
                                            + " dirtyBefore=" + dependencyNode.dirty()
                            );
                        }

                        ConnectablePropagationScheduler.cancelPendingAdoption(
                                world,
                                frame.startPosition
                        );

                        SignalState dependencyState = frame.resolvedState;
                        Nodes.mutate(world, frame.startPosition, currentNode -> {
                            currentNode.setInstantState(dependencyState);
                            currentNode.setDirty(true);
                        });
                    }
                } else {
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
                if (isDebugTarget(currentPosition)) {
                    debugTarget("BACKWARD VISIT node=null");
                }
                continue;
            }

            if (isDebugTarget(currentPosition)) {
                trackAndLog(
                        currentNode,
                        "BACKWARD VISIT"
                                + " frameStart=" + frame.startPosition
                                + " instant=" + currentNode.instantState()
                                + " effective=" + currentNode.effectiveState()
                                + " dirty=" + currentNode.dirty()
                );
            }

            if (!currentPosition.equals(frame.startPosition)) {

                if (currentNode.invertEnabled()) {
                    if (recomputing.contains(currentPosition)) {
                        continue;
                    }

                    frame.waitingForInverter = currentPosition;

                    RecomputeFrame dependencyFrame =
                            new RecomputeFrame(currentPosition);

                    recomputeStack.push(dependencyFrame);
                    recomputing.add(currentPosition);

                    continue;
                }

                long currentNetworkId = networks.networkAt(currentPosition);

                if (currentNetworkId != NetworkResource.NO_NETWORK
                        && networks.energyDelta(
                                currentNetworkId,
                                currentPosition
                        ) > 0) {
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

        startNode = nodeAt(world, position);
        if (startNode == null) {
            return;
        }

        if (startNode.instantState() != resolvedState) {

            if (isDebugTarget(position)) {
                trackAndLog(
                        startNode,
                        "ROOT SET"
                                + " from=" + startNode.instantState()
                                + " to=" + resolvedState
                                + " dirtyBefore=" + startNode.dirty()
                );
            }

            ConnectablePropagationScheduler.cancelPendingAdoption(
                    world,
                    position
            );

            SignalState rootState = resolvedState;
            Nodes.mutate(world, position, currentNode -> {
                currentNode.setInstantState(rootState);
                currentNode.setDirty(true);
            });

            startNode = nodeAt(world, position);
            if (startNode == null) {
                return;
            }
        }

        SignalState forwardState = startNode.invertEnabled()
                ? resolvedState.inverted()
                : resolvedState;

        if (isDebugTarget(position)) {
            trackAndLog(
                    startNode,
                    "ROOT RESOLVED"
                            + " resolved=" + resolvedState
                            + " forward=" + forwardState
                            + " instantNow=" + startNode.instantState()
                            + " dirtyNow=" + startNode.dirty()
            );
        }

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
                if (isDebugTarget(currentPosition)) {
                    debugTarget(
                            "FORWARD VISIT node=null"
                                    + " recomputeStart=" + position
                                    + " forwardState=" + forwardState
                    );
                }
                continue;
            }

            if (isDebugTarget(currentPosition)) {
                trackAndLog(
                        currentNode,
                        "FORWARD VISIT"
                                + " recomputeStart=" + position
                                + " forwardState=" + forwardState
                                + " instantBefore=" + currentNode.instantState()
                                + " effective=" + currentNode.effectiveState()
                                + " dirtyBefore=" + currentNode.dirty()
                );
            }

            if (currentNode.instantState() != forwardState) {

                if (isDebugTarget(currentPosition)) {
                    debugTarget(
                            "FORWARD SET"
                                    + " from=" + currentNode.instantState()
                                    + " to=" + forwardState
                    );
                }

                ConnectablePropagationScheduler.cancelPendingAdoption(
                        world,
                        currentPosition
                );

                SignalState stateToApply = forwardState;
                Nodes.mutate(world, currentPosition, nodeToMutate -> {
                    nodeToMutate.setInstantState(stateToApply);
                    nodeToMutate.setDirty(true);
                });

                currentNode = nodeAt(world, currentPosition);
                if (currentNode == null) {
                    continue;
                }
            }

            if (currentNode.invertEnabled()) {
                if (isDebugTarget(currentPosition)) {
                    debugTarget("FORWARD STOP inverter=true");
                }
                continue;
            }

            Set<Vector3i> forwardNeighbors =
                    ConnectableNeighborResolver.allForwardSignalNeighbors(
                            world,
                            currentPosition
                    );

            if (isDebugTarget(currentPosition)) {
                debugTarget(
                        "FORWARD CONTINUE neighbors=" + forwardNeighbors
                );
            }

            for (Vector3i forwardNeighbor : forwardNeighbors) {

                if (!forwardVisited.contains(forwardNeighbor)) {
                    forwardStack.push(forwardNeighbor);
                }
            }
        }
    }

    private static boolean isDebugTarget(@Nonnull Vector3i position) {
        return DEBUG_TARGET.equals(position);
    }

    private static void trackAndLog(
            @Nonnull NodeComponent node,
            @Nonnull String message
    ) {
        NodeComponent.debugTrackForSave(node);
        debugTarget(message);
    }

    private static void debugTarget(@Nonnull String message) {
        System.out.println(
                "[FoG Target Debug][Recompute][543,123,64] " + message
        );
    }

    private static NetworkResource networkResource(
            @Nonnull World world
    ) {
        return world
                .getChunkStore()
                .getStore()
                .getResource(
                        ForcesOfGraviumPlugin.NETWORK_RESOURCE_TYPE
                );
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
