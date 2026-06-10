package dev.moxinat.forcesofgravium.connectable.core;

import com.hypixel.hytale.server.core.universe.world.World;
import org.joml.Vector3i;

import javax.annotation.Nonnull;
import java.util.Objects;

public record ConnectableRuntimeKey(
        @Nonnull String worldIdentity,
        int x,
        int y,
        int z
) {
    public ConnectableRuntimeKey {
        worldIdentity = Objects.requireNonNull(worldIdentity, "worldIdentity");
    }

    public static @Nonnull ConnectableRuntimeKey from(@Nonnull World world, @Nonnull Vector3i position) {
        return new ConnectableRuntimeKey(world.getName(), position.x(), position.y(), position.z());
    }

    public @Nonnull Vector3i position() {
        return new Vector3i(x, y, z);
    }
}
