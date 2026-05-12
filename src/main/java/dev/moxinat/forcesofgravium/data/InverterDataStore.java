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

public final class InverterDataStore {

    private static final Map<BlockKey, InverterData> DATA = new ConcurrentHashMap<>();

    private InverterDataStore() {
    }

    public static void putDefault(@Nonnull World world, @Nonnull Vector3i position) {
        put(world, position, InverterData.defaultData());
    }

    public static void put(@Nonnull World world, @Nonnull Vector3i position, @Nonnull InverterData data) {
        WorldSaveFileService.ensureLoaded(world);
        DATA.put(BlockKey.from(world, position), data);
        WorldSaveFileService.markDirty(world);
    }

    public static @Nullable InverterData get(@Nonnull World world, @Nonnull Vector3i position) {
        WorldSaveFileService.ensureLoaded(world);
        return DATA.get(BlockKey.from(world, position));
    }

    public static @Nonnull InverterData getOrCreate(@Nonnull World world, @Nonnull Vector3i position) {
        WorldSaveFileService.ensureLoaded(world);
        return DATA.computeIfAbsent(BlockKey.from(world, position), ignored -> InverterData.defaultData());
    }

    public static void setState(
            @Nonnull World world,
            @Nonnull Vector3i position,
            @Nonnull String currentMode,
            @Nonnull String nextMode,
            boolean invertEnabled,
            boolean toggleInputActive
    ) {
        WorldSaveFileService.ensureLoaded(world);
        DATA.put(
                BlockKey.from(world, position),
                InverterData.transition(
                        DATA.get(BlockKey.from(world, position)),
                        currentMode,
                        nextMode,
                        invertEnabled,
                        toggleInputActive
                )
        );
        WorldSaveFileService.markDirty(world);
    }

    public static void adoptCurrentMode(@Nonnull World world, @Nonnull Vector3i position) {
        WorldSaveFileService.ensureLoaded(world);
        DATA.computeIfPresent(BlockKey.from(world, position), (ignored, existing) -> existing.withWaveStateFromCurrentMode());
        WorldSaveFileService.markDirty(world);
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

    public static @Nonnull Map<Vector3i, InverterData> snapshotForWorld(@Nonnull World world) {
        WorldSaveFileService.ensureLoaded(world);
        String worldId = world.getName();
        return DATA.entrySet().stream()
                .filter(entry -> entry.getKey().worldId().equals(worldId))
                .collect(Collectors.toMap(
                        entry -> new Vector3i(entry.getKey().x(), entry.getKey().y(), entry.getKey().z()),
                        Map.Entry::getValue
                ));
    }

    public record InverterData(
            @Nonnull String currentMode,
            @Nonnull String nextMode,
            boolean invertEnabled,
            boolean toggleInputActive,
            @Nonnull StateTimeline stateTimeline
    ) {

        public InverterData {
            currentMode = Objects.requireNonNull(currentMode, "currentMode");
            nextMode = Objects.requireNonNull(nextMode, "nextMode");
            stateTimeline = Objects.requireNonNull(stateTimeline, "stateTimeline");
            stateTimeline = new StateTimeline(currentMode, stateTimeline.waveState(), stateTimeline.previousState());
        }

        public static @Nonnull InverterData defaultData() {
            return initialize("off", "off", true, false);
        }

        public static @Nonnull InverterData initialize(
                @Nonnull String currentMode,
                @Nonnull String nextMode,
                boolean invertEnabled,
                boolean toggleInputActive
        ) {
            return new InverterData(
                    currentMode,
                    nextMode,
                    invertEnabled,
                    toggleInputActive,
                    StateTimeline.initialized(currentMode)
            );
        }

        public static @Nonnull InverterData transition(
                @Nullable InverterData existing,
                @Nonnull String currentMode,
                @Nonnull String nextMode,
                boolean invertEnabled,
                boolean toggleInputActive
        ) {
            String previousState = existing == null || currentMode.equals(existing.currentMode())
                    ? (existing == null ? currentMode : existing.previousState())
                    : existing.currentMode();
            return new InverterData(
                    currentMode,
                    nextMode,
                    invertEnabled,
                    toggleInputActive,
                    new StateTimeline(currentMode, currentMode, previousState)
            );
        }

        public @Nonnull InverterData withWaveStateFromCurrentMode() {
            return new InverterData(
                    currentMode,
                    nextMode,
                    invertEnabled,
                    toggleInputActive,
                    StateTimeline.initialized(currentMode)
            );
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
            return new BlockKey(world.getName(), position.getX(), position.getY(), position.getZ());
        }
    }
}
