package dev.moxinat.forcesofgravium.connectable.dispatcher;

import com.hypixel.hytale.server.core.universe.world.World;
import dev.moxinat.forcesofgravium.connectable.spatial.ConnectableNeighborResolver;
import org.joml.Vector3i;

import javax.annotation.Nonnull;

public final class ConnectableVisualDispatcher {

    private ConnectableVisualDispatcher() {
    }

    public static void refreshAt(
            @Nonnull World world,
            @Nonnull Vector3i position
    ) {
    }

    public static void refreshTopologyAround(
            @Nonnull World world,
            @Nonnull Vector3i position
    ) {
        refreshAt(
                world,
                position
        );

        for (Vector3i neighbor :
                ConnectableNeighborResolver.positionsAround(position)) {

            refreshAt(
                    world,
                    neighbor
            );
        }
    }
}
