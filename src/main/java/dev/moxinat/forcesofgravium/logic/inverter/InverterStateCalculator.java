package dev.moxinat.forcesofgravium.logic.inverter;

import org.joml.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import dev.moxinat.forcesofgravium.connectable.ConnectableRuntimeAccessor;
import dev.moxinat.forcesofgravium.connectable.ConnectableRuntimeData;
import dev.moxinat.forcesofgravium.data.GravityPowderBlockDataStore;
import dev.moxinat.forcesofgravium.data.SourceBlockDataStore;
import dev.moxinat.forcesofgravium.logic.network.ConnectableNeighborResolver;
import dev.moxinat.forcesofgravium.registry.ConnectableBlockRoles;
import dev.moxinat.forcesofgravium.registry.ConnectableRegistry;

public final class InverterStateCalculator {

    private InverterStateCalculator() {
    }

    public static String computeInputMode(World world, Vector3i position) {
        BlockType blockType = world.getBlockType(position.x(), position.y(), position.z());
        if (blockType == null || !ConnectableRegistry.isInverterId(blockType.getId())) {
            return GravityPowderBlockDataStore.STATE_OFF;
        }

        Vector3i backNeighborPosition = ConnectableNeighborResolver.adjacentPositionForLocalSide(
                world,
                position,
                ConnectableRegistry.SIDE_BACK
        );
        BlockType backNeighborType = world.getBlockType(
                backNeighborPosition.x(),
                backNeighborPosition.y(),
                backNeighborPosition.z()
        );
        if (backNeighborType == null) {
            return GravityPowderBlockDataStore.STATE_OFF;
        }

        if (ConnectableBlockRoles.isSource(backNeighborType.getId())) {
            if (!ConnectableNeighborResolver.isSourceNeighborOf(world, backNeighborPosition, position)) {
                return GravityPowderBlockDataStore.STATE_OFF;
            }
            return directSourceInputMode(world, backNeighborPosition, backNeighborType.getId());
        }

        if (ConnectableRegistry.isGravityPowderCarrierId(backNeighborType.getId())) {
            if (!ConnectableNeighborResolver.areMutuallyConnected(world, backNeighborPosition, position)) {
                return GravityPowderBlockDataStore.STATE_OFF;
            }
            return ConnectableRuntimeAccessor.getRuntimeData(world, backNeighborPosition)
                    .map(ConnectableRuntimeData::effectiveState)
                    .orElse(GravityPowderBlockDataStore.STATE_OFF);
        }

        if (!ConnectableRegistry.isInverterId(backNeighborType.getId())) {
            return GravityPowderBlockDataStore.STATE_OFF;
        }

        Vector3i upstreamFrontPosition = ConnectableNeighborResolver.adjacentPositionForLocalSide(
                world,
                backNeighborPosition,
                ConnectableRegistry.SIDE_FRONT
        );
        if (!upstreamFrontPosition.equals(position)) {
            return GravityPowderBlockDataStore.STATE_OFF;
        }

        return ConnectableRuntimeAccessor.getRuntimeData(world, backNeighborPosition)
                .map(ConnectableRuntimeData::instantState)
                .orElse(GravityPowderBlockDataStore.STATE_OFF);
    }

    static String directSourceInputMode(World world, Vector3i position, String blockId) {
        if (ConnectableBlockRoles.isSource(blockId) && SourceBlockDataStore.isActive(world, position, blockId)) {
            return GravityPowderBlockDataStore.STATE_PUSH;
        }
        return GravityPowderBlockDataStore.STATE_OFF;
    }

    public static String invertMode(String mode) {
        if (GravityPowderBlockDataStore.STATE_PUSH.equals(mode)) {
            return GravityPowderBlockDataStore.STATE_PULL;
        }
        if (GravityPowderBlockDataStore.STATE_PULL.equals(mode)) {
            return GravityPowderBlockDataStore.STATE_PUSH;
        }
        return GravityPowderBlockDataStore.STATE_OFF;
    }
}
