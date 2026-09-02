package dev.moxinat.forcesofgravium.block.inverter;

import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.universe.world.World;
import dev.moxinat.forcesofgravium.ForcesOfGraviumPlugin;
import dev.moxinat.forcesofgravium.data.NodeComponent;
import dev.moxinat.forcesofgravium.signal.SignalState;
import dev.moxinat.forcesofgravium.signal.ConnectablePropagationScheduler;
import dev.moxinat.forcesofgravium.signal.ConnectableSignalRecalculator;
import dev.moxinat.forcesofgravium.spatial.ConnectableNeighborResolver;
import org.joml.Vector3i;

public final class InverterStateCalculator {

    private InverterStateCalculator() {
    }

    public static void handleControlChange(
            World world,
            Vector3i inverterPosition,
            Vector3i controlSourcePosition
    ) {
        NodeComponent controlSource =
                nodeAt(world, controlSourcePosition);

        if (controlSource == null
                || controlSource.effectiveState() == SignalState.OFF) {
            return;
        }

        NodeComponent inverter =
                nodeAt(world, inverterPosition);

        if (inverter == null) {
            return;
        }

        inverter.setInvertEnabled(
                !inverter.invertEnabled()
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

            NodeComponent neighbor =
                    nodeAt(world, forwardNeighbor);

            if (neighbor != null && neighbor.dirty()) {
                ConnectablePropagationScheduler.scheduleAdoption(
                        world,
                        forwardNeighbor
                );
            }
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
}
