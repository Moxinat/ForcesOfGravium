package dev.moxinat.forcesofgravium.registry;

import javax.annotation.Nullable;
import java.util.Map;

public final class ConnectableRegistry {

    public static final String GRAVITY_POWDER_BLOCK_ID = "Gravity_Powder_Default";
    public static final String INVERTER_BLOCK_ID = "Inverter_Block";
    public static final String WIND_GENERATOR_BLOCK_ID = "WindGenerator_Block";
    public static final String GRAVIUM_SIPHON_BLOCK_ID = "Gravium_Siphon_Block";
    public static final String WOODEN_BUTTON_BLOCK_ID = "Wooden_Button_Block";
    public static final String GRAVITY_POWDER_STATE_PREFIX = "*" + GRAVITY_POWDER_BLOCK_ID + "_State_";
    public static final String INVERTER_STATE_PREFIX = "*" + INVERTER_BLOCK_ID + "_State_";
    public static final String GRAVIUM_SIPHON_STATE_PREFIX = "*" + GRAVIUM_SIPHON_BLOCK_ID + "_State_";
    public static final String WOODEN_BUTTON_STATE_PREFIX = "*" + WOODEN_BUTTON_BLOCK_ID + "_State_";
    public static final int SIDE_FRONT = 1;
    public static final int SIDE_BACK = 1 << 1;
    public static final int SIDE_RIGHT = 1 << 2;
    public static final int SIDE_LEFT = 1 << 3;
    public static final int SIDE_TOP = 1 << 4;
    public static final int SIDE_BOTTOM = 1 << 5;
    public static final int ALL_SIDES_MASK = SIDE_FRONT | SIDE_BACK | SIDE_RIGHT | SIDE_LEFT | SIDE_TOP | SIDE_BOTTOM;
    public static final int HORIZONTAL_SIDES_MASK = SIDE_FRONT | SIDE_BACK | SIDE_RIGHT | SIDE_LEFT;
    public static final int SIDES_EXCEPT_FRONT_BACK_MASK = SIDE_RIGHT | SIDE_LEFT | SIDE_TOP | SIDE_BOTTOM;

    private static final Map<String, Integer> CONNECTABLE_SIDE_MASKS = Map.of(
            GRAVITY_POWDER_BLOCK_ID, ALL_SIDES_MASK,
            INVERTER_BLOCK_ID, ALL_SIDES_MASK,
            WIND_GENERATOR_BLOCK_ID, SIDE_BACK,
            GRAVIUM_SIPHON_BLOCK_ID, SIDES_EXCEPT_FRONT_BACK_MASK,
            WOODEN_BUTTON_BLOCK_ID, ALL_SIDES_MASK
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
        if (isGraviumSiphonId(blockId)) {
            return CONNECTABLE_SIDE_MASKS.getOrDefault(GRAVIUM_SIPHON_BLOCK_ID, 0);
        }
        if (isWoodenButtonId(blockId)) {
            return CONNECTABLE_SIDE_MASKS.getOrDefault(WOODEN_BUTTON_BLOCK_ID, 0);
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

    public static boolean isGraviumSiphonId(@Nullable String blockId) {
        return GRAVIUM_SIPHON_BLOCK_ID.equals(blockId)
                || (blockId != null && blockId.startsWith(GRAVIUM_SIPHON_STATE_PREFIX));
    }

    public static boolean isWoodenButtonId(@Nullable String blockId) {
        return WOODEN_BUTTON_BLOCK_ID.equals(blockId)
                || (blockId != null && blockId.startsWith(WOODEN_BUTTON_STATE_PREFIX));
    }
}
