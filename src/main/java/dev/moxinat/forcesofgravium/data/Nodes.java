package dev.moxinat.forcesofgravium.data;

import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import com.hypixel.hytale.server.core.universe.world.World;
import dev.moxinat.forcesofgravium.block.gravity.GravityPowderSpecialStateStore;
import dev.moxinat.forcesofgravium.connectable.core.ConnectableDefinition;
import dev.moxinat.forcesofgravium.connectable.core.ConnectableRuntimeData;
import org.joml.Vector3i;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Central runtime home for all placed connectable nodes.
 *
 * A Node contains the complete state of one concrete placed node. Values that are
 * normally defined by a node type, such as connectable sides, are copied into the
 * node when it is created so they can still be changed per node at runtime later.
 */
public final class Nodes {

    private static final Map<NodeKey, Node> NODES = new ConcurrentHashMap<>();

    private Nodes() {
    }

    public static void put(@Nonnull World world, @Nonnull Node node) {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(node, "node");
        NODES.put(NodeKey.from(world, node.position()), node);
    }

    public static @Nullable Node get(@Nonnull World world, @Nonnull Vector3i position) {
        return NODES.get(NodeKey.from(world, position));
    }

    public static boolean contains(@Nonnull World world, @Nonnull Vector3i position) {
        return NODES.containsKey(NodeKey.from(world, position));
    }

    public static void remove(@Nonnull World world, @Nonnull Vector3i position) {
        NODES.remove(NodeKey.from(world, position));
    }

    public static void clearWorld(@Nonnull World world) {
        String worldIdentity = world.getName();
        NODES.keySet().removeIf(key -> key.worldIdentity().equals(worldIdentity));
    }

    public static int sizeForWorld(@Nonnull World world) {
        String worldIdentity = world.getName();
        return (int) NODES.keySet().stream()
                .filter(key -> key.worldIdentity().equals(worldIdentity))
                .count();
    }

    public static @Nonnull Set<Vector3i> positionsForWorld(@Nonnull World world) {
        return Set.copyOf(snapshotForWorld(world).keySet());
    }

    public static @Nonnull Map<Vector3i, Node> snapshotForWorld(@Nonnull World world) {
        String worldIdentity = world.getName();
        return NODES.entrySet().stream()
                .filter(entry -> entry.getKey().worldIdentity().equals(worldIdentity))
                .collect(Collectors.toUnmodifiableMap(
                        entry -> entry.getKey().position(),
                        Map.Entry::getValue
                ));
    }

    /**
     * Complete data for one concrete node in the world.
     *
     * The connectable-side masks and capabilities intentionally live on each Node,
     * not only on a shared type definition. A registry/type can therefore act as a
     * blueprint at creation time while the concrete node stays fully mutable by
     * replacement during runtime.
     */
    public record Node(
            @Nonnull Vector3i position,
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
        public static final long NO_NETWORK = 0L;

        public Node {
            position = new Vector3i(Objects.requireNonNull(position, "position"));
            blockId = Objects.requireNonNull(blockId, "blockId");
            rotation = Objects.requireNonNull(rotation, "rotation");
            previousInstantState = GravityPowderSpecialStateStore.normalizeState(previousInstantState);
            instantState = GravityPowderSpecialStateStore.normalizeState(instantState);
            previousEffectiveState = GravityPowderSpecialStateStore.normalizeState(previousEffectiveState);
            effectiveState = GravityPowderSpecialStateStore.normalizeState(effectiveState);
        }

        /**
         * Migration bridge for the current architecture. Later the registry can
         * create Nodes directly from its node-type blueprint instead.
         */
        public static @Nonnull Node fromCurrent(
                @Nonnull Vector3i position,
                @Nonnull String blockId,
                @Nonnull ConnectableDefinition definition,
                @Nonnull ConnectableRuntimeData runtimeData
        ) {
            Objects.requireNonNull(definition, "definition");
            Objects.requireNonNull(runtimeData, "runtimeData");

            return new Node(
                    position,
                    blockId,
                    definition.signalInputSidesMask(),
                    definition.signalOutputSidesMask(),
                    definition.controlInputSidesMask(),
                    definition.invertCapable(),
                    definition.passBehaviorCapable(),
                    runtimeData.rotation(),
                    runtimeData.previousInstantState(),
                    runtimeData.instantState(),
                    runtimeData.previousEffectiveState(),
                    runtimeData.effectiveState(),
                    runtimeData.dirty(),
                    runtimeData.invertEnabled(),
                    runtimeData.passing(),
                    runtimeData.energyDelta(),
                    runtimeData.networkId()
            );
        }

        public static @Nonnull Node defaultNode(
                @Nonnull Vector3i position,
                @Nonnull String blockId,
                @Nonnull ConnectableDefinition definition
        ) {
            return fromCurrent(position, blockId, definition, ConnectableRuntimeData.defaultData());
        }

        @Override
        public @Nonnull Vector3i position() {
            return new Vector3i(position);
        }

        public boolean canReceiveSignalFrom(int localSide) {
            return (signalInputSides & localSide) != 0;
        }

        public boolean canOutputSignalTo(int localSide) {
            return (signalOutputSides & localSide) != 0;
        }

        public boolean canReceiveControlFrom(int localSide) {
            return (controlInputSides & localSide) != 0;
        }

        public boolean isSignalRuntimeNode() {
            return signalOutputSides != 0 || passBehaviorCapable || invertCapable;
        }

        public boolean shouldStorePropagatedInstantState() {
            return signalInputSides != 0 && isSignalRuntimeNode();
        }

        public @Nonnull Node withSignalInputSides(int value) {
            return copy(value, signalOutputSides, controlInputSides, invertCapable, passBehaviorCapable,
                    rotation, previousInstantState, instantState, previousEffectiveState, effectiveState,
                    dirty, invertEnabled, passing, energyDelta, networkId);
        }

        public @Nonnull Node withSignalOutputSides(int value) {
            return copy(signalInputSides, value, controlInputSides, invertCapable, passBehaviorCapable,
                    rotation, previousInstantState, instantState, previousEffectiveState, effectiveState,
                    dirty, invertEnabled, passing, energyDelta, networkId);
        }

        public @Nonnull Node withControlInputSides(int value) {
            return copy(signalInputSides, signalOutputSides, value, invertCapable, passBehaviorCapable,
                    rotation, previousInstantState, instantState, previousEffectiveState, effectiveState,
                    dirty, invertEnabled, passing, energyDelta, networkId);
        }

        public @Nonnull Node withInvertCapable(boolean value) {
            return copy(signalInputSides, signalOutputSides, controlInputSides, value, passBehaviorCapable,
                    rotation, previousInstantState, instantState, previousEffectiveState, effectiveState,
                    dirty, invertEnabled, passing, energyDelta, networkId);
        }

        public @Nonnull Node withPassBehaviorCapable(boolean value) {
            return copy(signalInputSides, signalOutputSides, controlInputSides, invertCapable, value,
                    rotation, previousInstantState, instantState, previousEffectiveState, effectiveState,
                    dirty, invertEnabled, passing, energyDelta, networkId);
        }

        public @Nonnull Node withRotation(@Nonnull RotationTuple value) {
            return copy(signalInputSides, signalOutputSides, controlInputSides, invertCapable, passBehaviorCapable,
                    value, previousInstantState, instantState, previousEffectiveState, effectiveState,
                    dirty, invertEnabled, passing, energyDelta, networkId);
        }

        public @Nonnull Node withInstantState(@Nonnull String value) {
            return copy(signalInputSides, signalOutputSides, controlInputSides, invertCapable, passBehaviorCapable,
                    rotation, instantState, value, previousEffectiveState, effectiveState,
                    dirty, invertEnabled, passing, energyDelta, networkId);
        }

        public @Nonnull Node withEffectiveState(@Nonnull String value) {
            return copy(signalInputSides, signalOutputSides, controlInputSides, invertCapable, passBehaviorCapable,
                    rotation, previousInstantState, instantState, effectiveState, value,
                    dirty, invertEnabled, passing, energyDelta, networkId);
        }

        public @Nonnull Node adoptInstantState() {
            return copy(signalInputSides, signalOutputSides, controlInputSides, invertCapable, passBehaviorCapable,
                    rotation, previousInstantState, instantState, effectiveState, instantState,
                    false, invertEnabled, passing, energyDelta, networkId);
        }

        public @Nonnull Node withDirty(boolean value) {
            return copy(signalInputSides, signalOutputSides, controlInputSides, invertCapable, passBehaviorCapable,
                    rotation, previousInstantState, instantState, previousEffectiveState, effectiveState,
                    value, invertEnabled, passing, energyDelta, networkId);
        }

        public @Nonnull Node withInvertEnabled(boolean value) {
            return copy(signalInputSides, signalOutputSides, controlInputSides, invertCapable, passBehaviorCapable,
                    rotation, previousInstantState, instantState, previousEffectiveState, effectiveState,
                    dirty, value, passing, energyDelta, networkId);
        }

        public @Nonnull Node withPassing(boolean value) {
            return copy(signalInputSides, signalOutputSides, controlInputSides, invertCapable, passBehaviorCapable,
                    rotation, previousInstantState, instantState, previousEffectiveState, effectiveState,
                    dirty, invertEnabled, value, energyDelta, networkId);
        }

        public @Nonnull Node withEnergyDelta(int value) {
            return copy(signalInputSides, signalOutputSides, controlInputSides, invertCapable, passBehaviorCapable,
                    rotation, previousInstantState, instantState, previousEffectiveState, effectiveState,
                    dirty, invertEnabled, passing, value, networkId);
        }

        public @Nonnull Node withNetworkId(long value) {
            return copy(signalInputSides, signalOutputSides, controlInputSides, invertCapable, passBehaviorCapable,
                    rotation, previousInstantState, instantState, previousEffectiveState, effectiveState,
                    dirty, invertEnabled, passing, energyDelta, value);
        }

        private @Nonnull Node copy(
                int newSignalInputSides,
                int newSignalOutputSides,
                int newControlInputSides,
                boolean newInvertCapable,
                boolean newPassBehaviorCapable,
                @Nonnull RotationTuple newRotation,
                @Nonnull String newPreviousInstantState,
                @Nonnull String newInstantState,
                @Nonnull String newPreviousEffectiveState,
                @Nonnull String newEffectiveState,
                boolean newDirty,
                boolean newInvertEnabled,
                boolean newPassing,
                int newEnergyDelta,
                long newNetworkId
        ) {
            return new Node(
                    position,
                    blockId,
                    newSignalInputSides,
                    newSignalOutputSides,
                    newControlInputSides,
                    newInvertCapable,
                    newPassBehaviorCapable,
                    newRotation,
                    newPreviousInstantState,
                    newInstantState,
                    newPreviousEffectiveState,
                    newEffectiveState,
                    newDirty,
                    newInvertEnabled,
                    newPassing,
                    newEnergyDelta,
                    newNetworkId
            );
        }
    }

    private record NodeKey(
            @Nonnull String worldIdentity,
            int x,
            int y,
            int z
    ) {
        private NodeKey {
            worldIdentity = Objects.requireNonNull(worldIdentity, "worldIdentity");
        }

        private static @Nonnull NodeKey from(@Nonnull World world, @Nonnull Vector3i position) {
            Objects.requireNonNull(world, "world");
            Objects.requireNonNull(position, "position");
            return new NodeKey(world.getName(), position.x(), position.y(), position.z());
        }

        private @Nonnull Vector3i position() {
            return new Vector3i(x, y, z);
        }
    }
}
