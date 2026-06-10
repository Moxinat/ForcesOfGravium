package dev.moxinat.forcesofgravium.block.source;

import org.joml.Vector3i;
import com.hypixel.hytale.server.core.universe.world.World;
import dev.moxinat.forcesofgravium.connectable.core.ConnectableRuntimeAccessor;
import dev.moxinat.forcesofgravium.connectable.propagation.ConnectablePropagationScheduler;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class SourceActivationScheduler {

    private static final Map<World, Map<Vector3i, Long>> ACTIVE_UNTIL_TICKS = new ConcurrentHashMap<>();

    private SourceActivationScheduler() {
    }

    public static void activateForTicks(@Nonnull World world, @Nonnull Vector3i position, long ticks) {
        ConnectableRuntimeAccessor.setEnergyDelta(world, position, 1);
        ACTIVE_UNTIL_TICKS.computeIfAbsent(world, ignored -> new ConcurrentHashMap<>())
                .put(position, world.getTick() + ticks);
        ConnectablePropagationScheduler.onConnectablePlaced(world, position);
    }

    public static void tickWorld(@Nonnull World world) {
        Map<Vector3i, Long> activeUntilByPosition = ACTIVE_UNTIL_TICKS.get(world);
        if (activeUntilByPosition == null || activeUntilByPosition.isEmpty()) {
            return;
        }

        long tick = world.getTick();
        Map<Vector3i, Long> expired = new HashMap<>();
        for (Map.Entry<Vector3i, Long> entry : Map.copyOf(activeUntilByPosition).entrySet()) {
            if (entry.getValue() <= tick) {
                expired.put(entry.getKey(), entry.getValue());
            }
        }

        for (Map.Entry<Vector3i, Long> entry : expired.entrySet()) {
            Vector3i position = entry.getKey();
            if (!activeUntilByPosition.remove(position, entry.getValue())) {
                continue;
            }
            ConnectableRuntimeAccessor.setEnergyDelta(world, position, 0);
            ConnectablePropagationScheduler.onConnectableBroken(world, position);
        }
        if (activeUntilByPosition.isEmpty()) {
            ACTIVE_UNTIL_TICKS.remove(world, activeUntilByPosition);
        }
    }
}
