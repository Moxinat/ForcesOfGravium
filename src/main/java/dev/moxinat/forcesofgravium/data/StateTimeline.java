package dev.moxinat.forcesofgravium.data;

import javax.annotation.Nonnull;
import java.util.Objects;

public record StateTimeline(
        @Nonnull String instantState,
        @Nonnull String waveState,
        @Nonnull String previousState
) {
    public StateTimeline {
        instantState = Objects.requireNonNull(instantState, "instantState");
        waveState = Objects.requireNonNull(waveState, "waveState");
        previousState = Objects.requireNonNull(previousState, "previousState");
    }

    public static @Nonnull StateTimeline initialized(@Nonnull String state) {
        return new StateTimeline(state, state, state);
    }

    public @Nonnull StateTimeline withInstantState(@Nonnull String nextInstantState) {
        String nextPreviousState = waveState.equals(nextInstantState) ? nextInstantState : previousState;
        return new StateTimeline(nextInstantState, waveState, nextPreviousState);
    }

    public @Nonnull StateTimeline withWaveStateFromInstantState() {
        return initialized(instantState);
    }

    public boolean hasWaveMismatch() {
        return !waveState.equals(instantState);
    }

    public @Nonnull String effectiveState() {
        return waveState.equals(instantState) ? instantState : previousState;
    }
}
