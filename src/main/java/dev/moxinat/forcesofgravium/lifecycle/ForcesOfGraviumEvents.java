package dev.moxinat.forcesofgravium.lifecycle;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ShutdownEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.modules.entity.component.DisplayNameComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.moxinat.forcesofgravium.ForcesOfGraviumPlugin;
import dev.moxinat.forcesofgravium.data.Nodes;
import dev.moxinat.forcesofgravium.data.SensorComponent;
import dev.moxinat.forcesofgravium.persistence.WorldSaveFileService;
import dev.moxinat.forcesofgravium.registry.NodeTypes;
import org.joml.Vector3i;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ForcesOfGraviumEvents {

    public static void onPlayerReady(PlayerReadyEvent event) {
        Player player = event.getPlayer();
        World world = player.getWorld();
        if (world == null) {
            return;
        }

        WorldSaveFileService.ensureLoaded(world);

        for (Nodes.Node node : Nodes.snapshotForWorld(world).values()) {
            if (!NodeTypes.GRAVIUM_SENSOR.blockId().equals(node.blockId())) {
                continue;
            }

            Vector3i position = node.position();

            SensorComponent component =
                    BlockModule.getComponent(
                            ForcesOfGraviumPlugin.SENSOR_COMPONENT_TYPE,
                            world,
                            position.x(),
                            position.y(),
                            position.z()
                    );
        }

        world.sendMessage(Message.join(
                Message.raw("Welcome "),
                getDisplayName(player),
                Message.raw(" to ForcesOfGravium.")
        ));
    }

    public static void onShutdown(ShutdownEvent event) {
        WorldSaveFileService.saveLoadedWorlds();
    }

    private static Message getDisplayName(Player player) {
        Ref<EntityStore> entityRef = player.getReference();
        if (entityRef != null && entityRef.isValid()) {
            Store<EntityStore> store = entityRef.getStore();
            DisplayNameComponent displayName = store.getComponent(entityRef, DisplayNameComponent.getComponentType());
            if (displayName != null) {
                return displayName.getDisplayName();
            }

            PlayerRef playerRef = store.getComponent(entityRef, PlayerRef.getComponentType());
            if (playerRef != null) {
                return Message.raw(playerRef.getUsername());
            }
        }

        return Message.raw("Player");
    }
}
