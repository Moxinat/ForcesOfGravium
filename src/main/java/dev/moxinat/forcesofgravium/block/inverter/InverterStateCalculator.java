package dev.moxinat.forcesofgravium.block.inverter;

import com.hypixel.hytale.server.core.universe.world.World;
import dev.moxinat.forcesofgravium.connectable.SignalState;
import dev.moxinat.forcesofgravium.connectable.dispatcher.ConnectableVisualDispatcher;
import dev.moxinat.forcesofgravium.connectable.propagation.ConnectablePropagationScheduler;
import dev.moxinat.forcesofgravium.connectable.propagation.ConnectableSignalRecalculator;
import dev.moxinat.forcesofgravium.connectable.spatial.ConnectableNeighborResolver;
import dev.moxinat.forcesofgravium.data.Nodes;
import org.joml.Vector3i;

public final class InverterStateCalculator {

    private InverterStateCalculator() {
    }

    public static void handleControlChange(
            World world,
            Vector3i inverterPosition,
            Vector3i controlSourcePosition
    ) {
        Nodes.Node controlSource = Nodes.get(world, controlSourcePosition);

        if (controlSource == null
                || controlSource.effectiveState() == SignalState.OFF) {
            return;
        }

        Nodes.Node inverter = Nodes.get(world, inverterPosition);

        if (inverter == null) {
            return;
        }

        Nodes.put(
                world,
                inverter.withInvertEnabled(!inverter.invertEnabled())
        );

        for (Vector3i forwardNeighbor :
                ConnectableNeighborResolver.allForwardSignalNeighbors(
                        world,
                        inverterPosition
                )) {

            ConnectableSignalRecalculator.recompute(
                    world,
                    forwardNeighbor
            );

            Nodes.Node neighbor =
                    Nodes.get(world, forwardNeighbor);

            if (neighbor != null && neighbor.dirty()) {
                ConnectablePropagationScheduler.scheduleAdoption(
                        world,
                        forwardNeighbor
                );
            }
        }
    }
}
