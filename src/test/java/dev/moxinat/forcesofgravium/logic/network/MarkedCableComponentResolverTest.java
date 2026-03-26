package dev.moxinat.forcesofgravium.logic.network;

import com.hypixel.hytale.math.vector.Vector3i;
import org.junit.jupiter.api.Test;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarkedCableComponentResolverTest {

    @Test
    void findsWholeMarkedCableComponent() {
        Vector3i start = new Vector3i(0, 0, 0);
        Vector3i second = new Vector3i(1, 0, 0);
        Vector3i third = new Vector3i(1, 1, 0);
        Vector3i unmarked = new Vector3i(5, 0, 0);

        TestAdapter adapter = new TestAdapter(
                Map.of(
                        start, List.of(second),
                        second, List.of(start, third),
                        third, List.of(second),
                        unmarked, List.of()
                ),
                Set.of(start, second, third)
        );

        Set<Vector3i> result = MarkedCableComponentResolver.findComponent(adapter, start, "off");

        assertEquals(Set.of(start, second, third), result);
    }

    @Test
    void returnsEmptyForUnmarkedStartCable() {
        Vector3i start = new Vector3i(0, 0, 0);

        Set<Vector3i> result = MarkedCableComponentResolver.findComponent(
                new TestAdapter(Map.of(start, List.of()), Set.of()),
                start,
                "pull"
        );

        assertTrue(result.isEmpty());
    }

    @Test
    void findsMarkedComponentAcrossDifferentMarkedStates() {
        Vector3i start = new Vector3i(0, 0, 0);
        Vector3i second = new Vector3i(1, 0, 0);
        Vector3i third = new Vector3i(2, 0, 0);

        Set<Vector3i> result = MarkedCableComponentResolver.findAnyMarkedComponent(
                new TestAdapter(
                        Map.of(
                                start, List.of(second),
                                second, List.of(start, third),
                                third, List.of(second)
                        ),
                        Set.of(start, second, third)
                ),
                start
        );

        assertEquals(Set.of(start, second, third), result);
    }

    private static final class TestAdapter implements MarkedCableComponentResolver.ComponentAdapter {
        private final Map<Vector3i, List<Vector3i>> neighbors;
        private final Set<Vector3i> marked;

        private TestAdapter(Map<Vector3i, List<Vector3i>> neighbors, Set<Vector3i> marked) {
            this.neighbors = neighbors;
            this.marked = marked;
        }

        @Override
        public boolean isMarkedCable(@Nonnull Vector3i position, @Nonnull String decayMark) {
            return marked.contains(position);
        }

        @Override
        public boolean isAnyMarkedCable(@Nonnull Vector3i position) {
            return marked.contains(position);
        }

        @Override
        public @Nonnull List<Vector3i> directCableNeighbors(@Nonnull Vector3i position) {
            return neighbors.getOrDefault(position, List.of());
        }
    }
}
