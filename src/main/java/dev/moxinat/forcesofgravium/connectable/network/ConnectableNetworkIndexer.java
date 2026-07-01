package dev.moxinat.forcesofgravium.connectable.network;

import com.hypixel.hytale.server.core.universe.world.World;
import dev.moxinat.forcesofgravium.block.gravity.GravityPowderSpecialStateStore;
import dev.moxinat.forcesofgravium.connectable.core.ConnectableRuntimeAccessor;
import dev.moxinat.forcesofgravium.connectable.propagation.ConnectableNode;
import dev.moxinat.forcesofgravium.connectable.propagation.ConnectableNodeProvider;
import dev.moxinat.forcesofgravium.connectable.propagation.NetworkStep;
import dev.moxinat.forcesofgravium.connectable.propagation.SignalState;
import dev.moxinat.forcesofgravium.connectable.spatial.ConnectableNeighborResolver;
import org.joml.Vector3i;

import javax.annotation.Nonnull;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class ConnectableNetworkIndexer {

    private static final AtomicLong NEXT_NETWORK_ID = new AtomicLong(1L);
    private static final Map<String, Map<Long, ConnectableNetworkSummary>> SUMMARIES_BY_WORLD = new ConcurrentHashMap<>();

    private ConnectableNetworkIndexer() {
    }

    public static @Nonnull Map<Long, ConnectableNetworkSummary> rebuildWorld(@Nonnull World world) {
        ConnectableRuntimeAccessor.clearNetworkIds(world);

        LinkedHashMap<Long, ConnectableNetworkSummary> summaries = new LinkedHashMap<>();
        LinkedHashSet<NetworkStep> indexedSteps = new LinkedHashSet<>();

        for (ConnectableNode node : ConnectableNodeProvider.nodesForWorld(world).values()) {
            if (!node.isSignalRuntimeNode()) {
                continue;
            }
            SignalState effectiveState = effectiveSignalState(node);
            if (effectiveState == SignalState.OFF || !indexedSteps.add(new NetworkStep(node.position(), effectiveState))) {
                continue;
            }

            NetworkScanResult result = ConnectableNetworkScanner.scanFrom(world, node.position(), effectiveState);
            if (result.nodes().isEmpty()) {
                continue;
            }

            long networkId = NEXT_NETWORK_ID.getAndIncrement();
            ConnectableNetworkSummary summary = summaryFor(world, networkId, result);
            summaries.put(networkId, summary);

            for (Vector3i member : summary.members()) {
                ConnectableRuntimeAccessor.setNetworkId(world, member, networkId);
            }
            for (Vector3i resultNode : result.nodes()) {
                SignalState memberState = signalForState(ConnectableRuntimeAccessor.effectiveState(world, resultNode));
                if (memberState != SignalState.OFF) {
                    indexedSteps.add(new NetworkStep(resultNode, memberState));
                }
            }
        }

        Map<Long, ConnectableNetworkSummary> snapshot = Map.copyOf(summaries);
        SUMMARIES_BY_WORLD.put(world.getName(), snapshot);
        return snapshot;
    }

    public static @Nonnull Map<Long, ConnectableNetworkSummary> snapshotForWorld(@Nonnull World world) {
        return SUMMARIES_BY_WORLD.getOrDefault(world.getName(), Map.of());
    }

    public static void clearWorld(@Nonnull World world) {
        SUMMARIES_BY_WORLD.remove(world.getName());
        ConnectableRuntimeAccessor.clearNetworkIds(world);
    }

    private static @Nonnull ConnectableNetworkSummary summaryFor(
            @Nonnull World world,
            long networkId,
            @Nonnull NetworkScanResult result
    ) {
        LinkedHashSet<Vector3i> members = new LinkedHashSet<>(result.nodes());
        LinkedHashSet<Vector3i> energyCandidates = energyCandidates(world, result, members);
        LinkedHashSet<Vector3i> producers = new LinkedHashSet<>();
        LinkedHashSet<Vector3i> consumers = new LinkedHashSet<>();

        int totalEnergyDelta = 0;
        for (Vector3i position : energyCandidates) {
            int energyDelta = ConnectableRuntimeAccessor.getEnergyDelta(world, position);
            if (energyDelta == 0) {
                continue;
            }
            totalEnergyDelta += energyDelta;
            if (energyDelta > 0) {
                producers.add(position);
            } else {
                consumers.add(position);
            }
        }

        return new ConnectableNetworkSummary(
                networkId,
                result.requestedState(),
                Set.copyOf(members),
                result.nodes(),
                Set.copyOf(producers),
                Set.copyOf(consumers),
                totalEnergyDelta
        );
    }

    private static @Nonnull LinkedHashSet<Vector3i> energyCandidates(
            @Nonnull World world,
            @Nonnull NetworkScanResult result,
            @Nonnull Set<Vector3i> members
    ) {
        LinkedHashSet<Vector3i> candidates = new LinkedHashSet<>();
        candidates.addAll(members);
        candidates.addAll(result.sources());
        candidates.addAll(result.consumers());

        for (Vector3i member : members) {
            for (Vector3i candidate : ConnectableNeighborResolver.positionsAround(member)) {
                if (candidate.equals(member) || !ConnectableRuntimeAccessor.contributesEnergy(world, candidate)) {
                    continue;
                }
                if (ConnectableNeighborResolver.hasConnectableSideFacing(world, member, candidate)
                        && ConnectableNeighborResolver.hasConnectableSideFacing(world, candidate, member)) {
                    candidates.add(candidate);
                }
            }
        }

        return candidates;
    }

    private static @Nonnull SignalState effectiveSignalState(@Nonnull ConnectableNode node) {
        return signalForState(node.effectiveState());
    }

    private static @Nonnull SignalState signalForState(@Nonnull String state) {
        return switch (GravityPowderSpecialStateStore.normalizeState(state)) {
            case GravityPowderSpecialStateStore.STATE_PUSH -> SignalState.PUSH;
            case GravityPowderSpecialStateStore.STATE_PULL -> SignalState.PULL;
            default -> SignalState.OFF;
        };
    }
}
