package dev.moxinat.forcesofgravium.connectable.propagation;

import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import dev.moxinat.forcesofgravium.block.gravity.CasedGravityPowderBlockRefresher;
import dev.moxinat.forcesofgravium.block.gravity.GravityPowderBlockRefresher;
import dev.moxinat.forcesofgravium.block.gravity.GravityPowderSpecialStateStore;
import dev.moxinat.forcesofgravium.block.inverter.InverterBlockRefresher;
import dev.moxinat.forcesofgravium.connectable.core.ConnectableRuntimeAccessor;
import dev.moxinat.forcesofgravium.connectable.network.ConnectableNetworkIndexer;
import dev.moxinat.forcesofgravium.connectable.network.ConnectableNetworkUpdateService;
import dev.moxinat.forcesofgravium.connectable.registry.ConnectableBlockRoles;
import dev.moxinat.forcesofgravium.connectable.registry.ConnectableRegistry;
import dev.moxinat.forcesofgravium.connectable.spatial.ConnectableNeighborResolver;
import dev.moxinat.forcesofgravium.connectable.spatial.ConnectableNeighborResolver.WorldSide;
import org.joml.Vector3i;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class ConnectablePropagationScheduler {

    private static final int[] LOCAL_SIDES = new int[]{
            ConnectableRegistry.SIDE_FRONT,
            ConnectableRegistry.SIDE_BACK,
            ConnectableRegistry.SIDE_RIGHT,
            ConnectableRegistry.SIDE_LEFT,
            ConnectableRegistry.SIDE_TOP,
            ConnectableRegistry.SIDE_BOTTOM
    };

    private static final Map<World, Set<Vector3i>> PENDING_RECOMPUTE = new ConcurrentHashMap<>();
    private static final Map<World, Set<Vector3i>> PENDING_PLACED = new ConcurrentHashMap<>();
    private static final Map<World, Set<Vector3i>> PENDING_BROKEN = new ConcurrentHashMap<>();
    private static final Map<World, Set<Vector3i>> PENDING_WAVE_ADOPTION = new ConcurrentHashMap<>();

    private ConnectablePropagationScheduler() {
    }

    public static void onConnectablePlaced(World world, Vector3i target) {
        PENDING_PLACED.computeIfAbsent(world, ignored -> ConcurrentHashMap.newKeySet()).add(target);
        enqueueRecompute(world, target);
    }

    public static void onConnectableBroken(World world, Vector3i target) {
        PENDING_BROKEN.computeIfAbsent(world, ignored -> ConcurrentHashMap.newKeySet()).add(target);
        enqueueRecompute(world, target);
    }

    public static void onConnectableConnectionsChanged(World world, Vector3i target, Set<Vector3i> previousNeighbors, Set<Vector3i> nextNeighbors) {
        PENDING_BROKEN.computeIfAbsent(world, ignored -> ConcurrentHashMap.newKeySet()).add(target);
        PENDING_BROKEN.computeIfAbsent(world, ignored -> ConcurrentHashMap.newKeySet()).addAll(previousNeighbors);
        PENDING_PLACED.computeIfAbsent(world, ignored -> ConcurrentHashMap.newKeySet()).add(target);
        PENDING_PLACED.computeIfAbsent(world, ignored -> ConcurrentHashMap.newKeySet()).addAll(nextNeighbors);

        enqueueRecompute(world, target);
        for (Vector3i neighbor : previousNeighbors) {
            enqueueRecompute(world, neighbor);
        }
        for (Vector3i neighbor : nextNeighbors) {
            enqueueRecompute(world, neighbor);
        }
    }

    public static void tickPropagation() {
        Map<World, Set<Vector3i>> pendingByWorld = drainPendingRecomputes();
        Map<World, Set<Vector3i>> pendingWaveAdoptionsByWorld = drainPendingWaveAdoptions();
        Set<World> worlds = new HashSet<>();
        worlds.addAll(pendingByWorld.keySet());
        worlds.addAll(pendingWaveAdoptionsByWorld.keySet());
        for (World world : worlds) {
            tickWorld(
                    world,
                    pendingByWorld.getOrDefault(world, Set.of()),
                    drainPendingTargets(PENDING_PLACED, world),
                    drainPendingTargets(PENDING_BROKEN, world),
                    pendingWaveAdoptionsByWorld.getOrDefault(world, Set.of())
            );
        }
    }

    public static boolean isNotGravityPowder(BlockType blockType) {
        return blockType == null || !ConnectableRegistry.isGravityPowderId(blockType.getId());
    }

    public static boolean isNotInverter(BlockType blockType) {
        return blockType == null || !ConnectableRegistry.isInverterId(blockType.getId());
    }

    private static void tickWorld(
            World world,
            Set<Vector3i> dirtyPositions,
            Set<Vector3i> placedTargets,
            Set<Vector3i> brokenTargets,
            Set<Vector3i> waveAdoptionTargets
    ) {
        Set<Vector3i> affectedPositions = affectedConnectablePositions(world, dirtyPositions);
        LinkedHashSet<Vector3i> changedEffectiveNodes = new LinkedHashSet<>();
        if (affectedPositions.isEmpty()) {
            changedEffectiveNodes.addAll(processWaveAdoptions(world, waveAdoptionTargets));
            refreshVisuals(world, changedEffectiveNodes);
            ConnectableNetworkUpdateService.updateSiphonsNear(world, merge(dirtyPositions, changedEffectiveNodes));
            ConnectableNetworkIndexer.rebuildWorld(world);
            return;
        }

        Map<Vector3i, String> previousInstantStates = snapshotInstantStates(world, retainSignalRuntimeNodes(world, affectedPositions));
        ConnectableSignalRecalculator.recompute(world, affectedPositions);
        Set<Vector3i> changedInstantNodes = changedInstantStateNodes(world, previousInstantStates);
        clearPendingWaveAdoptions(world, changedInstantNodes);
        waveAdoptionTargets = without(waveAdoptionTargets, changedInstantNodes);

        changedEffectiveNodes.addAll(syncSourceTargets(world, placedTargets));
        changedEffectiveNodes.addAll(syncSourceTargets(world, brokenTargets));
        changedEffectiveNodes.addAll(syncPlacedTargets(world, placedTargets));
        changedEffectiveNodes.addAll(syncNeighborsOfBrokenTargets(world, brokenTargets));
        changedEffectiveNodes.addAll(processWaveAdoptions(world, waveAdoptionTargets));

        refreshVisuals(world, merge(affectedPositions, changedEffectiveNodes));
        ConnectableNetworkUpdateService.updateSiphonsNear(world, merge(merge(dirtyPositions, affectedPositions), changedEffectiveNodes));
        ConnectableNetworkIndexer.rebuildWorld(world);
    }

    private static Set<Vector3i> merge(Set<Vector3i> first, Set<Vector3i> second) {
        LinkedHashSet<Vector3i> result = new LinkedHashSet<>();
        result.addAll(first);
        result.addAll(second);
        return Set.copyOf(result);
    }

    private static void refreshVisuals(World world, Set<Vector3i> positions) {
        for (Vector3i position : positions) {
            refreshVisualAt(world, position);
        }
    }

    private static void refreshVisualAt(World world, Vector3i position) {
        BlockType blockType = world.getBlockType(position.x(), position.y(), position.z());
        if (blockType == null) {
            return;
        }

        if (ConnectableRegistry.isCasedGravityPowderId(blockType.getId())) {
            CasedGravityPowderBlockRefresher.refreshAt(world, position);
            return;
        }

        if (ConnectableRegistry.isGravityPowderId(blockType.getId())) {
            GravityPowderBlockRefresher.refreshAt(world, position);
            return;
        }

        if (ConnectableRegistry.isInverterId(blockType.getId())) {
            InverterBlockRefresher.refreshAt(world, position);
        }
    }

    private static void enqueueRecompute(World world, Vector3i target) {
        PENDING_RECOMPUTE.computeIfAbsent(world, ignored -> ConcurrentHashMap.newKeySet())
                .addAll(ConnectableNeighborResolver.positionsAround(target));
    }

    private static void enqueueWaveAdoption(World world, Vector3i target) {
        PENDING_WAVE_ADOPTION.computeIfAbsent(world, ignored -> ConcurrentHashMap.newKeySet()).add(target);
    }

    private static void clearPendingWaveAdoptions(World world, Set<Vector3i> targets) {
        Set<Vector3i> pending = PENDING_WAVE_ADOPTION.get(world);
        if (pending == null || pending.isEmpty() || targets.isEmpty()) {
            return;
        }
        pending.removeAll(targets);
        if (pending.isEmpty()) {
            PENDING_WAVE_ADOPTION.remove(world, pending);
        }
    }

    private static Set<Vector3i> without(Set<Vector3i> source, Set<Vector3i> excluded) {
        if (source.isEmpty() || excluded.isEmpty()) {
            return source;
        }
        LinkedHashSet<Vector3i> result = new LinkedHashSet<>(source);
        result.removeAll(excluded);
        return Set.copyOf(result);
    }

    private static Set<Vector3i> retainSignalRuntimeNodes(World world, Set<Vector3i> positions) {
        LinkedHashSet<Vector3i> result = new LinkedHashSet<>();
        for (Vector3i position : positions) {
            ConnectableNodeProvider.nodeAt(world, position)
                    .filter(ConnectableNode::isSignalRuntimeNode)
                    .ifPresent(ignored -> result.add(position));
        }
        return Set.copyOf(result);
    }

    private static Map<Vector3i, String> snapshotInstantStates(World world, Set<Vector3i> positions) {
        Map<Vector3i, String> result = new HashMap<>();
        for (Vector3i position : positions) {
            result.put(position, ConnectableRuntimeAccessor.instantState(world, position));
        }
        return Map.copyOf(result);
    }

    private static Set<Vector3i> changedInstantStateNodes(World world, Map<Vector3i, String> previousInstantStates) {
        LinkedHashSet<Vector3i> result = new LinkedHashSet<>();
        for (Map.Entry<Vector3i, String> entry : previousInstantStates.entrySet()) {
            if (!ConnectableRuntimeAccessor.instantState(world, entry.getKey()).equals(entry.getValue())) {
                result.add(entry.getKey());
            }
        }
        return Set.copyOf(result);
    }

    private static Map<World, Set<Vector3i>> drainPendingRecomputes() {
        Map<World, Set<Vector3i>> pendingByWorld = new HashMap<>();
        for (Map.Entry<World, Set<Vector3i>> entry : PENDING_RECOMPUTE.entrySet()) {
            Set<Vector3i> positions = new HashSet<>(entry.getValue());
            if (positions.isEmpty()) {
                continue;
            }

            entry.getValue().removeAll(positions);
            pendingByWorld.put(entry.getKey(), positions);
            if (entry.getValue().isEmpty()) {
                PENDING_RECOMPUTE.remove(entry.getKey(), entry.getValue());
            }
        }
        return Map.copyOf(pendingByWorld);
    }

    private static Map<World, Set<Vector3i>> drainPendingWaveAdoptions() {
        Map<World, Set<Vector3i>> pendingByWorld = new HashMap<>();
        for (Map.Entry<World, Set<Vector3i>> entry : PENDING_WAVE_ADOPTION.entrySet()) {
            Set<Vector3i> positions = new HashSet<>(entry.getValue());
            if (positions.isEmpty()) {
                continue;
            }

            entry.getValue().removeAll(positions);
            pendingByWorld.put(entry.getKey(), positions);
            if (entry.getValue().isEmpty()) {
                PENDING_WAVE_ADOPTION.remove(entry.getKey(), entry.getValue());
            }
        }
        return Map.copyOf(pendingByWorld);
    }

    private static Set<Vector3i> drainPendingTargets(Map<World, Set<Vector3i>> pendingByWorld, World world) {
        Set<Vector3i> pending = pendingByWorld.remove(world);
        if (pending == null || pending.isEmpty()) {
            return Set.of();
        }
        return Set.copyOf(pending);
    }

    private static Set<Vector3i> affectedConnectablePositions(World world, Set<Vector3i> dirtyPositions) {
        Set<Vector3i> connectables = ConnectableNodeProvider.connectableNodePositionsForWorld(world);
        if (connectables.isEmpty()) {
            return Set.of();
        }

        LinkedHashSet<Vector3i> affected = new LinkedHashSet<>();
        LinkedHashSet<Vector3i> queue = new LinkedHashSet<>();
        for (Vector3i dirtyPosition : dirtyPositions) {
            if (isKnownConnectable(world, dirtyPosition) && connectables.contains(dirtyPosition) && affected.add(dirtyPosition)) {
                queue.add(dirtyPosition);
            }
            for (Vector3i candidate : ConnectableNeighborResolver.positionsAround(dirtyPosition)) {
                if (candidate.equals(dirtyPosition) || !connectables.contains(candidate) || !isKnownConnectable(world, candidate)) {
                    continue;
                }
                if (isConnectableAffectedByDirtyPosition(world, dirtyPosition, candidate, connectables)
                        && affected.add(candidate)) {
                    queue.add(candidate);
                }
            }
        }

        while (!queue.isEmpty()) {
            Vector3i current = queue.removeFirst();
            for (Vector3i neighbor : ConnectableNeighborResolver.mutuallyConnectedNeighbors(world, current)) {
                if (!connectables.contains(neighbor) || !isKnownConnectable(world, neighbor)) {
                    continue;
                }
                if (affected.add(neighbor)) {
                    queue.add(neighbor);
                }
            }
        }

        return Set.copyOf(affected);
    }

    private static boolean isConnectableAffectedByDirtyPosition(
            World world,
            Vector3i dirtyPosition,
            Vector3i candidate,
            Set<Vector3i> connectables
    ) {
        if (isKnownConnectable(world, dirtyPosition) && connectables.contains(dirtyPosition)) {
            return ConnectableNeighborResolver.areMutuallyConnected(world, dirtyPosition, candidate);
        }

        BlockType dirtyType = world.getBlockType(dirtyPosition.x(), dirtyPosition.y(), dirtyPosition.z());
        if (dirtyType != null && ConnectableBlockRoles.isSource(dirtyType.getId())) {
            return ConnectableNeighborResolver.hasConnectableSideFacing(world, dirtyPosition, candidate)
                    && ConnectableNeighborResolver.hasConnectableSideFacing(world, candidate, dirtyPosition);
        }

        return ConnectableNeighborResolver.hasConnectableSideFacing(world, candidate, dirtyPosition);
    }

    private static boolean isKnownConnectable(World world, Vector3i position) {
        return ConnectableNodeProvider.nodeAt(world, position).isPresent();
    }

    static Set<Vector3i> affectedConnectablePositions(Set<Vector3i> dirtyPositions, Set<Vector3i> signalNodes) {
        if (signalNodes.isEmpty()) {
            return Set.of();
        }

        LinkedHashSet<Vector3i> affected = new LinkedHashSet<>();
        LinkedHashSet<Vector3i> queue = new LinkedHashSet<>();
        for (Vector3i dirtyPosition : dirtyPositions) {
            for (Vector3i candidate : ConnectableNeighborResolver.positionsAround(dirtyPosition)) {
                if (signalNodes.contains(candidate) && affected.add(candidate)) {
                    queue.add(candidate);
                }
            }
        }

        while (!queue.isEmpty()) {
            Vector3i current = queue.removeFirst();
            for (Vector3i neighbor : ConnectableNeighborResolver.positionsAround(current)) {
                if (neighbor.equals(current) || !signalNodes.contains(neighbor)) {
                    continue;
                }
                if (affected.add(neighbor)) {
                    queue.add(neighbor);
                }
            }
        }

        return Set.copyOf(affected);
    }

    static Set<Vector3i> mismatchedWaveNeighbors(
            Vector3i node,
            Iterable<Vector3i> neighbors,
            java.util.function.Function<Vector3i, GravityPowderSpecialStateStore.GravityPowderBlockData> dataProvider
    ) {
        LinkedHashSet<Vector3i> mismatched = new LinkedHashSet<>();
        for (Vector3i neighbor : neighbors) {
            if (neighbor.equals(node)) {
                continue;
            }
            GravityPowderSpecialStateStore.GravityPowderBlockData neighborData = dataProvider.apply(neighbor);
            if (neighborData != null && neighborData.hasWaveMismatch()) {
                mismatched.add(neighbor);
            }
        }
        return Set.copyOf(mismatched);
    }

    private static Set<Vector3i> syncSourceTargets(World world, Set<Vector3i> sourceTargets) {
        LinkedHashSet<Vector3i> changedEffectiveNodes = new LinkedHashSet<>();
        for (Vector3i sourceTarget : sourceTargets) {
            BlockType blockType = world.getBlockType(sourceTarget.x, sourceTarget.y, sourceTarget.z);
            if (blockType == null || !ConnectableBlockRoles.isSource(blockType.getId())) {
                continue;
            }
            for (Vector3i neighbor : ConnectableNeighborResolver.positionsAround(sourceTarget)) {
                ConnectableNode target = ConnectableNodeProvider.nodeAt(world, neighbor).orElse(null);
                if (target == null || !target.isSignalRuntimeNode()) {
                    continue;
                }
                if (!ConnectableNeighborResolver.isSourceNeighborOf(world, sourceTarget, neighbor)
                        || !canReceiveSignalFromSource(target, sourceTarget)) {
                    continue;
                }
                if (ConnectableRuntimeAccessor.isDirty(world, neighbor) && adoptInstantStateAndScheduleNeighbors(world, neighbor)) {
                    changedEffectiveNodes.add(neighbor);
                }
            }
        }
        return Set.copyOf(changedEffectiveNodes);
    }

    private static boolean canReceiveSignalFromSource(ConnectableNode target, Vector3i sourcePosition) {
        WorldSide sourceToTarget = ConnectableNeighborResolver.worldSideFromSourceToTarget(sourcePosition, target.position());
        if (sourceToTarget == null) {
            return false;
        }
        int targetLocalSide = ConnectableNeighborResolver.localSideForWorldSide(target.rotation(), sourceToTarget.opposite());
        return target.canReceiveSignalFrom(targetLocalSide);
    }

    private static Set<Vector3i> syncPlacedTargets(World world, Set<Vector3i> placedTargets) {
        LinkedHashSet<Vector3i> changedEffectiveNodes = new LinkedHashSet<>();
        for (Vector3i target : placedTargets) {
            ConnectableNode node = ConnectableNodeProvider.nodeAt(world, target).orElse(null);
            if (node == null || !node.isSignalRuntimeNode()) {
                continue;
            }
            if (adoptInstantStateAndScheduleNeighbors(world, target)) {
                changedEffectiveNodes.add(target);
            }
        }
        return Set.copyOf(changedEffectiveNodes);
    }

    private static Set<Vector3i> syncNeighborsOfBrokenTargets(World world, Set<Vector3i> brokenTargets) {
        LinkedHashSet<Vector3i> changedEffectiveNodes = new LinkedHashSet<>();
        for (Vector3i target : brokenTargets) {
            for (Vector3i neighbor : ConnectableNeighborResolver.positionsAround(target)) {
                if (neighbor.equals(target)) {
                    continue;
                }
                ConnectableNode node = ConnectableNodeProvider.nodeAt(world, neighbor).orElse(null);
                if (node == null || !node.isSignalRuntimeNode()) {
                    continue;
                }
                if (!ConnectableNeighborResolver.hasConnectableSideFacing(world, neighbor, target)) {
                    continue;
                }
                if (adoptInstantStateAndScheduleNeighbors(world, neighbor)) {
                    changedEffectiveNodes.add(neighbor);
                }
            }
        }
        return Set.copyOf(changedEffectiveNodes);
    }

    private static Set<Vector3i> processWaveAdoptions(World world, Set<Vector3i> waveAdoptionTargets) {
        LinkedHashSet<Vector3i> changedEffectiveNodes = new LinkedHashSet<>();
        for (Vector3i target : waveAdoptionTargets) {
            if (adoptInstantStateAndScheduleNeighbors(world, target)) {
                changedEffectiveNodes.add(target);
            }
        }
        return Set.copyOf(changedEffectiveNodes);
    }

    private static boolean adoptInstantStateAndScheduleNeighbors(World world, Vector3i target) {
        if (!ConnectableRuntimeAccessor.isDirty(world, target)) {
            return false;
        }

        String previousEffectiveState = ConnectableRuntimeAccessor.effectiveState(world, target);
        ConnectableRuntimeAccessor.adoptInstantState(world, target);

        for (Vector3i neighbor : signalOutputNeighbors(world, target)) {
            boolean neighborDirty = ConnectableRuntimeAccessor.isDirty(world, neighbor);
            if (neighborDirty) {
                enqueueWaveAdoption(world, neighbor);
            }
        }
        return !ConnectableRuntimeAccessor.effectiveState(world, target).equals(previousEffectiveState);
    }

    private static Set<Vector3i> signalOutputNeighbors(World world, Vector3i sourcePosition) {
        ConnectableNode source = ConnectableNodeProvider.nodeAt(world, sourcePosition).orElse(null);
        if (source == null) {
            return Set.of();
        }

        LinkedHashSet<Vector3i> result = new LinkedHashSet<>();
        for (int localSide : LOCAL_SIDES) {
            if (!source.canOutputSignalTo(localSide)) {
                continue;
            }
            Vector3i neighborPosition = ConnectableNeighborResolver.adjacentPositionForLocalSide(world, sourcePosition, localSide);
            ConnectableNode neighbor = ConnectableNodeProvider.nodeAt(world, neighborPosition).orElse(null);
            if (neighbor != null && isSignalOutputNeighbor(source, neighbor)) {
                result.add(neighbor.position());
            }
        }
        return Set.copyOf(result);
    }

    static boolean isSignalOutputNeighbor(ConnectableNode source, ConnectableNode neighbor) {
        WorldSide sourceToNeighbor = ConnectableNeighborResolver.worldSideFromSourceToTarget(
                source.position(),
                neighbor.position()
        );
        if (sourceToNeighbor == null) {
            return false;
        }

        int sourceLocalSide = ConnectableNeighborResolver.localSideForWorldSide(source.rotation(), sourceToNeighbor);
        int neighborLocalSide = ConnectableNeighborResolver.localSideForWorldSide(neighbor.rotation(), sourceToNeighbor.opposite());
        return source.canOutputSignalTo(sourceLocalSide) && neighbor.canReceiveSignalFrom(neighborLocalSide);
    }
}
