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
import dev.moxinat.forcesofgravium.data.StateTimeline;
import dev.moxinat.forcesofgravium.registry.ConnectableBlockRoles;
import dev.moxinat.forcesofgravium.registry.ConnectableRegistry;
import org.joml.Vector3i;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public final class ConnectableRuntimeAccessor {

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
        ConnectableRuntimeData mirror = ConnectableDataStore.get(world, position);
        if (mirror != null) {
            if (ConnectableRegistry.isInverterId(blockId) && isGenericDefaultRuntime(mirror)) {
                InverterData inverterData = InverterDataStore.get(world, position);
                ConnectableRuntimeData repaired = inverterData == null
                        ? defaultInverterRuntimeData(mirror.rotation(), mirror.networkId())
                        : fromInverter(world, position, mirror.rotation(), mirror.networkId());
                ConnectableDataStore.put(world, position, repaired);
                if (inverterData == null) {
                    writeInverter(world, position, repaired);
                }
                return repaired;
            }
            if (ConnectableBlockRoles.isSource(blockId) && shouldRepairSourceRuntime(world, position, blockId, mirror)) {
                ConnectableRuntimeData repaired = fromSource(world, position, blockId, mirror.rotation(), mirror.networkId());
                ConnectableDataStore.put(world, position, repaired);
                syncCompatibilityStore(world, position, blockId, repaired);
                return repaired;
            }
            return mirror;
        }

        RotationTuple rotation = ConnectableRotationStore.getOrDefault(world, position, RotationTuple.NONE);
        ConnectableRuntimeData data = fromOldStores(world, position, blockId, rotation, ConnectableRuntimeData.NO_NETWORK);
        ConnectableDataStore.put(world, position, data);
        return data;
    }

    public static void put(@Nonnull World world, @Nonnull Vector3i position, @Nonnull ConnectableRuntimeData data) {
        BlockType blockType = world.getBlockType(position.x(), position.y(), position.z());
        if (blockType == null || ConnectableRegistry.isNotConnectable(blockType.getId())) {
            return;
        }

        ConnectableDataStore.put(world, position, data);
        ConnectableRotationStore.put(world, position, data.rotation());
        syncCompatibilityStore(world, position, blockType.getId(), data);
    }

    public static void adoptInstantState(@Nonnull World world, @Nonnull Vector3i position) {
        BlockType blockType = world.getBlockType(position.x(), position.y(), position.z());
        if (blockType == null) {
            return;
        }
        ConnectableRuntimeData current = currentOrDefault(world, position);
        ConnectableRuntimeData updated = new ConnectableRuntimeData(
                current.rotation(),
                current.previousInstantState(),
                current.instantState(),
                current.effectiveState(),
                current.instantState(),
                false,
                current.invertEnabled(),
                current.passing(),
                current.energyDelta(),
                current.networkId()
        );
        mirrorRuntimeData(world, position, updated);
        syncCompatibilityStore(world, position, blockType.getId(), updated);
    }

    public static @Nonnull RotationTuple rotation(@Nonnull World world, @Nonnull Vector3i position) {
        return getRotation(world, position);
    }

    public static @Nonnull RotationTuple getRotation(@Nonnull World world, @Nonnull Vector3i position) {
        ConnectableRuntimeData mirror = ConnectableDataStore.get(world, position);
        if (mirror != null) {
            return mirror.rotation();
        }

        RotationTuple rotation = ConnectableRotationStore.getOrDefault(world, position, RotationTuple.NONE);
        // Rotation is runtime-owned by ConnectableDataStore from this step onward.
        // The old rotation store remains the persistence/compatibility fallback until save migration.
        BlockType blockType = world.getBlockType(position.x(), position.y(), position.z());
        ConnectableRuntimeData data = blockType == null || ConnectableRegistry.isNotConnectable(blockType.getId())
                ? ConnectableRuntimeData.defaultData().withRotation(rotation)
                : fromOldStores(world, position, blockType.getId(), rotation, ConnectableRuntimeData.NO_NETWORK);
        ConnectableDataStore.put(world, position, data);
        return rotation;
    }

    public static @Nullable RotationTuple storedRotation(@Nonnull World world, @Nonnull Vector3i position) {
        RotationTuple rotation = ConnectableRotationStore.get(world, position);
        if (rotation == null) {
            return null;
        }

        ConnectableRuntimeData mirror = ConnectableDataStore.get(world, position);
        if (mirror != null) {
            return mirror.rotation();
        }

        BlockType blockType = world.getBlockType(position.x(), position.y(), position.z());
        ConnectableRuntimeData data = blockType == null || ConnectableRegistry.isNotConnectable(blockType.getId())
                ? ConnectableRuntimeData.defaultData().withRotation(rotation)
                : fromOldStores(world, position, blockType.getId(), rotation, ConnectableRuntimeData.NO_NETWORK);
        ConnectableDataStore.put(world, position, data);
        return rotation;
    }

    public static void setRotation(@Nonnull World world, @Nonnull Vector3i position, @Nonnull RotationTuple rotation) {
        ConnectableRuntimeData data = get(world, position)
                .orElseGet(() -> dataForRotationOnlyWrite(world, position, rotation));
        ConnectableDataStore.put(world, position, data.withRotation(rotation));
        ConnectableRotationStore.put(world, position, rotation);
    }

    public static void remove(@Nonnull World world, @Nonnull Vector3i position) {
        ConnectableDataStore.remove(world, position);
        ConnectableRotationStore.remove(world, position);
    }

    public static void setInstantState(@Nonnull World world, @Nonnull Vector3i position, @Nonnull String instantState) {
        BlockType blockType = world.getBlockType(position.x(), position.y(), position.z());
        if (blockType == null) {
            return;
        }
        ConnectableRuntimeData updated = currentOrDefault(world, position).withInstantState(instantState);
        mirrorRuntimeData(world, position, updated);
        syncCompatibilityStore(world, position, blockType.getId(), updated);
    }

    public static void setDirty(@Nonnull World world, @Nonnull Vector3i position, boolean dirty) {
        BlockType blockType = world.getBlockType(position.x(), position.y(), position.z());
        if (blockType == null) {
            return;
        }
        ConnectableRuntimeData updated = currentOrDefault(world, position).withDirty(dirty);
        mirrorRuntimeData(world, position, updated);
        syncCompatibilityStore(world, position, blockType.getId(), updated);
    }

    public static void setInvertEnabled(@Nonnull World world, @Nonnull Vector3i position, boolean invertEnabled) {
        ConnectableRuntimeData updated = currentOrDefault(world, position).withInvertEnabled(invertEnabled);
        mirrorRuntimeData(world, position, updated);
        syncCompatibilityStore(world, position, ConnectableRegistry.INVERTER_BLOCK_ID, updated);
    }

    public static @Nonnull String instantState(@Nonnull World world, @Nonnull Vector3i position) {
        return currentOrDefault(world, position).instantState();
    }

    public static @Nonnull String effectiveState(@Nonnull World world, @Nonnull Vector3i position) {
        return currentOrDefault(world, position).effectiveState();
    }

    public static boolean isDirty(@Nonnull World world, @Nonnull Vector3i position) {
        return currentOrDefault(world, position).dirty();
    }

    public static boolean invertEnabled(@Nonnull World world, @Nonnull Vector3i position) {
        return currentOrDefault(world, position).invertEnabled();
    }

    public static void setInverterState(
            @Nonnull World world,
            @Nonnull Vector3i position,
            @Nonnull String instantState,
            @Nonnull String nextMode,
            boolean invertEnabled,
            @Nonnull String lastToggleInputMode
    ) {
        ConnectableRuntimeData current = currentOrDefault(world, position);
        ConnectableRuntimeData updated = new ConnectableRuntimeData(
                current.rotation(),
                current.instantState(),
                instantState,
                current.previousEffectiveState(),
                current.effectiveState(),
                current.dirty(),
                invertEnabled,
                current.passing(),
                current.energyDelta(),
                current.networkId()
        );
        mirrorRuntimeData(world, position, updated);
        writeInverter(world, position, updated, nextMode, lastToggleInputMode);
    }

    public static void setPassing(@Nonnull World world, @Nonnull Vector3i position, boolean passing) {
        // Runtime-only placeholder. There is no old-store field for generic pass behavior yet.
        mirrorRuntimeData(world, position, currentOrDefault(world, position).withPassing(passing));
    }

    public static int energyDelta(@Nonnull World world, @Nonnull Vector3i position) {
        return currentOrDefault(world, position).energyDelta();
    }

    public static int getEnergyDelta(@Nonnull World world, @Nonnull Vector3i position) {
        return energyDelta(world, position);
    }

    public static boolean contributesEnergy(@Nonnull World world, @Nonnull Vector3i position) {
        return getEnergyDelta(world, position) != 0;
    }

    public static boolean isEnergyProducer(@Nonnull World world, @Nonnull Vector3i position) {
        return getEnergyDelta(world, position) > 0;
    }

    public static boolean isEnergyConsumer(@Nonnull World world, @Nonnull Vector3i position) {
        return getEnergyDelta(world, position) < 0;
    }

    public static void setEnergyDelta(@Nonnull World world, @Nonnull Vector3i position, int energyDelta) {
        ConnectableRuntimeData current = currentOrDefault(world, position);
        BlockType blockType = world.getBlockType(position.x(), position.y(), position.z());
        if (blockType != null && ConnectableBlockRoles.isSource(blockType.getId())) {
            mirrorRuntimeData(world, position, fromSource(world, position, current.rotation(), energyDelta, current.networkId()));
            // Compatibility bridge only: source save data mirrors runtime source truth until save migration.
            SourceBlockDataStore.setActive(world, position, energyDelta > 0);
            return;
        }
        mirrorRuntimeData(world, position, current.withEnergyDelta(energyDelta));
    }

    public static boolean isSignalSourceActive(@Nonnull World world, @Nonnull Vector3i position) {
        BlockType blockType = world.getBlockType(position.x(), position.y(), position.z());
        return blockType != null
                && ConnectableBlockRoles.isSource(blockType.getId())
                && energyDelta(world, position) > 0;
    }

    public static @Nonnull String sourceOutputState(@Nonnull World world, @Nonnull Vector3i position) {
        return isSignalSourceActive(world, position)
                ? GravityPowderBlockDataStore.STATE_PUSH
                : GravityPowderBlockDataStore.STATE_OFF;
    }

    public static long networkId(@Nonnull World world, @Nonnull Vector3i position) {
        return getNetworkId(world, position);
    }

    public static long getNetworkId(@Nonnull World world, @Nonnull Vector3i position) {
        ConnectableRuntimeData data = ConnectableDataStore.get(world, position);
        return data == null ? ConnectableRuntimeData.NO_NETWORK : data.networkId();
    }

    public static void setNetworkId(@Nonnull World world, @Nonnull Vector3i position, long networkId) {
        mirrorRuntimeData(world, position, currentOrDefault(world, position).withNetworkId(networkId));
    }

    public static void clearNetworkId(@Nonnull World world, @Nonnull Vector3i position) {
        setNetworkId(world, position, ConnectableRuntimeData.NO_NETWORK);
    }

    public static void clearNetworkIds(@Nonnull World world) {
        for (Map.Entry<Vector3i, ConnectableRuntimeData> entry : ConnectableDataStore.snapshotForWorld(world).entrySet()) {
            ConnectableDataStore.put(world, entry.getKey(), entry.getValue().withNetworkId(ConnectableRuntimeData.NO_NETWORK));
        }
    }

    public static void clearMirrorForWorld(@Nonnull World world) {
        ConnectableDataStore.clearWorld(world);
    }

    public static @Nonnull Map<Vector3i, Long> networkIdSnapshotForWorld(@Nonnull World world) {
        return ConnectableDataStore.snapshotForWorld(world).entrySet().stream()
                .filter(entry -> entry.getValue().networkId() != ConnectableRuntimeData.NO_NETWORK)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().networkId()
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
                // No physical previous-instant field exists yet; mirror instant until store migration.
                data.instantState(),
                data.instantState(),
                // Existing previousState is the visible fallback, so it maps to previousEffectiveState.
                data.previousState(),
                data.effectiveState(),
                data.dirty(),
                false,
                true,
                defaultEnergyDelta(world, position),
                networkId
        );
    }

    private static @Nonnull ConnectableRuntimeData fromInverter(
            @Nonnull World world,
            @Nonnull Vector3i position,
            @Nonnull RotationTuple rotation,
            long networkId
    ) {
        InverterData data = InverterDataStore.get(world, position);
        if (data == null) {
            ConnectableRuntimeData defaultData = defaultInverterRuntimeData(rotation, networkId);
            writeInverter(world, position, defaultData);
            return defaultData;
        }
        // previousInstantState has no exact old-store field yet, so it mirrors the current output mode.
        return new ConnectableRuntimeData(
                rotation,
                // No physical previous-instant field exists yet; mirror current output mode until store migration.
                data.currentMode(),
                data.currentMode(),
                // Existing previousState is the visible fallback, so it maps to previousEffectiveState.
                data.previousState(),
                data.effectiveState(),
                data.dirty(),
                data.invertEnabled(),
                true,
                defaultEnergyDelta(world, position),
                networkId
        );
    }

    private static @Nonnull ConnectableRuntimeData defaultInverterRuntimeData(@Nonnull RotationTuple rotation, long networkId) {
        return new ConnectableRuntimeData(
                rotation,
                GravityPowderBlockDataStore.STATE_OFF,
                GravityPowderBlockDataStore.STATE_OFF,
                GravityPowderBlockDataStore.STATE_OFF,
                GravityPowderBlockDataStore.STATE_OFF,
                false,
                true,
                true,
                0,
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
        int energyDelta = defaultEnergyDelta(world, position, blockId);
        ConnectableRuntimeData data = fromSource(world, position, rotation, energyDelta, networkId);
        if (SourceBlockDataStore.get(world, position) == null) {
            SourceBlockDataStore.setActive(world, position, energyDelta > 0);
        }
        return data;
    }

    private static @Nonnull ConnectableRuntimeData fromSource(
            @Nonnull World world,
            @Nonnull Vector3i position,
            @Nonnull RotationTuple rotation,
            int energyDelta,
            long networkId
    ) {
        String state = energyDelta > 0 ? GravityPowderBlockDataStore.STATE_PUSH : GravityPowderBlockDataStore.STATE_OFF;
        // Source active state is now runtime energyDelta truth; the old source store initializes and mirrors it.
        return new ConnectableRuntimeData(
                rotation,
                state,
                state,
                state,
                state,
                false,
                false,
                false,
                energyDelta,
                networkId
        );
    }

    private static @Nonnull ConnectableRuntimeData fromOldStores(
            @Nonnull World world,
            @Nonnull Vector3i position,
            @Nonnull String blockId,
            @Nonnull RotationTuple rotation,
            long networkId
    ) {
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

    private static @Nonnull ConnectableRuntimeData dataForRotationOnlyWrite(
            @Nonnull World world,
            @Nonnull Vector3i position,
            @Nonnull RotationTuple rotation
    ) {
        InverterData inverterData = InverterDataStore.get(world, position);
        if (inverterData != null) {
            return fromInverter(world, position, rotation, ConnectableRuntimeData.NO_NETWORK);
        }
        return ConnectableRuntimeData.defaultData().withRotation(rotation);
    }

    private static boolean isGenericDefaultRuntime(@Nonnull ConnectableRuntimeData data) {
        return GravityPowderBlockDataStore.STATE_OFF.equals(data.previousInstantState())
                && GravityPowderBlockDataStore.STATE_OFF.equals(data.instantState())
                && GravityPowderBlockDataStore.STATE_OFF.equals(data.previousEffectiveState())
                && GravityPowderBlockDataStore.STATE_OFF.equals(data.effectiveState())
                && !data.dirty()
                && !data.invertEnabled()
                && !data.passing()
                && data.energyDelta() == 0;
    }

    private static boolean shouldRepairSourceRuntime(
            @Nonnull World world,
            @Nonnull Vector3i position,
            @Nullable String blockId,
            @Nonnull ConnectableRuntimeData data
    ) {
        int defaultEnergyDelta = defaultEnergyDelta(world, position, blockId);
        return data.energyDelta() != defaultEnergyDelta
                && GravityPowderBlockDataStore.STATE_OFF.equals(data.instantState())
                && GravityPowderBlockDataStore.STATE_OFF.equals(data.effectiveState());
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
                        timelineForRuntime(data),
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
        writeInverter(world, position, data, existing.nextMode(), existing.lastToggleInputMode());
    }

    private static void writeInverter(
            @Nonnull World world,
            @Nonnull Vector3i position,
            @Nonnull ConnectableRuntimeData data,
            @Nonnull String nextMode,
            @Nonnull String lastToggleInputMode
    ) {
        InverterDataStore.put(
                world,
                position,
                new InverterData(
                        data.instantState(),
                        nextMode,
                        data.invertEnabled(),
                        lastToggleInputMode,
                        timelineForRuntime(data),
                        data.dirty()
                )
        );
    }

    private static @Nonnull ConnectableRuntimeData currentOrDefault(@Nonnull World world, @Nonnull Vector3i position) {
        return get(world, position).orElse(ConnectableRuntimeData.defaultData());
    }

    private static void mirrorRuntimeData(@Nonnull World world, @Nonnull Vector3i position, @Nonnull ConnectableRuntimeData data) {
        ConnectableDataStore.put(world, position, data);
    }

    private static void syncCompatibilityStore(
            @Nonnull World world,
            @Nonnull Vector3i position,
            @Nonnull String blockId,
            @Nonnull ConnectableRuntimeData data
    ) {
        if (ConnectableRegistry.isGravityPowderCarrierId(blockId)) {
            writeGravityPowder(world, position, data);
            return;
        }
        if (ConnectableRegistry.isInverterId(blockId)) {
            writeInverter(world, position, data);
            return;
        }
        if (ConnectableBlockRoles.isSource(blockId)) {
            SourceBlockDataStore.setActive(world, position, data.energyDelta() > 0);
        }
    }

    private static @Nonnull StateTimeline timelineForRuntime(@Nonnull ConnectableRuntimeData data) {
        boolean effectiveMatchesInstant = data.effectiveState().equals(data.instantState());
        return new StateTimeline(
                data.instantState(),
                effectiveMatchesInstant ? data.instantState() : data.effectiveState(),
                effectiveMatchesInstant ? data.previousEffectiveState() : data.effectiveState()
        );
    }

    private static int defaultEnergyDelta(@Nonnull World world, @Nonnull Vector3i position) {
        BlockType blockType = world.getBlockType(position.x(), position.y(), position.z());
        if (blockType == null) {
            return 0;
        }
        return defaultEnergyDelta(world, position, blockType.getId());
    }

    private static int defaultEnergyDelta(@Nonnull World world, @Nonnull Vector3i position, @Nullable String blockId) {
        if (!ConnectableBlockRoles.isSource(blockId)) {
            return 0;
        }
        SourceBlockDataStore.SourceBlockData savedData = SourceBlockDataStore.get(world, position);
        if (savedData != null) {
            return savedData.active() ? 1 : 0;
        }
        if (ConnectableRegistry.WIND_GENERATOR_BLOCK_ID.equals(blockId)) {
            return 1;
        }
        if (ConnectableRegistry.isWoodenButtonId(blockId)) {
            return 0;
        }
        return 0;
    }
}
