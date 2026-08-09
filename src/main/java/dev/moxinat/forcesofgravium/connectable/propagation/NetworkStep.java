package dev.moxinat.forcesofgravium.connectable.propagation;

import dev.moxinat.forcesofgravium.connectable.SignalState;
import org.joml.Vector3i;

import javax.annotation.Nonnull;

public record NetworkStep(@Nonnull Vector3i position, @Nonnull SignalState signalState) {
}
