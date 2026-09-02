package dev.moxinat.forcesofgravium.block.sensor;

import com.hypixel.hytale.builtin.triggervolumes.EntityTargetType;
import com.hypixel.hytale.builtin.triggervolumes.TriggerVolumesPlugin;
import com.hypixel.hytale.builtin.triggervolumes.manager.TriggerVolumeManager;
import com.hypixel.hytale.builtin.triggervolumes.manager.VolumeEntry;
import com.hypixel.hytale.builtin.triggervolumes.shape.BoxShape;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.spatial.SpatialResource;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.event.EventRegistration;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.UseBlockEvent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock;
import com.hypixel.hytale.server.core.modules.entity.EntityModule;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.universe.world.ParticleUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.moxinat.forcesofgravium.ForcesOfGraviumPlugin;
import dev.moxinat.forcesofgravium.block.siphon.GraviumSiphonLogic;
import dev.moxinat.forcesofgravium.data.NodeComponent;
import dev.moxinat.forcesofgravium.data.SensorComponent;
import dev.moxinat.forcesofgravium.energy.EnergyManager;
import dev.moxinat.forcesofgravium.registry.ConnectableRegistry;
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

    private static final double NUMBER_SPACING = 0.12;

    private static final Map<World, Set<Vector3i>> CURRENT_PENDING_COMPARE =
            new ConcurrentHashMap<>();

    private static final Map<World, Set<Vector3i>> NEXT_PENDING_COMPARE =
            new ConcurrentHashMap<>();

    private static final Map<World, Map<Vector3i, EventRegistration<?, ?>>> CONTAINER_CHANGE_LISTENERS =
            new ConcurrentHashMap<>();

    private static final Map<World, Map<Ref<EntityStore>, Vector3i>> ITEM_POSITIONS =
            new ConcurrentHashMap<>();

    private static final Map<World, Set<Vector3i>> NUMBER_UPDATE_SENSORS =
            new ConcurrentHashMap<>();

    private static final Query<EntityStore> ITEM_QUERY =
            Query.and(ItemComponent.getComponentType(), TransformComponent.getComponentType());

    private static SensorComponent getComponent(
            World world,
            Vector3i position
    ) {
        return BlockModule.getComponent(
                ForcesOfGraviumPlugin.SENSOR_COMPONENT_TYPE,
                world,
                position.x(),
                position.y(),
                position.z()
        );
    }

    private static NodeComponent nodeAt(
            World world,
            Vector3i position
    ) {
        return BlockModule.getComponent(
                ForcesOfGraviumPlugin.NODE_COMPONENT_TYPE,
                world,
                position.x(),
                position.y(),
                position.z()
        );
    }

    private static void putComponent(
            World world,
            Vector3i position,
            SensorComponent component
    ) {
        Ref<ChunkStore> blockRef =
                BlockModule.getBlockEntity(
                        world,
                        position.x(),
                        position.y(),
                        position.z()
                );

        if (blockRef == null) {
            return;
        }

        world.getChunkStore()
                .getStore()
                .putComponent(
                        blockRef,
                        ForcesOfGraviumPlugin.SENSOR_COMPONENT_TYPE,
                        component
                );
    }

    public static void handleStateChange(
            World world,
            Vector3i position
    ) {
        NodeComponent node =
                nodeAt(world, position);

        SensorComponent sensor =
                getComponent(world, position);

        if (node == null || sensor == null) {
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
                removeNumberUpdateSensor(world, position);

                SensorComponent component =
                        getComponent(world, position);

                if (component != null) {
                    SensorComponent updated = component.clone();
                    updated.clearSnapshot();

                    putComponent(
                            world,
                            position,
                            updated
                    );
                }

                unregisterTriggerVolume(world, position);
                removeContainerListener(world, position);
            }

            case PUSH -> {
                removeNumberUpdateSensor(world, position);

                registerTriggerVolume(world, position);
                refreshContainerListener(world, position);

                NEXT_PENDING_COMPARE
                        .computeIfAbsent(world, ignored -> ConcurrentHashMap.newKeySet())
                        .add(new Vector3i(position));
            }

            case PULL -> {
                NUMBER_UPDATE_SENSORS
                        .computeIfAbsent(
                                world,
                                ignored -> ConcurrentHashMap.newKeySet()
                        )
                        .add(new Vector3i(position));

                Vector3i observedPosition =
                        ConnectableNeighborResolver.adjacentPositionForLocalSide(
                                world,
                                position,
                                ConnectableRegistry.SIDE_BACK
                        );

                BlockType observedBlock =
                        world.getBlockType(
                                observedPosition.x(),
                                observedPosition.y(),
                                observedPosition.z()
                        );

                if (observedBlock != null
                        && !BlockType.EMPTY_KEY.equals(observedBlock.getId())) {
                    return;
                }
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

        if (world.getTick() % 30L == 0L) {
            updateNumbers(world);
        }
    }

    private static void updateNumbers(World world) {
        Set<Vector3i> sensors =
                NUMBER_UPDATE_SENSORS.get(world);

        if (sensors == null || sensors.isEmpty()) {
            return;
        }

        for (Vector3i sensorPosition : Set.copyOf(sensors)) {
            NodeComponent sensor =
                    nodeAt(world, sensorPosition);

            SensorComponent sensorComponent =
                    getComponent(world, sensorPosition);

            if (sensor == null
                    || sensorComponent == null
                    || sensor.effectiveState() != SignalState.PULL) {
                sensors.remove(sensorPosition);
                continue;
            }

            Vector3i observedPosition =
                    ConnectableNeighborResolver.adjacentPositionForLocalSide(
                            world,
                            sensorPosition,
                            ConnectableRegistry.SIDE_BACK
                    );

            BlockType observedBlock =
                    world.getBlockType(
                            observedPosition.x(),
                            observedPosition.y(),
                            observedPosition.z()
                    );

            if (observedBlock != null
                    && !BlockType.EMPTY_KEY.equals(observedBlock.getId())) {
                continue;
            }

            int energy =
                    EnergyManager.remainingEnergy(
                            world,
                            sensorPosition
                    );

            Vector3d center =
                    new Vector3d(
                            observedPosition.x() + 0.5,
                            observedPosition.y() + 0.5,
                            observedPosition.z() + 0.5
                    );

            showNumbers(
                    world,
                    sensorPosition,
                    center,
                    energy
            );
        }

        if (sensors.isEmpty()) {
            NUMBER_UPDATE_SENSORS.remove(world, sensors);
        }
    }

    private static void showNumbers(
            World world,
            Vector3i sensorPosition,
            Vector3d position,
            int number
    ) {
        String digits =
                Integer.toString(Math.max(0, number));

        int digitCount =
                digits.length();

        double startOffset =
                -((digitCount - 1) * NUMBER_SPACING) / 2.0;

        Vector3i observedPosition =
                blockPosition(position);

        position.add(
                (sensorPosition.x() - observedPosition.x()) * 0.25,
                (sensorPosition.y() - observedPosition.y()) * 0.25,
                (sensorPosition.z() - observedPosition.z()) * 0.25
        );

        int directionX =
                observedPosition.x() - sensorPosition.x();

        int directionY =
                observedPosition.y() - sensorPosition.y();

        int directionZ =
                observedPosition.z() - sensorPosition.z();

        float yaw = 0.0F;
        float pitch = 0.0F;
        float roll = 0.0F;

        if (directionX > 0) {
            yaw = (float) (Math.PI / 2.0);
        } else if (directionX < 0) {
            yaw = (float) (-Math.PI / 2.0);
        } else if (directionZ < 0) {
            yaw = (float) Math.PI;
        } else if (directionY > 0) {
            pitch = (float) (-Math.PI / 2.0);
        } else if (directionY < 0) {
            pitch = (float) (Math.PI / 2.0);
        }

        Vector3d numberAxis =
                new Vector3d(1.0, 0.0, 0.0)
                        .rotateY(yaw)
                        .rotateX(pitch)
                        .rotateZ(roll);

        Store<EntityStore> store =
                world.getEntityStore().getStore();

        for (int i = 0; i < digitCount; i++) {
            int digit =
                    digits.charAt(i) - '0';

            double offset =
                    startOffset + i * NUMBER_SPACING;

            Vector3d digitPosition =
                    new Vector3d(position)
                            .add(
                                    numberAxis.x * offset,
                                    numberAxis.y * offset,
                                    numberAxis.z * offset
                            );

            ParticleUtil.spawnParticleEffect(
                    "Sensor_Number_" + digit,
                    digitPosition,
                    yaw,
                    pitch,
                    roll,
                    1.5F,
                    0.0F,
                    store
            );
        }
    }

    private static void removeNumberUpdateSensor(
            World world,
            Vector3i position
    ) {
        Set<Vector3i> sensors =
                NUMBER_UPDATE_SENSORS.get(world);

        if (sensors == null) {
            return;
        }

        sensors.remove(position);

        if (sensors.isEmpty()) {
            NUMBER_UPDATE_SENSORS.remove(world, sensors);
        }
    }

    private static void handleSnapshotChanged(
            World world,
            Vector3i position
    ) {
        NodeComponent node =
                nodeAt(world, position);

        if (node == null) {
            return;
        }

        node.setInvertEnabled(
                !node.invertEnabled()
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
        removeContainerListener(
                world,
                sensorPosition
        );

        Map<Vector3i, EventRegistration<?, ?>> listeners =
                CONTAINER_CHANGE_LISTENERS.computeIfAbsent(
                        world,
                        ignored -> new ConcurrentHashMap<>()
                );

        Vector3i sensorKey =
                new Vector3i(sensorPosition);

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

    private static void removeContainerListener(
            World world,
            Vector3i sensorPosition
    ) {
        Map<Vector3i, EventRegistration<?, ?>> listeners =
                CONTAINER_CHANGE_LISTENERS.get(world);

        if (listeners == null) {
            return;
        }

        EventRegistration<?, ?> registration =
                listeners.remove(sensorPosition);

        if (registration != null) {
            registration.unregister();
        }

        if (listeners.isEmpty()) {
            CONTAINER_CHANGE_LISTENERS.remove(
                    world,
                    listeners
            );
        }
    }

    private static SensorComponent captureSnapshot(
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
                ConnectableRegistry.rawBlockId(blockStateId);

        Integer containerItemCount = null;
        ItemContainerBlock itemContainerBlock =
                GraviumSiphonLogic.itemContainerBlockAt(
                        world,
                        observedPosition
                );

        if (itemContainerBlock != null) {
            ItemContainer itemContainer =
                    itemContainerBlock.getItemContainer();

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

        Store<EntityStore> store =
                world.getEntityStore().getStore();

        int entityCount =
                volume == null
                        ? 0
                        : volume.getTrackedEntities().size();

        var itemSpatialResource =
                store.getResource(
                        EntityModule.get()
                                .getItemSpatialResourceType()
                );

        List<Ref<EntityStore>> itemRefs =
                SpatialResource.getThreadLocalReferenceList();

        itemRefs.clear();

        itemSpatialResource
                .getSpatialStructure()
                .collectBox(
                        new Vector3d(
                                observedPosition.x(),
                                observedPosition.y(),
                                observedPosition.z()
                        ),
                        new Vector3d(
                                observedPosition.x() + 1.0,
                                observedPosition.y() + 1.0,
                                observedPosition.z() + 1.0
                        ),
                        itemRefs
                );

        for (Ref<EntityStore> itemRef : itemRefs) {
            if (!itemRef.isValid()) {
                continue;
            }

            TransformComponent transform =
                    store.getComponent(
                            itemRef,
                            TransformComponent.getComponentType()
                    );

            ItemComponent item =
                    store.getComponent(
                            itemRef,
                            ItemComponent.getComponentType()
                    );

            if (transform == null
                    || item == null
                    || ItemStack.isEmpty(item.getItemStack())) {
                continue;
            }

            if (observedPosition.equals(
                    blockPosition(transform.getPosition())
            )) {
                entityCount++;
            }
        }

        NodeComponent observedNode =
                nodeAt(world, observedPosition);

        SensorComponent component =
                new SensorComponent();

        boolean hasNodeSnapshot =
                observedNode != null;

        SignalState nodeEffectiveState =
                observedNode == null
                        ? SignalState.OFF
                        : observedNode.effectiveState();

        boolean nodeInvertEnabled =
                observedNode != null
                        && observedNode.invertEnabled();

        boolean nodePassing =
                observedNode != null
                        && observedNode.passing();

        boolean hasContainerItemCount =
                containerItemCount != null;

        component.setSnapshot(
                blockId,
                blockStateId,
                false,
                hasNodeSnapshot,
                nodeEffectiveState,
                nodeInvertEnabled,
                nodePassing,
                hasContainerItemCount,
                containerItemCount == null
                        ? 0
                        : containerItemCount,
                entityCount
        );

        return component;
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
        NodeComponent sensor =
                nodeAt(world, sensorPosition);

        SensorComponent oldSnapshot =
                getComponent(
                        world,
                        sensorPosition
                );

        if (sensor == null
                || oldSnapshot == null
                || sensor.effectiveState() != SignalState.PUSH) {
            return;
        }

        SensorComponent newSnapshot =
                captureSnapshot(
                        world,
                        sensorPosition
                );

        if (!oldSnapshot.hasSnapshot()) {
            putComponent(
                    world,
                    sensorPosition,
                    newSnapshot
            );
            return;
        }

        boolean nodeChanged =
                oldSnapshot.hasNodeSnapshot()
                        != newSnapshot.hasNodeSnapshot();

        if (!nodeChanged && oldSnapshot.hasNodeSnapshot()) {
            nodeChanged =
                    oldSnapshot.nodeEffectiveState()
                            != newSnapshot.nodeEffectiveState()
                            || oldSnapshot.nodeInvertEnabled()
                            != newSnapshot.nodeInvertEnabled()
                            || oldSnapshot.nodePassing()
                            != newSnapshot.nodePassing();
        }

        boolean containerChanged =
                oldSnapshot.hasContainerItemCount()
                        != newSnapshot.hasContainerItemCount();

        if (!containerChanged
                && oldSnapshot.hasContainerItemCount()) {

            containerChanged =
                    oldSnapshot.containerItemCount()
                            != newSnapshot.containerItemCount();
        }

        boolean changed =
                !oldSnapshot.blockId()
                        .equals(newSnapshot.blockId())

                        || nodeChanged

                        || containerChanged

                        || oldSnapshot.entityCount()
                        != newSnapshot.entityCount()

                        || (oldSnapshot.blockUsed()
                        && !oldSnapshot.blockStateId()
                        .equals(newSnapshot.blockStateId()));

        if (changed) {
            handleSnapshotChanged(
                    world,
                    sensorPosition
            );
        }

        putComponent(
                world,
                sensorPosition,
                newSnapshot
        );
    }

    public static void compareSensorsObserving(
            World world,
            Vector3i observedPosition,
            boolean refreshContainerListeners
    ) {

        for (Vector3i neighbor :
                ConnectableNeighborResolver.positionsAround(observedPosition)) {

            if (neighbor.equals(observedPosition)) {
                continue;
            }

            SensorComponent possibleSensor =
                    getComponent(world, neighbor);

            if (possibleSensor == null) {
                continue;
            }

            Vector3i sensorObservedPosition =
                    ConnectableNeighborResolver.adjacentPositionForLocalSide(
                            world,
                            neighbor,
                            ConnectableRegistry.SIDE_BACK
                    );

            if (sensorObservedPosition.equals(observedPosition)) {

                if (refreshContainerListeners) {
                    refreshContainerListener(
                            world,
                            neighbor
                    );
                }

                compareSnapshot(
                        world,
                        neighbor
                );
            }
        }
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
                new Vector3d(-0.48, 0.02, -0.48),
                new Vector3d(0.48, 0.98, 0.48)
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
                        EntityTargetType.PROJECTILE
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
                                TriggerVolumesPlugin.get()
                                        .getManagerResourceType()
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
        if (GraviumSiphonLogic.itemContainerBlockAt(world, observedPosition) != null) {
            return;
        }

        for (Vector3i neighbor :
                ConnectableNeighborResolver.positionsAround(observedPosition)) {

            if (neighbor.equals(observedPosition)) {
                continue;
            }

            NodeComponent sensor =
                    nodeAt(world, neighbor);

            SensorComponent component =
                    getComponent(
                            world,
                            neighbor
                    );

            if (sensor == null || component == null) {
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

            if (!component.hasSnapshot()) {
                continue;
            }

            SensorComponent updated =
                    component.clone();

            updated.setBlockUsed(true);

            putComponent(
                    world,
                    neighbor,
                    updated
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
        removeContainerListener(world, position);
        removeNumberUpdateSensor(world, position);

        Set<Vector3i> current = CURRENT_PENDING_COMPARE.get(world);
        if (current != null) {
            current.remove(position);
        }

        Set<Vector3i> next = NEXT_PENDING_COMPARE.get(world);
        if (next != null) {
            next.remove(position);
        }

        SensorBlockRefresher.handleBroken(world, position);
    }

    private static void updatePassing(
            World world,
            Vector3i position,
            NodeComponent node
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

            node.setPassing(false);
        } else {
            node.setPassing(true);

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

    private static Vector3i blockPosition(Vector3d position) {
        return new Vector3i(
                (int) Math.floor(position.x()),
                (int) Math.floor(position.y()),
                (int) Math.floor(position.z())
        );
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

            TriggerEventType eventType =
                    context.getEventType();

            SensorLogic.compareSensorsObserving(
                    world,
                    position,
                    eventType == TriggerEventType.BLOCK_PLACED
                            || eventType == TriggerEventType.BLOCK_BROKEN
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

    public static final class ItemTrackingSystem
            extends EntityTickingSystem<EntityStore> {

        @Override
        public @Nonnull Query<EntityStore> getQuery() {
            return ITEM_QUERY;
        }

        @Override
        public boolean isParallel(
                int archetypeChunkSize,
                int taskCount
        ) {
            return false;
        }

        @Override
        public void tick(
                float dt,
                int index,
                @Nonnull ArchetypeChunk<EntityStore> chunk,
                @Nonnull Store<EntityStore> store,
                @Nonnull CommandBuffer<EntityStore> commandBuffer
        ) {
            TransformComponent transform =
                    chunk.getComponent(
                            index,
                            TransformComponent.getComponentType()
                    );

            if (transform == null) {
                return;
            }

            World world =
                    store.getExternalData().getWorld();

            Ref<EntityStore> ref =
                    chunk.getReferenceTo(index);

            Vector3i currentPosition =
                    blockPosition(transform.getPosition());

            Map<Ref<EntityStore>, Vector3i> positions =
                    ITEM_POSITIONS.computeIfAbsent(
                            world,
                            ignored -> new ConcurrentHashMap<>()
                    );

            Vector3i previousPosition =
                    positions.put(
                            ref,
                            currentPosition
                    );

            if (previousPosition == null) {
                compareSensorsObserving(
                        world,
                        currentPosition,
                        false
                );
                return;
            }

            if (previousPosition.equals(currentPosition)) {
                return;
            }

            compareSensorsObserving(
                    world,
                    previousPosition,
                    false
            );

            compareSensorsObserving(
                    world,
                    currentPosition,
                    false
            );
        }
    }

    public static final class ItemLifecycleSystem
            extends RefSystem<EntityStore> {

        @Override
        public @Nonnull Query<EntityStore> getQuery() {
            return ITEM_QUERY;
        }

        @Override
        public void onEntityAdded(
                @Nonnull Ref<EntityStore> ref,
                @Nonnull AddReason reason,
                @Nonnull Store<EntityStore> store,
                @Nonnull CommandBuffer<EntityStore> commandBuffer
        ) {
            TransformComponent transform =
                    store.getComponent(
                            ref,
                            TransformComponent.getComponentType()
                    );

            if (transform == null) {
                return;
            }

            World world =
                    store.getExternalData().getWorld();

            Vector3i position =
                    blockPosition(transform.getPosition());

            ITEM_POSITIONS
                    .computeIfAbsent(
                            world,
                            ignored -> new ConcurrentHashMap<>()
                    )
                    .put(
                            ref,
                            position
                    );

            compareSensorsObserving(
                    world,
                    position,
                    false
            );
        }

        @Override
        public void onEntityRemove(
                @Nonnull Ref<EntityStore> ref,
                @Nonnull RemoveReason reason,
                @Nonnull Store<EntityStore> store,
                @Nonnull CommandBuffer<EntityStore> commandBuffer
        ) {
            World world =
                    store.getExternalData().getWorld();

            Map<Ref<EntityStore>, Vector3i> positions =
                    ITEM_POSITIONS.get(world);

            if (positions == null) {
                return;
            }

            Vector3i position =
                    positions.remove(ref);

            if (positions.isEmpty()) {
                ITEM_POSITIONS.remove(
                        world,
                        positions
                );
            }

            if (position == null) {
                return;
            }

            if (reason == RemoveReason.UNLOAD) {
                return;
            }

            compareSensorsObserving(
                    world,
                    position,
                    false
            );
        }
    }
}
