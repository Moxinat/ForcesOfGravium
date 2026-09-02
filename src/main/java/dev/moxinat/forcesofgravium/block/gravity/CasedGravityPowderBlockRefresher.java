package dev.moxinat.forcesofgravium.block.gravity;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockOperations;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import dev.moxinat.forcesofgravium.ForcesOfGraviumPlugin;
import dev.moxinat.forcesofgravium.data.NodeComponent;
import dev.moxinat.forcesofgravium.registry.ConnectableRegistry;
import org.joml.Vector3i;

public final class CasedGravityPowderBlockRefresher {

    private CasedGravityPowderBlockRefresher() {
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

        int x = position.x();
        int y = position.y();
        int z = position.z();

        BlockType currentType =
                world.getBlockType(x, y, z);

        if (currentType == null) {
            return;
        }

        String rawBlockId =
                ConnectableRegistry.rawBlockId(
                        currentType.getId()
                );

        String baseBlockId;

        if (ConnectableRegistry.STRAIGHT_CASED_GRAVITY_POWDER_BLOCK_ID
                .equals(rawBlockId)) {

            baseBlockId =
                    ConnectableRegistry.STRAIGHT_CASED_GRAVITY_POWDER_BLOCK_ID;

        } else if (ConnectableRegistry.CURVE_CASED_GRAVITY_POWDER_BLOCK_ID
                .equals(rawBlockId)) {

            baseBlockId =
                    ConnectableRegistry.CURVE_CASED_GRAVITY_POWDER_BLOCK_ID;

        } else {
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

}
