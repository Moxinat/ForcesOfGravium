package dev.moxinat.forcesofgravium.block.sensor;

import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.accessor.BlockAccessor;
import dev.moxinat.forcesofgravium.data.Nodes;
import dev.moxinat.forcesofgravium.registry.NodeTypes;
import org.joml.Vector3i;

public final class SensorBlockRefresher {

    private SensorBlockRefresher() {
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
                || !NodeTypes.GRAVIUM_SENSOR
                .blockId()
                .equals(node.blockId())) {
            return;
        }

        BlockType baseType = BlockType.fromString(
                NodeTypes.GRAVIUM_SENSOR.blockId()
        );

        if (baseType == null) {
            return;
        }

        String stateName = switch (node.effectiveState()) {
            case PUSH -> "Push";
            case PULL -> "Pull";
            case OFF -> "Off";
        };

        BlockAccessor chunk = world.getChunk(
                ChunkUtil.indexChunkFromBlock(
                        position.x(),
                        position.z()
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
}