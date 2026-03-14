package dev.moxinat.forcesofgravium;

import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import dev.moxinat.forcesofgravium.commands.ForcesOfGraviumCommand;
import dev.moxinat.forcesofgravium.event.BlockPlacementEvents;
import dev.moxinat.forcesofgravium.event.ForcesOfGraviumEvents;
import dev.moxinat.forcesofgravium.event.GravityPowderLogic;
import dev.moxinat.forcesofgravium.event.InverterLogic;

import javax.annotation.Nonnull;

public class ForcesOfGraviumPlugin extends JavaPlugin {

    public ForcesOfGraviumPlugin(@Nonnull JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        this.getCommandRegistry().registerCommand(
            new ForcesOfGraviumCommand("test", "Primary command for ForcesOfGravium")
        );
        this.getEventRegistry().registerGlobal(PlayerReadyEvent.class, ForcesOfGraviumEvents::onPlayerReady);
        this.getEntityStoreRegistry().registerSystem(new BlockPlacementEvents.PlaceBlockRotationSystem());
        this.getEntityStoreRegistry().registerSystem(new GravityPowderLogic.PlaceSystem());
        this.getEntityStoreRegistry().registerSystem(new GravityPowderLogic.BreakSystem());
        this.getEntityStoreRegistry().registerSystem(new InverterLogic.PlaceSystem());
        this.getEntityStoreRegistry().registerSystem(new InverterLogic.BreakSystem());
    }
}
