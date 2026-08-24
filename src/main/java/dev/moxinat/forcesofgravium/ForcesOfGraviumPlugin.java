package dev.moxinat.forcesofgravium;

import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import dev.moxinat.forcesofgravium.block.button.ButtonInteractionSystem;
import dev.moxinat.forcesofgravium.block.gravity.CurveCasedGravityPowderRotationSystem;
import dev.moxinat.forcesofgravium.block.sensor.SensorLogic;
import dev.moxinat.forcesofgravium.block.sensor.SensorRotationSystem;
import dev.moxinat.forcesofgravium.block.windgen.WindGeneratorInteractionSystem;
import dev.moxinat.forcesofgravium.commands.ForcesOfGraviumCommand;
import dev.moxinat.forcesofgravium.lifecycle.ForcesOfGraviumEvents;
import dev.moxinat.forcesofgravium.lifecycle.*;
import dev.moxinat.forcesofgravium.persistence.WorldSaveFileService;

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
        this.getEntityStoreRegistry().registerSystem(new ButtonInteractionSystem());
        this.getEntityStoreRegistry().registerSystem(new CurveCasedGravityPowderRotationSystem.UseSystem());
        this.getEntityStoreRegistry().registerSystem(new ConnectableBlockLifecycleSystem.PlaceSystem());
        this.getEntityStoreRegistry().registerSystem(new ConnectableBlockLifecycleSystem.BreakSystem());
        this.getEntityStoreRegistry().registerSystem(new WorldTickSystem());
        this.getEntityStoreRegistry().registerSystem(new WindGeneratorInteractionSystem());
        this.getEntityStoreRegistry().registerSystem(new SensorRotationSystem.UseSystem());
        this.getEntityStoreRegistry().registerSystem(new SensorLogic.BlockUseSystem()); //temp

        SensorLogic.registerTriggerEffects();
    }

    @Override
    protected void shutdown() {
        System.out.println("[FoG] PLUGIN SHUTDOWN");

        WorldSaveFileService.saveLoadedWorlds();
    }
}
