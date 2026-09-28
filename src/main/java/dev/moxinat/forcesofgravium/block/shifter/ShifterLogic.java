package dev.moxinat.forcesofgravium.block.shifter;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.math.shape.Box;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.protocol.BlockMaterial;
import com.hypixel.hytale.server.core.asset.type.blockhitbox.BlockBoundingBoxes;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.modules.entity.component.BoundingBox;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.hitboxcollision.HitboxCollision;
import com.hypixel.hytale.server.core.modules.entity.hitboxcollision.HitboxCollisionConfig;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.FillerBlockUtil;
import dev.moxinat.forcesofgravium.ForcesOfGraviumPlugin;
import dev.moxinat.forcesofgravium.data.NetworkResource;
import dev.moxinat.forcesofgravium.data.NodeComponent;
import dev.moxinat.forcesofgravium.data.ShifterMovementResource;
import dev.moxinat.forcesofgravium.energy.EnergyManager;
import dev.moxinat.forcesofgravium.registry.ConnectableRegistry;
import dev.moxinat.forcesofgravium.signal.SignalState;
import dev.moxinat.forcesofgravium.spatial.ConnectableNeighborResolver;
import org.joml.Vector3d;
import org.joml.Vector3i;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

public class ShifterLogic {

    private static final int MAX_MOVED_BLOCKS = 100;

    private static final int BASE_ENERGY_COST = 1;

    private static final int MOVEMENT_DURATION_TICKS = 5;

    private ShifterLogic() {
    }

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

        if (!networks.isFailing(networkId)) {
            EnergyManager.checkNetwork(
                    world,
                    position
            );
        }
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

        NetworkResource networks =
                world.getChunkStore()
                        .getStore()
                        .getResource(
                                ForcesOfGraviumPlugin.NETWORK_RESOURCE_TYPE
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
                            blockTypeAtLoaded(
                                    world,
                                    sourcePosition
                            );

                    if (sourceBlock == null
                            || isUnbreakable(sourceBlock)
                            || sourceBlock.getMaterial() == BlockMaterial.Empty) {
                        continue;
                    }

                    // Temporary queue for this Shifter.
                    List<ShifterMovementResource.MovementEntry> shifterQueue =
                            new ArrayList<>();

                    boolean valid = false;
                    while (true) {

                        BlockType targetBlock =
                                blockTypeAtLoaded(
                                        world,
                                        targetPosition
                                );

                        if (targetBlock == null
                                || isUnbreakable(targetBlock)) {
                            break;
                        }

                        if (shifterQueue.size() >= MAX_MOVED_BLOCKS) {
                            break;
                        }

                        shifterQueue.add(
                                new ShifterMovementResource.MovementEntry(
                                        shifterPosition,
                                        sourcePosition,
                                        targetPosition
                                )
                        );

                        // End of the blockchain.
                        if (targetBlock.getMaterial() == BlockMaterial.Empty) {
                            valid = true;
                            break;
                        }
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
                            blockTypeAtLoaded(
                                    world,
                                    frontPosition
                            );

                    // Position unavailable.
                    if (frontBlock == null
                            || isUnbreakable(frontBlock)) {
                        continue;
                    }
                    if (frontBlock.getMaterial() != BlockMaterial.Empty) {

                        movements.holdBlock(
                                frontPosition,
                                shifterPosition
                        );

                        continue;
                    }

                    movements.releaseHeldBlocks(shifterPosition);

                    Vector3i direction =
                            new Vector3i(frontPosition)
                                    .sub(shifterPosition);

                    Vector3i sourcePosition =
                            new Vector3i(frontPosition)
                                    .add(direction);

                    BlockType sourceBlock =
                            blockTypeAtLoaded(
                                    world,
                                    sourcePosition
                            );

                    // Nothing to pull or position unavailable.
                    if (sourceBlock == null
                            || isUnbreakable(sourceBlock)
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

        // Find Shifters that are being moved by another Shifter.
        Set<Vector3i> movedShifters = new HashSet<>();

        for (ShifterMovementResource.MovementEntry entry : movementQueue) {

            Vector3i source = entry.sourcePosition();

            if (activeShifters.contains(source)
                    && !source.equals(entry.shifterPosition())) {

                movedShifters.add(source);
            }
        }

        // Remove all movements initiated by those Shifters.
        movementQueue.removeIf(
                entry -> movedShifters.contains(entry.shifterPosition())
        );

        Map<Vector3i, Vector3i> directions = new HashMap<>();
        Map<Vector3i, Set<Vector3i>> shiftersByBlock = new HashMap<>();

        Set<Vector3i> conflictingBlocks = new HashSet<>();

        for (ShifterMovementResource.MovementEntry entry : movementQueue) {

            Vector3i source = entry.sourcePosition();

            Vector3i direction =
                    entry.targetPosition()
                            .sub(source);

            // Remember every Shifter trying to move this block.
            shiftersByBlock
                    .computeIfAbsent(source, ignored -> new HashSet<>())
                    .add(entry.shifterPosition());

            // Remember the first movement direction.
            Vector3i previousDirection =
                    directions.putIfAbsent(source, direction);

            // Different movement directions for the same block.
            if (previousDirection != null
                    && !previousDirection.equals(direction)) {

                conflictingBlocks.add(source);
            }
        }

        // Collect every Shifter involved in a conflict.
        Set<Vector3i> conflictingShifters = new HashSet<>();

        for (Vector3i block : conflictingBlocks) {
            conflictingShifters.addAll(
                    shiftersByBlock.get(block)
            );
        }

        // Remove their entire movement queues.
        movementQueue.removeIf(
                entry -> conflictingShifters.contains(
                        entry.shifterPosition()
                )
        );

        // Collect all positions occupied by ongoing movements.
        Set<Vector3i> reservedPositions = new HashSet<>();

        for (ShifterMovementResource.ActiveMovement movement
                : movements.activeMovements().values()) {

            for (ShifterMovementResource.MovementEntry entry
                    : movement.entries()) {

                reservedPositions.add(entry.sourcePosition());
                reservedPositions.add(entry.targetPosition());
            }
        }

        // Find Shifters trying to use reserved positions.
        Set<Vector3i> reservedConflictingShifters = new HashSet<>();

        for (ShifterMovementResource.MovementEntry entry : movementQueue) {

            if (reservedPositions.contains(entry.sourcePosition())
                    || reservedPositions.contains(entry.targetPosition())) {

                reservedConflictingShifters.add(entry.shifterPosition());
            }
        }

        movementQueue.removeIf(
                entry -> reservedConflictingShifters.contains(
                        entry.shifterPosition()
                )
        );

        // --------------------------------------------------
        // DETECT TARGET POSITION CONFLICTS
        // --------------------------------------------------

        Map<Vector3i, Map<Vector3i, Set<Vector3i>>> targetClaims =
                new HashMap<>();

        for (ShifterMovementResource.MovementEntry entry : movementQueue) {

            targetClaims
                    .computeIfAbsent(
                            entry.targetPosition(),
                            ignored -> new HashMap<>()
                    )
                    .computeIfAbsent(
                            entry.sourcePosition(),
                            ignored -> new HashSet<>()
                    )
                    .add(entry.shifterPosition());
        }

        Set<Vector3i> targetConflictingShifters = new HashSet<>();

        for (Map<Vector3i, Set<Vector3i>> claims
                : targetClaims.values()) {

            // Multiple different blocks claim the same target.
            if (claims.size() > 1) {

                for (Set<Vector3i> owners : claims.values()) {
                    targetConflictingShifters.addAll(owners);
                }
            }
        }

        movementQueue.removeIf(
                entry -> targetConflictingShifters.contains(
                        entry.shifterPosition()
                )
        );

        // --------------------------------------------------
        // VALIDATE MULTIBLOCKS AND HOLDING RELATIONSHIPS
        // --------------------------------------------------

        boolean changed;

        do {
            changed = false;

            Map<Vector3i, Vector3i> directionBySource =
                    new HashMap<>();

            Map<Vector3i, Set<Vector3i>> ownersBySource =
                    new HashMap<>();

            Map<Vector3i, Integer> movedBlocksByShifter =
                    new HashMap<>();

            // Index the remaining movements.
            for (ShifterMovementResource.MovementEntry entry : movementQueue) {

                Vector3i source = entry.sourcePosition();

                Vector3i direction =
                        entry.targetPosition().sub(source);

                directionBySource.put(source, direction);

                ownersBySource
                        .computeIfAbsent(source, ignored -> new HashSet<>())
                        .add(entry.shifterPosition());

                movedBlocksByShifter.merge(
                        entry.shifterPosition(),
                        1,
                        Integer::sum
                );
            }

            Map<Vector3i, Integer> energyCostByShifter =
                    new HashMap<>();

            for (Map.Entry<Vector3i, Integer> entry
                    : movedBlocksByShifter.entrySet()) {

                energyCostByShifter.put(
                        entry.getKey(),
                        movementEnergyCost(entry.getValue())
                );
            }

            Set<Vector3i> invalidShifters = new HashSet<>();

            // Every multiblock only needs to be checked once per iteration.
            Set<Vector3i> checkedOrigins = new HashSet<>();

            for (ShifterMovementResource.MovementEntry entry : movementQueue) {

                Vector3i source = entry.sourcePosition();

                Vector3i direction =
                        directionBySource.get(source);


                // ----------------------------------------------
                // MULTIBLOCK VALIDATION
                // ----------------------------------------------

                Vector3i origin =
                        multiblockOrigin(world, source);

                if (origin == null) {
                    invalidShifters.addAll(
                            ownersBySource.get(source)
                    );
                    continue;
                }

                if (checkedOrigins.add(origin)) {

                    Set<Vector3i> cells =
                            multiblockCells(world, origin);

                    if (cells == null) {

                        invalidShifters.addAll(
                                ownersBySource.get(source)
                        );

                    } else if (cells.size() > 1) {

                        Vector3i expectedDirection = null;
                        boolean valid = true;

                        for (Vector3i cell : cells) {

                            Vector3i cellDirection =
                                    directionBySource.get(cell);

                            if (cellDirection == null) {
                                valid = false;
                                continue;
                            }

                            if (expectedDirection == null) {
                                expectedDirection = cellDirection;

                            } else if (!expectedDirection.equals(cellDirection)) {
                                valid = false;
                            }
                        }

                        if (!valid) {

                            // Cancel every Shifter moving any part
                            // of this incomplete multiblock.
                            for (Vector3i cell : cells) {

                                invalidShifters.addAll(
                                        ownersBySource.getOrDefault(
                                                cell,
                                                Set.of()
                                        )
                                );
                            }
                        }
                    }
                }


                // ----------------------------------------------
                // HOLDING RELATIONSHIPS
                // ----------------------------------------------

                Set<Vector3i> partners = new HashSet<>();

                Vector3i holdingShifter =
                        movements.holdingShifter(source);

                if (holdingShifter != null) {
                    partners.add(holdingShifter);
                }

                Vector3i heldBlock =
                        movements.heldBlockFor(source);

                if (heldBlock != null) {
                    partners.add(heldBlock);
                }

                for (Vector3i partner : partners) {

                    Vector3i partnerDirection =
                            directionBySource.get(partner);

                    if (!direction.equals(partnerDirection)) {

                        // Cancel Shifters moving this block.
                        invalidShifters.addAll(
                                ownersBySource.get(source)
                        );

                        // Also cancel Shifters moving the partner,
                        // if it was queued in another direction.
                        invalidShifters.addAll(
                                ownersBySource.getOrDefault(
                                        partner,
                                        Set.of()
                                )
                        );
                    }
                }
            }

            // Remove every invalid Shifter queue simultaneously.
            if (!invalidShifters.isEmpty()) {

                changed = movementQueue.removeIf(
                        entry -> invalidShifters.contains(
                                entry.shifterPosition()
                        )
                );

                if (changed) {
                    Map<Long, Vector3i> resetNetworksToCheck =
                            new HashMap<>();

                    for (Vector3i shifterPosition : invalidShifters) {

                        long networkId =
                                networks.networkAt(shifterPosition);

                        if (networkId == NetworkResource.NO_NETWORK) {
                            continue;
                        }

                        networks.setEnergyDelta(
                                networkId,
                                shifterPosition,
                                -BASE_ENERGY_COST
                        );

                        if (!networks.isFailing(networkId)) {
                            resetNetworksToCheck.putIfAbsent(
                                    networkId,
                                    shifterPosition
                            );
                        }
                    }

                    for (Vector3i shifterPosition
                            : resetNetworksToCheck.values()) {

                        EnergyManager.checkNetwork(
                                world,
                                shifterPosition
                        );
                    }

                    continue;
                }
            }

            Map<Long, Vector3i> networksToCheck =
                    new HashMap<>();

            // Apply all movement energy costs first.
            for (Map.Entry<Vector3i, Integer> entry
                    : energyCostByShifter.entrySet()) {

                Vector3i shifterPosition =
                        entry.getKey();

                long networkId =
                        networks.networkAt(shifterPosition);

                if (networkId == NetworkResource.NO_NETWORK) {
                    continue;
                }

                networks.setEnergyDelta(
                        networkId,
                        shifterPosition,
                        -entry.getValue()
                );

                networksToCheck.putIfAbsent(
                        networkId,
                        shifterPosition
                );
            }

            // Only after every Shifter cost is applied,
            // evaluate each affected network once.
            for (Vector3i shifterPosition : networksToCheck.values()) {
                EnergyManager.checkNetwork(
                        world,
                        shifterPosition
                );
            }

            Set<Vector3i> failingShifters =
                    new HashSet<>();

            for (Vector3i shifterPosition
                    : energyCostByShifter.keySet()) {

                long networkId =
                        networks.networkAt(shifterPosition);

                if (networkId != NetworkResource.NO_NETWORK
                        && networks.isFailing(networkId)) {

                    failingShifters.add(shifterPosition);

                    networks.setEnergyDelta(
                            networkId,
                            shifterPosition,
                            -BASE_ENERGY_COST
                    );
                }
            }

            if (!failingShifters.isEmpty()) {
                changed = movementQueue.removeIf(
                        entry -> failingShifters.contains(
                                entry.shifterPosition()
                        )
                );
            }

        } while (changed);



        Map<Vector3i, List<ShifterMovementResource.MovementEntry>> entriesByShifter =
                new HashMap<>();

        for (ShifterMovementResource.MovementEntry entry : movementQueue) {

            entriesByShifter
                    .computeIfAbsent(
                            entry.shifterPosition(),
                            ignored -> new ArrayList<>()
                    )
                    .add(entry);
        }

        for (Map.Entry<Vector3i, List<ShifterMovementResource.MovementEntry>> entry
                : entriesByShifter.entrySet()) {

            Vector3i shifterPosition =
                    entry.getKey();

            List<ShifterMovementResource.MovementEntry> entries =
                    entry.getValue();

            Set<Vector3i> multiblockBases =
                    new HashSet<>();

            for (ShifterMovementResource.MovementEntry movementEntry : entries) {

                Vector3i origin =
                        multiblockOrigin(
                                world,
                                movementEntry.sourcePosition()
                        );

                if (origin == null) {
                    continue;
                }

                Set<Vector3i> cells =
                        multiblockCells(world, origin);

                if (cells != null && cells.size() > 1) {
                    multiblockBases.add(origin);
                }
            }

            movements.startMovement(
                    shifterPosition,
                    entries,
                    multiblockBases,
                    MOVEMENT_DURATION_TICKS
            );
        }

    }

    public static void tickActiveMovements(
            @Nonnull World world,
            @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        ShifterMovementResource movements =
                world.getChunkStore()
                        .getStore()
                        .getResource(
                                ForcesOfGraviumPlugin.SHIFTER_MOVEMENT_RESOURCE_TYPE
                        );

        Map<Vector3i, ShifterMovementResource.ActiveMovement> activeMovements =
                movements.activeMovements();

        if (activeMovements.isEmpty()) {
            return;
        }

        // --------------------------------------------------
        // PREPARED
        // --------------------------------------------------

        Set<Vector3i> preparedShifters =
                new HashSet<>();

        /*
         * Physical source -> physical target.
         *
         * For normal blocks:
         *     source -> target
         *
         * For multiblocks:
         *     origin -> origin + direction
         *
         * This also deduplicates the same block being moved
         * by multiple Shifters.
         */
        Map<Vector3i, Vector3i> blocksToPrepare =
                new LinkedHashMap<>();

        for (Map.Entry<Vector3i, ShifterMovementResource.ActiveMovement> activeEntry
                : activeMovements.entrySet()) {

            Vector3i shifterPosition =
                    activeEntry.getKey();

            ShifterMovementResource.ActiveMovement movement =
                    activeEntry.getValue();

            if (movement.stage()
                    != ShifterMovementResource.MovementStage.PREPARED) {
                continue;
            }

            preparedShifters.add(
                    shifterPosition
            );

            for (ShifterMovementResource.MovementEntry movementEntry
                    : movement.entries()) {

                Vector3i source =
                        movementEntry.sourcePosition();

                Vector3i direction =
                        movementEntry.targetPosition()
                                .sub(source);

                Vector3i origin =
                        multiblockOrigin(
                                world,
                                source
                        );

                // This should already have been validated.
                if (origin == null) {
                    return;
                }

                Vector3i physicalTarget =
                        new Vector3i(origin)
                                .add(direction);

                Vector3i existingTarget =
                        blocksToPrepare.putIfAbsent(
                                origin,
                                physicalTarget
                        );

                /*
                 * Should be impossible after queue validation.
                 * Do not mutate the world if something unexpected
                 * happened between planning and preparation.
                 */
                if (existingTarget != null
                        && !existingTarget.equals(physicalTarget)) {

                    return;
                }
            }
        }

        if (!preparedShifters.isEmpty()) {

            // --------------------------------------------------
            // VALIDATE EVERYTHING BEFORE CHANGING THE WORLD
            // --------------------------------------------------

            Map<Vector3i, BlockType> blockTypes =
                    new LinkedHashMap<>();

            Map<Vector3i, Integer> rotations =
                    new LinkedHashMap<>();

            Map<Vector3i, Holder<ChunkStore>> componentSnapshots =
                    new HashMap<>();

            for (Vector3i sourcePosition
                    : blocksToPrepare.keySet()) {

                // Should never already exist because active movement
                // positions are reserved.
                if (movements.movementVisualEntity(sourcePosition) != null) {
                    return;
                }

                BlockType blockType =
                        blockTypeAtLoaded(
                                world,
                                sourcePosition
                        );

                if (blockType == null
                        || blockType.getMaterial() == BlockMaterial.Empty) {

                    return;
                }

                BlockSection section =
                        blockSectionAt(
                                world,
                                sourcePosition
                        );

                if (section == null) {
                    return;
                }

                int blockIndex =
                        ChunkUtil.indexBlock(
                                sourcePosition.x(),
                                sourcePosition.y(),
                                sourcePosition.z()
                        );

                int rotationIndex =
                        section.getRotationIndex(
                                blockIndex
                        );

                blockTypes.put(
                        new Vector3i(sourcePosition),
                        blockType
                );

                rotations.put(
                        new Vector3i(sourcePosition),
                        rotationIndex
                );

                WorldChunk chunk =
                        world.getChunkIfLoaded(
                                ChunkUtil.indexChunkFromBlock(
                                        sourcePosition.x(),
                                        sourcePosition.z()
                                )
                        );

                if (chunk == null) {
                    return;
                }

                Holder<ChunkStore> components =
                        chunk.getBlockComponentHolder(
                                sourcePosition.x(),
                                sourcePosition.y(),
                                sourcePosition.z()
                        );

                if (components != null) {
                    componentSnapshots.put(
                            new Vector3i(sourcePosition),
                            components
                    );
                }
            }

            // --------------------------------------------------
            // CREATE VISUAL ENTITIES
            // --------------------------------------------------

            Map<Vector3i, Ref<EntityStore>> visualEntities =
                    new LinkedHashMap<>();

            for (Vector3i sourcePosition
                    : blocksToPrepare.keySet()) {

                BlockType blockType =
                        blockTypes.get(sourcePosition);

                int rotationIndex =
                        rotations.get(sourcePosition);

                Holder<EntityStore> visualHolder =
                        createMovingBlockVisual(
                                blockType,
                                rotationIndex,
                                sourcePosition
                        );

                Ref<EntityStore> visualRef =
                        commandBuffer.addEntity(
                                visualHolder,
                                AddReason.SPAWN
                        );

                visualEntities.put(
                        new Vector3i(sourcePosition),
                        visualRef
                );
            }

            // --------------------------------------------------
            // REMOVE REAL BLOCKS
            // --------------------------------------------------

            for (Vector3i sourcePosition
                    : blocksToPrepare.keySet()) {

                WorldChunk sourceChunk =
                        loadedChunkAt(
                                world,
                                sourcePosition
                        );

                if (sourceChunk == null) {
                    return;
                }

                sourceChunk.setBlock(
                        sourcePosition.x(),
                        sourcePosition.y(),
                        sourcePosition.z(),
                        BlockType.EMPTY_KEY,
                        4
                );

            }

            // --------------------------------------------------
            // REGISTER RUNTIME MOVEMENT DATA
            // --------------------------------------------------

            for (Vector3i sourcePosition
                    : blocksToPrepare.keySet()) {

                movements.setMovementBlockComponents(
                        sourcePosition,
                        componentSnapshots.get(sourcePosition)
                );

                movements.setMovementVisualEntity(
                        sourcePosition,
                        visualEntities.get(sourcePosition)
                );

                movements.setMovementBlockType(
                        sourcePosition,
                        blockTypes.get(sourcePosition).getId()
                );

                movements.setMovementBlockRotation(
                        sourcePosition,
                        rotations.get(sourcePosition)
                );
            }

            // Every physical block has now successfully entered
            // the moving-entity state.
            for (Vector3i shifterPosition : preparedShifters) {

                movements.setMovementStage(
                        shifterPosition,
                        ShifterMovementResource.MovementStage.MOVING
                );
            }
        }


        // --------------------------------------------------
        // PROCESS ACTIVE STAGES
        // --------------------------------------------------

        for (Map.Entry<Vector3i, ShifterMovementResource.ActiveMovement> entry
                : activeMovements.entrySet()) {

            Vector3i shifterPosition =
                    entry.getKey();

            ShifterMovementResource.ActiveMovement movement =
                    entry.getValue();

            switch (movement.stage()) {

                case PREPARED -> {
                    /*
                     * Already handled globally above.
                     */
                }

                case MOVING -> {

                    int nextTick =
                            Math.min(
                                    movement.progressTicks() + 1,
                                    movement.durationTicks()
                            );

                    double progress =
                            (double) nextTick
                                    / movement.durationTicks();

                    for (ShifterMovementResource.MovementEntry movementEntry
                            : movement.entries()) {

                        Vector3i source =
                                movementEntry.sourcePosition();

                        Ref<EntityStore> visualEntity =
                                movements.movementVisualEntity(source);

                        if (visualEntity == null
                                || !visualEntity.isValid()) {
                            continue;
                        }

                        Vector3i target =
                                movementEntry.targetPosition();

                        Vector3d position =
                                new Vector3d(
                                        source.x() + 0.5,
                                        source.y() + 0.5,
                                        source.z() + 0.5
                                );

                        position.lerp(
                                new Vector3d(
                                        target.x() + 0.5,
                                        target.y() + 0.5,
                                        target.z() + 0.5
                                ),
                                progress
                        );

                        TransformComponent transform =
                                commandBuffer.getComponent(
                                        visualEntity,
                                        TransformComponent.getComponentType()
                                );

                        if (transform != null) {
                            transform.setPosition(position);
                        }
                    }

                    movements.advanceMovement(
                            shifterPosition
                    );

                    if (nextTick >= movement.durationTicks()) {
                        movements.setMovementStage(
                                shifterPosition,
                                ShifterMovementResource.MovementStage.COMMITTING
                        );
                    }
                }

                case COMMITTING -> {

                    for (ShifterMovementResource.MovementEntry movementEntry
                            : movement.entries()) {

                        Vector3i source =
                                movementEntry.sourcePosition();

                        String blockTypeKey =
                                movements.movementBlockType(source);

                        Integer rotationIndex =
                                movements.movementBlockRotation(source);

                        if (blockTypeKey == null
                                || rotationIndex == null) {
                            continue;
                        }

                        Vector3i target =
                                movementEntry.targetPosition();

                        BlockType blockType =
                                BlockType.getAssetMap()
                                        .getAsset(blockTypeKey);

                        if (blockType == null) {
                            continue;
                        }

                        long targetChunkIndex =
                                ChunkUtil.indexChunkFromBlock(
                                        target.x(),
                                        target.z()
                                );

                        WorldChunk targetChunk =
                                world.getChunkIfLoaded(
                                        targetChunkIndex
                                );

                        if (targetChunk == null) {
                            return;
                        }

                        Holder<ChunkStore> components =
                                movements.movementBlockComponents(source);

                        int settings = 4;

                        if (components != null) {
                            settings |= 2;
                        }

                        int blockId =
                                BlockType.getAssetMap()
                                        .getIndex(blockTypeKey);

                        targetChunk.setBlock(
                                target.x(),
                                target.y(),
                                target.z(),
                                blockId,
                                blockType,
                                rotationIndex,
                                0,
                                settings
                        );

                        if (components != null) {
                            targetChunk.setState(
                                    target.x(),
                                    target.y(),
                                    target.z(),
                                    blockType,
                                    rotationIndex,
                                    components.clone()
                            );
                        }

                        Ref<EntityStore> visualEntity =
                                movements.movementVisualEntity(source);

                        if (visualEntity != null
                                && visualEntity.isValid()) {

                            commandBuffer.removeEntity(
                                    visualEntity,
                                    RemoveReason.REMOVE
                            );
                        }

                        movements.clearMovementRuntime(
                                source
                        );
                    }

                    // Movement energy -> normal active Shifter energy.
                    NetworkResource networks =
                            world.getChunkStore()
                                    .getStore()
                                    .getResource(
                                            ForcesOfGraviumPlugin.NETWORK_RESOURCE_TYPE
                                    );

                    long networkId =
                            networks.networkAt(shifterPosition);

                    if (networkId != NetworkResource.NO_NETWORK) {

                        networks.setEnergyDelta(
                                networkId,
                                shifterPosition,
                                -BASE_ENERGY_COST
                        );

                        if (!networks.isFailing(networkId)) {
                            EnergyManager.checkNetwork(
                                    world,
                                    shifterPosition
                            );
                        }
                    }

                    movements.finishMovement(
                            shifterPosition
                    );
                }

                case ROLLING_BACK -> {
                    // später
                }
            }
        }
    }

    private static @Nonnull Holder<EntityStore> createMovingBlockVisual(
            @Nonnull BlockType blockType,
            int rotationIndex,
            @Nonnull Vector3i position
    ) {
        RotationTuple rotation =
                RotationTuple.get(rotationIndex);

        Holder<EntityStore> holder =
                EntityStore.REGISTRY.newHolder();

        holder.addComponent(
                com.hypixel.hytale.server.core.entity.entities.BlockEntity
                        .getComponentType(),
                new com.hypixel.hytale.server.core.entity.entities.BlockEntity(
                        blockType.getId()
                )
        );

        holder.addComponent(
                TransformComponent.getComponentType(),
                new TransformComponent(
                        new Vector3d(
                                position.x() + 0.5,
                                position.y() + 0.5,
                                position.z() + 0.5
                        ),
                        new Rotation3f()
                )
        );

        holder.addComponent(
                HeadRotation.getComponentType(),
                new HeadRotation(
                        new Rotation3f(
                                (float) rotation.pitch().getRadians(),
                                (float) rotation.yaw().getRadians()
                                        + (float) Math.PI,
                                (float) rotation.roll().getRadians()
                        )
                )
        );

        holder.ensureComponent(
                UUIDComponent.getComponentType()
        );

        BlockBoundingBoxes hitboxes =
                BlockBoundingBoxes.getAssetMap()
                        .getAsset(
                                blockType.getHitboxTypeIndex()
                        );

        if (hitboxes != null) {

            Box blockBox =
                    hitboxes.get(rotationIndex)
                            .getBoundingBox();

            Box entityBox =
                    new Box(blockBox);

            entityBox.offset(
                    -0.5,
                    0.0,
                    -0.5
            );

            holder.addComponent(
                    BoundingBox.getComponentType(),
                    new BoundingBox(entityBox)
            );
        }

        HitboxCollisionConfig collisionConfig =
                HitboxCollisionConfig.getAssetMap()
                        .getAsset("Gravium_Moving_Block");

        if (collisionConfig != null) {
            holder.addComponent(
                    HitboxCollision.getComponentType(),
                    new HitboxCollision(collisionConfig)
            );
        }

        return holder;
    }

    private static @Nullable Vector3i multiblockOrigin(
            @Nonnull World world,
            @Nonnull Vector3i position
    ) {
        BlockSection section =
                blockSectionAt(world, position);

        if (section == null) {
            return null;
        }

        int filler =
                section.getFiller(
                        ChunkUtil.indexBlock(
                                position.x(),
                                position.y(),
                                position.z()
                        )
                );

        if (filler == FillerBlockUtil.NO_FILLER) {
            return new Vector3i(position);
        }

        return new Vector3i(position).sub(
                FillerBlockUtil.unpackX(filler),
                FillerBlockUtil.unpackY(filler),
                FillerBlockUtil.unpackZ(filler)
        );
    }

    private static @Nullable Set<Vector3i> multiblockCells(
            @Nonnull World world,
            @Nonnull Vector3i origin
    ) {
        BlockSection section =
                blockSectionAt(world, origin);

        if (section == null) {
            return null;
        }

        int index =
                ChunkUtil.indexBlock(
                        origin.x(),
                        origin.y(),
                        origin.z()
                );

        int blockId = section.get(index);
        int rotation = section.getRotationIndex(index);

        var footprint =
                FillerBlockUtil.multiCellFootprint(
                        blockId,
                        rotation
                );

        Set<Vector3i> cells = new HashSet<>();

        cells.add(new Vector3i(origin));

        if (footprint == null) {
            return cells;
        }

        FillerBlockUtil.forEachFillerBlock(
                footprint,
                (x, y, z) -> cells.add(
                        new Vector3i(origin).add(x, y, z)
                )
        );

        // Do not validate an incomplete or unloaded footprint.
        for (Vector3i cell : cells) {
            if (blockSectionAt(world, cell) == null) {
                return null;
            }
        }

        return cells;
    }

    private static @Nullable BlockSection blockSectionAt(
            @Nonnull World world,
            @Nonnull Vector3i position
    ) {
        Ref<ChunkStore> sectionRef =
                world.getChunkStore()
                        .getChunkSectionReferenceAtBlock(
                                position.x(),
                                position.y(),
                                position.z()
                        );

        if (sectionRef == null || !sectionRef.isValid()) {
            return null;
        }

        return sectionRef.getStore().getComponent(
                sectionRef,
                BlockSection.getComponentType()
        );
    }

    private static @Nullable WorldChunk loadedChunkAt(
            @Nonnull World world,
            @Nonnull Vector3i position
    ) {
        return world.getChunkIfLoaded(
                ChunkUtil.indexChunkFromBlock(
                        position.x(),
                        position.z()
                )
        );
    }

    private static @Nullable BlockType blockTypeAtLoaded(
            @Nonnull World world,
            @Nonnull Vector3i position
    ) {
        WorldChunk chunk =
                loadedChunkAt(
                        world,
                        position
                );

        if (chunk == null) {
            return null;
        }

        return chunk.getBlockType(
                position.x(),
                position.y(),
                position.z()
        );
    }

    private static int movementEnergyCost(
            int blockCount
    ) {
        return BASE_ENERGY_COST
                + 2 * blockCount
                + blockCount * blockCount;
    }

    private static boolean isUnbreakable(
            @Nonnull BlockType block
    ) {
        // Actual air is never unbreakable.
        if (BlockType.EMPTY_KEY.equals(block.getId())) {
            return false;
        }

        var gathering = block.getGathering();

        return gathering == null
                || (gathering.getBreaking() == null
                && gathering.getHarvest() == null
                && gathering.getSoft() == null);
    }

}
