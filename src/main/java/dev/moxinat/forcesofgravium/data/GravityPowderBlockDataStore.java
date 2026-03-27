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
    public static final String STATE_OFF_WAVE = "off_wave";
    public static final String STATE_PUSH_WAVE = "push_wave";
    public static final String STATE_PULL_WAVE = "pull_wave";

    // Compatibility aliases while the rest of the codebase and save migration settle.
    public static final String WAVE_NONE = "none";
    public static final String WAVE_OFF = STATE_OFF_WAVE;
    public static final String WAVE_PUSH = STATE_PUSH_WAVE;
    public static final String WAVE_PULL = STATE_PULL_WAVE;

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

    public static void setState(@Nonnull World world, @Nonnull Vector3i position, @Nonnull String state) {
        WorldSaveFileService.ensureLoaded(world);
        DATA.compute(BlockKey.from(world, position), (ignored, existing) -> {
            GravityPowderBlockData data = existing == null ? GravityPowderBlockData.defaultData() : existing;
            return data.withState(state);
        });
        WorldSaveFileService.markDirty(world);
    }

    public static void setStateTicksRemaining(@Nonnull World world, @Nonnull Vector3i position, int ticks) {
        WorldSaveFileService.ensureLoaded(world);
        DATA.compute(BlockKey.from(world, position), (ignored, existing) -> {
            GravityPowderBlockData data = existing == null ? GravityPowderBlockData.defaultData() : existing;
            return data.withStateTicksRemaining(ticks);
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

    public static boolean isWaveState(@Nullable String state) {
        return STATE_OFF_WAVE.equals(state) || STATE_PUSH_WAVE.equals(state) || STATE_PULL_WAVE.equals(state);
    }

    public static boolean hasActiveWave(@Nullable GravityPowderBlockData data) {
        return data != null && isWaveState(data.state());
    }

    public static @Nonnull String modeForState(@Nullable String state) {
        if (STATE_PUSH.equals(state) || STATE_PUSH_WAVE.equals(state)) {
            return STATE_PUSH;
        }
        if (STATE_PULL.equals(state) || STATE_PULL_WAVE.equals(state)) {
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

    public static @Nullable String waveStateForMode(@Nullable String mode) {
        if (STATE_PUSH.equals(mode)) {
            return STATE_PUSH_WAVE;
        }
        if (STATE_PULL.equals(mode)) {
            return STATE_PULL_WAVE;
        }
        if (STATE_OFF.equals(mode)) {
            return STATE_OFF_WAVE;
        }
        return null;
    }

    public static @Nonnull String stableStateFor(@Nullable String state) {
        return stableStateForMode(modeForState(state));
    }

    public static @Nonnull String effectiveMode(@Nonnull GravityPowderBlockData data) {
        return modeForState(data.state());
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

        if (!WAVE_NONE.equals(decayMark)) {
            return new GravityPowderBlockData(connectionsMask, decayMark, decayLockTicks);
        }
        return new GravityPowderBlockData(connectionsMask, stableStateForMode(currentMode), 0);
    }

    public record GravityPowderBlockData(
            int connectionsMask,
            @Nonnull String state,
            int stateTicksRemaining
    ) {

        public GravityPowderBlockData {
            state = Objects.requireNonNull(state, "state");
        }

        public static @Nonnull GravityPowderBlockData defaultData() {
            return new GravityPowderBlockData(0, STATE_OFF, 0);
        }

        public @Nonnull GravityPowderBlockData withConnectionsMask(int value) {
            return new GravityPowderBlockData(value, state, stateTicksRemaining);
        }

        public @Nonnull GravityPowderBlockData withState(@Nonnull String value) {
            return new GravityPowderBlockData(connectionsMask, Objects.requireNonNull(value, "state"), stateTicksRemaining);
        }

        public @Nonnull GravityPowderBlockData withStateTicksRemaining(int value) {
            return new GravityPowderBlockData(connectionsMask, state, value);
        }
    }

    private record BlockKey(@Nonnull String worldId, int x, int y, int z) {

        private static @Nonnull BlockKey from(@Nonnull World world, @Nonnull Vector3i position) {
            return new BlockKey(world.getName(), position.getX(), position.getY(), position.getZ());
        }
    }
}
