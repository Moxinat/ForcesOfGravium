package dev.moxinat.forcesofgravium.connectable.network;

import dev.moxinat.forcesofgravium.connectable.propagation.SignalState;
import org.joml.Vector3i;

import javax.annotation.Nonnull;
import java.util.Set;

public record NetworkScanResult(
        @Nonnull SignalState requestedState,
        @Nonnull Set<Vector3i> nodes,
        @Nonnull Set<Vector3i> sources,
        @Nonnull Set<Vector3i> consumers
) {

    public NetworkScanResult {
        nodes = Set.copyOf(nodes);
        sources = Set.copyOf(sources);
        consumers = Set.copyOf(consumers);
    }

    public @Nonnull Set<Vector3i> members() {
        return nodes;
    }

    public boolean hasAnySource() {
        return !sources.isEmpty();
    }
}
