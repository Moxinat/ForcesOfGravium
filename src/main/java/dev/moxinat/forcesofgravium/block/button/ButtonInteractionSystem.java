package dev.moxinat.forcesofgravium.block.button;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import dev.moxinat.forcesofgravium.ForcesOfGraviumPlugin;
import dev.moxinat.forcesofgravium.data.NodeComponent;
import dev.moxinat.forcesofgravium.data.SourceComponent;
import dev.moxinat.forcesofgravium.registry.ConnectableRegistry;
import org.joml.Vector3i;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.UseBlockEvent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.moxinat.forcesofgravium.source.SourceActivationScheduler;

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
        Ref<EntityStore> entityRef = chunk.getReferenceTo(index);
        Player player = store.getComponent(entityRef, Player.getComponentType());
        if (player == null || player.getWorld() == null) {
            return;
        }

        World world = player.getWorld();
        Vector3i position = new Vector3i(event.getTargetBlock());
        NodeComponent node =
                BlockModule.getComponent(
                        ForcesOfGraviumPlugin.NODE_COMPONENT_TYPE,
                        world,
                        position.x(),
                        position.y(),
                        position.z()
                );

        BlockType blockType = event.getBlockType();

        if (node == null
                || !ConnectableRegistry.WOODEN_BUTTON_BLOCK_ID.equals(
                ConnectableRegistry.rawBlockId(
                        blockType.getId()
                )
        )) {
            return;
        }

        SourceActivationScheduler.activateForTicks(world, position, BUTTON_ACTIVE_TICKS);
    }

    public static final class TickSystem
            extends EntityTickingSystem<ChunkStore> {

        @Override
        public @Nonnull Query<ChunkStore> getQuery() {
            return Query.and(
                    ForcesOfGraviumPlugin.SOURCE_COMPONENT_TYPE,
                    BlockModule.BlockStateInfo.getComponentType()
            );
        }

        @Override
        public void tick(
                float delta,
                int index,
                @Nonnull ArchetypeChunk<ChunkStore> chunk,
                @Nonnull Store<ChunkStore> store,
                @Nonnull CommandBuffer<ChunkStore> commandBuffer
        ) {
            SourceComponent source =
                    chunk.getComponent(
                            index,
                            ForcesOfGraviumPlugin.SOURCE_COMPONENT_TYPE
                    );

            BlockModule.BlockStateInfo blockStateInfo =
                    chunk.getComponent(
                            index,
                            BlockModule.BlockStateInfo.getComponentType()
                    );

            if (source == null || blockStateInfo == null) {
                return;
            }

            Vector3i position = new Vector3i();

            if (!blockStateInfo.fillWorldPos(
                    store,
                    position
            )) {
                return;
            }

            World world =
                    store.getExternalData().getWorld();

            BlockType blockType =
                    world.getBlockType(
                            position.x(),
                            position.y(),
                            position.z()
                    );

            if (blockType == null
                    || !ConnectableRegistry.WOODEN_BUTTON_BLOCK_ID.equals(
                    ConnectableRegistry.rawBlockId(
                            blockType.getId()
                    )
            )) {
                return;
            }

            if (source.remainingActiveTicks() <= 0) {
                return;
            }

            source.tickRemainingActiveTicks();

            if (source.remainingActiveTicks() == 0) {
                SourceActivationScheduler.deactivate(
                        world,
                        position
                );
            }
        }
    }
}
