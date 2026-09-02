package dev.moxinat.forcesofgravium.registry;

import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;

public final class ConnectableRegistry {

    public static final String GRAVITY_POWDER_BLOCK_ID = "Gravity_Powder_Default";
    public static final String INVERTER_BLOCK_ID = "Inverter_Block";
    public static final String WIND_GENERATOR_BLOCK_ID = "WindGenerator_Block";
    public static final String GRAVIUM_SIPHON_BLOCK_ID = "Gravium_Siphon_Block";
    public static final String WOODEN_BUTTON_BLOCK_ID = "Wooden_Button_Block";
    public static final String STRAIGHT_CASED_GRAVITY_POWDER_BLOCK_ID = "Straight_Cased_Gravity_Powder";
    public static final String CURVE_CASED_GRAVITY_POWDER_BLOCK_ID = "Curve_Cased_Gravity_Powder";
    public static final String GRAVIUM_SENSOR_BLOCK_ID = "Gravium_Sensor_Block";
    public static final String GRAVITY_POWDER_STATE_PREFIX = "*" + GRAVITY_POWDER_BLOCK_ID + "_State_";
    public static final String INVERTER_STATE_PREFIX = "*" + INVERTER_BLOCK_ID + "_State_";
    public static final String GRAVIUM_SIPHON_STATE_PREFIX = "*" + GRAVIUM_SIPHON_BLOCK_ID + "_State_";
    public static final String WOODEN_BUTTON_STATE_PREFIX = "*" + WOODEN_BUTTON_BLOCK_ID + "_State_";
    public static final String STRAIGHT_CASED_GRAVITY_POWDER_STATE_PREFIX = "*" + STRAIGHT_CASED_GRAVITY_POWDER_BLOCK_ID + "_State_";
    public static final String CURVE_CASED_GRAVITY_POWDER_STATE_PREFIX = "*" + CURVE_CASED_GRAVITY_POWDER_BLOCK_ID + "_State_";
    public static final int SIDE_FRONT = 1;
    public static final int SIDE_BACK = 1 << 1;
    public static final int SIDE_RIGHT = 1 << 2;
    public static final int SIDE_LEFT = 1 << 3;
    public static final int SIDE_TOP = 1 << 4;
    public static final int SIDE_BOTTOM = 1 << 5;
    public static final int ALL_SIDES_MASK = SIDE_FRONT | SIDE_BACK | SIDE_RIGHT | SIDE_LEFT | SIDE_TOP | SIDE_BOTTOM;
    public static final int SIDES_EXCEPT_FRONT_BACK_MASK = SIDE_RIGHT | SIDE_LEFT | SIDE_TOP | SIDE_BOTTOM;

    private ConnectableRegistry() {
    }

    public static boolean isConnectableId(String blockId) {
        return GRAVITY_POWDER_BLOCK_ID.equals(blockId)
                || INVERTER_BLOCK_ID.equals(blockId)
                || WIND_GENERATOR_BLOCK_ID.equals(blockId)
                || GRAVIUM_SIPHON_BLOCK_ID.equals(blockId)
                || WOODEN_BUTTON_BLOCK_ID.equals(blockId)
                || STRAIGHT_CASED_GRAVITY_POWDER_BLOCK_ID.equals(blockId)
                || CURVE_CASED_GRAVITY_POWDER_BLOCK_ID.equals(blockId)
                || GRAVIUM_SENSOR_BLOCK_ID.equals(blockId);
    }

    public static String rawBlockId(String blockId) {
        BlockType blockType =
                BlockType.getAssetMap().getAsset(blockId);

        if (blockType == null || !blockType.isState()) {
            return blockId;
        }

        for (BlockType candidate :
                BlockType.getAssetMap().getAssetMap().values()) {

            if (candidate.isState()) {
                continue;
            }

            if (candidate.getStateForBlock(blockId) != null) {
                return candidate.getId();
            }
        }

        return blockId;
    }
}
