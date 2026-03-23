package dev.moxinat.forcesofgravium.event;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.event.events.ecs.PlaceBlockEvent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.moxinat.forcesofgravium.data.ConnectableRotationStore;
import dev.moxinat.forcesofgravium.data.GravityPowderBlockDataStore;

import javax.annotation.Nonnull;

public final class GravityPowderLogic {

    private GravityPowderLogic() {
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
            if (itemInHand == null || !CableNetworkUpdater.GRAVITY_POWDER_BLOCK_ID.equals(itemInHand.getItemId())) {
                return;
            }

            Ref<EntityStore> entityRef = chunk.getReferenceTo(index);
            Player player = store.getComponent(entityRef, Player.getComponentType());
            if (player == null || player.getWorld() == null) {
                return;
            }

            World world = player.getWorld();
            Vector3i target = new Vector3i(event.getTargetBlock());
            GravityPowderBlockDataStore.putDefault(world, target);
            ConnectableRotationStore.put(world, target, event.getRotation());
            CableNetworkUpdater.onConnectablePlaced(world, target);
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
            if (CableNetworkUpdater.isNotGravityPowder(brokenType)) {
                return;
            }

            Ref<EntityStore> entityRef = chunk.getReferenceTo(index);
            Player player = store.getComponent(entityRef, Player.getComponentType());
            if (player == null || player.getWorld() == null) {
                return;
            }

            World world = player.getWorld();
            Vector3i target = new Vector3i(event.getTargetBlock());
            ConnectableRotationStore.remove(world, target);
            GravityPowderBlockDataStore.remove(world, target);
            CableNetworkUpdater.onConnectableBroken(world, target);
        }
    }
}
