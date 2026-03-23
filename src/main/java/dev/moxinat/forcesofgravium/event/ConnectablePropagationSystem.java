package dev.moxinat.forcesofgravium.event;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public final class ConnectablePropagationSystem extends TickingSystem<EntityStore> {

    @Override
    public void tick(float delta, int tick, Store<EntityStore> store) {
        CableNetworkUpdater.tickPropagation();
    }
}
