package dev.moxinat.forcesofgravium.data;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.EnumCodec;
import com.hypixel.hytale.codec.codecs.array.ArrayCodec;
import com.hypixel.hytale.codec.store.StoredCodec;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Resource;
import com.hypixel.hytale.math.vector.Vector3iUtil;
import com.hypixel.hytale.server.core.entity.reference.PersistentRef;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.moxinat.forcesofgravium.signal.SignalState;

import org.joml.Vector3i;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


public final class ShifterMovementResource
        implements Resource<ChunkStore> {


    // --------------------------------------------------
    // CODECS
    // --------------------------------------------------

    private static final ArrayCodec<Vector3i> POSITION_ARRAY_CODEC =
            ArrayCodec.ofBuilderCodec(
                    Vector3iUtil.CODEC,
                    Vector3i[]::new
            );

    private static final ArrayCodec<MovementEntry> MOVEMENT_ENTRY_ARRAY_CODEC =
            ArrayCodec.ofBuilderCodec(
                    MovementEntry.CODEC,
                    MovementEntry[]::new
            );

    private static final ArrayCodec<ActiveMovement> ACTIVE_MOVEMENT_ARRAY_CODEC =
            ArrayCodec.ofBuilderCodec(
                    ActiveMovement.CODEC,
                    ActiveMovement[]::new
            );

    private static final ArrayCodec<HeldBlockData> HELD_BLOCK_ARRAY_CODEC =
            ArrayCodec.ofBuilderCodec(
                    HeldBlockData.CODEC,
                    HeldBlockData[]::new
            );

    private static final ArrayCodec<MovementBlockComponentsData> MOVEMENT_BLOCK_COMPONENTS_ARRAY_CODEC =
            ArrayCodec.ofBuilderCodec(
                    MovementBlockComponentsData.CODEC,
                    MovementBlockComponentsData[]::new
            );

    private static final ArrayCodec<MovementVisualData> MOVEMENT_VISUAL_ARRAY_CODEC =
            ArrayCodec.ofBuilderCodec(
                    MovementVisualData.CODEC,
                    MovementVisualData[]::new
            );

    private static final ArrayCodec<MovementBlockTypeData> MOVEMENT_BLOCK_TYPE_ARRAY_CODEC =
            ArrayCodec.ofBuilderCodec(
                    MovementBlockTypeData.CODEC,
                    MovementBlockTypeData[]::new
            );

    private static final ArrayCodec<MovementBlockRotationData> MOVEMENT_BLOCK_ROTATION_ARRAY_CODEC =
            ArrayCodec.ofBuilderCodec(
                    MovementBlockRotationData.CODEC,
                    MovementBlockRotationData[]::new
            );

    private static final ArrayCodec<MovementNodeEnergyData> MOVEMENT_NODE_ENERGY_ARRAY_CODEC =
            ArrayCodec.ofBuilderCodec(
                    MovementNodeEnergyData.CODEC,
                    MovementNodeEnergyData[]::new
            );


    public static final BuilderCodec<ShifterMovementResource> CODEC =
            BuilderCodec.builder(
                            ShifterMovementResource.class,
                            ShifterMovementResource::new
                    )

                    .append(
                            new KeyedCodec<>(
                                    "PushShifters",
                                    POSITION_ARRAY_CODEC
                            ),
                            ShifterMovementResource::setPushShifters,
                            ShifterMovementResource::getPushShifters
                    )
                    .add()

                    .append(
                            new KeyedCodec<>(
                                    "PullShifters",
                                    POSITION_ARRAY_CODEC
                            ),
                            ShifterMovementResource::setPullShifters,
                            ShifterMovementResource::getPullShifters
                    )
                    .add()

                    .append(
                            new KeyedCodec<>(
                                    "ActiveMovements",
                                    ACTIVE_MOVEMENT_ARRAY_CODEC
                            ),
                            ShifterMovementResource::setActiveMovements,
                            ShifterMovementResource::getActiveMovements
                    )
                    .add()

                    .append(
                            new KeyedCodec<>(
                                    "NextMovementId",
                                    Codec.LONG
                            ),
                            (resource, value) ->
                                    resource.nextMovementId = value,
                            resource -> resource.nextMovementId
                    )
                    .add()

                    .append(
                            new KeyedCodec<>(
                                    "HeldBlocks",
                                    HELD_BLOCK_ARRAY_CODEC
                            ),
                            ShifterMovementResource::setHeldBlocks,
                            ShifterMovementResource::getHeldBlocks
                    )
                    .add()

                    .append(
                            new KeyedCodec<>(
                                    "MovementBlockComponents",
                                    MOVEMENT_BLOCK_COMPONENTS_ARRAY_CODEC
                            ),
                            ShifterMovementResource::setMovementBlockComponentsData,
                            ShifterMovementResource::getMovementBlockComponentsData
                    )
                    .add()

                    .append(
                            new KeyedCodec<>(
                                    "MovementVisuals",
                                    MOVEMENT_VISUAL_ARRAY_CODEC
                            ),
                            ShifterMovementResource::setMovementVisualData,
                            ShifterMovementResource::getMovementVisualData
                    )
                    .add()

                    .append(
                            new KeyedCodec<>(
                                    "MovementBlockTypes",
                                    MOVEMENT_BLOCK_TYPE_ARRAY_CODEC
                            ),
                            ShifterMovementResource::setMovementBlockTypeData,
                            ShifterMovementResource::getMovementBlockTypeData
                    )
                    .add()

                    .append(
                            new KeyedCodec<>(
                                    "MovementBlockRotations",
                                    MOVEMENT_BLOCK_ROTATION_ARRAY_CODEC
                            ),
                            ShifterMovementResource::setMovementBlockRotationData,
                            ShifterMovementResource::getMovementBlockRotationData
                    )
                    .add()

                    .append(
                            new KeyedCodec<>(
                                    "MovementNodeEnergyDeltas",
                                    MOVEMENT_NODE_ENERGY_ARRAY_CODEC
                            ),
                            ShifterMovementResource::setMovementNodeEnergyData,
                            ShifterMovementResource::getMovementNodeEnergyData
                    )
                    .add()

                    .build();


    // --------------------------------------------------
    // DATA
    // --------------------------------------------------

    private final Set<Vector3i> pushShifters =
            new LinkedHashSet<>();

    private final Set<Vector3i> pullShifters =
            new LinkedHashSet<>();

    private final Map<Vector3i, ActiveMovement> activeMovements =
            new HashMap<>();

    private final Map<Vector3i, Holder<ChunkStore>> movementBlockComponents =
            new HashMap<>();

    private final Map<Vector3i, PersistentRef> movementVisualEntities =
            new HashMap<>();

    private final Map<Vector3i, String> movementBlockTypes =
            new HashMap<>();

    private final Map<Vector3i, Integer> movementBlockRotations =
            new HashMap<>();

    /*
     * Present only for moved FoG nodes.
     * The value is the node's NetworkResource energy delta at its source
     * before the source is removed from the network.
     */
    private final Map<Vector3i, Integer> movementNodeEnergyDeltas =
            new HashMap<>();

    private final Map<Vector3i, Vector3i> heldBlocks =
            new HashMap<>();

    private long nextMovementId = 1L;


    // --------------------------------------------------
    // CONSTRUCTORS
    // --------------------------------------------------

    public ShifterMovementResource() {
    }


    private ShifterMovementResource(
            @Nonnull ShifterMovementResource other
    ) {
        pushShifters.addAll(
                copyPositions(other.pushShifters)
        );

        pullShifters.addAll(
                copyPositions(other.pullShifters)
        );

        for (Map.Entry<Vector3i, ActiveMovement> entry
                : other.activeMovements.entrySet()) {

            activeMovements.put(
                    new Vector3i(entry.getKey()),
                    new ActiveMovement(entry.getValue())
            );
        }

        for (Map.Entry<Vector3i, Holder<ChunkStore>> entry
                : other.movementBlockComponents.entrySet()) {

            movementBlockComponents.put(
                    new Vector3i(entry.getKey()),
                    entry.getValue().clone()
            );
        }

        for (Map.Entry<Vector3i, PersistentRef> entry
                : other.movementVisualEntities.entrySet()) {

            movementVisualEntities.put(
                    new Vector3i(entry.getKey()),
                    new PersistentRef(
                            entry.getValue().getUuid()
                    )
            );
        }

        for (Map.Entry<Vector3i, String> entry
                : other.movementBlockTypes.entrySet()) {

            movementBlockTypes.put(
                    new Vector3i(entry.getKey()),
                    entry.getValue()
            );
        }

        for (Map.Entry<Vector3i, Integer> entry
                : other.movementBlockRotations.entrySet()) {

            movementBlockRotations.put(
                    new Vector3i(entry.getKey()),
                    entry.getValue()
            );
        }

        for (Map.Entry<Vector3i, Integer> entry
                : other.movementNodeEnergyDeltas.entrySet()) {

            movementNodeEnergyDeltas.put(
                    new Vector3i(entry.getKey()),
                    entry.getValue()
            );
        }

        for (Map.Entry<Vector3i, Vector3i> entry
                : other.heldBlocks.entrySet()) {

            heldBlocks.put(
                    new Vector3i(entry.getKey()),
                    new Vector3i(entry.getValue())
            );
        }

        nextMovementId = other.nextMovementId;
    }


    // --------------------------------------------------
    // SHIFTER STATES
    // --------------------------------------------------

    public void setShifterState(
            @Nonnull Vector3i position,
            @Nonnull SignalState state
    ) {
        pushShifters.remove(position);
        pullShifters.remove(position);

        if (state != SignalState.PULL) {
            releaseHeldBlocks(position);
        }

        switch (state) {

            case PUSH ->
                    pushShifters.add(
                            new Vector3i(position)
                    );

            case PULL ->
                    pullShifters.add(
                            new Vector3i(position)
                    );

            case OFF -> {
            }
        }
    }


    public @Nonnull Set<Vector3i> pushShifters() {
        return copyPositions(pushShifters);
    }


    public @Nonnull Set<Vector3i> pullShifters() {
        return copyPositions(pullShifters);
    }


    public boolean isActiveShifter(
            @Nonnull Vector3i position
    ) {
        return pushShifters.contains(position)
                || pullShifters.contains(position);
    }


    // --------------------------------------------------
    // MOVEMENT ACCESS
    // --------------------------------------------------

    public boolean hasActiveMovement(
            @Nonnull Vector3i shifterPosition
    ) {
        return activeMovements.containsKey(
                shifterPosition
        );
    }


    public @Nullable ActiveMovement movementAt(
            @Nonnull Vector3i shifterPosition
    ) {
        ActiveMovement movement =
                activeMovements.get(shifterPosition);

        return movement == null
                ? null
                : new ActiveMovement(movement);
    }


    public @Nonnull Map<Vector3i, ActiveMovement> activeMovements() {

        Map<Vector3i, ActiveMovement> result =
                new HashMap<>();

        for (Map.Entry<Vector3i, ActiveMovement> entry
                : activeMovements.entrySet()) {

            result.put(
                    new Vector3i(entry.getKey()),
                    new ActiveMovement(entry.getValue())
            );
        }

        return result;
    }


    // --------------------------------------------------
    // MOVEMENT MANAGEMENT
    // --------------------------------------------------

    public boolean startMovement(
            @Nonnull Vector3i shifterPosition,
            @Nonnull List<MovementEntry> entries,
            @Nonnull Collection<Vector3i> multiblockBases,
            int durationTicks
    ) {
        if (hasActiveMovement(shifterPosition)
                || !isActiveShifter(shifterPosition)
                || entries.isEmpty()
                || durationTicks <= 0) {

            return false;
        }

        for (MovementEntry entry : entries) {

            if (entry == null
                    || !entry.shifterPosition()
                    .equals(shifterPosition)) {

                return false;
            }
        }

        ActiveMovement movement =
                new ActiveMovement(
                        nextMovementId++,
                        shifterPosition,
                        entries,
                        multiblockBases,
                        durationTicks
                );

        activeMovements.put(
                new Vector3i(shifterPosition),
                movement
        );

        return true;
    }


    public void setMovementStage(
            @Nonnull Vector3i shifterPosition,
            @Nonnull MovementStage stage
    ) {
        ActiveMovement movement =
                activeMovements.get(shifterPosition);

        if (movement == null) {
            return;
        }

        movement.stage = stage;
    }


    public void advanceMovement(
            @Nonnull Vector3i shifterPosition
    ) {
        ActiveMovement movement =
                activeMovements.get(shifterPosition);

        if (movement == null
                || movement.stage != MovementStage.MOVING) {

            return;
        }

        movement.progressTicks =
                Math.min(
                        movement.progressTicks + 1,
                        movement.durationTicks
                );
    }

    public boolean holdBlock(
            @Nonnull Vector3i blockPosition,
            @Nonnull Vector3i shifterPosition
    ) {
        Vector3i existingHolder =
                heldBlocks.get(blockPosition);

        // Another Shifter already holds this block.
        if (existingHolder != null
                && !existingHolder.equals(shifterPosition)) {

            return false;
        }

        // One Shifter can only hold one block.
        releaseHeldBlocks(shifterPosition);

        heldBlocks.put(
                new Vector3i(blockPosition),
                new Vector3i(shifterPosition)
        );

        return true;
    }


    public void releaseHeldBlocks(
            @Nonnull Vector3i shifterPosition
    ) {
        heldBlocks.entrySet().removeIf(
                entry ->
                        entry.getValue().equals(shifterPosition)
        );
    }


    public @Nullable Vector3i holdingShifter(
            @Nonnull Vector3i blockPosition
    ) {
        Vector3i position =
                heldBlocks.get(blockPosition);

        return position == null
                ? null
                : new Vector3i(position);
    }

    public @Nullable Vector3i heldBlockFor(
            @Nonnull Vector3i shifterPosition
    ) {
        for (Map.Entry<Vector3i, Vector3i> entry : heldBlocks.entrySet()) {
            if (entry.getValue().equals(shifterPosition)) {
                return new Vector3i(entry.getKey());
            }
        }

        return null;
    }


    /**
     * Call only after movement completion
     * or a successful rollback.
     */
    public void finishMovement(
            @Nonnull Vector3i shifterPosition
    ) {
        activeMovements.remove(
                shifterPosition
        );
    }

    // --------------------------------------------------
    // MOVEMENT RUNTIME DATA
    // --------------------------------------------------

    public void setMovementBlockComponents(
            @Nonnull Vector3i sourcePosition,
            @Nullable Holder<ChunkStore> components
    ) {
        if (components == null) {
            movementBlockComponents.remove(
                    sourcePosition
            );

            return;
        }

        movementBlockComponents.put(
                new Vector3i(sourcePosition),
                components
        );
    }

    public void setMovementBlockType(
            @Nonnull Vector3i sourcePosition,
            @Nonnull String blockTypeKey
    ) {
        movementBlockTypes.put(
                new Vector3i(sourcePosition),
                blockTypeKey
        );
    }


    public @Nullable String movementBlockType(
            @Nonnull Vector3i sourcePosition
    ) {
        return movementBlockTypes.get(
                sourcePosition
        );
    }


    public void setMovementBlockRotation(
            @Nonnull Vector3i sourcePosition,
            int rotationIndex
    ) {
        movementBlockRotations.put(
                new Vector3i(sourcePosition),
                rotationIndex
        );
    }


    public @Nullable Integer movementBlockRotation(
            @Nonnull Vector3i sourcePosition
    ) {
        return movementBlockRotations.get(
                sourcePosition
        );
    }


    public @Nullable Holder<ChunkStore> movementBlockComponents(
            @Nonnull Vector3i sourcePosition
    ) {
        return movementBlockComponents.get(
                sourcePosition
        );
    }


    public void setMovementNodeEnergyDelta(
            @Nonnull Vector3i sourcePosition,
            int energyDelta
    ) {
        movementNodeEnergyDeltas.put(
                new Vector3i(sourcePosition),
                energyDelta
        );
    }


    public @Nullable Integer movementNodeEnergyDelta(
            @Nonnull Vector3i sourcePosition
    ) {
        return movementNodeEnergyDeltas.get(
                sourcePosition
        );
    }


    public void setMovementVisualEntity(
            @Nonnull Vector3i sourcePosition,
            @Nonnull Ref<EntityStore> entityRef,
            @Nonnull ComponentAccessor<EntityStore> componentAccessor
    ) {
        PersistentRef persistentRef =
                new PersistentRef();

        persistentRef.setEntity(
                entityRef,
                componentAccessor
        );

        movementVisualEntities.put(
                new Vector3i(sourcePosition),
                persistentRef
        );
    }


    public @Nullable Ref<EntityStore> movementVisualEntity(
            @Nonnull Vector3i sourcePosition,
            @Nonnull ComponentAccessor<EntityStore> componentAccessor
    ) {
        PersistentRef persistentRef =
                movementVisualEntities.get(
                        sourcePosition
                );

        return persistentRef == null
                ? null
                : persistentRef.getEntity(
                        componentAccessor
                );
    }


    public void clearMovementRuntime(
            @Nonnull Vector3i sourcePosition
    ) {
        movementBlockComponents.remove(
                sourcePosition
        );

        movementVisualEntities.remove(
                sourcePosition
        );

        movementBlockTypes.remove(
                sourcePosition
        );

        movementBlockRotations.remove(
                sourcePosition
        );

        movementNodeEnergyDeltas.remove(
                sourcePosition
        );
    }


    // --------------------------------------------------
    // SERIALIZATION
    // --------------------------------------------------

    private Vector3i[] getPushShifters() {
        return pushShifters.toArray(
                Vector3i[]::new
        );
    }


    private void setPushShifters(
            Vector3i[] positions
    ) {
        pushShifters.clear();

        if (positions == null) {
            return;
        }

        for (Vector3i position : positions) {

            if (position != null) {
                pushShifters.add(
                        new Vector3i(position)
                );
            }
        }
    }


    private Vector3i[] getPullShifters() {
        return pullShifters.toArray(
                Vector3i[]::new
        );
    }


    private void setPullShifters(
            Vector3i[] positions
    ) {
        pullShifters.clear();

        if (positions == null) {
            return;
        }

        for (Vector3i position : positions) {

            if (position != null) {
                pullShifters.add(
                        new Vector3i(position)
                );
            }
        }
    }

    private HeldBlockData[] getHeldBlocks() {

        HeldBlockData[] result =
                new HeldBlockData[heldBlocks.size()];

        int index = 0;

        for (Map.Entry<Vector3i, Vector3i> entry
                : heldBlocks.entrySet()) {

            result[index++] =
                    new HeldBlockData(
                            entry.getKey(),
                            entry.getValue()
                    );
        }

        return result;
    }


    private void setHeldBlocks(
            HeldBlockData[] loaded
    ) {
        heldBlocks.clear();

        if (loaded == null) {
            return;
        }

        for (HeldBlockData data : loaded) {

            if (data == null
                    || data.blockPosition == null
                    || data.shifterPosition == null) {
                continue;
            }

            heldBlocks.put(
                    new Vector3i(data.blockPosition),
                    new Vector3i(data.shifterPosition)
            );
        }
    }


    private ActiveMovement[] getActiveMovements() {
        return activeMovements.values()
                .toArray(ActiveMovement[]::new);
    }


    private void setActiveMovements(
            ActiveMovement[] movements
    ) {
        activeMovements.clear();

        if (movements == null) {
            return;
        }

        for (ActiveMovement movement : movements) {

            if (movement == null
                    || movement.shifterPosition == null) {

                continue;
            }

            activeMovements.put(
                    new Vector3i(movement.shifterPosition),
                    new ActiveMovement(movement)
            );
        }
    }


    private MovementBlockComponentsData[] getMovementBlockComponentsData() {

        MovementBlockComponentsData[] result =
                new MovementBlockComponentsData[
                        movementBlockComponents.size()
                ];

        int index = 0;

        for (Map.Entry<Vector3i, Holder<ChunkStore>> entry
                : movementBlockComponents.entrySet()) {

            result[index++] =
                    new MovementBlockComponentsData(
                            entry.getKey(),
                            entry.getValue()
                    );
        }

        return result;
    }


    private void setMovementBlockComponentsData(
            MovementBlockComponentsData[] loaded
    ) {
        movementBlockComponents.clear();

        if (loaded == null) {
            return;
        }

        for (MovementBlockComponentsData data : loaded) {

            if (data == null
                    || data.sourcePosition == null
                    || data.components == null) {
                continue;
            }

            movementBlockComponents.put(
                    new Vector3i(data.sourcePosition),
                    data.components
            );
        }
    }


    private MovementVisualData[] getMovementVisualData() {

        MovementVisualData[] result =
                new MovementVisualData[
                        movementVisualEntities.size()
                ];

        int index = 0;

        for (Map.Entry<Vector3i, PersistentRef> entry
                : movementVisualEntities.entrySet()) {

            result[index++] =
                    new MovementVisualData(
                            entry.getKey(),
                            new PersistentRef(
                                    entry.getValue().getUuid()
                            )
                    );
        }

        return result;
    }


    private void setMovementVisualData(
            MovementVisualData[] loaded
    ) {
        movementVisualEntities.clear();

        if (loaded == null) {
            return;
        }

        for (MovementVisualData data : loaded) {

            if (data == null
                    || data.sourcePosition == null
                    || data.persistentRef == null
                    || !data.persistentRef.isValid()) {
                continue;
            }

            movementVisualEntities.put(
                    new Vector3i(data.sourcePosition),
                    data.persistentRef
            );
        }
    }


    private MovementBlockTypeData[] getMovementBlockTypeData() {

        MovementBlockTypeData[] result =
                new MovementBlockTypeData[
                        movementBlockTypes.size()
                ];

        int index = 0;

        for (Map.Entry<Vector3i, String> entry
                : movementBlockTypes.entrySet()) {

            result[index++] =
                    new MovementBlockTypeData(
                            entry.getKey(),
                            entry.getValue()
                    );
        }

        return result;
    }


    private void setMovementBlockTypeData(
            MovementBlockTypeData[] loaded
    ) {
        movementBlockTypes.clear();

        if (loaded == null) {
            return;
        }

        for (MovementBlockTypeData data : loaded) {

            if (data == null
                    || data.sourcePosition == null
                    || data.blockTypeKey == null) {
                continue;
            }

            movementBlockTypes.put(
                    new Vector3i(data.sourcePosition),
                    data.blockTypeKey
            );
        }
    }


    private MovementBlockRotationData[] getMovementBlockRotationData() {

        MovementBlockRotationData[] result =
                new MovementBlockRotationData[
                        movementBlockRotations.size()
                ];

        int index = 0;

        for (Map.Entry<Vector3i, Integer> entry
                : movementBlockRotations.entrySet()) {

            result[index++] =
                    new MovementBlockRotationData(
                            entry.getKey(),
                            entry.getValue()
                    );
        }

        return result;
    }


    private void setMovementBlockRotationData(
            MovementBlockRotationData[] loaded
    ) {
        movementBlockRotations.clear();

        if (loaded == null) {
            return;
        }

        for (MovementBlockRotationData data : loaded) {

            if (data == null
                    || data.sourcePosition == null) {
                continue;
            }

            movementBlockRotations.put(
                    new Vector3i(data.sourcePosition),
                    data.rotationIndex
            );
        }
    }


    private MovementNodeEnergyData[] getMovementNodeEnergyData() {

        MovementNodeEnergyData[] result =
                new MovementNodeEnergyData[
                        movementNodeEnergyDeltas.size()
                ];

        int index = 0;

        for (Map.Entry<Vector3i, Integer> entry
                : movementNodeEnergyDeltas.entrySet()) {

            result[index++] =
                    new MovementNodeEnergyData(
                            entry.getKey(),
                            entry.getValue()
                    );
        }

        return result;
    }


    private void setMovementNodeEnergyData(
            MovementNodeEnergyData[] loaded
    ) {
        movementNodeEnergyDeltas.clear();

        if (loaded == null) {
            return;
        }

        for (MovementNodeEnergyData data : loaded) {

            if (data == null
                    || data.sourcePosition == null) {
                continue;
            }

            movementNodeEnergyDeltas.put(
                    new Vector3i(data.sourcePosition),
                    data.energyDelta
            );
        }
    }


    // --------------------------------------------------
    // UTILITY
    // --------------------------------------------------

    private static @Nonnull Set<Vector3i> copyPositions(
            @Nonnull Collection<Vector3i> positions
    ) {
        Set<Vector3i> result =
                new LinkedHashSet<>();

        for (Vector3i position : positions) {
            result.add(
                    new Vector3i(position)
            );
        }

        return result;
    }


    @Override
    public @Nonnull ShifterMovementResource clone() {
        return new ShifterMovementResource(this);
    }


    // ==================================================
    // PERSISTED MOVEMENT BLOCK DATA
    // ==================================================

    private static final class MovementBlockComponentsData {

        private static final BuilderCodec<MovementBlockComponentsData> CODEC =
                BuilderCodec.builder(
                                MovementBlockComponentsData.class,
                                MovementBlockComponentsData::new
                        )

                        .append(
                                new KeyedCodec<>(
                                        "SourcePosition",
                                        Vector3iUtil.CODEC
                                ),
                                (data, value) ->
                                        data.sourcePosition =
                                                new Vector3i(value),
                                data -> data.sourcePosition
                        )
                        .add()

                        .append(
                                new KeyedCodec<>(
                                        "Components",
                                        new StoredCodec<>(
                                                ChunkStore.HOLDER_CODEC_KEY
                                        )
                                ),
                                (data, value) ->
                                        data.components = value,
                                data -> data.components
                        )
                        .add()

                        .build();

        private Vector3i sourcePosition =
                new Vector3i();

        private Holder<ChunkStore> components;


        public MovementBlockComponentsData() {
        }


        private MovementBlockComponentsData(
                @Nonnull Vector3i sourcePosition,
                @Nonnull Holder<ChunkStore> components
        ) {
            this.sourcePosition =
                    new Vector3i(sourcePosition);

            this.components = components;
        }
    }


    private static final class MovementVisualData {

        private static final BuilderCodec<MovementVisualData> CODEC =
                BuilderCodec.builder(
                                MovementVisualData.class,
                                MovementVisualData::new
                        )

                        .append(
                                new KeyedCodec<>(
                                        "SourcePosition",
                                        Vector3iUtil.CODEC
                                ),
                                (data, value) ->
                                        data.sourcePosition =
                                                new Vector3i(value),
                                data -> data.sourcePosition
                        )
                        .add()

                        .append(
                                new KeyedCodec<>(
                                        "PersistentRef",
                                        PersistentRef.CODEC
                                ),
                                (data, value) ->
                                        data.persistentRef = value,
                                data -> data.persistentRef
                        )
                        .add()

                        .build();

        private Vector3i sourcePosition =
                new Vector3i();

        private PersistentRef persistentRef =
                new PersistentRef();


        public MovementVisualData() {
        }


        private MovementVisualData(
                @Nonnull Vector3i sourcePosition,
                @Nonnull PersistentRef persistentRef
        ) {
            this.sourcePosition =
                    new Vector3i(sourcePosition);

            this.persistentRef =
                    persistentRef;
        }
    }


    private static final class MovementBlockTypeData {

        private static final BuilderCodec<MovementBlockTypeData> CODEC =
                BuilderCodec.builder(
                                MovementBlockTypeData.class,
                                MovementBlockTypeData::new
                        )

                        .append(
                                new KeyedCodec<>(
                                        "SourcePosition",
                                        Vector3iUtil.CODEC
                                ),
                                (data, value) ->
                                        data.sourcePosition =
                                                new Vector3i(value),
                                data -> data.sourcePosition
                        )
                        .add()

                        .append(
                                new KeyedCodec<>(
                                        "BlockTypeKey",
                                        Codec.STRING
                                ),
                                (data, value) ->
                                        data.blockTypeKey = value,
                                data -> data.blockTypeKey
                        )
                        .add()

                        .build();

        private Vector3i sourcePosition =
                new Vector3i();

        private String blockTypeKey;


        public MovementBlockTypeData() {
        }


        private MovementBlockTypeData(
                @Nonnull Vector3i sourcePosition,
                @Nonnull String blockTypeKey
        ) {
            this.sourcePosition =
                    new Vector3i(sourcePosition);

            this.blockTypeKey =
                    blockTypeKey;
        }
    }


    private static final class MovementBlockRotationData {

        private static final BuilderCodec<MovementBlockRotationData> CODEC =
                BuilderCodec.builder(
                                MovementBlockRotationData.class,
                                MovementBlockRotationData::new
                        )

                        .append(
                                new KeyedCodec<>(
                                        "SourcePosition",
                                        Vector3iUtil.CODEC
                                ),
                                (data, value) ->
                                        data.sourcePosition =
                                                new Vector3i(value),
                                data -> data.sourcePosition
                        )
                        .add()

                        .append(
                                new KeyedCodec<>(
                                        "RotationIndex",
                                        Codec.INTEGER
                                ),
                                (data, value) ->
                                        data.rotationIndex = value,
                                data -> data.rotationIndex
                        )
                        .add()

                        .build();

        private Vector3i sourcePosition =
                new Vector3i();

        private int rotationIndex;


        public MovementBlockRotationData() {
        }


        private MovementBlockRotationData(
                @Nonnull Vector3i sourcePosition,
                int rotationIndex
        ) {
            this.sourcePosition =
                    new Vector3i(sourcePosition);

            this.rotationIndex =
                    rotationIndex;
        }
    }


    private static final class MovementNodeEnergyData {

        private static final BuilderCodec<MovementNodeEnergyData> CODEC =
                BuilderCodec.builder(
                                MovementNodeEnergyData.class,
                                MovementNodeEnergyData::new
                        )

                        .append(
                                new KeyedCodec<>(
                                        "SourcePosition",
                                        Vector3iUtil.CODEC
                                ),
                                (data, value) ->
                                        data.sourcePosition =
                                                new Vector3i(value),
                                data -> data.sourcePosition
                        )
                        .add()

                        .append(
                                new KeyedCodec<>(
                                        "EnergyDelta",
                                        Codec.INTEGER
                                ),
                                (data, value) ->
                                        data.energyDelta = value,
                                data -> data.energyDelta
                        )
                        .add()

                        .build();

        private Vector3i sourcePosition =
                new Vector3i();

        private int energyDelta;


        public MovementNodeEnergyData() {
        }


        private MovementNodeEnergyData(
                @Nonnull Vector3i sourcePosition,
                int energyDelta
        ) {
            this.sourcePosition =
                    new Vector3i(sourcePosition);

            this.energyDelta =
                    energyDelta;
        }
    }


    // ==================================================
    // MOVEMENT ENTRY
    // ==================================================

    public static final class MovementEntry {

        public static final BuilderCodec<MovementEntry> CODEC =
                BuilderCodec.builder(
                                MovementEntry.class,
                                MovementEntry::new
                        )

                        .append(
                                new KeyedCodec<>(
                                        "ShifterPosition",
                                        Vector3iUtil.CODEC
                                ),
                                (entry, value) ->
                                        entry.shifterPosition =
                                                new Vector3i(value),
                                entry -> entry.shifterPosition
                        )
                        .add()

                        .append(
                                new KeyedCodec<>(
                                        "SourcePosition",
                                        Vector3iUtil.CODEC
                                ),
                                (entry, value) ->
                                        entry.sourcePosition =
                                                new Vector3i(value),
                                entry -> entry.sourcePosition
                        )
                        .add()

                        .append(
                                new KeyedCodec<>(
                                        "TargetPosition",
                                        Vector3iUtil.CODEC
                                ),
                                (entry, value) ->
                                        entry.targetPosition =
                                                new Vector3i(value),
                                entry -> entry.targetPosition
                        )
                        .add()

                        .build();


        private Vector3i shifterPosition =
                new Vector3i();

        private Vector3i sourcePosition =
                new Vector3i();

        private Vector3i targetPosition =
                new Vector3i();


        public MovementEntry() {
        }


        public MovementEntry(
                @Nonnull Vector3i shifterPosition,
                @Nonnull Vector3i sourcePosition,
                @Nonnull Vector3i targetPosition
        ) {
            this.shifterPosition =
                    new Vector3i(shifterPosition);

            this.sourcePosition =
                    new Vector3i(sourcePosition);

            this.targetPosition =
                    new Vector3i(targetPosition);
        }


        public MovementEntry(
                @Nonnull MovementEntry other
        ) {
            this(
                    other.shifterPosition,
                    other.sourcePosition,
                    other.targetPosition
            );
        }


        public @Nonnull Vector3i shifterPosition() {
            return new Vector3i(shifterPosition);
        }


        public @Nonnull Vector3i sourcePosition() {
            return new Vector3i(sourcePosition);
        }


        public @Nonnull Vector3i targetPosition() {
            return new Vector3i(targetPosition);
        }
    }


    // ==================================================
    // MOVEMENT STAGE
    // ==================================================

    public enum MovementStage {

        PREPARED,

        MOVING,

        COMMITTING,

        ROLLING_BACK

    }


    // ==================================================
    // ACTIVE MOVEMENT
    // ==================================================

    public static final class ActiveMovement {

        private static final EnumCodec<MovementStage> STAGE_CODEC =
                new EnumCodec<>(MovementStage.class);


        public static final BuilderCodec<ActiveMovement> CODEC =
                BuilderCodec.builder(
                                ActiveMovement.class,
                                ActiveMovement::new
                        )

                        .append(
                                new KeyedCodec<>(
                                        "MovementId",
                                        Codec.LONG
                                ),
                                (data, value) ->
                                        data.movementId = value,
                                data -> data.movementId
                        )
                        .add()

                        .append(
                                new KeyedCodec<>(
                                        "ShifterPosition",
                                        Vector3iUtil.CODEC
                                ),
                                (data, value) ->
                                        data.shifterPosition =
                                                new Vector3i(value),
                                data -> data.shifterPosition
                        )
                        .add()

                        .append(
                                new KeyedCodec<>(
                                        "Entries",
                                        MOVEMENT_ENTRY_ARRAY_CODEC
                                ),
                                ActiveMovement::setEntries,
                                ActiveMovement::getEntries
                        )
                        .add()

                        .append(
                                new KeyedCodec<>(
                                        "MultiblockBases",
                                        POSITION_ARRAY_CODEC
                                ),
                                ActiveMovement::setMultiblockBases,
                                ActiveMovement::getMultiblockBases
                        )
                        .add()

                        .append(
                                new KeyedCodec<>(
                                        "Stage",
                                        STAGE_CODEC
                                ),
                                (data, value) ->
                                        data.stage = value,
                                data -> data.stage
                        )
                        .add()

                        .append(
                                new KeyedCodec<>(
                                        "ProgressTicks",
                                        Codec.INTEGER
                                ),
                                (data, value) ->
                                        data.progressTicks = value,
                                data -> data.progressTicks
                        )
                        .add()

                        .append(
                                new KeyedCodec<>(
                                        "DurationTicks",
                                        Codec.INTEGER
                                ),
                                (data, value) ->
                                        data.durationTicks = value,
                                data -> data.durationTicks
                        )
                        .add()

                        .build();


        // ----------------------------------------------
        // DATA
        // ----------------------------------------------

        private long movementId;

        private Vector3i shifterPosition =
                new Vector3i();

        private final List<MovementEntry> entries =
                new ArrayList<>();

        private final Set<Vector3i> multiblockBases =
                new LinkedHashSet<>();

        private MovementStage stage =
                MovementStage.PREPARED;

        private int progressTicks;

        private int durationTicks;


        // ----------------------------------------------
        // CONSTRUCTORS
        // ----------------------------------------------

        public ActiveMovement() {
        }


        private ActiveMovement(
                long movementId,
                @Nonnull Vector3i shifterPosition,
                @Nonnull Collection<MovementEntry> entries,
                @Nonnull Collection<Vector3i> multiblockBases,
                int durationTicks
        ) {
            this.movementId = movementId;

            this.shifterPosition =
                    new Vector3i(shifterPosition);

            for (MovementEntry entry : entries) {
                this.entries.add(
                        new MovementEntry(entry)
                );
            }

            this.multiblockBases.addAll(
                    copyPositions(multiblockBases)
            );

            this.durationTicks = durationTicks;
        }


        private ActiveMovement(
                @Nonnull ActiveMovement other
        ) {
            this(
                    other.movementId,
                    other.shifterPosition,
                    other.entries,
                    other.multiblockBases,
                    other.durationTicks
            );

            this.stage = other.stage;

            this.progressTicks =
                    other.progressTicks;
        }


        // ----------------------------------------------
        // ACCESS
        // ----------------------------------------------

        public long movementId() {
            return movementId;
        }


        public @Nonnull Vector3i shifterPosition() {
            return new Vector3i(shifterPosition);
        }


        public @Nonnull List<MovementEntry> entries() {

            List<MovementEntry> result =
                    new ArrayList<>();

            for (MovementEntry entry : entries) {
                result.add(
                        new MovementEntry(entry)
                );
            }

            return result;
        }


        public @Nonnull Set<Vector3i> multiblockBases() {
            return copyPositions(multiblockBases);
        }


        public @Nonnull MovementStage stage() {
            return stage;
        }


        public int progressTicks() {
            return progressTicks;
        }


        public int durationTicks() {
            return durationTicks;
        }


        public boolean isFinished() {
            return progressTicks >= durationTicks;
        }


        // ----------------------------------------------
        // SERIALIZATION
        // ----------------------------------------------

        private MovementEntry[] getEntries() {
            return entries.toArray(
                    MovementEntry[]::new
            );
        }


        private void setEntries(
                MovementEntry[] loadedEntries
        ) {
            entries.clear();

            if (loadedEntries == null) {
                return;
            }

            for (MovementEntry entry : loadedEntries) {

                if (entry != null) {
                    entries.add(
                            new MovementEntry(entry)
                    );
                }
            }
        }


        private Vector3i[] getMultiblockBases() {
            return multiblockBases.toArray(
                    Vector3i[]::new
            );
        }


        private void setMultiblockBases(
                Vector3i[] positions
        ) {
            multiblockBases.clear();

            if (positions == null) {
                return;
            }

            for (Vector3i position : positions) {

                if (position != null) {
                    multiblockBases.add(
                            new Vector3i(position)
                    );
                }
            }
        }
    }

    public static final class HeldBlockData {

        public static final BuilderCodec<HeldBlockData> CODEC =
                BuilderCodec.builder(
                                HeldBlockData.class,
                                HeldBlockData::new
                        )
                        .append(
                                new KeyedCodec<>(
                                        "BlockPosition",
                                        Vector3iUtil.CODEC
                                ),
                                (data, value) ->
                                        data.blockPosition = value,
                                data -> data.blockPosition
                        )
                        .add()
                        .append(
                                new KeyedCodec<>(
                                        "ShifterPosition",
                                        Vector3iUtil.CODEC
                                ),
                                (data, value) ->
                                        data.shifterPosition = value,
                                data -> data.shifterPosition
                        )
                        .add()
                        .build();

        private Vector3i blockPosition = new Vector3i();
        private Vector3i shifterPosition = new Vector3i();

        public HeldBlockData() {
        }

        public HeldBlockData(
                Vector3i blockPosition,
                Vector3i shifterPosition
        ) {
            this.blockPosition = new Vector3i(blockPosition);
            this.shifterPosition = new Vector3i(shifterPosition);
        }
    }
}