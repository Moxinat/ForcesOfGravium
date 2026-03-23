package dev.moxinat.forcesofgravium.event;

import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.accessor.BlockAccessor;
import dev.moxinat.forcesofgravium.data.ConnectableRotationStore;
import dev.moxinat.forcesofgravium.data.GravityPowderBlockDataStore;
import dev.moxinat.forcesofgravium.data.GravityPowderBlockDataStore.GravityPowderBlockData;
import dev.moxinat.forcesofgravium.data.GravityPowderBlockDataStore.PositionDistance;
import dev.moxinat.forcesofgravium.registry.ConnectableRegistry;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class CableNetworkUpdater {

    public static final String GRAVITY_POWDER_BLOCK_ID = ConnectableRegistry.GRAVITY_POWDER_BLOCK_ID;
    public static final String INVERTER_BLOCK_ID = ConnectableRegistry.INVERTER_BLOCK_ID;
    public static final int CONNECTION_EAST = 1;
    public static final int CONNECTION_WEST = 1 << 1;
    public static final int CONNECTION_SOUTH = 1 << 2;
    public static final int CONNECTION_NORTH = 1 << 3;
    public static final int CONNECTION_UP = 1 << 4;
    public static final int CONNECTION_DOWN = 1 << 5;

    private static final String ONE_CONNECT_STATE = "OneConnect";
    private static final String STRAIGHT_STATE = "Straight";
    private static final String CURVE_STATE = "Curve";
    private static final String THREE_D_CURVE_STATE = "ThreeDCurve";
    private static final String CROSS_STATE = "Cross";
    private static final String FOUR_CURVE_STATE = "FourCurve";
    private static final String FIVE_CROSS_STATE = "FiveCross";
    private static final String ALL_CONNECT_STATE = "AllConnect";
    private static final String T_CONNECT_STATE = "TConnect";
    private static final String MODE_OFF = "off";
    private static final String MODE_PUSH = "push";
    private static final String MODE_PULL = "pull";
    private static final Map<World, Set<Vector3i>> PENDING_CURRENT = new ConcurrentHashMap<>();
    private static final Map<World, Set<Vector3i>> PENDING_NEXT = new ConcurrentHashMap<>();

    private CableNetworkUpdater() {
    }

    public static void onConnectablePlaced(World world, Vector3i target) {
        enqueueCurrent(world, positionsAround(target));
    }

    public static void onConnectableBroken(World world, Vector3i target) {
        enqueueCurrent(world, positionsAround(target));
    }

    public static void tickPropagation() {
        Set<World> worlds = new HashSet<>();
        worlds.addAll(PENDING_CURRENT.keySet());
        worlds.addAll(PENDING_NEXT.keySet());
        for (World world : worlds) {
            tickWorld(world);
        }
    }

    public static boolean isNotGravityPowder(BlockType blockType) {
        if (blockType == null) {
            return true;
        }

        return !ConnectableRegistry.isGravityPowderId(blockType.getId());
    }

    public static boolean isNotInverter(BlockType blockType) {
        if (blockType == null) {
            return true;
        }

        return !ConnectableRegistry.isInverterId(blockType.getId());
    }

    private static void tickWorld(World world) {
        Set<Vector3i> current = PENDING_CURRENT.computeIfAbsent(world, ignored -> ConcurrentHashMap.newKeySet());
        if (current.isEmpty()) {
            Set<Vector3i> next = PENDING_NEXT.remove(world);
            if (next == null || next.isEmpty()) {
                PENDING_CURRENT.remove(world, current);
                return;
            }
            current.addAll(next);
        }

        List<Vector3i> positions = List.copyOf(current);
        current.clear();
        if (positions.isEmpty()) {
            return;
        }

        List<GravityPowderStateUpdate> plannedUpdates = new ArrayList<>();
        for (Vector3i position : positions) {
            if (isNotGravityPowder(world.getBlockType(position.getX(), position.getY(), position.getZ()))) {
                continue;
            }
            plannedUpdates.add(computeGravityPowderStateUpdate(world, position));
        }

        Set<Vector3i> changedPositions = new LinkedHashSet<>();
        for (GravityPowderStateUpdate update : plannedUpdates) {
            GravityPowderBlockData existing = GravityPowderBlockDataStore.getOrCreate(world, update.position());
            GravityPowderBlockDataStore.setNextMode(world, update.position(), update.nextMode());
            GravityPowderBlockDataStore.setNextPositionDistances(world, update.position(), update.nextPositionDistances());

            boolean changed = !existing.currentMode().equals(update.nextMode())
                    || !existing.positionDistances().equals(update.nextPositionDistances());
            if (changed) {
                GravityPowderBlockDataStore.setCurrentMode(world, update.position(), update.nextMode());
                GravityPowderBlockDataStore.setPositionDistances(world, update.position(), update.nextPositionDistances());
                changedPositions.add(update.position());
            }
        }

        for (Vector3i position : positions) {
            refreshAt(world, position.getX(), position.getY(), position.getZ(), null);
        }

        if (!changedPositions.isEmpty()) {
            Set<Vector3i> nextQueue = PENDING_NEXT.computeIfAbsent(world, ignored -> ConcurrentHashMap.newKeySet());
            for (Vector3i changedPosition : changedPositions) {
                nextQueue.addAll(positionsAround(changedPosition));
            }
        }
    }

    private static void refreshAt(World world, int x, int y, int z, Vector3i treatAsEmpty) {
        BlockType blockType = world.getBlockType(x, y, z);
        if (isNotGravityPowder(blockType)) {
            return;
        }

        String modeStateSuffix = modeStateSuffix(world, x, y, z);

        boolean east = isConnectable(world, x + 1, y, z, treatAsEmpty, WorldSide.WEST);
        boolean west = isConnectable(world, x - 1, y, z, treatAsEmpty, WorldSide.EAST);
        boolean south = isConnectable(world, x, y, z + 1, treatAsEmpty, WorldSide.NORTH);
        boolean north = isConnectable(world, x, y, z - 1, treatAsEmpty, WorldSide.SOUTH);
        boolean up = isConnectable(world, x, y + 1, z, treatAsEmpty, WorldSide.DOWN);
        boolean down = isConnectable(world, x, y - 1, z, treatAsEmpty, WorldSide.UP);
        GravityPowderBlockDataStore.setConnectionsMask(world, new Vector3i(x, y, z), buildConnectionsMask(east, west, south, north, up, down));
        int connectionCount = (east ? 1 : 0) + (west ? 1 : 0) + (south ? 1 : 0) + (north ? 1 : 0) + (up ? 1 : 0) + (down ? 1 : 0);

        boolean straightEastWest = east && west && !north && !south && !up && !down;
        boolean straightNorthSouth = north && south && !east && !west && !up && !down;
        boolean straightUpDown = up && down && !east && !west && !north && !south;
        boolean straight = straightEastWest || straightNorthSouth || straightUpDown;
        int oppositePairCount = (east && west ? 1 : 0) + (north && south ? 1 : 0) + (up && down ? 1 : 0);
        boolean hasOppositePair = oppositePairCount > 0;
        boolean fiveCrossHorizontalUp = east && west && north && south && up;
        boolean fiveCrossHorizontalDown = east && west && north && south && down;
        boolean fiveCrossEastWestUpDownNorth = east && west && up && down && north;
        boolean fiveCrossEastWestUpDownSouth = east && west && up && down && south;
        boolean fiveCrossNorthSouthUpDownEast = north && south && up && down && east;
        boolean fiveCross = connectionCount == 5;
        boolean allConnect = connectionCount == 6;
        boolean fourCurveNorthEast = up && down && north && east;
        boolean fourCurveEastSouth = up && down && east && south;
        boolean fourCurveSouthWest = up && down && south && west;
        boolean fourCurveWestNorth = up && down && west && north;
        boolean fourCurveNorthEastWestUp = north && east && west && up;
        boolean fourCurveNorthEastSouthUp = north && east && south && up;
        boolean fourCurveEastSouthWestUp = east && south && west && up;
        boolean fourCurveNorthSouthWestUp = north && south && west && up;
        boolean fourCurveNorthEastWestDown = north && east && west && down;
        boolean fourCurveNorthEastSouthDown = north && east && south && down;
        boolean fourCurveEastSouthWestDown = east && south && west && down;
        boolean fourCurveNorthSouthWestDown = north && south && west && down;
        boolean fourCurve = connectionCount == 4
                && (fourCurveNorthEast || fourCurveEastSouth || fourCurveSouthWest || fourCurveWestNorth
                || fourCurveNorthEastWestUp || fourCurveNorthEastSouthUp || fourCurveEastSouthWestUp || fourCurveNorthSouthWestUp
                || fourCurveNorthEastWestDown || fourCurveNorthEastSouthDown || fourCurveEastSouthWestDown || fourCurveNorthSouthWestDown);
        boolean cross = connectionCount == 4 && oppositePairCount == 2;
        boolean threeDCurveNorthEastUp = north && east && up;
        boolean threeDCurveEastSouthUp = east && south && up;
        boolean threeDCurveSouthWestUp = south && west && up;
        boolean threeDCurveWestNorthUp = west && north && up;
        boolean threeDCurveNorthEastDown = north && east && down;
        boolean threeDCurveEastSouthDown = east && south && down;
        boolean threeDCurveSouthWestDown = south && west && down;
        boolean threeDCurveWestNorthDown = west && north && down;
        boolean threeDCurve = connectionCount == 3
                && (threeDCurveNorthEastUp || threeDCurveEastSouthUp || threeDCurveSouthWestUp || threeDCurveWestNorthUp
                || threeDCurveNorthEastDown || threeDCurveEastSouthDown || threeDCurveSouthWestDown || threeDCurveWestNorthDown);
        boolean tConnect = connectionCount == 3 && hasOppositePair;
        boolean curve = connectionCount == 2;

        BlockType baseType = BlockType.fromString(GRAVITY_POWDER_BLOCK_ID);
        if (baseType == null) {
            return;
        }

        BlockAccessor chunk = world.getChunk(ChunkUtil.indexChunkFromBlock(x, z));
        if (chunk == null) {
            return;
        }

        if (straight) {
            String straightBlockKey = stateBlockKey(baseType, STRAIGHT_STATE, modeStateSuffix);
            if (straightBlockKey == null) {
                return;
            }

            RotationTuple targetRotation;
            if (straightEastWest) {
                targetRotation = RotationTuple.of(Rotation.Ninety, Rotation.None, Rotation.None);
            } else if (straightNorthSouth) {
                targetRotation = RotationTuple.of(Rotation.None, Rotation.None, Rotation.None);
            } else {
                targetRotation = RotationTuple.of(Rotation.None, Rotation.Ninety, Rotation.None);
            }
            chunk.placeBlock(x, y, z, straightBlockKey, targetRotation, 0, false);
            return;
        }

        if (connectionCount == 1) {
            String oneConnectBlockKey = stateBlockKey(baseType, ONE_CONNECT_STATE, modeStateSuffix);
            if (oneConnectBlockKey == null) {
                return;
            }

            RotationTuple targetRotation;
            if (east) {
                targetRotation = RotationTuple.of(Rotation.TwoSeventy, Rotation.None, Rotation.None);
            } else if (west) {
                targetRotation = RotationTuple.of(Rotation.Ninety, Rotation.None, Rotation.None);
            } else if (north) {
                targetRotation = RotationTuple.of(Rotation.None, Rotation.None, Rotation.None);
            } else if (south) {
                targetRotation = RotationTuple.of(Rotation.OneEighty, Rotation.None, Rotation.None);
            } else if (up) {
                targetRotation = RotationTuple.of(Rotation.None, Rotation.Ninety, Rotation.None);
            } else {
                targetRotation = RotationTuple.of(Rotation.None, Rotation.TwoSeventy, Rotation.None);
            }

            chunk.placeBlock(x, y, z, oneConnectBlockKey, targetRotation, 0, false);
            return;
        }

        if (fiveCross) {
            String fiveCrossBlockKey = stateBlockKey(baseType, FIVE_CROSS_STATE, modeStateSuffix);
            if (fiveCrossBlockKey == null) {
                return;
            }

            RotationTuple targetRotation;
            if (fiveCrossHorizontalUp) {
                targetRotation = RotationTuple.of(Rotation.None, Rotation.None, Rotation.None);
            } else if (fiveCrossHorizontalDown) {
                targetRotation = RotationTuple.of(Rotation.None, Rotation.OneEighty, Rotation.None);
            } else if (fiveCrossEastWestUpDownNorth) {
                targetRotation = RotationTuple.of(Rotation.TwoSeventy, Rotation.None, Rotation.Ninety);
            } else if (fiveCrossEastWestUpDownSouth) {
                targetRotation = RotationTuple.of(Rotation.Ninety, Rotation.None, Rotation.Ninety);
            } else if (fiveCrossNorthSouthUpDownEast) {
                targetRotation = RotationTuple.of(Rotation.OneEighty, Rotation.None, Rotation.Ninety);
            } else {
                targetRotation = RotationTuple.of(Rotation.None, Rotation.None, Rotation.Ninety);
            }

            chunk.placeBlock(x, y, z, fiveCrossBlockKey, targetRotation, 0, false);
            return;
        }

        if (allConnect) {
            String allConnectBlockKey = stateBlockKey(baseType, ALL_CONNECT_STATE, modeStateSuffix);
            if (allConnectBlockKey == null) {
                return;
            }

            chunk.placeBlock(x, y, z, allConnectBlockKey, RotationTuple.of(Rotation.None, Rotation.None, Rotation.None), 0, false);
            return;
        }

        if (fourCurve) {
            String fourCurveBlockKey = stateBlockKey(baseType, FOUR_CURVE_STATE, modeStateSuffix);
            if (fourCurveBlockKey == null) {
                return;
            }

            RotationTuple targetRotation;
            if (fourCurveNorthEast) {
                targetRotation = RotationTuple.of(Rotation.TwoSeventy, Rotation.None, Rotation.None);
            } else if (fourCurveEastSouth) {
                targetRotation = RotationTuple.of(Rotation.OneEighty, Rotation.None, Rotation.None);
            } else if (fourCurveSouthWest) {
                targetRotation = RotationTuple.of(Rotation.Ninety, Rotation.None, Rotation.None);
            } else if (fourCurveWestNorth) {
                targetRotation = RotationTuple.of(Rotation.None, Rotation.None, Rotation.None);
            } else if (fourCurveNorthEastWestUp) {
                targetRotation = RotationTuple.of(Rotation.TwoSeventy, Rotation.Ninety, Rotation.None);
            } else if (fourCurveNorthEastSouthUp) {
                targetRotation = RotationTuple.of(Rotation.OneEighty, Rotation.Ninety, Rotation.None);
            } else if (fourCurveEastSouthWestUp) {
                targetRotation = RotationTuple.of(Rotation.Ninety, Rotation.Ninety, Rotation.None);
            } else if (fourCurveNorthSouthWestUp) {
                targetRotation = RotationTuple.of(Rotation.None, Rotation.Ninety, Rotation.None);
            } else if (fourCurveNorthEastWestDown) {
                targetRotation = RotationTuple.of(Rotation.TwoSeventy, Rotation.TwoSeventy, Rotation.None);
            } else if (fourCurveNorthEastSouthDown) {
                targetRotation = RotationTuple.of(Rotation.OneEighty, Rotation.TwoSeventy, Rotation.None);
            } else if (fourCurveEastSouthWestDown) {
                targetRotation = RotationTuple.of(Rotation.Ninety, Rotation.TwoSeventy, Rotation.None);
            } else {
                targetRotation = RotationTuple.of(Rotation.None, Rotation.TwoSeventy, Rotation.None);
            }

            chunk.placeBlock(x, y, z, fourCurveBlockKey, targetRotation, 0, false);
            return;
        }

        if (cross) {
            String crossBlockKey = stateBlockKey(baseType, CROSS_STATE, modeStateSuffix);
            if (crossBlockKey == null) {
                return;
            }

            RotationTuple targetRotation;
            if (east && west && up && down) {
                targetRotation = RotationTuple.of(Rotation.None, Rotation.Ninety, Rotation.None);
            } else if (north && south && up && down) {
                targetRotation = RotationTuple.of(Rotation.Ninety, Rotation.Ninety, Rotation.None);
            } else {
                targetRotation = RotationTuple.of(Rotation.None, Rotation.None, Rotation.None);
            }

            chunk.placeBlock(x, y, z, crossBlockKey, targetRotation, 0, false);
            return;
        }

        if (tConnect) {
            String tConnectBlockKey = stateBlockKey(baseType, T_CONNECT_STATE, modeStateSuffix);
            if (tConnectBlockKey == null) {
                return;
            }

            RotationTuple targetRotation;
            if (north && east && west) {
                targetRotation = RotationTuple.of(Rotation.None, Rotation.None, Rotation.None);
            } else if (north && east && south) {
                targetRotation = RotationTuple.of(Rotation.TwoSeventy, Rotation.None, Rotation.None);
            } else if (east && south && west) {
                targetRotation = RotationTuple.of(Rotation.OneEighty, Rotation.None, Rotation.None);
            } else if (north && south && west) {
                targetRotation = RotationTuple.of(Rotation.Ninety, Rotation.None, Rotation.None);
            } else if (east && west && up) {
                targetRotation = RotationTuple.of(Rotation.None, Rotation.Ninety, Rotation.None);
            } else if (east && west && down) {
                targetRotation = RotationTuple.of(Rotation.None, Rotation.TwoSeventy, Rotation.None);
            } else if (north && south && up) {
                targetRotation = RotationTuple.of(Rotation.Ninety, Rotation.Ninety, Rotation.None);
            } else if (north && south && down) {
                targetRotation = RotationTuple.of(Rotation.Ninety, Rotation.TwoSeventy, Rotation.None);
            } else if (up && down) {
                if (north) {
                    targetRotation = RotationTuple.of(Rotation.None, Rotation.None, Rotation.Ninety);
                } else if (east) {
                    targetRotation = RotationTuple.of(Rotation.TwoSeventy, Rotation.None, Rotation.Ninety);
                } else if (south) {
                    targetRotation = RotationTuple.of(Rotation.OneEighty, Rotation.None, Rotation.Ninety);
                } else {
                    targetRotation = RotationTuple.of(Rotation.Ninety, Rotation.None, Rotation.Ninety);
                }
            } else {
                targetRotation = RotationTuple.of(Rotation.None, Rotation.None, Rotation.None);
            }

            chunk.placeBlock(x, y, z, tConnectBlockKey, targetRotation, 0, false);
            return;
        }

        if (threeDCurve) {
            String threeDCurveBlockKey = stateBlockKey(baseType, THREE_D_CURVE_STATE, modeStateSuffix);
            if (threeDCurveBlockKey == null) {
                return;
            }

            RotationTuple targetRotation;
            if (threeDCurveNorthEastUp) {
                targetRotation = RotationTuple.of(Rotation.OneEighty, Rotation.Ninety, Rotation.None);
            } else if (threeDCurveEastSouthUp) {
                targetRotation = RotationTuple.of(Rotation.Ninety, Rotation.Ninety, Rotation.None);
            } else if (threeDCurveSouthWestUp) {
                targetRotation = RotationTuple.of(Rotation.None, Rotation.Ninety, Rotation.None);
            } else if (threeDCurveWestNorthUp) {
                targetRotation = RotationTuple.of(Rotation.TwoSeventy, Rotation.Ninety, Rotation.None);
            } else if (threeDCurveNorthEastDown) {
                targetRotation = RotationTuple.of(Rotation.TwoSeventy, Rotation.TwoSeventy, Rotation.None);
            } else if (threeDCurveEastSouthDown) {
                targetRotation = RotationTuple.of(Rotation.OneEighty, Rotation.TwoSeventy, Rotation.None);
            } else if (threeDCurveSouthWestDown) {
                targetRotation = RotationTuple.of(Rotation.Ninety, Rotation.TwoSeventy, Rotation.None);
            } else {
                targetRotation = RotationTuple.of(Rotation.None, Rotation.TwoSeventy, Rotation.None);
            }

            chunk.placeBlock(x, y, z, threeDCurveBlockKey, targetRotation, 0, false);
            return;
        }

        if (curve) {
            String curveBlockKey = stateBlockKey(baseType, CURVE_STATE, modeStateSuffix);
            if (curveBlockKey == null) {
                return;
            }

            RotationTuple targetRotation;
            if (up) {
                if (north) {
                    targetRotation = RotationTuple.of(Rotation.TwoSeventy, Rotation.Ninety, Rotation.None);
                } else if (east) {
                    targetRotation = RotationTuple.of(Rotation.OneEighty, Rotation.Ninety, Rotation.None);
                } else if (south) {
                    targetRotation = RotationTuple.of(Rotation.Ninety, Rotation.Ninety, Rotation.None);
                } else {
                    targetRotation = RotationTuple.of(Rotation.None, Rotation.Ninety, Rotation.None);
                }
            } else if (down) {
                if (north) {
                    targetRotation = RotationTuple.of(Rotation.TwoSeventy, Rotation.TwoSeventy, Rotation.None);
                } else if (east) {
                    targetRotation = RotationTuple.of(Rotation.OneEighty, Rotation.TwoSeventy, Rotation.None);
                } else if (south) {
                    targetRotation = RotationTuple.of(Rotation.Ninety, Rotation.TwoSeventy, Rotation.None);
                } else {
                    targetRotation = RotationTuple.of(Rotation.None, Rotation.TwoSeventy, Rotation.None);
                }
            } else {
                if (north && east) {
                    targetRotation = RotationTuple.of(Rotation.TwoSeventy, Rotation.None, Rotation.None);
                } else if (east && south) {
                    targetRotation = RotationTuple.of(Rotation.OneEighty, Rotation.None, Rotation.None);
                } else if (south && west) {
                    targetRotation = RotationTuple.of(Rotation.Ninety, Rotation.None, Rotation.None);
                } else {
                    targetRotation = RotationTuple.of(Rotation.None, Rotation.None, Rotation.None);
                }
            }

            chunk.placeBlock(x, y, z, curveBlockKey, targetRotation, 0, false);
            return;
        }

        String defaultBlockKey = stateBlockKey(baseType, null, modeStateSuffix);
        if (defaultBlockKey == null) {
            defaultBlockKey = GRAVITY_POWDER_BLOCK_ID;
        }
        chunk.placeBlock(x, y, z, defaultBlockKey, RotationTuple.of(Rotation.None, Rotation.None, Rotation.None), 0, false);
    }

    private static boolean isConnectable(World world, int x, int y, int z, Vector3i treatAsEmpty, WorldSide requiredWorldSide) {
        if (treatAsEmpty != null && treatAsEmpty.getX() == x && treatAsEmpty.getY() == y && treatAsEmpty.getZ() == z) {
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

        RotationTuple rotation = ConnectableRotationStore.getOrDefault(world, new Vector3i(x, y, z), RotationTuple.NONE);
        return hasLocalSideFacingWorldSide(id, rotation, requiredWorldSide);
    }

    private static GravityPowderStateUpdate computeGravityPowderStateUpdate(World world, Vector3i position) {
        GravityPowderBlockData selfData = GravityPowderBlockDataStore.getOrCreate(world, position);
        List<NeighborGravityPowderData> neighbors = neighboringGravityPowders(world, position, null);
        List<Vector3i> sourceNeighbors = sourceNeighbors(world, position, null);
        boolean hasSourceNeighbor = !sourceNeighbors.isEmpty();
        List<PositionDistance> retainedDistances = retainReachableDistances(position, selfData.positionDistances(), neighbors);

        if (hasSourceNeighbor) {
            return new GravityPowderStateUpdate(position, MODE_PUSH, sourceNeighborDistances(sourceNeighbors));
        }

        ModeDistances pushCandidate = mergedCandidate(neighbors, MODE_PUSH);
        ModeDistances pullCandidate = mergedCandidate(neighbors, MODE_PULL);
        String currentMode = selfData.currentMode();

        if (MODE_OFF.equals(currentMode)) {
            if (pushCandidate != null) {
                return new GravityPowderStateUpdate(position, MODE_PUSH, pushCandidate.positionDistances());
            }
            if (pullCandidate != null) {
                return new GravityPowderStateUpdate(position, MODE_PULL, pullCandidate.positionDistances());
            }
        }

        if (MODE_PULL.equals(currentMode) && pushCandidate != null) {
            return new GravityPowderStateUpdate(position, MODE_PUSH, pushCandidate.positionDistances());
        }

        if (MODE_PUSH.equals(currentMode) && pushCandidate != null) {
            return new GravityPowderStateUpdate(position, MODE_PUSH, pushCandidate.positionDistances());
        }

        if (MODE_PULL.equals(currentMode) && pullCandidate != null) {
            return new GravityPowderStateUpdate(position, MODE_PULL, pullCandidate.positionDistances());
        }

        if (!retainedDistances.equals(selfData.positionDistances())) {
            if (retainedDistances.isEmpty()) {
                return new GravityPowderStateUpdate(position, MODE_OFF, List.of());
            }
            return new GravityPowderStateUpdate(position, currentMode, retainedDistances);
        }

        if (retainedDistances.isEmpty()) {
            return new GravityPowderStateUpdate(position, MODE_OFF, List.of());
        }

        return new GravityPowderStateUpdate(position, currentMode, selfData.positionDistances());
    }

    private static List<PositionDistance> retainReachableDistances(Vector3i position, List<PositionDistance> positionDistances, List<NeighborGravityPowderData> neighbors) {
        List<PositionDistance> retained = new ArrayList<>();
        for (PositionDistance ownDistance : positionDistances) {
            if (isTargetAdjacent(position, ownDistance) || hasNeighborWithSmallerDistance(ownDistance, neighbors)) {
                retained.add(ownDistance);
            }
        }
        return List.copyOf(retained);
    }

    private static boolean hasNeighborWithSmallerDistance(PositionDistance ownDistance, List<NeighborGravityPowderData> neighbors) {
        for (NeighborGravityPowderData neighbor : neighbors) {
            for (PositionDistance neighborDistance : neighbor.data().positionDistances()) {
                if (sameTarget(ownDistance, neighborDistance) && neighborDistance.distance() < ownDistance.distance()) {
                    return true;
                }
            }
        }
        return false;
    }

    private static ModeDistances mergedCandidate(List<NeighborGravityPowderData> neighbors, String mode) {
        List<PositionDistance> merged = new ArrayList<>();
        for (NeighborGravityPowderData neighbor : neighbors) {
            if (!mode.equals(neighbor.data().currentMode())) {
                continue;
            }

            for (PositionDistance neighborDistance : neighbor.data().positionDistances()) {
                mergeDistance(merged, new PositionDistance(
                        neighborDistance.x(),
                        neighborDistance.y(),
                        neighborDistance.z(),
                        neighborDistance.distance() + 1
                ));
            }
        }

        if (merged.isEmpty()) {
            return null;
        }
        return new ModeDistances(mode, List.copyOf(merged));
    }

    private static void mergeDistance(List<PositionDistance> distances, PositionDistance candidate) {
        for (int i = 0; i < distances.size(); i++) {
            PositionDistance existing = distances.get(i);
            if (sameTarget(existing, candidate)) {
                if (candidate.distance() < existing.distance()) {
                    distances.set(i, candidate);
                }
                return;
            }
        }
        distances.add(candidate);
    }

    private static boolean sameTarget(PositionDistance first, PositionDistance second) {
        return first.x() == second.x() && first.y() == second.y() && first.z() == second.z();
    }

    private static boolean isTargetAdjacent(Vector3i position, PositionDistance target) {
        int dx = Math.abs(position.getX() - target.x());
        int dy = Math.abs(position.getY() - target.y());
        int dz = Math.abs(position.getZ() - target.z());
        return dx + dy + dz == 1;
    }

    private static List<NeighborGravityPowderData> neighboringGravityPowders(World world, Vector3i position, Vector3i treatAsEmpty) {
        List<NeighborGravityPowderData> neighbors = new ArrayList<>();
        addNeighboringGravityPowder(world, neighbors, new Vector3i(position.getX() + 1, position.getY(), position.getZ()), treatAsEmpty);
        addNeighboringGravityPowder(world, neighbors, new Vector3i(position.getX() - 1, position.getY(), position.getZ()), treatAsEmpty);
        addNeighboringGravityPowder(world, neighbors, new Vector3i(position.getX(), position.getY(), position.getZ() + 1), treatAsEmpty);
        addNeighboringGravityPowder(world, neighbors, new Vector3i(position.getX(), position.getY(), position.getZ() - 1), treatAsEmpty);
        addNeighboringGravityPowder(world, neighbors, new Vector3i(position.getX(), position.getY() + 1, position.getZ()), treatAsEmpty);
        addNeighboringGravityPowder(world, neighbors, new Vector3i(position.getX(), position.getY() - 1, position.getZ()), treatAsEmpty);
        return neighbors;
    }

    private static void addNeighboringGravityPowder(World world, List<NeighborGravityPowderData> neighbors, Vector3i neighborPosition, Vector3i treatAsEmpty) {
        if (isTreatAsEmpty(neighborPosition, treatAsEmpty)) {
            return;
        }

        GravityPowderBlockData data = GravityPowderBlockDataStore.get(world, neighborPosition);
        if (data == null) {
            return;
        }

        BlockType blockType = world.getBlockType(neighborPosition.getX(), neighborPosition.getY(), neighborPosition.getZ());
        if (isNotGravityPowder(blockType)) {
            return;
        }

        neighbors.add(new NeighborGravityPowderData(neighborPosition, data));
    }

    private static List<Vector3i> sourceNeighbors(World world, Vector3i position, Vector3i treatAsEmpty) {
        List<Vector3i> sources = new ArrayList<>();
        addSourceNeighbor(world, sources, position.getX() + 1, position.getY(), position.getZ(), treatAsEmpty, WorldSide.WEST);
        addSourceNeighbor(world, sources, position.getX() - 1, position.getY(), position.getZ(), treatAsEmpty, WorldSide.EAST);
        addSourceNeighbor(world, sources, position.getX(), position.getY(), position.getZ() + 1, treatAsEmpty, WorldSide.NORTH);
        addSourceNeighbor(world, sources, position.getX(), position.getY(), position.getZ() - 1, treatAsEmpty, WorldSide.SOUTH);
        addSourceNeighbor(world, sources, position.getX(), position.getY() + 1, position.getZ(), treatAsEmpty, WorldSide.DOWN);
        addSourceNeighbor(world, sources, position.getX(), position.getY() - 1, position.getZ(), treatAsEmpty, WorldSide.UP);
        return List.copyOf(sources);
    }

    private static void addSourceNeighbor(World world, List<Vector3i> sources, int x, int y, int z, Vector3i treatAsEmpty, WorldSide requiredWorldSide) {
        if (isTreatAsEmpty(new Vector3i(x, y, z), treatAsEmpty)) {
            return;
        }

        BlockType blockType = world.getBlockType(x, y, z);
        if (blockType == null || !ConnectableRegistry.isSource(blockType.getId())) {
            return;
        }

        BlockAccessor chunk = world.getChunk(ChunkUtil.indexChunkFromBlock(x, z));
        if (chunk == null) {
            return;
        }

        RotationTuple rotation = ConnectableRotationStore.getOrDefault(world, new Vector3i(x, y, z), RotationTuple.NONE);
        if (hasLocalSideFacingWorldSide(blockType.getId(), rotation, requiredWorldSide)) {
            sources.add(new Vector3i(x, y, z));
        }
    }

    private static List<PositionDistance> sourceNeighborDistances(List<Vector3i> sourceNeighbors) {
        List<PositionDistance> distances = new ArrayList<>();
        for (Vector3i sourceNeighbor : sourceNeighbors) {
            mergeDistance(distances, PositionDistance.from(sourceNeighbor, 1));
        }
        return List.copyOf(distances);
    }

    private static boolean isTreatAsEmpty(Vector3i position, Vector3i treatAsEmpty) {
        return treatAsEmpty != null
                && treatAsEmpty.getX() == position.getX()
                && treatAsEmpty.getY() == position.getY()
                && treatAsEmpty.getZ() == position.getZ();
    }

    private static List<Vector3i> positionsAround(Vector3i center) {
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

    private static void enqueueCurrent(World world, Collection<Vector3i> positions) {
        if (positions.isEmpty()) {
            return;
        }
        PENDING_CURRENT.computeIfAbsent(world, ignored -> ConcurrentHashMap.newKeySet()).addAll(positions);
    }

    private static String modeStateSuffix(World world, int x, int y, int z) {
        GravityPowderBlockData data = GravityPowderBlockDataStore.get(world, new Vector3i(x, y, z));
        if (data == null) {
            return "";
        }

        return switch (data.currentMode()) {
            case MODE_PUSH -> "Push";
            case MODE_PULL -> "Pull";
            default -> "";
        };
    }

    private static String stateBlockKey(BlockType baseType, String baseState, String modeSuffix) {
        String stateName = stateName(baseType, baseState, modeSuffix);
        return stateName == null ? null : baseType.getBlockKeyForState(stateName);
    }

    private static String stateName(BlockType baseType, String baseState, String modeSuffix) {
        if (baseState == null || baseState.isBlank()) {
            String defaultStateKey = baseType.getDefaultStateKey();
            if (defaultStateKey == null || defaultStateKey.isBlank() || "default".equals(defaultStateKey)) {
                return modeSuffix.isEmpty() ? defaultStateKey : null;
            }

            String modeState = defaultStateKey + modeSuffix;
            if (!modeSuffix.isEmpty() && baseType.getBlockKeyForState(modeState) != null) {
                return modeState;
            }
            return defaultStateKey;
        }

        String modeState = baseState + modeSuffix;
        if (!modeSuffix.isEmpty() && baseType.getBlockKeyForState(modeState) != null) {
            return modeState;
        }
        return baseState;
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

    private static WorldSide worldSideForLocalSide(RotationTuple rotation, int localSideMask) {
        Vector3d rotated = rotation.rotate(localNormal(localSideMask));
        int x = (int) Math.round(rotated.getX());
        int y = (int) Math.round(rotated.getY());
        int z = (int) Math.round(rotated.getZ());
        return WorldSide.fromVector(x, y, z);
    }

    private static Vector3d localNormal(int localSideMask) {
        if (localSideMask == ConnectableRegistry.SIDE_FRONT) {
            return new Vector3d(0, 0, -1);
        }
        if (localSideMask == ConnectableRegistry.SIDE_BACK) {
            return new Vector3d(0, 0, 1);
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

    private static int buildConnectionsMask(boolean east, boolean west, boolean south, boolean north, boolean up, boolean down) {
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

    private enum WorldSide {
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

    private record NeighborGravityPowderData(Vector3i position, GravityPowderBlockData data) {
    }

    private record ModeDistances(String mode, List<PositionDistance> positionDistances) {
        private ModeDistances {
            Objects.requireNonNull(mode, "mode");
            positionDistances = List.copyOf(positionDistances);
        }
    }

    private record GravityPowderStateUpdate(Vector3i position, String nextMode, List<PositionDistance> nextPositionDistances) {
        private GravityPowderStateUpdate {
            Objects.requireNonNull(position, "position");
            Objects.requireNonNull(nextMode, "nextMode");
            nextPositionDistances = List.copyOf(nextPositionDistances);
        }
    }
}
