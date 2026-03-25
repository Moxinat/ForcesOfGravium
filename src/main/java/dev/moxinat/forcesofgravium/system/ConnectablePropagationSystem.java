package dev.moxinat.forcesofgravium.system;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.moxinat.forcesofgravium.logic.network.ConnectablePropagationScheduler;
import dev.moxinat.forcesofgravium.persistence.WorldSaveFileService;

import javax.annotation.Nonnull;

public final class ConnectablePropagationSystem extends TickingSystem<EntityStore> {

    private static final int AUTOSAVE_INTERVAL_TICKS = 100;

    @Override
    public void tick(float delta, int tick, @Nonnull Store<EntityStore> store) {
        ConnectablePropagationScheduler.tickPropagation();
        if (tick % AUTOSAVE_INTERVAL_TICKS == 0) {
            WorldSaveFileService.saveDirtyWorlds();
        }
    }
}
