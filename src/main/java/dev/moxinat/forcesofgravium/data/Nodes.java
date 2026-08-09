package dev.moxinat.forcesofgravium.data;

import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import com.hypixel.hytale.server.core.universe.world.World;
import dev.moxinat.forcesofgravium.connectable.SignalState;
import dev.moxinat.forcesofgravium.connectable.registry.NodeTypes.NodeType;
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
 */
public final class Nodes {

    private static final Map<NodeKey, Node> NODES = new ConcurrentHashMap<>();

    private Nodes() {
    }

    /**
     * Creates a node with explicitly provided values and immediately stores it in
     * the current runtime node map.
     */
    public static @Nonnull Node createNode(
            @Nonnull World world,
            @Nonnull Vector3i position,
            @Nonnull String blockId,
            int signalInputSides,
            int signalOutputSides,
            int controlInputSides,
            boolean invertCapable,
            boolean passBehaviorCapable,
            @Nonnull RotationTuple rotation,
            @Nonnull SignalState previousInstantState,
            @Nonnull SignalState instantState,
            @Nonnull SignalState previousEffectiveState,
            @Nonnull SignalState effectiveState,
            boolean dirty,
            boolean invertEnabled,
            boolean passing,
            int energyDelta,
            long networkId
    ) {
        Node node = new Node(
                position,
                blockId,
                signalInputSides,
                signalOutputSides,
                controlInputSides,
                invertCapable,
                passBehaviorCapable,
                rotation,
                previousInstantState,
                instantState,
                previousEffectiveState,
                effectiveState,
                dirty,
                invertEnabled,
                passing,
                energyDelta,
                networkId
        );
        put(world, node);
        return node;
    }

    /**
     * Creates and stores a node by copying all defaults from a registered NodeType.
     */
    public static @Nonnull Node createWithType(
            @Nonnull World world,
            @Nonnull Vector3i position,
            @Nonnull NodeType type
    ) {
        Objects.requireNonNull(type, "type");
        return createNode(
                world,
                position,
                type.blockId(),
                type.signalInputSides(),
                type.signalOutputSides(),
                type.controlInputSides(),
                type.invertCapable(),
                type.passBehaviorCapable(),
                type.rotation(),
                type.previousInstantState(),
                type.instantState(),
                type.previousEffectiveState(),
                type.effectiveState(),
                type.dirty(),
                type.invertEnabled(),
                type.passing(),
                type.energyDelta(),
                type.networkId()
        );
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
     * Values copied from NodeTypes stay on the concrete Node so they can later
     * change independently for individual nodes at runtime.
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
            @Nonnull SignalState previousInstantState,
            @Nonnull SignalState instantState,
            @Nonnull SignalState previousEffectiveState,
            @Nonnull SignalState effectiveState,
            boolean dirty,
            boolean invertEnabled,
            boolean passing,
            int energyDelta,
            long networkId
    ) {
        public static final long NO_NETWORK = 0L;

        public Node {
            position = new Vector3i(Objects.requireNonNull(position, "position"));

            Objects.requireNonNull(blockId, "blockId");
            Objects.requireNonNull(rotation, "rotation");
            Objects.requireNonNull(previousInstantState, "previousInstantState");
            Objects.requireNonNull(instantState, "instantState");
            Objects.requireNonNull(previousEffectiveState, "previousEffectiveState");
            Objects.requireNonNull(effectiveState, "effectiveState");
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

        public @Nonnull Node withInstantState(@Nonnull SignalState value) {
            return copy(signalInputSides, signalOutputSides, controlInputSides, invertCapable, passBehaviorCapable,
                    rotation, instantState, value, previousEffectiveState, effectiveState,
                    dirty, invertEnabled, passing, energyDelta, networkId);
        }

        public @Nonnull Node withEffectiveState(@Nonnull SignalState value) {
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
                @Nonnull SignalState newPreviousInstantState,
                @Nonnull SignalState newInstantState,
                @Nonnull SignalState newPreviousEffectiveState,
                @Nonnull SignalState newEffectiveState,
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
