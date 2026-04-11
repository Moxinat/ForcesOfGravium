package dev.moxinat.forcesofgravium.logic.inverter;

import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.accessor.BlockAccessor;
import dev.moxinat.forcesofgravium.data.ConnectableRotationStore;
import dev.moxinat.forcesofgravium.data.InverterDataStore;
import dev.moxinat.forcesofgravium.data.InverterDataStore.InverterData;
import dev.moxinat.forcesofgravium.logic.gravity.GravityPowderStateCalculator;
import dev.moxinat.forcesofgravium.registry.ConnectableRegistry;

public final class InverterBlockRefresher {

    private InverterBlockRefresher() {
    }

    public static void refreshAt(World world, Vector3i position) {
        int x = position.getX();
        int y = position.getY();
        int z = position.getZ();
        BlockType blockType = world.getBlockType(x, y, z);
        if (blockType == null || !ConnectableRegistry.isInverterId(blockType.getId())) {
            return;
        }

        BlockType baseType = BlockType.fromString(ConnectableRegistry.INVERTER_BLOCK_ID);
        if (baseType == null) {
            return;
        }

        String blockKey = baseType.getBlockKeyForState(stateName(world, position));
        if (blockKey == null) {
            blockKey = ConnectableRegistry.INVERTER_BLOCK_ID;
        }

        BlockAccessor chunk = world.getChunk(ChunkUtil.indexChunkFromBlock(x, z));
        if (chunk == null) {
            return;
        }

        RotationTuple rotation = ConnectableRotationStore.getOrDefault(world, position, RotationTuple.NONE);
        chunk.placeBlock(x, y, z, blockKey, rotation, 0, false);
    }

    private static String stateName(World world, Vector3i position) {
        InverterData data = InverterDataStore.get(world, position);
        if (data == null) {
            data = InverterData.defaultData();
        }

        return modeStatePrefix(InverterStateCalculator.computeInputMode(world, position)) + (data.invertEnabled() ? "Off" : "On");
    }

    private static String modeStatePrefix(String mode) {
        return switch (mode) {
            case GravityPowderStateCalculator.MODE_PUSH -> "Push";
            case GravityPowderStateCalculator.MODE_PULL -> "Pull";
            default -> "Off";
        };
    }
}
