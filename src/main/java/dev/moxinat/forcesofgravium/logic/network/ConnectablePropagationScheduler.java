package dev.moxinat.forcesofgravium.logic.network;

import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import dev.moxinat.forcesofgravium.data.GravityPowderBlockDataStore;
import dev.moxinat.forcesofgravium.data.InverterDataStore;
import dev.moxinat.forcesofgravium.logic.gravity.GravityPowderBlockRefresher;
import dev.moxinat.forcesofgravium.logic.inverter.InverterBlockRefresher;
import dev.moxinat.forcesofgravium.registry.ConnectableRegistry;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

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

    public static void onCableInstantStateChanged(World world, Vector3i cable) {
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
                GravityPowderBlockRefresher.refreshAt(world, cable);
            }
            ConnectableNetworkUpdateService.updateSiphonsNear(world, merge(dirtyPositions, visibleChangedCables));
            return;
        }

        ConnectableSignalRecalculator.recompute(world, affectedPositions);
        Set<Vector3i> cables = GravityPowderBlockDataStore.snapshotForWorld(world).keySet();
        Set<Vector3i> inverters = InverterDataStore.snapshotForWorld(world).keySet();
        visibleChangedCables.addAll(syncPlacedTargets(world, placedTargets, cables, inverters));
        visibleChangedCables.addAll(syncNeighborsOfBrokenTargets(world, brokenTargets, cables, inverters));
        visibleChangedCables.addAll(processWaveAdoptions(world, waveAdoptionTargets));
        LinkedHashSet<Vector3i> refreshCables = new LinkedHashSet<>(affectedPositions);
        refreshCables.addAll(visibleChangedCables);
        for (Vector3i cable : refreshCables) {
            if (!cables.contains(cable)) {
                continue;
            }
            GravityPowderBlockRefresher.refreshAt(world, cable);
        }
        for (Vector3i inverter : affectedPositions) {
            if (!inverters.contains(inverter)) {
                continue;
            }
            InverterBlockRefresher.refreshAt(world, inverter);
        }
        ConnectableNetworkUpdateService.updateSiphonsNear(world, merge(merge(dirtyPositions, affectedPositions), visibleChangedCables));
    }

    private static Set<Vector3i> merge(Set<Vector3i> first, Set<Vector3i> second) {
        LinkedHashSet<Vector3i> result = new LinkedHashSet<>();
        result.addAll(first);
        result.addAll(second);
        return Set.copyOf(result);
    }

    private static void enqueueRecompute(World world, Vector3i target) {
        PENDING_RECOMPUTE.computeIfAbsent(world, ignored -> ConcurrentHashMap.newKeySet())
                .addAll(ConnectableNeighborResolver.positionsAround(target));
    }

    private static void enqueueWaveAdoption(World world, Vector3i target) {
        PENDING_WAVE_ADOPTION.computeIfAbsent(world, ignored -> ConcurrentHashMap.newKeySet()).add(target);
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
        return affectedConnectablePositions(dirtyPositions, cables, inverters);
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

    private static Set<Vector3i> syncPlacedTargets(World world, Set<Vector3i> placedTargets, Set<Vector3i> cables, Set<Vector3i> inverters) {
        LinkedHashSet<Vector3i> visibleChangedCables = new LinkedHashSet<>();
        for (Vector3i target : placedTargets) {
            if (cables.contains(target)) {
                if (adoptInstantStateAndScheduleNeighbors(world, target)) {
                    visibleChangedCables.add(target);
                }
            }
            if (inverters.contains(target)) {
                InverterDataStore.adoptCurrentMode(world, target);
            }
        }
        return Set.copyOf(visibleChangedCables);
    }

    private static Set<Vector3i> syncNeighborsOfBrokenTargets(World world, Set<Vector3i> brokenTargets, Set<Vector3i> cables, Set<Vector3i> inverters) {
        LinkedHashSet<Vector3i> visibleChangedCables = new LinkedHashSet<>();
        for (Vector3i target : brokenTargets) {
            for (Vector3i neighbor : ConnectableNeighborResolver.positionsAround(target)) {
                if (neighbor.equals(target)) {
                    continue;
                }
                if (cables.contains(neighbor)) {
                    if (adoptInstantStateAndScheduleNeighbors(world, neighbor)) {
                        visibleChangedCables.add(neighbor);
                    }
                }
                if (inverters.contains(neighbor)) {
                    InverterDataStore.adoptCurrentMode(world, neighbor);
                }
            }
        }
        return Set.copyOf(visibleChangedCables);
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
        GravityPowderBlockDataStore.GravityPowderBlockData previous = GravityPowderBlockDataStore.get(world, target);
        if (previous == null || !previous.hasWaveMismatch()) {
            return false;
        }

        String previousEffectiveState = previous.effectiveState();
        GravityPowderBlockDataStore.adoptInstantState(world, target);
        GravityPowderBlockDataStore.GravityPowderBlockData updated = GravityPowderBlockDataStore.get(world, target);
        if (updated == null) {
            return false;
        }

        for (Vector3i neighbor : mismatchedCableNeighbors(
                target,
                ConnectableNeighborResolver.positionsAround(target),
                neighbor -> GravityPowderBlockDataStore.get(world, neighbor)
        )) {
            enqueueWaveAdoption(world, neighbor);
        }
        return !updated.effectiveState().equals(previousEffectiveState);
    }
}
