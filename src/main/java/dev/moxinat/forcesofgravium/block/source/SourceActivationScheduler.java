package dev.moxinat.forcesofgravium.block.source;

import dev.moxinat.forcesofgravium.connectable.SignalState;
import dev.moxinat.forcesofgravium.connectable.energy.EnergyManager;
import dev.moxinat.forcesofgravium.connectable.propagation.ConnectableSignalRecalculator;
import dev.moxinat.forcesofgravium.connectable.registry.SourceRegistry;
import dev.moxinat.forcesofgravium.connectable.spatial.ConnectableNeighborResolver;
import dev.moxinat.forcesofgravium.data.Nodes;
import org.joml.Vector3i;
import com.hypixel.hytale.server.core.universe.world.World;
import dev.moxinat.forcesofgravium.connectable.propagation.ConnectablePropagationScheduler;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class SourceActivationScheduler {

    private static final Map<World, Map<Vector3i, Long>> ACTIVE_UNTIL_TICKS = new ConcurrentHashMap<>();

    private SourceActivationScheduler() {
    }

    public static void activateForTicks(
            @Nonnull World world,
            @Nonnull Vector3i position,
            long ticks
    ) {
        Nodes.Node node = Nodes.get(world, position);

        if (node == null) {
            return;
        }

        int power = SourceRegistry.powerFor(node.blockId());

        if (power <= 0) {
            return;
        }

        node = node
                .withEnergyDelta(power)
                .withInstantState(SignalState.PUSH)
                .withDirty(true);

        Nodes.put(
                world,
                node
        );

        EnergyManager.checkNetwork(
                world,
                position
        );

        ACTIVE_UNTIL_TICKS
                .computeIfAbsent(
                        world,
                        ignored -> new ConcurrentHashMap<>()
                )
                .put(
                        position,
                        world.getTick() + ticks
                );

        for (Vector3i forwardNeighbor :
                ConnectableNeighborResolver.allForwardSignalNeighbors(
                        world,
                        position
                )) {

            ConnectableSignalRecalculator.recompute(
                    world,
                    forwardNeighbor
            );
        }

        ConnectablePropagationScheduler.scheduleAdoption(
                world,
                position
        );
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

            Nodes.Node node = Nodes.get(
                    world,
                    position
            );

            if (node == null) {
                continue;
            }

            node = node
                    .withEnergyDelta(0)
                    .withInstantState(SignalState.OFF)
                    .withDirty(true);

            Nodes.put(
                    world,
                    node
            );

            EnergyManager.checkNetwork(
                    world,
                    position
            );

            for (Vector3i forwardNeighbor :
                    ConnectableNeighborResolver.allForwardSignalNeighbors(
                            world,
                            position
                    )) {

                ConnectableSignalRecalculator.recompute(
                        world,
                        forwardNeighbor
                );
            }

            ConnectablePropagationScheduler.scheduleAdoption(
                    world,
                    position
            );
        }
        if (activeUntilByPosition.isEmpty()) {
            ACTIVE_UNTIL_TICKS.remove(world, activeUntilByPosition);
        }
    }

    public static @Nonnull Map<Vector3i, Long> snapshotRemainingTicks(
            @Nonnull World world
    ) {
        Map<Vector3i, Long> activeUntilByPosition =
                ACTIVE_UNTIL_TICKS.get(world);

        if (activeUntilByPosition == null
                || activeUntilByPosition.isEmpty()) {
            return Map.of();
        }

        long currentTick = world.getTick();

        Map<Vector3i, Long> remainingTicks =
                new HashMap<>();

        for (Map.Entry<Vector3i, Long> entry :
                activeUntilByPosition.entrySet()) {

            long remaining = Math.max(
                    0L,
                    entry.getValue() - currentTick
            );

            remainingTicks.put(
                    new Vector3i(entry.getKey()),
                    remaining
            );
        }

        return Map.copyOf(remainingTicks);
    }

    public static void restoreTimedSources(
            @Nonnull World world,
            @Nonnull Map<Vector3i, Long> remainingTicks
    ) {
        clearWorld(world);

        if (remainingTicks.isEmpty()) {
            return;
        }

        long currentTick = world.getTick();

        Map<Vector3i, Long> activeUntilByPosition =
                new ConcurrentHashMap<>();

        for (Map.Entry<Vector3i, Long> entry :
                remainingTicks.entrySet()) {

            activeUntilByPosition.put(
                    new Vector3i(entry.getKey()),
                    currentTick + entry.getValue()
            );
        }

        ACTIVE_UNTIL_TICKS.put(
                world,
                activeUntilByPosition
        );
    }

    public static void clearWorld(
            @Nonnull World world
    ) {
        ACTIVE_UNTIL_TICKS.remove(world);
    }
}
