package dev.moxinat.forcesofgravium.connectable.spatial;

import com.hypixel.hytale.math.util.ChunkUtil;
import org.joml.Vector3d;
import org.joml.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.accessor.BlockAccessor;
import dev.moxinat.forcesofgravium.connectable.core.ConnectableRuntimeAccessor;
import dev.moxinat.forcesofgravium.connectable.registry.ConnectableBlockRoles;
import dev.moxinat.forcesofgravium.connectable.registry.ConnectableRegistry;

import java.util.LinkedHashSet;
import java.util.List;

public final class ConnectableNeighborResolver {

    public static final int CONNECTION_EAST = 1;
    public static final int CONNECTION_WEST = 1 << 1;
    public static final int CONNECTION_SOUTH = 1 << 2;
    public static final int CONNECTION_NORTH = 1 << 3;
    public static final int CONNECTION_UP = 1 << 4;
    public static final int CONNECTION_DOWN = 1 << 5;

    private ConnectableNeighborResolver() {
    }

    public static boolean isConnectable(World world, int x, int y, int z, Vector3i treatAsEmpty, WorldSide requiredWorldSide) {
        if (isTreatAsEmpty(new Vector3i(x, y, z), treatAsEmpty)) {
            return false;
        }

        BlockType blockType = world.getBlockType(x, y, z);
        if (blockType == null) {
            return false;
        }

        String id = blockType.getId();
        if (ConnectableRegistry.isNotConnectable(id)) {
            return false;
        }

        BlockAccessor chunk = world.getChunk(ChunkUtil.indexChunkFromBlock(x, z));
        if (chunk == null) {
            return false;
        }

        RotationTuple rotation = rotationFor(world, new Vector3i(x, y, z));
        return hasLocalSideFacingWorldSide(id, rotation, requiredWorldSide);
    }

    public static List<Vector3i> sourceNeighbors(World world, Vector3i position, Vector3i treatAsEmpty) {
        java.util.ArrayList<Vector3i> sources = new java.util.ArrayList<>();
        addSourceNeighbor(world, sources, position.x() + 1, position.y(), position.z(), treatAsEmpty, WorldSide.WEST);
        addSourceNeighbor(world, sources, position.x() - 1, position.y(), position.z(), treatAsEmpty, WorldSide.EAST);
        addSourceNeighbor(world, sources, position.x(), position.y(), position.z() + 1, treatAsEmpty, WorldSide.NORTH);
        addSourceNeighbor(world, sources, position.x(), position.y(), position.z() - 1, treatAsEmpty, WorldSide.SOUTH);
        addSourceNeighbor(world, sources, position.x(), position.y() + 1, position.z(), treatAsEmpty, WorldSide.DOWN);
        addSourceNeighbor(world, sources, position.x(), position.y() - 1, position.z(), treatAsEmpty, WorldSide.UP);
        return List.copyOf(sources);
    }

    public static boolean isSourceNeighborOf(World world, Vector3i sourcePosition, Vector3i targetPosition) {
        WorldSide requiredWorldSide = worldSideFromSourceToTarget(sourcePosition, targetPosition);
        if (requiredWorldSide == null) {
            return false;
        }

        BlockType blockType = world.getBlockType(sourcePosition.x(), sourcePosition.y(), sourcePosition.z());
        if (blockType == null || !ConnectableBlockRoles.isSource(blockType.getId())) {
            return false;
        }
        if (!ConnectableRuntimeAccessor.isSignalSourceActive(world, sourcePosition)) {
            return false;
        }

        BlockAccessor chunk = world.getChunk(ChunkUtil.indexChunkFromBlock(sourcePosition.x(), sourcePosition.z()));
        if (chunk == null) {
            return false;
        }

        RotationTuple rotation = rotationFor(world, sourcePosition);
        return hasSignalOutputSideFacingWorldSide(blockType.getId(), rotation, requiredWorldSide);
    }

    public static List<Vector3i> positionsAround(Vector3i center) {
        LinkedHashSet<Vector3i> positions = new LinkedHashSet<>();
        positions.add(center);
        positions.add(new Vector3i(center.x() + 1, center.y(), center.z()));
        positions.add(new Vector3i(center.x() - 1, center.y(), center.z()));
        positions.add(new Vector3i(center.x(), center.y(), center.z() + 1));
        positions.add(new Vector3i(center.x(), center.y(), center.z() - 1));
        positions.add(new Vector3i(center.x(), center.y() + 1, center.z()));
        positions.add(new Vector3i(center.x(), center.y() - 1, center.z()));
        return List.copyOf(positions);
    }

    public static int buildConnectionsMask(boolean east, boolean west, boolean south, boolean north, boolean up, boolean down) {
        int mask = 0;
        if (east) {
            mask |= CONNECTION_EAST;
        }
        if (west) {
            mask |= CONNECTION_WEST;
        }
        if (south) {
            mask |= CONNECTION_SOUTH;
        }
        if (north) {
            mask |= CONNECTION_NORTH;
        }
        if (up) {
            mask |= CONNECTION_UP;
        }
        if (down) {
            mask |= CONNECTION_DOWN;
        }
        return mask;
    }

    public static boolean areMutuallyConnected(World world, Vector3i first, Vector3i second) {
        WorldSide firstToSecond = worldSideFromSourceToTarget(first, second);
        if (firstToSecond == null) {
            return false;
        }

        BlockType firstType = world.getBlockType(first.x(), first.y(), first.z());
        BlockType secondType = world.getBlockType(second.x(), second.y(), second.z());
        if (firstType == null || secondType == null) {
            return false;
        }

        String firstId = firstType.getId();
        String secondId = secondType.getId();
        if (ConnectableRegistry.isNotConnectable(firstId) || ConnectableRegistry.isNotConnectable(secondId)) {
            return false;
        }

        RotationTuple firstRotation = rotationFor(world, first);
        RotationTuple secondRotation = rotationFor(world, second);
        return hasLocalSideFacingWorldSide(firstId, firstRotation, firstToSecond)
                && hasLocalSideFacingWorldSide(secondId, secondRotation, firstToSecond.opposite());
    }

    public static java.util.Set<Vector3i> mutuallyConnectedNeighbors(World world, Vector3i position) {
        LinkedHashSet<Vector3i> result = new LinkedHashSet<>();
        for (Vector3i candidate : positionsAround(position)) {
            if (candidate.equals(position)) {
                continue;
            }
            if (areMutuallyConnected(world, position, candidate)) {
                result.add(candidate);
            }
        }
        return java.util.Set.copyOf(result);
    }

    public static boolean hasConnectableSideFacing(World world, Vector3i position, Vector3i target) {
        WorldSide worldSide = worldSideFromSourceToTarget(position, target);
        if (worldSide == null) {
            return false;
        }

        BlockType blockType = world.getBlockType(position.x(), position.y(), position.z());
        if (blockType == null || ConnectableRegistry.isNotConnectable(blockType.getId())) {
            return false;
        }

        return hasLocalSideFacingWorldSide(blockType.getId(), rotationFor(world, position), worldSide);
    }

    private static void addSourceNeighbor(World world, List<Vector3i> sources, int x, int y, int z, Vector3i treatAsEmpty, WorldSide requiredWorldSide) {
        if (isTreatAsEmpty(new Vector3i(x, y, z), treatAsEmpty)) {
            return;
        }

        BlockType blockType = world.getBlockType(x, y, z);
        if (blockType == null || !ConnectableBlockRoles.isSource(blockType.getId())) {
            return;
        }
        Vector3i sourcePosition = new Vector3i(x, y, z);
        if (!ConnectableRuntimeAccessor.isSignalSourceActive(world, sourcePosition)) {
            return;
        }

        BlockAccessor chunk = world.getChunk(ChunkUtil.indexChunkFromBlock(x, z));
        if (chunk == null) {
            return;
        }

        RotationTuple rotation = rotationFor(world, sourcePosition);
        if (hasSignalOutputSideFacingWorldSide(blockType.getId(), rotation, requiredWorldSide)) {
            sources.add(sourcePosition);
        }
    }

    private static boolean hasSignalOutputSideFacingWorldSide(String blockId, RotationTuple rotation, WorldSide requiredWorldSide) {
        RotationTuple resolvedRotation = rotation == null ? RotationTuple.NONE : rotation;
        return isLocalSignalOutputSideFacingWorldSide(blockId, resolvedRotation, ConnectableRegistry.SIDE_FRONT, requiredWorldSide)
                || isLocalSignalOutputSideFacingWorldSide(blockId, resolvedRotation, ConnectableRegistry.SIDE_BACK, requiredWorldSide)
                || isLocalSignalOutputSideFacingWorldSide(blockId, resolvedRotation, ConnectableRegistry.SIDE_RIGHT, requiredWorldSide)
                || isLocalSignalOutputSideFacingWorldSide(blockId, resolvedRotation, ConnectableRegistry.SIDE_LEFT, requiredWorldSide)
                || isLocalSignalOutputSideFacingWorldSide(blockId, resolvedRotation, ConnectableRegistry.SIDE_TOP, requiredWorldSide)
                || isLocalSignalOutputSideFacingWorldSide(blockId, resolvedRotation, ConnectableRegistry.SIDE_BOTTOM, requiredWorldSide);
    }

    private static boolean hasLocalSideFacingWorldSide(String blockId, RotationTuple rotation, WorldSide requiredWorldSide) {
        RotationTuple resolvedRotation = rotation == null ? RotationTuple.NONE : rotation;
        return isLocalSideFacingWorldSide(blockId, resolvedRotation, ConnectableRegistry.SIDE_FRONT, requiredWorldSide)
                || isLocalSideFacingWorldSide(blockId, resolvedRotation, ConnectableRegistry.SIDE_BACK, requiredWorldSide)
                || isLocalSideFacingWorldSide(blockId, resolvedRotation, ConnectableRegistry.SIDE_RIGHT, requiredWorldSide)
                || isLocalSideFacingWorldSide(blockId, resolvedRotation, ConnectableRegistry.SIDE_LEFT, requiredWorldSide)
                || isLocalSideFacingWorldSide(blockId, resolvedRotation, ConnectableRegistry.SIDE_TOP, requiredWorldSide)
                || isLocalSideFacingWorldSide(blockId, resolvedRotation, ConnectableRegistry.SIDE_BOTTOM, requiredWorldSide);
    }

    private static boolean isLocalSideFacingWorldSide(String blockId, RotationTuple rotation, int localSideMask, WorldSide requiredWorldSide) {
        return ConnectableRegistry.isConnectableOnSide(blockId, localSideMask)
                && worldSideForLocalSide(rotation, localSideMask) == requiredWorldSide;
    }

    private static boolean isLocalSignalOutputSideFacingWorldSide(String blockId, RotationTuple rotation, int localSideMask, WorldSide requiredWorldSide) {
        return ConnectableRuntimeAccessor.canOutputSignalTo(blockId, localSideMask)
                && worldSideForLocalSide(rotation, localSideMask) == requiredWorldSide;
    }

    public static WorldSide worldSideFromSourceToTarget(Vector3i sourcePosition, Vector3i targetPosition) {
        int dx = targetPosition.x() - sourcePosition.x();
        int dy = targetPosition.y() - sourcePosition.y();
        int dz = targetPosition.z() - sourcePosition.z();
        if (Math.abs(dx) + Math.abs(dy) + Math.abs(dz) != 1) {
            return null;
        }
        return WorldSide.fromVector(dx, dy, dz);
    }

    public static Vector3i adjacentPositionForLocalSide(World world, Vector3i position, int localSideMask) {
        RotationTuple rotation = rotationFor(world, position);
        WorldSide worldSide = worldSideForLocalSide(rotation, localSideMask);
        return switch (worldSide) {
            case EAST -> new Vector3i(position.x() + 1, position.y(), position.z());
            case WEST -> new Vector3i(position.x() - 1, position.y(), position.z());
            case SOUTH -> new Vector3i(position.x(), position.y(), position.z() + 1);
            case NORTH -> new Vector3i(position.x(), position.y(), position.z() - 1);
            case UP -> new Vector3i(position.x(), position.y() + 1, position.z());
            case DOWN -> new Vector3i(position.x(), position.y() - 1, position.z());
        };
    }

    public static RotationTuple rotationFor(World world, Vector3i position) {
        return ConnectableRuntimeAccessor.getRotation(world, position);
    }

    public static WorldSide worldSideForLocalSide(RotationTuple rotation, int localSideMask) {
        Vector3d rotated = rotation.rotatedVector(localNormal(localSideMask));
        int x = (int) Math.round(rotated.x());
        int y = (int) Math.round(rotated.y());
        int z = (int) Math.round(rotated.z());
        return WorldSide.fromVector(x, y, z);
    }

    public static int localSideForWorldSide(RotationTuple rotation, WorldSide worldSide) {
        for (int localSideMask : List.of(
                ConnectableRegistry.SIDE_FRONT,
                ConnectableRegistry.SIDE_BACK,
                ConnectableRegistry.SIDE_RIGHT,
                ConnectableRegistry.SIDE_LEFT,
                ConnectableRegistry.SIDE_TOP,
                ConnectableRegistry.SIDE_BOTTOM
        )) {
            if (worldSideForLocalSide(rotation, localSideMask) == worldSide) {
                return localSideMask;
            }
        }
        return 0;
    }

    private static Vector3d localNormal(int localSideMask) {
        if (localSideMask == ConnectableRegistry.SIDE_FRONT) {
            return new Vector3d(0, 0, 1);
        }
        if (localSideMask == ConnectableRegistry.SIDE_BACK) {
            return new Vector3d(0, 0, -1);
        }
        if (localSideMask == ConnectableRegistry.SIDE_RIGHT) {
            return new Vector3d(1, 0, 0);
        }
        if (localSideMask == ConnectableRegistry.SIDE_LEFT) {
            return new Vector3d(-1, 0, 0);
        }
        if (localSideMask == ConnectableRegistry.SIDE_TOP) {
            return new Vector3d(0, 1, 0);
        }
        if (localSideMask == ConnectableRegistry.SIDE_BOTTOM) {
            return new Vector3d(0, -1, 0);
        }
        throw new IllegalArgumentException("Unknown local side mask: " + localSideMask);
    }

    private static boolean isTreatAsEmpty(Vector3i position, Vector3i treatAsEmpty) {
        return treatAsEmpty != null
                && treatAsEmpty.x() == position.x()
                && treatAsEmpty.y() == position.y()
                && treatAsEmpty.z() == position.z();
    }

    public enum WorldSide {
        EAST(1, 0, 0),
        WEST(-1, 0, 0),
        SOUTH(0, 0, 1),
        NORTH(0, 0, -1),
        UP(0, 1, 0),
        DOWN(0, -1, 0);

        private final int x;
        private final int y;
        private final int z;

        WorldSide(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        private static WorldSide fromVector(int x, int y, int z) {
            for (WorldSide side : values()) {
                if (side.x == x && side.y == y && side.z == z) {
                    return side;
                }
            }
            throw new IllegalArgumentException("Unsupported world direction vector: " + x + "," + y + "," + z);
        }

        public WorldSide opposite() {
            return switch (this) {
                case EAST -> WEST;
                case WEST -> EAST;
                case SOUTH -> NORTH;
                case NORTH -> SOUTH;
                case UP -> DOWN;
                case DOWN -> UP;
            };
        }
    }
}
