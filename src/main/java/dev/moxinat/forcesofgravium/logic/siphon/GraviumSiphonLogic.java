package dev.moxinat.forcesofgravium.logic.siphon;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.builtin.crafting.component.ProcessingBenchBlock;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction;
import com.hypixel.hytale.server.core.inventory.transaction.ListTransaction;
import com.hypixel.hytale.server.core.inventory.transaction.MoveTransaction;
import com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockComponentChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.util.FillerBlockUtil;
import dev.moxinat.forcesofgravium.data.ConnectableRotationStore;
import dev.moxinat.forcesofgravium.data.GraviumSiphonStore;
import dev.moxinat.forcesofgravium.data.GraviumSiphonStore.GraviumSiphonData;
import dev.moxinat.forcesofgravium.logic.network.ConnectableNeighborResolver;
import dev.moxinat.forcesofgravium.registry.ConnectableRegistry;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public final class GraviumSiphonLogic {

    private static final int POWERED_TRANSFER_INTERVAL_TICKS = 30;
    private static final int UNPOWERED_TRANSFER_INTERVAL_TICKS = 60;
    private static final Map<String, Long> LAST_TRANSFER_TICKS = new ConcurrentHashMap<>();

    private GraviumSiphonLogic() {
    }

    public static void tickWorld(@Nonnull World world) {
        Objects.requireNonNull(world, "world");

        for (Vector3i position : ConnectableRotationStore.snapshotForWorld(world).keySet()) {
            BlockType blockType = world.getBlockType(position.getX(), position.getY(), position.getZ());
            if (blockType != null && ConnectableRegistry.isGraviumSiphonId(blockType.getId())) {
                GraviumSiphonStore.add(world, position);
            }
        }

        for (Map.Entry<Vector3i, GraviumSiphonData> entry : GraviumSiphonStore.snapshotForWorld(world).entrySet()) {
            Vector3i position = entry.getKey();
            BlockType blockType = world.getBlockType(position.getX(), position.getY(), position.getZ());
            if (blockType == null || !ConnectableRegistry.isGraviumSiphonId(blockType.getId())) {
                GraviumSiphonStore.remove(world, position);
                continue;
            }

            if (entry.getValue().locked()) {
                continue;
            }
            if (canTransfer(world, position, entry.getValue())) {
                transferOneItem(world, position);
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
                + ":" + position.getX()
                + "," + position.getY()
                + "," + position.getZ();
    }

    public static @Nonnull SiphonMoveResult transferOneItem(@Nonnull World world, @Nonnull Vector3i siphonPosition) {
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

        return transferOneItemBetween(world, sourcePosition, targetPosition);
    }

    public static @Nonnull SiphonMoveResult transferOneStack(@Nonnull World world, @Nonnull Vector3i siphonPosition) {
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

        return transferOneStackBetween(world, sourcePosition, targetPosition);
    }

    public static @Nonnull SiphonMoveResult transferOneItemBetween(
            @Nonnull World world,
            @Nonnull Vector3i sourcePosition,
            @Nonnull Vector3i targetPosition
    ) {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(sourcePosition, "sourcePosition");
        Objects.requireNonNull(targetPosition, "targetPosition");

        SiphonEndpoint source = SiphonEndpoint.at(world, sourcePosition);
        ItemContainer sourceContainer = source.extractContainer();
        if (sourceContainer == null) {
            return SiphonMoveResult.NO_SOURCE_CONTAINER;
        }

        SiphonEndpoint target = SiphonEndpoint.at(world, targetPosition);
        if (!target.hasInsertContainer()) {
            return SiphonMoveResult.NO_TARGET_CONTAINER;
        }

        return transferOneItem(sourceContainer, target);
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

    public static @Nonnull SiphonMoveResult transferOneItem(@Nonnull ItemContainer source, @Nonnull ItemContainer target) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(target, "target");

        boolean foundItem = false;
        for (short slot = 0; slot < source.getCapacity(); slot++) {
            ItemStack stack = source.getItemStack(slot);
            if (ItemStack.isEmpty(stack)) {
                continue;
            }

            foundItem = true;
            MoveTransaction<ItemStackTransaction> transaction = source.moveItemStackFromSlot(slot, 1, target);
            if (transaction.succeeded()) {
                return SiphonMoveResult.MOVED;
            }
        }

        return foundItem ? SiphonMoveResult.TARGET_REJECTED_ITEM : SiphonMoveResult.SOURCE_EMPTY;
    }

    public static @Nonnull SiphonMoveResult transferOneStackBetween(
            @Nonnull World world,
            @Nonnull Vector3i sourcePosition,
            @Nonnull Vector3i targetPosition
    ) {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(sourcePosition, "sourcePosition");
        Objects.requireNonNull(targetPosition, "targetPosition");

        ItemContainer source = itemContainerAt(world, sourcePosition);
        if (source == null) {
            return SiphonMoveResult.NO_SOURCE_CONTAINER;
        }

        ItemContainer target = itemContainerAt(world, targetPosition);
        if (target == null) {
            return SiphonMoveResult.NO_TARGET_CONTAINER;
        }

        for (short slot = 0; slot < source.getCapacity(); slot++) {
            ItemStack stack = source.getItemStack(slot);
            if (ItemStack.isEmpty(stack)) {
                continue;
            }

            MoveTransaction<ItemStackTransaction> transaction = source.moveItemStackFromSlot(slot, target);
            return transaction.succeeded() ? SiphonMoveResult.MOVED : SiphonMoveResult.TARGET_REJECTED_ITEM;
        }

        return SiphonMoveResult.SOURCE_EMPTY;
    }

    public static @Nullable ItemContainer itemContainerAt(@Nonnull World world, @Nonnull Vector3i position) {
        ItemContainerBlock block = itemContainerBlockAt(world, position);
        return block == null ? null : block.getItemContainer();
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
                ChunkUtil.indexChunkFromBlock(basePosition.getX(), basePosition.getZ())
        );
        if (chunkRef == null) {
            return null;
        }

        Store<ChunkStore> store = chunkStore.getStore();
        BlockComponentChunk blockComponents = store.getComponent(chunkRef, BlockComponentChunk.getComponentType());
        if (blockComponents == null) {
            return null;
        }

        Ref<ChunkStore> blockEntityRef = blockComponents.getEntityReference(ChunkUtil.indexBlockInColumn(
                basePosition.getX(),
                basePosition.getY(),
                basePosition.getZ()
        ));
        if (blockEntityRef == null) {
            return null;
        }

        return blockEntityRef;
    }

    @SuppressWarnings("deprecation")
    private static @Nonnull Vector3i baseBlockPosition(@Nonnull World world, @Nonnull Vector3i position) {
        if (position.getY() < 0 || position.getY() >= 320) {
            return position;
        }

        WorldChunk chunk = world.getNonTickingChunk(ChunkUtil.indexChunkFromBlock(position.getX(), position.getZ()));
        if (chunk == null) {
            return position;
        }

        BlockSection section = chunk.getBlockChunk().getSectionAtIndex(ChunkUtil.indexSection(position.getY()));
        int filler = section.getFiller(ChunkUtil.indexBlock(position.getX(), position.getY(), position.getZ()));
        if (filler == FillerBlockUtil.NO_FILLER) {
            return position;
        }

        return new Vector3i(
                position.getX() - FillerBlockUtil.unpackX(filler),
                position.getY() - FillerBlockUtil.unpackY(filler),
                position.getZ() - FillerBlockUtil.unpackZ(filler)
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
        NO_SOURCE_CONTAINER,
        NO_TARGET_CONTAINER,
        SOURCE_EMPTY,
        TARGET_REJECTED_ITEM
    }
}
