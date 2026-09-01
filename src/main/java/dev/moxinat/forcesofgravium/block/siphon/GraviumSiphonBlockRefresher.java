package dev.moxinat.forcesofgravium.block.siphon;

import com.hypixel.hytale.component.Ref;
import dev.moxinat.forcesofgravium.data.Nodes;
import org.joml.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockOperations;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

public final class GraviumSiphonBlockRefresher {

    private static final String STATE_LOCKED = "Locked";
    private static final String STATE_POWERED = "Powered";

    private GraviumSiphonBlockRefresher() {
    }

    public static void refreshAt(
            World world,
            Vector3i position
    ) {
        Nodes.Node node = Nodes.get(
                world,
                position
        );

        if (node == null
                || !NodeTypes.GRAVIUM_SIPHON
                .blockId()
                .equals(node.blockId())) {
            return;
        }

        int x = position.x();
        int y = position.y();
        int z = position.z();

        BlockType blockType =
                world.getBlockType(x, y, z);

        if (blockType == null) {
            return;
        }

        BlockType baseType = BlockType.fromString(
                NodeTypes.GRAVIUM_SIPHON.blockId()
        );

        if (baseType == null) {
            return;
        }

        String stateName = switch (node.effectiveState()) {
            case PUSH -> STATE_POWERED;
            case PULL -> STATE_LOCKED;
            case OFF -> null;
        };

        String blockKey =
                stateName == null
                        ? NodeTypes.GRAVIUM_SIPHON.blockId()
                        : baseType.getBlockKeyForState(stateName);

        if (blockKey == null) {
            blockKey =
                    NodeTypes.GRAVIUM_SIPHON.blockId();
        }

        if (blockKey.equals(blockType.getId())) {
            return;
        }

        ChunkStore chunkStore = world.getChunkStore();
        Ref<ChunkStore> sectionRef =
                chunkStore.getChunkSectionReferenceAtBlock(x, y, z);

        if (sectionRef == null) {
            return;
        }

        BlockType targetType = BlockType.fromString(blockKey);
        if (targetType == null) {
            return;
        }

        int blockId = BlockType.getAssetMap().getIndex(blockKey);
        if (blockId < 0) {
            return;
        }

        BlockOperations.setBlock(
                chunkStore,
                sectionRef,
                x,
                y,
                z,
                blockId,
                targetType,
                node.rotation().index(),
                0,
                0
        );
    }

}
