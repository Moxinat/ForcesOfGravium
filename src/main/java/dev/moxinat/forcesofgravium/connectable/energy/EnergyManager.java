package dev.moxinat.forcesofgravium.connectable.energy;

import com.hypixel.hytale.server.core.universe.world.World;
import dev.moxinat.forcesofgravium.data.Nodes;
import org.joml.Vector3i;

import javax.annotation.Nonnull;

public final class EnergyManager {

    private EnergyManager() {
    }

    public static void checkNetwork(
            @Nonnull World world,
            @Nonnull Vector3i position
    ) {
        Nodes.Node node = Nodes.get(world, position);

        if (node == null || node.networkId() == Nodes.Node.NO_NETWORK) {
            return;
        }

        long networkId = node.networkId();
        int energy = Nodes.snapshotForWorld(world)
                .values()
                .stream()
                .filter(networkNode -> networkNode.networkId() == networkId)
                .mapToInt(Nodes.Node::energyDelta)
                .sum();

        if (energy < 0) {
            failNetwork(world, networkId);
        }
    }

    private static void failNetwork(
            @Nonnull World world,
            long networkId
    ) {
    }
}
