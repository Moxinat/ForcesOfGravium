package dev.moxinat.forcesofgravium.lifecycle;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.moxinat.forcesofgravium.dispatcher.ConnectableVisualRefreshScheduler;
import dev.moxinat.forcesofgravium.dispatcher.NodeControlDispatcher;
import dev.moxinat.forcesofgravium.energy.EnergyManager;
import dev.moxinat.forcesofgravium.signal.ConnectablePropagationScheduler;
import dev.moxinat.forcesofgravium.block.siphon.GraviumSiphonLogic;
import dev.moxinat.forcesofgravium.source.SourceActivationScheduler;
import dev.moxinat.forcesofgravium.persistence.WorldSaveFileService;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class WorldTickSystem extends EntityTickingSystem<EntityStore> {

    private static final int SIPHON_LOGIC_INTERVAL_TICKS = 5;
    private static final int AUTOSAVE_INTERVAL_TICKS = 1000;
    private static final Map<String, Long> LAST_PROCESSED_WORLD_TICKS = new ConcurrentHashMap<>();
    private static final Map<String, Long> LAST_AUTOSAVED_WORLD_TICKS = new ConcurrentHashMap<>();

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

        WorldSaveFileService.ensureLoaded(world);

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

        // Siphon Ticks
        if (tick % SIPHON_LOGIC_INTERVAL_TICKS == 0) {
            GraviumSiphonLogic.tickWorld(
                    world,
                    commandBuffer
            );
        }

        // Autosave
        if (shouldAutosave(world, tick)) {
            WorldSaveFileService.saveWorld(world);
        }
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

    private static boolean shouldAutosave(
            @Nonnull World world,
            long tick
    ) {
        String worldKey = world.getName();

        Long previousTick =
                LAST_AUTOSAVED_WORLD_TICKS.get(worldKey);

        if (previousTick != null
                && tick - previousTick < AUTOSAVE_INTERVAL_TICKS) {

            return false;
        }

        LAST_AUTOSAVED_WORLD_TICKS.put(
                worldKey,
                tick
        );

        return true;
    }
}
