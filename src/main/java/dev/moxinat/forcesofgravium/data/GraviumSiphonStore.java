package dev.moxinat.forcesofgravium.data;

import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.universe.world.World;
import dev.moxinat.forcesofgravium.persistence.WorldSaveFileService;

import javax.annotation.Nonnull;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public final class GraviumSiphonStore {

    private static final Set<BlockKey> POSITIONS = ConcurrentHashMap.newKeySet();

    private GraviumSiphonStore() {
    }

    public static void add(@Nonnull World world, @Nonnull Vector3i position) {
        WorldSaveFileService.ensureLoaded(world);
        if (POSITIONS.add(BlockKey.from(world, position))) {
            WorldSaveFileService.markDirty(world);
        }
    }

    public static void remove(@Nonnull World world, @Nonnull Vector3i position) {
        WorldSaveFileService.ensureLoaded(world);
        if (POSITIONS.remove(BlockKey.from(world, position))) {
            WorldSaveFileService.markDirty(world);
        }
    }

    public static @Nonnull Set<Vector3i> snapshotForWorld(@Nonnull World world) {
        WorldSaveFileService.ensureLoaded(world);
        String worldId = world.getName();
        return POSITIONS.stream()
                .filter(key -> key.worldId().equals(worldId))
                .map(key -> new Vector3i(key.x(), key.y(), key.z()))
                .collect(Collectors.toUnmodifiableSet());
    }

    public static void retainExisting(@Nonnull World world, @Nonnull Set<Vector3i> positions) {
        WorldSaveFileService.ensureLoaded(world);
        String worldId = world.getName();
        boolean removed = POSITIONS.removeIf(key -> key.worldId().equals(worldId)
                && !positions.contains(new Vector3i(key.x(), key.y(), key.z())));
        if (removed) {
            WorldSaveFileService.markDirty(world);
        }
    }

    public static void clearWorld(@Nonnull World world) {
        String worldId = world.getName();
        POSITIONS.removeIf(key -> key.worldId().equals(worldId));
    }

    private record BlockKey(@Nonnull String worldId, int x, int y, int z) {

        private static @Nonnull BlockKey from(@Nonnull World world, @Nonnull Vector3i position) {
            return new BlockKey(world.getName(), position.getX(), position.getY(), position.getZ());
        }
    }
}
