package dev.moxinat.forcesofgravium.registry;

import dev.moxinat.forcesofgravium.connectable.ConnectableDefinition;
import dev.moxinat.forcesofgravium.connectable.ConnectableDefinitions;

import javax.annotation.Nullable;
import java.util.Optional;

public final class ConnectableRegistry {

    public static final String GRAVITY_POWDER_BLOCK_ID = "Gravity_Powder_Default";
    public static final String INVERTER_BLOCK_ID = "Inverter_Block";
    public static final String WIND_GENERATOR_BLOCK_ID = "WindGenerator_Block";
    public static final String GRAVIUM_SIPHON_BLOCK_ID = "Gravium_Siphon_Block";
    public static final String WOODEN_BUTTON_BLOCK_ID = "Wooden_Button_Block";
    public static final String STRAIGHT_CASED_GRAVITY_POWDER_BLOCK_ID = "Straight_Cased_Gravity_Powder";
    public static final String CURVE_CASED_GRAVITY_POWDER_BLOCK_ID = "Curve_Cased_Gravity_Powder";
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

    public static boolean isConnectable(@Nullable String blockId) {
        return getConnectableSidesMask(blockId) != 0;
    }

    public static boolean isNotConnectable(@Nullable String blockId) {
        return !isConnectable(blockId);
    }

    public static int getConnectableSidesMask(@Nullable String blockId) {
        Optional<ConnectableDefinition> definition = ConnectableDefinitions.findByBlockId(blockId);
        if (definition.isEmpty()) {
            return 0;
        }

        ConnectableDefinition value = definition.get();
        return value.signalInputSidesMask() | value.signalOutputSidesMask() | value.controlInputSidesMask();
    }

    public static boolean isConnectableOnSide(@Nullable String blockId, int sideMask) {
        return (getConnectableSidesMask(blockId) & sideMask) != 0;
    }

    public static int signalInputSidesMask(@Nullable String blockId) {
        return ConnectableDefinitions.findByBlockId(blockId)
                .map(ConnectableDefinition::signalInputSidesMask)
                .orElse(0);
    }

    public static int signalOutputSidesMask(@Nullable String blockId) {
        return ConnectableDefinitions.findByBlockId(blockId)
                .map(ConnectableDefinition::signalOutputSidesMask)
                .orElse(0);
    }

    public static int controlInputSidesMask(@Nullable String blockId) {
        return ConnectableDefinitions.findByBlockId(blockId)
                .map(ConnectableDefinition::controlInputSidesMask)
                .orElse(0);
    }

    public static boolean canReceiveSignalFrom(@Nullable String blockId, int localSide) {
        return (signalInputSidesMask(blockId) & localSide) != 0;
    }

    public static boolean canOutputSignalTo(@Nullable String blockId, int localSide) {
        return (signalOutputSidesMask(blockId) & localSide) != 0;
    }

    public static boolean canReceiveControlFrom(@Nullable String blockId, int localSide) {
        return (controlInputSidesMask(blockId) & localSide) != 0;
    }

    public static boolean isInvertCapable(@Nullable String blockId) {
        return ConnectableDefinitions.findByBlockId(blockId)
                .map(ConnectableDefinition::invertCapable)
                .orElse(false);
    }

    public static boolean isPassBehaviorCapable(@Nullable String blockId) {
        return ConnectableDefinitions.findByBlockId(blockId)
                .map(ConnectableDefinition::passBehaviorCapable)
                .orElse(false);
    }

    public static boolean isGravityPowderId(@Nullable String blockId) {
        return GRAVITY_POWDER_BLOCK_ID.equals(blockId)
                || (blockId != null && blockId.startsWith(GRAVITY_POWDER_STATE_PREFIX));
    }

    public static boolean isGravityPowderCarrierId(@Nullable String blockId) {
        return isGravityPowderId(blockId) || isCasedGravityPowderId(blockId);
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

    public static boolean isStraightCasedGravityPowderId(@Nullable String blockId) {
        return STRAIGHT_CASED_GRAVITY_POWDER_BLOCK_ID.equals(blockId)
                || (blockId != null && blockId.startsWith(STRAIGHT_CASED_GRAVITY_POWDER_STATE_PREFIX));
    }

    public static boolean isCurveCasedGravityPowderId(@Nullable String blockId) {
        return CURVE_CASED_GRAVITY_POWDER_BLOCK_ID.equals(blockId)
                || (blockId != null && blockId.startsWith(CURVE_CASED_GRAVITY_POWDER_STATE_PREFIX));
    }

    public static boolean isCasedGravityPowderId(@Nullable String blockId) {
        return isStraightCasedGravityPowderId(blockId) || isCurveCasedGravityPowderId(blockId);
    }
}
