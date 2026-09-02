package dev.moxinat.forcesofgravium;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.server.core.event.events.ShutdownEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import dev.moxinat.forcesofgravium.block.button.ButtonInteractionSystem;
import dev.moxinat.forcesofgravium.block.gravity.CurveCasedGravityPowderRotationSystem;
import dev.moxinat.forcesofgravium.block.sensor.SensorLogic;
import dev.moxinat.forcesofgravium.block.sensor.SensorRotationSystem;
import dev.moxinat.forcesofgravium.block.siphon.GraviumSiphonLogic;
import dev.moxinat.forcesofgravium.block.windgen.WindGeneratorInteractionSystem;
import dev.moxinat.forcesofgravium.commands.ForcesOfGraviumCommand;
import dev.moxinat.forcesofgravium.data.*;
import dev.moxinat.forcesofgravium.lifecycle.ForcesOfGraviumEvents;
import dev.moxinat.forcesofgravium.lifecycle.*;

import javax.annotation.Nonnull;

public class ForcesOfGraviumPlugin extends JavaPlugin {

    public ForcesOfGraviumPlugin(@Nonnull JavaPluginInit init) {
        super(init);
    }

    public static ComponentType<ChunkStore, SensorComponent> SENSOR_COMPONENT_TYPE;
    public static ComponentType<ChunkStore, NodeComponent> NODE_COMPONENT_TYPE;
    public static ComponentType<ChunkStore, SourceComponent> SOURCE_COMPONENT_TYPE;
    public static ResourceType<ChunkStore, NetworkResource> NETWORK_RESOURCE_TYPE;
    public static ResourceType<ChunkStore, SignalRuntimeResource> SIGNAL_RESOURCE_TYPE;
    public static ComponentType<ChunkStore, SiphonComponent> SIPHON_COMPONENT_TYPE;

    @Override
    protected void setup() {
        NODE_COMPONENT_TYPE =
                this.getChunkStoreRegistry().registerComponent(
                        NodeComponent.class,
                        "forcesofgravium:node",
                        NodeComponent.CODEC
                );

        SOURCE_COMPONENT_TYPE =
                this.getChunkStoreRegistry().registerComponent(
                        SourceComponent.class,
                        "forcesofgravium:source",
                        SourceComponent.CODEC
                );

        SENSOR_COMPONENT_TYPE =
                this.getChunkStoreRegistry().registerComponent(
                        SensorComponent.class,
                        "forcesofgravium:sensor",
                        SensorComponent.CODEC
                );

        SIPHON_COMPONENT_TYPE =
                this.getChunkStoreRegistry().registerComponent(
                        SiphonComponent.class,
                        "forcesofgravium:siphon",
                        SiphonComponent.CODEC
                );

        NETWORK_RESOURCE_TYPE =
                this.getChunkStoreRegistry().registerResource(
                        NetworkResource.class,
                        "forcesofgravium:networks",
                        NetworkResource.CODEC
                );

        SIGNAL_RESOURCE_TYPE =
                this.getChunkStoreRegistry().registerResource(
                        SignalRuntimeResource.class,
                        "forcesofgravium:signal",
                        SignalRuntimeResource.CODEC
                );


        this.getCommandRegistry().registerCommand(
            new ForcesOfGraviumCommand("fog", "Main command for ForcesOfGravium")
        );
        this.getEventRegistry().registerGlobal(PlayerReadyEvent.class, ForcesOfGraviumEvents::onPlayerReady);
        this.getEntityStoreRegistry().registerSystem(new ButtonInteractionSystem());
        this.getEntityStoreRegistry().registerSystem(new CurveCasedGravityPowderRotationSystem.UseSystem());
        this.getEntityStoreRegistry().registerSystem(new ConnectableBlockLifecycleSystem.PlaceSystem());
        this.getChunkStoreRegistry().registerSystem(new ConnectableBlockLifecycleSystem.PlacedSystem());
        this.getEntityStoreRegistry().registerSystem(new ConnectableBlockLifecycleSystem.BreakSystem());
        this.getEntityStoreRegistry().registerSystem(new WorldTickSystem());
        this.getChunkStoreRegistry().registerSystem(new GraviumSiphonLogic.TickSystem());
        this.getEntityStoreRegistry().registerSystem(new WindGeneratorInteractionSystem());
        this.getEntityStoreRegistry().registerSystem(new SensorRotationSystem.UseSystem());
        this.getEntityStoreRegistry().registerSystem(new SensorLogic.BlockUseSystem()); //temp
        this.getEntityStoreRegistry().registerSystem(new SensorLogic.ItemLifecycleSystem());
        this.getEntityStoreRegistry().registerSystem(new SensorLogic.ItemTrackingSystem());

        SensorLogic.registerTriggerEffects();
    }
}
