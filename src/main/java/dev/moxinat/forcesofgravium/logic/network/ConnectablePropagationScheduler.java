package dev.moxinat.forcesofgravium.logic.network;

import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.World;
import dev.moxinat.forcesofgravium.data.GravityPowderBlockDataStore;
import dev.moxinat.forcesofgravium.data.GravityPowderBlockDataStore.GravityPowderBlockData;
import dev.moxinat.forcesofgravium.data.InverterDataStore;
import dev.moxinat.forcesofgravium.data.InverterDataStore.InverterData;
import dev.moxinat.forcesofgravium.debug.ReconnectWaveDebug;
import dev.moxinat.forcesofgravium.logic.gravity.GravityPowderBlockRefresher;
import dev.moxinat.forcesofgravium.logic.gravity.GravityPowderStateCalculator;
import dev.moxinat.forcesofgravium.logic.gravity.GravityPowderStateCalculator.GravityPowderStateUpdate;
import dev.moxinat.forcesofgravium.logic.inverter.InverterStateCalculator;
import dev.moxinat.forcesofgravium.logic.inverter.InverterStateCalculator.InverterStateUpdate;
import dev.moxinat.forcesofgravium.registry.ConnectableRegistry;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class ConnectablePropagationScheduler {

    private static final ConcurrentHashMap<World, Set<Vector3i>> PENDING_CURRENT = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<World, Set<Vector3i>> PENDING_NEXT = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<World, Queue<ReconnectPlacementRequest>> PENDING_RECONNECT_CURRENT = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<World, Queue<ReconnectPlacementRequest>> PENDING_RECONNECT_NEXT = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<World, Set<Vector3i>> WAVE_SUPPRESSED = new ConcurrentHashMap<>();

    private ConnectablePropagationScheduler() {
    }

    public static void onConnectablePlaced(World world, Vector3i target) {
        if (shouldEnqueueReconnectPlacement(world, target)) {
            enqueueReconnectPlacement(world, target, null);
        }
        enqueueCurrent(world, ConnectableNeighborResolver.positionsAround(target));
    }

    public static void onConnectablePlaced(World world, Vector3i target, Player debugPlayer) {
        if (shouldEnqueueReconnectPlacement(world, target)) {
            enqueueReconnectPlacement(world, target, debugPlayer);
        }
        enqueueCurrent(world, ConnectableNeighborResolver.positionsAround(target));
    }

    public static void onConnectableBroken(World world, Vector3i target) {
        handleBrokenConnectable(world, target);
        enqueueCurrent(world, ConnectableNeighborResolver.positionsAround(target));
    }

    public static void tickPropagation() {
        Set<World> worlds = new HashSet<>();
        worlds.addAll(PENDING_CURRENT.keySet());
        worlds.addAll(PENDING_NEXT.keySet());
        worlds.addAll(PENDING_RECONNECT_CURRENT.keySet());
        worlds.addAll(PENDING_RECONNECT_NEXT.keySet());
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
        Set<Vector3i> suppressed = WAVE_SUPPRESSED.computeIfAbsent(world, ignored -> ConcurrentHashMap.newKeySet());
        Queue<ReconnectPlacementRequest> reconnectCurrent = PENDING_RECONNECT_CURRENT.computeIfAbsent(world, ignored -> new ConcurrentLinkedQueue<>());
        Queue<ReconnectPlacementRequest> reconnectNext = PENDING_RECONNECT_NEXT.computeIfAbsent(world, ignored -> new ConcurrentLinkedQueue<>());
        ReconnectPlacementRequest reconnectRequest;
        while ((reconnectRequest = reconnectCurrent.poll()) != null) {
            handlePlacedConnectable(world, reconnectRequest.target(), reconnectRequest.debugPlayer());
        }

        Set<Vector3i> current = PENDING_CURRENT.computeIfAbsent(world, ignored -> ConcurrentHashMap.newKeySet());
        if (current.isEmpty()) {
            Set<Vector3i> next = PENDING_NEXT.remove(world);
            if (next == null || next.isEmpty()) {
                if (reconnectCurrent.isEmpty() && !reconnectNext.isEmpty()) {
                    reconnectCurrent.addAll(reconnectNext);
                    reconnectNext.clear();
                } else {
                    PENDING_CURRENT.remove(world, current);
                    if (reconnectCurrent.isEmpty()) {
                        PENDING_RECONNECT_CURRENT.remove(world, reconnectCurrent);
                    }
                    if (reconnectNext.isEmpty()) {
                        PENDING_RECONNECT_NEXT.remove(world, reconnectNext);
                    }
                    if (suppressed.isEmpty()) {
                        WAVE_SUPPRESSED.remove(world, suppressed);
                    }
                    return;
                }
            }
            if (next != null && !current.isEmpty()) {
                // no-op
            } else if (next != null) {
                current.addAll(next);
            }
        }

        List<Vector3i> positions = List.copyOf(current);
        current.clear();
        if (positions.isEmpty()) {
            return;
        }

        Set<Vector3i> changedPositions = new LinkedHashSet<>();
        for (Vector3i position : positions) {
            if (!isNotInverter(world.getBlockType(position.getX(), position.getY(), position.getZ()))) {
                applyInverterUpdate(world, InverterStateCalculator.computeStateUpdate(world, position), changedPositions);
            }
        }

        for (Vector3i position : positions) {
            if (isNotGravityPowder(world.getBlockType(position.getX(), position.getY(), position.getZ()))) {
                continue;
            }
            applyPowderUpdate(world, GravityPowderStateCalculator.computeStateUpdate(world, position), changedPositions);
        }

        for (Vector3i position : positions) {
            GravityPowderBlockRefresher.refreshAt(world, position);
        }

        if (!changedPositions.isEmpty()) {
            Set<Vector3i> nextQueue = PENDING_NEXT.computeIfAbsent(world, ignored -> ConcurrentHashMap.newKeySet());
            for (Vector3i changedPosition : changedPositions) {
                nextQueue.addAll(ConnectableNeighborResolver.positionsAround(changedPosition));
            }
        }

        suppressed.clear();
        WAVE_SUPPRESSED.remove(world, suppressed);
        if (reconnectCurrent.isEmpty() && !reconnectNext.isEmpty()) {
            reconnectCurrent.addAll(reconnectNext);
            reconnectNext.clear();
        }
        if (reconnectCurrent.isEmpty()) {
            PENDING_RECONNECT_CURRENT.remove(world, reconnectCurrent);
        }
        if (reconnectNext.isEmpty()) {
            PENDING_RECONNECT_NEXT.remove(world, reconnectNext);
        }
    }

    private static void applyPowderUpdate(World world, GravityPowderStateUpdate update, Set<Vector3i> changedPositions) {
        GravityPowderBlockData existing = GravityPowderBlockDataStore.getOrCreate(world, update.position());
        GravityPowderBlockDataStore.setState(world, update.position(), update.nextState());
        GravityPowderBlockDataStore.setStateTicksRemaining(world, update.position(), update.nextStateTicksRemaining());
        if (!existing.state().equals(update.nextState())
                || existing.stateTicksRemaining() != update.nextStateTicksRemaining()) {
            changedPositions.add(update.position());
        }
    }

    private static void applyInverterUpdate(World world, InverterStateUpdate update, Set<Vector3i> changedPositions) {
        InverterData existing = InverterDataStore.getOrCreate(world, update.position());
        InverterDataStore.setNextMode(world, update.position(), update.nextMode());
        if (!existing.currentMode().equals(update.nextMode())) {
            InverterDataStore.setCurrentMode(world, update.position(), update.nextMode());
            changedPositions.add(update.position());
            if (GravityPowderStateCalculator.MODE_OFF.equals(update.nextMode())) {
                triggerOutputSideLossCheck(world, update.position());
            }
        }
    }

    private static void enqueueCurrent(World world, List<Vector3i> positions) {
        if (positions.isEmpty()) {
            return;
        }
        PENDING_CURRENT.computeIfAbsent(world, ignored -> ConcurrentHashMap.newKeySet()).addAll(positions);
    }

    static void handleBrokenConnectable(World world, Vector3i target) {
        for (Vector3i neighbor : ConnectableNeighborResolver.positionsAround(target)) {
            if (neighbor.equals(target)) {
                continue;
            }
            handleCableLossCheck(world, neighbor, target);
        }
    }

    static void handlePlacedConnectable(World world, Vector3i target, Player debugPlayer) {
        List<Vector3i> candidateCables = reconnectCandidateCables(world, target);
        Set<Vector3i> processedCables = new LinkedHashSet<>();
        for (Vector3i candidate : candidateCables) {
            if (processedCables.contains(candidate)) {
                continue;
            }

            Set<Vector3i> component = CableComponentResolver.findComponent(world, candidate);
            processedCables.addAll(component);
            if (component.isEmpty()) {
                continue;
            }

            if (!containsWave(world, component)) {
                continue;
            }
            ReconnectWaveDebug.send(debugPlayer, "Wave in component found at " + formatPosition(candidate) + " size=" + component.size() + ".");

            if (!ReconnectSourceBfs.canReachSource(world, candidate)) {
                ReconnectWaveDebug.send(debugPlayer, "No source path found from " + formatPosition(candidate) + ".");
                continue;
            }
            ReconnectWaveDebug.send(debugPlayer, "Source path found from " + formatPosition(candidate) + ".");

            clearWaves(world, component);
            ReconnectWaveDebug.send(debugPlayer, "Wave cleared for component at " + formatPosition(candidate) + " size=" + component.size() + ".");
        }
    }

    static String decayMarkForBrokenNeighbor(
            String currentMode,
            SignalSourceBfs.SourceSearchResult currentModeResult,
            SignalSourceBfs.ModeSearchResult replacementMode
    ) {
        Objects.requireNonNull(currentMode, "currentMode");
        Objects.requireNonNull(currentModeResult, "currentModeResult");
        Objects.requireNonNull(replacementMode, "replacementMode");

        if (currentModeResult.foundSource()) {
            return null;
        }

        if (GravityPowderStateCalculator.MODE_OFF.equals(replacementMode.resolvedMode())) {
            return GravityPowderBlockDataStore.WAVE_OFF;
        }

        if (GravityPowderStateCalculator.MODE_PUSH.equals(currentMode)
                && GravityPowderStateCalculator.MODE_PULL.equals(replacementMode.resolvedMode())) {
            return GravityPowderBlockDataStore.WAVE_PULL;
        }

        return null;
    }

    private static boolean isMarkedCable(World world, Vector3i position) {
        BlockType blockType = world.getBlockType(position.getX(), position.getY(), position.getZ());
        if (isNotGravityPowder(blockType)) {
            return false;
        }

        GravityPowderBlockData data = GravityPowderBlockDataStore.get(world, position);
        return GravityPowderBlockDataStore.hasActiveWave(data);
    }

    private static boolean containsWave(World world, Set<Vector3i> component) {
        for (Vector3i cable : component) {
            if (isMarkedCable(world, cable)) {
                return true;
            }
        }
        return false;
    }

    private static void clearWaves(World world, Set<Vector3i> component) {
        Set<Vector3i> suppressed = WAVE_SUPPRESSED.computeIfAbsent(world, ignored -> ConcurrentHashMap.newKeySet());
        for (Vector3i cable : component) {
            GravityPowderBlockData data = GravityPowderBlockDataStore.get(world, cable);
            if (!GravityPowderBlockDataStore.hasActiveWave(data)) {
                continue;
            }
            GravityPowderBlockDataStore.setState(world, cable, GravityPowderBlockDataStore.stableStateFor(data.state()));
            GravityPowderBlockDataStore.setStateTicksRemaining(world, cable, 0);
            suppressed.add(cable);
        }
        enqueueCurrent(world, List.copyOf(component));
    }

    private static void triggerOutputSideLossCheck(World world, Vector3i inverterPosition) {
        Vector3i outputCable = ConnectableNeighborResolver.adjacentPositionForLocalSide(
                world,
                inverterPosition,
                ConnectableRegistry.SIDE_FRONT
        );
        handleCableLossCheck(world, outputCable, inverterPosition);
    }

    static boolean shouldEnqueueReconnectPlacement(String blockId) {
        return ConnectableRegistry.isGravityPowderId(blockId);
    }

    private static void handleCableLossCheck(World world, Vector3i cablePosition, Vector3i treatAsEmpty) {
        BlockType cableType = world.getBlockType(cablePosition.getX(), cablePosition.getY(), cablePosition.getZ());
        if (isNotGravityPowder(cableType)) {
            return;
        }

        GravityPowderBlockData data = GravityPowderBlockDataStore.get(world, cablePosition);
        if (data == null || GravityPowderStateCalculator.MODE_OFF.equals(GravityPowderBlockDataStore.effectiveMode(data))) {
            return;
        }

        String effectiveMode = GravityPowderBlockDataStore.effectiveMode(data);
        SignalSourceBfs.SourceSearchResult currentModeResult = SignalSourceBfs.findSource(world, cablePosition, effectiveMode, treatAsEmpty);
        if (currentModeResult.foundSource()) {
            return;
        }

        SignalSourceBfs.ModeSearchResult replacementMode = SignalSourceBfs.resolveMode(world, cablePosition, treatAsEmpty);
        String decayMark = decayMarkForBrokenNeighbor(effectiveMode, currentModeResult, replacementMode);
        if (decayMark == null) {
            return;
        }

        if (isWaveSuppressed(world, cablePosition)) {
            return;
        }
        GravityPowderBlockDataStore.setState(world, cablePosition, decayMark);
        GravityPowderBlockDataStore.setStateTicksRemaining(world, cablePosition, GravityPowderStateCalculator.WAVE_TICKS);
        enqueueCurrent(world, ConnectableNeighborResolver.positionsAround(cablePosition));
    }

    private static boolean isWaveSuppressed(World world, Vector3i position) {
        Set<Vector3i> suppressed = WAVE_SUPPRESSED.get(world);
        return suppressed != null && suppressed.contains(position);
    }

    private static void enqueueReconnectPlacement(World world, Vector3i target, Player debugPlayer) {
        Queue<ReconnectPlacementRequest> queue = PENDING_RECONNECT_NEXT.computeIfAbsent(world, ignored -> new ConcurrentLinkedQueue<>());
        queue.add(new ReconnectPlacementRequest(target, debugPlayer));
    }

    private static boolean shouldEnqueueReconnectPlacement(World world, Vector3i target) {
        BlockType placedType = world.getBlockType(target.getX(), target.getY(), target.getZ());
        return placedType != null && shouldEnqueueReconnectPlacement(placedType.getId());
    }

    private static List<Vector3i> reconnectCandidateCables(World world, Vector3i target) {
        LinkedHashSet<Vector3i> candidates = new LinkedHashSet<>();
        BlockType placedType = world.getBlockType(target.getX(), target.getY(), target.getZ());
        if (placedType != null && ConnectableRegistry.isGravityPowderId(placedType.getId())) {
            candidates.add(target);
        }

        for (Vector3i position : ConnectableNeighborResolver.positionsAround(target)) {
            if (position.equals(target)) {
                continue;
            }
            BlockType neighborType = world.getBlockType(position.getX(), position.getY(), position.getZ());
            if (neighborType != null && ConnectableRegistry.isGravityPowderId(neighborType.getId())) {
                candidates.add(position);
            }
        }
        return List.copyOf(candidates);
    }

    private static String formatPosition(Vector3i position) {
        return "(" + position.getX() + "," + position.getY() + "," + position.getZ() + ")";
    }

    private record ReconnectPlacementRequest(Vector3i target, Player debugPlayer) {
    }
}
