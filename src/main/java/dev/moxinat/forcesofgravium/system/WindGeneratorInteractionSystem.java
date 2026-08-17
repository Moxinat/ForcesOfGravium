package dev.moxinat.forcesofgravium.system;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.UseBlockEvent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.moxinat.forcesofgravium.connectable.SignalState;
import dev.moxinat.forcesofgravium.connectable.energy.EnergyManager;
import dev.moxinat.forcesofgravium.connectable.propagation.ConnectablePropagationScheduler;
import dev.moxinat.forcesofgravium.connectable.propagation.ConnectableSignalRecalculator;
import dev.moxinat.forcesofgravium.connectable.registry.NodeTypes;
import dev.moxinat.forcesofgravium.connectable.registry.SourceRegistry;
import dev.moxinat.forcesofgravium.connectable.spatial.ConnectableNeighborResolver;
import dev.moxinat.forcesofgravium.data.Nodes;
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

        if (!NodeTypes.WIND_GENERATOR
                .blockId()
                .equals(blockType.getId())) {
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

        Nodes.Node node = Nodes.get(
                world,
                position
        );

        if (node == null) {
            return;
        }

        boolean turningOn =
                node.energyDelta() == 0;

        int energyDelta;

        SignalState instantState;

        if (turningOn) {
            energyDelta =
                    SourceRegistry.powerFor(node.blockId());

            if (energyDelta <= 0) {
                return;
            }

            instantState = SignalState.PUSH;
        } else {
            energyDelta = 0;
            instantState = SignalState.OFF;
        }

        node = node
                .withEnergyDelta(energyDelta)
                .withInstantState(instantState)
                .withDirty(true);

        Nodes.put(
                world,
                node
        );

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