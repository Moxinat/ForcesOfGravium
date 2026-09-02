package dev.moxinat.forcesofgravium.lifecycle;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.moxinat.forcesofgravium.block.sensor.SensorBlockRefresher;
import dev.moxinat.forcesofgravium.block.sensor.SensorLogic;
import dev.moxinat.forcesofgravium.dispatcher.ConnectableVisualRefreshScheduler;
import dev.moxinat.forcesofgravium.dispatcher.NodeControlDispatcher;
import dev.moxinat.forcesofgravium.energy.EnergyManager;
import dev.moxinat.forcesofgravium.signal.ConnectablePropagationScheduler;
import dev.moxinat.forcesofgravium.source.SourceActivationScheduler;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class WorldTickSystem extends EntityTickingSystem<EntityStore> {

    private static final Map<String, Long> LAST_PROCESSED_WORLD_TICKS = new ConcurrentHashMap<>();

    public WorldTickSystem() {
    }

    @Override
    public @Nonnull Query<EntityStore> getQuery() {
        return Player.getComponentType();
    }

    @Override
    public void tick(
            float delta,
            int index,
            @Nonnull ArchetypeChunk<EntityStore> chunk,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        World world = store.getExternalData().getWorld();

        long tick = world.getTick();

        if (!markWorldTickProcessed(world, tick)) {
            return;
        }

        // Every tick
        SourceActivationScheduler.tickWorld(world);
        ConnectablePropagationScheduler.tickWorld(world);
        EnergyManager.tickWorld(world);
        ConnectableVisualRefreshScheduler.tickWorld(world);
        NodeControlDispatcher.tickWorld(world);
        SensorLogic.tickWorld(world);
        SensorBlockRefresher.tickWorld(world);
    }

    private static boolean markWorldTickProcessed(
            @Nonnull World world,
            long tick
    ) {
        Long previousTick =
                LAST_PROCESSED_WORLD_TICKS.put(
                        world.getName(),
                        tick
                );

        return previousTick == null
                || previousTick != tick;
    }
}
