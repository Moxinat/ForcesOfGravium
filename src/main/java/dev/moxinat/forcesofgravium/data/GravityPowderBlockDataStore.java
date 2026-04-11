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

    public static final String STATE_OFF = "off";
    public static final String STATE_PUSH = "push";
    public static final String STATE_PULL = "pull";

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

    public static void setSignals(@Nonnull World world, @Nonnull Vector3i position, boolean push, boolean pull) {
        WorldSaveFileService.ensureLoaded(world);
        DATA.compute(BlockKey.from(world, position), (ignored, existing) -> {
            GravityPowderBlockData data = existing == null ? GravityPowderBlockData.defaultData() : existing;
            return data.withSignals(push, pull);
        });
        WorldSaveFileService.markDirty(world);
    }

    public static void remove(@Nonnull World world, @Nonnull Vector3i position) {
        WorldSaveFileService.ensureLoaded(world);
        DATA.remove(BlockKey.from(world, position));
        WorldSaveFileService.markDirty(world);
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

    public static @Nonnull String modeForState(@Nullable String state) {
        if (STATE_PUSH.equals(state) || "push_wave".equals(state)) {
            return STATE_PUSH;
        }
        if (STATE_PULL.equals(state) || "pull_wave".equals(state)) {
            return STATE_PULL;
        }
        return STATE_OFF;
    }

    public static @Nonnull String stableStateForMode(@Nullable String mode) {
        if (STATE_PUSH.equals(mode)) {
            return STATE_PUSH;
        }
        if (STATE_PULL.equals(mode)) {
            return STATE_PULL;
        }
        return STATE_OFF;
    }

    public static @Nonnull String effectiveMode(@Nonnull GravityPowderBlockData data) {
        if (data.push()) {
            return STATE_PUSH;
        }
        if (data.pull()) {
            return STATE_PULL;
        }
        return STATE_OFF;
    }

    public static @Nonnull GravityPowderBlockData fromLegacyData(
            int connectionsMask,
            @Nonnull String currentMode,
            @Nonnull String nextMode,
            @Nonnull String decayMark,
            int decayLockTicks
    ) {
        Objects.requireNonNull(currentMode, "currentMode");
        Objects.requireNonNull(nextMode, "nextMode");
        Objects.requireNonNull(decayMark, "decayMark");

        if (!"none".equals(decayMark)) {
            return fromState(connectionsMask, decayMark);
        }
        return fromState(connectionsMask, currentMode);
    }

    public static @Nonnull GravityPowderBlockData fromState(int connectionsMask, @Nullable String state) {
        String mode = modeForState(state);
        return new GravityPowderBlockData(
                connectionsMask,
                STATE_PUSH.equals(mode),
                STATE_PULL.equals(mode)
        );
    }

    public record GravityPowderBlockData(
            int connectionsMask,
            boolean push,
            boolean pull
    ) {

        public GravityPowderBlockData {
        }

        public static @Nonnull GravityPowderBlockData defaultData() {
            return new GravityPowderBlockData(0, false, false);
        }

        public @Nonnull GravityPowderBlockData withConnectionsMask(int value) {
            return new GravityPowderBlockData(value, push, pull);
        }

        public @Nonnull GravityPowderBlockData withSignals(boolean nextPush, boolean nextPull) {
            return new GravityPowderBlockData(connectionsMask, nextPush, nextPull);
        }
    }

    private record BlockKey(@Nonnull String worldId, int x, int y, int z) {

        private static @Nonnull BlockKey from(@Nonnull World world, @Nonnull Vector3i position) {
            return new BlockKey(world.getName(), position.getX(), position.getY(), position.getZ());
        }
    }
}
