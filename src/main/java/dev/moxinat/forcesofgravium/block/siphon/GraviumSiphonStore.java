package dev.moxinat.forcesofgravium.block.siphon;

import org.joml.Vector3i;
import com.hypixel.hytale.server.core.universe.world.World;
import dev.moxinat.forcesofgravium.persistence.WorldSaveFileService;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public final class GraviumSiphonStore {

    private static final Map<BlockKey, GraviumSiphonData> DATA = new ConcurrentHashMap<>();

    private GraviumSiphonStore() {
    }

    public static void add(@Nonnull World world, @Nonnull Vector3i position) {
        putIfAbsent(world, position, GraviumSiphonData.defaultData());
    }

    public static void putIfAbsent(@Nonnull World world, @Nonnull Vector3i position, @Nonnull GraviumSiphonData data) {
        WorldSaveFileService.ensureLoaded(world);
        GraviumSiphonData previous = DATA.putIfAbsent(BlockKey.from(world, position), data);
        if (previous == null) {
            WorldSaveFileService.markDirty(world);
        }
    }

    public static void remove(@Nonnull World world, @Nonnull Vector3i position) {
        WorldSaveFileService.ensureLoaded(world);
        if (DATA.remove(BlockKey.from(world, position)) != null) {
            WorldSaveFileService.markDirty(world);
        }
    }

    public static @Nullable GraviumSiphonData get(@Nonnull World world, @Nonnull Vector3i position) {
        WorldSaveFileService.ensureLoaded(world);
        return DATA.get(BlockKey.from(world, position));
    }

    public static void setPowered(@Nonnull World world, @Nonnull Vector3i position, boolean powered) {
        WorldSaveFileService.ensureLoaded(world);
        DATA.compute(BlockKey.from(world, position), (ignored, existing) -> {
            GraviumSiphonData data = existing == null ? GraviumSiphonData.defaultData() : existing;
            return data.withPowered(powered);
        });
        WorldSaveFileService.markDirty(world);
    }

    public static void setLocked(@Nonnull World world, @Nonnull Vector3i position, boolean locked) {
        WorldSaveFileService.ensureLoaded(world);
        DATA.compute(BlockKey.from(world, position), (ignored, existing) -> {
            GraviumSiphonData data = existing == null ? GraviumSiphonData.defaultData() : existing;
            return data.withLocked(locked);
        });
        WorldSaveFileService.markDirty(world);
    }

    public static boolean setState(@Nonnull World world, @Nonnull Vector3i position, boolean powered, boolean locked) {
        WorldSaveFileService.ensureLoaded(world);
        BlockKey key = BlockKey.from(world, position);
        GraviumSiphonData next = new GraviumSiphonData(powered, locked);
        GraviumSiphonData previous = DATA.put(key, next);
        if (!next.equals(previous)) {
            WorldSaveFileService.markDirty(world);
            return true;
        }
        return false;
    }

    public static @Nonnull Map<Vector3i, GraviumSiphonData> snapshotForWorld(@Nonnull World world) {
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

    public record GraviumSiphonData(boolean powered, boolean locked) {

        public static @Nonnull GraviumSiphonData defaultData() {
            return new GraviumSiphonData(false, false);
        }

        public @Nonnull GraviumSiphonData withPowered(boolean value) {
            return new GraviumSiphonData(value, locked);
        }

        public @Nonnull GraviumSiphonData withLocked(boolean value) {
            return new GraviumSiphonData(powered, value);
        }
    }

    private record BlockKey(@Nonnull String worldId, int x, int y, int z) {

        private static @Nonnull BlockKey from(@Nonnull World world, @Nonnull Vector3i position) {
            return new BlockKey(world.getName(), position.x(), position.y(), position.z());
        }
    }
}
