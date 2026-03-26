package dev.moxinat.forcesofgravium.logic.network;

import com.hypixel.hytale.math.vector.Vector3i;
import org.junit.jupiter.api.Test;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReconnectSourceBfsTest {

    @Test
    void findsSourceAcrossInactiveCableChain() {
        Vector3i start = new Vector3i(0, 0, 0);
        Vector3i mid = new Vector3i(1, 0, 0);

        TestAdapter adapter = new TestAdapter(
                Set.of(start, mid),
                Map.of(
                        start, List.of(mid),
                        mid, List.of(start)
                ),
                Set.of(mid)
        );

        assertTrue(ReconnectSourceBfs.canReachSource(adapter, start));
    }

    @Test
    void returnsFalseWithoutSourcePath() {
        Vector3i start = new Vector3i(0, 0, 0);
        TestAdapter adapter = new TestAdapter(Set.of(start), Map.of(start, List.of()), Set.of());

        assertFalse(ReconnectSourceBfs.canReachSource(adapter, start));
    }

    private static final class TestAdapter implements ReconnectSourceBfs.ReconnectAdapter {
        private final Set<Vector3i> traversable;
        private final Map<Vector3i, List<Vector3i>> neighbors;
        private final Set<Vector3i> sourceAdjacent;

        private TestAdapter(Set<Vector3i> traversable, Map<Vector3i, List<Vector3i>> neighbors, Set<Vector3i> sourceAdjacent) {
            this.traversable = traversable;
            this.neighbors = neighbors;
            this.sourceAdjacent = sourceAdjacent;
        }

        @Override
        public boolean isTraversable(@Nonnull Vector3i position) {
            return traversable.contains(position);
        }

        @Override
        public boolean hasAdjacentSource(@Nonnull Vector3i position) {
            return sourceAdjacent.contains(position);
        }

        @Override
        public @Nonnull List<Vector3i> reverseTraversalNeighbors(@Nonnull Vector3i position) {
            return neighbors.getOrDefault(position, List.of());
        }
    }
}
