package dev.moxinat.forcesofgravium.block.shifter;

import com.hypixel.hytale.protocol.BlockMaterial;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.universe.world.World;
import dev.moxinat.forcesofgravium.ForcesOfGraviumPlugin;
import dev.moxinat.forcesofgravium.data.NetworkResource;
import dev.moxinat.forcesofgravium.data.NodeComponent;
import dev.moxinat.forcesofgravium.data.Nodes;
import dev.moxinat.forcesofgravium.data.ShifterMovementResource;
import dev.moxinat.forcesofgravium.energy.EnergyManager;
import dev.moxinat.forcesofgravium.registry.ConnectableRegistry;
import dev.moxinat.forcesofgravium.signal.SignalState;
import dev.moxinat.forcesofgravium.spatial.ConnectableNeighborResolver;
import org.joml.Vector3i;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class ShifterLogic {

    private static final int BASE_ENERGY_COST = 1;

    private ShifterLogic() {
    }

    List<ShifterMovementResource.MovementEntry> movementQueue = new ArrayList<>();

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

        var store = world.getChunkStore().getStore();

        // Update active Shifter lists.
        ShifterMovementResource movements =
                store.getResource(
                        ForcesOfGraviumPlugin.SHIFTER_MOVEMENT_RESOURCE_TYPE
                );

        movements.setShifterState(
                position,
                node.effectiveState()
        );

        // Update base energy consumption.
        NetworkResource networks =
                store.getResource(
                        ForcesOfGraviumPlugin.NETWORK_RESOURCE_TYPE
                );

        long networkId = networks.networkAt(position);

        if (networkId == NetworkResource.NO_NETWORK) {
            return;
        }

        int energyDelta = switch (node.effectiveState()) {
            case PUSH, PULL -> -BASE_ENERGY_COST;
            case OFF -> 0;
        };

        networks.setEnergyDelta(
                networkId,
                position,
                energyDelta
        );

        EnergyManager.checkNetwork(world, position);
    }

    public static void tickShifter(
            @Nonnull World world
    ) {
        ShifterMovementResource movements =
                world.getChunkStore()
                        .getStore()
                        .getResource(
                                ForcesOfGraviumPlugin.SHIFTER_MOVEMENT_RESOURCE_TYPE
                        );

        Set<Vector3i> pushShifters =
                movements.pushShifters();

        Set<Vector3i> pullShifters =
                movements.pullShifters();

        Set<Vector3i> activeShifters =
                new LinkedHashSet<>(pushShifters);

        activeShifters.addAll(pullShifters);

        // Combined movement queue for this tick.
        List<ShifterMovementResource.MovementEntry> movementQueue =
                new ArrayList<>();


        // --------------------------------------------------
        // PLAN ALL SHIFTER MOVEMENTS
        // --------------------------------------------------

        for (Vector3i shifterPosition : activeShifters) {

            // Do not create another plan during an active movement.
            if (movements.hasActiveMovement(shifterPosition)) {
                continue;
            }

            SignalState state =
                    pushShifters.contains(shifterPosition)
                            ? SignalState.PUSH
                            : SignalState.PULL;

            switch (state) {

                case PUSH -> {

                    Vector3i sourcePosition =
                            ConnectableNeighborResolver.adjacentPositionForLocalSide(
                                    world,
                                    shifterPosition,
                                    ConnectableRegistry.SIDE_FRONT
                            );

                    Vector3i direction =
                            new Vector3i(sourcePosition)
                                    .sub(shifterPosition);

                    Vector3i targetPosition =
                            new Vector3i(sourcePosition)
                                    .add(direction);

                    // Check whether there is a block to push.
                    BlockType sourceBlock =
                            world.getBlockType(sourcePosition);

                    if (sourceBlock == null
                            || sourceBlock.getMaterial() == BlockMaterial.Empty) {
                        continue;
                    }

                    // Temporary queue for this Shifter.
                    List<ShifterMovementResource.MovementEntry> shifterQueue =
                            new ArrayList<>();

                    boolean valid = false;

                    while (true) {

                        BlockType targetBlock =
                                world.getBlockType(targetPosition);

                        if (targetBlock == null) {
                            break;
                        }

                        shifterQueue.add(
                                new ShifterMovementResource.MovementEntry(
                                        shifterPosition,
                                        sourcePosition,
                                        targetPosition
                                )
                        );

                        // End of the block chain.
                        if (targetBlock.getMaterial() == BlockMaterial.Empty) {
                            valid = true;
                            break;
                        }

                        // Continue the chain.
                        sourcePosition = new Vector3i(targetPosition);

                        targetPosition =
                                new Vector3i(targetPosition)
                                        .add(direction);
                    }

                    if (valid) {
                        movementQueue.addAll(shifterQueue);
                    }
                }

                case PULL -> {

                    Vector3i frontPosition =
                            ConnectableNeighborResolver.adjacentPositionForLocalSide(
                                    world,
                                    shifterPosition,
                                    ConnectableRegistry.SIDE_FRONT
                            );

                    BlockType frontBlock =
                            world.getBlockType(frontPosition);

                    // Position unavailable.
                    if (frontBlock == null) {
                        continue;
                    }

                    // A block is directly in front of the Shifter.
                    if (frontBlock.getMaterial() != BlockMaterial.Empty) {

                        movements.holdBlock(
                                frontPosition,
                                shifterPosition
                        );

                        continue;
                    }

                    // No block is currently being held.
                    movements.releaseHeldBlocks(shifterPosition);

                    Vector3i direction =
                            new Vector3i(frontPosition)
                                    .sub(shifterPosition);

                    Vector3i sourcePosition =
                            new Vector3i(frontPosition)
                                    .add(direction);

                    BlockType sourceBlock =
                            world.getBlockType(sourcePosition);

                    // Nothing to pull or position unavailable.
                    if (sourceBlock == null
                            || sourceBlock.getMaterial() == BlockMaterial.Empty) {

                        continue;
                    }

                    movementQueue.add(
                            new ShifterMovementResource.MovementEntry(
                                    shifterPosition,
                                    sourcePosition,
                                    frontPosition
                            )
                    );
                }

                case OFF -> {
                }
            }
        }


        // --------------------------------------------------
        // PROCESS MOVEMENT QUEUE
        // --------------------------------------------------

        // TODO:
        // All active Shifters have now been processed.
        // Validate movement dependencies, multiblocks
        // and conflicts before starting movements.
    }





}
