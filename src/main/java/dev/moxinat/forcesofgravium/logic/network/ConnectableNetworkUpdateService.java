package dev.moxinat.forcesofgravium.logic.network;

import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import dev.moxinat.forcesofgravium.data.GraviumSiphonStore;
import dev.moxinat.forcesofgravium.logic.siphon.GraviumSiphonBlockRefresher;
import dev.moxinat.forcesofgravium.registry.ConnectableRegistry;

import javax.annotation.Nonnull;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class ConnectableNetworkUpdateService {

    private static final Set<String> INITIALIZED_WORLDS = ConcurrentHashMap.newKeySet();

    private ConnectableNetworkUpdateService() {
    }

    public static void ensureInitialized(@Nonnull World world) {
        String worldKey = world.getSavePath().toAbsolutePath().normalize().toString();
        if (INITIALIZED_WORLDS.add(worldKey)) {
            updateAllSiphons(world);
        }
    }

    public static void updateAllSiphons(@Nonnull World world) {
        updateSiphons(world, GraviumSiphonStore.snapshotForWorld(world).keySet(), true);
    }

    public static void updateSiphonsNear(@Nonnull World world, @Nonnull Set<Vector3i> affectedPositions) {
        LinkedHashSet<Vector3i> siphons = new LinkedHashSet<>();
        for (Vector3i affectedPosition : affectedPositions) {
            for (Vector3i candidate : ConnectableNeighborResolver.positionsAround(affectedPosition)) {
                if (isGraviumSiphon(world, candidate)) {
                    siphons.add(candidate);
                }
            }
        }
        updateSiphons(world, siphons, false);
    }

    private static void updateSiphons(@Nonnull World world, @Nonnull Set<Vector3i> siphons, boolean refreshUnchanged) {
        if (siphons.isEmpty()) {
            return;
        }

        ScanCache scanCache = new ScanCache();
        for (Vector3i siphon : siphons) {
            if (!isGraviumSiphon(world, siphon)) {
                GraviumSiphonStore.remove(world, siphon);
                continue;
            }

            SiphonNetworkState state = resolveSiphonState(world, siphon, scanCache);
            boolean changed = GraviumSiphonStore.setState(world, siphon, state.powered(), state.locked());
            if (changed || refreshUnchanged) {
                GraviumSiphonBlockRefresher.refreshAt(world, siphon);
            }
        }
    }

    private static @Nonnull SiphonNetworkState resolveSiphonState(@Nonnull World world, @Nonnull Vector3i siphon, @Nonnull ScanCache scanCache) {
        boolean powered = false;
        boolean locked = false;

        for (Vector3i neighbor : controlNeighbors(world, siphon)) {
            NetworkScanResult pushResult = scanCache.scan(world, neighbor, SignalMode.PUSH);
            if (pushResult.hasAnySource()) {
                powered = true;
            }

            NetworkScanResult pullResult = scanCache.scan(world, neighbor, SignalMode.PULL);
            if (pullResult.hasAnySource()) {
                locked = true;
            }
        }

        return new SiphonNetworkState(powered, locked);
    }

    private static @Nonnull Set<Vector3i> controlNeighbors(@Nonnull World world, @Nonnull Vector3i siphon) {
        LinkedHashSet<Vector3i> result = new LinkedHashSet<>();
        addControlNeighbor(world, result, siphon, ConnectableRegistry.SIDE_RIGHT);
        addControlNeighbor(world, result, siphon, ConnectableRegistry.SIDE_LEFT);
        addControlNeighbor(world, result, siphon, ConnectableRegistry.SIDE_TOP);
        addControlNeighbor(world, result, siphon, ConnectableRegistry.SIDE_BOTTOM);
        return Set.copyOf(result);
    }

    private static void addControlNeighbor(@Nonnull World world, @Nonnull Set<Vector3i> result, @Nonnull Vector3i siphon, int localSide) {
        Vector3i neighbor = ConnectableNeighborResolver.adjacentPositionForLocalSide(world, siphon, localSide);
        BlockType blockType = world.getBlockType(neighbor.getX(), neighbor.getY(), neighbor.getZ());
        if (blockType != null && ConnectableRegistry.isGravityPowderId(blockType.getId())) {
            result.add(neighbor);
        }
    }

    private static boolean isGraviumSiphon(@Nonnull World world, @Nonnull Vector3i position) {
        BlockType blockType = world.getBlockType(position.getX(), position.getY(), position.getZ());
        return blockType != null && ConnectableRegistry.isGraviumSiphonId(blockType.getId());
    }

    private record SiphonNetworkState(boolean powered, boolean locked) {
    }

    private static final class ScanCache {
        private final Map<NetworkStep, NetworkScanResult> resultsByCarrierStep = new LinkedHashMap<>();

        private @Nonnull NetworkScanResult scan(@Nonnull World world, @Nonnull Vector3i start, @Nonnull SignalMode mode) {
            NetworkStep key = new NetworkStep(start, mode);
            NetworkScanResult cached = resultsByCarrierStep.get(key);
            if (cached != null) {
                return cached;
            }

            NetworkScanResult result = ConnectableNetworkScanner.scanFrom(world, start, mode);
            resultsByCarrierStep.put(key, result);
            for (Vector3i carrier : result.carriers()) {
                resultsByCarrierStep.put(new NetworkStep(carrier, mode), result);
            }
            return result;
        }
    }
}
