package dev.moxinat.forcesofgravium.connectable.network;

import dev.moxinat.forcesofgravium.connectable.spatial.ConnectableNeighborResolver;

import dev.moxinat.forcesofgravium.connectable.SignalState;

import dev.moxinat.forcesofgravium.connectable.propagation.NetworkStep;

import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import org.joml.Vector3i;
import com.hypixel.hytale.server.core.universe.world.World;
import dev.moxinat.forcesofgravium.connectable.core.ConnectableRuntimeAccessor;
import dev.moxinat.forcesofgravium.block.siphon.GraviumSiphonStore;
import dev.moxinat.forcesofgravium.block.siphon.GraviumSiphonBlockRefresher;
import dev.moxinat.forcesofgravium.connectable.registry.ConnectableRegistry;

import javax.annotation.Nonnull;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public final class ConnectableNetworkUpdateService {

    private ConnectableNetworkUpdateService() {
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
        updateSiphons(world, siphons);
    }

    private static void updateSiphons(@Nonnull World world, @Nonnull Set<Vector3i> siphons) {
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
            if (changed) {
                GraviumSiphonBlockRefresher.refreshAt(world, siphon);
            }
        }
    }

    private static @Nonnull SiphonNetworkState resolveSiphonState(@Nonnull World world, @Nonnull Vector3i siphon, @Nonnull ScanCache scanCache) {
        boolean powered = false;
        boolean locked = false;

        for (Vector3i neighbor : controlNeighbors(world, siphon)) {
            NetworkScanResult pushResult = scanCache.scan(world, neighbor, SignalState.PUSH);
            if (pushResult.hasAnySource()) {
                powered = true;
            }

            NetworkScanResult pullResult = scanCache.scan(world, neighbor, SignalState.PULL);
            if (pullResult.hasAnySource()) {
                locked = true;
            }
        }

        return new SiphonNetworkState(powered, locked);
    }

    private static @Nonnull Set<Vector3i> controlNeighbors(@Nonnull World world, @Nonnull Vector3i siphon) {
        LinkedHashSet<Vector3i> result = new LinkedHashSet<>();
        addControlNeighborIfSignalInput(world, result, siphon, ConnectableRegistry.SIDE_FRONT);
        addControlNeighborIfSignalInput(world, result, siphon, ConnectableRegistry.SIDE_BACK);
        addControlNeighborIfSignalInput(world, result, siphon, ConnectableRegistry.SIDE_RIGHT);
        addControlNeighborIfSignalInput(world, result, siphon, ConnectableRegistry.SIDE_LEFT);
        addControlNeighborIfSignalInput(world, result, siphon, ConnectableRegistry.SIDE_TOP);
        addControlNeighborIfSignalInput(world, result, siphon, ConnectableRegistry.SIDE_BOTTOM);
        return Set.copyOf(result);
    }

    private static void addControlNeighborIfSignalInput(@Nonnull World world, @Nonnull Set<Vector3i> result, @Nonnull Vector3i siphon, int localSide) {
        if (ConnectableRuntimeAccessor.canReceiveSignalFrom(ConnectableRegistry.GRAVIUM_SIPHON_BLOCK_ID, localSide)) {
            addControlNeighbor(world, result, siphon, localSide);
        }
    }

    private static void addControlNeighbor(@Nonnull World world, @Nonnull Set<Vector3i> result, @Nonnull Vector3i siphon, int localSide) {
        Vector3i neighbor = ConnectableNeighborResolver.adjacentPositionForLocalSide(world, siphon, localSide);
        ConnectableNode node = ConnectableNodeProvider.nodeAt(world, neighbor).orElse(null);
        if (node != null
                && node.isSignalRuntimeNode()
                && ConnectableNeighborResolver.areMutuallyConnected(world, siphon, neighbor)) {
            result.add(neighbor);
        }
    }

    private static boolean isGraviumSiphon(@Nonnull World world, @Nonnull Vector3i position) {
        BlockType blockType = world.getBlockType(position.x(), position.y(), position.z());
        return blockType != null && ConnectableRegistry.isGraviumSiphonId(blockType.getId());
    }

    private record SiphonNetworkState(boolean powered, boolean locked) {
    }

    private static final class ScanCache {
        private final Map<NetworkStep, NetworkScanResult> resultsByCarrierStep = new LinkedHashMap<>();

        private @Nonnull NetworkScanResult scan(@Nonnull World world, @Nonnull Vector3i start, @Nonnull SignalState mode) {
            NetworkStep key = new NetworkStep(start, mode);
            NetworkScanResult cached = resultsByCarrierStep.get(key);
            if (cached != null) {
                return cached;
            }

            NetworkScanResult result = ConnectableNetworkScanner.scanFrom(world, start, mode);
            resultsByCarrierStep.put(key, result);
            for (Vector3i node : result.nodes()) {
                resultsByCarrierStep.put(new NetworkStep(node, mode), result);
            }
            return result;
        }
    }
}
