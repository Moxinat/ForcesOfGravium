package dev.moxinat.forcesofgravium.logic.network;

import com.hypixel.hytale.math.vector.Vector3i;
import dev.moxinat.forcesofgravium.data.GravityPowderBlockDataStore;
import dev.moxinat.forcesofgravium.logic.gravity.GravityPowderStateCalculator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
    void marksBrokenPushComponentForOffWhenNoReplacementSourceExists() {
        SignalSourceBfs.SourceSearchResult currentModeResult = new SignalSourceBfs.SourceSearchResult(
                false,
                GravityPowderStateCalculator.MODE_PUSH,
                null,
                List.of(new SignalSourceBfs.TraversalNode(new Vector3i(0, 0, 0), GravityPowderStateCalculator.MODE_PUSH))
        );
        SignalSourceBfs.ModeSearchResult replacementMode = new SignalSourceBfs.ModeSearchResult(
                GravityPowderStateCalculator.MODE_OFF,
                currentModeResult
        );

        String decayMark = ConnectablePropagationScheduler.decayMarkForBrokenNeighbor(
                GravityPowderStateCalculator.MODE_PUSH,
                currentModeResult,
                replacementMode
        );

        assertEquals(GravityPowderBlockDataStore.WAVE_OFF, decayMark);
    }

    @Test
    void marksBrokenPushComponentForPullWhenOnlyPullReplacementExists() {
        SignalSourceBfs.SourceSearchResult currentModeResult = new SignalSourceBfs.SourceSearchResult(
                false,
                GravityPowderStateCalculator.MODE_PUSH,
                null,
                List.of(new SignalSourceBfs.TraversalNode(new Vector3i(0, 0, 0), GravityPowderStateCalculator.MODE_PUSH))
        );
        SignalSourceBfs.ModeSearchResult replacementMode = new SignalSourceBfs.ModeSearchResult(
                GravityPowderStateCalculator.MODE_PULL,
                new SignalSourceBfs.SourceSearchResult(
                        true,
                        GravityPowderStateCalculator.MODE_PULL,
                        new Vector3i(2, 0, 0),
                        List.of()
                )
        );

        String decayMark = ConnectablePropagationScheduler.decayMarkForBrokenNeighbor(
                GravityPowderStateCalculator.MODE_PUSH,
                currentModeResult,
                replacementMode
        );

        assertEquals(GravityPowderBlockDataStore.WAVE_PULL, decayMark);
    }

    @Test
    void doesNothingForBrokenPullComponentWhenPushReplacementExists() {
        SignalSourceBfs.SourceSearchResult currentModeResult = new SignalSourceBfs.SourceSearchResult(
                false,
                GravityPowderStateCalculator.MODE_PULL,
                null,
                List.of(new SignalSourceBfs.TraversalNode(new Vector3i(0, 0, 0), GravityPowderStateCalculator.MODE_PULL))
        );
        SignalSourceBfs.ModeSearchResult replacementMode = new SignalSourceBfs.ModeSearchResult(
                GravityPowderStateCalculator.MODE_PUSH,
                new SignalSourceBfs.SourceSearchResult(
                        true,
                        GravityPowderStateCalculator.MODE_PUSH,
                        new Vector3i(2, 0, 0),
                        List.of()
                )
        );

        String decayMark = ConnectablePropagationScheduler.decayMarkForBrokenNeighbor(
                GravityPowderStateCalculator.MODE_PULL,
                currentModeResult,
                replacementMode
        );

        assertNull(decayMark);
    }
}
