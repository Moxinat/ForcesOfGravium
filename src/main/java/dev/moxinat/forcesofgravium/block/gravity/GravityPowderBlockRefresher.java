package dev.moxinat.forcesofgravium.block.gravity;

import com.hypixel.hytale.math.util.ChunkUtil;
import dev.moxinat.forcesofgravium.connectable.registry.NodeTypes;
import dev.moxinat.forcesofgravium.data.Nodes;
import org.joml.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.accessor.BlockAccessor;
import dev.moxinat.forcesofgravium.connectable.spatial.ConnectableNeighborResolver;
import dev.moxinat.forcesofgravium.connectable.registry.ConnectableRegistry;

import java.util.Set;

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

        Nodes.Node node = Nodes.get(
                world,
                position
        );

        if (node == null
                || !NodeTypes.GRAVITY_POWDER
                .blockId()
                .equals(node.blockId())) {
            return;
        }

        int x = position.x();
        int y = position.y();
        int z = position.z();

        String modeStateSuffix = modeStateSuffix(node);

        Set<Vector3i> connectedNeighbors =
                ConnectableNeighborResolver.mutuallyConnectedNeighbors(
                        world,
                        position
                );



        System.out.println("\n========== GRAVITY POWDER REFRESH ==========");
        System.out.println("position: " + position);
        System.out.println("blockId: " + node.blockId());
        System.out.println("rotation: " + node.rotation());
        System.out.println("effectiveState: " + node.effectiveState());
        System.out.println("instantState: " + node.instantState());

        System.out.println(
                "sides input=" + node.signalInputSides()
                        + " output=" + node.signalOutputSides()
                        + " control=" + node.controlInputSides()
        );

        System.out.println("connectedNeighbors: " + connectedNeighbors);

        for (Vector3i candidate :
                ConnectableNeighborResolver.positionsAround(position)) {

            if (candidate.equals(position)) {
                continue;
            }

            Nodes.Node neighbor = Nodes.get(world, candidate);

            System.out.println("\n--- neighbor " + candidate + " ---");

            if (neighbor == null) {
                System.out.println("NO NODE");
                continue;
            }

            System.out.println("blockId: " + neighbor.blockId());
            System.out.println("rotation: " + neighbor.rotation());

            System.out.println(
                    "sides input=" + neighbor.signalInputSides()
                            + " output=" + neighbor.signalOutputSides()
                            + " control=" + neighbor.controlInputSides()
            );

            boolean powderToNeighborSignal =
                    ConnectableNeighborResolver.isForwardSignalConnection(
                            world,
                            position,
                            candidate
                    );

            boolean powderToNeighborControl =
                    ConnectableNeighborResolver.isControlForwardConnection(
                            world,
                            position,
                            candidate
                    );

            boolean neighborToPowderSignal =
                    ConnectableNeighborResolver.isForwardSignalConnection(
                            world,
                            candidate,
                            position
                    );

            boolean neighborToPowderControl =
                    ConnectableNeighborResolver.isControlForwardConnection(
                            world,
                            candidate,
                            position
                    );

            System.out.println(
                    "powder -> neighbor SIGNAL: "
                            + powderToNeighborSignal
            );

            System.out.println(
                    "powder -> neighbor CONTROL: "
                            + powderToNeighborControl
            );

            System.out.println(
                    "neighbor -> powder SIGNAL: "
                            + neighborToPowderSignal
            );

            System.out.println(
                    "neighbor -> powder CONTROL: "
                            + neighborToPowderControl
            );

            System.out.println(
                    "MUTUALLY CONNECTED RESULT: "
                            + connectedNeighbors.contains(candidate)
            );
        }



        boolean east = connectedNeighbors.contains(
                new Vector3i(x + 1, y, z)
        );

        boolean west = connectedNeighbors.contains(
                new Vector3i(x - 1, y, z)
        );

        boolean south = connectedNeighbors.contains(
                new Vector3i(x, y, z + 1)
        );

        boolean north = connectedNeighbors.contains(
                new Vector3i(x, y, z - 1)
        );

        boolean up = connectedNeighbors.contains(
                new Vector3i(x, y + 1, z)
        );

        boolean down = connectedNeighbors.contains(
                new Vector3i(x, y - 1, z)
        );
        int connectionCount = (east ? 1 : 0) + (west ? 1 : 0) + (south ? 1 : 0) + (north ? 1 : 0) + (up ? 1 : 0) + (down ? 1 : 0);


        System.out.println(
                "\nconnections:"
                        + " east=" + east
                        + " west=" + west
                        + " south=" + south
                        + " north=" + north
                        + " up=" + up
                        + " down=" + down
        );

        System.out.println(
                "connectionCount: " + connectionCount
        );


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


        System.out.println(
                "\nbaseType: " + baseType.getId()
        );

        System.out.println(
                "OneConnect key: "
                        + baseType.getBlockKeyForState("OneConnect")
        );

        System.out.println(
                "Straight key: "
                        + baseType.getBlockKeyForState("Straight")
        );

        System.out.println(
                "Curve key: "
                        + baseType.getBlockKeyForState("Curve")
        );


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

            System.out.println(
                    "PLACING STATE: Straight"
                            + " key=" + straightBlockKey
                            + " rotation=" + targetRotation
            );

            chunk.setBlockInteractionState(
                    position,
                    baseType,
                    stateName(baseType, STRAIGHT_STATE, modeStateSuffix)
            );
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

            System.out.println(
                    "PLACING STATE: OneConnect"
                            + " key=" + oneConnectBlockKey
                            + " rotation=" + targetRotation
            );

            chunk.setBlockInteractionState(
                    position,
                    baseType,
                    stateName(baseType, ONE_CONNECT_STATE, modeStateSuffix)
            );
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

            chunk.setBlockInteractionState(
                    position,
                    baseType,
                    stateName(baseType, FIVE_CROSS_STATE, modeStateSuffix)
            );
            return;
        }

        if (allConnect) {
            String allConnectBlockKey = stateBlockKey(baseType, ALL_CONNECT_STATE, modeStateSuffix);
            if (allConnectBlockKey == null) {
                return;
            }

            chunk.setBlockInteractionState(
                    position,
                    baseType,
                    stateName(baseType, ALL_CONNECT_STATE, modeStateSuffix)
            );
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

            chunk.setBlockInteractionState(
                    position,
                    baseType,
                    stateName(baseType, FOUR_CURVE_STATE, modeStateSuffix)
            );
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

            chunk.setBlockInteractionState(
                    position,
                    baseType,
                    stateName(baseType, CROSS_STATE, modeStateSuffix)
            );
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

            chunk.setBlockInteractionState(
                    position,
                    baseType,
                    stateName(baseType, T_CONNECT_STATE, modeStateSuffix)
            );
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

            chunk.setBlockInteractionState(
                    position,
                    baseType,
                    stateName(baseType, THREE_D_CURVE_STATE, modeStateSuffix)
            );
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

            System.out.println(
                    "PLACING STATE: Curve"
                            + " key=" + curveBlockKey
                            + " rotation=" + targetRotation
            );

            chunk.setBlockInteractionState(
                    position,
                    baseType,
                    stateName(baseType, CURVE_STATE, modeStateSuffix)
            );
            return;
        }

        String defaultState = stateName(baseType, null, modeStateSuffix);
        if (defaultState == null) {
            return;
        }

        System.out.println(
                "PLACING STATE: DEFAULT"
                        + " state=" + defaultState
        );

        chunk.setBlockInteractionState(
                position,
                baseType,
                defaultState
        );
    }

    private static String modeStateSuffix(Nodes.Node node) {
        return switch (node.effectiveState()) {
            case PUSH -> "Push";
            case PULL -> "Pull";
            case OFF -> "";
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
}
