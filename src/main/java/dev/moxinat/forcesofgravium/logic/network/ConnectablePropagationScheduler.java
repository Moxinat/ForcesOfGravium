package dev.moxinat.forcesofgravium.logic.network;

import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.entities.Player;
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

    private ConnectablePropagationScheduler() {
    }

    public static void onConnectablePlaced(World world, Vector3i target) {
        enqueueRecompute(world, target);
    }

    public static void onConnectablePlaced(World world, Vector3i target, Player debugPlayer) {
        enqueueRecompute(world, target);
    }

    public static void onConnectableBroken(World world, Vector3i target) {
        enqueueRecompute(world, target);
    }

    public static void tickPropagation() {
        Map<World, Set<Vector3i>> pendingByWorld = drainPendingRecomputes();
        for (Map.Entry<World, Set<Vector3i>> entry : pendingByWorld.entrySet()) {
            tickWorld(entry.getKey(), entry.getValue());
        }
    }

    public static boolean isNotGravityPowder(BlockType blockType) {
        return blockType == null || !ConnectableRegistry.isGravityPowderId(blockType.getId());
    }

    public static boolean isNotInverter(BlockType blockType) {
        return blockType == null || !ConnectableRegistry.isInverterId(blockType.getId());
    }

    private static void tickWorld(World world, Set<Vector3i> dirtyPositions) {
        Set<Vector3i> affectedPositions = affectedConnectablePositions(world, dirtyPositions);
        if (affectedPositions.isEmpty()) {
            ConnectableNetworkUpdateService.updateSiphonsNear(world, dirtyPositions);
            return;
        }

        ConnectableSignalRecalculator.recompute(world, affectedPositions);
        Set<Vector3i> cables = GravityPowderBlockDataStore.snapshotForWorld(world).keySet();
        Set<Vector3i> inverters = InverterDataStore.snapshotForWorld(world).keySet();
        for (Vector3i cable : affectedPositions) {
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
        ConnectableNetworkUpdateService.updateSiphonsNear(world, merge(dirtyPositions, affectedPositions));
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
}
