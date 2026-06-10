package dev.moxinat.forcesofgravium.block.gravity;

import dev.moxinat.forcesofgravium.data.StateTimeline;

import org.joml.Vector3i;
import com.hypixel.hytale.server.core.universe.world.World;
import dev.moxinat.forcesofgravium.persistence.WorldSaveFileService;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public final class GravityPowderSpecialStateStore {

    // Owns gravity-powder-specific shape state such as connectionsMask.
    // Timeline fields here are compatibility mirrors; ConnectableRuntimeData is authoritative.
    public static final String STATE_OFF = "off";
    public static final String STATE_PUSH = "push";
    public static final String STATE_PULL = "pull";

    private static final Map<BlockKey, GravityPowderBlockData> DATA = new ConcurrentHashMap<>();

    private GravityPowderSpecialStateStore() {
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

    public static void setInstantState(@Nonnull World world, @Nonnull Vector3i position, @Nonnull String state) {
        WorldSaveFileService.ensureLoaded(world);
        DATA.compute(BlockKey.from(world, position), (ignored, existing) -> {
            GravityPowderBlockData data = existing == null ? GravityPowderBlockData.defaultData() : existing;
            return data.withInstantState(state);
        });
        WorldSaveFileService.markDirty(world);
    }

    public static void adoptInstantState(@Nonnull World world, @Nonnull Vector3i position) {
        WorldSaveFileService.ensureLoaded(world);
        DATA.computeIfPresent(BlockKey.from(world, position), (ignored, existing) -> existing.withWaveStateFromInstantState());
        WorldSaveFileService.markDirty(world);
    }

    public static void markWaveDirty(@Nonnull World world, @Nonnull Vector3i position) {
        WorldSaveFileService.ensureLoaded(world);
        DATA.computeIfPresent(BlockKey.from(world, position),
                (ignored, existing) -> existing.withDirty(true));
        WorldSaveFileService.markDirty(world);
    }

    public static void clearWaveDirty(@Nonnull World world, @Nonnull Vector3i position) {
        WorldSaveFileService.ensureLoaded(world);
        DATA.computeIfPresent(BlockKey.from(world, position),
                (ignored, existing) -> existing.withDirty(false));
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

    public static @Nonnull String normalizeState(@Nullable String state) {
        if (STATE_PUSH.equals(state) || "push_wave".equals(state)) {
            return STATE_PUSH;
        }
        if (STATE_PULL.equals(state) || "pull_wave".equals(state)) {
            return STATE_PULL;
        }
        return STATE_OFF;
    }

    public static @Nonnull GravityPowderBlockData fromLegacyData(
            int connectionsMask,
            @Nonnull String currentMode,
            @Nonnull String decayMark
    ) {
        Objects.requireNonNull(currentMode, "currentMode");
        Objects.requireNonNull(decayMark, "decayMark");

        if (!"none".equals(decayMark)) {
            return fromState(connectionsMask, decayMark);
        }
        return fromState(connectionsMask, currentMode);
    }

    public static @Nonnull GravityPowderBlockData fromState(int connectionsMask, @Nullable String state) {
        return GravityPowderBlockData.initialize(connectionsMask, normalizeState(state));
    }

    public record GravityPowderBlockData(
            int connectionsMask,
            @Nonnull StateTimeline stateTimeline,
            boolean dirty
    ) {
        public GravityPowderBlockData(int connectionsMask, @Nonnull StateTimeline stateTimeline) {
            this(connectionsMask, stateTimeline, false);
        }

        public GravityPowderBlockData {
            stateTimeline = Objects.requireNonNull(stateTimeline, "stateTimeline");
            stateTimeline = new StateTimeline(
                    normalizeState(stateTimeline.instantState()),
                    normalizeState(stateTimeline.waveState()),
                    normalizeState(stateTimeline.previousState())
            );
        }

        public static @Nonnull GravityPowderBlockData defaultData() {
            return initialize(0, STATE_OFF);
        }

        public static @Nonnull GravityPowderBlockData initialize(
                int connectionsMask,
                @Nonnull String state
        ) {
            return new GravityPowderBlockData(
                    connectionsMask,
                    StateTimeline.initialized(normalizeState(state)),
                    false
            );
        }

        public @Nonnull GravityPowderBlockData withConnectionsMask(int value) {
            return new GravityPowderBlockData(value, stateTimeline, dirty);
        }

        public @Nonnull GravityPowderBlockData withInstantState(@Nonnull String nextInstantState) {
            return new GravityPowderBlockData(
                    connectionsMask,
                    stateTimeline.withInstantState(normalizeState(nextInstantState)),
                    dirty
            );
        }

        public boolean hasWaveMismatch() {
            return stateTimeline.hasWaveMismatch();
        }

        public @Nonnull GravityPowderBlockData withWaveStateFromInstantState() {
            return new GravityPowderBlockData(
                    connectionsMask,
                    stateTimeline.withWaveStateFromInstantState(),
                    false
            );
        }

        public @Nonnull GravityPowderBlockData withDirty(boolean value) {
            return new GravityPowderBlockData(connectionsMask, stateTimeline, value);
        }

        public @Nonnull String instantState() {
            return stateTimeline.instantState();
        }

        public @Nonnull String waveState() {
            return stateTimeline.waveState();
        }

        public @Nonnull String effectiveState() {
            return stateTimeline.effectiveState();
        }

        public @Nonnull String previousState() {
            return stateTimeline.previousState();
        }
    }

    private record BlockKey(@Nonnull String worldId, int x, int y, int z) {

        private static @Nonnull BlockKey from(@Nonnull World world, @Nonnull Vector3i position) {
            return new BlockKey(world.getName(), position.x(), position.y(), position.z());
        }
    }
}
