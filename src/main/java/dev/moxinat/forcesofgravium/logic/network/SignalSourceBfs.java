package dev.moxinat.forcesofgravium.logic.network;

import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import dev.moxinat.forcesofgravium.data.GravityPowderBlockDataStore;
import dev.moxinat.forcesofgravium.data.GravityPowderBlockDataStore.GravityPowderBlockData;
import dev.moxinat.forcesofgravium.data.InverterDataStore;
import dev.moxinat.forcesofgravium.data.InverterDataStore.InverterData;
import dev.moxinat.forcesofgravium.logic.gravity.GravityPowderStateCalculator;
import dev.moxinat.forcesofgravium.registry.ConnectableRegistry;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class SignalSourceBfs {

    private SignalSourceBfs() {
    }

    public static @Nonnull SourceSearchResult findSource(@Nonnull World world, @Nonnull Vector3i start, @Nonnull String expectedMode) {
        return findSource(world, start, expectedMode, null);
    }

    public static @Nonnull SourceSearchResult findSource(
            @Nonnull World world,
            @Nonnull Vector3i start,
            @Nonnull String expectedMode,
            @Nullable Vector3i treatAsEmpty
    ) {
        return findSource(new WorldTraversalAdapter(world, treatAsEmpty), start, expectedMode);
    }

    public static @Nonnull ModeSearchResult resolveMode(@Nonnull World world, @Nonnull Vector3i start) {
        return resolveMode(world, start, null);
    }

    public static @Nonnull ModeSearchResult resolveMode(
            @Nonnull World world,
            @Nonnull Vector3i start,
            @Nullable Vector3i treatAsEmpty
    ) {
        return resolveMode(new WorldTraversalAdapter(world, treatAsEmpty), start);
    }

    public static @Nonnull SourceSearchResult findSource(@Nonnull TraversalAdapter adapter, @Nonnull Vector3i start, @Nonnull String expectedMode) {
        TraversalNode startNode = new TraversalNode(start, expectedMode);
        ArrayDeque<TraversalNode> queue = new ArrayDeque<>();
        Set<TraversalNode> visited = new LinkedHashSet<>();
        queue.add(startNode);
        visited.add(startNode);

        while (!queue.isEmpty()) {
            TraversalNode current = queue.removeFirst();
            Vector3i sourcePosition = adapter.findAdjacentSource(current);
            if (sourcePosition != null) {
                return new SourceSearchResult(true, current.expectedMode(), sourcePosition, List.copyOf(visited));
            }

            for (TraversalStep step : adapter.reverseTraversalSteps(current)) {
                TraversalNode next = new TraversalNode(step.position(), step.expectedMode());
                if (visited.add(next)) {
                    queue.addLast(next);
                }
            }
        }

        return new SourceSearchResult(false, expectedMode, null, List.copyOf(visited));
    }

    public static @Nonnull ModeSearchResult resolveMode(@Nonnull TraversalAdapter adapter, @Nonnull Vector3i start) {
        SourceSearchResult pushResult = findSource(adapter, start, GravityPowderStateCalculator.MODE_PUSH);
        if (pushResult.foundSource()) {
            return new ModeSearchResult(GravityPowderStateCalculator.MODE_PUSH, pushResult);
        }

        SourceSearchResult pullResult = findSource(adapter, start, GravityPowderStateCalculator.MODE_PULL);
        if (pullResult.foundSource()) {
            return new ModeSearchResult(GravityPowderStateCalculator.MODE_PULL, pullResult);
        }

        return new ModeSearchResult(GravityPowderStateCalculator.MODE_OFF, pushResult);
    }

    public interface TraversalAdapter {
        @Nullable Vector3i findAdjacentSource(@Nonnull TraversalNode node);

        @Nonnull List<TraversalStep> reverseTraversalSteps(@Nonnull TraversalNode node);
    }

    public record TraversalNode(@Nonnull Vector3i position, @Nonnull String expectedMode) {
        public TraversalNode {
            Objects.requireNonNull(position, "position");
            Objects.requireNonNull(expectedMode, "expectedMode");
        }
    }

    public record TraversalStep(@Nonnull Vector3i position, @Nonnull String expectedMode) {
        public TraversalStep {
            Objects.requireNonNull(position, "position");
            Objects.requireNonNull(expectedMode, "expectedMode");
        }
    }

    public record SourceSearchResult(
            boolean foundSource,
            @Nonnull String expectedMode,
            @Nullable Vector3i sourcePosition,
            @Nonnull List<TraversalNode> visitedNodes
    ) {
        public SourceSearchResult {
            Objects.requireNonNull(expectedMode, "expectedMode");
            visitedNodes = List.copyOf(Objects.requireNonNull(visitedNodes, "visitedNodes"));
        }
    }

    public record ModeSearchResult(@Nonnull String resolvedMode, @Nonnull SourceSearchResult searchResult) {
        public ModeSearchResult {
            Objects.requireNonNull(resolvedMode, "resolvedMode");
            Objects.requireNonNull(searchResult, "searchResult");
        }
    }

    private static final class WorldTraversalAdapter implements TraversalAdapter {

        private final World world;
        private final Vector3i treatAsEmpty;

        private WorldTraversalAdapter(World world, @Nullable Vector3i treatAsEmpty) {
            this.world = Objects.requireNonNull(world, "world");
            this.treatAsEmpty = treatAsEmpty;
        }

        @Override
        public @Nullable Vector3i findAdjacentSource(@Nonnull TraversalNode node) {
            if (!GravityPowderStateCalculator.MODE_PUSH.equals(node.expectedMode())) {
                return null;
            }
            if (isInverterAt(node.position())) {
                return null;
            }

            List<Vector3i> sourceNeighbors = ConnectableNeighborResolver.sourceNeighbors(world, node.position(), treatAsEmpty);
            return sourceNeighbors.isEmpty() ? null : sourceNeighbors.getFirst();
        }

        @Override
        public @Nonnull List<TraversalStep> reverseTraversalSteps(@Nonnull TraversalNode node) {
            List<TraversalStep> steps = new ArrayList<>();
            if (isInverterAt(node.position())) {
                addBackInputTransitionForInverter(node, steps);
            } else {
                addSameModeCableNeighbors(node, steps);
                addReverseInverterTransitions(node, steps);
            }
            return List.copyOf(steps);
        }

        private void addSameModeCableNeighbors(TraversalNode node, List<TraversalStep> steps) {
            for (Vector3i neighborPosition : ConnectableNeighborResolver.positionsAround(node.position())) {
                if (neighborPosition.equals(node.position())) {
                    continue;
                }
                if (isTreatAsEmpty(neighborPosition)) {
                    continue;
                }

                BlockType blockType = world.getBlockType(
                        neighborPosition.getX(),
                        neighborPosition.getY(),
                        neighborPosition.getZ()
                );
                if (blockType == null || !ConnectableRegistry.isGravityPowderId(blockType.getId())) {
                    continue;
                }

                GravityPowderBlockData data = GravityPowderBlockDataStore.get(world, neighborPosition);
                if (data == null || !node.expectedMode().equals(data.currentMode())) {
                    continue;
                }

                steps.add(new TraversalStep(neighborPosition, node.expectedMode()));
            }
        }

        private void addReverseInverterTransitions(TraversalNode node, List<TraversalStep> steps) {
            for (Vector3i neighborPosition : ConnectableNeighborResolver.positionsAround(node.position())) {
                if (neighborPosition.equals(node.position())) {
                    continue;
                }
                if (isTreatAsEmpty(neighborPosition)) {
                    continue;
                }

                BlockType blockType = world.getBlockType(
                        neighborPosition.getX(),
                        neighborPosition.getY(),
                        neighborPosition.getZ()
                );
                if (blockType == null || !ConnectableRegistry.isInverterId(blockType.getId())) {
                    continue;
                }

                Vector3i frontPosition = ConnectableNeighborResolver.adjacentPositionForLocalSide(
                        world,
                        neighborPosition,
                        ConnectableRegistry.SIDE_FRONT
                );
                if (!frontPosition.equals(node.position())) {
                    continue;
                }

                String flippedMode = flipMode(node.expectedMode());
                if (GravityPowderStateCalculator.MODE_OFF.equals(flippedMode)) {
                    continue;
                }

                Vector3i backPosition = ConnectableNeighborResolver.adjacentPositionForLocalSide(
                        world,
                        neighborPosition,
                        ConnectableRegistry.SIDE_BACK
                );
                if (isTreatAsEmpty(backPosition)) {
                    continue;
                }
                BlockType backType = world.getBlockType(backPosition.getX(), backPosition.getY(), backPosition.getZ());
                if (backType == null) {
                    continue;
                }

                if (ConnectableRegistry.isGravityPowderId(backType.getId())) {
                    GravityPowderBlockData backData = GravityPowderBlockDataStore.get(world, backPosition);
                    if (backData == null || !flippedMode.equals(backData.currentMode())) {
                        continue;
                    }
                    steps.add(new TraversalStep(backPosition, flippedMode));
                    continue;
                }

                if (!ConnectableRegistry.isInverterId(backType.getId())) {
                    continue;
                }

                InverterData backData = InverterDataStore.get(world, backPosition);
                if (backData == null || !flippedMode.equals(backData.currentMode())) {
                    continue;
                }

                Vector3i upstreamFrontPosition = ConnectableNeighborResolver.adjacentPositionForLocalSide(
                        world,
                        backPosition,
                        ConnectableRegistry.SIDE_FRONT
                );
                if (!upstreamFrontPosition.equals(neighborPosition)) {
                    continue;
                }

                steps.add(new TraversalStep(backPosition, flippedMode));
            }
        }

        private void addBackInputTransitionForInverter(TraversalNode node, List<TraversalStep> steps) {
            String flippedMode = flipMode(node.expectedMode());
            if (GravityPowderStateCalculator.MODE_OFF.equals(flippedMode)) {
                return;
            }

            Vector3i backPosition = ConnectableNeighborResolver.adjacentPositionForLocalSide(
                    world,
                    node.position(),
                    ConnectableRegistry.SIDE_BACK
            );
            if (isTreatAsEmpty(backPosition)) {
                return;
            }

            BlockType backType = world.getBlockType(backPosition.getX(), backPosition.getY(), backPosition.getZ());
            if (backType == null) {
                return;
            }

            if (ConnectableRegistry.isGravityPowderId(backType.getId())) {
                GravityPowderBlockData backData = GravityPowderBlockDataStore.get(world, backPosition);
                if (backData != null && flippedMode.equals(backData.currentMode())) {
                    steps.add(new TraversalStep(backPosition, flippedMode));
                }
                return;
            }

            if (!ConnectableRegistry.isInverterId(backType.getId())) {
                return;
            }

            InverterData backData = InverterDataStore.get(world, backPosition);
            if (backData == null || !flippedMode.equals(backData.currentMode())) {
                return;
            }

            Vector3i upstreamFrontPosition = ConnectableNeighborResolver.adjacentPositionForLocalSide(
                    world,
                    backPosition,
                    ConnectableRegistry.SIDE_FRONT
            );
            if (upstreamFrontPosition.equals(node.position())) {
                steps.add(new TraversalStep(backPosition, flippedMode));
            }
        }

        private static String flipMode(String mode) {
            if (GravityPowderStateCalculator.MODE_PUSH.equals(mode)) {
                return GravityPowderStateCalculator.MODE_PULL;
            }
            if (GravityPowderStateCalculator.MODE_PULL.equals(mode)) {
                return GravityPowderStateCalculator.MODE_PUSH;
            }
            return GravityPowderStateCalculator.MODE_OFF;
        }

        private boolean isTreatAsEmpty(Vector3i position) {
            return treatAsEmpty != null && treatAsEmpty.equals(position);
        }

        private boolean isInverterAt(Vector3i position) {
            BlockType blockType = world.getBlockType(position.getX(), position.getY(), position.getZ());
            return blockType != null && ConnectableRegistry.isInverterId(blockType.getId());
        }
    }
}
