package dev.moxinat.forcesofgravium.block.siphon;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import dev.moxinat.forcesofgravium.ForcesOfGraviumPlugin;
import dev.moxinat.forcesofgravium.data.NodeComponent;
import dev.moxinat.forcesofgravium.data.SiphonComponent;
import dev.moxinat.forcesofgravium.registry.ConnectableRegistry;
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
        NodeComponent node =
                BlockModule.getComponent(
                        ForcesOfGraviumPlugin.NODE_COMPONENT_TYPE,
                        world,
                        position.x(),
                        position.y(),
                        position.z()
                );

        SiphonComponent siphon =
                BlockModule.getComponent(
                        ForcesOfGraviumPlugin.SIPHON_COMPONENT_TYPE,
                        world,
                        position.x(),
                        position.y(),
                        position.z()
                );

        if (node == null || siphon == null) {
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
                ConnectableRegistry.GRAVIUM_SIPHON_BLOCK_ID
        );

        if (baseType == null) {
            return;
        }

        String stateName = switch (node.effectiveState()) {
            case PUSH -> STATE_POWERED;
            case PULL -> STATE_LOCKED;
            case OFF -> "default";
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
