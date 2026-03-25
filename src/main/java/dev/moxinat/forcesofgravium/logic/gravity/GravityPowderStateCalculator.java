package dev.moxinat.forcesofgravium.logic.gravity;

import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import dev.moxinat.forcesofgravium.data.GravityPowderBlockDataStore;
import dev.moxinat.forcesofgravium.data.GravityPowderBlockDataStore.GravityPowderBlockData;
import dev.moxinat.forcesofgravium.data.GravityPowderBlockDataStore.PositionDistance;
import dev.moxinat.forcesofgravium.data.InverterDataStore;
import dev.moxinat.forcesofgravium.data.InverterDataStore.InverterData;
import dev.moxinat.forcesofgravium.logic.network.ConnectableNeighborResolver;
import dev.moxinat.forcesofgravium.registry.ConnectableBlockRoles;
import dev.moxinat.forcesofgravium.registry.ConnectableRegistry;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class GravityPowderStateCalculator {
    public static final String MODE_OFF = "off";
    public static final String MODE_PUSH = "push";
    public static final String MODE_PULL = "pull";

    private GravityPowderStateCalculator() {
    }

    public static GravityPowderStateUpdate computeStateUpdate(World world, Vector3i position) {
        GravityPowderBlockData selfData = GravityPowderBlockDataStore.getOrCreate(world, position);
        List<NeighborSignal> neighbors = neighboringSignals(world, position);
        List<Vector3i> sourceNeighbors = ConnectableNeighborResolver.sourceNeighbors(world, position, null);
        boolean hasSourceNeighbor = !sourceNeighbors.isEmpty();
        String currentMode = selfData.currentMode();
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

        if (MODE_OFF.equals(currentMode)) {
            if (pushCandidate != null) {
                return new GravityPowderStateUpdate(
                        position,
                        MODE_PUSH,
                        true,
                        0,
                        mergeDistanceLists(
                                retainedDistances,
                                acceptedCandidateDistances(selfData.positionDistances(), pushCandidate.positionDistances())
                        )
                );
            }
            if (pullCandidate != null) {
                return new GravityPowderStateUpdate(
                        position,
                        MODE_PULL,
                        true,
                        0,
                        mergeDistanceLists(
                                retainedDistances,
                                acceptedCandidateDistances(selfData.positionDistances(), pullCandidate.positionDistances())
                        )
                );
            }
        }

        if (MODE_PULL.equals(currentMode) && pushCandidate != null) {
            return new GravityPowderStateUpdate(
                    position,
                    MODE_PUSH,
                    true,
                    0,
                    mergeDistanceLists(
                            retainedDistances,
                            acceptedCandidateDistances(selfData.positionDistances(), pushCandidate.positionDistances())
                    )
            );
        }

        if (MODE_PUSH.equals(currentMode)) {
            if (pushCandidate != null) {
                return new GravityPowderStateUpdate(
                        position,
                        MODE_PUSH,
                        true,
                        0,
                        mergeDistanceLists(
                                retainedDistances,
                                acceptedCandidateDistances(selfData.positionDistances(), pushCandidate.positionDistances())
                        )
                );
            }
            if (pullCandidate != null) {
                return new GravityPowderStateUpdate(
                        position,
                        MODE_PULL,
                        true,
                        0,
                        mergeDistanceLists(
                                retainedDistances,
                                acceptedCandidateDistances(selfData.positionDistances(), pullCandidate.positionDistances())
                        )
                );
            }
            return new GravityPowderStateUpdate(position, MODE_OFF, false, 0, List.of());
        }

        if (MODE_PULL.equals(currentMode) && pullCandidate != null) {
            return new GravityPowderStateUpdate(
                    position,
                    MODE_PULL,
                    true,
                    0,
                    mergeDistanceLists(
                            retainedDistances,
                            acceptedCandidateDistances(selfData.positionDistances(), pullCandidate.positionDistances())
                    )
            );
        }

        if (!retainedDistances.equals(selfData.positionDistances())) {
            if (retainedDistances.isEmpty()) {
                if (pushCandidate != null) {
                    return new GravityPowderStateUpdate(
                            position,
                            MODE_PUSH,
                            true,
                            0,
                            acceptedCandidateDistances(selfData.positionDistances(), pushCandidate.positionDistances())
                    );
                }
                if (pullCandidate != null) {
                    return new GravityPowderStateUpdate(
                            position,
                            MODE_PULL,
                            true,
                            0,
                            acceptedCandidateDistances(selfData.positionDistances(), pullCandidate.positionDistances())
                    );
                }
                return new GravityPowderStateUpdate(position, MODE_OFF, false, 0, List.of());
            }
            return new GravityPowderStateUpdate(position, currentMode, true, 0, retainedDistances);
        }

        if (retainedDistances.isEmpty()) {
            if (pushCandidate != null) {
                return new GravityPowderStateUpdate(
                        position,
                        MODE_PUSH,
                        true,
                        0,
                        acceptedCandidateDistances(selfData.positionDistances(), pushCandidate.positionDistances())
                );
            }
            if (pullCandidate != null) {
                return new GravityPowderStateUpdate(
                        position,
                        MODE_PULL,
                        true,
                        0,
                        acceptedCandidateDistances(selfData.positionDistances(), pullCandidate.positionDistances())
                );
            }
            return new GravityPowderStateUpdate(position, MODE_OFF, false, 0, List.of());
        }

        return new GravityPowderStateUpdate(position, currentMode, selfData.stable(), selfData.lossTicks(), selfData.positionDistances());
    }

    private static List<PositionDistance> retainReachableDistances(
            World world,
            Vector3i position,
            List<PositionDistance> positionDistances,
            List<NeighborSignal> neighbors,
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

    private static boolean hasNeighborWithSmallerDistance(World world, PositionDistance ownDistance, List<NeighborSignal> neighbors) {
        for (NeighborSignal neighbor : neighbors) {
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

    private static ModeDistances mergedCandidate(World world, List<NeighborSignal> neighbors, String mode) {
        List<PositionDistance> merged = new ArrayList<>();
        for (NeighborSignal neighbor : neighbors) {
            if (!neighbor.stable()) {
                continue;
            }
            if (!mode.equals(neighbor.currentMode())) {
                continue;
            }

            for (PositionDistance neighborDistance : neighbor.positionDistances()) {
                if (!hasLivePathToSource(world, neighbor.position(), neighborDistance, new HashSet<>())) {
                    continue;
                }
                PositionDistance candidate = new PositionDistance(
                        neighborDistance.x(),
                        neighborDistance.y(),
                        neighborDistance.z(),
                        neighborDistance.distance() + 1
                );
                mergeDistance(merged, candidate);
            }
        }

        if (merged.isEmpty()) {
            return null;
        }
        return new ModeDistances(mode, List.copyOf(merged));
    }

    private static List<PositionDistance> acceptedCandidateDistances(List<PositionDistance> ownDistances, List<PositionDistance> candidateDistances) {
        List<PositionDistance> accepted = new ArrayList<>();
        for (PositionDistance candidateDistance : candidateDistances) {
            if (canAcceptCandidateDistance(ownDistances, candidateDistance)) {
                mergeDistance(accepted, candidateDistance);
            }
        }
        return List.copyOf(accepted);
    }

    private static boolean hasLivePathToSource(World world, Vector3i position, PositionDistance distance, Set<PathNode> visited) {
        if (distance.distance() <= 0) {
            return false;
        }

        PathNode node = new PathNode(position, distance);
        if (!visited.add(node)) {
            return false;
        }

        BlockType currentBlockType = world.getBlockType(position.getX(), position.getY(), position.getZ());
        if (currentBlockType != null && ConnectableRegistry.isInverterId(currentBlockType.getId())) {
            PositionDistance backNeighborDistance = matchingBackNeighborDistance(world, position, distance);
            if (backNeighborDistance != null && hasLivePathToSource(world, backNeighborPosition(world, position), backNeighborDistance, visited)) {
                return true;
            }
        }

        if (distance.distance() == 1) {
            return isAdjacent(position, new Vector3i(distance.x(), distance.y(), distance.z()))
                    && isLiveSourceTarget(world, distance);
        }

        for (NeighborSignal neighbor : neighboringSignals(world, position)) {
            if (!neighbor.stable()) {
                continue;
            }

            PositionDistance matchingNeighborDistance = neighbor.distanceToTarget(distance);
            if (matchingNeighborDistance == null || matchingNeighborDistance.distance() != distance.distance() - 1) {
                continue;
            }

            if (hasLivePathToSource(world, neighbor.position(), matchingNeighborDistance, visited)) {
                return true;
            }
        }

        return false;
    }

    private static PositionDistance matchingBackNeighborDistance(World world, Vector3i inverterPosition, PositionDistance targetDistance) {
        Vector3i backNeighborPosition = backNeighborPosition(world, inverterPosition);
        GravityPowderBlockData backNeighborData = GravityPowderBlockDataStore.get(world, backNeighborPosition);
        if (backNeighborData == null || !backNeighborData.stable()) {
            return null;
        }

        for (PositionDistance positionDistance : backNeighborData.positionDistances()) {
            if (sameTarget(positionDistance, targetDistance) && positionDistance.distance() == targetDistance.distance()) {
                return positionDistance;
            }
        }
        return null;
    }

    private static Vector3i backNeighborPosition(World world, Vector3i inverterPosition) {
        return ConnectableNeighborResolver.adjacentPositionForLocalSide(world, inverterPosition, ConnectableRegistry.SIDE_BACK);
    }

    private static boolean canAcceptCandidateDistance(List<PositionDistance> ownDistances, PositionDistance candidate) {
        for (PositionDistance ownDistance : ownDistances) {
            if (!sameTarget(ownDistance, candidate)) {
                continue;
            }
            return candidate.distance() <= ownDistance.distance();
        }
        return true;
    }

    private static List<NeighborSignal> neighboringSignals(World world, Vector3i position) {
        List<NeighborSignal> signals = new ArrayList<>();
        addNeighborSignal(world, signals, position, new Vector3i(position.getX() + 1, position.getY(), position.getZ()));
        addNeighborSignal(world, signals, position, new Vector3i(position.getX() - 1, position.getY(), position.getZ()));
        addNeighborSignal(world, signals, position, new Vector3i(position.getX(), position.getY(), position.getZ() + 1));
        addNeighborSignal(world, signals, position, new Vector3i(position.getX(), position.getY(), position.getZ() - 1));
        addNeighborSignal(world, signals, position, new Vector3i(position.getX(), position.getY() + 1, position.getZ()));
        addNeighborSignal(world, signals, position, new Vector3i(position.getX(), position.getY() - 1, position.getZ()));
        return List.copyOf(signals);
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

    private static void addNeighborSignal(World world, List<NeighborSignal> signals, Vector3i position, Vector3i neighborPosition) {
        BlockType neighborBlockType = world.getBlockType(neighborPosition.getX(), neighborPosition.getY(), neighborPosition.getZ());
        if (neighborBlockType == null) {
            return;
        }

        if (ConnectableRegistry.isGravityPowderId(neighborBlockType.getId())) {
            GravityPowderBlockData data = GravityPowderBlockDataStore.get(world, neighborPosition);
            if (data != null) {
                signals.add(new NeighborSignal(neighborPosition, data.currentMode(), data.stable(), data.positionDistances()));
            }
            return;
        }

        if (!ConnectableRegistry.isInverterId(neighborBlockType.getId())) {
            return;
        }

        Vector3i frontPosition = ConnectableNeighborResolver.adjacentPositionForLocalSide(world, neighborPosition, ConnectableRegistry.SIDE_FRONT);
        if (!frontPosition.equals(position)) {
            return;
        }

        InverterData data = InverterDataStore.get(world, neighborPosition);
        if (data != null) {
            signals.add(new NeighborSignal(neighborPosition, data.currentMode(), data.stable(), data.positionDistances()));
        }
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

    private record NeighborSignal(Vector3i position, String currentMode, boolean stable, List<PositionDistance> positionDistances) {
        private NeighborSignal {
            Objects.requireNonNull(position, "position");
            Objects.requireNonNull(currentMode, "currentMode");
            positionDistances = List.copyOf(positionDistances);
        }

        private PositionDistance distanceToTarget(PositionDistance target) {
            for (PositionDistance positionDistance : positionDistances) {
                if (sameTarget(positionDistance, target)) {
                    return positionDistance;
                }
            }
            return null;
        }
    }

    private record PathNode(Vector3i position, PositionDistance distance) {
        private PathNode {
            Objects.requireNonNull(position, "position");
            Objects.requireNonNull(distance, "distance");
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
