package dev.moxinat.forcesofgravium.data;

import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.universe.world.World;
import dev.moxinat.forcesofgravium.persistence.WorldSaveFileService;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public final class GravityPowderBlockDataStore {

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

    public static void setPositionDistances(@Nonnull World world, @Nonnull Vector3i position, @Nonnull List<PositionDistance> positionDistances) {
        WorldSaveFileService.ensureLoaded(world);
        DATA.compute(BlockKey.from(world, position), (ignored, existing) -> {
            GravityPowderBlockData data = existing == null ? GravityPowderBlockData.defaultData() : existing;
            return data.withPositionDistances(positionDistances);
        });
        WorldSaveFileService.markDirty(world);
    }

    public static void setNextPositionDistances(@Nonnull World world, @Nonnull Vector3i position, @Nonnull List<PositionDistance> nextPositionDistances) {
        WorldSaveFileService.ensureLoaded(world);
        DATA.compute(BlockKey.from(world, position), (ignored, existing) -> {
            GravityPowderBlockData data = existing == null ? GravityPowderBlockData.defaultData() : existing;
            return data.withNextPositionDistances(nextPositionDistances);
        });
        WorldSaveFileService.markDirty(world);
    }

    public static void setLossTicks(@Nonnull World world, @Nonnull Vector3i position, int lossTicks) {
        WorldSaveFileService.ensureLoaded(world);
        DATA.compute(BlockKey.from(world, position), (ignored, existing) -> {
            GravityPowderBlockData data = existing == null ? GravityPowderBlockData.defaultData() : existing;
            return data.withLossTicks(lossTicks);
        });
        WorldSaveFileService.markDirty(world);
    }

    public static void setNextLossTicks(@Nonnull World world, @Nonnull Vector3i position, int nextLossTicks) {
        WorldSaveFileService.ensureLoaded(world);
        DATA.compute(BlockKey.from(world, position), (ignored, existing) -> {
            GravityPowderBlockData data = existing == null ? GravityPowderBlockData.defaultData() : existing;
            return data.withNextLossTicks(nextLossTicks);
        });
        WorldSaveFileService.markDirty(world);
    }

    public static void setStable(@Nonnull World world, @Nonnull Vector3i position, boolean stable) {
        WorldSaveFileService.ensureLoaded(world);
        DATA.compute(BlockKey.from(world, position), (ignored, existing) -> {
            GravityPowderBlockData data = existing == null ? GravityPowderBlockData.defaultData() : existing;
            return data.withStable(stable);
        });
        WorldSaveFileService.markDirty(world);
    }

    public static void setNextStable(@Nonnull World world, @Nonnull Vector3i position, boolean nextStable) {
        WorldSaveFileService.ensureLoaded(world);
        DATA.compute(BlockKey.from(world, position), (ignored, existing) -> {
            GravityPowderBlockData data = existing == null ? GravityPowderBlockData.defaultData() : existing;
            return data.withNextStable(nextStable);
        });
        WorldSaveFileService.markDirty(world);
    }

    public static void addPositionDistance(@Nonnull World world, @Nonnull Vector3i position, @Nonnull Vector3i targetPosition, int distance) {
        WorldSaveFileService.ensureLoaded(world);
        DATA.compute(BlockKey.from(world, position), (ignored, existing) -> {
            GravityPowderBlockData data = existing == null ? GravityPowderBlockData.defaultData() : existing;
            return data.withAddedPositionDistance(targetPosition, distance);
        });
        WorldSaveFileService.markDirty(world);
    }

    public static void clearPositionDistances(@Nonnull World world, @Nonnull Vector3i position) {
        WorldSaveFileService.ensureLoaded(world);
        DATA.compute(BlockKey.from(world, position), (ignored, existing) -> {
            GravityPowderBlockData data = existing == null ? GravityPowderBlockData.defaultData() : existing;
            return data.withPositionDistances(List.of());
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
            boolean stable,
            boolean nextStable,
            int lossTicks,
            int nextLossTicks,
            @Nonnull List<PositionDistance> positionDistances,
            @Nonnull List<PositionDistance> nextPositionDistances
    ) {

        public GravityPowderBlockData {
            currentMode = Objects.requireNonNull(currentMode, "currentMode");
            nextMode = Objects.requireNonNull(nextMode, "nextMode");
            positionDistances = List.copyOf(Objects.requireNonNull(positionDistances, "positionDistances"));
            nextPositionDistances = List.copyOf(Objects.requireNonNull(nextPositionDistances, "nextPositionDistances"));
        }

        public static @Nonnull GravityPowderBlockData defaultData() {
            return new GravityPowderBlockData(0, "off", "off", false, false, 0, 0, List.of(), List.of());
        }

        public @Nonnull GravityPowderBlockData withConnectionsMask(int value) {
            return new GravityPowderBlockData(value, currentMode, nextMode, stable, nextStable, lossTicks, nextLossTicks, positionDistances, nextPositionDistances);
        }

        public @Nonnull GravityPowderBlockData withCurrentMode(@Nonnull String value) {
            return new GravityPowderBlockData(connectionsMask, Objects.requireNonNull(value, "currentMode"), nextMode, stable, nextStable, lossTicks, nextLossTicks, positionDistances, nextPositionDistances);
        }

        public @Nonnull GravityPowderBlockData withNextMode(@Nonnull String value) {
            return new GravityPowderBlockData(connectionsMask, currentMode, Objects.requireNonNull(value, "nextMode"), stable, nextStable, lossTicks, nextLossTicks, positionDistances, nextPositionDistances);
        }

        public @Nonnull GravityPowderBlockData withStable(boolean value) {
            return new GravityPowderBlockData(connectionsMask, currentMode, nextMode, value, nextStable, lossTicks, nextLossTicks, positionDistances, nextPositionDistances);
        }

        public @Nonnull GravityPowderBlockData withNextStable(boolean value) {
            return new GravityPowderBlockData(connectionsMask, currentMode, nextMode, stable, value, lossTicks, nextLossTicks, positionDistances, nextPositionDistances);
        }

        public @Nonnull GravityPowderBlockData withLossTicks(int value) {
            return new GravityPowderBlockData(connectionsMask, currentMode, nextMode, stable, nextStable, value, nextLossTicks, positionDistances, nextPositionDistances);
        }

        public @Nonnull GravityPowderBlockData withNextLossTicks(int value) {
            return new GravityPowderBlockData(connectionsMask, currentMode, nextMode, stable, nextStable, lossTicks, value, positionDistances, nextPositionDistances);
        }

        public @Nonnull GravityPowderBlockData withPositionDistances(@Nonnull List<PositionDistance> value) {
            return new GravityPowderBlockData(connectionsMask, currentMode, nextMode, stable, nextStable, lossTicks, nextLossTicks, value, nextPositionDistances);
        }

        public @Nonnull GravityPowderBlockData withNextPositionDistances(@Nonnull List<PositionDistance> value) {
            return new GravityPowderBlockData(connectionsMask, currentMode, nextMode, stable, nextStable, lossTicks, nextLossTicks, positionDistances, value);
        }

        public @Nonnull GravityPowderBlockData withAddedPositionDistance(@Nonnull Vector3i targetPosition, int distance) {
            List<PositionDistance> updated = new java.util.ArrayList<>(positionDistances);
            updated.add(PositionDistance.from(targetPosition, distance));
            return new GravityPowderBlockData(connectionsMask, currentMode, nextMode, stable, nextStable, lossTicks, nextLossTicks, updated, nextPositionDistances);
        }
    }

    public record PositionDistance(int x, int y, int z, int distance) {

        public static @Nonnull PositionDistance from(@Nonnull Vector3i position, int distance) {
            return new PositionDistance(position.getX(), position.getY(), position.getZ(), distance);
        }
    }

    private record BlockKey(@Nonnull String worldId, int x, int y, int z) {

        private static @Nonnull BlockKey from(@Nonnull World world, @Nonnull Vector3i position) {
            return new BlockKey(world.getName(), position.getX(), position.getY(), position.getZ());
        }
    }
}
