package dev.moxinat.forcesofgravium.system;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import dev.moxinat.forcesofgravium.connectable.SignalState;
import dev.moxinat.forcesofgravium.connectable.dispatcher.ConnectableVisualDispatcher;
import dev.moxinat.forcesofgravium.connectable.energy.EnergyManager;
import dev.moxinat.forcesofgravium.connectable.network.ConnectableNetworkManager;
import dev.moxinat.forcesofgravium.connectable.propagation.ConnectableSignalRecalculator;
import dev.moxinat.forcesofgravium.connectable.registry.NodeTypes;
import dev.moxinat.forcesofgravium.connectable.spatial.ConnectableNeighborResolver;
import dev.moxinat.forcesofgravium.data.Nodes;
import org.joml.Vector3i;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.event.events.ecs.PlaceBlockEvent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.moxinat.forcesofgravium.connectable.propagation.ConnectablePropagationScheduler;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;

import javax.annotation.Nonnull;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class ConnectableBlockLifecycleSystem {

    private ConnectableBlockLifecycleSystem() {
    }

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

            NodeTypes.NodeType nodeType = NodeTypes.find(blockId)
                    .orElse(null);

            if (nodeType == null) {
                return;
            }

            Ref<EntityStore> entityRef = chunk.getReferenceTo(index);

            Player player = store.getComponent(
                    entityRef,
                    Player.getComponentType()
            );

            if (player == null || player.getWorld() == null) {
                return;
            }

            World world = player.getWorld();
            Vector3i target = new Vector3i(event.getTargetBlock());

            System.out.println("PLACE EVENT: " + event.getTargetBlock());

            // -------------------------
            // ROTATION
            // -------------------------

            RotationTuple rotation = event.getRotation();

            HeadRotation headRotation = store.getComponent(
                    entityRef,
                    HeadRotation.getComponentType()
            );

            if (headRotation != null) {
                rotation = BlockPlacementRotationSystem.resolveRotation(
                        blockId,
                        rotation,
                        headRotation
                );
            }

            // Hytale block and Node must use the same final rotation.
            event.setRotation(rotation);

            // -------------------------
            // CREATE NODE
            // -------------------------

            Nodes.Node node = Nodes.createWithType(
                    world,
                    target,
                    nodeType
            );

            Nodes.put(
                    world,
                    node.withRotation(rotation)
            );

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

                Nodes.Node neighbor = Nodes.get(
                        world,
                        neighborPosition
                );

                if (neighbor != null && !neighbor.dirty()) {
                    hasStableBackwardNeighbor = true;
                    break;
                }
            }

            if (hasStableBackwardNeighbor) {
                ConnectablePropagationScheduler
                        .scheduleAdoption(
                                world,
                                target
                        );
            }

            ConnectableVisualDispatcher.refreshTopologyAround(world, target);
        }
    }

    public static final class BreakSystem extends EntityEventSystem<EntityStore, BreakBlockEvent> {

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

            Ref<EntityStore> entityRef = chunk.getReferenceTo(index);

            Player player = store.getComponent(
                    entityRef,
                    Player.getComponentType()
            );

            if (player == null || player.getWorld() == null) {
                return;
            }

            World world = player.getWorld();
            Vector3i target = new Vector3i(event.getTargetBlock());

            Nodes.Node brokenNode = Nodes.get(world, target);

            if (brokenNode == null) {
                return;
            }

            System.out.println("BREAK EVENT: " + event.getTargetBlock());

            // -------------------------
            // SNAPSHOT BEFORE REMOVAL
            // -------------------------

            long oldNetworkId = brokenNode.networkId();

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

            // Snapshot ALL old instant states before the first recompute.
            Map<Vector3i, SignalState> oldForwardInstantStates =
                    new LinkedHashMap<>();

            for (Vector3i forwardNeighbor : formerForwardNeighbors) {
                Nodes.Node neighbor = Nodes.get(
                        world,
                        forwardNeighbor
                );

                if (neighbor != null) {
                    oldForwardInstantStates.put(
                            forwardNeighbor,
                            neighbor.instantState()
                    );
                }
            }

            // -------------------------
            // REMOVE NODE
            // -------------------------

            Nodes.remove(
                    world,
                    target
            );

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

                Vector3i position = entry.getKey();
                SignalState oldInstantState = entry.getValue();

                ConnectableSignalRecalculator.recompute(
                        world,
                        position
                );

                Nodes.Node recomputedNode = Nodes.get(
                        world,
                        position
                );

                if (recomputedNode == null) {
                    continue;
                }

                // Only start a new effective wave if breaking the node
                // actually changed the logical instant truth here.
                if (recomputedNode.instantState() != oldInstantState) {
                    ConnectablePropagationScheduler.scheduleAdoption(
                            world,
                            position
                    );
                }
            }

            ConnectableVisualDispatcher.refreshTopologyAround(world, target);
        }
    }
}
