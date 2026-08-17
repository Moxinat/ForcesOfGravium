package dev.moxinat.forcesofgravium.block.inverter;

import com.hypixel.hytale.math.util.ChunkUtil;
import dev.moxinat.forcesofgravium.connectable.registry.NodeTypes;
import dev.moxinat.forcesofgravium.data.Nodes;
import org.joml.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.accessor.BlockAccessor;

public final class InverterBlockRefresher {

    private InverterBlockRefresher() {
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
                || !NodeTypes.INVERTER
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
                NodeTypes.INVERTER.blockId()
        );

        if (baseType == null) {
            return;
        }

        String stateName =
                stateName(node);

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

        chunk.setBlockInteractionState(
                position,
                baseType,
                stateName
        );
    }

    private static String stateName(
            Nodes.Node node
    ) {
        String prefix = switch (node.effectiveState()) {
            case PUSH -> "Push";
            case PULL -> "Pull";
            case OFF -> "Off";
        };

        return prefix
                + (node.invertEnabled()
                ? "Off"
                : "On");
    }
}
