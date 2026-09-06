package dev.moxinat.forcesofgravium.network;

import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.universe.world.World;
import dev.moxinat.forcesofgravium.ForcesOfGraviumPlugin;
import dev.moxinat.forcesofgravium.data.NetworkResource;
import dev.moxinat.forcesofgravium.data.NodeComponent;
import dev.moxinat.forcesofgravium.spatial.ConnectableNeighborResolver;
import org.joml.Vector3i;

import javax.annotation.Nonnull;
import java.util.ArrayDeque;
import java.util.LinkedHashSet;
import java.util.Set;

public class ConnectableNetworkManager {

    public static void onNodePlaced(
            @Nonnull World world,
            @Nonnull Vector3i position
    ) {
        NodeComponent node =
                nodeAt(
                        world,
                        position
                );

        if (node == null) {
            return;
        }

        NetworkResource networks =
                networks(world);

        Set<Vector3i> neighbors =
                ConnectableNeighborResolver.allNetworkNeighbors(
                        world,
                        position
                );

        LinkedHashSet<Long> neighborNetworkIds =
                new LinkedHashSet<>();

        for (Vector3i neighborPosition : neighbors) {
            long neighborNetworkId =
                    networkIdAt(
                            networks,
                            neighborPosition
                    );

            if (neighborNetworkId != NodeComponent.NO_NETWORK) {
                neighborNetworkIds.add(
                        neighborNetworkId
                );
            }
        }

        long targetNetworkId;

        if (neighborNetworkIds.isEmpty()) {
            targetNetworkId =
                    networks.createNetwork();
        } else {
            targetNetworkId =
                    neighborNetworkIds.iterator().next();

            for (long sourceNetworkId : neighborNetworkIds) {
                if (sourceNetworkId == targetNetworkId) {
                    continue;
                }

                mergeNetworkInto(
                        world,
                        networks,
                        sourceNetworkId,
                        targetNetworkId
                );
            }
        }

        networks.addMember(
                targetNetworkId,
                position
        );

        networks.setEnergyDelta(
                targetNetworkId,
                position,
                node.energyDelta()
        );

        node.setNetworkId(
                targetNetworkId
        );

        for (Vector3i neighborPosition : neighbors) {
            if (networkIdAt(
                    networks,
                    neighborPosition
            ) != targetNetworkId) {
                continue;
            }

            networks.addEdge(
                    targetNetworkId,
                    position,
                    neighborPosition
            );
        }

        networks.setEnergy(
                targetNetworkId,
                graphEnergy(
                        networks,
                        targetNetworkId
                )
        );
    }

    public static @Nonnull Set<Vector3i> onNodeBroken(
            @Nonnull World world,
            @Nonnull Vector3i position
    ) {
        NetworkResource networks =
                networks(world);

        long networkId =
                networkIdAt(
                        networks,
                        position
                );

        if (networkId == NodeComponent.NO_NETWORK) {
            return Set.of();
        }

        Set<Vector3i> formerNeighbours =
                copyPositions(
                        networks.neighbours(
                                networkId,
                                position
                        )
                );

        boolean wasFailing =
                networks.isFailing(networkId);

        int failureStep =
                networks.failureStep(networkId);

        long failureRemainingTicks =
                networks.failureRemainingTicks(
                        networkId
                );

        Set<Vector3i> pendingFailureOff =
                networks.pendingFailureOff(
                        networkId
                );

        networks.removeMember(
                networkId,
                position
        );

        networks.removePendingFailureOff(
                networkId,
                position
        );

        if (networks.members(networkId).isEmpty()) {
            networks.removeNetwork(
                    networkId
            );
            return formerNeighbours;
        }

        Set<Set<Vector3i>> components =
                connectedComponents(
                        networks,
                        networkId
                );

        if (components.size() == 1) {
            networks.setEnergy(
                    networkId,
                    graphEnergy(
                            networks,
                            networkId
                    )
            );
            return formerNeighbours;
        }

        Set<Vector3i> retainedComponent =
                components.iterator().next();

        networks.clearPendingFailureOff(
                networkId
        );

        for (Vector3i pendingPosition :
                pendingFailureOff) {

            if (retainedComponent.contains(pendingPosition)) {
                networks.addPendingFailureOff(
                        networkId,
                        pendingPosition
                );
            }
        }

        boolean firstComponent = true;

        for (Set<Vector3i> component : components) {
            if (firstComponent) {
                firstComponent = false;
                continue;
            }

            long splitNetworkId =
                    networks.createNetwork();

            copyComponent(
                    world,
                    networks,
                    networkId,
                    splitNetworkId,
                    component
            );

            if (wasFailing) {
                networks.setFailureState(
                        splitNetworkId,
                        failureStep,
                        failureRemainingTicks
                );
            }

            for (Vector3i pendingPosition :
                    pendingFailureOff) {

                if (component.contains(pendingPosition)) {
                    networks.addPendingFailureOff(
                            splitNetworkId,
                            pendingPosition
                    );
                }
            }

            networks.setEnergy(
                    splitNetworkId,
                    graphEnergy(
                            networks,
                            splitNetworkId
                    )
            );

            for (Vector3i member : component) {
                networks.removeMember(
                        networkId,
                        member
                );
            }
        }

        networks.setEnergy(
                networkId,
                graphEnergy(
                        networks,
                        networkId
                )
        );

        return formerNeighbours;
    }

    public static void updateNodeNetwork(
            @Nonnull World world,
            @Nonnull Vector3i position
    ) {
        onNodeBroken(
                world,
                position
        );

        NodeComponent node =
                nodeAt(
                        world,
                        position
                );

        if (node == null) {
            return;
        }

        node.setNetworkId(
                NodeComponent.NO_NETWORK
        );

        onNodePlaced(
                world,
                position
        );
    }

    public static void updateNodeNetwork(
            @Nonnull World world,
            @Nonnull Vector3i position,
            long ignoredOldNetworkId,
            @Nonnull Set<Vector3i> ignoredFormerNetworkNeighbors
    ) {
        updateNodeNetwork(
                world,
                position
        );
    }

    private static void mergeNetworkInto(
            @Nonnull World world,
            @Nonnull NetworkResource networks,
            long sourceNetworkId,
            long targetNetworkId
    ) {
        if (!networks.containsNetwork(sourceNetworkId)
                || !networks.containsNetwork(targetNetworkId)) {
            return;
        }

        boolean targetWasFailing =
                networks.isFailing(targetNetworkId);

        boolean sourceWasFailing =
                networks.isFailing(sourceNetworkId);

        int sourceFailureStep =
                networks.failureStep(sourceNetworkId);

        long sourceFailureRemainingTicks =
                networks.failureRemainingTicks(
                        sourceNetworkId
                );

        Set<Vector3i> sourcePendingFailureOff =
                networks.pendingFailureOff(
                        sourceNetworkId
                );

        Set<Vector3i> sourceMembers =
                networks.members(sourceNetworkId);

        for (Vector3i member : sourceMembers) {
            networks.addMember(
                    targetNetworkId,
                    member
            );

            networks.setEnergyDelta(
                    targetNetworkId,
                    member,
                    networks.energyDelta(
                            sourceNetworkId,
                            member
                    )
            );
        }

        for (Vector3i member : sourceMembers) {
            for (Vector3i neighbour :
                    networks.neighbours(
                            sourceNetworkId,
                            member
                    )) {

                if (sourceMembers.contains(neighbour)) {
                    networks.addEdge(
                            targetNetworkId,
                            member,
                            neighbour
                    );
                }
            }
        }

        for (Vector3i memberPosition : sourceMembers) {
            NodeComponent member =
                    nodeAt(
                            world,
                            memberPosition
                    );

            if (member != null) {
                member.setNetworkId(
                        targetNetworkId
                );
            }
        }

        for (Vector3i pendingPosition :
                sourcePendingFailureOff) {

            networks.addPendingFailureOff(
                    targetNetworkId,
                    pendingPosition
            );
        }

        if (!targetWasFailing && sourceWasFailing) {
            networks.setFailureState(
                    targetNetworkId,
                    sourceFailureStep,
                    sourceFailureRemainingTicks
            );
        }

        networks.removeNetwork(
                sourceNetworkId
        );
    }

    private static void copyComponent(
            @Nonnull World world,
            @Nonnull NetworkResource networks,
            long sourceNetworkId,
            long targetNetworkId,
            @Nonnull Set<Vector3i> component
    ) {
        for (Vector3i member : component) {
            networks.addMember(
                    targetNetworkId,
                    member
            );

            networks.setEnergyDelta(
                    targetNetworkId,
                    member,
                    networks.energyDelta(
                            sourceNetworkId,
                            member
                    )
            );
        }

        for (Vector3i member : component) {
            for (Vector3i neighbour :
                    networks.neighbours(
                            sourceNetworkId,
                            member
                    )) {

                if (component.contains(neighbour)) {
                    networks.addEdge(
                            targetNetworkId,
                            member,
                            neighbour
                    );
                }
            }
        }

        for (Vector3i memberPosition : component) {
            NodeComponent member =
                    nodeAt(
                            world,
                            memberPosition
                    );

            if (member != null) {
                member.setNetworkId(
                        targetNetworkId
                );
            }
        }
    }

    private static @Nonnull Set<Set<Vector3i>> connectedComponents(
            @Nonnull NetworkResource networks,
            long networkId
    ) {
        Set<Vector3i> members =
                networks.members(networkId);

        LinkedHashSet<Set<Vector3i>> components =
                new LinkedHashSet<>();

        LinkedHashSet<Vector3i> visited =
                new LinkedHashSet<>();

        for (Vector3i start : members) {
            if (visited.contains(start)) {
                continue;
            }

            ArrayDeque<Vector3i> queue =
                    new ArrayDeque<>();

            LinkedHashSet<Vector3i> component =
                    new LinkedHashSet<>();

            queue.add(
                    new Vector3i(start)
            );

            while (!queue.isEmpty()) {
                Vector3i current =
                        queue.removeFirst();

                if (!members.contains(current)
                        || !visited.add(current)) {
                    continue;
                }

                component.add(
                        new Vector3i(current)
                );

                for (Vector3i neighbour :
                        networks.neighbours(
                                networkId,
                                current
                        )) {

                    if (members.contains(neighbour)
                            && !visited.contains(neighbour)) {

                        queue.addLast(
                                new Vector3i(neighbour)
                        );
                    }
                }
            }

            if (!component.isEmpty()) {
                components.add(
                        Set.copyOf(component)
                );
            }
        }

        return Set.copyOf(components);
    }

    private static @Nonnull Set<Vector3i> copyPositions(
            @Nonnull Set<Vector3i> positions
    ) {
        LinkedHashSet<Vector3i> copy =
                new LinkedHashSet<>();

        for (Vector3i position : positions) {
            copy.add(
                    new Vector3i(position)
            );
        }

        return Set.copyOf(copy);
    }

    private static long networkIdAt(
            @Nonnull NetworkResource networks,
            @Nonnull Vector3i position
    ) {
        for (long networkId :
                networks.networkIds()) {

            if (networks.containsMember(
                    networkId,
                    position
            )) {
                return networkId;
            }
        }

        return NodeComponent.NO_NETWORK;
    }

    private static int graphEnergy(
            @Nonnull NetworkResource networks,
            long networkId
    ) {
        int energy = 0;

        for (Vector3i member :
                networks.members(networkId)) {

            energy += networks.energyDelta(
                    networkId,
                    member
            );
        }

        return energy;
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
