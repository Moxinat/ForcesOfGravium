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
        RotationTuple rotation = getRotation(world, position);
        ConnectableRuntimeData mirror = ConnectableDataStore.get(world, position);
        long networkId = mirror == null ? ConnectableRuntimeData.NO_NETWORK : mirror.networkId();

        ConnectableRuntimeData data = fromOldStores(world, position, blockId, rotation, networkId);
        data = withMirrorRuntimeOnlyFields(data, mirror);
        ConnectableDataStore.put(world, position, data);
        return data;
    }

    public static void put(@Nonnull World world, @Nonnull Vector3i position, @Nonnull ConnectableRuntimeData data) {
        BlockType blockType = world.getBlockType(position.x(), position.y(), position.z());
        if (blockType == null || ConnectableRegistry.isNotConnectable(blockType.getId())) {
            return;
        }

        ConnectableDataStore.put(world, position, data);
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
        // Delegates to the existing wave adoption behavior; no dirty-versioning or timing semantics change here.
        if (ConnectableRegistry.isGravityPowderCarrierId(blockType.getId())) {
            GravityPowderBlockDataStore.adoptInstantState(world, position);
            mirrorFromOldStores(world, position, blockType.getId());
            return;
        }
        if (ConnectableRegistry.isInverterId(blockType.getId())) {
            InverterDataStore.adoptCurrentMode(world, position);
            mirrorFromOldStores(world, position, blockType.getId());
        }
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
                .orElseGet(() -> ConnectableRuntimeData.defaultData().withRotation(rotation));
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
        // Delegates to the old physical stores. The unified runtime layer does not own timeline data yet.
        if (ConnectableRegistry.isGravityPowderCarrierId(blockType.getId())) {
            GravityPowderBlockDataStore.setInstantState(world, position, instantState);
            mirrorFromOldStores(world, position, blockType.getId());
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
            mirrorFromOldStores(world, position, blockType.getId());
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
            mirrorFromOldStores(world, position, blockType.getId());
            return;
        }
        if (ConnectableRegistry.isInverterId(blockType.getId())) {
            if (dirty) {
                InverterDataStore.markWaveDirty(world, position);
            } else {
                InverterDataStore.clearWaveDirty(world, position);
            }
            mirrorFromOldStores(world, position, blockType.getId());
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
        mirrorFromOldStores(world, position, ConnectableRegistry.INVERTER_BLOCK_ID);
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

    public static void setEnergyDelta(@Nonnull World world, @Nonnull Vector3i position, int energyDelta) {
        // Runtime-only placeholder. This must not activate power mechanics or source behavior yet.
        mirrorRuntimeData(world, position, currentOrDefault(world, position).withEnergyDelta(energyDelta));
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
        InverterData data = InverterDataStore.getOrCreate(world, position);
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
                false,
                defaultEnergyDelta(world, position),
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

    private static @Nonnull ConnectableRuntimeData currentOrDefault(@Nonnull World world, @Nonnull Vector3i position) {
        return get(world, position).orElse(ConnectableRuntimeData.defaultData());
    }

    private static void mirrorRuntimeData(@Nonnull World world, @Nonnull Vector3i position, @Nonnull ConnectableRuntimeData data) {
        ConnectableDataStore.put(world, position, data);
    }

    private static void mirrorFromOldStores(@Nonnull World world, @Nonnull Vector3i position, @Nonnull String blockId) {
        get(world, position, blockId);
    }

    private static @Nonnull ConnectableRuntimeData withMirrorRuntimeOnlyFields(
            @Nonnull ConnectableRuntimeData oldStoreData,
            @Nullable ConnectableRuntimeData mirror
    ) {
        if (mirror == null) {
            return oldStoreData;
        }
        return oldStoreData
                .withPassing(mirror.passing())
                .withEnergyDelta(mirror.energyDelta())
                .withNetworkId(mirror.networkId());
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
