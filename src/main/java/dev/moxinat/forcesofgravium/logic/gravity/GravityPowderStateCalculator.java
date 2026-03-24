package dev.moxinat.forcesofgravium.logic.gravity;

import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import dev.moxinat.forcesofgravium.data.GravityPowderBlockDataStore;
import dev.moxinat.forcesofgravium.data.GravityPowderBlockDataStore.GravityPowderBlockData;
import dev.moxinat.forcesofgravium.data.GravityPowderBlockDataStore.PositionDistance;
import dev.moxinat.forcesofgravium.logic.network.ConnectableNeighborResolver;
import dev.moxinat.forcesofgravium.registry.ConnectableBlockRoles;

import java.util.ArrayList;
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
        List<GravityPowderBlockData> neighbors = ConnectableNeighborResolver.neighboringGravityPowderData(world, position, null);
        List<Vector3i> sourceNeighbors = ConnectableNeighborResolver.sourceNeighbors(world, position, null);
        boolean hasSourceNeighbor = !sourceNeighbors.isEmpty();
        List<PositionDistance> retainedDistances = retainReachableDistances(world, position, selfData.positionDistances(), neighbors, sourceNeighbors);
        ModeDistances pushCandidate = mergedCandidate(world, neighbors, MODE_PUSH);
        ModeDistances pullCandidate = mergedCandidate(world, neighbors, MODE_PULL);

        if (hasSourceNeighbor) {
            List<PositionDistance> nextDistances = mergeDistanceLists(
                    ConnectableNeighborResolver.sourceNeighborDistances(sourceNeighbors),
                    retainedDistances,
                    pushCandidate == null ? List.of() : pushCandidate.positionDistances()
            );
            return new GravityPowderStateUpdate(position, MODE_PUSH, true, 0, nextDistances);
        }

        String currentMode = selfData.currentMode();

        if (MODE_OFF.equals(currentMode)) {
            if (pushCandidate != null) {
                return new GravityPowderStateUpdate(position, MODE_PUSH, true, 0, pushCandidate.positionDistances());
            }
            if (pullCandidate != null) {
                return new GravityPowderStateUpdate(position, MODE_PULL, true, 0, pullCandidate.positionDistances());
            }
        }

        if (MODE_PULL.equals(currentMode) && pushCandidate != null) {
            return new GravityPowderStateUpdate(position, MODE_PUSH, true, 0, pushCandidate.positionDistances());
        }

        if (MODE_PUSH.equals(currentMode)) {
            List<PositionDistance> nextDistances = mergeDistanceLists(
                    retainedDistances,
                    pushCandidate == null ? List.of() : pushCandidate.positionDistances()
            );
            if (!nextDistances.isEmpty()) {
                return new GravityPowderStateUpdate(position, MODE_PUSH, true, 0, nextDistances);
            }
            return new GravityPowderStateUpdate(position, MODE_OFF, false, 0, List.of());
        }

        if (MODE_PULL.equals(currentMode) && pullCandidate != null) {
            return new GravityPowderStateUpdate(position, MODE_PULL, true, 0, pullCandidate.positionDistances());
        }

        if (!retainedDistances.equals(selfData.positionDistances())) {
            if (retainedDistances.isEmpty()) {
                return new GravityPowderStateUpdate(position, MODE_OFF, false, 0, List.of());
            }
            return new GravityPowderStateUpdate(position, currentMode, true, 0, retainedDistances);
        }

        if (retainedDistances.isEmpty()) {
            return new GravityPowderStateUpdate(position, MODE_OFF, false, 0, List.of());
        }

        return new GravityPowderStateUpdate(position, currentMode, selfData.stable(), selfData.lossTicks(), selfData.positionDistances());
    }

    private static List<PositionDistance> retainReachableDistances(
            World world,
            Vector3i position,
            List<PositionDistance> positionDistances,
            List<GravityPowderBlockData> neighbors,
            List<Vector3i> sourceNeighbors
    ) {
        List<PositionDistance> retained = new ArrayList<>();
        for (PositionDistance ownDistance : positionDistances) {
            if (isLiveSourceAdjacent(position, ownDistance, sourceNeighbors) || hasNeighborWithSmallerDistance(world, ownDistance, neighbors)) {
                retained.add(ownDistance);
            }
        }
        return List.copyOf(retained);
    }

    private static boolean hasNeighborWithSmallerDistance(World world, PositionDistance ownDistance, List<GravityPowderBlockData> neighbors) {
        for (GravityPowderBlockData neighbor : neighbors) {
            if (!neighbor.stable()) {
                continue;
            }
            for (PositionDistance neighborDistance : neighbor.positionDistances()) {
                if (sameTarget(ownDistance, neighborDistance) && neighborDistance.distance() < ownDistance.distance()) {
                    return true;
                }
            }
        }
        return false;
    }

    private static ModeDistances mergedCandidate(World world, List<GravityPowderBlockData> neighbors, String mode) {
        List<PositionDistance> merged = new ArrayList<>();
        for (GravityPowderBlockData neighbor : neighbors) {
            if (!neighbor.stable()) {
                continue;
            }
            if (!mode.equals(neighbor.currentMode())) {
                continue;
            }

            for (PositionDistance neighborDistance : neighbor.positionDistances()) {
                if (!isLiveSourceTarget(world, neighborDistance)) {
                    continue;
                }
                mergeDistance(merged, new PositionDistance(
                        neighborDistance.x(),
                        neighborDistance.y(),
                        neighborDistance.z(),
                        neighborDistance.distance() + 1
                ));
            }
        }

        if (merged.isEmpty()) {
            return null;
        }
        return new ModeDistances(mode, List.copyOf(merged));
    }

    private static void mergeDistance(List<PositionDistance> distances, PositionDistance candidate) {
        for (int i = 0; i < distances.size(); i++) {
            PositionDistance existing = distances.get(i);
            if (sameTarget(existing, candidate)) {
                if (candidate.distance() < existing.distance()) {
                    distances.set(i, candidate);
                }
                return;
            }
        }
        distances.add(candidate);
    }

    @SafeVarargs
    private static List<PositionDistance> mergeDistanceLists(List<PositionDistance>... distanceLists) {
        List<PositionDistance> merged = new ArrayList<>();
        for (List<PositionDistance> distanceList : distanceLists) {
            for (PositionDistance distance : distanceList) {
                mergeDistance(merged, distance);
            }
        }
        return List.copyOf(merged);
    }

    private static boolean sameTarget(PositionDistance first, PositionDistance second) {
        return first.x() == second.x() && first.y() == second.y() && first.z() == second.z();
    }

    private static boolean isLiveSourceAdjacent(Vector3i position, PositionDistance target, List<Vector3i> sourceNeighbors) {
        for (Vector3i sourceNeighbor : sourceNeighbors) {
            if (sourceNeighbor.getX() == target.x()
                    && sourceNeighbor.getY() == target.y()
                    && sourceNeighbor.getZ() == target.z()) {
                return isAdjacent(position, sourceNeighbor);
            }
        }
        return false;
    }

    private static boolean isAdjacent(Vector3i position, Vector3i target) {
        int dx = Math.abs(position.getX() - target.getX());
        int dy = Math.abs(position.getY() - target.getY());
        int dz = Math.abs(position.getZ() - target.getZ());
        return dx + dy + dz == 1;
    }

    private static boolean isLiveSourceTarget(World world, PositionDistance target) {
        BlockType blockType = world.getBlockType(target.x(), target.y(), target.z());
        return blockType != null && ConnectableBlockRoles.isSource(blockType.getId());
    }

    private record ModeDistances(String mode, List<PositionDistance> positionDistances) {
        private ModeDistances {
            Objects.requireNonNull(mode, "mode");
            positionDistances = List.copyOf(positionDistances);
        }
    }

    public record GravityPowderStateUpdate(Vector3i position, String nextMode, boolean nextStable, int nextLossTicks, List<PositionDistance> nextPositionDistances) {
        public GravityPowderStateUpdate {
            Objects.requireNonNull(position, "position");
            Objects.requireNonNull(nextMode, "nextMode");
            nextPositionDistances = List.copyOf(nextPositionDistances);
        }
    }
}
