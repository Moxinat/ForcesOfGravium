package dev.moxinat.forcesofgravium.connectable.propagation;

import org.joml.Vector3i;

import javax.annotation.Nonnull;

public record NetworkStep(@Nonnull Vector3i position, @Nonnull SignalState signalState) {
}
