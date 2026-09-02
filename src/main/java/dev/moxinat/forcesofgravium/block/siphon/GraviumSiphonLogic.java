package dev.moxinat.forcesofgravium.block.siphon;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.builtin.crafting.component.ProcessingBenchBlock;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import dev.moxinat.forcesofgravium.ForcesOfGraviumPlugin;
import dev.moxinat.forcesofgravium.data.NodeComponent;
import dev.moxinat.forcesofgravium.data.SiphonComponent;
import dev.moxinat.forcesofgravium.signal.SignalState;
import org.joml.Vector3d;
import org.joml.Vector3i;
import com.hypixel.hytale.protocol.BlockMaterial;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackSlotTransaction;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction;
import com.hypixel.hytale.server.core.inventory.transaction.ListTransaction;
import com.hypixel.hytale.server.core.inventory.transaction.MoveTransaction;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.FillerBlockUtil;
import dev.moxinat.forcesofgravium.spatial.ConnectableNeighborResolver;
import dev.moxinat.forcesofgravium.registry.ConnectableRegistry;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;

public final class GraviumSiphonLogic {

    private static final int POWERED_TRANSFER_INTERVAL_TICKS = 30;
    private static final int UNPOWERED_TRANSFER_INTERVAL_TICKS = 60;
    private static final float DROPPED_ITEM_OUTPUT_SPEED = 3.0F;

    private GraviumSiphonLogic() {
    }

    public static void tickSiphon(
            @Nonnull World world,
            @Nonnull Vector3i position,
            @Nonnull NodeComponent node,
            @Nonnull SiphonComponent siphon,
            @Nonnull Store<EntityStore> entityStore
    ) {
        if (node.effectiveState() == SignalState.PULL) {
            return;
        }

        if (!canTransfer(
                world,
                position,
                node,
                siphon
        )) {
            return;
        }

        transferOneItem(
                world,
                position,
                entityStore
        );
    }

    private static boolean canTransfer(
            @Nonnull World world,
            @Nonnull Vector3i position,
            @Nonnull NodeComponent node,
            @Nonnull SiphonComponent siphon
    ) {
        boolean powered =
                node.effectiveState() == SignalState.PUSH;

        if (!powered
                && ConnectableNeighborResolver
                .rotationFor(world, position)
                .pitch() != Rotation.Ninety) {
            return false;
        }

        if (siphon.onCooldown()) {
            siphon.tickCooldown();
            return false;
        }

        siphon.setCooldownTicks(
                powered
                        ? POWERED_TRANSFER_INTERVAL_TICKS
                        : UNPOWERED_TRANSFER_INTERVAL_TICKS
        );

        return true;
    }

    public static @Nonnull SiphonMoveResult transferOneItem(
            @Nonnull World world,
            @Nonnull Vector3i siphonPosition
    ) {
        return transferOneItem(
                world,
                siphonPosition,
                world.getEntityStore().getStore()
        );
    }

    private static @Nonnull SiphonMoveResult transferOneItem(
            @Nonnull World world,
            @Nonnull Vector3i siphonPosition,
            @Nonnull Store<EntityStore> entityStore
    ) {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(siphonPosition, "siphonPosition");

        Vector3i sourcePosition =
                ConnectableNeighborResolver.adjacentPositionForLocalSide(
                        world,
                        siphonPosition,
                        ConnectableRegistry.SIDE_BACK
                );

        Vector3i targetPosition =
                ConnectableNeighborResolver.adjacentPositionForLocalSide(
                        world,
                        siphonPosition,
                        ConnectableRegistry.SIDE_FRONT
                );

        return transferOneItemBetween(
                world,
                sourcePosition,
                targetPosition,
                siphonPosition,
                entityStore
        );
    }

    private static @Nonnull SiphonMoveResult transferOneItemBetween(
            @Nonnull World world,
            @Nonnull Vector3i sourcePosition,
            @Nonnull Vector3i targetPosition,
            @Nonnull Vector3i velocityOriginPosition,
            @Nonnull Store<EntityStore> entityStore
    ) {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(sourcePosition, "sourcePosition");
        Objects.requireNonNull(targetPosition, "targetPosition");
        Objects.requireNonNull(velocityOriginPosition, "velocityOriginPosition");

        SiphonEndpoint target =
                SiphonEndpoint.at(
                        world,
                        targetPosition
                );

        SiphonEndpoint source =
                SiphonEndpoint.at(
                        world,
                        sourcePosition
                );

        ItemContainer sourceContainer =
                source.extractContainer();

        if (sourceContainer == null) {

            if (isDroppableTarget(
                    world,
                    sourcePosition
            )) {
                WorldItemSource worldItemSource =
                        worldItemSourceAt(
                                entityStore,
                                sourcePosition
                        );

                if (worldItemSource == null) {
                    return SiphonMoveResult.SOURCE_EMPTY;
                }

                return transferOneWorldItem(
                        world,
                        worldItemSource,
                        target,
                        entityStore,
                        velocityOriginPosition,
                        targetPosition
                );
            }

            return SiphonMoveResult.NO_SOURCE_CONTAINER;
        }

        if (!target.hasInsertContainer()) {

            if (isDroppableTarget(
                    world,
                    targetPosition
            )) {
                return dropOneItem(
                        sourceContainer,
                        entityStore,
                        velocityOriginPosition,
                        targetPosition
                );
            }

            return SiphonMoveResult.NO_TARGET_CONTAINER;
        }

        return transferOneItem(
                sourceContainer,
                target
        );
    }

    private static boolean isDroppableTarget(@Nonnull World world, @Nonnull Vector3i targetPosition) {
        BlockType blockType = world.getBlockType(targetPosition.x(), targetPosition.y(), targetPosition.z());
        return blockType != null && blockType.getMaterial() == BlockMaterial.Empty;
    }

    private static @Nullable WorldItemSource worldItemSourceAt(@Nonnull Store<EntityStore> store, @Nonnull Vector3i position) {
        WorldItemSource[] result = new WorldItemSource[1];
        store.forEachChunk(Query.and(ItemComponent.getComponentType(), TransformComponent.getComponentType()), (chunk, ignoredCommandBuffer) -> {
            if (result[0] != null) {
                return;
            }

            for (int index = 0; index < chunk.size(); index++) {
                TransformComponent transform = chunk.getComponent(index, TransformComponent.getComponentType());
                ItemComponent item = chunk.getComponent(index, ItemComponent.getComponentType());
                if (transform == null || item == null || ItemStack.isEmpty(item.getItemStack())) {
                    continue;
                }
                if (position.equals(blockPosition(transform.getPosition()))) {
                    result[0] = new WorldItemSource(chunk.getReferenceTo(index), item);
                    return;
                }
            }
        });
        return result[0];
    }

    private static @Nonnull SiphonMoveResult transferOneWorldItem(
            @Nonnull World world,
            @Nonnull WorldItemSource source,
            @Nonnull SiphonEndpoint target,
            @Nonnull Store<EntityStore> entityStore,
            @Nonnull Vector3i velocityOriginPosition,
            @Nonnull Vector3i targetPosition
    ) {
        ItemStack stack =
                source.item().getItemStack();

        if (ItemStack.isEmpty(stack)) {
            return SiphonMoveResult.SOURCE_EMPTY;
        }

        ItemStack itemToMove =
                Objects.requireNonNull(
                        stack.withQuantity(1),
                        "itemToMove"
                );

        if (target.hasInsertContainer()) {

            if (!target.insertOne(itemToMove)) {
                return SiphonMoveResult.TARGET_REJECTED_ITEM;
            }

            consumeOneWorldItem(
                    source,
                    entityStore
            );

            return SiphonMoveResult.MOVED;
        }

        if (!isDroppableTarget(
                world,
                targetPosition
        )) {
            return SiphonMoveResult.NO_TARGET_CONTAINER;
        }

        Holder<EntityStore> itemDrop =
                createDroppedItem(
                        entityStore,
                        velocityOriginPosition,
                        targetPosition,
                        itemToMove
                );

        if (itemDrop == null) {
            return SiphonMoveResult.TARGET_REJECTED_ITEM;
        }

        consumeOneWorldItem(
                source,
                entityStore
        );

        entityStore.addEntity(
                itemDrop,
                AddReason.SPAWN
        );

        return SiphonMoveResult.DROPPED;
    }

    private static void consumeOneWorldItem(
            @Nonnull WorldItemSource source,
            @Nonnull Store<EntityStore> entityStore
    ) {
        ItemStack stack =
                source.item().getItemStack();

        if (ItemStack.isEmpty(stack)) {
            return;
        }

        if (stack.getQuantity() <= 1) {
            source.item().setItemStack(
                    ItemStack.EMPTY
            );

            if (source.ref().isValid()) {
                entityStore.removeEntity(
                        source.ref(),
                        RemoveReason.REMOVE
                );
            }

            return;
        }

        source.item().setItemStack(
                stack.withQuantity(
                        stack.getQuantity() - 1
                )
        );
    }

    private static @Nonnull SiphonMoveResult dropOneItem(
            @Nonnull ItemContainer source,
            @Nonnull Store<EntityStore> entityStore,
            @Nonnull Vector3i velocityOriginPosition,
            @Nonnull Vector3i targetPosition
    ) {
        for (short slot = 0;
             slot < source.getCapacity();
             slot++) {

            ItemStack stack =
                    source.getItemStack(slot);

            if (ItemStack.isEmpty(stack)) {
                continue;
            }

            Holder<EntityStore> itemDrop =
                    createDroppedItem(
                            entityStore,
                            velocityOriginPosition,
                            targetPosition,
                            Objects.requireNonNull(
                                    stack.withQuantity(1),
                                    "itemToMove"
                            )
                    );

            if (itemDrop == null) {
                return SiphonMoveResult.TARGET_REJECTED_ITEM;
            }

            ItemStackSlotTransaction transaction =
                    source.removeItemStackFromSlot(
                            slot,
                            1,
                            true,
                            true
                    );

            if (!transaction.succeeded()
                    || !ItemStack.isEmpty(
                    transaction.getRemainder()
            )) {

                return SiphonMoveResult.TARGET_REJECTED_ITEM;
            }

            entityStore.addEntity(
                    itemDrop,
                    AddReason.SPAWN
            );

            return SiphonMoveResult.DROPPED;
        }

        return SiphonMoveResult.SOURCE_EMPTY;
    }

    private static @Nullable Holder<EntityStore> createDroppedItem(
            @Nonnull Store<EntityStore> entityStore,
            @Nonnull Vector3i velocityOriginPosition,
            @Nonnull Vector3i targetPosition,
            @Nonnull ItemStack itemStack
    ) {
        Vector3d position =
                new Vector3d(
                        targetPosition.x() + 0.5D,
                        targetPosition.y() + 0.5D,
                        targetPosition.z() + 0.5D
                );

        float velocityX =
                outputVelocityComponent(
                        targetPosition.x()
                                - velocityOriginPosition.x()
                );

        float velocityY =
                outputVelocityComponent(
                        targetPosition.y()
                                - velocityOriginPosition.y()
                );

        float velocityZ =
                outputVelocityComponent(
                        targetPosition.z()
                                - velocityOriginPosition.z()
                );

        return ItemComponent.generateItemDrop(
                entityStore,
                itemStack,
                position,
                Rotation3f.ZERO,
                velocityX,
                velocityY,
                velocityZ
        );
    }

    private static float outputVelocityComponent(int delta) {
        return Integer.compare(delta, 0) * DROPPED_ITEM_OUTPUT_SPEED;
    }

    private static @Nonnull Vector3i blockPosition(@Nonnull Vector3d position) {
        return new Vector3i(
                (int) Math.floor(position.x()),
                (int) Math.floor(position.y()),
                (int) Math.floor(position.z())
        );
    }

    private record WorldItemSource(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull ItemComponent item
    ) {
    }

    private static @Nonnull SiphonMoveResult transferOneItem(@Nonnull ItemContainer source, @Nonnull SiphonEndpoint target) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(target, "target");

        boolean foundItem = false;
        for (short slot = 0; slot < source.getCapacity(); slot++) {
            ItemStack stack = source.getItemStack(slot);
            if (ItemStack.isEmpty(stack)) {
                continue;
            }

            foundItem = true;
            if (target.insertOneFrom(source, slot)) {
                return SiphonMoveResult.MOVED;
            }
        }

        return foundItem ? SiphonMoveResult.TARGET_REJECTED_ITEM : SiphonMoveResult.SOURCE_EMPTY;
    }

    public static @Nullable ItemContainerBlock itemContainerBlockAt(@Nonnull World world, @Nonnull Vector3i position) {
        Ref<ChunkStore> blockEntityRef = blockEntityRefAt(world, position);
        if (blockEntityRef == null) {
            return null;
        }

        return world.getChunkStore().getStore().getComponent(blockEntityRef, ItemContainerBlock.getComponentType());
    }

    private static @Nullable ProcessingBenchBlock processingBenchBlockAt(@Nonnull World world, @Nonnull Vector3i position) {
        Ref<ChunkStore> blockEntityRef = blockEntityRefAt(world, position);
        if (blockEntityRef == null) {
            return null;
        }

        return world.getChunkStore().getStore().getComponent(blockEntityRef, ProcessingBenchBlock.getComponentType());
    }

    private static @Nullable Ref<ChunkStore> blockEntityRefAt(
            @Nonnull World world,
            @Nonnull Vector3i position
    ) {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(position, "position");

        Vector3i basePosition =
                baseBlockPosition(
                        world,
                        position
                );

        return BlockModule.getBlockEntity(
                world,
                basePosition.x(),
                basePosition.y(),
                basePosition.z()
        );
    }

    @SuppressWarnings("deprecation")
    private static @Nonnull Vector3i baseBlockPosition(@Nonnull World world, @Nonnull Vector3i position) {
        if (position.y() < 0 || position.y() >= 320) {
            return position;
        }

        WorldChunk chunk = world.getNonTickingChunk(ChunkUtil.indexChunkFromBlock(position.x(), position.z()));
        if (chunk == null) {
            return position;
        }

        BlockChunk blockChunk = chunk.getBlockChunk();
        if (blockChunk == null) {
            return position;
        }

        BlockSection section = blockChunk.getSectionAtIndex(ChunkUtil.indexSection(position.y()));
        if (section == null) {
            return position;
        }
        int filler = section.getFiller(ChunkUtil.indexBlock(position.x(), position.y(), position.z()));
        if (filler == FillerBlockUtil.NO_FILLER) {
            return position;
        }

        return new Vector3i(
                position.x() - FillerBlockUtil.unpackX(filler),
                position.y() - FillerBlockUtil.unpackY(filler),
                position.z() - FillerBlockUtil.unpackZ(filler)
        );
    }

    private record SiphonEndpoint(
            @Nullable ProcessingBenchBlock processingBenchBlock,
            @Nullable ItemContainerBlock itemContainerBlock
    ) {

        private static @Nonnull SiphonEndpoint at(@Nonnull World world, @Nonnull Vector3i position) {
            ProcessingBenchBlock processingBenchBlock = processingBenchBlockAt(world, position);
            ItemContainerBlock itemContainerBlock = processingBenchBlock == null ? itemContainerBlockAt(world, position) : null;
            return new SiphonEndpoint(processingBenchBlock, itemContainerBlock);
        }

        private @Nullable ItemContainer extractContainer() {
            if (processingBenchBlock != null) {
                return processingBenchBlock.getOutputContainer();
            }
            return itemContainerBlock == null ? null : itemContainerBlock.getItemContainer();
        }

        private boolean hasInsertContainer() {
            if (processingBenchBlock != null) {
                return processingBenchBlock.getInputContainer() != null || processingBenchBlock.getFuelContainer() != null;
            }
            return itemContainerBlock != null && itemContainerBlock.getItemContainer() != null;
        }

        private boolean insertOneFrom(@Nonnull ItemContainer source, short sourceSlot) {
            if (processingBenchBlock != null) {
                return moveOne(source, sourceSlot, processingBenchBlock.getInputContainer(), processingBenchBlock.getFuelContainer());
            }

            return itemContainerBlock != null && moveOne(source, sourceSlot, itemContainerBlock.getItemContainer());
        }

        private boolean insertOne(@Nonnull ItemStack itemStack) {
            if (processingBenchBlock != null) {
                return addOne(itemStack, processingBenchBlock.getInputContainer(), processingBenchBlock.getFuelContainer());
            }

            return itemContainerBlock != null && addOne(itemStack, itemContainerBlock.getItemContainer());
        }

        private static boolean addOne(@Nonnull ItemStack itemStack, @Nullable ItemContainer firstTarget, @Nullable ItemContainer secondTarget) {
            return addOne(itemStack, firstTarget) || addOne(itemStack, secondTarget);
        }

        private static boolean addOne(@Nonnull ItemStack itemStack, @Nullable ItemContainer target) {
            if (target == null) {
                return false;
            }

            for (short slot = 0; slot < target.getCapacity(); slot++) {
                ItemStack existing = target.getItemStack(slot);
                if (ItemStack.isEmpty(existing) || !existing.isStackableWith(itemStack)) {
                    continue;
                }

                ItemStackSlotTransaction slotTransaction = target.addItemStackToSlot(slot, itemStack, true, true);
                if (slotTransaction.succeeded() && ItemStack.isEmpty(slotTransaction.getRemainder())) {
                    return true;
                }
            }

            ItemStackTransaction transaction = target.addItemStack(itemStack, true, true, true);
            return transaction.succeeded() && ItemStack.isEmpty(transaction.getRemainder());
        }

        private static boolean moveOne(@Nonnull ItemContainer source, short sourceSlot, @Nullable ItemContainer firstTarget, @Nullable ItemContainer secondTarget) {
            if (firstTarget == null) {
                return moveOne(source, sourceSlot, secondTarget);
            }
            if (secondTarget == null) {
                return moveOne(source, sourceSlot, firstTarget);
            }

            ListTransaction<MoveTransaction<ItemStackTransaction>> transaction = source.moveItemStackFromSlot(sourceSlot, 1, firstTarget, secondTarget);
            return transaction.getList().stream().anyMatch(move -> {
                ItemStackTransaction addTransaction = move.getAddTransaction();
                return move.succeeded() && addTransaction.succeeded() && ItemStack.isEmpty(addTransaction.getRemainder());
            });
        }

        private static boolean moveOne(@Nonnull ItemContainer source, short sourceSlot, @Nullable ItemContainer target) {
            if (target == null) {
                return false;
            }

            MoveTransaction<ItemStackTransaction> transaction = source.moveItemStackFromSlot(sourceSlot, 1, target);
            ItemStackTransaction addTransaction = transaction.getAddTransaction();
            return transaction.succeeded() && addTransaction.succeeded() && ItemStack.isEmpty(addTransaction.getRemainder());
        }
    }

    public enum SiphonMoveResult {
        MOVED,
        DROPPED,
        NO_SOURCE_CONTAINER,
        NO_TARGET_CONTAINER,
        SOURCE_EMPTY,
        TARGET_REJECTED_ITEM
    }

    public static final class TickSystem
            extends EntityTickingSystem<ChunkStore> {

        @Override
        public @Nonnull Query<ChunkStore> getQuery() {
            return Query.and(
                    ForcesOfGraviumPlugin.SIPHON_COMPONENT_TYPE,
                    ForcesOfGraviumPlugin.NODE_COMPONENT_TYPE,
                    BlockModule.BlockStateInfo.getComponentType()
            );
        }

        @Override
        public void tick(
                float delta,
                int index,
                @Nonnull ArchetypeChunk<ChunkStore> chunk,
                @Nonnull Store<ChunkStore> store,
                @Nonnull CommandBuffer<ChunkStore> commandBuffer
        ) {
            NodeComponent node =
                    chunk.getComponent(
                            index,
                            ForcesOfGraviumPlugin.NODE_COMPONENT_TYPE
                    );

            SiphonComponent siphon =
                    chunk.getComponent(
                            index,
                            ForcesOfGraviumPlugin.SIPHON_COMPONENT_TYPE
                    );

            BlockModule.BlockStateInfo blockStateInfo =
                    chunk.getComponent(
                            index,
                            BlockModule.BlockStateInfo.getComponentType()
                    );

            if (node == null
                    || siphon == null
                    || blockStateInfo == null) {
                return;
            }

            Vector3i position =
                    new Vector3i();

            if (!blockStateInfo.fillWorldPos(
                    store,
                    position
            )) {
                return;
            }

            World world =
                    store.getExternalData().getWorld();

            Store<EntityStore> entityStore =
                    world.getEntityStore().getStore();

            tickSiphon(
                    world,
                    position,
                    node,
                    siphon,
                    entityStore
            );
        }
    }
}
