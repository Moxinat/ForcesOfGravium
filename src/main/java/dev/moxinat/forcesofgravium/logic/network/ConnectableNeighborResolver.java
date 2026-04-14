package dev.moxinat.forcesofgravium.logic.network;

import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.accessor.BlockAccessor;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import dev.moxinat.forcesofgravium.data.ConnectableRotationStore;
import dev.moxinat.forcesofgravium.data.GravityPowderBlockDataStore;
import dev.moxinat.forcesofgravium.data.GravityPowderBlockDataStore.GravityPowderBlockData;
import dev.moxinat.forcesofgravium.data.InverterDataStore;
import dev.moxinat.forcesofgravium.data.InverterDataStore.InverterData;
import dev.moxinat.forcesofgravium.registry.ConnectableBlockRoles;
import dev.moxinat.forcesofgravium.registry.ConnectableRegistry;

import java.util.ArrayList;
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

    public static List<GravityPowderBlockData> neighboringGravityPowderData(World world, Vector3i position, Vector3i treatAsEmpty) {
        List<GravityPowderBlockData> neighbors = new ArrayList<>();
        addNeighboringGravityPowderData(world, neighbors, new Vector3i(position.getX() + 1, position.getY(), position.getZ()), treatAsEmpty);
        addNeighboringGravityPowderData(world, neighbors, new Vector3i(position.getX() - 1, position.getY(), position.getZ()), treatAsEmpty);
        addNeighboringGravityPowderData(world, neighbors, new Vector3i(position.getX(), position.getY(), position.getZ() + 1), treatAsEmpty);
        addNeighboringGravityPowderData(world, neighbors, new Vector3i(position.getX(), position.getY(), position.getZ() - 1), treatAsEmpty);
        addNeighboringGravityPowderData(world, neighbors, new Vector3i(position.getX(), position.getY() + 1, position.getZ()), treatAsEmpty);
        addNeighboringGravityPowderData(world, neighbors, new Vector3i(position.getX(), position.getY() - 1, position.getZ()), treatAsEmpty);
        return List.copyOf(neighbors);
    }

    public static List<InverterData> neighboringFrontFacingInverterData(World world, Vector3i position, Vector3i treatAsEmpty) {
        List<InverterData> neighbors = new ArrayList<>();
        addNeighboringFrontFacingInverterData(world, neighbors, position, new Vector3i(position.getX() + 1, position.getY(), position.getZ()), treatAsEmpty);
        addNeighboringFrontFacingInverterData(world, neighbors, position, new Vector3i(position.getX() - 1, position.getY(), position.getZ()), treatAsEmpty);
        addNeighboringFrontFacingInverterData(world, neighbors, position, new Vector3i(position.getX(), position.getY(), position.getZ() + 1), treatAsEmpty);
        addNeighboringFrontFacingInverterData(world, neighbors, position, new Vector3i(position.getX(), position.getY(), position.getZ() - 1), treatAsEmpty);
        addNeighboringFrontFacingInverterData(world, neighbors, position, new Vector3i(position.getX(), position.getY() + 1, position.getZ()), treatAsEmpty);
        addNeighboringFrontFacingInverterData(world, neighbors, position, new Vector3i(position.getX(), position.getY() - 1, position.getZ()), treatAsEmpty);
        return List.copyOf(neighbors);
    }

    public static List<Vector3i> sourceNeighbors(World world, Vector3i position, Vector3i treatAsEmpty) {
        List<Vector3i> sources = new ArrayList<>();
        addSourceNeighbor(world, sources, position.getX() + 1, position.getY(), position.getZ(), treatAsEmpty, WorldSide.WEST);
        addSourceNeighbor(world, sources, position.getX() - 1, position.getY(), position.getZ(), treatAsEmpty, WorldSide.EAST);
        addSourceNeighbor(world, sources, position.getX(), position.getY(), position.getZ() + 1, treatAsEmpty, WorldSide.NORTH);
        addSourceNeighbor(world, sources, position.getX(), position.getY(), position.getZ() - 1, treatAsEmpty, WorldSide.SOUTH);
        addSourceNeighbor(world, sources, position.getX(), position.getY() + 1, position.getZ(), treatAsEmpty, WorldSide.DOWN);
        addSourceNeighbor(world, sources, position.getX(), position.getY() - 1, position.getZ(), treatAsEmpty, WorldSide.UP);
        return List.copyOf(sources);
    }

    public static boolean isSourceNeighborOf(World world, Vector3i sourcePosition, Vector3i targetPosition) {
        WorldSide requiredWorldSide = worldSideFromSourceToTarget(sourcePosition, targetPosition);
        if (requiredWorldSide == null) {
            return false;
        }

        BlockType blockType = world.getBlockType(sourcePosition.getX(), sourcePosition.getY(), sourcePosition.getZ());
        if (blockType == null || !ConnectableBlockRoles.isSource(blockType.getId())) {
            return false;
        }

        BlockAccessor chunk = world.getChunk(ChunkUtil.indexChunkFromBlock(sourcePosition.getX(), sourcePosition.getZ()));
        if (chunk == null) {
            return false;
        }

        RotationTuple rotation = rotationFor(world, sourcePosition);
        return hasLocalSideFacingWorldSide(blockType.getId(), rotation, requiredWorldSide);
    }

    public static List<Vector3i> positionsAround(Vector3i center) {
        LinkedHashSet<Vector3i> positions = new LinkedHashSet<>();
        positions.add(center);
        positions.add(new Vector3i(center.getX() + 1, center.getY(), center.getZ()));
        positions.add(new Vector3i(center.getX() - 1, center.getY(), center.getZ()));
        positions.add(new Vector3i(center.getX(), center.getY(), center.getZ() + 1));
        positions.add(new Vector3i(center.getX(), center.getY(), center.getZ() - 1));
        positions.add(new Vector3i(center.getX(), center.getY() + 1, center.getZ()));
        positions.add(new Vector3i(center.getX(), center.getY() - 1, center.getZ()));
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

    private static void addNeighboringGravityPowderData(World world, List<GravityPowderBlockData> neighbors, Vector3i neighborPosition, Vector3i treatAsEmpty) {
        if (isTreatAsEmpty(neighborPosition, treatAsEmpty)) {
            return;
        }

        GravityPowderBlockData data = GravityPowderBlockDataStore.get(world, neighborPosition);
        if (data == null) {
            return;
        }

        BlockType blockType = world.getBlockType(neighborPosition.getX(), neighborPosition.getY(), neighborPosition.getZ());
        if (blockType == null || !ConnectableRegistry.isGravityPowderId(blockType.getId())) {
            return;
        }

        neighbors.add(data);
    }

    private static void addNeighboringFrontFacingInverterData(World world, List<InverterData> neighbors, Vector3i position, Vector3i neighborPosition, Vector3i treatAsEmpty) {
        if (isTreatAsEmpty(neighborPosition, treatAsEmpty)) {
            return;
        }

        BlockType blockType = world.getBlockType(neighborPosition.getX(), neighborPosition.getY(), neighborPosition.getZ());
        if (blockType == null || !ConnectableRegistry.isInverterId(blockType.getId())) {
            return;
        }

        Vector3i frontPosition = adjacentPositionForLocalSide(world, neighborPosition, ConnectableRegistry.SIDE_FRONT);
        if (!frontPosition.equals(position)) {
            return;
        }

        InverterData data = InverterDataStore.get(world, neighborPosition);
        if (data != null) {
            neighbors.add(data);
        }
    }

    private static void addSourceNeighbor(World world, List<Vector3i> sources, int x, int y, int z, Vector3i treatAsEmpty, WorldSide requiredWorldSide) {
        if (isTreatAsEmpty(new Vector3i(x, y, z), treatAsEmpty)) {
            return;
        }

        BlockType blockType = world.getBlockType(x, y, z);
        if (blockType == null || !ConnectableBlockRoles.isSource(blockType.getId())) {
            return;
        }

        BlockAccessor chunk = world.getChunk(ChunkUtil.indexChunkFromBlock(x, z));
        if (chunk == null) {
            return;
        }

        RotationTuple rotation = rotationFor(world, new Vector3i(x, y, z));
        if (hasLocalSideFacingWorldSide(blockType.getId(), rotation, requiredWorldSide)) {
            sources.add(new Vector3i(x, y, z));
        }
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

    private static WorldSide worldSideFromSourceToTarget(Vector3i sourcePosition, Vector3i targetPosition) {
        int dx = targetPosition.getX() - sourcePosition.getX();
        int dy = targetPosition.getY() - sourcePosition.getY();
        int dz = targetPosition.getZ() - sourcePosition.getZ();
        if (Math.abs(dx) + Math.abs(dy) + Math.abs(dz) != 1) {
            return null;
        }
        return WorldSide.fromVector(dx, dy, dz);
    }

    public static Vector3i adjacentPositionForLocalSide(World world, Vector3i position, int localSideMask) {
        RotationTuple rotation = rotationFor(world, position);
        WorldSide worldSide = worldSideForLocalSide(rotation, localSideMask);
        return switch (worldSide) {
            case EAST -> new Vector3i(position.getX() + 1, position.getY(), position.getZ());
            case WEST -> new Vector3i(position.getX() - 1, position.getY(), position.getZ());
            case SOUTH -> new Vector3i(position.getX(), position.getY(), position.getZ() + 1);
            case NORTH -> new Vector3i(position.getX(), position.getY(), position.getZ() - 1);
            case UP -> new Vector3i(position.getX(), position.getY() + 1, position.getZ());
            case DOWN -> new Vector3i(position.getX(), position.getY() - 1, position.getZ());
        };
    }

    @SuppressWarnings("deprecation")
    public static RotationTuple rotationFor(World world, Vector3i position) {
        RotationTuple storedRotation = ConnectableRotationStore.get(world, position);
        if (storedRotation != null) {
            return storedRotation;
        }

        try {
            if (position.getY() < 0 || position.getY() >= 320) {
                return RotationTuple.NONE;
            }

            WorldChunk chunk = world.getNonTickingChunk(ChunkUtil.indexChunkFromBlock(position.getX(), position.getZ()));
            if (chunk == null) {
                return RotationTuple.NONE;
            }

            BlockSection section = chunk.getBlockChunk().getSectionAtIndex(ChunkUtil.indexSection(position.getY()));
            return RotationTuple.get(section.getRotationIndex(ChunkUtil.indexBlock(position.getX(), position.getY(), position.getZ())));
        } catch (Exception ignored) {
            return RotationTuple.NONE;
        }
    }

    public static WorldSide worldSideForLocalSide(RotationTuple rotation, int localSideMask) {
        Vector3d rotated = rotation.rotatedVector(localNormal(localSideMask));
        int x = (int) Math.round(rotated.getX());
        int y = (int) Math.round(rotated.getY());
        int z = (int) Math.round(rotated.getZ());
        return WorldSide.fromVector(x, y, z);
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
                && treatAsEmpty.getX() == position.getX()
                && treatAsEmpty.getY() == position.getY()
                && treatAsEmpty.getZ() == position.getZ();
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
    }
}
