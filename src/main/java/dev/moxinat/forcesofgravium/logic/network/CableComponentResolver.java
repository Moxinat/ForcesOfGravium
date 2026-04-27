package dev.moxinat.forcesofgravium.logic.network;

import com.hypixel.hytale.math.vector.Vector3i;

import javax.annotation.Nonnull;
import java.util.ArrayDeque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class CableComponentResolver {

    private CableComponentResolver() {
    }

    public static @Nonnull Set<Vector3i> findComponent(@Nonnull CableComponentAdapter adapter, @Nonnull Vector3i start) {
        Objects.requireNonNull(adapter, "adapter");
        Objects.requireNonNull(start, "start");

        if (!adapter.isCable(start)) {
            return Set.of();
        }

        ArrayDeque<Vector3i> queue = new ArrayDeque<>();
        LinkedHashSet<Vector3i> visited = new LinkedHashSet<>();
        queue.add(start);
        visited.add(start);

        while (!queue.isEmpty()) {
            Vector3i current = queue.removeFirst();
            for (Vector3i neighbor : adapter.directCableNeighbors(current)) {
                if (visited.add(neighbor)) {
                    queue.addLast(neighbor);
                }
            }
        }

        return Set.copyOf(visited);
    }

    public interface CableComponentAdapter {
        boolean isCable(@Nonnull Vector3i position);

        @Nonnull List<Vector3i> directCableNeighbors(@Nonnull Vector3i position);
    }
}
