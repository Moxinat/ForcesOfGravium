package dev.moxinat.forcesofgravium.logic.inverter;

import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import dev.moxinat.forcesofgravium.data.GravityPowderBlockDataStore;
import dev.moxinat.forcesofgravium.data.GravityPowderBlockDataStore.GravityPowderBlockData;
import dev.moxinat.forcesofgravium.data.GravityPowderBlockDataStore.PositionDistance;
import dev.moxinat.forcesofgravium.logic.gravity.GravityPowderStateCalculator;
import dev.moxinat.forcesofgravium.logic.network.ConnectableNeighborResolver;
import dev.moxinat.forcesofgravium.registry.ConnectableRegistry;

import java.util.List;
import java.util.Objects;

public final class InverterStateCalculator {

    private InverterStateCalculator() {
    }

    public static InverterStateUpdate computeStateUpdate(World world, Vector3i position) {
        BlockType blockType = world.getBlockType(position.getX(), position.getY(), position.getZ());
        if (blockType == null || !ConnectableRegistry.isInverterId(blockType.getId())) {
            return new InverterStateUpdate(position, GravityPowderStateCalculator.MODE_OFF, false, List.of());
        }

        Vector3i backNeighborPosition = ConnectableNeighborResolver.adjacentPositionForLocalSide(world, position, ConnectableRegistry.SIDE_BACK);
        BlockType backNeighborType = world.getBlockType(backNeighborPosition.getX(), backNeighborPosition.getY(), backNeighborPosition.getZ());
        if (backNeighborType == null || !ConnectableRegistry.isGravityPowderId(backNeighborType.getId())) {
            return new InverterStateUpdate(position, GravityPowderStateCalculator.MODE_OFF, false, List.of());
        }

        GravityPowderBlockData backNeighborData = GravityPowderBlockDataStore.getOrCreate(world, backNeighborPosition);
        String nextMode = invertMode(backNeighborData.currentMode());
        boolean nextStable = backNeighborData.stable() && !GravityPowderStateCalculator.MODE_OFF.equals(nextMode);
        List<PositionDistance> nextDistances = GravityPowderStateCalculator.MODE_OFF.equals(nextMode)
                ? List.of()
                : backNeighborData.positionDistances();
        return new InverterStateUpdate(position, nextMode, nextStable, nextDistances);
    }

    private static String invertMode(String mode) {
        if (GravityPowderStateCalculator.MODE_PUSH.equals(mode)) {
            return GravityPowderStateCalculator.MODE_PULL;
        }
        if (GravityPowderStateCalculator.MODE_PULL.equals(mode)) {
            return GravityPowderStateCalculator.MODE_PUSH;
        }
        return GravityPowderStateCalculator.MODE_OFF;
    }

    public record InverterStateUpdate(Vector3i position, String nextMode, boolean nextStable, List<PositionDistance> nextPositionDistances) {
        public InverterStateUpdate {
            Objects.requireNonNull(position, "position");
            Objects.requireNonNull(nextMode, "nextMode");
            nextPositionDistances = List.copyOf(nextPositionDistances);
        }
    }
}
