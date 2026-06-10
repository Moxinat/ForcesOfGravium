package dev.moxinat.forcesofgravium.connectable.core;

import com.hypixel.hytale.server.core.universe.world.World;
import org.joml.Vector3i;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public final class ConnectableDataStore {

    private static final Map<ConnectableRuntimeKey, ConnectableRuntimeData> DATA = new ConcurrentHashMap<>();

    private ConnectableDataStore() {
    }

    public static void put(@Nonnull World world, @Nonnull Vector3i position, @Nonnull ConnectableRuntimeData data) {
        DATA.put(ConnectableRuntimeKey.from(world, position), data);
    }

    public static @Nullable ConnectableRuntimeData get(@Nonnull World world, @Nonnull Vector3i position) {
        return DATA.get(ConnectableRuntimeKey.from(world, position));
    }

    public static void remove(@Nonnull World world, @Nonnull Vector3i position) {
        DATA.remove(ConnectableRuntimeKey.from(world, position));
    }

    public static void clearWorld(@Nonnull World world) {
        String worldIdentity = world.getName();
        DATA.keySet().removeIf(key -> key.worldIdentity().equals(worldIdentity));
    }

    public static @Nonnull Map<Vector3i, ConnectableRuntimeData> snapshotForWorld(@Nonnull World world) {
        String worldIdentity = world.getName();
        return DATA.entrySet().stream()
                .filter(entry -> entry.getKey().worldIdentity().equals(worldIdentity))
                .collect(Collectors.toMap(
                        entry -> entry.getKey().position(),
                        Map.Entry::getValue
                ));
    }
}
