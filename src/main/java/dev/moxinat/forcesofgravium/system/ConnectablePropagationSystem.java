package dev.moxinat.forcesofgravium.system;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.moxinat.forcesofgravium.logic.network.ConnectableNetworkUpdateService;
import dev.moxinat.forcesofgravium.logic.network.ConnectablePropagationScheduler;
import dev.moxinat.forcesofgravium.logic.siphon.GraviumSiphonLogic;
import dev.moxinat.forcesofgravium.persistence.WorldSaveFileService;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ConnectablePropagationSystem extends EntityTickingSystem<EntityStore> {

    private static final int SIPHON_LOGIC_INTERVAL_TICKS = 5;
    private static final int AUTOSAVE_INTERVAL_TICKS = 100;
    private static final Map<String, Long> LAST_PROCESSED_WORLD_TICKS = new ConcurrentHashMap<>();
    private static final Map<String, Long> LAST_AUTOSAVED_WORLD_TICKS = new ConcurrentHashMap<>();

    public ConnectablePropagationSystem() {
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

        String worldKey = worldKey(store);
        ConnectableNetworkUpdateService.ensureInitialized(store.getExternalData().getWorld());
        ConnectablePropagationScheduler.tickPropagation();
        if (tick % SIPHON_LOGIC_INTERVAL_TICKS == 0) {
            GraviumSiphonLogic.tickWorld(store.getExternalData().getWorld(), commandBuffer);
        }
        if (shouldAutosave(worldKey, tick)) {
            WorldSaveFileService.saveDirtyWorlds();
        }
    }

    private static boolean markWorldTickProcessed(Store<EntityStore> store, long tick) {
        Long previousTick = LAST_PROCESSED_WORLD_TICKS.put(worldKey(store), tick);
        return previousTick == null || previousTick.longValue() != tick;
    }

    private static boolean shouldAutosave(String worldKey, long tick) {
        Long previousTick = LAST_AUTOSAVED_WORLD_TICKS.get(worldKey);
        if (previousTick != null && tick - previousTick < AUTOSAVE_INTERVAL_TICKS) {
            return false;
        }

        LAST_AUTOSAVED_WORLD_TICKS.put(worldKey, tick);
        return true;
    }

    private static String worldKey(Store<EntityStore> store) {
        return store.getExternalData().getWorld().getSavePath().toAbsolutePath().normalize().toString();
    }
}
