package dev.moxinat.forcesofgravium.event;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.event.events.ShutdownEvent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import dev.moxinat.forcesofgravium.persistence.WorldSaveFileService;

public class ForcesOfGraviumEvents {

    public static void onPlayerReady(PlayerReadyEvent event) {
        Player player = event.getPlayer();
        if (player.getWorld() != null) {
            WorldSaveFileService.ensureLoaded(player.getWorld());
        }
        player.getWorld().sendMessage(Message.raw("Welcome " + player.getLegacyDisplayName() + " to ForcesOfGravium."));
    }

    public static void onShutdown(ShutdownEvent event) {
        WorldSaveFileService.saveLoadedWorlds();
    }
}
