package dev.moxinat.forcesofgravium.connectable.network;

import dev.moxinat.forcesofgravium.connectable.SignalState;
import org.joml.Vector3i;

import javax.annotation.Nonnull;
import java.util.Set;

public record ConnectableNetworkSummary(
        long networkId,
        @Nonnull SignalState mode,
        @Nonnull Set<Vector3i> members,
        @Nonnull Set<Vector3i> nodes,
        @Nonnull Set<Vector3i> sources,
        @Nonnull Set<Vector3i> consumers,
        int totalEnergyDelta
) {
}
