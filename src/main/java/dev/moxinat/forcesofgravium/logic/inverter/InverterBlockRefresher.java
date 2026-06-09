package dev.moxinat.forcesofgravium.logic.inverter;

import com.hypixel.hytale.math.util.ChunkUtil;
import org.joml.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.accessor.BlockAccessor;
import dev.moxinat.forcesofgravium.connectable.ConnectableRuntimeAccessor;
import dev.moxinat.forcesofgravium.connectable.ConnectableRuntimeData;
import dev.moxinat.forcesofgravium.data.GravityPowderBlockDataStore;
import dev.moxinat.forcesofgravium.registry.ConnectableRegistry;

public final class InverterBlockRefresher {

    private InverterBlockRefresher() {
    }

    public static void refreshAt(World world, Vector3i position) {
        int x = position.x();
        int y = position.y();
        int z = position.z();
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

        RotationTuple rotation = ConnectableRuntimeAccessor.getRotation(world, position);
        chunk.placeBlock(x, y, z, blockKey, rotation, 0, false);
    }

    private static String stateName(World world, Vector3i position) {
        boolean invertEnabled = ConnectableRuntimeAccessor.getRuntimeData(world, position)
                .map(ConnectableRuntimeData::invertEnabled)
                .orElse(true);

        return modeStatePrefix(InverterStateCalculator.computeInputMode(world, position)) + (invertEnabled ? "Off" : "On");
    }

    private static String modeStatePrefix(String mode) {
        return switch (mode) {
            case GravityPowderBlockDataStore.STATE_PUSH -> "Push";
            case GravityPowderBlockDataStore.STATE_PULL -> "Pull";
            default -> "Off";
        };
    }
}
