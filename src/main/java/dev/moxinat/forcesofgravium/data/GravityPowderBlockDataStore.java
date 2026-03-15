package dev.moxinat.forcesofgravium.data;

import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.universe.world.World;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public final class GravityPowderBlockDataStore {

    private static final Map<BlockKey, GravityPowderBlockData> DATA = new ConcurrentHashMap<>();

    private GravityPowderBlockDataStore() {
    }

    public static void putDefault(@Nonnull World world, @Nonnull Vector3i position) {
        put(world, position, GravityPowderBlockData.defaultData());
    }

    public static void put(@Nonnull World world, @Nonnull Vector3i position, @Nonnull GravityPowderBlockData data) {
        DATA.put(BlockKey.from(world, position), data);
    }

    public static @Nullable GravityPowderBlockData get(@Nonnull World world, @Nonnull Vector3i position) {
        return DATA.get(BlockKey.from(world, position));
    }

    public static boolean has(@Nonnull World world, @Nonnull Vector3i position) {
        return DATA.containsKey(BlockKey.from(world, position));
    }

    public static @Nonnull GravityPowderBlockData getOrCreate(@Nonnull World world, @Nonnull Vector3i position) {
        return DATA.computeIfAbsent(BlockKey.from(world, position), ignored -> GravityPowderBlockData.defaultData());
    }

    public static void setConnectionsMask(@Nonnull World world, @Nonnull Vector3i position, int connectionsMask) {
        DATA.compute(BlockKey.from(world, position), (ignored, existing) -> {
            GravityPowderBlockData data = existing == null ? GravityPowderBlockData.defaultData() : existing;
            return data.withConnectionsMask(connectionsMask);
        });
    }

    public static void setCurrentMode(@Nonnull World world, @Nonnull Vector3i position, @Nonnull String currentMode) {
        DATA.compute(BlockKey.from(world, position), (ignored, existing) -> {
            GravityPowderBlockData data = existing == null ? GravityPowderBlockData.defaultData() : existing;
            return data.withCurrentMode(currentMode);
        });
    }

    public static void setNextMode(@Nonnull World world, @Nonnull Vector3i position, @Nonnull String nextMode) {
        DATA.compute(BlockKey.from(world, position), (ignored, existing) -> {
            GravityPowderBlockData data = existing == null ? GravityPowderBlockData.defaultData() : existing;
            return data.withNextMode(nextMode);
        });
    }

    public static void setPositionDistances(@Nonnull World world, @Nonnull Vector3i position, @Nonnull List<PositionDistance> positionDistances) {
        DATA.compute(BlockKey.from(world, position), (ignored, existing) -> {
            GravityPowderBlockData data = existing == null ? GravityPowderBlockData.defaultData() : existing;
            return data.withPositionDistances(positionDistances);
        });
    }

    public static void addPositionDistance(@Nonnull World world, @Nonnull Vector3i position, @Nonnull Vector3i targetPosition, int distance) {
        DATA.compute(BlockKey.from(world, position), (ignored, existing) -> {
            GravityPowderBlockData data = existing == null ? GravityPowderBlockData.defaultData() : existing;
            return data.withAddedPositionDistance(targetPosition, distance);
        });
    }

    public static void clearPositionDistances(@Nonnull World world, @Nonnull Vector3i position) {
        DATA.compute(BlockKey.from(world, position), (ignored, existing) -> {
            GravityPowderBlockData data = existing == null ? GravityPowderBlockData.defaultData() : existing;
            return data.withPositionDistances(List.of());
        });
    }

    public static void remove(@Nonnull World world, @Nonnull Vector3i position) {
        DATA.remove(BlockKey.from(world, position));
    }

    public static int size() {
        return DATA.size();
    }

    public record GravityPowderBlockData(
            int connectionsMask,
            @Nonnull String currentMode,
            @Nonnull String nextMode,
            @Nonnull List<PositionDistance> positionDistances
    ) {

        public GravityPowderBlockData {
            currentMode = Objects.requireNonNull(currentMode, "currentMode");
            nextMode = Objects.requireNonNull(nextMode, "nextMode");
            positionDistances = List.copyOf(Objects.requireNonNull(positionDistances, "positionDistances"));
        }

        public static @Nonnull GravityPowderBlockData defaultData() {
            return new GravityPowderBlockData(0, "off", "off", List.of());
        }

        public @Nonnull GravityPowderBlockData withConnectionsMask(int value) {
            return new GravityPowderBlockData(value, currentMode, nextMode, positionDistances);
        }

        public @Nonnull GravityPowderBlockData withCurrentMode(@Nonnull String value) {
            return new GravityPowderBlockData(connectionsMask, Objects.requireNonNull(value, "currentMode"), nextMode, positionDistances);
        }

        public @Nonnull GravityPowderBlockData withNextMode(@Nonnull String value) {
            return new GravityPowderBlockData(connectionsMask, currentMode, Objects.requireNonNull(value, "nextMode"), positionDistances);
        }

        public @Nonnull GravityPowderBlockData withPositionDistances(@Nonnull List<PositionDistance> value) {
            return new GravityPowderBlockData(connectionsMask, currentMode, nextMode, value);
        }

        public @Nonnull GravityPowderBlockData withAddedPositionDistance(@Nonnull Vector3i targetPosition, int distance) {
            List<PositionDistance> updated = new java.util.ArrayList<>(positionDistances);
            updated.add(PositionDistance.from(targetPosition, distance));
            return new GravityPowderBlockData(connectionsMask, currentMode, nextMode, updated);
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
