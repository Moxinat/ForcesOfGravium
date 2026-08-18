package dev.moxinat.forcesofgravium.dispatcher;

import com.hypixel.hytale.server.core.universe.world.World;
import org.joml.Vector3i;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ConnectableVisualRefreshScheduler {

    private static final Map<World, Map<Vector3i, Long>> PENDING =
            new ConcurrentHashMap<>();

    private ConnectableVisualRefreshScheduler() {
    }

    public static void scheduleTopologyRefresh(
            World world,
            Vector3i position
    ) {
        PENDING
                .computeIfAbsent(
                        world,
                        ignored -> new ConcurrentHashMap<>()
                )
                .put(
                        new Vector3i(position),
                        world.getTick() + 1
                );
    }

    public static void tickWorld(World world) {
        Map<Vector3i, Long> pending =
                PENDING.get(world);

        if (pending == null) {
            return;
        }

        long currentTick = world.getTick();

        for (Map.Entry<Vector3i, Long> entry :
                pending.entrySet()) {

            if (entry.getValue() > currentTick) {
                continue;
            }

            Vector3i position = entry.getKey();

            if (!pending.remove(
                    position,
                    entry.getValue()
            )) {
                continue;
            }

            ConnectableVisualDispatcher
                    .refreshTopologyAround(
                            world,
                            position
                    );
        }

        if (pending.isEmpty()) {
            PENDING.remove(world);
        }
    }
}
