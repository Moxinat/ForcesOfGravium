package dev.moxinat.forcesofgravium.logic.gravity;

import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.accessor.BlockAccessor;
import dev.moxinat.forcesofgravium.connectable.ConnectableRuntimeAccessor;
import dev.moxinat.forcesofgravium.connectable.ConnectableRuntimeData;
import dev.moxinat.forcesofgravium.data.ConnectableRotationStore;
import dev.moxinat.forcesofgravium.data.GravityPowderBlockDataStore;
import dev.moxinat.forcesofgravium.registry.ConnectableRegistry;
import org.joml.Vector3i;

import javax.annotation.Nullable;

public final class CasedGravityPowderBlockRefresher {

    private CasedGravityPowderBlockRefresher() {
    }

    public static void refreshAt(World world, Vector3i position) {
        refreshAt(world, position.x(), position.y(), position.z());
    }

    public static void refreshAt(World world, int x, int y, int z) {
        BlockType currentType = world.getBlockType(x, y, z);
        if (currentType == null || !ConnectableRegistry.isCasedGravityPowderId(currentType.getId())) {
            return;
        }

        String baseBlockId = baseBlockIdFor(currentType.getId());
        if (baseBlockId == null) {
            return;
        }

        BlockType baseType = BlockType.fromString(baseBlockId);
        if (baseType == null) {
            return;
        }

        String stateName = stateNameFor(ConnectableRuntimeAccessor.getRuntimeData(world, new Vector3i(x, y, z))
                .map(ConnectableRuntimeData::effectiveState)
                .orElse(GravityPowderBlockDataStore.STATE_OFF));
        String blockKey = baseType.getBlockKeyForState(stateName);
        if (blockKey == null) {
            blockKey = baseBlockId;
        }

        BlockAccessor chunk = world.getChunk(ChunkUtil.indexChunkFromBlock(x, z));
        if (chunk == null) {
            return;
        }

        RotationTuple rotation = ConnectableRotationStore.getOrDefault(world, new Vector3i(x, y, z), RotationTuple.NONE);
        chunk.placeBlock(x, y, z, blockKey, rotation, 0, false);
    }

    private static @Nullable String baseBlockIdFor(@Nullable String blockId) {
        if (ConnectableRegistry.isStraightCasedGravityPowderId(blockId)) {
            return ConnectableRegistry.STRAIGHT_CASED_GRAVITY_POWDER_BLOCK_ID;
        }
        if (ConnectableRegistry.isCurveCasedGravityPowderId(blockId)) {
            return ConnectableRegistry.CURVE_CASED_GRAVITY_POWDER_BLOCK_ID;
        }
        return null;
    }

    private static String stateNameFor(@Nullable String effectiveState) {
        return switch (GravityPowderBlockDataStore.normalizeState(effectiveState)) {
            case GravityPowderBlockDataStore.STATE_PUSH -> "Push";
            case GravityPowderBlockDataStore.STATE_PULL -> "Pull";
            default -> "Off";
        };
    }
}
