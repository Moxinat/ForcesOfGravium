package dev.moxinat.forcesofgravium.logic.network;

import com.hypixel.hytale.math.vector.Vector3i;
import org.junit.jupiter.api.Test;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CableComponentResolverTest {

    @Test
    void findsWholeCableComponentWithoutUsingInverters() {
        Vector3i start = new Vector3i(0, 0, 0);
        Vector3i second = new Vector3i(1, 0, 0);
        Vector3i third = new Vector3i(1, 1, 0);
        Vector3i isolated = new Vector3i(5, 0, 0);

        TestAdapter adapter = new TestAdapter(
                Map.of(
                        start, List.of(second),
                        second, List.of(start, third),
                        third, List.of(second),
                        isolated, List.of()
                ),
                Set.of(start, second, third, isolated)
        );

        Set<Vector3i> result = CableComponentResolver.findComponent(adapter, start);

        assertEquals(Set.of(start, second, third), result);
    }

    @Test
    void returnsEmptyForNonCableStart() {
        Vector3i start = new Vector3i(0, 0, 0);

        Set<Vector3i> result = CableComponentResolver.findComponent(
                new TestAdapter(Map.of(start, List.of()), Set.of()),
                start
        );

        assertTrue(result.isEmpty());
    }

    private static final class TestAdapter implements CableComponentResolver.CableComponentAdapter {
        private final Map<Vector3i, List<Vector3i>> neighbors;
        private final Set<Vector3i> cables;

        private TestAdapter(Map<Vector3i, List<Vector3i>> neighbors, Set<Vector3i> cables) {
            this.neighbors = neighbors;
            this.cables = cables;
        }

        @Override
        public boolean isCable(@Nonnull Vector3i position) {
            return cables.contains(position);
        }

        @Override
        public @Nonnull List<Vector3i> directCableNeighbors(@Nonnull Vector3i position) {
            return neighbors.getOrDefault(position, List.of());
        }
    }
}
