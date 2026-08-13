package dev.moxinat.forcesofgravium.block.gravity;

import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.accessor.BlockAccessor;
import dev.moxinat.forcesofgravium.connectable.registry.NodeTypes;
import dev.moxinat.forcesofgravium.data.Nodes;
import org.joml.Vector3i;

import javax.annotation.Nullable;

public final class CasedGravityPowderBlockRefresher {

    private CasedGravityPowderBlockRefresher() {
    }

    public static void refreshAt(
            World world,
            Vector3i position
    ) {
        Nodes.Node node = Nodes.get(
                world,
                position
        );

        if (node == null) {
            return;
        }

        String baseBlockId;

        if (NodeTypes.STRAIGHT_CASED_GRAVITY_POWDER
                .blockId()
                .equals(node.blockId())) {

            baseBlockId =
                    NodeTypes.STRAIGHT_CASED_GRAVITY_POWDER.blockId();

        } else if (NodeTypes.CURVE_CASED_GRAVITY_POWDER
                .blockId()
                .equals(node.blockId())) {

            baseBlockId =
                    NodeTypes.CURVE_CASED_GRAVITY_POWDER.blockId();

        } else {
            return;
        }

        int x = position.x();
        int y = position.y();
        int z = position.z();

        BlockType currentType =
                world.getBlockType(x, y, z);

        if (currentType == null) {
            return;
        }

        BlockType baseType =
                BlockType.fromString(baseBlockId);

        if (baseType == null) {
            return;
        }

        String stateName = switch (node.effectiveState()) {
            case PUSH -> "Push";
            case PULL -> "Pull";
            case OFF -> "Off";
        };

        String blockKey =
                baseType.getBlockKeyForState(
                        stateName
                );

        if (blockKey == null) {
            blockKey = baseBlockId;
        }

        if (blockKey.equals(currentType.getId())) {
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
