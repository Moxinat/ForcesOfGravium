package dev.moxinat.forcesofgravium.block.sensor;

import com.hypixel.hytale.server.core.universe.world.World;
import dev.moxinat.forcesofgravium.data.Nodes;
import dev.moxinat.forcesofgravium.energy.EnergyManager;
import dev.moxinat.forcesofgravium.registry.NodeTypes;
import dev.moxinat.forcesofgravium.signal.ConnectablePropagationScheduler;
import dev.moxinat.forcesofgravium.signal.ConnectableSignalRecalculator;
import dev.moxinat.forcesofgravium.signal.SignalState;
import dev.moxinat.forcesofgravium.spatial.ConnectableNeighborResolver;
import org.joml.Vector3i;

import java.util.Set;

public final class SensorLogic {

    private SensorLogic() {
    }

    public static void handleStateChange(
            World world,
            Vector3i position
    ) {
        Nodes.Node node = Nodes.get(world, position);

        if (node == null
                || !NodeTypes.GRAVIUM_SENSOR.blockId().equals(node.blockId())) {
            return;
        }

        boolean shouldPass =
                node.effectiveState() != SignalState.PULL;

        if (node.passing() == shouldPass) {
            return;
        }

        Set<Vector3i> forwardNeighbors;

        if (!shouldPass) {
            // Connection still exists here.
            forwardNeighbors =
                    ConnectableNeighborResolver.allForwardSignalNeighbors(
                            world,
                            position
                    );

            Nodes.put(
                    world,
                    node.withPassing(false)
            );
        } else {
            // Restore outputs first.
            Nodes.put(
                    world,
                    node.withPassing(true)
            );

            // Now the forward connections exist again.
            forwardNeighbors =
                    ConnectableNeighborResolver.allForwardSignalNeighbors(
                            world,
                            position
                    );
        }

        for (Vector3i forwardNeighbor : forwardNeighbors) {
            ConnectableSignalRecalculator.recompute(
                    world,
                    forwardNeighbor
            );

            EnergyManager.checkNetwork(
                    world,
                    forwardNeighbor
            );

            ConnectablePropagationScheduler.scheduleAdoption(
                    world,
                    forwardNeighbor
            );
        }
    }
}