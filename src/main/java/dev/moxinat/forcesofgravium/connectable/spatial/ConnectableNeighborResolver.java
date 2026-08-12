package dev.moxinat.forcesofgravium.connectable.spatial;

import org.joml.Vector3d;
import org.joml.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import com.hypixel.hytale.server.core.universe.world.World;
import dev.moxinat.forcesofgravium.connectable.registry.ConnectableRegistry;
import dev.moxinat.forcesofgravium.data.Nodes;
import dev.moxinat.forcesofgravium.data.Nodes.Node;

import javax.annotation.Nonnull;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class ConnectableNeighborResolver {

    private ConnectableNeighborResolver() {
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

    private static boolean areMutuallyConnected(World world, Vector3i first, Vector3i second) {
        WorldSide firstToSecond = worldSideFromSourceToTarget(first, second);
        if (firstToSecond == null) {
            return false;
        }

        Node firstNode = Nodes.get(world, first);
        Node secondNode = Nodes.get(world, second);
        if (firstNode == null || secondNode == null) {
            return false;
        }

        int firstLocalSide = localSideForWorldSide(firstNode.rotation(), firstToSecond);
        int secondLocalSide = localSideForWorldSide(secondNode.rotation(), firstToSecond.opposite());

        boolean firstOutputsToSecond = firstNode.canOutputSignalTo(firstLocalSide)
                && (secondNode.canReceiveSignalFrom(secondLocalSide)
                || secondNode.canReceiveControlFrom(secondLocalSide));

        boolean secondOutputsToFirst = secondNode.canOutputSignalTo(secondLocalSide)
                && (firstNode.canReceiveSignalFrom(firstLocalSide)
                || firstNode.canReceiveControlFrom(firstLocalSide));

        return firstOutputsToSecond || secondOutputsToFirst;
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

    public static boolean isForwardSignalConnection(
            World world,
            Vector3i sourcePosition,
            Vector3i targetPosition
    ) {
        Node source = Nodes.get(world, sourcePosition);
        Node target = Nodes.get(world, targetPosition);

        if (source == null || target == null) {
            return false;
        }

        WorldSide sourceToTarget =
                worldSideFromSourceToTarget(sourcePosition, targetPosition);

        if (sourceToTarget == null) {
            return false;
        }

        int sourceLocalSide =
                localSideForWorldSide(source.rotation(), sourceToTarget);

        int targetLocalSide =
                localSideForWorldSide(target.rotation(), sourceToTarget.opposite());

        return source.canOutputSignalTo(sourceLocalSide)
                && target.canReceiveSignalFrom(targetLocalSide);
    }

    public static boolean isBackwardSignalConnection(
            World world,
            Vector3i sourcePosition,
            Vector3i targetPosition
    ) {
        return isForwardSignalConnection(world, targetPosition, sourcePosition);
    }

    public static boolean isControlForwardConnection(
            @Nonnull World world,
            @Nonnull Vector3i sourcePosition,
            @Nonnull Vector3i targetPosition
    ) {
        Node source = Nodes.get(world, sourcePosition);
        Node target = Nodes.get(world, targetPosition);

        if (source == null || target == null) {
            return false;
        }

        WorldSide sourceToTarget =
                worldSideFromSourceToTarget(sourcePosition, targetPosition);

        if (sourceToTarget == null) {
            return false;
        }

        int sourceLocalSide =
                localSideForWorldSide(
                        source.rotation(),
                        sourceToTarget
                );

        int targetLocalSide =
                localSideForWorldSide(
                        target.rotation(),
                        sourceToTarget.opposite()
                );

        return source.canOutputSignalTo(sourceLocalSide)
                && target.canReceiveControlFrom(targetLocalSide);
    }

    public static Set<Vector3i> allForwardSignalNeighbors(
            World world,
            Vector3i position
    ) {
        LinkedHashSet<Vector3i> result = new LinkedHashSet<>();

        for (Vector3i candidate : positionsAround(position)) {
            if (candidate.equals(position)) {
                continue;
            }

            if (isForwardSignalConnection(world, position, candidate)) {
                result.add(candidate);
            }
        }

        return Set.copyOf(result);
    }

    public static Set<Vector3i> allBackwardSignalNeighbors(
            World world,
            Vector3i position
    ) {
        LinkedHashSet<Vector3i> result = new LinkedHashSet<>();

        for (Vector3i candidate : positionsAround(position)) {
            if (candidate.equals(position)) {
                continue;
            }

            if (isBackwardSignalConnection(world, position, candidate)) {
                result.add(candidate);
            }
        }

        return Set.copyOf(result);
    }

    public static Set<Vector3i> allControlNeighbors(
            @Nonnull World world,
            @Nonnull Vector3i position
    ) {
        LinkedHashSet<Vector3i> result = new LinkedHashSet<>();

        for (Vector3i candidate : positionsAround(position)) {
            if (candidate.equals(position)) {
                continue;
            }

            if (isControlForwardConnection(
                    world,
                    position,
                    candidate
            )) {
                result.add(candidate);
            }
        }

        return Set.copyOf(result);
    }

    public static Set<Vector3i> allNetworkNeighbors(
            @Nonnull World world,
            @Nonnull Vector3i position
    ) {
        LinkedHashSet<Vector3i> result = new LinkedHashSet<>();

        result.addAll(
                allForwardSignalNeighbors(
                        world,
                        position
                )
        );

        result.addAll(
                allBackwardSignalNeighbors(
                        world,
                        position
                )
        );

        return Set.copyOf(result);
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
        Node node = Nodes.get(world, position);
        return node == null ? RotationTuple.NONE : node.rotation();
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
