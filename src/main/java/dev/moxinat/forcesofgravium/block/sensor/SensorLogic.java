package dev.moxinat.forcesofgravium.block.sensor;

import com.hypixel.hytale.builtin.triggervolumes.EntityTargetType;
import com.hypixel.hytale.builtin.triggervolumes.TriggerVolumesPlugin;
import com.hypixel.hytale.builtin.triggervolumes.manager.TriggerVolumeManager;
import com.hypixel.hytale.builtin.triggervolumes.manager.VolumeEntry;
import com.hypixel.hytale.builtin.triggervolumes.shape.BoxShape;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.event.EventRegistration;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.UseBlockEvent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.moxinat.forcesofgravium.block.siphon.GraviumSiphonLogic;
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

    private static final Map<World, Map<Vector3i, EventRegistration<?, ?>>> CONTAINER_CHANGE_LISTENERS =
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
                refreshContainerListener(world, position);

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

    private static void refreshContainerListener(
            World world,
            Vector3i sensorPosition
    ) {
        Map<Vector3i, EventRegistration<?, ?>> listeners =
                CONTAINER_CHANGE_LISTENERS.computeIfAbsent(
                        world,
                        ignored -> new ConcurrentHashMap<>()
                );

        Vector3i sensorKey =
                new Vector3i(sensorPosition);

        EventRegistration<?, ?> previousRegistration =
                listeners.remove(sensorKey);

        if (previousRegistration != null) {
            previousRegistration.unregister();
        }

        Vector3i observedPosition =
                ConnectableNeighborResolver.adjacentPositionForLocalSide(
                        world,
                        sensorPosition,
                        ConnectableRegistry.SIDE_BACK
                );

        ItemContainerBlock itemContainerBlock =
                GraviumSiphonLogic.itemContainerBlockAt(
                        world,
                        observedPosition
                );

        if (itemContainerBlock == null) {
            return;
        }

        ItemContainer itemContainer =
                itemContainerBlock.getItemContainer();

        if (itemContainer == null) {
            return;
        }

        EventRegistration<?, ?> registration =
                itemContainer.registerChangeEvent(
                        event -> compareSnapshot(
                                world,
                                sensorKey
                        )
                );

        listeners.put(
                sensorKey,
                registration
        );
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

        Integer containerItemCount = null;
        ItemContainerBlock itemContainerBlock =
                GraviumSiphonLogic.itemContainerBlockAt(
                        world,
                        observedPosition
                );

        if (itemContainerBlock != null) {
            ItemContainer itemContainer =
                    itemContainerBlock.getItemContainer();

            if (itemContainer != null) {
                int itemCount = 0;

                for (short slot = 0;
                     slot < itemContainer.getCapacity();
                     slot++) {
                    ItemStack stack =
                            itemContainer.getItemStack(slot);

                    if (!ItemStack.isEmpty(stack)) {
                        itemCount += stack.getQuantity();
                    }
                }

                containerItemCount = itemCount;
            }
        }

        TriggerVolumeManager manager =
                world.getEntityStore()
                        .getStore()
                        .getResource(
                                TriggerVolumesPlugin.get()
                                        .getManagerResourceType()
                        );

        VolumeEntry volume =
                manager.getVolume(
                        triggerVolumeId(sensorPosition)
                );

        int entityCount = 0;
        boolean playerPresent = false;

        if (volume != null) {
            var trackedEntities =
                    volume.getTrackedEntities();

            entityCount =
                    trackedEntities.size();

            Store<EntityStore> store =
                    world.getEntityStore().getStore();

            for (var entityRef : trackedEntities.values()) {

                if (store.getComponent(
                        entityRef,
                        Player.getComponentType()
                ) != null) {

                    playerPresent = true;
                    break;
                }
            }
        }

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
                containerItemCount,
                entityCount,
                playerPresent
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
                        || oldSnapshot.playerPresent() != newSnapshot.playerPresent()
                        || (oldSnapshot.blockUsed() && !oldSnapshot.blockStateId()
                            .equals(newSnapshot.blockStateId()));

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

        SensorBlockChangeEffect entityEnterEffect =
                new SensorBlockChangeEffect();

        entityEnterEffect.setEventType(
                TriggerEventType.ENTER
        );

        SensorBlockChangeEffect entityExitEffect =
                new SensorBlockChangeEffect();

        entityExitEffect.setEventType(
                TriggerEventType.EXIT
        );

/*
        SensorBlockUsedEffect blockUsedEffect =
                new SensorBlockUsedEffect();

        blockUsedEffect.setEventType(
                TriggerEventType.BLOCK_USED
        );

 */

        VolumeEntry volume = new VolumeEntry(
                volumeId,
                world.getName(),
                volumePosition,
                shape,
                List.of(
                        blockPlacedEffect,
                        blockBrokenEffect,
                        entityEnterEffect,
                        entityExitEffect
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

    public static void handleBlockUsed(
            World world,
            Vector3i observedPosition
    ) {
        for (Vector3i neighbor :
                ConnectableNeighborResolver.positionsAround(observedPosition)) {

            if (neighbor.equals(observedPosition)) {
                continue;
            }

            Nodes.Node sensor =
                    Nodes.get(world, neighbor);

            if (sensor == null
                    || !NodeTypes.GRAVIUM_SENSOR.blockId()
                    .equals(sensor.blockId())) {
                continue;
            }

            Vector3i sensorObservedPosition =
                    ConnectableNeighborResolver.adjacentPositionForLocalSide(
                            world,
                            neighbor,
                            ConnectableRegistry.SIDE_BACK
                    );

            if (!sensorObservedPosition.equals(observedPosition)) {
                continue;
            }

            SensorSnapshots.SensorSnapshot snapshot =
                    SensorSnapshots.get(world, neighbor);

            if (snapshot == null) {
                continue;
            }

            SensorSnapshots.put(
                    world,
                    neighbor,
                    new SensorSnapshots.SensorSnapshot(
                            snapshot.blockId(),
                            snapshot.blockStateId(),
                            true,
                            snapshot.node(),
                            snapshot.containerItemCount(),
                            snapshot.entityCount(),
                            snapshot.playerPresent()
                    )
            );

            if (sensor.effectiveState() == SignalState.PUSH) {
                compareSnapshot(
                        world,
                        neighbor
                );
            }
        }
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

    public static final class SensorBlockUsedEffect
            extends TriggerEffect {

        public static final BuilderCodec<SensorBlockUsedEffect> CODEC =
                BuilderCodec.builder(
                        SensorBlockUsedEffect.class,
                        SensorBlockUsedEffect::new,
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

            Vector3i observedPosition =
                    new Vector3i(
                            (int) Math.floor(volumePosition.x()),
                            (int) Math.floor(volumePosition.y()),
                            (int) Math.floor(volumePosition.z())
                    );

            SensorLogic.handleBlockUsed(
                    world,
                    observedPosition
            );
        }
    }

    public static void registerTriggerEffects() {
        TriggerVolumesPlugin.get().registerEffectType(
                "FoGSensorBlockChange",
                SensorBlockChangeEffect.class,
                SensorBlockChangeEffect.CODEC
        );

        TriggerVolumesPlugin.get().registerEffectType(
                "FoGSensorBlockUsed",
                SensorBlockUsedEffect.class,
                SensorBlockUsedEffect.CODEC
        );
    }

    //temp until trigger volumes can handle block use in update 6
    public static final class BlockUseSystem
            extends EntityEventSystem<EntityStore, UseBlockEvent.Post> {

        public BlockUseSystem() {
            super(UseBlockEvent.Post.class);
        }

        @Override
        public @Nonnull Query<EntityStore> getQuery() {
            return Player.getComponentType();
        }

        @Override
        public void handle(
                int index,
                @Nonnull ArchetypeChunk<EntityStore> chunk,
                @Nonnull Store<EntityStore> store,
                @Nonnull CommandBuffer<EntityStore> commandBuffer,
                @Nonnull UseBlockEvent.Post event
        ) {
            World world =
                    store.getExternalData().getWorld();

            Vector3i observedPosition =
                    new Vector3i(event.getTargetBlock());

            handleBlockUsed(
                    world,
                    observedPosition
            );
        }
    }
}
