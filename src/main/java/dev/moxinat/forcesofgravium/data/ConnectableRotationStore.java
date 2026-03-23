package dev.moxinat.forcesofgravium.data;

import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import com.hypixel.hytale.server.core.universe.world.World;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ConnectableRotationStore {

    private static final Map<BlockKey, RotationTuple> DATA = new ConcurrentHashMap<>();

    private ConnectableRotationStore() {
    }

    public static void put(@Nonnull World world, @Nonnull Vector3i position, @Nonnull RotationTuple rotation) {
        DATA.put(BlockKey.from(world, position), rotation);
    }

    public static @Nonnull RotationTuple getOrDefault(@Nonnull World world, @Nonnull Vector3i position, @Nonnull RotationTuple fallback) {
        return DATA.getOrDefault(BlockKey.from(world, position), fallback);
    }

    public static void remove(@Nonnull World world, @Nonnull Vector3i position) {
        DATA.remove(BlockKey.from(world, position));
    }

    private record BlockKey(@Nonnull String worldId, int x, int y, int z) {

        private static @Nonnull BlockKey from(@Nonnull World world, @Nonnull Vector3i position) {
            return new BlockKey(world.getName(), position.getX(), position.getY(), position.getZ());
        }
    }
}
