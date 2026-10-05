package dev.moxinat.forcesofgravium.block.shifter;

import com.hypixel.hytale.builtin.triggervolumes.EntityTargetType;
import com.hypixel.hytale.builtin.triggervolumes.TriggerVolumesPlugin;
import com.hypixel.hytale.builtin.triggervolumes.effect.TriggerEventType;
import com.hypixel.hytale.builtin.triggervolumes.effect.builtin.SetVelocityEffect;
import com.hypixel.hytale.builtin.triggervolumes.manager.TriggerVolumeManager;
import com.hypixel.hytale.builtin.triggervolumes.manager.VolumeEntry;
import com.hypixel.hytale.builtin.triggervolumes.shape.BoxShape;
import com.hypixel.hytale.server.core.universe.world.World;
import dev.moxinat.forcesofgravium.registry.ConnectableRegistry;
import dev.moxinat.forcesofgravium.signal.SignalState;
import dev.moxinat.forcesofgravium.spatial.ConnectableNeighborResolver;
import org.joml.Vector3d;
import org.joml.Vector3i;

import javax.annotation.Nonnull;
import java.util.EnumSet;
import java.util.List;

public final class ShifterVolumeManager {

    private static final double ENTITY_FORCE = 2.0;

    private ShifterVolumeManager() {
    }

    public static void update(
            @Nonnull World world,
            @Nonnull Vector3i position,
            @Nonnull SignalState state
    ) {
        unregisterTriggerVolume(
                world,
                position
        );

        switch (state) {
            case OFF -> {
            }
            case PUSH, PULL ->
                    registerTriggerVolume(
                            world,
                            position,
                            state
                    );
        }
    }

    public static void handleBroken(
            @Nonnull World world,
            @Nonnull Vector3i position
    ) {
        unregisterTriggerVolume(
                world,
                position
        );
    }

    private static void registerTriggerVolume(
            @Nonnull World world,
            @Nonnull Vector3i shifterPosition,
            @Nonnull SignalState state
    ) {
        TriggerVolumeManager manager =
                world.getEntityStore()
                        .getStore()
                        .getResource(
                                TriggerVolumesPlugin.get()
                                        .getManagerResourceType()
                        );

        Vector3i frontPosition =
                ConnectableNeighborResolver.adjacentPositionForLocalSide(
                        world,
                        shifterPosition,
                        ConnectableRegistry.SIDE_FRONT
                );

        Vector3d direction =
                new Vector3d(
                        frontPosition.x() - shifterPosition.x(),
                        frontPosition.y() - shifterPosition.y(),
                        frontPosition.z() - shifterPosition.z()
                );

        if (state == SignalState.PULL) {
            direction.negate();
        }

        Vector3d velocity =
                direction.mul(ENTITY_FORCE);

        SetVelocityEffect velocityEffect =
                SetVelocityEffect.create(
                        TriggerEventType.TICK,
                        velocity,
                        true
                );

        String volumeId =
                triggerVolumeId(
                        shifterPosition
                );

        Vector3d volumePosition =
                new Vector3d(
                        frontPosition.x() + 0.5,
                        frontPosition.y(),
                        frontPosition.z() + 0.5
                );

        BoxShape shape =
                new BoxShape(
                        new Vector3d(-0.48, 0.02, -0.48),
                        new Vector3d(0.48, 0.98, 0.48)
                );

        VolumeEntry volume =
                new VolumeEntry(
                        volumeId,
                        world.getName(),
                        volumePosition,
                        shape,
                        List.of(velocityEffect),
                        EnumSet.of(
                                EntityTargetType.PLAYER,
                                EntityTargetType.NPC
                        ),
                        true
                );

        manager.register(
                volumeId,
                volume
        );
    }

    private static void unregisterTriggerVolume(
            @Nonnull World world,
            @Nonnull Vector3i shifterPosition
    ) {
        TriggerVolumeManager manager =
                world.getEntityStore()
                        .getStore()
                        .getResource(
                                TriggerVolumesPlugin.get()
                                        .getManagerResourceType()
                        );

        String volumeId =
                triggerVolumeId(
                        shifterPosition
                );

        if (manager.hasVolume(volumeId)) {
            manager.unregister(volumeId);
        }
    }

    private static @Nonnull String triggerVolumeId(
            @Nonnull Vector3i position
    ) {
        return "fog_shifter_"
                + encodeCoordinate(position.x()) + "_"
                + encodeCoordinate(position.y()) + "_"
                + encodeCoordinate(position.z());
    }

    private static @Nonnull String encodeCoordinate(
            int value
    ) {
        long number =
                Math.abs(
                        (long) value
                );

        StringBuilder encoded =
                new StringBuilder();

        do {
            encoded.append(
                    (char) ('a' + (number % 26))
            );

            number /= 26;
        } while (number > 0);

        encoded.reverse();

        return (value < 0 ? "n" : "p")
                + encoded;
    }
}
