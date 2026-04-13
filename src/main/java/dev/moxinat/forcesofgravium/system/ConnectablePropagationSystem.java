package dev.moxinat.forcesofgravium.system;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.DelayedEntitySystem;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.moxinat.forcesofgravium.logic.network.ConnectablePropagationScheduler;
import dev.moxinat.forcesofgravium.logic.siphon.GraviumSiphonLogic;
import dev.moxinat.forcesofgravium.persistence.WorldSaveFileService;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ConnectablePropagationSystem extends DelayedEntitySystem<EntityStore> {

    private static final float TICK_INTERVAL_SECONDS = 2.0f;
    private static final int AUTOSAVE_INTERVAL_TICKS = 100;
    private static final Map<String, Long> LAST_PROCESSED_WORLD_TICKS = new ConcurrentHashMap<>();

    public ConnectablePropagationSystem() {
        super(TICK_INTERVAL_SECONDS);
    }

    @Override
    public @Nonnull Query<EntityStore> getQuery() {
        return Player.getComponentType();
    }

    @Override
    public void tick(float delta, int index, @Nonnull ArchetypeChunk<EntityStore> chunk, @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        long tick = store.getExternalData().getWorld().getTick();
        if (!markWorldTickProcessed(store, tick)) {
            return;
        }

        ConnectablePropagationScheduler.tickPropagation();
        GraviumSiphonLogic.tickWorld(store.getExternalData().getWorld());
        if (tick % AUTOSAVE_INTERVAL_TICKS == 0) {
            WorldSaveFileService.saveDirtyWorlds();
        }
    }

    private static boolean markWorldTickProcessed(Store<EntityStore> store, long tick) {
        String worldKey = store.getExternalData().getWorld().getSavePath().toAbsolutePath().normalize().toString();
        Long previousTick = LAST_PROCESSED_WORLD_TICKS.put(worldKey, tick);
        return previousTick == null || previousTick.longValue() != tick;
    }
}
