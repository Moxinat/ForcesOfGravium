package dev.moxinat.forcesofgravium.signal;

import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.universe.world.World;
import dev.moxinat.forcesofgravium.ForcesOfGraviumPlugin;
import dev.moxinat.forcesofgravium.data.NodeComponent;
import dev.moxinat.forcesofgravium.data.SignalRuntimeResource;
import dev.moxinat.forcesofgravium.dispatcher.ConnectableVisualDispatcher;
import dev.moxinat.forcesofgravium.dispatcher.NodeControlDispatcher;
import dev.moxinat.forcesofgravium.dispatcher.NodeStateDispatcher;
import dev.moxinat.forcesofgravium.spatial.ConnectableNeighborResolver;
import org.joml.Vector3i;

import javax.annotation.Nonnull;
import java.util.LinkedHashSet;
import java.util.Set;

public final class ConnectablePropagationScheduler {

    private ConnectablePropagationScheduler() {
    }

    public static void scheduleAdoption(
            @Nonnull World world,
            @Nonnull Vector3i position
    ) {
        signalResource(world)
                .currentWave()
                .add(new Vector3i(position));
    }

    public static void tickWorld(@Nonnull World world) {
        SignalRuntimeResource signal =
                signalResource(world);

        if (signal.currentWave().isEmpty()) {
            return;
        }

        Set<Vector3i> currentWave =
                new LinkedHashSet<>(
                        signal.currentWave()
                );

        signal.currentWave().clear();

        for (Vector3i position : currentWave) {
            adoptInstantStateAndScheduleNeighbors(world, position);
        }

        if (!signal.nextWave().isEmpty()) {

            signal.currentWave().addAll(
                    signal.nextWave()
            );

            signal.nextWave().clear();
        }
    }

    private static void adoptInstantStateAndScheduleNeighbors(
            @Nonnull World world,
            @Nonnull Vector3i position
    ) {
        NodeComponent node = nodeAt(world, position);

        if (node == null || !node.dirty()) {
            return;
        }

        SignalState previousEffectiveState = node.effectiveState();

        node.adoptInstantState();

        if (node.effectiveState() != previousEffectiveState) {

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

            for (Vector3i controlNeighbor :
                    ConnectableNeighborResolver.allControlNeighbors(
                            world,
                            position
                    )) {

                NodeControlDispatcher.dispatch(world, controlNeighbor, position);
                ConnectableVisualDispatcher.refreshAt(world, controlNeighbor);
            }
        }

        for (Vector3i signalNeighbor :
                ConnectableNeighborResolver.allForwardSignalNeighbors(
                        world,
                        position
                )) {

            NodeComponent neighbor = nodeAt(world, signalNeighbor);

            if (neighbor != null && neighbor.dirty()) {
                signalResource(world)
                        .nextWave()
                        .add(new Vector3i(signalNeighbor));
            }
        }
    }

    public static void cancelPendingAdoption(
            World world,
            Vector3i position
    ) {
        SignalRuntimeResource signal =
                signalResource(world);

        signal.currentWave().remove(position);
        signal.nextWave().remove(position);
    }

    private static NodeComponent nodeAt(
            World world,
            Vector3i position
    ) {
        return BlockModule.getComponent(
                ForcesOfGraviumPlugin.NODE_COMPONENT_TYPE,
                world,
                position.x(),
                position.y(),
                position.z()
        );
    }

    private static SignalRuntimeResource signalResource(
            @Nonnull World world
    ) {
        return world
                .getChunkStore()
                .getStore()
                .getResource(
                        ForcesOfGraviumPlugin.SIGNAL_RESOURCE_TYPE
                );
    }

}
