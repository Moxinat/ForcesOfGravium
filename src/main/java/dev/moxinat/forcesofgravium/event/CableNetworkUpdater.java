package dev.moxinat.forcesofgravium.event;

import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.accessor.BlockAccessor;

public final class CableNetworkUpdater {

    public static final String GRAVITY_POWDER_BLOCK_ID = "Gravity_Powder_Default";
    public static final String INVERTER_BLOCK_ID = "Inverter_Block";

    private static final String ONE_CONNECT_STATE = "OneConnect";
    private static final String STRAIGHT_STATE = "Straight";
    private static final String CURVE_STATE = "Curve";
    private static final String THREE_D_CURVE_STATE = "ThreeDCurve";
    private static final String CROSS_STATE = "Cross";
    private static final String FOUR_CURVE_STATE = "FourCurve";
    private static final String FIVE_CROSS_STATE = "FiveCross";
    private static final String ALL_CONNECT_STATE = "AllConnect";
    private static final String T_CONNECT_STATE = "TConnect";
    private static final String STATE_PREFIX = "*" + GRAVITY_POWDER_BLOCK_ID + "_State_";

    private CableNetworkUpdater() {
    }

    public static void onConnectablePlaced(World world, Vector3i target) {
        world.execute(() -> {
            refreshAround(world, target, null);
            world.execute(() -> refreshAround(world, target, null));
        });
    }

    public static void onConnectableBroken(World world, Vector3i target) {
        world.execute(() -> {
            refreshAround(world, target, target);
            world.execute(() -> refreshAround(world, target, null));
        });
    }

    public static boolean isNotGravityPowder(BlockType blockType) {
        if (blockType == null) {
            return true;
        }

        return !isGravityPowderId(blockType.getId());
    }

    public static boolean isNotInverter(BlockType blockType) {
        if (blockType == null) {
            return true;
        }

        return !INVERTER_BLOCK_ID.equals(blockType.getId());
    }

    private static void refreshAround(World world, Vector3i center, Vector3i treatAsEmpty) {
        refreshAt(world, center.getX(), center.getY(), center.getZ(), treatAsEmpty);
        refreshAt(world, center.getX() + 1, center.getY(), center.getZ(), treatAsEmpty);
        refreshAt(world, center.getX() - 1, center.getY(), center.getZ(), treatAsEmpty);
        refreshAt(world, center.getX(), center.getY(), center.getZ() + 1, treatAsEmpty);
        refreshAt(world, center.getX(), center.getY(), center.getZ() - 1, treatAsEmpty);
        refreshAt(world, center.getX(), center.getY() + 1, center.getZ(), treatAsEmpty);
        refreshAt(world, center.getX(), center.getY() - 1, center.getZ(), treatAsEmpty);
    }

    private static void refreshAt(World world, int x, int y, int z, Vector3i treatAsEmpty) {
        BlockType blockType = world.getBlockType(x, y, z);
        if (isNotGravityPowder(blockType)) {
            return;
        }

        boolean east = isConnectable(world, x + 1, y, z, treatAsEmpty);
        boolean west = isConnectable(world, x - 1, y, z, treatAsEmpty);
        boolean south = isConnectable(world, x, y, z + 1, treatAsEmpty);
        boolean north = isConnectable(world, x, y, z - 1, treatAsEmpty);
        boolean up = isConnectable(world, x, y + 1, z, treatAsEmpty);
        boolean down = isConnectable(world, x, y - 1, z, treatAsEmpty);
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
            String straightBlockKey = baseType.getBlockKeyForState(STRAIGHT_STATE);
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
            String oneConnectBlockKey = baseType.getBlockKeyForState(ONE_CONNECT_STATE);
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
            String fiveCrossBlockKey = baseType.getBlockKeyForState(FIVE_CROSS_STATE);
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
            String allConnectBlockKey = baseType.getBlockKeyForState(ALL_CONNECT_STATE);
            if (allConnectBlockKey == null) {
                return;
            }

            chunk.placeBlock(x, y, z, allConnectBlockKey, RotationTuple.of(Rotation.None, Rotation.None, Rotation.None), 0, false);
            return;
        }

        if (fourCurve) {
            String fourCurveBlockKey = baseType.getBlockKeyForState(FOUR_CURVE_STATE);
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
            String crossBlockKey = baseType.getBlockKeyForState(CROSS_STATE);
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
            String tConnectBlockKey = baseType.getBlockKeyForState(T_CONNECT_STATE);
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
            String threeDCurveBlockKey = baseType.getBlockKeyForState(THREE_D_CURVE_STATE);
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
            String curveBlockKey = baseType.getBlockKeyForState(CURVE_STATE);
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

        String defaultStateKey = baseType.getDefaultStateKey();
        if (defaultStateKey == null) {
            defaultStateKey = "default";
        }
        String defaultBlockKey = baseType.getBlockKeyForState(defaultStateKey);
        if (defaultBlockKey == null) {
            defaultBlockKey = GRAVITY_POWDER_BLOCK_ID;
        }
        chunk.placeBlock(x, y, z, defaultBlockKey, RotationTuple.of(Rotation.None, Rotation.None, Rotation.None), 0, false);
    }

    private static boolean isConnectable(World world, int x, int y, int z, Vector3i treatAsEmpty) {
        if (treatAsEmpty != null && treatAsEmpty.getX() == x && treatAsEmpty.getY() == y && treatAsEmpty.getZ() == z) {
            return false;
        }

        BlockType blockType = world.getBlockType(x, y, z);
        if (blockType == null) {
            return false;
        }

        String id = blockType.getId();
        return isGravityPowderId(id) || INVERTER_BLOCK_ID.equals(id);
    }

    private static boolean isGravityPowderId(String id) {
        return GRAVITY_POWDER_BLOCK_ID.equals(id) || (id != null && id.startsWith(STATE_PREFIX));
    }
}
