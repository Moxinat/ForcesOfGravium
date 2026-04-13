package dev.moxinat.forcesofgravium.logic.siphon;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction;
import com.hypixel.hytale.server.core.inventory.transaction.MoveTransaction;
import com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockComponentChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import dev.moxinat.forcesofgravium.data.ConnectableRotationStore;
import dev.moxinat.forcesofgravium.data.GraviumSiphonStore;
import dev.moxinat.forcesofgravium.logic.network.ConnectableNeighborResolver;
import dev.moxinat.forcesofgravium.registry.ConnectableRegistry;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;

public final class GraviumSiphonLogic {

    private GraviumSiphonLogic() {
    }

    public static void tickWorld(@Nonnull World world) {
        Objects.requireNonNull(world, "world");

        for (Vector3i position : ConnectableRotationStore.snapshotForWorld(world).keySet()) {
            BlockType blockType = world.getBlockType(position.getX(), position.getY(), position.getZ());
            if (blockType != null && ConnectableRegistry.GRAVIUM_SIPHON_BLOCK_ID.equals(blockType.getId())) {
                GraviumSiphonStore.add(world, position);
            }
        }

        for (Vector3i position : GraviumSiphonStore.snapshotForWorld(world)) {
            BlockType blockType = world.getBlockType(position.getX(), position.getY(), position.getZ());
            if (blockType == null || !ConnectableRegistry.GRAVIUM_SIPHON_BLOCK_ID.equals(blockType.getId())) {
                GraviumSiphonStore.remove(world, position);
                continue;
            }

            transferOneItem(world, position);
        }
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

        ItemContainer source = itemContainerAt(world, sourcePosition);
        if (source == null) {
            return SiphonMoveResult.NO_SOURCE_CONTAINER;
        }

        ItemContainer target = itemContainerAt(world, targetPosition);
        if (target == null) {
            return SiphonMoveResult.NO_TARGET_CONTAINER;
        }

        return transferOneItem(source, target);
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
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(position, "position");

        ChunkStore chunkStore = world.getChunkStore();
        Ref<ChunkStore> chunkRef = chunkStore.getChunkReference(
                ChunkUtil.indexChunkFromBlock(position.getX(), position.getZ())
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
                position.getX(),
                position.getY(),
                position.getZ()
        ));
        if (blockEntityRef == null) {
            return null;
        }

        return store.getComponent(blockEntityRef, ItemContainerBlock.getComponentType());
    }

    public enum SiphonMoveResult {
        MOVED,
        NO_SOURCE_CONTAINER,
        NO_TARGET_CONTAINER,
        SOURCE_EMPTY,
        TARGET_REJECTED_ITEM
    }
}
