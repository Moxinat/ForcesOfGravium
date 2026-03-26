package dev.moxinat.forcesofgravium.logic.gravity;

import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import dev.moxinat.forcesofgravium.data.GravityPowderBlockDataStore;
import dev.moxinat.forcesofgravium.data.GravityPowderBlockDataStore.GravityPowderBlockData;
import dev.moxinat.forcesofgravium.data.InverterDataStore;
import dev.moxinat.forcesofgravium.data.InverterDataStore.InverterData;
import dev.moxinat.forcesofgravium.logic.network.ConnectableNeighborResolver;
import dev.moxinat.forcesofgravium.registry.ConnectableBlockRoles;
import dev.moxinat.forcesofgravium.registry.ConnectableRegistry;

import java.util.List;
import java.util.Objects;

public final class GravityPowderStateCalculator {
    public static final String MODE_OFF = "off";
    public static final String MODE_PUSH = "push";
    public static final String MODE_PULL = "pull";

    private GravityPowderStateCalculator() {
    }

    public static GravityPowderStateUpdate computeStateUpdate(World world, Vector3i position) {
        GravityPowderBlockData selfData = GravityPowderBlockDataStore.getOrCreate(world, position);
        if (!GravityPowderBlockDataStore.WAVE_NONE.equals(selfData.decayMark())) {
            return new GravityPowderStateUpdate(position, selfData.currentMode());
        }
        String nextMode = resolveMode(world, position, selfData.currentMode());
        return new GravityPowderStateUpdate(position, nextMode);
    }

    private static String resolveMode(World world, Vector3i position, String currentMode) {
        boolean hasSourceNeighbor = hasAdjacentSource(world, position);
        boolean hasOffWaveNeighbor = false;
        boolean hasPullWaveNeighbor = false;
        boolean hasPushNeighbor = false;
        boolean hasPullNeighbor = false;
        for (Vector3i neighborPosition : ConnectableNeighborResolver.positionsAround(position)) {
            if (neighborPosition.equals(position)) {
                continue;
            }

            BlockType neighborType = world.getBlockType(
                    neighborPosition.getX(),
                    neighborPosition.getY(),
                    neighborPosition.getZ()
            );
            if (neighborType == null) {
                continue;
            }

            if (ConnectableRegistry.isGravityPowderId(neighborType.getId())) {
                GravityPowderBlockData data = GravityPowderBlockDataStore.get(world, neighborPosition);
                if (data == null) {
                    continue;
                }
                hasOffWaveNeighbor |= GravityPowderBlockDataStore.WAVE_OFF.equals(data.decayMark());
                hasPullWaveNeighbor |= GravityPowderBlockDataStore.WAVE_PULL.equals(data.decayMark());
                hasPushNeighbor |= MODE_PUSH.equals(data.currentMode());
                hasPullNeighbor |= MODE_PULL.equals(data.currentMode());
                continue;
            }

            if (!ConnectableRegistry.isInverterId(neighborType.getId())) {
                continue;
            }

            Vector3i frontPosition = ConnectableNeighborResolver.adjacentPositionForLocalSide(world, neighborPosition, ConnectableRegistry.SIDE_FRONT);
            if (!frontPosition.equals(position)) {
                continue;
            }

            InverterData data = InverterDataStore.get(world, neighborPosition);
            if (data == null) {
                continue;
            }
            hasPushNeighbor |= MODE_PUSH.equals(data.currentMode());
            hasPullNeighbor |= MODE_PULL.equals(data.currentMode());
        }

        if (hasOffWaveNeighbor) {
            return MODE_OFF;
        }
        if (hasPullWaveNeighbor) {
            return MODE_PULL;
        }
        if (hasSourceNeighbor) {
            return MODE_PUSH;
        }
        if (hasPushNeighbor) {
            return MODE_PUSH;
        }
        if (hasPullNeighbor) {
            return MODE_PULL;
        }
        return currentMode;
    }

    private static boolean hasAdjacentSource(World world, Vector3i position) {
        List<Vector3i> sourceNeighbors = ConnectableNeighborResolver.sourceNeighbors(world, position, null);
        for (Vector3i sourceNeighbor : sourceNeighbors) {
            BlockType sourceType = world.getBlockType(sourceNeighbor.getX(), sourceNeighbor.getY(), sourceNeighbor.getZ());
            if (sourceType != null && ConnectableBlockRoles.isSource(sourceType.getId())) {
                return true;
            }
        }
        return false;
    }

    public record GravityPowderStateUpdate(Vector3i position, String nextMode) {
        public GravityPowderStateUpdate {
            Objects.requireNonNull(position, "position");
            Objects.requireNonNull(nextMode, "nextMode");
        }
    }
}
