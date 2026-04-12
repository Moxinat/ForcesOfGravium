package dev.moxinat.forcesofgravium.logic.network;

import com.hypixel.hytale.math.vector.Vector3i;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConnectablePropagationSchedulerTest {

    @Test
    void treatsNullBlockTypeAsNotGravityPowder() {
        assertTrue(ConnectablePropagationScheduler.isNotGravityPowder(null));
    }

    @Test
    void treatsNullBlockTypeAsNotInverter() {
        assertTrue(ConnectablePropagationScheduler.isNotInverter(null));
    }

    @Test
    void affectedPositionsIncludeOnlyTouchedComponent() {
        Vector3i dirty = new Vector3i(0, 0, 0);
        Vector3i first = new Vector3i(1, 0, 0);
        Vector3i second = new Vector3i(2, 0, 0);
        Vector3i inverter = new Vector3i(3, 0, 0);
        Vector3i isolated = new Vector3i(20, 0, 0);

        Set<Vector3i> affected = ConnectablePropagationScheduler.affectedConnectablePositions(
                Set.of(dirty),
                Set.of(first, second, isolated),
                Set.of(inverter)
        );

        assertEquals(Set.of(first, second, inverter), affected);
    }

    @Test
    void affectedPositionsIncludeBothSidesAfterBridgeBreak() {
        Vector3i dirty = new Vector3i(0, 0, 0);
        Vector3i left = new Vector3i(-1, 0, 0);
        Vector3i right = new Vector3i(1, 0, 0);
        Vector3i isolated = new Vector3i(20, 0, 0);

        Set<Vector3i> affected = ConnectablePropagationScheduler.affectedConnectablePositions(
                Set.of(dirty),
                Set.of(left, right, isolated),
                Set.of()
        );

        assertEquals(Set.of(left, right), affected);
    }
}
