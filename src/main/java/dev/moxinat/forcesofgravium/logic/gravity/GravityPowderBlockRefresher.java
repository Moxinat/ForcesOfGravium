package dev.moxinat.forcesofgravium.logic.gravity;

import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.accessor.BlockAccessor;
import dev.moxinat.forcesofgravium.data.GravityPowderBlockDataStore;
import dev.moxinat.forcesofgravium.data.GravityPowderBlockDataStore.GravityPowderBlockData;
import dev.moxinat.forcesofgravium.logic.network.ConnectableNeighborResolver;
import dev.moxinat.forcesofgravium.logic.network.ConnectableNeighborResolver.WorldSide;
import dev.moxinat.forcesofgravium.registry.ConnectableRegistry;

public final class GravityPowderBlockRefresher {

    private static final String ONE_CONNECT_STATE = "OneConnect";
    private static final String STRAIGHT_STATE = "Straight";
    private static final String CURVE_STATE = "Curve";
    private static final String THREE_D_CURVE_STATE = "ThreeDCurve";
    private static final String CROSS_STATE = "Cross";
    private static final String FOUR_CURVE_STATE = "FourCurve";
    private static final String FIVE_CROSS_STATE = "FiveCross";
    private static final String ALL_CONNECT_STATE = "AllConnect";
    private static final String T_CONNECT_STATE = "TConnect";

    private GravityPowderBlockRefresher() {
    }

    public static void refreshAt(World world, Vector3i position) {
        refreshAt(world, position.getX(), position.getY(), position.getZ(), null);
    }

    public static void refreshAt(World world, int x, int y, int z, Vector3i treatAsEmpty) {
        BlockType blockType = world.getBlockType(x, y, z);
        if (blockType == null || !ConnectableRegistry.isGravityPowderId(blockType.getId())) {
            return;
        }

        String modeStateSuffix = modeStateSuffix(world, x, y, z);

        boolean east = ConnectableNeighborResolver.isConnectable(world, x + 1, y, z, treatAsEmpty, WorldSide.WEST);
        boolean west = ConnectableNeighborResolver.isConnectable(world, x - 1, y, z, treatAsEmpty, WorldSide.EAST);
        boolean south = ConnectableNeighborResolver.isConnectable(world, x, y, z + 1, treatAsEmpty, WorldSide.NORTH);
        boolean north = ConnectableNeighborResolver.isConnectable(world, x, y, z - 1, treatAsEmpty, WorldSide.SOUTH);
        boolean up = ConnectableNeighborResolver.isConnectable(world, x, y + 1, z, treatAsEmpty, WorldSide.DOWN);
        boolean down = ConnectableNeighborResolver.isConnectable(world, x, y - 1, z, treatAsEmpty, WorldSide.UP);
        GravityPowderBlockDataStore.setConnectionsMask(
                world,
                new Vector3i(x, y, z),
                ConnectableNeighborResolver.buildConnectionsMask(east, west, south, north, up, down)
        );
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

        BlockType baseType = BlockType.fromString(ConnectableRegistry.GRAVITY_POWDER_BLOCK_ID);
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
            defaultBlockKey = ConnectableRegistry.GRAVITY_POWDER_BLOCK_ID;
        }
        chunk.placeBlock(x, y, z, defaultBlockKey, RotationTuple.of(Rotation.None, Rotation.None, Rotation.None), 0, false);
    }

    private static String modeStateSuffix(World world, int x, int y, int z) {
        GravityPowderBlockData data = GravityPowderBlockDataStore.get(world, new Vector3i(x, y, z));
        if (data == null) {
            return "";
        }

        return switch (displayMode(data)) {
            case GravityPowderBlockDataStore.STATE_PUSH -> "Push";
            case GravityPowderBlockDataStore.STATE_PULL -> "Pull";
            default -> "";
        };
    }

    private static String displayMode(GravityPowderBlockData data) {
        return data.effectiveState();
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
}
