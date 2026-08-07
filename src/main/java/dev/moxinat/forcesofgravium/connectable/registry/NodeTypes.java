package dev.moxinat.forcesofgravium.connectable.registry;

import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import dev.moxinat.forcesofgravium.block.gravity.GravityPowderSpecialStateStore;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Default blueprints used when a new node is created.
 *
 * A NodeType is not runtime state. Every value is copied into the concrete
 * Nodes.Node instance at creation time, so any of these values can later differ
 * between two nodes of the same type.
 */
public final class NodeTypes {

    private static final String OFF = GravityPowderSpecialStateStore.STATE_OFF;
    private static final String PUSH = GravityPowderSpecialStateStore.STATE_PUSH;

    public static final NodeType GRAVITY_POWDER = new NodeType(
            ConnectableRegistry.GRAVITY_POWDER_BLOCK_ID,
            ConnectableRegistry.ALL_SIDES_MASK,
            ConnectableRegistry.ALL_SIDES_MASK,
            0,
            false,
            true,
            RotationTuple.NONE,
            OFF,
            OFF,
            OFF,
            OFF,
            false,
            false,
            true,
            0,
            0L
    );

    public static final NodeType STRAIGHT_CASED_GRAVITY_POWDER = new NodeType(
            ConnectableRegistry.STRAIGHT_CASED_GRAVITY_POWDER_BLOCK_ID,
            ConnectableRegistry.SIDE_FRONT | ConnectableRegistry.SIDE_BACK,
            ConnectableRegistry.SIDE_FRONT | ConnectableRegistry.SIDE_BACK,
            0,
            false,
            true,
            RotationTuple.NONE,
            OFF,
            OFF,
            OFF,
            OFF,
            false,
            false,
            true,
            0,
            0L
    );

    public static final NodeType CURVE_CASED_GRAVITY_POWDER = new NodeType(
            ConnectableRegistry.CURVE_CASED_GRAVITY_POWDER_BLOCK_ID,
            ConnectableRegistry.SIDE_BOTTOM | ConnectableRegistry.SIDE_BACK,
            ConnectableRegistry.SIDE_BOTTOM | ConnectableRegistry.SIDE_BACK,
            0,
            false,
            true,
            RotationTuple.NONE,
            OFF,
            OFF,
            OFF,
            OFF,
            false,
            false,
            true,
            0,
            0L
    );

    public static final NodeType INVERTER = new NodeType(
            ConnectableRegistry.INVERTER_BLOCK_ID,
            ConnectableRegistry.SIDE_BACK,
            ConnectableRegistry.SIDE_FRONT,
            ConnectableRegistry.SIDE_LEFT
                    | ConnectableRegistry.SIDE_RIGHT
                    | ConnectableRegistry.SIDE_TOP
                    | ConnectableRegistry.SIDE_BOTTOM,
            true,
            true,
            RotationTuple.NONE,
            OFF,
            OFF,
            OFF,
            OFF,
            false,
            true,
            true,
            0,
            0L
    );

    public static final NodeType WIND_GENERATOR = new NodeType(
            ConnectableRegistry.WIND_GENERATOR_BLOCK_ID,
            0,
            ConnectableRegistry.SIDE_BACK,
            0,
            false,
            false,
            RotationTuple.NONE,
            PUSH,
            PUSH,
            PUSH,
            PUSH,
            false,
            false,
            false,
            1,
            0L
    );

    public static final NodeType WOODEN_BUTTON = new NodeType(
            ConnectableRegistry.WOODEN_BUTTON_BLOCK_ID,
            0,
            ConnectableRegistry.ALL_SIDES_MASK,
            0,
            false,
            false,
            RotationTuple.NONE,
            OFF,
            OFF,
            OFF,
            OFF,
            false,
            false,
            false,
            0,
            0L
    );

    public static final NodeType GRAVIUM_SIPHON = new NodeType(
            ConnectableRegistry.GRAVIUM_SIPHON_BLOCK_ID,
            ConnectableRegistry.SIDES_EXCEPT_FRONT_BACK_MASK,
            0,
            0,
            false,
            false,
            RotationTuple.NONE,
            OFF,
            OFF,
            OFF,
            OFF,
            false,
            false,
            false,
            0,
            0L
    );

    private static final List<NodeType> ALL = List.of(
            GRAVITY_POWDER,
            STRAIGHT_CASED_GRAVITY_POWDER,
            CURVE_CASED_GRAVITY_POWDER,
            INVERTER,
            WIND_GENERATOR,
            WOODEN_BUTTON,
            GRAVIUM_SIPHON
    );

    private static final Map<String, NodeType> BY_NAME = Map.ofEntries(
            Map.entry("GravityPowder", GRAVITY_POWDER),
            Map.entry("StraightCasedGravityPowder", STRAIGHT_CASED_GRAVITY_POWDER),
            Map.entry("CurveCasedGravityPowder", CURVE_CASED_GRAVITY_POWDER),
            Map.entry("Inverter", INVERTER),
            Map.entry("WindGenerator", WIND_GENERATOR),
            Map.entry("WoodenButton", WOODEN_BUTTON),
            Map.entry("GraviumSiphon", GRAVIUM_SIPHON)
    );

    private static final Map<String, NodeType> BY_BLOCK_ID = Map.ofEntries(
            Map.entry(GRAVITY_POWDER.blockId(), GRAVITY_POWDER),
            Map.entry(STRAIGHT_CASED_GRAVITY_POWDER.blockId(), STRAIGHT_CASED_GRAVITY_POWDER),
            Map.entry(CURVE_CASED_GRAVITY_POWDER.blockId(), CURVE_CASED_GRAVITY_POWDER),
            Map.entry(INVERTER.blockId(), INVERTER),
            Map.entry(WIND_GENERATOR.blockId(), WIND_GENERATOR),
            Map.entry(WOODEN_BUTTON.blockId(), WOODEN_BUTTON),
            Map.entry(GRAVIUM_SIPHON.blockId(), GRAVIUM_SIPHON)
    );

    private NodeTypes() {
    }

    public static @Nonnull List<NodeType> all() {
        return ALL;
    }

    public static @Nonnull Optional<NodeType> find(@Nonnull String typeNameOrBlockId) {
        Objects.requireNonNull(typeNameOrBlockId, "typeNameOrBlockId");
        NodeType byName = BY_NAME.get(typeNameOrBlockId);
        return Optional.ofNullable(byName != null ? byName : BY_BLOCK_ID.get(typeNameOrBlockId));
    }

    public static @Nonnull NodeType require(@Nonnull String typeNameOrBlockId) {
        return find(typeNameOrBlockId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown node type: " + typeNameOrBlockId));
    }

    public record NodeType(
            @Nonnull String blockId,
            int signalInputSides,
            int signalOutputSides,
            int controlInputSides,
            boolean invertCapable,
            boolean passBehaviorCapable,
            @Nonnull RotationTuple rotation,
            @Nonnull String previousInstantState,
            @Nonnull String instantState,
            @Nonnull String previousEffectiveState,
            @Nonnull String effectiveState,
            boolean dirty,
            boolean invertEnabled,
            boolean passing,
            int energyDelta,
            long networkId
    ) {
        public NodeType {
            blockId = Objects.requireNonNull(blockId, "blockId");
            rotation = Objects.requireNonNull(rotation, "rotation");
            previousInstantState = Objects.requireNonNull(previousInstantState, "previousInstantState");
            instantState = Objects.requireNonNull(instantState, "instantState");
            previousEffectiveState = Objects.requireNonNull(previousEffectiveState, "previousEffectiveState");
            effectiveState = Objects.requireNonNull(effectiveState, "effectiveState");
        }
    }
}
