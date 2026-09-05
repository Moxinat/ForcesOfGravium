package dev.moxinat.forcesofgravium.source;

import com.hypixel.hytale.server.core.modules.block.BlockModule;
import dev.moxinat.forcesofgravium.ForcesOfGraviumPlugin;
import dev.moxinat.forcesofgravium.data.NodeComponent;
import dev.moxinat.forcesofgravium.data.SignalRuntimeResource;
import dev.moxinat.forcesofgravium.data.SourceComponent;
import dev.moxinat.forcesofgravium.signal.SignalState;
import dev.moxinat.forcesofgravium.energy.EnergyManager;
import dev.moxinat.forcesofgravium.signal.ConnectableSignalRecalculator;
import dev.moxinat.forcesofgravium.spatial.ConnectableNeighborResolver;
import org.joml.Vector3i;
import com.hypixel.hytale.server.core.universe.world.World;
import dev.moxinat.forcesofgravium.signal.ConnectablePropagationScheduler;

import javax.annotation.Nonnull;
import java.util.Map;

public final class SourceActivationScheduler {

    private SourceActivationScheduler() {
    }

    public static void activateForTicks(
            @Nonnull World world,
            @Nonnull Vector3i position,
            long ticks
    ) {
        NodeComponent node = nodeAt(world, position);
        SourceComponent source = sourceAt(world, position);

        if (node == null || source == null) {
            return;
        }

        int power = source.power();

        if (power <= 0) {
            return;
        }

        node.setEnergyDelta(power);
        node.setInstantState(SignalState.PUSH);
        node.setDirty(true);

        EnergyManager.checkNetwork(
                world,
                position
        );

        signalResource(world)
                .activeSources()
                .put(
                        new Vector3i(position),
                        ticks
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

    public static void tickWorld(
            @Nonnull World world
    ) {
        Map<Vector3i, Long> activeSources =
                signalResource(world).activeSources();

        if (activeSources.isEmpty()) {
            return;
        }

        for (Map.Entry<Vector3i, Long> entry
                : Map.copyOf(activeSources).entrySet()) {

            Vector3i position =
                    entry.getKey();

            long remainingTicks =
                    entry.getValue() - 1;

            if (remainingTicks > 0) {
                activeSources.put(
                        position,
                        remainingTicks
                );

                continue;
            }

            NodeComponent node =
                    nodeAt(
                            world,
                            position
                    );

            if (node == null) {
                continue;
            }

            activeSources.remove(position);

            node.setEnergyDelta(0);
            node.setInstantState(SignalState.OFF);
            node.setDirty(true);

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

    private static SourceComponent sourceAt(
            @Nonnull World world,
            @Nonnull Vector3i position
    ) {
        return BlockModule.getComponent(
                ForcesOfGraviumPlugin.SOURCE_COMPONENT_TYPE,
                world,
                position.x(),
                position.y(),
                position.z()
        );
    }
}
