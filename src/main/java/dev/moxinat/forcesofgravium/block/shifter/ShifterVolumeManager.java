package dev.moxinat.forcesofgravium.block.shifter;

import com.hypixel.hytale.server.core.universe.world.World;
import dev.moxinat.forcesofgravium.signal.SignalState;
import org.joml.Vector3i;

import javax.annotation.Nonnull;

public final class ShifterVolumeManager {

    private ShifterVolumeManager() {
    }

    public static void update(
            @Nonnull World world,
            @Nonnull Vector3i position,
            @Nonnull SignalState state
    ) {
        switch (state) {
            case OFF -> {
            }
            case PUSH -> {
            }
            case PULL -> {
            }
        }
    }

    public static void handleBroken(
            @Nonnull World world,
            @Nonnull Vector3i position
    ) {
    }
}
