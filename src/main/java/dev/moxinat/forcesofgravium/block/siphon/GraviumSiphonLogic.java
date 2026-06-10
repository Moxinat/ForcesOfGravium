package dev.moxinat.forcesofgravium.block.siphon;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.builtin.crafting.component.ProcessingBenchBlock;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Rotation3f;
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
import com.hypixel.hytale.server.core.universe.world.chunk.BlockComponentChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.FillerBlockUtil;
import dev.moxinat.forcesofgravium.connectable.core.ConnectableRuntimeAccessor;
import dev.moxinat.forcesofgravium.block.siphon.GraviumSiphonStore;
import dev.moxinat.forcesofgravium.block.siphon.GraviumSiphonStore.GraviumSiphonData;
import dev.moxinat.forcesofgravium.connectable.spatial.ConnectableNeighborResolver;
import dev.moxinat.forcesofgravium.connectable.registry.ConnectableRegistry;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public final class GraviumSiphonLogic {

    private static final int POWERED_TRANSFER_INTERVAL_TICKS = 30;
    private static final int UNPOWERED_TRANSFER_INTERVAL_TICKS = 60;
    private static final float DROPPED_ITEM_OUTPUT_SPEED = 3.0F;
    private static final Map<String, Long> LAST_TRANSFER_TICKS = new ConcurrentHashMap<>();

    private GraviumSiphonLogic() {
    }

    public static void tickWorld(@Nonnull World world, @Nullable CommandBuffer<EntityStore> commandBuffer) {
        Objects.requireNonNull(world, "world");

        for (Vector3i position : ConnectableRuntimeAccessor.positionsMatching(world, ConnectableRegistry::isGraviumSiphonId)) {
            GraviumSiphonStore.add(world, position);
        }

        for (Map.Entry<Vector3i, GraviumSiphonData> entry : GraviumSiphonStore.snapshotForWorld(world).entrySet()) {
            Vector3i position = entry.getKey();
            BlockType blockType = world.getBlockType(position.x(), position.y(), position.z());
            if (blockType == null || !ConnectableRegistry.isGraviumSiphonId(blockType.getId())) {
                GraviumSiphonStore.remove(world, position);
                continue;
            }

            if (entry.getValue().locked()) {
                continue;
            }
            if (canTransfer(world, position, entry.getValue())) {
                transferOneItem(world, position, commandBuffer);
            }
        }
    }

    private static boolean canTransfer(@Nonnull World world, @Nonnull Vector3i position, @Nonnull GraviumSiphonData data) {
        if (!data.powered() && ConnectableNeighborResolver.rotationFor(world, position).pitch() != Rotation.Ninety) {
            return false;
        }

        long tick = world.getTick();
        int interval = data.powered() ? POWERED_TRANSFER_INTERVAL_TICKS : UNPOWERED_TRANSFER_INTERVAL_TICKS;
        String key = transferKey(world, position);
        Long previousTick = LAST_TRANSFER_TICKS.get(key);
        if (previousTick != null && tick - previousTick < interval) {
            return false;
        }

        LAST_TRANSFER_TICKS.put(key, tick);
        return true;
    }

    private static @Nonnull String transferKey(@Nonnull World world, @Nonnull Vector3i position) {
        return world.getSavePath().toAbsolutePath().normalize()
                + ":" + position.x()
                + "," + position.y()
                + "," + position.z();
    }

    public static @Nonnull SiphonMoveResult transferOneItem(@Nonnull World world, @Nonnull Vector3i siphonPosition) {
        return transferOneItem(world, siphonPosition, null);
    }

    private static @Nonnull SiphonMoveResult transferOneItem(
            @Nonnull World world,
            @Nonnull Vector3i siphonPosition,
            @Nullable CommandBuffer<EntityStore> commandBuffer
    ) {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(siphonPosition, "siphonPosition");

        Vector3i sourcePosition = ConnectableNeighborResolver.adjacentPositionForLocalSide(
                world,
                siphonPosition,
                ConnectableRegistry.SIDE_BACK
        );
        Vector3i targetPosition = ConnectableNeighborResolver.adjacentPositionForLocalSide(
                world,
                siphonPosition,
                ConnectableRegistry.SIDE_FRONT
        );

        return transferOneItemBetween(world, sourcePosition, targetPosition, siphonPosition, commandBuffer);
    }

    private static @Nonnull SiphonMoveResult transferOneItemBetween(
            @Nonnull World world,
            @Nonnull Vector3i sourcePosition,
            @Nonnull Vector3i targetPosition,
            @Nonnull Vector3i velocityOriginPosition,
            @Nullable CommandBuffer<EntityStore> commandBuffer
    ) {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(sourcePosition, "sourcePosition");
        Objects.requireNonNull(targetPosition, "targetPosition");
        Objects.requireNonNull(velocityOriginPosition, "velocityOriginPosition");

        SiphonEndpoint target = SiphonEndpoint.at(world, targetPosition);
        SiphonEndpoint source = SiphonEndpoint.at(world, sourcePosition);
        ItemContainer sourceContainer = source.extractContainer();
        if (sourceContainer == null) {
            if (commandBuffer != null && isDroppableTarget(world, sourcePosition)) {
                WorldItemSource worldItemSource = worldItemSourceAt(commandBuffer.getStore(), sourcePosition);
                if (worldItemSource == null) {
                    return SiphonMoveResult.SOURCE_EMPTY;
                }
                return transferOneWorldItem(world, worldItemSource, target, commandBuffer, velocityOriginPosition, targetPosition);
            }
            return SiphonMoveResult.NO_SOURCE_CONTAINER;
        }

        if (!target.hasInsertContainer()) {
            if (commandBuffer != null && isDroppableTarget(world, targetPosition)) {
                return dropOneItem(sourceContainer, commandBuffer, velocityOriginPosition, targetPosition);
            }
            return SiphonMoveResult.NO_TARGET_CONTAINER;
        }

        return transferOneItem(sourceContainer, target);
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
            @Nonnull CommandBuffer<EntityStore> commandBuffer,
            @Nonnull Vector3i velocityOriginPosition,
            @Nonnull Vector3i targetPosition
    ) {
        ItemStack stack = source.item().getItemStack();
        if (ItemStack.isEmpty(stack)) {
            return SiphonMoveResult.SOURCE_EMPTY;
        }

        ItemStack itemToMove = Objects.requireNonNull(stack.withQuantity(1), "itemToMove");
        if (target.hasInsertContainer()) {
            if (!target.insertOne(itemToMove)) {
                return SiphonMoveResult.TARGET_REJECTED_ITEM;
            }

            consumeOneWorldItem(source, commandBuffer);
            return SiphonMoveResult.MOVED;
        }

        if (!isDroppableTarget(world, targetPosition)) {
            return SiphonMoveResult.NO_TARGET_CONTAINER;
        }

        Holder<EntityStore> itemDrop = createDroppedItem(commandBuffer, velocityOriginPosition, targetPosition, itemToMove);
        if (itemDrop == null) {
            return SiphonMoveResult.TARGET_REJECTED_ITEM;
        }

        consumeOneWorldItem(source, commandBuffer);
        commandBuffer.addEntity(itemDrop, AddReason.SPAWN);
        return SiphonMoveResult.DROPPED;
    }

    private static void consumeOneWorldItem(@Nonnull WorldItemSource source, @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        ItemStack stack = Objects.requireNonNull(source.item().getItemStack(), "stack");
        if (stack.getQuantity() <= 1) {
            commandBuffer.removeEntity(source.ref(), RemoveReason.REMOVE);
            return;
        }

        source.item().setItemStack(stack.withQuantity(stack.getQuantity() - 1));
    }

    private static @Nonnull SiphonMoveResult dropOneItem(
            @Nonnull ItemContainer source,
            @Nonnull CommandBuffer<EntityStore> commandBuffer,
            @Nonnull Vector3i velocityOriginPosition,
            @Nonnull Vector3i targetPosition
    ) {
        for (short slot = 0; slot < source.getCapacity(); slot++) {
            ItemStack stack = source.getItemStack(slot);
            if (ItemStack.isEmpty(stack)) {
                continue;
            }

            Holder<EntityStore> itemDrop = createDroppedItem(
                    commandBuffer,
                    velocityOriginPosition,
                    targetPosition,
                    Objects.requireNonNull(stack.withQuantity(1), "itemToMove")
            );
            if (itemDrop == null) {
                return SiphonMoveResult.TARGET_REJECTED_ITEM;
            }

            ItemStackSlotTransaction transaction = source.removeItemStackFromSlot(slot, 1, true, true);
            if (!transaction.succeeded() || !ItemStack.isEmpty(transaction.getRemainder())) {
                return SiphonMoveResult.TARGET_REJECTED_ITEM;
            }

            commandBuffer.addEntity(itemDrop, AddReason.SPAWN);
            return SiphonMoveResult.DROPPED;
        }

        return SiphonMoveResult.SOURCE_EMPTY;
    }

    private static @Nullable Holder<EntityStore> createDroppedItem(
            @Nonnull CommandBuffer<EntityStore> commandBuffer,
            @Nonnull Vector3i velocityOriginPosition,
            @Nonnull Vector3i targetPosition,
            @Nonnull ItemStack itemStack
    ) {
        Vector3d position = new Vector3d(
                targetPosition.x() + 0.5D,
                targetPosition.y() + 0.5D,
                targetPosition.z() + 0.5D
        );
        float velocityX = outputVelocityComponent(targetPosition.x() - velocityOriginPosition.x());
        float velocityY = outputVelocityComponent(targetPosition.y() - velocityOriginPosition.y());
        float velocityZ = outputVelocityComponent(targetPosition.z() - velocityOriginPosition.z());
        return ItemComponent.generateItemDrop(
                commandBuffer,
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

    private static @Nullable Ref<ChunkStore> blockEntityRefAt(@Nonnull World world, @Nonnull Vector3i position) {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(position, "position");

        Vector3i basePosition = baseBlockPosition(world, position);
        ChunkStore chunkStore = world.getChunkStore();
        Ref<ChunkStore> chunkRef = chunkStore.getChunkReference(
                ChunkUtil.indexChunkFromBlock(basePosition.x(), basePosition.z())
        );
        if (chunkRef == null) {
            return null;
        }

        Store<ChunkStore> store = chunkStore.getStore();
        BlockComponentChunk blockComponents = store.getComponent(chunkRef, BlockComponentChunk.getComponentType());
        if (blockComponents == null) {
            return null;
        }

        return blockComponents.getEntityReference(ChunkUtil.indexBlockInColumn(
                basePosition.x(),
                basePosition.y(),
                basePosition.z()
        ));
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
}
