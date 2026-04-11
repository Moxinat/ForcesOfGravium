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

    public static void setCurrentMode(@Nonnull World world, @Nonnull Vector3i position, @Nonnull String currentMode) {
        WorldSaveFileService.ensureLoaded(world);
        DATA.compute(BlockKey.from(world, position), (ignored, existing) -> {
            InverterData data = existing == null ? InverterData.defaultData() : existing;
            return data.withCurrentMode(currentMode);
        });
        WorldSaveFileService.markDirty(world);
    }

    public static void setNextMode(@Nonnull World world, @Nonnull Vector3i position, @Nonnull String nextMode) {
        WorldSaveFileService.ensureLoaded(world);
        DATA.compute(BlockKey.from(world, position), (ignored, existing) -> {
            InverterData data = existing == null ? InverterData.defaultData() : existing;
            return data.withNextMode(nextMode);
        });
        WorldSaveFileService.markDirty(world);
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
                new InverterData(currentMode, nextMode, invertEnabled, toggleInputActive)
        );
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
            boolean toggleInputActive
    ) {

        public InverterData {
            currentMode = Objects.requireNonNull(currentMode, "currentMode");
            nextMode = Objects.requireNonNull(nextMode, "nextMode");
        }

        public static @Nonnull InverterData defaultData() {
            return new InverterData("off", "off", true, false);
        }

        public @Nonnull InverterData withCurrentMode(@Nonnull String value) {
            return new InverterData(Objects.requireNonNull(value, "currentMode"), nextMode, invertEnabled, toggleInputActive);
        }

        public @Nonnull InverterData withNextMode(@Nonnull String value) {
            return new InverterData(currentMode, Objects.requireNonNull(value, "nextMode"), invertEnabled, toggleInputActive);
        }

        public @Nonnull InverterData withInvertEnabled(boolean value) {
            return new InverterData(currentMode, nextMode, value, toggleInputActive);
        }

        public @Nonnull InverterData withToggleInputActive(boolean value) {
            return new InverterData(currentMode, nextMode, invertEnabled, value);
        }
    }

    private record BlockKey(@Nonnull String worldId, int x, int y, int z) {

        private static @Nonnull BlockKey from(@Nonnull World world, @Nonnull Vector3i position) {
            return new BlockKey(world.getName(), position.getX(), position.getY(), position.getZ());
        }
    }
}
