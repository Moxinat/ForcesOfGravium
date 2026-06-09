package dev.moxinat.forcesofgravium.system;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import org.joml.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.event.events.ecs.PlaceBlockEvent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.moxinat.forcesofgravium.connectable.ConnectableRuntimeAccessor;
import dev.moxinat.forcesofgravium.data.GraviumSiphonStore;
import dev.moxinat.forcesofgravium.data.GravityPowderBlockDataStore;
import dev.moxinat.forcesofgravium.data.InverterDataStore;
import dev.moxinat.forcesofgravium.data.SourceBlockDataStore;
import dev.moxinat.forcesofgravium.logic.network.ConnectablePropagationScheduler;
import dev.moxinat.forcesofgravium.registry.ConnectableBlockRoles;
import dev.moxinat.forcesofgravium.registry.ConnectableRegistry;

import javax.annotation.Nonnull;

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
        public void handle(int index, @Nonnull ArchetypeChunk<EntityStore> chunk, @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer, @Nonnull PlaceBlockEvent event) {
            ItemStack itemInHand = event.getItemInHand();
            if (itemInHand == null || ConnectableRegistry.isNotConnectable(itemInHand.getItemId())) {
                return;
            }

            Ref<EntityStore> entityRef = chunk.getReferenceTo(index);
            Player player = store.getComponent(entityRef, Player.getComponentType());
            if (player == null || player.getWorld() == null) {
                return;
            }

            World world = player.getWorld();
            Vector3i target = new Vector3i(event.getTargetBlock());
            if (ConnectableRegistry.isGravityPowderCarrierId(itemInHand.getItemId())) {
                GravityPowderBlockDataStore.putDefault(world, target);
            }
            if (ConnectableRegistry.isInverterId(itemInHand.getItemId())) {
                InverterDataStore.putDefault(world, target);
            }
            if (ConnectableRegistry.GRAVIUM_SIPHON_BLOCK_ID.equals(itemInHand.getItemId())) {
                GraviumSiphonStore.add(world, target);
            }
            if (ConnectableBlockRoles.isSource(itemInHand.getItemId())) {
                SourceBlockDataStore.putDefault(world, target, itemInHand.getItemId());
            }
            ConnectableRuntimeAccessor.setRotation(world, target, event.getRotation());
            ConnectablePropagationScheduler.onConnectablePlaced(world, target);
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
        public void handle(int index, @Nonnull ArchetypeChunk<EntityStore> chunk, @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer, @Nonnull BreakBlockEvent event) {
            BlockType brokenType = event.getBlockType();
            if (ConnectableRegistry.isNotConnectable(brokenType.getId())) {
                return;
            }

            Ref<EntityStore> entityRef = chunk.getReferenceTo(index);
            Player player = store.getComponent(entityRef, Player.getComponentType());
            if (player == null || player.getWorld() == null) {
                return;
            }

            World world = player.getWorld();
            Vector3i target = new Vector3i(event.getTargetBlock());
            ConnectableRuntimeAccessor.remove(world, target);
            if (ConnectableRegistry.isGravityPowderCarrierId(brokenType.getId())) {
                GravityPowderBlockDataStore.remove(world, target);
            }
            if (ConnectableRegistry.isInverterId(brokenType.getId())) {
                InverterDataStore.remove(world, target);
            }
            if (ConnectableRegistry.isGraviumSiphonId(brokenType.getId())) {
                GraviumSiphonStore.remove(world, target);
            }
            if (ConnectableBlockRoles.isSource(brokenType.getId())) {
                SourceBlockDataStore.remove(world, target);
            }
            ConnectablePropagationScheduler.onConnectableBroken(world, target);
        }
    }
}
