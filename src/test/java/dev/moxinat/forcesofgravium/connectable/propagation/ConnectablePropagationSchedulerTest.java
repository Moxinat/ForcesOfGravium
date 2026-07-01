package dev.moxinat.forcesofgravium.connectable.propagation;

import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import org.joml.Vector3i;
import dev.moxinat.forcesofgravium.block.gravity.GravityPowderSpecialStateStore;
import dev.moxinat.forcesofgravium.block.gravity.GravityPowderSpecialStateStore.GravityPowderBlockData;
import dev.moxinat.forcesofgravium.connectable.core.ConnectableDefinition;
import dev.moxinat.forcesofgravium.connectable.core.ConnectableDefinitions;
import dev.moxinat.forcesofgravium.connectable.core.ConnectableRuntimeData;
import dev.moxinat.forcesofgravium.connectable.registry.ConnectableRegistry;
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
        Vector3i transformer = new Vector3i(3, 0, 0);
        Vector3i isolated = new Vector3i(20, 0, 0);

        Set<Vector3i> affected = ConnectablePropagationScheduler.affectedConnectablePositions(
                Set.of(dirty),
                Set.of(first, second, transformer, isolated)
        );

        assertEquals(Set.of(first, second, transformer), affected);
    }

    @Test
    void affectedPositionsIncludeBothSidesAfterBridgeBreak() {
        Vector3i dirty = new Vector3i(0, 0, 0);
        Vector3i left = new Vector3i(-1, 0, 0);
        Vector3i right = new Vector3i(1, 0, 0);
        Vector3i isolated = new Vector3i(20, 0, 0);

        Set<Vector3i> affected = ConnectablePropagationScheduler.affectedConnectablePositions(
                Set.of(dirty),
                Set.of(left, right, isolated)
        );

        assertEquals(Set.of(left, right), affected);
    }

    @Test
    void mismatchedWaveNeighborsIncludeOnlyNeighborsWithInstantWaveDiscrepancy() {
        Vector3i node = new Vector3i(0, 0, 0);
        Vector3i mismatched = new Vector3i(1, 0, 0);
        Vector3i confirmed = new Vector3i(-1, 0, 0);
        Vector3i empty = new Vector3i(0, 1, 0);

        Map<Vector3i, GravityPowderBlockData> data = Map.of(
                mismatched,
                new GravityPowderBlockData(
                        0,
                        new StateTimeline(
                                GravityPowderSpecialStateStore.STATE_PUSH,
                                GravityPowderSpecialStateStore.STATE_OFF,
                                GravityPowderSpecialStateStore.STATE_OFF
                        )
                ),
                confirmed,
                new GravityPowderBlockData(
                        0,
                        new StateTimeline(
                                GravityPowderSpecialStateStore.STATE_PUSH,
                                GravityPowderSpecialStateStore.STATE_PUSH,
                                GravityPowderSpecialStateStore.STATE_PUSH
                        )
                )
        );

        Set<Vector3i> pending = ConnectablePropagationScheduler.mismatchedWaveNeighbors(
                node,
                Set.of(node, mismatched, confirmed, empty),
                data::get
        );

        assertEquals(Set.of(mismatched), pending);
    }

    @Test
    void signalOutputNeighborIncludesGenericOutputSideNeighbor() {
        ConnectableNode source = node(new Vector3i(0, 0, 0), ConnectableRegistry.GRAVITY_POWDER_BLOCK_ID);
        ConnectableNode target = node(new Vector3i(1, 0, 0), ConnectableRegistry.GRAVITY_POWDER_BLOCK_ID);

        assertTrue(ConnectablePropagationScheduler.isSignalOutputNeighbor(source, target));
    }

    @Test
    void signalOutputNeighborIncludesInverterFrontOutput() {
        ConnectableNode inverter = node(new Vector3i(0, 0, 0), ConnectableRegistry.INVERTER_BLOCK_ID);
        ConnectableNode frontTarget = node(new Vector3i(0, 0, 1), ConnectableRegistry.GRAVITY_POWDER_BLOCK_ID);

        assertTrue(ConnectablePropagationScheduler.isSignalOutputNeighbor(inverter, frontTarget));
    }

    @Test
    void signalOutputNeighborExcludesInverterControlSide() {
        ConnectableNode inverter = node(new Vector3i(0, 0, 0), ConnectableRegistry.INVERTER_BLOCK_ID);
        ConnectableNode sideControlTarget = node(new Vector3i(1, 0, 0), ConnectableRegistry.GRAVITY_POWDER_BLOCK_ID);

        assertFalse(ConnectablePropagationScheduler.isSignalOutputNeighbor(inverter, sideControlTarget));
    }

    @Test
    void signalOutputNeighborExcludesControlOnlyTargetSide() {
        ConnectableNode source = node(new Vector3i(1, 0, 0), ConnectableRegistry.GRAVITY_POWDER_BLOCK_ID);
        ConnectableNode inverterControlSide = node(new Vector3i(0, 0, 0), ConnectableRegistry.INVERTER_BLOCK_ID);

        assertFalse(ConnectablePropagationScheduler.isSignalOutputNeighbor(source, inverterControlSide));
    }

    private static ConnectableNode node(Vector3i position, String blockId) {
        ConnectableDefinition definition = ConnectableDefinitions.findByBlockId(blockId).orElseThrow();
        return new ConnectableNode(position, blockId, definition, ConnectableRuntimeData.defaultData()
                .withRotation(RotationTuple.NONE)
                .withPassing(true));
    }
}
