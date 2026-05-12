package dev.moxinat.forcesofgravium.logic.network;

import com.hypixel.hytale.math.vector.Vector3i;

import javax.annotation.Nonnull;
import java.util.Set;

public record NetworkScanResult(
        @Nonnull SignalState requestedState,
        @Nonnull Set<Vector3i> carriers,
        @Nonnull Set<Vector3i> inverters,
        @Nonnull Set<Vector3i> sources,
        @Nonnull Set<Vector3i> consumers
) {

    public boolean hasAnySource() {
        return !sources.isEmpty();
    }
}
