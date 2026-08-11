package dev.moxinat.forcesofgravium.connectable.network;

import com.hypixel.hytale.server.core.universe.world.World;
import dev.moxinat.forcesofgravium.connectable.spatial.ConnectableNeighborResolver;
import dev.moxinat.forcesofgravium.data.Nodes;
import org.joml.Vector3i;

import javax.annotation.Nonnull;
import java.util.ArrayDeque;
import java.util.LinkedHashSet;
import java.util.Set;

public final class ConnectableNetworkScanner {

    private ConnectableNetworkScanner() {
    }

    public static @Nonnull Set<Vector3i> scanFrom(
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