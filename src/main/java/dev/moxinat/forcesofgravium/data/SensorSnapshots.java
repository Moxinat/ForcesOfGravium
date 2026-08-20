package dev.moxinat.forcesofgravium.data;

import com.hypixel.hytale.server.core.universe.world.World;
import dev.moxinat.forcesofgravium.signal.SignalState;
import org.joml.Vector3i;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class SensorSnapshots {

    private static final Map<SensorKey, SensorSnapshot> SNAPSHOTS =
            new ConcurrentHashMap<>();

    private SensorSnapshots() {
    }

    public static void put(
            @Nonnull World world,
            @Nonnull Vector3i sensorPosition,
            @Nonnull SensorSnapshot snapshot
    ) {
        SNAPSHOTS.put(
                SensorKey.from(world, sensorPosition),
                snapshot
        );
    }

    public static @Nullable SensorSnapshot get(
            @Nonnull World world,
            @Nonnull Vector3i sensorPosition
    ) {
        return SNAPSHOTS.get(
                SensorKey.from(world, sensorPosition)
        );
    }

    public static void remove(
            @Nonnull World world,
            @Nonnull Vector3i sensorPosition
    ) {
        SNAPSHOTS.remove(
                SensorKey.from(world, sensorPosition)
        );
    }

    public static void clearWorld(
            @Nonnull World world
    ) {
        String worldIdentity = world.getName();

        SNAPSHOTS.keySet().removeIf(
                key -> key.worldIdentity().equals(worldIdentity)
        );
    }

    public record SensorSnapshot(
            @Nonnull String blockId,
            @Nullable NodeSnapshot node,
            @Nullable Integer containerItemCount,
            int entityCount,
            boolean playerPresent
    ) {
    }

    public record NodeSnapshot(
            @Nonnull SignalState effectiveState,
            boolean invertEnabled,
            boolean passing,
            int energyDelta
    ) {
    }

    private record SensorKey(
            @Nonnull String worldIdentity,
            int x,
            int y,
            int z
    ) {
        private static SensorKey from(
                World world,
                Vector3i position
        ) {
            return new SensorKey(
                    world.getName(),
                    position.x(),
                    position.y(),
                    position.z()
            );
        }
    }
}
