package dev.moxinat.forcesofgravium.data;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.EnumCodec;
import com.hypixel.hytale.codec.codecs.array.ArrayCodec;
import com.hypixel.hytale.component.Resource;
import com.hypixel.hytale.math.vector.Vector3iUtil;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

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