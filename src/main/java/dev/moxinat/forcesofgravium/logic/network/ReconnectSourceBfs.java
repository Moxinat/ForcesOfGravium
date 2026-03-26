package dev.moxinat.forcesofgravium.logic.network;

import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import dev.moxinat.forcesofgravium.registry.ConnectableBlockRoles;
import dev.moxinat.forcesofgravium.registry.ConnectableRegistry;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class ReconnectSourceBfs {

    private ReconnectSourceBfs() {
    }

    public static boolean canReachSource(@Nonnull World world, @Nonnull Vector3i start) {
        return canReachSource(new WorldReconnectAdapter(world), start);
    }

    public static boolean canReachSource(@Nonnull ReconnectAdapter adapter, @Nonnull Vector3i start) {
        Objects.requireNonNull(adapter, "adapter");
        Objects.requireNonNull(start, "start");
        if (!adapter.isTraversable(start)) {
            return false;
        }

        ArrayDeque<Vector3i> queue = new ArrayDeque<>();
        Set<Vector3i> visited = new LinkedHashSet<>();
        queue.add(start);
        visited.add(start);

        while (!queue.isEmpty()) {
            Vector3i current = queue.removeFirst();
            if (adapter.hasAdjacentSource(current)) {
                return true;
            }

            for (Vector3i next : adapter.reverseTraversalNeighbors(current)) {
                if (visited.add(next)) {
                    queue.addLast(next);
                }
            }
        }

        return false;
    }

    public interface ReconnectAdapter {
        boolean isTraversable(@Nonnull Vector3i position);

        boolean hasAdjacentSource(@Nonnull Vector3i position);

        @Nonnull List<Vector3i> reverseTraversalNeighbors(@Nonnull Vector3i position);
    }

    private static final class WorldReconnectAdapter implements ReconnectAdapter {
        private final World world;

        private WorldReconnectAdapter(World world) {
            this.world = Objects.requireNonNull(world, "world");
        }

        @Override
        public boolean isTraversable(@Nonnull Vector3i position) {
            BlockType blockType = world.getBlockType(position.getX(), position.getY(), position.getZ());
            return blockType != null && (ConnectableRegistry.isGravityPowderId(blockType.getId()) || ConnectableRegistry.isInverterId(blockType.getId()));
        }

        @Override
        public boolean hasAdjacentSource(@Nonnull Vector3i position) {
            BlockType blockType = world.getBlockType(position.getX(), position.getY(), position.getZ());
            if (blockType == null) {
                return false;
            }

            if (ConnectableRegistry.isGravityPowderId(blockType.getId())) {
                for (Vector3i sourceNeighbor : ConnectableNeighborResolver.sourceNeighbors(world, position, null)) {
                    BlockType sourceType = world.getBlockType(sourceNeighbor.getX(), sourceNeighbor.getY(), sourceNeighbor.getZ());
                    if (sourceType != null && ConnectableBlockRoles.isSource(sourceType.getId())) {
                        return true;
                    }
                }
                return false;
            }

            if (!ConnectableRegistry.isInverterId(blockType.getId())) {
                return false;
            }

            Vector3i backPosition = ConnectableNeighborResolver.adjacentPositionForLocalSide(world, position, ConnectableRegistry.SIDE_BACK);
            BlockType backType = world.getBlockType(backPosition.getX(), backPosition.getY(), backPosition.getZ());
            return backType != null && ConnectableBlockRoles.isSource(backType.getId());
        }

        @Override
        public @Nonnull List<Vector3i> reverseTraversalNeighbors(@Nonnull Vector3i position) {
            BlockType blockType = world.getBlockType(position.getX(), position.getY(), position.getZ());
            if (blockType == null) {
                return List.of();
            }

            if (ConnectableRegistry.isInverterId(blockType.getId())) {
                return inverterBackNeighbors(position);
            }

            if (!ConnectableRegistry.isGravityPowderId(blockType.getId())) {
                return List.of();
            }

            List<Vector3i> neighbors = new ArrayList<>();
            for (Vector3i neighbor : ConnectableNeighborResolver.positionsAround(position)) {
                if (neighbor.equals(position)) {
                    continue;
                }

                BlockType neighborType = world.getBlockType(neighbor.getX(), neighbor.getY(), neighbor.getZ());
                if (neighborType == null) {
                    continue;
                }

                if (ConnectableRegistry.isGravityPowderId(neighborType.getId())) {
                    neighbors.add(neighbor);
                    continue;
                }

                if (!ConnectableRegistry.isInverterId(neighborType.getId())) {
                    continue;
                }

                Vector3i frontPosition = ConnectableNeighborResolver.adjacentPositionForLocalSide(world, neighbor, ConnectableRegistry.SIDE_FRONT);
                if (frontPosition.equals(position)) {
                    neighbors.add(neighbor);
                }
            }
            return List.copyOf(neighbors);
        }

        private List<Vector3i> inverterBackNeighbors(Vector3i inverterPosition) {
            Vector3i backPosition = ConnectableNeighborResolver.adjacentPositionForLocalSide(world, inverterPosition, ConnectableRegistry.SIDE_BACK);
            BlockType backType = world.getBlockType(backPosition.getX(), backPosition.getY(), backPosition.getZ());
            if (backType == null) {
                return List.of();
            }
            if (ConnectableRegistry.isGravityPowderId(backType.getId())) {
                return List.of(backPosition);
            }
            if (!ConnectableRegistry.isInverterId(backType.getId())) {
                return List.of();
            }

            Vector3i upstreamFrontPosition = ConnectableNeighborResolver.adjacentPositionForLocalSide(world, backPosition, ConnectableRegistry.SIDE_FRONT);
            if (!upstreamFrontPosition.equals(inverterPosition)) {
                return List.of();
            }
            return List.of(backPosition);
        }
    }
}
