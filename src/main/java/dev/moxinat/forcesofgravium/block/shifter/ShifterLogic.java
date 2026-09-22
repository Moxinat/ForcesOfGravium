package dev.moxinat.forcesofgravium.block.shifter;

import com.hypixel.hytale.protocol.BlockMaterial;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.universe.world.World;
import dev.moxinat.forcesofgravium.ForcesOfGraviumPlugin;
import dev.moxinat.forcesofgravium.data.NetworkResource;
import dev.moxinat.forcesofgravium.data.NodeComponent;
import dev.moxinat.forcesofgravium.energy.EnergyManager;
import dev.moxinat.forcesofgravium.registry.ConnectableRegistry;
import dev.moxinat.forcesofgravium.spatial.ConnectableNeighborResolver;
import org.joml.Vector3i;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

public class ShifterLogic {

    private static final int BASE_ENERGY_COST = 1;

    private ShifterLogic() {
    }

    public record MovementEntry(
            Vector3i shifterPosition,
            Vector3i sourcePosition,
            Vector3i targetPosition
    ) {
    }

    List<MovementEntry> movementQueue = new ArrayList<>();

    public static void handleStateChange(
            @Nonnull World world,
            @Nonnull Vector3i position
    ) {
        NodeComponent node =
                BlockModule.getComponent(
                        ForcesOfGraviumPlugin.NODE_COMPONENT_TYPE,
                        world,
                        position.x(),
                        position.y(),
                        position.z()
                );

        if (node == null) {
            return;
        }

        NetworkResource networks =
                world.getChunkStore()
                        .getStore()
                        .getResource(
                                ForcesOfGraviumPlugin.NETWORK_RESOURCE_TYPE
                        );

        long networkId =
                networks.networkAt(position);

        if (networkId == NetworkResource.NO_NETWORK) {
            return;
        }

        int energyDelta =
                switch (node.effectiveState()) {
                    case PUSH, PULL -> -BASE_ENERGY_COST;
                    case OFF -> 0;
                };

        networks.setEnergyDelta(
                networkId,
                position,
                energyDelta
        );

        EnergyManager.checkNetwork(
                world,
                position
        );
    }

    public static void tickShifter(
            @Nonnull World world,
            @Nonnull Vector3i shifterPosition,
            @Nonnull NodeComponent node
    ) {
        switch (node.effectiveState()) {
            case PUSH -> {
                Vector3i sourcePosition =
                        ConnectableNeighborResolver.adjacentPositionForLocalSide(
                                world,
                                shifterPosition,
                                ConnectableRegistry.SIDE_FRONT
                        );

                Vector3i targetPosition =
                        new Vector3i(sourcePosition)
                                .add(
                                        sourcePosition.x() - shifterPosition.x(),
                                        sourcePosition.y() - shifterPosition.y(),
                                        sourcePosition.z() - shifterPosition.z()
                                );

                List<MovementEntry> movementQueue =
                        new ArrayList<>();

                tryMoveBlock(
                        world,
                        shifterPosition,
                        sourcePosition,
                        targetPosition,
                        movementQueue
                );
            }

            case PULL, OFF -> {
            }
        }
    }


    private static boolean tryMoveBlock(
            @Nonnull World world,
            @Nonnull Vector3i shifterPosition,
            @Nonnull Vector3i sourcePosition,
            @Nonnull Vector3i targetPosition,
            @Nonnull List<MovementEntry> movementQueue
    ) {
        Vector3i direction =
                new Vector3i(targetPosition)
                        .sub(sourcePosition);

        Vector3i currentSource =
                new Vector3i(sourcePosition);

        Vector3i currentTarget =
                new Vector3i(targetPosition);

        while (true) {

            movementQueue.add(
                    new MovementEntry(
                            new Vector3i(shifterPosition),
                            new Vector3i(currentSource),
                            new Vector3i(currentTarget)
                    )
            );

            BlockType targetBlock =
                    world.getBlockType(currentTarget);

            if (targetBlock == null) {
                return false;
            }

            if (targetBlock.getMaterial() == BlockMaterial.Empty) {
                return true;
            }

            currentSource.set(currentTarget);
            currentTarget.add(direction);
        }
    }


}
