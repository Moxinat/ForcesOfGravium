package dev.moxinat.forcesofgravium.logic.network;

import org.joml.Vector3i;
import dev.moxinat.forcesofgravium.data.GravityPowderBlockDataStore;
import dev.moxinat.forcesofgravium.data.GravityPowderBlockDataStore.GravityPowderBlockData;
import dev.moxinat.forcesofgravium.data.InverterDataStore.InverterData;
import dev.moxinat.forcesofgravium.data.StateTimeline;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    @Test
    void mismatchedCableNeighborsIncludeOnlyNeighborsWithInstantWaveDiscrepancy() {
        Vector3i cable = new Vector3i(0, 0, 0);
        Vector3i mismatched = new Vector3i(1, 0, 0);
        Vector3i confirmed = new Vector3i(-1, 0, 0);
        Vector3i empty = new Vector3i(0, 1, 0);

        Map<Vector3i, GravityPowderBlockData> data = Map.of(
                mismatched,
                new GravityPowderBlockData(
                        0,
                        new StateTimeline(
                                GravityPowderBlockDataStore.STATE_PUSH,
                                GravityPowderBlockDataStore.STATE_OFF,
                                GravityPowderBlockDataStore.STATE_OFF
                        )
                ),
                confirmed,
                new GravityPowderBlockData(
                        0,
                        new StateTimeline(
                                GravityPowderBlockDataStore.STATE_PUSH,
                                GravityPowderBlockDataStore.STATE_PUSH,
                                GravityPowderBlockDataStore.STATE_PUSH
                        )
                )
        );

        Set<Vector3i> pending = ConnectablePropagationScheduler.mismatchedCableNeighbors(
                cable,
                Set.of(cable, mismatched, confirmed, empty),
                data::get
        );

        assertEquals(Set.of(mismatched), pending);
    }

    @Test
    void sideInputInvertersForChangedCablesExcludesBackAndFrontInputs() {
        Vector3i sideCable = new Vector3i(0, 1, 0);
        Vector3i backCable = new Vector3i(-1, 0, 0);
        Vector3i frontCable = new Vector3i(1, 0, 0);
        Vector3i inverter = new Vector3i(0, 0, 0);
        Vector3i isolatedInverter = new Vector3i(20, 0, 0);

        Set<Vector3i> sideInverters = ConnectablePropagationScheduler.sideInputInvertersForChangedCables(
                Set.of(sideCable, backCable, frontCable),
                Set.of(inverter, isolatedInverter),
                candidate -> candidate.equals(inverter) ? backCable : new Vector3i(19, 0, 0),
                candidate -> candidate.equals(inverter) ? frontCable : new Vector3i(21, 0, 0)
        );

        assertEquals(Set.of(inverter), sideInverters);
    }

    @Test
    void placedInverterAdoptionIsSkippedWhenRecomputeMarkedItDirty() {
        InverterData dirty = InverterData.defaultData().withDirty(true);
        InverterData clean = InverterData.defaultData();

        assertFalse(ConnectablePropagationScheduler.shouldAdoptPlacedInverter(dirty));
        assertTrue(ConnectablePropagationScheduler.shouldAdoptPlacedInverter(clean));
        assertTrue(ConnectablePropagationScheduler.shouldAdoptPlacedInverter(null));
    }

    @Test
    void brokenNeighborInverterAdoptionIsSkippedWhenRecomputeMarkedItDirty() {
        InverterData dirty = InverterData.defaultData().withDirty(true);
        InverterData clean = InverterData.defaultData();

        assertFalse(ConnectablePropagationScheduler.shouldAdoptBrokenNeighborInverter(dirty));
        assertTrue(ConnectablePropagationScheduler.shouldAdoptBrokenNeighborInverter(clean));
        assertTrue(ConnectablePropagationScheduler.shouldAdoptBrokenNeighborInverter(null));
    }
}
