package dev.moxinat.forcesofgravium.logic.network;

import com.hypixel.hytale.server.core.universe.world.World;
import dev.moxinat.forcesofgravium.connectable.ConnectableRuntimeAccessor;
import dev.moxinat.forcesofgravium.data.GravityPowderBlockDataStore;
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
        Set<Vector3i> carriers = GravityPowderBlockDataStore.snapshotForWorld(world).keySet();

        for (Vector3i carrier : carriers) {
            SignalState effectiveState = effectiveSignalState(world, carrier);
            if (effectiveState == SignalState.OFF || !indexedSteps.add(new NetworkStep(carrier, effectiveState))) {
                continue;
            }

            NetworkScanResult result = ConnectableNetworkScanner.scanFrom(world, carrier, effectiveState);
            if (result.carriers().isEmpty()) {
                continue;
            }

            long networkId = NEXT_NETWORK_ID.getAndIncrement();
            ConnectableNetworkSummary summary = summaryFor(world, networkId, result);
            summaries.put(networkId, summary);

            for (Vector3i member : summary.members()) {
                ConnectableRuntimeAccessor.setNetworkId(world, member, networkId);
            }
            for (Vector3i resultCarrier : result.carriers()) {
                indexedSteps.add(new NetworkStep(resultCarrier, result.requestedState()));
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
        LinkedHashSet<Vector3i> members = new LinkedHashSet<>();
        members.addAll(result.carriers());
        members.addAll(result.inverters());

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
                result.carriers(),
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

    private static @Nonnull SignalState effectiveSignalState(@Nonnull World world, @Nonnull Vector3i position) {
        return ConnectableRuntimeAccessor.getRuntimeData(world, position)
                .map(data -> signalForState(data.effectiveState()))
                .orElse(SignalState.OFF);
    }

    private static @Nonnull SignalState signalForState(@Nonnull String state) {
        return switch (state) {
            case GravityPowderBlockDataStore.STATE_PUSH -> SignalState.PUSH;
            case GravityPowderBlockDataStore.STATE_PULL -> SignalState.PULL;
            default -> SignalState.OFF;
        };
    }
}
