package dev.moxinat.forcesofgravium.lifecycle;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.component.system.RefChangeSystem;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.modules.interaction.InteractionModule;
import com.hypixel.hytale.server.core.modules.interaction.components.PlacedByInteractionComponent;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import dev.moxinat.forcesofgravium.ForcesOfGraviumPlugin;
import dev.moxinat.forcesofgravium.block.sensor.SensorLogic;
import dev.moxinat.forcesofgravium.data.NodeComponent;
import dev.moxinat.forcesofgravium.data.SensorComponent;
import dev.moxinat.forcesofgravium.registry.ConnectableRegistry;
import dev.moxinat.forcesofgravium.signal.SignalState;
import dev.moxinat.forcesofgravium.dispatcher.ConnectableVisualDispatcher;
import dev.moxinat.forcesofgravium.energy.EnergyManager;
import dev.moxinat.forcesofgravium.network.ConnectableNetworkManager;
import dev.moxinat.forcesofgravium.signal.ConnectableSignalRecalculator;
import dev.moxinat.forcesofgravium.spatial.BlockPlacementRotationSystem;
import dev.moxinat.forcesofgravium.spatial.ConnectableNeighborResolver;
import org.joml.Vector3i;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.PlaceBlockEvent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.moxinat.forcesofgravium.signal.ConnectablePropagationScheduler;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class ConnectableBlockLifecycleSystem {

    private ConnectableBlockLifecycleSystem() {
    }

    private static final Map<World, Map<Vector3i, Long>> PENDING_BREAKS =
            new ConcurrentHashMap<>();

    public static final class PlaceSystem extends EntityEventSystem<EntityStore, PlaceBlockEvent> {

        public PlaceSystem() {
            super(PlaceBlockEvent.class);
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
                @Nonnull PlaceBlockEvent event
        ) {
            ItemStack itemInHand = event.getItemInHand();

            if (itemInHand == null) {
                return;
            }

            String blockId = itemInHand.getItemId();

            if (!ConnectableRegistry.isConnectableId(blockId)) {
                return;
            }

            Ref<EntityStore> entityRef =
                    chunk.getReferenceTo(index);

            Player player =
                    store.getComponent(
                            entityRef,
                            Player.getComponentType()
                    );

            if (player == null || player.getWorld() == null) {
                return;
            }

            RotationTuple rotation =
                    event.getRotation();

            HeadRotation headRotation =
                    store.getComponent(
                            entityRef,
                            HeadRotation.getComponentType()
                    );

            if (headRotation != null) {
                rotation =
                        BlockPlacementRotationSystem.resolveRotation(
                                blockId,
                                rotation,
                                headRotation
                        );
            }

            event.setRotation(rotation);
        }
    }

    public static final class PlacedSystem
            extends RefChangeSystem<ChunkStore, PlacedByInteractionComponent> {

        @Override
        public @Nonnull ComponentType<ChunkStore, PlacedByInteractionComponent> componentType() {
            return InteractionModule.get().getPlacedByComponentType();
        }

        @Override
        public @Nonnull Query<ChunkStore> getQuery() {
            return Query.and(
                    InteractionModule.get().getPlacedByComponentType(),
                    ForcesOfGraviumPlugin.NODE_COMPONENT_TYPE,
                    BlockModule.BlockStateInfo.getComponentType()
            );
        }

        @Override
        public void onComponentSet(
                @Nonnull Ref<ChunkStore> ref,
                @Nullable PlacedByInteractionComponent oldComponent,
                @Nonnull PlacedByInteractionComponent newComponent,
                @Nonnull Store<ChunkStore> store,
                @Nonnull CommandBuffer<ChunkStore> commandBuffer
        ) {
        }

        @Override
        public void onComponentRemoved(
                @Nonnull Ref<ChunkStore> ref,
                @Nonnull PlacedByInteractionComponent component,
                @Nonnull Store<ChunkStore> store,
                @Nonnull CommandBuffer<ChunkStore> commandBuffer
        ) {
        }

        @Override
        public void onComponentAdded(
                @Nonnull Ref<ChunkStore> ref,
                @Nonnull PlacedByInteractionComponent component,
                @Nonnull Store<ChunkStore> store,
                @Nonnull CommandBuffer<ChunkStore> commandBuffer
        ) {
            BlockModule.BlockStateInfo blockStateInfo =
                    store.getComponent(
                            ref,
                            BlockModule.BlockStateInfo.getComponentType()
                    );

            if (blockStateInfo == null) {
                return;
            }

            Vector3i target = new Vector3i();

            if (!blockStateInfo.fillWorldPos(
                    store,
                    target
            )) {
                return;
            }

            World world =
                    store.getExternalData().getWorld();

            // -------------------------
            // NETWORK
            // -------------------------

            ConnectableNetworkManager.onNodePlaced(
                    world,
                    target
            );

            EnergyManager.checkNetwork(
                    world,
                    target
            );

            // -------------------------
            // SIGNAL
            // -------------------------

            ConnectableSignalRecalculator.recompute(
                    world,
                    target
            );

            // -------------------------
            // EFFECTIVE ADOPTION
            // -------------------------

            boolean hasStableBackwardNeighbor = false;

            for (Vector3i neighborPosition :
                    ConnectableNeighborResolver.allBackwardSignalNeighbors(
                            world,
                            target
                    )) {

                NodeComponent neighbor =
                        nodeAt(
                                world,
                                neighborPosition
                        );

                if (neighbor != null && !neighbor.dirty()) {
                    hasStableBackwardNeighbor = true;
                    break;
                }
            }

            if (hasStableBackwardNeighbor) {
                ConnectablePropagationScheduler.scheduleAdoption(
                        world,
                        target
                );
            }

            // -------------------------
            // VISUAL
            // -------------------------

            ConnectableVisualDispatcher.refreshTopologyAround(
                    world,
                    target
            );
        }
    }

    public static final class BreakSystem
            extends EntityEventSystem<EntityStore, BreakBlockEvent> {

        public BreakSystem() {
            super(BreakBlockEvent.class);
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
                @Nonnull BreakBlockEvent event
        ) {
            if (event.isCancelled()) {
                return;
            }

            Ref<EntityStore> entityRef =
                    chunk.getReferenceTo(index);

            Player player =
                    store.getComponent(
                            entityRef,
                            Player.getComponentType()
                    );

            if (player == null || player.getWorld() == null) {
                return;
            }

            World world =
                    player.getWorld();

            Vector3i target =
                    new Vector3i(
                            event.getTargetBlock()
                    );

            NodeComponent node =
                    nodeAt(
                            world,
                            target
                    );

            if (node == null) {
                return;
            }

            long currentTick =
                    world.getTick();

            Map<Vector3i, Long> pending =
                    PENDING_BREAKS.computeIfAbsent(
                            world,
                            ignored -> new ConcurrentHashMap<>()
                    );

            // Remove stale break intents.
            pending.entrySet().removeIf(
                    entry -> entry.getValue() < currentTick
            );

            pending.put(
                    target,
                    currentTick
            );
        }
    }

    public static final class BrokenSystem
            extends RefSystem<ChunkStore> {

        @Override
        public @Nonnull Query<ChunkStore> getQuery() {
            return Query.and(
                    ForcesOfGraviumPlugin.NODE_COMPONENT_TYPE,
                    BlockModule.BlockStateInfo.getComponentType()
            );
        }

        @Override
        public void onEntityAdded(
                @Nonnull Ref<ChunkStore> ref,
                @Nonnull AddReason reason,
                @Nonnull Store<ChunkStore> store,
                @Nonnull CommandBuffer<ChunkStore> commandBuffer
        ) {
        }

        @Override
        public void onEntityRemove(
                @Nonnull Ref<ChunkStore> ref,
                @Nonnull RemoveReason reason,
                @Nonnull Store<ChunkStore> store,
                @Nonnull CommandBuffer<ChunkStore> commandBuffer
        ) {

            if (reason != RemoveReason.REMOVE
                    && reason != RemoveReason.BUILDER_TOOLS_UNDO) {
                return;
            }

            BlockModule.BlockStateInfo blockStateInfo =
                    store.getComponent(
                            ref,
                            BlockModule.BlockStateInfo.getComponentType()
                    );

            NodeComponent brokenNode =
                    store.getComponent(
                            ref,
                            ForcesOfGraviumPlugin.NODE_COMPONENT_TYPE
                    );

            if (blockStateInfo == null || brokenNode == null) {
                return;
            }

            Vector3i target = new Vector3i();

            if (!blockStateInfo.fillWorldPos(
                    store,
                    target
            )) {
                return;
            }

            World world =
                    store.getExternalData().getWorld();

            if (!consumePendingBreak(
                    world,
                    target
            )) {
                return;
            }

            SensorComponent sensor =
                    store.getComponent(
                            ref,
                            ForcesOfGraviumPlugin.SENSOR_COMPONENT_TYPE
                    );

            if (sensor != null) {
                SensorLogic.handleBroken(
                        world,
                        target
                );
            }

            // -------------------------
            // SNAPSHOT BEFORE REMOVAL
            // -------------------------

            long oldNetworkId =
                    brokenNode.networkId();

            Set<Vector3i> formerForwardNeighbors =
                    ConnectableNeighborResolver.allForwardSignalNeighbors(
                            world,
                            target
                    );

            Set<Vector3i> formerNetworkNeighbors =
                    ConnectableNeighborResolver.allNetworkNeighbors(
                            world,
                            target
                    );

            Map<Vector3i, SignalState> oldForwardInstantStates =
                    new LinkedHashMap<>();

            for (Vector3i forwardNeighbor : formerForwardNeighbors) {

                NodeComponent neighbor =
                        nodeAt(
                                world,
                                forwardNeighbor
                        );

                if (neighbor != null) {
                    oldForwardInstantStates.put(
                            new Vector3i(forwardNeighbor),
                            neighbor.instantState()
                    );
                }
            }

            // Everything below runs after the block entity was removed.
            commandBuffer.run(ignored -> {

                // -------------------------
                // NETWORK
                // -------------------------

                ConnectableNetworkManager.onNodeBroken(
                        world,
                        oldNetworkId,
                        formerNetworkNeighbors
                );

                for (Vector3i neighbor : formerNetworkNeighbors) {
                    EnergyManager.checkNetwork(
                            world,
                            neighbor
                    );
                }

                // -------------------------
                // SIGNAL
                // -------------------------

                for (Map.Entry<Vector3i, SignalState> entry :
                        oldForwardInstantStates.entrySet()) {

                    Vector3i position =
                            entry.getKey();

                    SignalState oldInstantState =
                            entry.getValue();

                    ConnectableSignalRecalculator.recompute(
                            world,
                            position
                    );

                    NodeComponent recomputedNode =
                            nodeAt(
                                    world,
                                    position
                            );

                    if (recomputedNode == null) {
                        continue;
                    }

                    if (recomputedNode.instantState()
                            != oldInstantState) {

                        ConnectablePropagationScheduler.scheduleAdoption(
                                world,
                                position
                        );
                    }
                }

                ConnectableVisualDispatcher.refreshTopologyAround(
                        world,
                        target
                );
            });
        }
    }

    private static boolean consumePendingBreak(
            @Nonnull World world,
            @Nonnull Vector3i position
    ) {
        Map<Vector3i, Long> pending =
                PENDING_BREAKS.get(world);

        if (pending == null) {
            return false;
        }

        Long breakTick =
                pending.remove(position);

        if (pending.isEmpty()) {
            PENDING_BREAKS.remove(world, pending);
        }

        return breakTick != null
                && breakTick == world.getTick();
    }

    private static NodeComponent nodeAt(
            @Nonnull World world,
            @Nonnull Vector3i position
    ) {
        return BlockModule.getComponent(
                ForcesOfGraviumPlugin.NODE_COMPONENT_TYPE,
                world,
                position.x(),
                position.y(),
                position.z()
        );
    }
}
