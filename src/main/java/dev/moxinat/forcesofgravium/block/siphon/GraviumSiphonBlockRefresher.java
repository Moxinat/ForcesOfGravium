package dev.moxinat.forcesofgravium.block.siphon;

import com.hypixel.hytale.math.util.ChunkUtil;
import dev.moxinat.forcesofgravium.connectable.registry.NodeTypes;
import dev.moxinat.forcesofgravium.data.Nodes;
import org.joml.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.accessor.BlockAccessor;

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

        BlockAccessor chunk =
                world.getChunk(
                        ChunkUtil.indexChunkFromBlock(
                                x,
                                z
                        )
                );

        if (chunk == null) {
            return;
        }

        chunk.placeBlock(
                x,
                y,
                z,
                blockKey,
                node.rotation(),
                0,
                false
        );
    }

}
