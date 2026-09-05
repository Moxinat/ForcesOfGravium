package dev.moxinat.forcesofgravium.network;

import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.universe.world.World;
import dev.moxinat.forcesofgravium.ForcesOfGraviumPlugin;
import dev.moxinat.forcesofgravium.data.NetworkResource;
import dev.moxinat.forcesofgravium.data.NodeComponent;
import dev.moxinat.forcesofgravium.spatial.ConnectableNeighborResolver;
import org.joml.Vector3i;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
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

    public static void onNodeBroken(
            @Nonnull World world,
            long oldNetworkId,
            @Nonnull Set<Vector3i> formerNeighbors
    ) {
        NetworkResource networks =
                networks(world);

        Vector3i excludedPosition =
                inferExcludedPosition(
                        networks,
                        oldNetworkId,
                        formerNeighbors
                );

        onNodeBroken(
                world,
                oldNetworkId,
                formerNeighbors,
                excludedPosition
        );
    }

    public static void onNodeBroken(
            @Nonnull World world,
            long oldNetworkId,
            @Nonnull Set<Vector3i> formerNeighbors,
            @Nullable Vector3i excludedPosition
    ) {
        NetworkResource networks =
                networks(world);

        if (excludedPosition != null) {
            long graphNetworkId =
                    networkIdAt(
                            networks,
                            excludedPosition
                    );

            if (graphNetworkId != NodeComponent.NO_NETWORK) {
                oldNetworkId = graphNetworkId;
            }
        }

        if (oldNetworkId == NodeComponent.NO_NETWORK
                || !networks.containsNetwork(oldNetworkId)) {
            return;
        }

        Set<Vector3i> oldMembers =
                networks.members(oldNetworkId);

        Map<Vector3i, Set<Vector3i>> oldNeighbours =
                new LinkedHashMap<>();

        Map<Vector3i, Integer> oldEnergyDeltas =
                new LinkedHashMap<>();

        for (Vector3i member : oldMembers) {
            oldNeighbours.put(
                    new Vector3i(member),
                    networks.neighbours(
                            oldNetworkId,
                            member
                    )
            );

            oldEnergyDeltas.put(
                    new Vector3i(member),
                    networks.energyDelta(
                            oldNetworkId,
                            member
                    )
            );
        }

        boolean wasFailing =
                networks.isFailing(oldNetworkId);

        int failureStep =
                networks.failureStep(oldNetworkId);

        long failureRemainingTicks =
                networks.failureRemainingTicks(
                        oldNetworkId
                );

        Set<Vector3i> pendingFailureOff =
                networks.pendingFailureOff(
                        oldNetworkId
                );

        LinkedHashSet<Vector3i> remainingMembers =
                new LinkedHashSet<>();

        for (Vector3i member : oldMembers) {
            if (excludedPosition != null
                    && member.equals(excludedPosition)) {
                continue;
            }

            remainingMembers.add(
                    new Vector3i(member)
            );
        }

        if (remainingMembers.isEmpty()) {
            networks.removeNetwork(
                    oldNetworkId
            );
            return;
        }

        Set<Set<Vector3i>> components =
                connectedComponents(
                        remainingMembers,
                        oldNeighbours
                );

        for (Vector3i member : oldMembers) {
            networks.removeMember(
                    oldNetworkId,
                    member
            );
        }

        networks.clearPendingFailureOff(
                oldNetworkId
        );

        boolean firstComponent = true;

        for (Set<Vector3i> component : components) {
            long networkId;

            if (firstComponent) {
                networkId = oldNetworkId;
                firstComponent = false;
            } else {
                networkId =
                        networks.createNetwork();
            }

            rebuildComponent(
                    world,
                    networks,
                    networkId,
                    component,
                    oldNeighbours,
                    oldEnergyDeltas
            );

            networks.setEnergy(
                    networkId,
                    graphEnergy(
                            networks,
                            networkId
                    )
            );

            if (wasFailing) {
                networks.setFailureState(
                        networkId,
                        failureStep,
                        failureRemainingTicks
                );

                for (Vector3i pendingPosition :
                        pendingFailureOff) {

                    if (component.contains(pendingPosition)) {
                        networks.addPendingFailureOff(
                                networkId,
                                pendingPosition
                        );
                    }
                }
            }
        }
    }

    public static void updateNodeNetwork(
            @Nonnull World world,
            @Nonnull Vector3i position,
            long oldNetworkId,
            @Nonnull Set<Vector3i> formerNetworkNeighbors
    ) {
        NetworkResource networks =
                networks(world);

        long graphNetworkId =
                networkIdAt(
                        networks,
                        position
                );

        if (graphNetworkId != NodeComponent.NO_NETWORK) {
            oldNetworkId = graphNetworkId;
        }

        onNodeBroken(
                world,
                oldNetworkId,
                formerNetworkNeighbors,
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

        Set<Vector3i> sourceMembers =
                networks.members(sourceNetworkId);

        Map<Vector3i, Set<Vector3i>> sourceNeighbours =
                new LinkedHashMap<>();

        Map<Vector3i, Integer> sourceEnergyDeltas =
                new LinkedHashMap<>();

        for (Vector3i member : sourceMembers) {
            sourceNeighbours.put(
                    new Vector3i(member),
                    networks.neighbours(
                            sourceNetworkId,
                            member
                    )
            );

            sourceEnergyDeltas.put(
                    new Vector3i(member),
                    networks.energyDelta(
                            sourceNetworkId,
                            member
                    )
            );
        }

        for (Vector3i member : sourceMembers) {
            networks.addMember(
                    targetNetworkId,
                    member
            );

            networks.setEnergyDelta(
                    targetNetworkId,
                    member,
                    sourceEnergyDeltas.getOrDefault(
                            member,
                            0
                    )
            );
        }

        for (Vector3i member : sourceMembers) {
            for (Vector3i neighbour :
                    sourceNeighbours.getOrDefault(
                            member,
                            Set.of()
                    )) {

                if (!sourceMembers.contains(neighbour)) {
                    continue;
                }

                networks.addEdge(
                        targetNetworkId,
                        member,
                        neighbour
                );
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

        networks.removeNetwork(
                sourceNetworkId
        );
    }

    private static void rebuildComponent(
            @Nonnull World world,
            @Nonnull NetworkResource networks,
            long networkId,
            @Nonnull Set<Vector3i> component,
            @Nonnull Map<Vector3i, Set<Vector3i>> oldNeighbours,
            @Nonnull Map<Vector3i, Integer> oldEnergyDeltas
    ) {
        for (Vector3i member : component) {
            networks.addMember(
                    networkId,
                    member
            );

            networks.setEnergyDelta(
                    networkId,
                    member,
                    oldEnergyDeltas.getOrDefault(
                            member,
                            0
                    )
            );
        }

        for (Vector3i member : component) {
            for (Vector3i neighbour :
                    oldNeighbours.getOrDefault(
                            member,
                            Set.of()
                    )) {

                if (!component.contains(neighbour)) {
                    continue;
                }

                networks.addEdge(
                        networkId,
                        member,
                        neighbour
                );
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
                        networkId
                );
            }
        }
    }

    private static @Nonnull Set<Set<Vector3i>> connectedComponents(
            @Nonnull Set<Vector3i> members,
            @Nonnull Map<Vector3i, Set<Vector3i>> neighbours
    ) {
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
                        neighbours.getOrDefault(
                                current,
                                Set.of()
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

    private static @Nullable Vector3i inferExcludedPosition(
            @Nonnull NetworkResource networks,
            long networkId,
            @Nonnull Set<Vector3i> formerNeighbors
    ) {
        if (!networks.containsNetwork(networkId)) {
            return null;
        }

        Set<Vector3i> members =
                networks.members(networkId);

        if (members.size() == 1) {
            return new Vector3i(
                    members.iterator().next()
            );
        }

        for (Vector3i member : members) {
            if (networks.neighbours(
                    networkId,
                    member
            ).equals(formerNeighbors)) {
                return new Vector3i(member);
            }
        }

        return null;
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
