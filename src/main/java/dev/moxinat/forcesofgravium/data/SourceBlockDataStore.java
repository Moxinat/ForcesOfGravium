package dev.moxinat.forcesofgravium.data;

import org.joml.Vector3i;
import com.hypixel.hytale.server.core.universe.world.World;
import dev.moxinat.forcesofgravium.persistence.WorldSaveFileService;
import dev.moxinat.forcesofgravium.registry.ConnectableRegistry;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public final class SourceBlockDataStore {

    private static final Map<BlockKey, SourceBlockData> DATA = new ConcurrentHashMap<>();

    private SourceBlockDataStore() {
    }

    public static void putDefault(@Nonnull World world, @Nonnull Vector3i position, @Nullable String blockId) {
        put(world, position, new SourceBlockData(defaultActiveForBlockId(blockId)));
    }

    public static void put(@Nonnull World world, @Nonnull Vector3i position, @Nonnull SourceBlockData data) {
        WorldSaveFileService.ensureLoaded(world);
        DATA.put(BlockKey.from(world, position), data);
        WorldSaveFileService.markDirty(world);
    }

    public static boolean isActive(@Nonnull World world, @Nonnull Vector3i position, @Nullable String blockId) {
        WorldSaveFileService.ensureLoaded(world);
        SourceBlockData data = DATA.get(BlockKey.from(world, position));
        return data == null ? defaultActiveForBlockId(blockId) : data.active();
    }

    public static void setActive(@Nonnull World world, @Nonnull Vector3i position, boolean active) {
        WorldSaveFileService.ensureLoaded(world);
        DATA.put(BlockKey.from(world, position), new SourceBlockData(active));
        WorldSaveFileService.markDirty(world);
    }

    public static void remove(@Nonnull World world, @Nonnull Vector3i position) {
        WorldSaveFileService.ensureLoaded(world);
        if (DATA.remove(BlockKey.from(world, position)) != null) {
            WorldSaveFileService.markDirty(world);
        }
    }

    public static @Nonnull Map<Vector3i, SourceBlockData> snapshotForWorld(@Nonnull World world) {
        WorldSaveFileService.ensureLoaded(world);
        String worldId = world.getName();
        return DATA.entrySet().stream()
                .filter(entry -> entry.getKey().worldId().equals(worldId))
                .collect(Collectors.toMap(
                        entry -> new Vector3i(entry.getKey().x(), entry.getKey().y(), entry.getKey().z()),
                        Map.Entry::getValue
                ));
    }

    public static void clearWorld(@Nonnull World world) {
        String worldId = world.getName();
        DATA.keySet().removeIf(key -> key.worldId().equals(worldId));
    }

    public static boolean defaultActiveForBlockId(@Nullable String blockId) {
        return ConnectableRegistry.WIND_GENERATOR_BLOCK_ID.equals(blockId);
    }

    public record SourceBlockData(boolean active) {
    }

    private record BlockKey(@Nonnull String worldId, int x, int y, int z) {

        private static @Nonnull BlockKey from(@Nonnull World world, @Nonnull Vector3i position) {
            return new BlockKey(world.getName(), position.x(), position.y(), position.z());
        }
    }
}
