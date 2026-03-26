package dev.moxinat.forcesofgravium.debug;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;

import javax.annotation.Nullable;

public final class ReconnectWaveDebug {

    private static volatile boolean enabled;

    private ReconnectWaveDebug() {
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean value) {
        enabled = value;
    }

    public static void send(@Nullable Player player, String message) {
        if (!enabled || player == null) {
            return;
        }
        player.sendMessage(Message.raw("[FoG ReconnectDebug] " + message));
    }
}
