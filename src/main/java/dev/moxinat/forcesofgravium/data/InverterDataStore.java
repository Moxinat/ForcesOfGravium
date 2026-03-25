package dev.moxinat.forcesofgravium.data;

import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.universe.world.World;
import dev.moxinat.forcesofgravium.data.GravityPowderBlockDataStore.PositionDistance;
import dev.moxinat.forcesofgravium.persistence.WorldSaveFileService;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
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

    public static void setStable(@Nonnull World world, @Nonnull Vector3i position, boolean stable) {
        WorldSaveFileService.ensureLoaded(world);
        DATA.compute(BlockKey.from(world, position), (ignored, existing) -> {
            InverterData data = existing == null ? InverterData.defaultData() : existing;
            return data.withStable(stable);
        });
        WorldSaveFileService.markDirty(world);
    }

    public static void setNextStable(@Nonnull World world, @Nonnull Vector3i position, boolean nextStable) {
        WorldSaveFileService.ensureLoaded(world);
        DATA.compute(BlockKey.from(world, position), (ignored, existing) -> {
            InverterData data = existing == null ? InverterData.defaultData() : existing;
            return data.withNextStable(nextStable);
        });
        WorldSaveFileService.markDirty(world);
    }

    public static void setPositionDistances(@Nonnull World world, @Nonnull Vector3i position, @Nonnull List<PositionDistance> positionDistances) {
        WorldSaveFileService.ensureLoaded(world);
        DATA.compute(BlockKey.from(world, position), (ignored, existing) -> {
            InverterData data = existing == null ? InverterData.defaultData() : existing;
            return data.withPositionDistances(positionDistances);
        });
        WorldSaveFileService.markDirty(world);
    }

    public static void setNextPositionDistances(@Nonnull World world, @Nonnull Vector3i position, @Nonnull List<PositionDistance> nextPositionDistances) {
        WorldSaveFileService.ensureLoaded(world);
        DATA.compute(BlockKey.from(world, position), (ignored, existing) -> {
            InverterData data = existing == null ? InverterData.defaultData() : existing;
            return data.withNextPositionDistances(nextPositionDistances);
        });
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
            boolean stable,
            boolean nextStable,
            @Nonnull List<PositionDistance> positionDistances,
            @Nonnull List<PositionDistance> nextPositionDistances
    ) {

        public InverterData {
            currentMode = Objects.requireNonNull(currentMode, "currentMode");
            nextMode = Objects.requireNonNull(nextMode, "nextMode");
            positionDistances = List.copyOf(Objects.requireNonNull(positionDistances, "positionDistances"));
            nextPositionDistances = List.copyOf(Objects.requireNonNull(nextPositionDistances, "nextPositionDistances"));
        }

        public static @Nonnull InverterData defaultData() {
            return new InverterData("off", "off", false, false, List.of(), List.of());
        }

        public @Nonnull InverterData withCurrentMode(@Nonnull String value) {
            return new InverterData(Objects.requireNonNull(value, "currentMode"), nextMode, stable, nextStable, positionDistances, nextPositionDistances);
        }

        public @Nonnull InverterData withNextMode(@Nonnull String value) {
            return new InverterData(currentMode, Objects.requireNonNull(value, "nextMode"), stable, nextStable, positionDistances, nextPositionDistances);
        }

        public @Nonnull InverterData withStable(boolean value) {
            return new InverterData(currentMode, nextMode, value, nextStable, positionDistances, nextPositionDistances);
        }

        public @Nonnull InverterData withNextStable(boolean value) {
            return new InverterData(currentMode, nextMode, stable, value, positionDistances, nextPositionDistances);
        }

        public @Nonnull InverterData withPositionDistances(@Nonnull List<PositionDistance> value) {
            return new InverterData(currentMode, nextMode, stable, nextStable, value, nextPositionDistances);
        }

        public @Nonnull InverterData withNextPositionDistances(@Nonnull List<PositionDistance> value) {
            return new InverterData(currentMode, nextMode, stable, nextStable, positionDistances, value);
        }
    }

    private record BlockKey(@Nonnull String worldId, int x, int y, int z) {

        private static @Nonnull BlockKey from(@Nonnull World world, @Nonnull Vector3i position) {
            return new BlockKey(world.getName(), position.getX(), position.getY(), position.getZ());
        }
    }
}
