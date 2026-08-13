package dev.moxinat.forcesofgravium.connectable.dispatcher;

import com.hypixel.hytale.server.core.universe.world.World;
import dev.moxinat.forcesofgravium.block.inverter.InverterStateCalculator;
import dev.moxinat.forcesofgravium.connectable.registry.ConnectableRegistry;
import dev.moxinat.forcesofgravium.data.Nodes;
import org.joml.Vector3i;

import javax.annotation.Nonnull;

public final class NodeControlDispatcher {

    private NodeControlDispatcher() {
    }

    public static void dispatch(
            @Nonnull World world,
            @Nonnull Vector3i targetPosition,
            @Nonnull Vector3i sourcePosition
    ) {
        Nodes.Node node = Nodes.get(
                world,
                targetPosition
        );

        if (node == null) {
            return;
        }

        switch (node.blockId()) {
            case ConnectableRegistry.INVERTER_BLOCK_ID ->
                    InverterStateCalculator.handleControlChange(
                            world,
                            targetPosition,
                            sourcePosition
                    );

            default -> {
            }
        }
    }
}
