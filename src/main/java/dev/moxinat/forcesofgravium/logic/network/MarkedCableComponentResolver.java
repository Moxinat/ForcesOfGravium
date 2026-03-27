package dev.moxinat.forcesofgravium.logic.network;

import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import dev.moxinat.forcesofgravium.data.GravityPowderBlockDataStore;
import dev.moxinat.forcesofgravium.data.GravityPowderBlockDataStore.GravityPowderBlockData;
import dev.moxinat.forcesofgravium.registry.ConnectableRegistry;

import javax.annotation.Nonnull;
import java.util.ArrayDeque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class MarkedCableComponentResolver {

    private MarkedCableComponentResolver() {
    }

    public static @Nonnull Set<Vector3i> findComponent(@Nonnull World world, @Nonnull Vector3i start, @Nonnull String decayMark) {
        return findComponent(new WorldComponentAdapter(world), start, decayMark);
    }

    public static @Nonnull Set<Vector3i> findAnyMarkedComponent(@Nonnull World world, @Nonnull Vector3i start) {
        return findAnyMarkedComponent(new WorldComponentAdapter(world), start);
    }

    public static @Nonnull Set<Vector3i> findComponent(@Nonnull ComponentAdapter adapter, @Nonnull Vector3i start, @Nonnull String decayMark) {
        Objects.requireNonNull(adapter, "adapter");
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(decayMark, "decayMark");

        if (!adapter.isMarkedCable(start, decayMark)) {
            return Set.of();
        }

        ArrayDeque<Vector3i> queue = new ArrayDeque<>();
        LinkedHashSet<Vector3i> visited = new LinkedHashSet<>();
        queue.add(start);
        visited.add(start);

        while (!queue.isEmpty()) {
            Vector3i current = queue.removeFirst();
            for (Vector3i neighbor : adapter.directCableNeighbors(current)) {
                if (!adapter.isMarkedCable(neighbor, decayMark)) {
                    continue;
                }
                if (visited.add(neighbor)) {
                    queue.addLast(neighbor);
                }
            }
        }

        return Set.copyOf(visited);
    }

    public static @Nonnull Set<Vector3i> findAnyMarkedComponent(@Nonnull ComponentAdapter adapter, @Nonnull Vector3i start) {
        Objects.requireNonNull(adapter, "adapter");
        Objects.requireNonNull(start, "start");

        if (!adapter.isAnyMarkedCable(start)) {
            return Set.of();
        }

        ArrayDeque<Vector3i> queue = new ArrayDeque<>();
        LinkedHashSet<Vector3i> visited = new LinkedHashSet<>();
        queue.add(start);
        visited.add(start);

        while (!queue.isEmpty()) {
            Vector3i current = queue.removeFirst();
            for (Vector3i neighbor : adapter.directCableNeighbors(current)) {
                if (!adapter.isAnyMarkedCable(neighbor)) {
                    continue;
                }
                if (visited.add(neighbor)) {
                    queue.addLast(neighbor);
                }
            }
        }

        return Set.copyOf(visited);
    }

    public interface ComponentAdapter {
        boolean isMarkedCable(@Nonnull Vector3i position, @Nonnull String decayMark);

        boolean isAnyMarkedCable(@Nonnull Vector3i position);

        @Nonnull List<Vector3i> directCableNeighbors(@Nonnull Vector3i position);
    }

    private static final class WorldComponentAdapter implements ComponentAdapter {

        private final World world;

        private WorldComponentAdapter(World world) {
            this.world = Objects.requireNonNull(world, "world");
        }

        @Override
        public boolean isMarkedCable(@Nonnull Vector3i position, @Nonnull String decayMark) {
            BlockType blockType = world.getBlockType(position.getX(), position.getY(), position.getZ());
            if (blockType == null || !ConnectableRegistry.isGravityPowderId(blockType.getId())) {
                return false;
            }

            GravityPowderBlockData data = GravityPowderBlockDataStore.get(world, position);
            return data != null && decayMark.equals(data.state());
        }

        @Override
        public boolean isAnyMarkedCable(@Nonnull Vector3i position) {
            BlockType blockType = world.getBlockType(position.getX(), position.getY(), position.getZ());
            if (blockType == null || !ConnectableRegistry.isGravityPowderId(blockType.getId())) {
                return false;
            }

            GravityPowderBlockData data = GravityPowderBlockDataStore.get(world, position);
            return GravityPowderBlockDataStore.hasActiveWave(data);
        }

        @Override
        public @Nonnull List<Vector3i> directCableNeighbors(@Nonnull Vector3i position) {
            return ConnectableNeighborResolver.positionsAround(position).stream()
                    .filter(candidate -> !candidate.equals(position))
                    .filter(candidate -> {
                        BlockType blockType = world.getBlockType(candidate.getX(), candidate.getY(), candidate.getZ());
                        return blockType != null && ConnectableRegistry.isGravityPowderId(blockType.getId());
                    })
                    .toList();
        }
    }
}
