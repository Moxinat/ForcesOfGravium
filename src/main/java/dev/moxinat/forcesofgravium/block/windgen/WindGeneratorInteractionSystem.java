package dev.moxinat.forcesofgravium.block.windgen;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.UseBlockEvent;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.moxinat.forcesofgravium.ForcesOfGraviumPlugin;
import dev.moxinat.forcesofgravium.data.NodeComponent;
import dev.moxinat.forcesofgravium.data.SourceComponent;
import dev.moxinat.forcesofgravium.registry.ConnectableRegistry;
import dev.moxinat.forcesofgravium.signal.SignalState;
import dev.moxinat.forcesofgravium.energy.EnergyManager;
import dev.moxinat.forcesofgravium.signal.ConnectablePropagationScheduler;
import dev.moxinat.forcesofgravium.signal.ConnectableSignalRecalculator;
import dev.moxinat.forcesofgravium.spatial.ConnectableNeighborResolver;
import org.joml.Vector3i;

import javax.annotation.Nonnull;

public final class WindGeneratorInteractionSystem
        extends EntityEventSystem<EntityStore, UseBlockEvent.Post> {

    public WindGeneratorInteractionSystem() {
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

        if (blockType == null
                || !ConnectableRegistry.WIND_GENERATOR_BLOCK_ID
                .equals(
                        ConnectableRegistry.rawBlockId(
                                blockType.getId()
                        )
                )) {
            return;
        }

        Ref<EntityStore> entityRef =
                chunk.getReferenceTo(index);

        Player player = store.getComponent(
                entityRef,
                Player.getComponentType()
        );

        if (player == null || player.getWorld() == null) {
            return;
        }

        World world = player.getWorld();

        Vector3i position =
                new Vector3i(event.getTargetBlock());

        NodeComponent node =
                BlockModule.getComponent(
                        ForcesOfGraviumPlugin.NODE_COMPONENT_TYPE,
                        world,
                        position.x(),
                        position.y(),
                        position.z()
                );

        if (node == null) {
            return;
        }

        SourceComponent source =
                BlockModule.getComponent(
                        ForcesOfGraviumPlugin.SOURCE_COMPONENT_TYPE,
                        world,
                        position.x(),
                        position.y(),
                        position.z()
                );

        if (source == null) {
            return;
        }

        boolean turningOn =
                node.energyDelta() == 0;

        int energyDelta;

        SignalState instantState;

        if (turningOn) {
            energyDelta = source.power();

            if (energyDelta <= 0) {
                return;
            }

            instantState = SignalState.PUSH;
        } else {
            energyDelta = 0;
            instantState = SignalState.OFF;
        }

        node.setEnergyDelta(energyDelta);
        node.setInstantState(instantState);
        node.setDirty(true);

        EnergyManager.checkNetwork(
                world,
                position
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
        }

        ConnectablePropagationScheduler.scheduleAdoption(
                world,
                position
        );
    }
}
