package dev.moxinat.forcesofgravium.block.sensor;

import com.hypixel.hytale.builtin.triggervolumes.EntityTargetType;
import com.hypixel.hytale.builtin.triggervolumes.TriggerVolumesPlugin;
import com.hypixel.hytale.builtin.triggervolumes.manager.TriggerVolumeManager;
import com.hypixel.hytale.builtin.triggervolumes.manager.VolumeEntry;
import com.hypixel.hytale.builtin.triggervolumes.shape.BoxShape;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import dev.moxinat.forcesofgravium.data.Nodes;
import dev.moxinat.forcesofgravium.data.SensorSnapshots;
import dev.moxinat.forcesofgravium.energy.EnergyManager;
import dev.moxinat.forcesofgravium.registry.ConnectableRegistry;
import dev.moxinat.forcesofgravium.registry.NodeTypes;
import dev.moxinat.forcesofgravium.signal.ConnectablePropagationScheduler;
import dev.moxinat.forcesofgravium.signal.ConnectableSignalRecalculator;
import dev.moxinat.forcesofgravium.signal.SignalState;
import dev.moxinat.forcesofgravium.spatial.ConnectableNeighborResolver;
import org.joml.Vector3d;
import org.joml.Vector3i;
import com.hypixel.hytale.builtin.triggervolumes.effect.TriggerContext;
import com.hypixel.hytale.builtin.triggervolumes.effect.TriggerEffect;
import com.hypixel.hytale.builtin.triggervolumes.effect.TriggerEventType;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import javax.annotation.Nonnull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class SensorLogic {

    private SensorLogic() {
    }

    private static final Map<World, Set<Vector3i>> CURRENT_PENDING_COMPARE =
            new ConcurrentHashMap<>();

    private static final Map<World, Set<Vector3i>> NEXT_PENDING_COMPARE =
            new ConcurrentHashMap<>();

    public static void handleStateChange(
            World world,
            Vector3i position
    ) {
        Nodes.Node node = Nodes.get(world, position);

        if (node == null
                || !NodeTypes.GRAVIUM_SENSOR.blockId().equals(node.blockId())) {
            return;
        }

        handleObservationState(
                world,
                position,
                node.effectiveState()
        );

        updatePassing(
                world,
                position,
                node
        );

    }

    private static void handleObservationState(
            World world,
            Vector3i position,
            SignalState state
    ) {
        switch (state) {
            case OFF -> {
                SensorSnapshots.remove(world, position);
                unregisterTriggerVolume(world, position);
            }

            case PUSH -> {

                registerTriggerVolume(world, position);

                NEXT_PENDING_COMPARE
                        .computeIfAbsent(world, ignored -> ConcurrentHashMap.newKeySet())
                        .add(new Vector3i(position));
            }

            case PULL -> {
            }
        }
    }

    public static void tickWorld(World world) {

        Set<Vector3i> current =
                CURRENT_PENDING_COMPARE.remove(world);

        if (current != null) {

            for (Vector3i position : current) {
                compareSnapshotNow(
                        world,
                        position
                );
            }
        }

        Set<Vector3i> next =
                NEXT_PENDING_COMPARE.remove(world);

        if (next != null && !next.isEmpty()) {

            CURRENT_PENDING_COMPARE.put(
                    world,
                    next
            );
        }
    }

    private static void handleSnapshotChanged(
            World world,
            Vector3i position
    ) {
        Nodes.Node node = Nodes.get(world, position);

        if (node == null) {
            return;
        }

        Nodes.put(
                world,
                node.withInvertEnabled(!node.invertEnabled())
        );

        for (Vector3i forwardNeighbor :
                ConnectableNeighborResolver.allForwardSignalNeighbors(
                        world,
                        position
                )) {

            ConnectableSignalRecalculator.recompute(
                    world,
                    forwardNeighbor
            );

            ConnectablePropagationScheduler.scheduleAdoption(
                    world,
                    forwardNeighbor
            );
        }
    }

    private static SensorSnapshots.SensorSnapshot captureSnapshot(
            World world,
            Vector3i sensorPosition
    ) {
        Vector3i observedPosition =
                ConnectableNeighborResolver.adjacentPositionForLocalSide(
                        world,
                        sensorPosition,
                        ConnectableRegistry.SIDE_BACK
                );

        BlockType blockType =
                world.getBlockType(
                        observedPosition.x(),
                        observedPosition.y(),
                        observedPosition.z()
                );

        String blockStateId =
                blockType == null
                        ? ""
                        : blockType.getId();

        String blockId =
                rawBlockId(blockStateId);



        Nodes.Node observedNode =
                Nodes.get(world, observedPosition);

        SensorSnapshots.NodeSnapshot nodeSnapshot =
                observedNode == null
                        ? null
                        : new SensorSnapshots.NodeSnapshot(
                        observedNode.effectiveState(),
                        observedNode.invertEnabled(),
                        observedNode.passing(),
                        observedNode.energyDelta()
                );

        return new SensorSnapshots.SensorSnapshot(
                blockId,
                blockStateId,
                false,
                nodeSnapshot,
                null,        // containerItemCount später
                0,           // entityCount später
                false        // playerPresent später
        );
    }

    public static void compareSnapshot(
            World world,
            Vector3i sensorPosition
    ) {
        NEXT_PENDING_COMPARE
                .computeIfAbsent(
                        world,
                        ignored -> ConcurrentHashMap.newKeySet()
                )
                .add(new Vector3i(sensorPosition));
    }

    public static void compareSnapshotNow(
            World world,
            Vector3i sensorPosition
    ) {
        Nodes.Node sensor = Nodes.get(world, sensorPosition);

        if (sensor == null
                || !NodeTypes.GRAVIUM_SENSOR.blockId().equals(sensor.blockId())
                || sensor.effectiveState() != SignalState.PUSH) {
            return;
        }

        SensorSnapshots.SensorSnapshot oldSnapshot =
                SensorSnapshots.get(world, sensorPosition);

        SensorSnapshots.SensorSnapshot newSnapshot =
                captureSnapshot(world, sensorPosition);

        if (oldSnapshot == null) {
            SensorSnapshots.put(
                    world,
                    sensorPosition,
                    newSnapshot
            );
            return;
        }

        boolean changed =
                !oldSnapshot.blockId().equals(newSnapshot.blockId())
                        || !Objects.equals(
                        oldSnapshot.node(),
                        newSnapshot.node()
                )
                        || !Objects.equals(
                        oldSnapshot.containerItemCount(),
                        newSnapshot.containerItemCount()
                )
                        || oldSnapshot.entityCount() != newSnapshot.entityCount()
                        || oldSnapshot.playerPresent() != newSnapshot.playerPresent();

        // später zusätzlich:
        //
        // || (
        //      oldSnapshot.blockUsed()
        //      && !oldSnapshot.blockStateId()
        //              .equals(newSnapshot.blockStateId())
        // )

        if (changed) {
            handleSnapshotChanged(
                    world,
                    sensorPosition
            );
        }

        SensorSnapshots.put(
                world,
                sensorPosition,
                newSnapshot
        );
    }

    public static void compareSensorsObserving(
            World world,
            Vector3i observedPosition
    ) {

        for (Vector3i neighbor :
                ConnectableNeighborResolver.positionsAround(observedPosition)) {

            if (neighbor.equals(observedPosition)) {
                continue;
            }

            Nodes.Node possibleSensor =
                    Nodes.get(world, neighbor);

            if (possibleSensor == null
                    || !NodeTypes.GRAVIUM_SENSOR.blockId()
                    .equals(possibleSensor.blockId())) {
                continue;
            }

            Vector3i sensorObservedPosition =
                    ConnectableNeighborResolver.adjacentPositionForLocalSide(
                            world,
                            neighbor,
                            ConnectableRegistry.SIDE_BACK
                    );

            if (sensorObservedPosition.equals(observedPosition)) {

                compareSnapshot(
                        world,
                        neighbor
                );
            }
        }
    }

    private static String rawBlockId(String blockId) {
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

    private static void registerTriggerVolume(
            World world,
            Vector3i sensorPosition
    ) {
        TriggerVolumeManager manager =
                world.getEntityStore()
                        .getStore()
                        .getResource(
                                TriggerVolumesPlugin.get().getManagerResourceType()
                        );

        Vector3i observedPosition =
                ConnectableNeighborResolver.adjacentPositionForLocalSide(
                        world,
                        sensorPosition,
                        ConnectableRegistry.SIDE_BACK
                );

        String volumeId = triggerVolumeId(sensorPosition);

        Vector3d volumePosition = new Vector3d(
                observedPosition.x() + 0.5,
                observedPosition.y(),
                observedPosition.z() + 0.5
        );

        BoxShape shape = new BoxShape(
                new Vector3d(-0.5, 0.0, -0.5),
                new Vector3d(0.5, 1.0, 0.5)
        );

        SensorBlockChangeEffect blockPlacedEffect =
                new SensorBlockChangeEffect();

        blockPlacedEffect.setEventType(
                TriggerEventType.BLOCK_PLACED
        );

        SensorBlockChangeEffect blockBrokenEffect =
                new SensorBlockChangeEffect();

        blockBrokenEffect.setEventType(
                TriggerEventType.BLOCK_BROKEN
        );

        VolumeEntry volume = new VolumeEntry(
                volumeId,
                world.getName(),
                volumePosition,
                shape,
                List.of(
                        blockPlacedEffect,
                        blockBrokenEffect
                ),
                EnumSet.of(
                        EntityTargetType.PLAYER,
                        EntityTargetType.NPC,
                        EntityTargetType.PROJECTILE,
                        EntityTargetType.ITEM_DROP
                ),
                true
        );

        manager.register(
                volumeId,
                volume
        );
    }

    private static void unregisterTriggerVolume(
            World world,
            Vector3i sensorPosition
    ) {
        TriggerVolumeManager manager =
                world.getEntityStore()
                        .getStore()
                        .getResource(
                                TriggerVolumesPlugin.get().getManagerResourceType()
                        );

        if (manager.hasVolume(triggerVolumeId(sensorPosition))) {
            manager.unregister(triggerVolumeId(sensorPosition));
        }
    }

    private static String triggerVolumeId(Vector3i position) {
        return "fog_sensor_"
                + encodeCoordinate(position.x()) + "_"
                + encodeCoordinate(position.y()) + "_"
                + encodeCoordinate(position.z());
    }

    private static String encodeCoordinate(int value) {
        long number = Math.abs((long) value);

        StringBuilder encoded = new StringBuilder();

        do {
            encoded.append((char) ('a' + (number % 26)));
            number /= 26;
        } while (number > 0);

        encoded.reverse();

        return (value < 0 ? "n" : "p") + encoded;
    }

    public static void handleBroken(
            World world,
            Vector3i position
    ) {
        unregisterTriggerVolume(world, position);

        SensorSnapshots.remove(
                world,
                position
        );

        Set<Vector3i> current = CURRENT_PENDING_COMPARE.get(world);
        if (current != null) {
            current.remove(position);
        }

        Set<Vector3i> next = NEXT_PENDING_COMPARE.get(world);
        if (next != null) {
            next.remove(position);
        }
    }

    private static void updatePassing(
            World world,
            Vector3i position,
            Nodes.Node node
    ) {
        boolean shouldPass =
                node.effectiveState() != SignalState.PULL;

        if (node.passing() == shouldPass) {
            return;
        }

        Set<Vector3i> forwardNeighbors;

        if (!shouldPass) {
            forwardNeighbors =
                    ConnectableNeighborResolver.allForwardSignalNeighbors(
                            world,
                            position
                    );

            Nodes.put(
                    world,
                    node.withPassing(false)
            );
        } else {
            Nodes.put(
                    world,
                    node.withPassing(true)
            );

            forwardNeighbors =
                    ConnectableNeighborResolver.allForwardSignalNeighbors(
                            world,
                            position
                    );
        }

        for (Vector3i forwardNeighbor : forwardNeighbors) {
            ConnectableSignalRecalculator.recompute(
                    world,
                    forwardNeighbor
            );

            EnergyManager.checkNetwork(
                    world,
                    forwardNeighbor
            );

            ConnectablePropagationScheduler.scheduleAdoption(
                    world,
                    forwardNeighbor
            );
        }
    }

    public static final class SensorBlockChangeEffect
            extends TriggerEffect {

        public static final BuilderCodec<SensorBlockChangeEffect> CODEC =
                BuilderCodec.builder(
                        SensorBlockChangeEffect.class,
                        SensorBlockChangeEffect::new,
                        TriggerEffect.BASE_CODEC
                ).build();

        @Override
        public void execute(
                @Nonnull TriggerContext context
        ) {

            World world =
                    context.getStore()
                            .getExternalData()
                            .getWorld();

            Vector3d volumePosition =
                    context.getVolume().getPosition();

            Vector3i position =
                    new Vector3i(
                            (int) Math.floor(volumePosition.x()),
                            (int) Math.floor(volumePosition.y()),
                            (int) Math.floor(volumePosition.z())
                    );

            SensorLogic.compareSensorsObserving(
                    world,
                    position
            );
        }
    }

    public static void registerTriggerEffects() {
        TriggerVolumesPlugin.get().registerEffectType(
                "FoGSensorBlockChange",
                SensorBlockChangeEffect.class,
                SensorBlockChangeEffect.CODEC
        );
    }
}