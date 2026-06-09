package dev.moxinat.forcesofgravium.connectable;

import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import com.hypixel.hytale.server.core.universe.world.World;
import dev.moxinat.forcesofgravium.data.ConnectableRotationStore;
import dev.moxinat.forcesofgravium.data.GravityPowderBlockDataStore;
import dev.moxinat.forcesofgravium.data.GravityPowderBlockDataStore.GravityPowderBlockData;
import dev.moxinat.forcesofgravium.data.InverterDataStore;
import dev.moxinat.forcesofgravium.data.InverterDataStore.InverterData;
import dev.moxinat.forcesofgravium.data.SourceBlockDataStore;
import dev.moxinat.forcesofgravium.registry.ConnectableBlockRoles;
import dev.moxinat.forcesofgravium.registry.ConnectableRegistry;
import org.joml.Vector3i;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public final class ConnectableRuntimeAccessor {

    private static final Map<ConnectableRuntimeKey, Long> NETWORK_IDS = new ConcurrentHashMap<>();
    private static final Map<ConnectableRuntimeKey, Integer> ENERGY_DELTAS = new ConcurrentHashMap<>();
    private static final Map<ConnectableRuntimeKey, Boolean> PASSING_OVERRIDES = new ConcurrentHashMap<>();

    private ConnectableRuntimeAccessor() {
    }

    public static boolean isConnectable(@Nullable String blockId) {
        return ConnectableRegistry.isConnectable(blockId);
    }

    public static @Nonnull Optional<ConnectableDefinition> definitionFor(@Nullable String blockId) {
        return ConnectableDefinitions.findByBlockId(blockId);
    }

    public static @Nonnull Optional<ConnectableRuntimeData> runtimeFor(@Nonnull World world, @Nonnull Vector3i position) {
        return get(world, position);
    }

    public static @Nonnull Optional<ConnectableRuntimeData> getRuntimeData(@Nonnull World world, @Nonnull Vector3i position) {
        return get(world, position);
    }

    public static @Nonnull Optional<ConnectableRuntimeData> get(@Nonnull World world, @Nonnull Vector3i position) {
        BlockType blockType = world.getBlockType(position.x(), position.y(), position.z());
        if (blockType == null || ConnectableRegistry.isNotConnectable(blockType.getId())) {
            return Optional.empty();
        }
        return Optional.of(get(world, position, blockType.getId()));
    }

    public static @Nonnull ConnectableRuntimeData get(@Nonnull World world, @Nonnull Vector3i position, @Nonnull String blockId) {
        RotationTuple rotation = ConnectableRotationStore.getOrDefault(world, position, RotationTuple.NONE);
        long networkId = getNetworkId(world, position);

        if (ConnectableRegistry.isGravityPowderCarrierId(blockId)) {
            return fromGravityPowder(world, position, rotation, networkId);
        }
        if (ConnectableRegistry.isInverterId(blockId)) {
            return fromInverter(world, position, rotation, networkId);
        }
        if (ConnectableBlockRoles.isSource(blockId)) {
            return fromSource(world, position, blockId, rotation, networkId);
        }
        return ConnectableRuntimeData.defaultData()
                .withRotation(rotation)
                .withNetworkId(networkId);
    }

    public static void put(@Nonnull World world, @Nonnull Vector3i position, @Nonnull ConnectableRuntimeData data) {
        BlockType blockType = world.getBlockType(position.x(), position.y(), position.z());
        if (blockType == null || ConnectableRegistry.isNotConnectable(blockType.getId())) {
            return;
        }

        setRotation(world, position, data.rotation());
        setNetworkId(world, position, data.networkId());
        setPassing(world, position, data.passing());
        setEnergyDelta(world, position, data.energyDelta());
        if (ConnectableRegistry.isGravityPowderCarrierId(blockType.getId())) {
            writeGravityPowder(world, position, data);
            return;
        }
        if (ConnectableRegistry.isInverterId(blockType.getId())) {
            writeInverter(world, position, data);
            return;
        }
        if (ConnectableBlockRoles.isSource(blockType.getId())) {
            // TODO: when energy mechanics are introduced, derive source active state from energyDelta > 0.
            SourceBlockDataStore.setActive(
                    world,
                    position,
                    GravityPowderBlockDataStore.STATE_PUSH.equals(data.instantState())
            );
        }
    }

    public static void adoptInstantState(@Nonnull World world, @Nonnull Vector3i position) {
        BlockType blockType = world.getBlockType(position.x(), position.y(), position.z());
        if (blockType == null) {
            return;
        }
        if (ConnectableRegistry.isGravityPowderCarrierId(blockType.getId())) {
            GravityPowderBlockDataStore.adoptInstantState(world, position);
            return;
        }
        if (ConnectableRegistry.isInverterId(blockType.getId())) {
            InverterDataStore.adoptCurrentMode(world, position);
        }
    }

    public static void setRotation(@Nonnull World world, @Nonnull Vector3i position, @Nonnull RotationTuple rotation) {
        ConnectableRotationStore.put(world, position, rotation);
    }

    public static void setInstantState(@Nonnull World world, @Nonnull Vector3i position, @Nonnull String instantState) {
        BlockType blockType = world.getBlockType(position.x(), position.y(), position.z());
        if (blockType == null) {
            return;
        }
        if (ConnectableRegistry.isGravityPowderCarrierId(blockType.getId())) {
            GravityPowderBlockDataStore.setInstantState(world, position, instantState);
            return;
        }
        if (ConnectableRegistry.isInverterId(blockType.getId())) {
            InverterData existing = InverterDataStore.getOrCreate(world, position);
            InverterDataStore.setState(
                    world,
                    position,
                    instantState,
                    existing.nextMode(),
                    existing.invertEnabled(),
                    existing.lastToggleInputMode()
            );
        }
    }

    public static void setDirty(@Nonnull World world, @Nonnull Vector3i position, boolean dirty) {
        BlockType blockType = world.getBlockType(position.x(), position.y(), position.z());
        if (blockType == null) {
            return;
        }
        if (ConnectableRegistry.isGravityPowderCarrierId(blockType.getId())) {
            if (dirty) {
                GravityPowderBlockDataStore.markWaveDirty(world, position);
            } else {
                GravityPowderBlockDataStore.clearWaveDirty(world, position);
            }
            return;
        }
        if (ConnectableRegistry.isInverterId(blockType.getId())) {
            if (dirty) {
                InverterDataStore.markWaveDirty(world, position);
            } else {
                InverterDataStore.clearWaveDirty(world, position);
            }
        }
    }

    public static void setInvertEnabled(@Nonnull World world, @Nonnull Vector3i position, boolean invertEnabled) {
        InverterData existing = InverterDataStore.getOrCreate(world, position);
        InverterDataStore.setState(
                world,
                position,
                existing.currentMode(),
                existing.nextMode(),
                invertEnabled,
                existing.lastToggleInputMode()
        );
    }

    public static void setPassing(@Nonnull World world, @Nonnull Vector3i position, boolean passing) {
        ConnectableRuntimeKey key = ConnectableRuntimeKey.from(world, position);
        boolean defaultPassing = defaultPassing(world, position);
        if (passing == defaultPassing) {
            PASSING_OVERRIDES.remove(key);
            return;
        }
        // Runtime-only placeholder. There is no old-store field for generic pass behavior yet.
        PASSING_OVERRIDES.put(key, passing);
    }

    public static int energyDelta(@Nonnull World world, @Nonnull Vector3i position) {
        return ENERGY_DELTAS.getOrDefault(ConnectableRuntimeKey.from(world, position), defaultEnergyDelta(world, position));
    }

    public static int getEnergyDelta(@Nonnull World world, @Nonnull Vector3i position) {
        return energyDelta(world, position);
    }

    public static void setEnergyDelta(@Nonnull World world, @Nonnull Vector3i position, int energyDelta) {
        ConnectableRuntimeKey key = ConnectableRuntimeKey.from(world, position);
        int defaultEnergyDelta = defaultEnergyDelta(world, position);
        if (energyDelta == defaultEnergyDelta) {
            ENERGY_DELTAS.remove(key);
            return;
        }
        // Runtime-only placeholder. This must not activate power mechanics or source behavior yet.
        ENERGY_DELTAS.put(key, energyDelta);
    }

    public static long networkId(@Nonnull World world, @Nonnull Vector3i position) {
        return getNetworkId(world, position);
    }

    public static long getNetworkId(@Nonnull World world, @Nonnull Vector3i position) {
        return NETWORK_IDS.getOrDefault(ConnectableRuntimeKey.from(world, position), ConnectableRuntimeData.NO_NETWORK);
    }

    public static void setNetworkId(@Nonnull World world, @Nonnull Vector3i position, long networkId) {
        ConnectableRuntimeKey key = ConnectableRuntimeKey.from(world, position);
        if (networkId == ConnectableRuntimeData.NO_NETWORK) {
            NETWORK_IDS.remove(key);
            return;
        }
        NETWORK_IDS.put(key, networkId);
    }

    public static void clearNetworkId(@Nonnull World world, @Nonnull Vector3i position) {
        NETWORK_IDS.remove(ConnectableRuntimeKey.from(world, position));
    }

    public static void clearNetworkIds(@Nonnull World world) {
        String worldIdentity = world.getName();
        NETWORK_IDS.keySet().removeIf(key -> key.worldIdentity().equals(worldIdentity));
    }

    public static void clearRuntimeOnlyDataForWorld(@Nonnull World world) {
        String worldIdentity = world.getName();
        NETWORK_IDS.keySet().removeIf(key -> key.worldIdentity().equals(worldIdentity));
        ENERGY_DELTAS.keySet().removeIf(key -> key.worldIdentity().equals(worldIdentity));
        PASSING_OVERRIDES.keySet().removeIf(key -> key.worldIdentity().equals(worldIdentity));
    }

    public static @Nonnull Map<Vector3i, Long> networkIdSnapshotForWorld(@Nonnull World world) {
        String worldIdentity = world.getName();
        return NETWORK_IDS.entrySet().stream()
                .filter(entry -> entry.getKey().worldIdentity().equals(worldIdentity))
                .collect(Collectors.toMap(
                        entry -> entry.getKey().position(),
                        Map.Entry::getValue
                ));
    }

    public static int signalInputSides(@Nullable String blockId) {
        return ConnectableRegistry.signalInputSidesMask(blockId);
    }

    public static int signalOutputSides(@Nullable String blockId) {
        return ConnectableRegistry.signalOutputSidesMask(blockId);
    }

    public static int controlInputSides(@Nullable String blockId) {
        return ConnectableRegistry.controlInputSidesMask(blockId);
    }

    public static boolean canReceiveSignalFrom(@Nullable String blockId, int localSide) {
        return ConnectableRegistry.canReceiveSignalFrom(blockId, localSide);
    }

    public static boolean canOutputSignalTo(@Nullable String blockId, int localSide) {
        return ConnectableRegistry.canOutputSignalTo(blockId, localSide);
    }

    public static boolean canReceiveControlFrom(@Nullable String blockId, int localSide) {
        return ConnectableRegistry.canReceiveControlFrom(blockId, localSide);
    }

    private static @Nonnull ConnectableRuntimeData fromGravityPowder(
            @Nonnull World world,
            @Nonnull Vector3i position,
            @Nonnull RotationTuple rotation,
            long networkId
    ) {
        GravityPowderBlockData data = GravityPowderBlockDataStore.getOrCreate(world, position);
        // previousInstantState has no exact old-store field yet, so it mirrors the current instant state.
        return new ConnectableRuntimeData(
                rotation,
                data.instantState(),
                data.instantState(),
                data.previousState(),
                data.effectiveState(),
                data.dirty(),
                false,
                passing(world, position, true),
                energyDelta(world, position),
                networkId
        );
    }

    private static @Nonnull ConnectableRuntimeData fromInverter(
            @Nonnull World world,
            @Nonnull Vector3i position,
            @Nonnull RotationTuple rotation,
            long networkId
    ) {
        InverterData data = InverterDataStore.getOrCreate(world, position);
        // previousInstantState has no exact old-store field yet, so it mirrors the current output mode.
        return new ConnectableRuntimeData(
                rotation,
                data.currentMode(),
                data.currentMode(),
                data.previousState(),
                data.effectiveState(),
                data.dirty(),
                data.invertEnabled(),
                passing(world, position, true),
                energyDelta(world, position),
                networkId
        );
    }

    private static @Nonnull ConnectableRuntimeData fromSource(
            @Nonnull World world,
            @Nonnull Vector3i position,
            @Nonnull String blockId,
            @Nonnull RotationTuple rotation,
            long networkId
    ) {
        boolean active = SourceBlockDataStore.isActive(world, position, blockId);
        String state = active ? GravityPowderBlockDataStore.STATE_PUSH : GravityPowderBlockDataStore.STATE_OFF;
        // Source active state still comes from SourceBlockDataStore; energyDelta is inert runtime data for now.
        return new ConnectableRuntimeData(
                rotation,
                state,
                state,
                state,
                state,
                false,
                false,
                passing(world, position, false),
                energyDelta(world, position),
                networkId
        );
    }

    private static void writeGravityPowder(
            @Nonnull World world,
            @Nonnull Vector3i position,
            @Nonnull ConnectableRuntimeData data
    ) {
        GravityPowderBlockData existing = GravityPowderBlockDataStore.getOrCreate(world, position);
        GravityPowderBlockDataStore.put(
                world,
                position,
                new GravityPowderBlockData(
                        existing.connectionsMask(),
                        existing.stateTimeline().withInstantState(data.instantState()),
                        data.dirty()
                )
        );
    }

    private static void writeInverter(
            @Nonnull World world,
            @Nonnull Vector3i position,
            @Nonnull ConnectableRuntimeData data
    ) {
        InverterData existing = InverterDataStore.getOrCreate(world, position);
        InverterDataStore.setState(
                world,
                position,
                data.instantState(),
                existing.nextMode(),
                data.invertEnabled(),
                existing.lastToggleInputMode()
        );
        setDirty(world, position, data.dirty());
    }

    private static boolean passing(@Nonnull World world, @Nonnull Vector3i position, boolean defaultValue) {
        return PASSING_OVERRIDES.getOrDefault(ConnectableRuntimeKey.from(world, position), defaultValue);
    }

    private static boolean defaultPassing(@Nonnull World world, @Nonnull Vector3i position) {
        BlockType blockType = world.getBlockType(position.x(), position.y(), position.z());
        if (blockType == null) {
            return false;
        }
        return ConnectableRegistry.isGravityPowderCarrierId(blockType.getId())
                || ConnectableRegistry.isInverterId(blockType.getId());
    }

    private static int defaultEnergyDelta(@Nonnull World world, @Nonnull Vector3i position) {
        BlockType blockType = world.getBlockType(position.x(), position.y(), position.z());
        if (blockType == null) {
            return 0;
        }
        if (ConnectableBlockRoles.isSource(blockType.getId())) {
            return SourceBlockDataStore.isActive(world, position, blockType.getId()) ? 1 : 0;
        }
        return 0;
    }
}
