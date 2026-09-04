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
import java.util.LinkedHashSet;
import java.util.Set;

public class ConnectableNetworkManager {

    public static void onNodePlaced(
            @Nonnull World world,
            @Nonnull Vector3i position
    ) {
        NodeComponent node = nodeAt(world, position);
        NetworkResource networks = networks(world);

        if (node == null) {
            return;
        }

        Set<Vector3i> neighbors =
                ConnectableNeighborResolver.allNetworkNeighbors(
                        world,
                        position
                );

        LinkedHashSet<Long> neighborNetworkIds = new LinkedHashSet<>();

        for (Vector3i neighborPosition : neighbors) {
            NodeComponent neighbor = nodeAt(world, neighborPosition);

            if (neighbor != null
                    && neighbor.networkId() != NodeComponent.NO_NETWORK) {

                neighborNetworkIds.add(neighbor.networkId());
            }
        }

        // no Network -> make one
        if (neighborNetworkIds.isEmpty()) {
            long networkId = networks.createNetwork();

            networks.addMember(networkId, position);
            node.setNetworkId(networkId);

            return;
        }

        // one Network -> carry over
        if (neighborNetworkIds.size() == 1) {
            long networkId = neighborNetworkIds.iterator().next();

            networks.addMember(networkId, position);
            node.setNetworkId(networkId);

            return;
        }

        // more Networks -> merge with new node
        long targetNetworkId =
                neighborNetworkIds.iterator().next();

        networks.addMember(targetNetworkId, position);
        node.setNetworkId(targetNetworkId);

        for (long sourceNetworkId : neighborNetworkIds) {

            if (sourceNetworkId == targetNetworkId) {
                continue;
            }

            for (Vector3i memberPosition :
                    networks.members(sourceNetworkId)) {

                NodeComponent member =
                        nodeAt(world, memberPosition);

                if (member != null) {
                    member.setNetworkId(targetNetworkId);

                    networks.addMember(
                            targetNetworkId,
                            memberPosition
                    );
                }
            }

            networks.removeNetwork(sourceNetworkId);
        }
    }

    public static void onNodeBroken(
            @Nonnull World world,
            long oldNetworkId,
            @Nonnull Set<Vector3i> formerNeighbors
    ) {
        onNodeBroken(
                world,
                oldNetworkId,
                formerNeighbors,
                null
        );
    }

    public static void onNodeBroken(
            @Nonnull World world,
            long oldNetworkId,
            @Nonnull Set<Vector3i> formerNeighbors,
            @Nullable Vector3i excludedPosition
    ) {

        if (oldNetworkId == NodeComponent.NO_NETWORK) {
            return;
        }

        NetworkResource networks = networks(world);

        NetworkResource.NetworkData oldNetwork =
                networks.getNetwork(oldNetworkId);

        if (oldNetwork == null) {
            return;
        }

        for (Vector3i oldMember : networks.members(oldNetworkId)) {
            networks.removeMember(
                    oldNetworkId,
                    oldMember
            );
        }

        LinkedHashSet<Vector3i> processed =
                new LinkedHashSet<>();

        boolean firstComponent = true;

        for (Vector3i neighborPosition : formerNeighbors) {

            if (processed.contains(neighborPosition)) {
                continue;
            }

            NodeComponent neighbor =
                    nodeAt(world, neighborPosition);

            if (neighbor == null) {
                continue;
            }

            Set<Vector3i> component =
                    scanFrom(
                            world,
                            neighborPosition,
                            excludedPosition
                    );

            if (component.isEmpty()) {
                continue;
            }

            long networkId;

            if (firstComponent) {
                networkId = oldNetworkId;
                firstComponent = false;
            } else {
                networkId = networks.createNetwork();
            }

            for (Vector3i memberPosition : component) {

                NodeComponent member =
                        nodeAt(world, memberPosition);

                if (member == null) {
                    continue;
                }

                member.setNetworkId(networkId);

                networks.addMember(
                        networkId,
                        memberPosition
                );
            }

            processed.addAll(component);
        }

        if (firstComponent) {
            networks.removeNetwork(oldNetworkId);
        }
    }

    private static @Nonnull Set<Vector3i> scanFrom(
            @Nonnull World world,
            @Nonnull Vector3i start
    ) {
        return scanFrom(
                world,
                start,
                null
        );
    }

    private static @Nonnull Set<Vector3i> scanFrom(
            @Nonnull World world,
            @Nonnull Vector3i start,
            @Nullable Vector3i excludedPosition
    ) {
        if (excludedPosition != null
                && start.equals(excludedPosition)) {
            return Set.of();
        }

        if (nodeAt(world, start) == null) {
            return Set.of();
        }

        ArrayDeque<Vector3i> queue =
                new ArrayDeque<>();

        LinkedHashSet<Vector3i> visited =
                new LinkedHashSet<>();

        queue.add(
                new Vector3i(start)
        );

        while (!queue.isEmpty()) {

            Vector3i position =
                    queue.removeFirst();

            if (excludedPosition != null
                    && position.equals(excludedPosition)) {
                continue;
            }

            if (!visited.add(position)) {
                continue;
            }

            if (nodeAt(world, position) == null) {
                continue;
            }

            for (Vector3i neighbor :
                    ConnectableNeighborResolver.allNetworkNeighbors(
                            world,
                            position
                    )) {

                if (excludedPosition != null
                        && neighbor.equals(excludedPosition)) {
                    continue;
                }

                if (!visited.contains(neighbor)) {
                    queue.addLast(
                            new Vector3i(neighbor)
                    );
                }
            }
        }

        return Set.copyOf(visited);
    }

    public static void updateNodeNetwork(
            @Nonnull World world,
            @Nonnull Vector3i position,
            long oldNetworkId,
            @Nonnull Set<Vector3i> formerNetworkNeighbors
    ) {
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

    private static NetworkResource networks(
            World world
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
