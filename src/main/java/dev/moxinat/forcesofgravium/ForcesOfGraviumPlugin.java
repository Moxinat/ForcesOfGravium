package dev.moxinat.forcesofgravium;

import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import dev.moxinat.forcesofgravium.commands.ForcesOfGraviumCommand;
import dev.moxinat.forcesofgravium.event.ForcesOfGraviumEvents;
import dev.moxinat.forcesofgravium.system.BlockPlacementRotationSystem;
import dev.moxinat.forcesofgravium.system.ConnectableBlockLifecycleSystem;
import dev.moxinat.forcesofgravium.system.ConnectablePropagationSystem;

import javax.annotation.Nonnull;

public class ForcesOfGraviumPlugin extends JavaPlugin {

    public ForcesOfGraviumPlugin(@Nonnull JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        this.getCommandRegistry().registerCommand(
            new ForcesOfGraviumCommand("fog", "Main command for ForcesOfGravium")
        );
        this.getEventRegistry().registerGlobal(PlayerReadyEvent.class, ForcesOfGraviumEvents::onPlayerReady);
        this.getEntityStoreRegistry().registerSystem(new BlockPlacementRotationSystem());
        this.getEntityStoreRegistry().registerSystem(new ConnectableBlockLifecycleSystem.PlaceSystem());
        this.getEntityStoreRegistry().registerSystem(new ConnectableBlockLifecycleSystem.BreakSystem());
        this.getEntityStoreRegistry().registerSystem(new ConnectablePropagationSystem());
    }
}
