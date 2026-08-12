package dev.moxinat.forcesofgravium.system;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import dev.moxinat.forcesofgravium.connectable.registry.NodeTypes;
import org.joml.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.UseBlockEvent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.moxinat.forcesofgravium.block.source.SourceActivationScheduler;

import javax.annotation.Nonnull;

public final class ButtonInteractionSystem extends EntityEventSystem<EntityStore, UseBlockEvent.Post> {

    private static final long BUTTON_ACTIVE_TICKS = 30L;

    public ButtonInteractionSystem() {
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
        BlockType blockType = event.getBlockType();
        if (!NodeTypes.WOODEN_BUTTON.blockId().equals(blockType.getId())) {
            return;
        }

        Ref<EntityStore> entityRef = chunk.getReferenceTo(index);
        Player player = store.getComponent(entityRef, Player.getComponentType());
        if (player == null || player.getWorld() == null) {
            return;
        }

        World world = player.getWorld();
        Vector3i position = new Vector3i(event.getTargetBlock());
        SourceActivationScheduler.activateForTicks(world, position, BUTTON_ACTIVE_TICKS);
    }
}
