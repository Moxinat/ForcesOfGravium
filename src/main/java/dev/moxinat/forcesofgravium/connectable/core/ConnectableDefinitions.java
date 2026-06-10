package dev.moxinat.forcesofgravium.connectable.core;

import dev.moxinat.forcesofgravium.connectable.registry.ConnectableRegistry;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;

public final class ConnectableDefinitions {

    private static final List<ConnectableDefinition> DEFINITIONS = List.of(
            new ConnectableDefinition(
                    ConnectableRegistry.GRAVITY_POWDER_BLOCK_ID,
                    ConnectableRegistry.GRAVITY_POWDER_STATE_PREFIX,
                    ConnectableRegistry.ALL_SIDES_MASK,
                    ConnectableRegistry.ALL_SIDES_MASK,
                    0,
                    false,
                    true
            ),
            new ConnectableDefinition(
                    ConnectableRegistry.STRAIGHT_CASED_GRAVITY_POWDER_BLOCK_ID,
                    ConnectableRegistry.STRAIGHT_CASED_GRAVITY_POWDER_STATE_PREFIX,
                    ConnectableRegistry.SIDE_FRONT | ConnectableRegistry.SIDE_BACK,
                    ConnectableRegistry.SIDE_FRONT | ConnectableRegistry.SIDE_BACK,
                    0,
                    false,
                    true
            ),
            new ConnectableDefinition(
                    ConnectableRegistry.CURVE_CASED_GRAVITY_POWDER_BLOCK_ID,
                    ConnectableRegistry.CURVE_CASED_GRAVITY_POWDER_STATE_PREFIX,
                    ConnectableRegistry.SIDE_BOTTOM | ConnectableRegistry.SIDE_BACK,
                    ConnectableRegistry.SIDE_BOTTOM | ConnectableRegistry.SIDE_BACK,
                    0,
                    false,
                    true
            ),
            new ConnectableDefinition(
                    ConnectableRegistry.INVERTER_BLOCK_ID,
                    ConnectableRegistry.INVERTER_STATE_PREFIX,
                    ConnectableRegistry.SIDE_BACK,
                    ConnectableRegistry.SIDE_FRONT,
                    ConnectableRegistry.SIDE_LEFT
                            | ConnectableRegistry.SIDE_RIGHT
                            | ConnectableRegistry.SIDE_TOP
                            | ConnectableRegistry.SIDE_BOTTOM,
                    true,
                    true
            ),
            new ConnectableDefinition(
                    ConnectableRegistry.WIND_GENERATOR_BLOCK_ID,
                    "",
                    0,
                    ConnectableRegistry.SIDE_BACK,
                    0,
                    false,
                    false
            ),
            new ConnectableDefinition(
                    ConnectableRegistry.WOODEN_BUTTON_BLOCK_ID,
                    ConnectableRegistry.WOODEN_BUTTON_STATE_PREFIX,
                    0,
                    ConnectableRegistry.ALL_SIDES_MASK,
                    0,
                    false,
                    false
            ),
            new ConnectableDefinition(
                    ConnectableRegistry.GRAVIUM_SIPHON_BLOCK_ID,
                    ConnectableRegistry.GRAVIUM_SIPHON_STATE_PREFIX,
                    ConnectableRegistry.SIDES_EXCEPT_FRONT_BACK_MASK,
                    0,
                    0,
                    false,
                    false
            )
    );

    private ConnectableDefinitions() {
    }

    public static @Nonnull List<ConnectableDefinition> all() {
        return DEFINITIONS;
    }

    public static @Nonnull Optional<ConnectableDefinition> findByBlockId(@Nullable String blockId) {
        if (blockId == null) {
            return Optional.empty();
        }
        return DEFINITIONS.stream()
                .filter(definition -> definition.matchesBlockId(blockId))
                .findFirst();
    }

    public static boolean isDefined(@Nullable String blockId) {
        return findByBlockId(blockId).isPresent();
    }
}
