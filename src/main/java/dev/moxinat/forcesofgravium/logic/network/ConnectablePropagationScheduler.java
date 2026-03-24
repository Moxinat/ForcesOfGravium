package dev.moxinat.forcesofgravium.logic.network;

import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import dev.moxinat.forcesofgravium.data.GravityPowderBlockDataStore;
import dev.moxinat.forcesofgravium.data.GravityPowderBlockDataStore.GravityPowderBlockData;
import dev.moxinat.forcesofgravium.logic.gravity.GravityPowderBlockRefresher;
import dev.moxinat.forcesofgravium.logic.gravity.GravityPowderStateCalculator;
import dev.moxinat.forcesofgravium.logic.gravity.GravityPowderStateCalculator.GravityPowderStateUpdate;
import dev.moxinat.forcesofgravium.registry.ConnectableRegistry;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class ConnectablePropagationScheduler {

    private static final Map<World, Set<Vector3i>> PENDING_CURRENT = new ConcurrentHashMap<>();
    private static final Map<World, Set<Vector3i>> PENDING_NEXT = new ConcurrentHashMap<>();

    private ConnectablePropagationScheduler() {
    }

    public static void onConnectablePlaced(World world, Vector3i target) {
        enqueueCurrent(world, ConnectableNeighborResolver.positionsAround(target));
    }

    public static void onConnectableBroken(World world, Vector3i target) {
        enqueueCurrent(world, ConnectableNeighborResolver.positionsAround(target));
    }

    public static void tickPropagation() {
        Set<World> worlds = new HashSet<>();
        worlds.addAll(PENDING_CURRENT.keySet());
        worlds.addAll(PENDING_NEXT.keySet());
        for (World world : worlds) {
            tickWorld(world);
        }
    }

    public static boolean isNotGravityPowder(BlockType blockType) {
        return blockType == null || !ConnectableRegistry.isGravityPowderId(blockType.getId());
    }

    public static boolean isNotInverter(BlockType blockType) {
        return blockType == null || !ConnectableRegistry.isInverterId(blockType.getId());
    }

    private static void tickWorld(World world) {
        Set<Vector3i> current = PENDING_CURRENT.computeIfAbsent(world, ignored -> ConcurrentHashMap.newKeySet());
        if (current.isEmpty()) {
            Set<Vector3i> next = PENDING_NEXT.remove(world);
            if (next == null || next.isEmpty()) {
                PENDING_CURRENT.remove(world, current);
                return;
            }
            current.addAll(next);
        }

        List<Vector3i> positions = List.copyOf(current);
        current.clear();
        if (positions.isEmpty()) {
            return;
        }

        Set<Vector3i> changedPositions = new LinkedHashSet<>();
        Map<Vector3i, GravityPowderStateUpdate> updates = new ConcurrentHashMap<>();
        for (Vector3i position : positions) {
            if (isNotGravityPowder(world.getBlockType(position.getX(), position.getY(), position.getZ()))) {
                continue;
            }

            GravityPowderStateUpdate update = GravityPowderStateCalculator.computeStateUpdate(world, position);
            updates.put(update.position(), update);
        }

        for (GravityPowderStateUpdate update : updates.values()) {
            GravityPowderBlockData existing = GravityPowderBlockDataStore.getOrCreate(world, update.position());
            GravityPowderBlockDataStore.setNextMode(world, update.position(), update.nextMode());
            GravityPowderBlockDataStore.setNextStable(world, update.position(), update.nextStable());
            GravityPowderBlockDataStore.setNextLossTicks(world, update.position(), update.nextLossTicks());
            GravityPowderBlockDataStore.setNextPositionDistances(world, update.position(), update.nextPositionDistances());

            boolean changed = !existing.currentMode().equals(update.nextMode())
                    || existing.stable() != update.nextStable()
                    || existing.lossTicks() != update.nextLossTicks()
                    || !existing.positionDistances().equals(update.nextPositionDistances());
            if (changed) {
                GravityPowderBlockDataStore.setCurrentMode(world, update.position(), update.nextMode());
                GravityPowderBlockDataStore.setStable(world, update.position(), update.nextStable());
                GravityPowderBlockDataStore.setLossTicks(world, update.position(), update.nextLossTicks());
                GravityPowderBlockDataStore.setPositionDistances(world, update.position(), update.nextPositionDistances());
                changedPositions.add(update.position());
            }
        }

        for (Vector3i position : positions) {
            GravityPowderBlockRefresher.refreshAt(world, position);
        }

        if (!changedPositions.isEmpty()) {
            Set<Vector3i> nextQueue = PENDING_NEXT.computeIfAbsent(world, ignored -> ConcurrentHashMap.newKeySet());
            for (Vector3i changedPosition : changedPositions) {
                GravityPowderBlockData changedData = GravityPowderBlockDataStore.get(world, changedPosition);
                if (changedData != null && GravityPowderStateCalculator.MODE_OFF.equals(changedData.currentMode())) {
                    for (Vector3i neighbor : ConnectableNeighborResolver.positionsAround(changedPosition)) {
                        if (neighbor.equals(changedPosition)) {
                            continue;
                        }
                        if (isNotGravityPowder(world.getBlockType(neighbor.getX(), neighbor.getY(), neighbor.getZ()))) {
                            continue;
                        }
                        GravityPowderBlockDataStore.setStable(world, neighbor, false);
                    }
                }
                nextQueue.addAll(ConnectableNeighborResolver.positionsAround(changedPosition));
            }
        }
    }

    private static void enqueueCurrent(World world, List<Vector3i> positions) {
        if (positions.isEmpty()) {
            return;
        }
        PENDING_CURRENT.computeIfAbsent(world, ignored -> ConcurrentHashMap.newKeySet()).addAll(positions);
    }
}
