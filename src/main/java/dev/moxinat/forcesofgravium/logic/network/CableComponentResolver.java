package dev.moxinat.forcesofgravium.logic.network;

import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import dev.moxinat.forcesofgravium.registry.ConnectableRegistry;

import javax.annotation.Nonnull;
import java.util.ArrayDeque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class CableComponentResolver {

    private CableComponentResolver() {
    }

    public static @Nonnull Set<Vector3i> findComponent(@Nonnull World world, @Nonnull Vector3i start) {
        return findComponent(new WorldCableComponentAdapter(world), start);
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
                if (!adapter.isCable(neighbor)) {
                    continue;
                }
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

    private static final class WorldCableComponentAdapter implements CableComponentAdapter {
        private final World world;

        private WorldCableComponentAdapter(World world) {
            this.world = Objects.requireNonNull(world, "world");
        }

        @Override
        public boolean isCable(@Nonnull Vector3i position) {
            BlockType blockType = world.getBlockType(position.getX(), position.getY(), position.getZ());
            return blockType != null && ConnectableRegistry.isGravityPowderId(blockType.getId());
        }

        @Override
        public @Nonnull List<Vector3i> directCableNeighbors(@Nonnull Vector3i position) {
            return ConnectableNeighborResolver.positionsAround(position).stream()
                    .filter(candidate -> !candidate.equals(position))
                    .filter(this::isCable)
                    .toList();
        }
    }
}
