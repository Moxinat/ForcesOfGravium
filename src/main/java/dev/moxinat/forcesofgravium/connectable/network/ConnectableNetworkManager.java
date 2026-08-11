package dev.moxinat.forcesofgravium.connectable.network;

import com.hypixel.hytale.server.core.universe.world.World;
import dev.moxinat.forcesofgravium.connectable.spatial.ConnectableNeighborResolver;
import dev.moxinat.forcesofgravium.data.Nodes;
import org.joml.Vector3i;

import javax.annotation.Nonnull;
import java.util.ArrayDeque;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class ConnectableNetworkManager {

    private static final Map<String, AtomicLong> NEXT_NETWORK_ID =
            new ConcurrentHashMap<>();

    private static long nextNetworkId(@Nonnull World world) {
        AtomicLong counter = NEXT_NETWORK_ID.computeIfAbsent(
                world.getName(),
                ignored -> {
                    long maxId = Nodes.snapshotForWorld(world)
                            .values()
                            .stream()
                            .mapToLong(Nodes.Node::networkId)
                            .max()
                            .orElse(0L);

                    return new AtomicLong(maxId + 1);
                }
        );

        return counter.getAndIncrement();
    }

    public static void onNodePlaced(
            @Nonnull World world,
            @Nonnull Vector3i position
    ) {
        Nodes.Node node = Nodes.get(world, position);

        if (node == null) {
            return;
        }

        LinkedHashSet<Vector3i> neighbors = new LinkedHashSet<>();

        neighbors.addAll(
                ConnectableNeighborResolver.allForwardSignalNeighbors(
                        world,
                        position
                )
        );

        neighbors.addAll(
                ConnectableNeighborResolver.allBackwardSignalNeighbors(
                        world,
                        position
                )
        );

        LinkedHashSet<Long> neighborNetworkIds = new LinkedHashSet<>();

        for (Vector3i neighborPosition : neighbors) {
            Nodes.Node neighbor = Nodes.get(world, neighborPosition);

            if (neighbor != null
                    && neighbor.networkId() != Nodes.Node.NO_NETWORK) {

                neighborNetworkIds.add(neighbor.networkId());
            }
        }

        // no Network -> make one
        if (neighborNetworkIds.isEmpty()) {
            long networkId = nextNetworkId(world);

            Nodes.put(
                    world,
                    node.withNetworkId(networkId)
            );

            return;
        }

        // one Network -> carry over
        if (neighborNetworkIds.size() == 1) {
            long networkId = neighborNetworkIds.iterator().next();

            Nodes.put(
                    world,
                    node.withNetworkId(networkId)
            );

            return;
        }

        // more Networks -> merge with new node
        long targetNetworkId = neighborNetworkIds.iterator().next();

        for (Vector3i memberPosition :
                scanFrom(world, position)) {

            Nodes.Node member = Nodes.get(world, memberPosition);

            if (member != null
                    && member.networkId() != targetNetworkId) {

                Nodes.put(
                        world,
                        member.withNetworkId(targetNetworkId)
                );
            }
        }
    }

    public static void onNodeBroken(
            @Nonnull World world,
            long oldNetworkId,
            @Nonnull Set<Vector3i> formerNeighbors
    ) {
        LinkedHashSet<Vector3i> processed = new LinkedHashSet<>();

        boolean firstComponent = true;

        for (Vector3i neighborPosition : formerNeighbors) {
            if (processed.contains(neighborPosition)) {
                continue;
            }

            Nodes.Node neighbor = Nodes.get(world, neighborPosition);

            if (neighbor == null) {
                continue;
            }

            Set<Vector3i> component =
                    scanFrom(
                            world,
                            neighborPosition
                    );

            if (component.isEmpty()) {
                continue;
            }

            long networkId;

            if (firstComponent) {
                networkId = oldNetworkId;
                firstComponent = false;
            } else {
                networkId = nextNetworkId(world);
            }

            for (Vector3i memberPosition : component) {
                Nodes.Node member = Nodes.get(world, memberPosition);

                if (member != null
                        && member.networkId() != networkId) {

                    Nodes.put(
                            world,
                            member.withNetworkId(networkId)
                    );
                }
            }

            processed.addAll(component);
        }
    }

    private static @Nonnull Set<Vector3i> scanFrom(
            @Nonnull World world,
            @Nonnull Vector3i start
    ) {
        if (Nodes.get(world, start) == null) {
            return Set.of();
        }

        ArrayDeque<Vector3i> queue = new ArrayDeque<>();
        LinkedHashSet<Vector3i> visited = new LinkedHashSet<>();

        queue.add(start);

        while (!queue.isEmpty()) {
            Vector3i position = queue.removeFirst();

            if (!visited.add(position)) {
                continue;
            }

            if (Nodes.get(world, position) == null) {
                continue;
            }

            for (Vector3i neighbor :
                    ConnectableNeighborResolver.allForwardSignalNeighbors(
                            world,
                            position
                    )) {

                if (!visited.contains(neighbor)) {
                    queue.addLast(neighbor);
                }
            }

            for (Vector3i neighbor :
                    ConnectableNeighborResolver.allBackwardSignalNeighbors(
                            world,
                            position
                    )) {

                if (!visited.contains(neighbor)) {
                    queue.addLast(neighbor);
                }
            }
        }

        return Set.copyOf(visited);
    }
}
