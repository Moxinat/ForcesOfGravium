package dev.moxinat.forcesofgravium.registry;

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

    private static final String STATE_MARKER = "_State_";

    private ConnectableRegistry() {
    }

    public static boolean isConnectableId(String blockId) {
        String rawBlockId = rawBlockId(blockId);

        return GRAVITY_POWDER_BLOCK_ID.equals(rawBlockId)
                || INVERTER_BLOCK_ID.equals(rawBlockId)
                || WIND_GENERATOR_BLOCK_ID.equals(rawBlockId)
                || GRAVIUM_SIPHON_BLOCK_ID.equals(rawBlockId)
                || WOODEN_BUTTON_BLOCK_ID.equals(rawBlockId)
                || STRAIGHT_CASED_GRAVITY_POWDER_BLOCK_ID.equals(rawBlockId)
                || CURVE_CASED_GRAVITY_POWDER_BLOCK_ID.equals(rawBlockId)
                || GRAVIUM_SENSOR_BLOCK_ID.equals(rawBlockId);
    }

    public static String rawBlockId(String blockId) {
        if (blockId == null || !blockId.startsWith("*")) {
            return blockId;
        }

        int stateIndex = blockId.indexOf(STATE_MARKER);

        if (stateIndex <= 1) {
            return blockId;
        }

        return blockId.substring(1, stateIndex);
    }
}
