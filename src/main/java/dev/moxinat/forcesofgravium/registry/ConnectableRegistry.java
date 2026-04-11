package dev.moxinat.forcesofgravium.registry;

import javax.annotation.Nullable;
import java.util.Map;

public final class ConnectableRegistry {

    public static final String GRAVITY_POWDER_BLOCK_ID = "Gravity_Powder_Default";
    public static final String INVERTER_BLOCK_ID = "Inverter_Block";
    public static final String GRAVITY_POWDER_STATE_PREFIX = "*" + GRAVITY_POWDER_BLOCK_ID + "_State_";
    public static final String INVERTER_STATE_PREFIX = "*" + INVERTER_BLOCK_ID + "_State_";
    public static final int SIDE_FRONT = 1;
    public static final int SIDE_BACK = 1 << 1;
    public static final int SIDE_RIGHT = 1 << 2;
    public static final int SIDE_LEFT = 1 << 3;
    public static final int SIDE_TOP = 1 << 4;
    public static final int SIDE_BOTTOM = 1 << 5;
    public static final int ALL_SIDES_MASK = SIDE_FRONT | SIDE_BACK | SIDE_RIGHT | SIDE_LEFT | SIDE_TOP | SIDE_BOTTOM;

    private static final Map<String, Integer> CONNECTABLE_SIDE_MASKS = Map.of(
            GRAVITY_POWDER_BLOCK_ID, ALL_SIDES_MASK,
            INVERTER_BLOCK_ID, ALL_SIDES_MASK,
            "Rock_Crystal_Blue_Block", ALL_SIDES_MASK
    );

    private ConnectableRegistry() {
    }

    public static boolean isConnectable(@Nullable String blockId) {
        return getConnectableSidesMask(blockId) != 0;
    }

    public static boolean isNotConnectable(@Nullable String blockId) {
        return !isConnectable(blockId);
    }

    public static int getConnectableSidesMask(@Nullable String blockId) {
        if (blockId == null) {
            return 0;
        }

        if (isGravityPowderId(blockId)) {
            return CONNECTABLE_SIDE_MASKS.getOrDefault(GRAVITY_POWDER_BLOCK_ID, 0);
        }
        if (isInverterId(blockId)) {
            return CONNECTABLE_SIDE_MASKS.getOrDefault(INVERTER_BLOCK_ID, 0);
        }

        return CONNECTABLE_SIDE_MASKS.getOrDefault(blockId, 0);
    }

    public static boolean isConnectableOnSide(@Nullable String blockId, int sideMask) {
        return (getConnectableSidesMask(blockId) & sideMask) != 0;
    }

    public static boolean isGravityPowderId(@Nullable String blockId) {
        return GRAVITY_POWDER_BLOCK_ID.equals(blockId)
                || (blockId != null && blockId.startsWith(GRAVITY_POWDER_STATE_PREFIX));
    }

    public static boolean isInverterId(@Nullable String blockId) {
        return INVERTER_BLOCK_ID.equals(blockId)
                || (blockId != null && blockId.startsWith(INVERTER_STATE_PREFIX));
    }
}
