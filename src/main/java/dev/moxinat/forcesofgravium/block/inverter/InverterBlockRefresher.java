package dev.moxinat.forcesofgravium.block.inverter;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockOperations;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import dev.moxinat.forcesofgravium.ForcesOfGraviumPlugin;
import dev.moxinat.forcesofgravium.data.NodeComponent;
import dev.moxinat.forcesofgravium.registry.ConnectableRegistry;
import org.joml.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;

public final class InverterBlockRefresher {

    private InverterBlockRefresher() {
    }

    public static void refreshAt(
            World world,
            Vector3i position
    ) {
        NodeComponent node =
                BlockModule.getComponent(
                        ForcesOfGraviumPlugin.NODE_COMPONENT_TYPE,
                        world,
                        position.x(),
                        position.y(),
                        position.z()
                );

        if (node == null) {
            return;
        }

        BlockType blockType =
                world.getBlockType(
                        position.x(),
                        position.y(),
                        position.z()
                );

        if (blockType == null
                || !ConnectableRegistry.INVERTER_BLOCK_ID
                .equals(
                        ConnectableRegistry.rawBlockId(
                                blockType.getId()
                        )
                )) {
            return;
        }

        int x = position.x();
        int y = position.y();
        int z = position.z();

        BlockType baseType = BlockType.fromString(
                ConnectableRegistry.INVERTER_BLOCK_ID
        );

        if (baseType == null) {
            return;
        }

        String stateName =
                stateName(node);

        ChunkStore chunkStore =
                world.getChunkStore();

        Ref<ChunkStore> sectionRef =
                chunkStore.getChunkSectionReferenceAtBlock(
                        x,
                        y,
                        z
                );

        if (sectionRef == null) {
            return;
        }

        BlockOperations.setBlockInteractionState(
                chunkStore,
                sectionRef,
                x,
                y,
                z,
                baseType,
                stateName,
                true
        );
    }

    private static String stateName(
            NodeComponent node
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
