package dev.moxinat.forcesofgravium.block.inverter;

import org.joml.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import dev.moxinat.forcesofgravium.connectable.core.ConnectableRuntimeAccessor;
import dev.moxinat.forcesofgravium.connectable.spatial.ConnectableNeighborResolver;
import dev.moxinat.forcesofgravium.connectable.registry.ConnectableBlockRoles;
import dev.moxinat.forcesofgravium.connectable.registry.ConnectableRegistry;

public final class InverterStateCalculator {

    private InverterStateCalculator() {
    }

    public static String computeInputMode(World world, Vector3i position) {
        BlockType blockType = world.getBlockType(position.x(), position.y(), position.z());
        if (blockType == null || !ConnectableRegistry.isInverterId(blockType.getId())) {
            return GravityPowderSpecialStateStore.STATE_OFF;
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
            return GravityPowderSpecialStateStore.STATE_OFF;
        }

        if (ConnectableBlockRoles.isSource(backNeighborType.getId())) {
            if (!ConnectableNeighborResolver.isSourceNeighborOf(world, backNeighborPosition, position)) {
                return GravityPowderSpecialStateStore.STATE_OFF;
            }
            return directSourceInputMode(world, backNeighborPosition, backNeighborType.getId());
        }

        if (ConnectableRegistry.isGravityPowderCarrierId(backNeighborType.getId())) {
            if (!ConnectableNeighborResolver.areMutuallyConnected(world, backNeighborPosition, position)) {
                return GravityPowderSpecialStateStore.STATE_OFF;
            }
            return ConnectableRuntimeAccessor.effectiveState(world, backNeighborPosition);
        }

        if (!ConnectableRegistry.isInverterId(backNeighborType.getId())) {
            return GravityPowderSpecialStateStore.STATE_OFF;
        }

        Vector3i upstreamFrontPosition = ConnectableNeighborResolver.adjacentPositionForLocalSide(
                world,
                backNeighborPosition,
                ConnectableRegistry.SIDE_FRONT
        );
        if (!upstreamFrontPosition.equals(position)) {
            return GravityPowderSpecialStateStore.STATE_OFF;
        }

        return ConnectableRuntimeAccessor.instantState(world, backNeighborPosition);
    }

    static String directSourceInputMode(World world, Vector3i position, String blockId) {
        return ConnectableBlockRoles.isSource(blockId)
                ? ConnectableRuntimeAccessor.sourceOutputState(world, position)
                : GravityPowderSpecialStateStore.STATE_OFF;
    }

    public static String invertMode(String mode) {
        if (GravityPowderSpecialStateStore.STATE_PUSH.equals(mode)) {
            return GravityPowderSpecialStateStore.STATE_PULL;
        }
        if (GravityPowderSpecialStateStore.STATE_PULL.equals(mode)) {
            return GravityPowderSpecialStateStore.STATE_PUSH;
        }
        return GravityPowderSpecialStateStore.STATE_OFF;
    }
}
