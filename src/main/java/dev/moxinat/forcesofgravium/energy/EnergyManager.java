package dev.moxinat.forcesofgravium.energy;

import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.universe.world.World;
import dev.moxinat.forcesofgravium.ForcesOfGraviumPlugin;
import dev.moxinat.forcesofgravium.data.NetworkResource;
import dev.moxinat.forcesofgravium.data.NodeComponent;
import dev.moxinat.forcesofgravium.signal.SignalState;
import dev.moxinat.forcesofgravium.dispatcher.ConnectableVisualDispatcher;
import org.joml.Vector3i;

import javax.annotation.Nonnull;

public final class EnergyManager {

    private static final long FAILURE_STATE_TICKS = 5L;
    private static final SignalState[] FAILURE_SEQUENCE = {
            SignalState.PUSH,
            SignalState.PULL,
            SignalState.PUSH,
            SignalState.PULL,
            SignalState.OFF
    };

    private EnergyManager() {
    }

    public static void checkNetwork(
            @Nonnull World world,
            @Nonnull Vector3i position
    ) {
        NodeComponent node =
                nodeAt(world, position);

        if (node == null
                || node.networkId() == NodeComponent.NO_NETWORK) {
            return;
        }

        long networkId =
                node.networkId();

        NetworkResource networks =
                networks(world);

        if (!networks.containsNetwork(networkId)) {
            return;
        }

        int energy = 0;
        boolean hasEnergySource = false;

        for (Vector3i memberPosition :
                networks.members(networkId)) {

            NodeComponent networkNode =
                    nodeAt(
                            world,
                            memberPosition
                    );

            if (networkNode == null) {
                continue;
            }

            energy += networkNode.energyDelta();

            if (networkNode.energyDelta() > 0) {
                hasEnergySource = true;
            }
        }

        networks.setEnergy(
                networkId,
                energy
        );

        if (hasEnergySource && energy < 0) {
            failNetwork(
                    world,
                    networkId
            );
        } else if (networks.isFailing(networkId)) {
            recoverNetwork(
                    world,
                    networkId
            );
        }
    }

    public static int remainingEnergy(
            @Nonnull World world,
            @Nonnull Vector3i position
    ) {
        NodeComponent node =
                nodeAt(world, position);

        if (node == null
                || node.networkId() == NodeComponent.NO_NETWORK) {
            return 0;
        }

        return networks(world)
                .energy(node.networkId());
    }

    public static void tickWorld(
            @Nonnull World world
    ) {
        NetworkResource networks =
                networks(world);

        for (long networkId :
                networks.networkIds()) {

            if (!networks.isFailing(networkId)) {
                continue;
            }

            long remainingTicks =
                    networks.failureRemainingTicks(
                            networkId
                    );

            if (remainingTicks > 0) {
                remainingTicks--;

                networks.setFailureState(
                        networkId,
                        networks.failureStep(networkId),
                        remainingTicks
                );

                if (remainingTicks > 0) {
                    continue;
                }
            }

            int step =
                    networks.failureStep(networkId);

            SignalState state =
                    FAILURE_SEQUENCE[step];

            if (state == SignalState.OFF) {
                if (networks.pendingFailureOff(networkId).isEmpty()) {
                    for (Vector3i position :
                            networks.members(networkId)) {

                        networks.addPendingFailureOff(
                                networkId,
                                position
                        );
                    }
                }

                for (Vector3i position :
                        networks.pendingFailureOff(networkId)) {

                    NodeComponent node =
                            nodeAt(
                                    world,
                                    position
                            );

                    if (node == null) {
                        continue;
                    }

                    node.setEffectiveState(SignalState.OFF);
                    node.setInstantState(SignalState.OFF);
                    node.setDirty(false);

                    ConnectableVisualDispatcher.refreshAt(
                            world,
                            position
                    );

                    networks.removePendingFailureOff(
                            networkId,
                            position
                    );
                }

                if (networks.pendingFailureOff(networkId).isEmpty()) {
                    networks.clearFailure(
                            networkId
                    );
                }

                continue;
            }

            for (Vector3i position :
                    networks.members(networkId)) {

                NodeComponent node =
                        nodeAt(
                                world,
                                position
                        );

                if (node == null) {
                    continue;
                }

                node.setEffectiveState(state);
                node.setDirty(false);

                ConnectableVisualDispatcher.refreshAt(
                        world,
                        position
                );
            }

            int nextStep =
                    step + 1;

            networks.setFailureState(
                    networkId,
                    nextStep,
                    FAILURE_STATE_TICKS
            );
        }
    }

    private static void failNetwork(
            @Nonnull World world,
            long networkId
    ) {
        NetworkResource networks =
                networks(world);

        if (networks.isFailing(networkId)) {
            return;
        }

        networks.clearPendingFailureOff(
                networkId
        );

        networks.setFailureState(
                networkId,
                0,
                0L
        );
    }

    private static void recoverNetwork(
            @Nonnull World world,
            long networkId
    ) {
        NetworkResource networks =
                networks(world);

        for (Vector3i position :
                networks.members(networkId)) {

            NodeComponent node =
                    nodeAt(
                            world,
                            position
                    );

            if (node == null) {
                continue;
            }

            node.setEffectiveState(SignalState.OFF);
            node.setDirty(false);

            ConnectableVisualDispatcher.refreshAt(
                    world,
                    position
            );
        }

        networks.clearPendingFailureOff(
                networkId
        );

        networks.clearFailure(
                networkId
        );
    }

    private static NetworkResource networks(
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
            @Nonnull World world,
            @Nonnull Vector3i position
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
