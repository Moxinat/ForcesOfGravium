package dev.moxinat.forcesofgravium.logic.network;

import org.joml.Vector3i;

import javax.annotation.Nonnull;
import java.util.Set;

public record ConnectableNetworkSummary(
        long networkId,
        @Nonnull SignalState mode,
        @Nonnull Set<Vector3i> members,
        @Nonnull Set<Vector3i> carriers,
        @Nonnull Set<Vector3i> sources,
        @Nonnull Set<Vector3i> consumers,
        int totalEnergyDelta
) {
}
