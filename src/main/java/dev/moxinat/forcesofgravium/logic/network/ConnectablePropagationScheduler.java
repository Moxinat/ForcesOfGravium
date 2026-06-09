package dev.moxinat.forcesofgravium.logic.network;

import org.joml.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import dev.moxinat.forcesofgravium.connectable.ConnectableRuntimeAccessor;
import dev.moxinat.forcesofgravium.connectable.ConnectableRuntimeData;
import dev.moxinat.forcesofgravium.data.GravityPowderBlockDataStore;
import dev.moxinat.forcesofgravium.data.InverterDataStore;
import dev.moxinat.forcesofgravium.data.InverterDataStore.InverterData;
import dev.moxinat.forcesofgravium.logic.gravity.CasedGravityPowderBlockRefresher;
import dev.moxinat.forcesofgravium.logic.gravity.GravityPowderBlockRefresher;
import dev.moxinat.forcesofgravium.logic.inverter.InverterBlockRefresher;
import dev.moxinat.forcesofgravium.logic.inverter.InverterStateCalculator;
import dev.moxinat.forcesofgravium.registry.ConnectableBlockRoles;
import dev.moxinat.forcesofgravium.registry.ConnectableRegistry;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public final class ConnectablePropagationScheduler {

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
        LinkedHashSet<Vector3i> visibleChangedCables = new LinkedHashSet<>();
        if (affectedPositions.isEmpty()) {
            visibleChangedCables.addAll(processWaveAdoptions(world, waveAdoptionTargets));
            for (Vector3i cable : visibleChangedCables) {
                refreshCableAt(world, cable);
            }
            visibleChangedCables.addAll(updateInvertersWithBackIn(world, visibleChangedCables));
            updateInvertersWithSideIn(world, visibleChangedCables);
            visibleChangedCables.addAll(syncDirtyInverterFronts(world, InverterDataStore.snapshotForWorld(world).keySet()));
            ConnectableNetworkUpdateService.updateSiphonsNear(world, merge(dirtyPositions, visibleChangedCables));
            ConnectableNetworkIndexer.rebuildWorld(world);
            return;
        }

        Map<Vector3i, String> previousInstantStates = snapshotInstantStates(world, retainKnownCables(world, affectedPositions));

        ConnectableSignalRecalculator.recompute(world, affectedPositions);
        Set<Vector3i> changedInstantCables = changedInstantStateCables(world, previousInstantStates);
        clearPendingWaveAdoptions(world, changedInstantCables);
        waveAdoptionTargets = without(waveAdoptionTargets, changedInstantCables);
        Set<Vector3i> cables = GravityPowderBlockDataStore.snapshotForWorld(world).keySet();
        Set<Vector3i> inverters = InverterDataStore.snapshotForWorld(world).keySet();
        visibleChangedCables.addAll(syncSourceTargets(world, placedTargets, cables));
        visibleChangedCables.addAll(syncSourceTargets(world, brokenTargets, cables));
        visibleChangedCables.addAll(syncPlacedTargets(world, placedTargets, cables, inverters));
        visibleChangedCables.addAll(syncNeighborsOfBrokenTargets(world, brokenTargets, cables, inverters));
        visibleChangedCables.addAll(processWaveAdoptions(world, waveAdoptionTargets));
        LinkedHashSet<Vector3i> refreshCables = new LinkedHashSet<>(affectedPositions);
        refreshCables.addAll(visibleChangedCables);
        for (Vector3i cable : refreshCables) {
            if (!cables.contains(cable)) {
                continue;
            }
            refreshCableAt(world, cable);
        }
        for (Vector3i inverter : affectedPositions) {
            if (!inverters.contains(inverter)) {
                continue;
            }
            InverterBlockRefresher.refreshAt(world, inverter);
        }
        updateInvertersWithSideIn(world, visibleChangedCables);
        visibleChangedCables.addAll(syncDirtyInverterFronts(world, inverters));
        visibleChangedCables.addAll(updateInvertersWithBackIn(world, visibleChangedCables));
        updateInvertersWithSideIn(world, visibleChangedCables);
        visibleChangedCables.addAll(syncDirtyInverterFronts(world, inverters));
        ConnectableNetworkUpdateService.updateSiphonsNear(world, merge(merge(dirtyPositions, affectedPositions), visibleChangedCables));
        ConnectableNetworkIndexer.rebuildWorld(world);
    }

    private static Set<Vector3i> updateInvertersWithBackIn(World world, Set<Vector3i> cablePositions) {
        LinkedHashSet<Vector3i> visibleChangedCables = new LinkedHashSet<>();
        Set<Vector3i> inverters = InverterDataStore.snapshotForWorld(world).keySet();
        for (Vector3i inverter : inverters) {
            Vector3i back = ConnectableNeighborResolver.adjacentPositionForLocalSide(world, inverter, ConnectableRegistry.SIDE_BACK);
            if (!cablePositions.contains(back)) {
                continue;
            }

            InverterData previous = InverterDataStore.get(world, inverter);
            boolean invertEnabled = previous == null || previous.invertEnabled();
            String lastToggleInputMode = previous == null
                    ? GravityPowderBlockDataStore.STATE_OFF
                    : previous.lastToggleInputMode();
            String inputMode = InverterStateCalculator.computeInputMode(world, inverter);
            String outputMode = invertEnabled ? InverterStateCalculator.invertMode(inputMode) : inputMode;
            String previousMode = previous == null ? GravityPowderBlockDataStore.STATE_OFF : previous.currentMode();

            InverterDataStore.setState(world, inverter, outputMode, outputMode, invertEnabled, lastToggleInputMode);
            InverterBlockRefresher.refreshAt(world, inverter);
            if (outputMode.equals(previousMode)) {
                continue;
            }

            ConnectableRuntimeAccessor.setDirty(world, inverter, true);
        }
        return Set.copyOf(visibleChangedCables);
    }

    private static void updateInvertersWithSideIn(World world, Set<Vector3i> cablePositions) {
        Set<Vector3i> inverters = InverterDataStore.snapshotForWorld(world).keySet();
        Set<Vector3i> sideInputInverters = sideInputInvertersForChangedCables(
                cablePositions,
                inverters,
                inverter -> ConnectableNeighborResolver.adjacentPositionForLocalSide(world, inverter, ConnectableRegistry.SIDE_BACK),
                inverter -> ConnectableNeighborResolver.adjacentPositionForLocalSide(world, inverter, ConnectableRegistry.SIDE_FRONT)
        );
        if (sideInputInverters.isEmpty()) {
            return;
        }

        Set<Vector3i> affectedPositions = affectedConnectablePositions(world, sideInputInverters);
        Map<Vector3i, String> previousInstantStates = snapshotInstantStates(world, retainKnownCables(world, affectedPositions));
        ConnectableSignalRecalculator.recompute(world, affectedPositions);
        clearPendingWaveAdoptions(world, changedInstantStateCables(world, previousInstantStates));
        for (Vector3i inverter : sideInputInverters) {
            InverterBlockRefresher.refreshAt(world, inverter);
        }
    }

    private static Set<Vector3i> syncDirtyInverterFronts(World world, Set<Vector3i> inverters) {
        LinkedHashSet<Vector3i> visibleChangedCables = new LinkedHashSet<>();
        for (Vector3i inverter : inverters) {
            if (!ConnectableRuntimeAccessor.getRuntimeData(world, inverter)
                    .map(ConnectableRuntimeData::dirty)
                    .orElse(false)) {
                continue;
            }

            Vector3i front = ConnectableNeighborResolver.adjacentPositionForLocalSide(world, inverter, ConnectableRegistry.SIDE_FRONT);
            Set<Vector3i> affectedPositions = affectedConnectablePositions(world, Set.of(inverter));
            Map<Vector3i, String> previousInstantStates = snapshotInstantStates(world, retainKnownCables(world, affectedPositions));
            ConnectableSignalRecalculator.recompute(world, affectedPositions);
            clearPendingWaveAdoptions(world, changedInstantStateCables(world, previousInstantStates));
            if (ConnectableNeighborResolver.areMutuallyConnected(world, inverter, front)
                    && adoptInstantStateAndScheduleNeighbors(world, front)) {
                refreshCableAt(world, front);
                visibleChangedCables.add(front);
            }
            ConnectableRuntimeAccessor.setDirty(world, inverter, false);
        }
        return Set.copyOf(visibleChangedCables);
    }

    private static Set<Vector3i> merge(Set<Vector3i> first, Set<Vector3i> second) {
        LinkedHashSet<Vector3i> result = new LinkedHashSet<>();
        result.addAll(first);
        result.addAll(second);
        return Set.copyOf(result);
    }

    private static void refreshCableAt(World world, Vector3i cable) {
        BlockType blockType = world.getBlockType(cable.x(), cable.y(), cable.z());
        if (blockType == null) {
            return;
        }

        if (ConnectableRegistry.isCasedGravityPowderId(blockType.getId())) {
            CasedGravityPowderBlockRefresher.refreshAt(world, cable);
            return;
        }

        if (ConnectableRegistry.isGravityPowderId(blockType.getId())) {
            GravityPowderBlockRefresher.refreshAt(world, cable);
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

    private static Set<Vector3i> retainKnownCables(World world, Set<Vector3i> positions) {
        Set<Vector3i> cables = GravityPowderBlockDataStore.snapshotForWorld(world).keySet();
        LinkedHashSet<Vector3i> result = new LinkedHashSet<>(positions);
        result.retainAll(cables);
        return Set.copyOf(result);
    }

    private static Map<Vector3i, String> snapshotInstantStates(World world, Set<Vector3i> cables) {
        Map<Vector3i, String> result = new HashMap<>();
        for (Vector3i cable : cables) {
            ConnectableRuntimeAccessor.getRuntimeData(world, cable)
                    .ifPresent(data -> result.put(cable, data.instantState()));
        }
        return Map.copyOf(result);
    }

    private static Set<Vector3i> changedInstantStateCables(World world, Map<Vector3i, String> previousInstantStates) {
        LinkedHashSet<Vector3i> result = new LinkedHashSet<>();
        for (Map.Entry<Vector3i, String> entry : previousInstantStates.entrySet()) {
            java.util.Optional<ConnectableRuntimeData> data = ConnectableRuntimeAccessor.getRuntimeData(world, entry.getKey());
            if (data.isPresent() && !data.get().instantState().equals(entry.getValue())) {
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
        Set<Vector3i> cables = GravityPowderBlockDataStore.snapshotForWorld(world).keySet();
        Set<Vector3i> inverters = InverterDataStore.snapshotForWorld(world).keySet();
        Set<Vector3i> connectables = new HashSet<>();
        connectables.addAll(cables);
        connectables.addAll(inverters);
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
        BlockType blockType = world.getBlockType(position.x(), position.y(), position.z());
        return blockType != null && !ConnectableRegistry.isNotConnectable(blockType.getId());
    }

    static Set<Vector3i> affectedConnectablePositions(Set<Vector3i> dirtyPositions, Set<Vector3i> cables, Set<Vector3i> inverters) {
        Set<Vector3i> connectables = new HashSet<>();
        connectables.addAll(cables);
        connectables.addAll(inverters);
        if (connectables.isEmpty()) {
            return Set.of();
        }

        LinkedHashSet<Vector3i> affected = new LinkedHashSet<>();
        LinkedHashSet<Vector3i> queue = new LinkedHashSet<>();
        for (Vector3i dirtyPosition : dirtyPositions) {
            for (Vector3i candidate : ConnectableNeighborResolver.positionsAround(dirtyPosition)) {
                if (connectables.contains(candidate) && affected.add(candidate)) {
                    queue.add(candidate);
                }
            }
        }

        while (!queue.isEmpty()) {
            Vector3i current = queue.removeFirst();
            for (Vector3i neighbor : ConnectableNeighborResolver.positionsAround(current)) {
                if (neighbor.equals(current) || !connectables.contains(neighbor)) {
                    continue;
                }
                if (affected.add(neighbor)) {
                    queue.add(neighbor);
                }
            }
        }

        return Set.copyOf(affected);
    }

    static Set<Vector3i> mismatchedCableNeighbors(
            Vector3i cable,
            Iterable<Vector3i> neighbors,
            java.util.function.Function<Vector3i, GravityPowderBlockDataStore.GravityPowderBlockData> dataProvider
    ) {
        LinkedHashSet<Vector3i> mismatched = new LinkedHashSet<>();
        for (Vector3i neighbor : neighbors) {
            if (neighbor.equals(cable)) {
                continue;
            }
            GravityPowderBlockDataStore.GravityPowderBlockData neighborData = dataProvider.apply(neighbor);
            if (neighborData != null && neighborData.hasWaveMismatch()) {
                mismatched.add(neighbor);
            }
        }
        return Set.copyOf(mismatched);
    }

    static Set<Vector3i> sideInputInvertersForChangedCables(
            Set<Vector3i> cablePositions,
            Set<Vector3i> inverters,
            Function<Vector3i, Vector3i> backResolver,
            Function<Vector3i, Vector3i> frontResolver
    ) {
        if (cablePositions.isEmpty() || inverters.isEmpty()) {
            return Set.of();
        }

        LinkedHashSet<Vector3i> result = new LinkedHashSet<>();
        for (Vector3i inverter : inverters) {
            Vector3i back = backResolver.apply(inverter);
            Vector3i front = frontResolver.apply(inverter);
            for (Vector3i cable : cablePositions) {
                if (!ConnectableNeighborResolver.positionsAround(inverter).contains(cable)) {
                    continue;
                }
                if (!cable.equals(back) && !cable.equals(front)) {
                    result.add(inverter);
                    break;
                }
            }
        }
        return Set.copyOf(result);
    }

    private static Set<Vector3i> syncSourceTargets(World world, Set<Vector3i> sourceTargets, Set<Vector3i> cables) {
        LinkedHashSet<Vector3i> visibleChangedCables = new LinkedHashSet<>();
        for (Vector3i sourceTarget : sourceTargets) {
            BlockType blockType = world.getBlockType(sourceTarget.x, sourceTarget.y, sourceTarget.z);
            if (blockType == null || !ConnectableBlockRoles.isSource(blockType.getId())) {
                continue;
            }
            for (Vector3i neighbor : ConnectableNeighborResolver.positionsAround(sourceTarget)) {
                if (!cables.contains(neighbor)) {
                    continue;
                }
                if (!ConnectableNeighborResolver.isSourceNeighborOf(world, sourceTarget, neighbor)
                        || !ConnectableNeighborResolver.hasConnectableSideFacing(world, neighbor, sourceTarget)) {
                    continue;
                }
                boolean dirty = ConnectableRuntimeAccessor.getRuntimeData(world, neighbor)
                        .map(ConnectableRuntimeData::dirty)
                        .orElse(false);
                if (dirty && adoptInstantStateAndScheduleNeighbors(world, neighbor)) {
                    visibleChangedCables.add(neighbor);
                }
            }
        }
        return Set.copyOf(visibleChangedCables);
    }

    private static Set<Vector3i> syncPlacedTargets(World world, Set<Vector3i> placedTargets, Set<Vector3i> cables, Set<Vector3i> inverters) {
        LinkedHashSet<Vector3i> visibleChangedCables = new LinkedHashSet<>();
        for (Vector3i target : placedTargets) {
            if (cables.contains(target)) {
                if (adoptInstantStateAndScheduleNeighbors(world, target)) {
                    visibleChangedCables.add(target);
                }
            }
            if (inverters.contains(target)) {
                InverterData data = InverterDataStore.get(world, target);
                if (shouldAdoptPlacedInverter(data)) {
                    ConnectableRuntimeAccessor.adoptInstantState(world, target);
                }
            }
        }
        return Set.copyOf(visibleChangedCables);
    }

    static boolean shouldAdoptPlacedInverter(InverterData data) {
        return data == null || !data.dirty();
    }

    private static Set<Vector3i> syncNeighborsOfBrokenTargets(World world, Set<Vector3i> brokenTargets, Set<Vector3i> cables, Set<Vector3i> inverters) {
        LinkedHashSet<Vector3i> visibleChangedCables = new LinkedHashSet<>();
        for (Vector3i target : brokenTargets) {
            for (Vector3i neighbor : ConnectableNeighborResolver.positionsAround(target)) {
                if (neighbor.equals(target)) {
                    continue;
                }
                if (cables.contains(neighbor)) {
                    if (!ConnectableNeighborResolver.hasConnectableSideFacing(world, neighbor, target)) {
                        continue;
                    }
                    if (adoptInstantStateAndScheduleNeighbors(world, neighbor)) {
                        visibleChangedCables.add(neighbor);
                    }
                }
                if (inverters.contains(neighbor)) {
                    InverterData data = InverterDataStore.get(world, neighbor);
                    if (shouldAdoptBrokenNeighborInverter(data)) {
                        ConnectableRuntimeAccessor.adoptInstantState(world, neighbor);
                    }
                }
            }
        }
        return Set.copyOf(visibleChangedCables);
    }

    static boolean shouldAdoptBrokenNeighborInverter(InverterData data) {
        return data == null || !data.dirty();
    }

    private static Set<Vector3i> processWaveAdoptions(World world, Set<Vector3i> waveAdoptionTargets) {
        LinkedHashSet<Vector3i> visibleChangedCables = new LinkedHashSet<>();
        for (Vector3i target : waveAdoptionTargets) {
            if (adoptInstantStateAndScheduleNeighbors(world, target)) {
                visibleChangedCables.add(target);
            }
        }
        return Set.copyOf(visibleChangedCables);
    }

    private static boolean adoptInstantStateAndScheduleNeighbors(World world, Vector3i target) {
        java.util.Optional<ConnectableRuntimeData> previous = ConnectableRuntimeAccessor.getRuntimeData(world, target);
        if (previous.isEmpty() || !previous.get().dirty()) {
            return false;
        }

        String previousEffectiveState = previous.get().effectiveState();
        ConnectableRuntimeAccessor.adoptInstantState(world, target);
        java.util.Optional<ConnectableRuntimeData> updated = ConnectableRuntimeAccessor.getRuntimeData(world, target);
        if (updated.isEmpty()) {
            return false;
        }

        for (Vector3i neighbor : ConnectableNeighborResolver.mutuallyConnectedNeighbors(world, target)) {
            boolean neighborDirty = ConnectableRuntimeAccessor.getRuntimeData(world, neighbor)
                    .map(ConnectableRuntimeData::dirty)
                    .orElse(false);
            if (neighborDirty) {
                enqueueWaveAdoption(world, neighbor);
            }
        }
        return !updated.get().effectiveState().equals(previousEffectiveState);
    }
}
