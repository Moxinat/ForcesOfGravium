package dev.moxinat.forcesofgravium.logic.inverter;

import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import dev.moxinat.forcesofgravium.data.GravityPowderBlockDataStore;
import dev.moxinat.forcesofgravium.data.GravityPowderBlockDataStore.GravityPowderBlockData;
import dev.moxinat.forcesofgravium.data.InverterDataStore;
import dev.moxinat.forcesofgravium.data.InverterDataStore.InverterData;
import dev.moxinat.forcesofgravium.data.SourceBlockDataStore;
import dev.moxinat.forcesofgravium.logic.gravity.GravityPowderStateCalculator;
import dev.moxinat.forcesofgravium.logic.network.ConnectableNeighborResolver;
import dev.moxinat.forcesofgravium.registry.ConnectableBlockRoles;
import dev.moxinat.forcesofgravium.registry.ConnectableRegistry;

public final class InverterStateCalculator {

    private InverterStateCalculator() {
    }

    public static String computeInputMode(World world, Vector3i position) {
        BlockType blockType = world.getBlockType(position.getX(), position.getY(), position.getZ());
        if (blockType == null || !ConnectableRegistry.isInverterId(blockType.getId())) {
            return GravityPowderStateCalculator.MODE_OFF;
        }

        Vector3i backNeighborPosition = ConnectableNeighborResolver.adjacentPositionForLocalSide(
                world,
                position,
                ConnectableRegistry.SIDE_BACK
        );
        BlockType backNeighborType = world.getBlockType(
                backNeighborPosition.getX(),
                backNeighborPosition.getY(),
                backNeighborPosition.getZ()
        );
        if (backNeighborType == null) {
            return GravityPowderStateCalculator.MODE_OFF;
        }

        if (ConnectableBlockRoles.isSource(backNeighborType.getId())) {
            if (!ConnectableNeighborResolver.isSourceNeighborOf(world, backNeighborPosition, position)) {
                return GravityPowderStateCalculator.MODE_OFF;
            }
            return directSourceInputMode(world, backNeighborPosition, backNeighborType.getId());
        }

        if (ConnectableRegistry.isGravityPowderId(backNeighborType.getId())) {
            GravityPowderBlockData backNeighborData = GravityPowderBlockDataStore.getOrCreate(world, backNeighborPosition);
            return GravityPowderBlockDataStore.effectiveMode(backNeighborData);
        }

        if (!ConnectableRegistry.isInverterId(backNeighborType.getId())) {
            return GravityPowderStateCalculator.MODE_OFF;
        }

        Vector3i upstreamFrontPosition = ConnectableNeighborResolver.adjacentPositionForLocalSide(
                world,
                backNeighborPosition,
                ConnectableRegistry.SIDE_FRONT
        );
        if (!upstreamFrontPosition.equals(position)) {
            return GravityPowderStateCalculator.MODE_OFF;
        }

        InverterData backNeighborData = InverterDataStore.getOrCreate(world, backNeighborPosition);
        return backNeighborData.currentMode();
    }

    static String directSourceInputMode(World world, Vector3i position, String blockId) {
        if (ConnectableBlockRoles.isSource(blockId) && SourceBlockDataStore.isActive(world, position, blockId)) {
            return GravityPowderStateCalculator.MODE_PUSH;
        }
        return GravityPowderStateCalculator.MODE_OFF;
    }

    public static String invertMode(String mode) {
        if (GravityPowderStateCalculator.MODE_PUSH.equals(mode)) {
            return GravityPowderStateCalculator.MODE_PULL;
        }
        if (GravityPowderStateCalculator.MODE_PULL.equals(mode)) {
            return GravityPowderStateCalculator.MODE_PUSH;
        }
        return GravityPowderStateCalculator.MODE_OFF;
    }
}
