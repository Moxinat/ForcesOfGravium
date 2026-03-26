package dev.moxinat.forcesofgravium.data;

import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.universe.world.World;
import dev.moxinat.forcesofgravium.persistence.WorldSaveFileService;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public final class GravityPowderBlockDataStore {

    public static final String WAVE_NONE = "none";
    public static final String WAVE_OFF = "off_wave";
    public static final String WAVE_PULL = "pull_wave";

    private static final Map<BlockKey, GravityPowderBlockData> DATA = new ConcurrentHashMap<>();

    private GravityPowderBlockDataStore() {
    }

    public static void putDefault(@Nonnull World world, @Nonnull Vector3i position) {
        put(world, position, GravityPowderBlockData.defaultData());
    }

    public static void put(@Nonnull World world, @Nonnull Vector3i position, @Nonnull GravityPowderBlockData data) {
        WorldSaveFileService.ensureLoaded(world);
        DATA.put(BlockKey.from(world, position), data);
        WorldSaveFileService.markDirty(world);
    }

    public static @Nullable GravityPowderBlockData get(@Nonnull World world, @Nonnull Vector3i position) {
        WorldSaveFileService.ensureLoaded(world);
        return DATA.get(BlockKey.from(world, position));
    }

    public static boolean has(@Nonnull World world, @Nonnull Vector3i position) {
        WorldSaveFileService.ensureLoaded(world);
        return DATA.containsKey(BlockKey.from(world, position));
    }

    public static @Nonnull GravityPowderBlockData getOrCreate(@Nonnull World world, @Nonnull Vector3i position) {
        WorldSaveFileService.ensureLoaded(world);
        return DATA.computeIfAbsent(BlockKey.from(world, position), ignored -> GravityPowderBlockData.defaultData());
    }

    public static void setConnectionsMask(@Nonnull World world, @Nonnull Vector3i position, int connectionsMask) {
        WorldSaveFileService.ensureLoaded(world);
        DATA.compute(BlockKey.from(world, position), (ignored, existing) -> {
            GravityPowderBlockData data = existing == null ? GravityPowderBlockData.defaultData() : existing;
            return data.withConnectionsMask(connectionsMask);
        });
        WorldSaveFileService.markDirty(world);
    }

    public static void setCurrentMode(@Nonnull World world, @Nonnull Vector3i position, @Nonnull String currentMode) {
        WorldSaveFileService.ensureLoaded(world);
        DATA.compute(BlockKey.from(world, position), (ignored, existing) -> {
            GravityPowderBlockData data = existing == null ? GravityPowderBlockData.defaultData() : existing;
            return data.withCurrentMode(currentMode);
        });
        WorldSaveFileService.markDirty(world);
    }

    public static void setNextMode(@Nonnull World world, @Nonnull Vector3i position, @Nonnull String nextMode) {
        WorldSaveFileService.ensureLoaded(world);
        DATA.compute(BlockKey.from(world, position), (ignored, existing) -> {
            GravityPowderBlockData data = existing == null ? GravityPowderBlockData.defaultData() : existing;
            return data.withNextMode(nextMode);
        });
        WorldSaveFileService.markDirty(world);
    }

    public static void setDecayMark(@Nonnull World world, @Nonnull Vector3i position, @Nonnull String decayMark) {
        WorldSaveFileService.ensureLoaded(world);
        DATA.compute(BlockKey.from(world, position), (ignored, existing) -> {
            GravityPowderBlockData data = existing == null ? GravityPowderBlockData.defaultData() : existing;
            return data.withDecayMark(decayMark);
        });
        WorldSaveFileService.markDirty(world);
    }

    public static void setDecayLockTicks(@Nonnull World world, @Nonnull Vector3i position, int decayLockTicks) {
        WorldSaveFileService.ensureLoaded(world);
        DATA.compute(BlockKey.from(world, position), (ignored, existing) -> {
            GravityPowderBlockData data = existing == null ? GravityPowderBlockData.defaultData() : existing;
            return data.withDecayLockTicks(decayLockTicks);
        });
        WorldSaveFileService.markDirty(world);
    }

    public static void remove(@Nonnull World world, @Nonnull Vector3i position) {
        WorldSaveFileService.ensureLoaded(world);
        DATA.remove(BlockKey.from(world, position));
        WorldSaveFileService.markDirty(world);
    }

    public static int size() {
        return DATA.size();
    }

    public static @Nonnull Map<Vector3i, GravityPowderBlockData> snapshotForWorld(@Nonnull World world) {
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

    public record GravityPowderBlockData(
            int connectionsMask,
            @Nonnull String currentMode,
            @Nonnull String nextMode,
            @Nonnull String decayMark,
            int decayLockTicks
    ) {

        public GravityPowderBlockData {
            currentMode = Objects.requireNonNull(currentMode, "currentMode");
            nextMode = Objects.requireNonNull(nextMode, "nextMode");
            decayMark = Objects.requireNonNull(decayMark, "decayMark");
        }

        public static @Nonnull GravityPowderBlockData defaultData() {
            return new GravityPowderBlockData(0, "off", "off", WAVE_NONE, 0);
        }

        public @Nonnull GravityPowderBlockData withConnectionsMask(int value) {
            return new GravityPowderBlockData(value, currentMode, nextMode, decayMark, decayLockTicks);
        }

        public @Nonnull GravityPowderBlockData withCurrentMode(@Nonnull String value) {
            return new GravityPowderBlockData(connectionsMask, Objects.requireNonNull(value, "currentMode"), nextMode, decayMark, decayLockTicks);
        }

        public @Nonnull GravityPowderBlockData withNextMode(@Nonnull String value) {
            return new GravityPowderBlockData(connectionsMask, currentMode, Objects.requireNonNull(value, "nextMode"), decayMark, decayLockTicks);
        }

        public @Nonnull GravityPowderBlockData withDecayMark(@Nonnull String value) {
            return new GravityPowderBlockData(connectionsMask, currentMode, nextMode, Objects.requireNonNull(value, "decayMark"), decayLockTicks);
        }

        public @Nonnull GravityPowderBlockData withDecayLockTicks(int value) {
            return new GravityPowderBlockData(connectionsMask, currentMode, nextMode, decayMark, value);
        }

        public boolean hasWave() {
            return WAVE_OFF.equals(decayMark) || WAVE_PULL.equals(decayMark);
        }
    }

    private record BlockKey(@Nonnull String worldId, int x, int y, int z) {

        private static @Nonnull BlockKey from(@Nonnull World world, @Nonnull Vector3i position) {
            return new BlockKey(world.getName(), position.getX(), position.getY(), position.getZ());
        }
    }
}
