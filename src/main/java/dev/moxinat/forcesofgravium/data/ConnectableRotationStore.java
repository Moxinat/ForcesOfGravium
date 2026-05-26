package dev.moxinat.forcesofgravium.data;

import org.joml.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import com.hypixel.hytale.server.core.universe.world.World;
import dev.moxinat.forcesofgravium.persistence.WorldSaveFileService;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public final class ConnectableRotationStore {

    private static final Map<BlockKey, RotationTuple> DATA = new ConcurrentHashMap<>();

    private ConnectableRotationStore() {
    }

    public static void put(@Nonnull World world, @Nonnull Vector3i position, @Nonnull RotationTuple rotation) {
        WorldSaveFileService.ensureLoaded(world);
        DATA.put(BlockKey.from(world, position), rotation);
        WorldSaveFileService.markDirty(world);
    }

    public static @Nonnull RotationTuple getOrDefault(@Nonnull World world, @Nonnull Vector3i position, @Nonnull RotationTuple fallback) {
        WorldSaveFileService.ensureLoaded(world);
        return DATA.getOrDefault(BlockKey.from(world, position), fallback);
    }

    public static @Nullable RotationTuple get(@Nonnull World world, @Nonnull Vector3i position) {
        WorldSaveFileService.ensureLoaded(world);
        return DATA.get(BlockKey.from(world, position));
    }

    public static void remove(@Nonnull World world, @Nonnull Vector3i position) {
        WorldSaveFileService.ensureLoaded(world);
        DATA.remove(BlockKey.from(world, position));
        WorldSaveFileService.markDirty(world);
    }

    public static void clearWorld(@Nonnull World world) {
        String worldId = world.getName();
        DATA.keySet().removeIf(key -> key.worldId().equals(worldId));
    }

    public static @Nonnull Map<Vector3i, RotationTuple> snapshotForWorld(@Nonnull World world) {
        WorldSaveFileService.ensureLoaded(world);
        String worldId = world.getName();
        return DATA.entrySet().stream()
                .filter(entry -> entry.getKey().worldId().equals(worldId))
                .collect(Collectors.toMap(
                        entry -> new Vector3i(entry.getKey().x(), entry.getKey().y(), entry.getKey().z()),
                        Map.Entry::getValue
                ));
    }

    private record BlockKey(@Nonnull String worldId, int x, int y, int z) {

        private static @Nonnull BlockKey from(@Nonnull World world, @Nonnull Vector3i position) {
            return new BlockKey(world.getName(), position.x(), position.y(), position.z());
        }
    }
}
