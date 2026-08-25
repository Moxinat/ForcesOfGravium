package dev.moxinat.forcesofgravium.signal;

import com.hypixel.hytale.server.core.universe.world.World;
import dev.moxinat.forcesofgravium.dispatcher.ConnectableVisualDispatcher;
import dev.moxinat.forcesofgravium.dispatcher.NodeControlDispatcher;
import dev.moxinat.forcesofgravium.dispatcher.NodeStateDispatcher;
import dev.moxinat.forcesofgravium.spatial.ConnectableNeighborResolver;
import dev.moxinat.forcesofgravium.data.Nodes;
import org.joml.Vector3i;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class ConnectablePropagationScheduler {

    private static final Map<World, Set<Vector3i>> CURRENT_WAVE = new ConcurrentHashMap<>();
    private static final Map<World, Set<Vector3i>> NEXT_WAVE = new ConcurrentHashMap<>();

    private ConnectablePropagationScheduler() {
    }

    public static void scheduleAdoption(
            @Nonnull World world,
            @Nonnull Vector3i position
    ) {
        CURRENT_WAVE
                .computeIfAbsent(world, ignored -> ConcurrentHashMap.newKeySet())
                .add(position);
    }

    public static void tickWorld(@Nonnull World world) {
        Set<Vector3i> currentWave = CURRENT_WAVE.remove(world);

        if (currentWave == null || currentWave.isEmpty()) {
            return;
        }

        for (Vector3i position : currentWave) {
            adoptInstantStateAndScheduleNeighbors(world, position);
        }

        Set<Vector3i> nextWave = NEXT_WAVE.remove(world);

        if (nextWave != null && !nextWave.isEmpty()) {
            CURRENT_WAVE
                    .computeIfAbsent(world, ignored -> ConcurrentHashMap.newKeySet())
                    .addAll(nextWave);
        }
    }

    private static void adoptInstantStateAndScheduleNeighbors(
            @Nonnull World world,
            @Nonnull Vector3i position
    ) {
        Nodes.Node node = Nodes.get(world, position);

        if (node == null || !node.dirty()) {
            return;
        }

        SignalState previousEffectiveState = node.effectiveState();

        node = node.adoptInstantState();
        Nodes.put(world, node);

        if (node.effectiveState() != previousEffectiveState) {

            System.out.println(
                    "[ADOPT] pos=" + position
                            + " block=" + node.blockId()
                            + " previous=" + previousEffectiveState
                            + " current=" + node.effectiveState()
            );


            NodeStateDispatcher.dispatch(world, position);

            ConnectableVisualDispatcher.refreshAt(world, position);

            if (node.invertEnabled()) {
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
            }

            // Control-Reaktionen auf diesen neuen Effective-State.
            for (Vector3i controlNeighbor :
                    ConnectableNeighborResolver.allControlNeighbors(
                            world,
                            position
                    )) {

                NodeControlDispatcher.dispatch(world, controlNeighbor, position);
                ConnectableVisualDispatcher.refreshAt(world, controlNeighbor);
            }
        }

        // Normale Signal-Wave.
        for (Vector3i signalNeighbor :
                ConnectableNeighborResolver.allForwardSignalNeighbors(
                        world,
                        position
                )) {

            Nodes.Node neighbor = Nodes.get(world, signalNeighbor);

            if (neighbor != null && neighbor.dirty()) {
                NEXT_WAVE
                        .computeIfAbsent(
                                world,
                                ignored -> ConcurrentHashMap.newKeySet()
                        )
                        .add(signalNeighbor);
            }
        }
    }

    public static void cancelPendingAdoption(
            World world,
            Vector3i position
    ) {
        Set<Vector3i> current = CURRENT_WAVE.get(world);

        if (current != null) {
            current.remove(position);

            if (current.isEmpty()) {
                CURRENT_WAVE.remove(world, current);
            }
        }

        Set<Vector3i> next = NEXT_WAVE.get(world);

        if (next != null) {
            next.remove(position);

            if (next.isEmpty()) {
                NEXT_WAVE.remove(world, next);
            }
        }
    }

    private static @Nonnull Set<Vector3i> copyPositions(
            Set<Vector3i> positions
    ) {
        Set<Vector3i> copy =
                ConcurrentHashMap.newKeySet();

        for (Vector3i position : positions) {
            copy.add(new Vector3i(position));
        }

        return copy;
    }

    public static void clearWorld(
            @Nonnull World world
    ) {
        CURRENT_WAVE.remove(world);
        NEXT_WAVE.remove(world);
    }

    public static @Nonnull Set<Vector3i> snapshotCurrentWave(
            @Nonnull World world
    ) {
        Set<Vector3i> wave = CURRENT_WAVE.get(world);

        if (wave == null) {
            return Set.of();
        }

        return Set.copyOf(copyPositions(wave));
    }

    public static @Nonnull Set<Vector3i> snapshotNextWave(
            @Nonnull World world
    ) {
        Set<Vector3i> wave = NEXT_WAVE.get(world);

        if (wave == null) {
            return Set.of();
        }

        return Set.copyOf(copyPositions(wave));
    }

    public static void restoreWaves(
            @Nonnull World world,
            @Nonnull Set<Vector3i> currentWave,
            @Nonnull Set<Vector3i> nextWave
    ) {
        clearWorld(world);

        if (!currentWave.isEmpty()) {
            CURRENT_WAVE.put(
                    world,
                    copyPositions(currentWave)
            );
        }

        if (!nextWave.isEmpty()) {
            NEXT_WAVE.put(
                    world,
                    copyPositions(nextWave)
            );
        }
    }


}
