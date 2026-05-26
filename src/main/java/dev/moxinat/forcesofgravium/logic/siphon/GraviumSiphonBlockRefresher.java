package dev.moxinat.forcesofgravium.logic.siphon;

import com.hypixel.hytale.math.util.ChunkUtil;
import org.joml.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.accessor.BlockAccessor;
import dev.moxinat.forcesofgravium.data.ConnectableRotationStore;
import dev.moxinat.forcesofgravium.data.GraviumSiphonStore;
import dev.moxinat.forcesofgravium.data.GraviumSiphonStore.GraviumSiphonData;
import dev.moxinat.forcesofgravium.registry.ConnectableRegistry;

public final class GraviumSiphonBlockRefresher {

    private static final String STATE_LOCKED = "Locked";
    private static final String STATE_POWERED = "Powered";

    private GraviumSiphonBlockRefresher() {
    }

    public static void refreshAt(World world, Vector3i position) {
        int x = position.x();
        int y = position.y();
        int z = position.z();
        BlockType blockType = world.getBlockType(x, y, z);
        if (blockType == null || !ConnectableRegistry.isGraviumSiphonId(blockType.getId())) {
            return;
        }

        BlockType baseType = BlockType.fromString(ConnectableRegistry.GRAVIUM_SIPHON_BLOCK_ID);
        if (baseType == null) {
            return;
        }

        String stateName = stateName(world, position);
        String blockKey = stateName == null ? ConnectableRegistry.GRAVIUM_SIPHON_BLOCK_ID : baseType.getBlockKeyForState(stateName);
        if (blockKey == null) {
            blockKey = ConnectableRegistry.GRAVIUM_SIPHON_BLOCK_ID;
        }
        if (blockKey.equals(blockType.getId())) {
            return;
        }

        BlockAccessor chunk = world.getChunk(ChunkUtil.indexChunkFromBlock(x, z));
        if (chunk == null) {
            return;
        }

        RotationTuple rotation = ConnectableRotationStore.getOrDefault(world, position, RotationTuple.NONE);
        chunk.placeBlock(x, y, z, blockKey, rotation, 0, false);
    }

    private static String stateName(World world, Vector3i position) {
        GraviumSiphonData data = GraviumSiphonStore.get(world, position);
        if (data == null) {
            data = GraviumSiphonData.defaultData();
        }

        if (data.locked()) {
            return STATE_LOCKED;
        }
        if (data.powered()) {
            return STATE_POWERED;
        }
        return null;
    }
}
