package dev.moxinat.forcesofgravium.logic.network;

import com.hypixel.hytale.math.vector.Vector3i;
import dev.moxinat.forcesofgravium.logic.gravity.GravityPowderStateCalculator;
import org.junit.jupiter.api.Test;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SignalSourceBfsTest {

    @Test
    void findsSourceThroughMatchingModeOnly() {
        Vector3i start = new Vector3i(0, 0, 0);
        Vector3i matching = new Vector3i(1, 0, 0);
        Vector3i wrongMode = new Vector3i(0, 1, 0);
        Vector3i source = new Vector3i(2, 0, 0);

        TestAdapter adapter = new TestAdapter(
                Map.of(
                        new SignalSourceBfs.TraversalNode(start, GravityPowderStateCalculator.MODE_PUSH), List.of(
                                new SignalSourceBfs.TraversalStep(matching, GravityPowderStateCalculator.MODE_PUSH),
                                new SignalSourceBfs.TraversalStep(wrongMode, GravityPowderStateCalculator.MODE_PULL)
                        ),
                        new SignalSourceBfs.TraversalNode(matching, GravityPowderStateCalculator.MODE_PUSH), List.of()
                ),
                Map.of(
                        new SignalSourceBfs.TraversalNode(matching, GravityPowderStateCalculator.MODE_PUSH), source
                )
        );

        SignalSourceBfs.SourceSearchResult result = SignalSourceBfs.findSource(
                adapter,
                start,
                GravityPowderStateCalculator.MODE_PUSH
        );

        assertTrue(result.foundSource());
        assertEquals(source, result.sourcePosition());
        assertEquals(GravityPowderStateCalculator.MODE_PUSH, result.expectedMode());
        assertFalse(result.visitedNodes().contains(new SignalSourceBfs.TraversalNode(wrongMode, GravityPowderStateCalculator.MODE_PUSH)));
    }

    @Test
    void resolvesPushBeforePull() {
        Vector3i start = new Vector3i(0, 0, 0);
        Vector3i pushSource = new Vector3i(2, 0, 0);
        Vector3i pullSource = new Vector3i(-2, 0, 0);

        TestAdapter adapter = new TestAdapter(
                Map.of(),
                Map.of(
                        new SignalSourceBfs.TraversalNode(start, GravityPowderStateCalculator.MODE_PUSH), pushSource,
                        new SignalSourceBfs.TraversalNode(start, GravityPowderStateCalculator.MODE_PULL), pullSource
                )
        );

        SignalSourceBfs.ModeSearchResult result = SignalSourceBfs.resolveMode(adapter, start);

        assertEquals(GravityPowderStateCalculator.MODE_PUSH, result.resolvedMode());
        assertTrue(result.searchResult().foundSource());
        assertEquals(pushSource, result.searchResult().sourcePosition());
    }

    @Test
    void supportsModeFlipAcrossInverterTransition() {
        Vector3i start = new Vector3i(0, 0, 0);
        Vector3i flippedCable = new Vector3i(-1, 0, 0);
        Vector3i source = new Vector3i(-2, 0, 0);

        TestAdapter adapter = new TestAdapter(
                Map.of(
                        new SignalSourceBfs.TraversalNode(start, GravityPowderStateCalculator.MODE_PULL),
                        List.of(new SignalSourceBfs.TraversalStep(flippedCable, GravityPowderStateCalculator.MODE_PUSH))
                ),
                Map.of(
                        new SignalSourceBfs.TraversalNode(flippedCable, GravityPowderStateCalculator.MODE_PUSH), source
                )
        );

        SignalSourceBfs.SourceSearchResult result = SignalSourceBfs.findSource(
                adapter,
                start,
                GravityPowderStateCalculator.MODE_PULL
        );

        assertTrue(result.foundSource());
        assertEquals(source, result.sourcePosition());
        assertTrue(result.visitedNodes().contains(new SignalSourceBfs.TraversalNode(flippedCable, GravityPowderStateCalculator.MODE_PUSH)));
    }

    @Test
    void returnsOffWhenNoModeCanReachSource() {
        SignalSourceBfs.ModeSearchResult result = SignalSourceBfs.resolveMode(new TestAdapter(Map.of(), Map.of()), new Vector3i(0, 0, 0));

        assertEquals(GravityPowderStateCalculator.MODE_OFF, result.resolvedMode());
        assertFalse(result.searchResult().foundSource());
        assertNotNull(result.searchResult().visitedNodes());
    }

    private static final class TestAdapter implements SignalSourceBfs.TraversalAdapter {
        private final Map<SignalSourceBfs.TraversalNode, List<SignalSourceBfs.TraversalStep>> steps;
        private final Map<SignalSourceBfs.TraversalNode, Vector3i> sources;

        private TestAdapter(
                Map<SignalSourceBfs.TraversalNode, List<SignalSourceBfs.TraversalStep>> steps,
                Map<SignalSourceBfs.TraversalNode, Vector3i> sources
        ) {
            this.steps = steps;
            this.sources = sources;
        }

        @Override
        public @Nullable Vector3i findAdjacentSource(@Nonnull SignalSourceBfs.TraversalNode node) {
            return sources.get(node);
        }

        @Override
        public @Nonnull List<SignalSourceBfs.TraversalStep> reverseTraversalSteps(@Nonnull SignalSourceBfs.TraversalNode node) {
            return steps.getOrDefault(node, List.of());
        }
    }
}
