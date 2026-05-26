package dev.moxinat.forcesofgravium.logic.network;

import org.joml.Vector3i;

import javax.annotation.Nonnull;

public record NetworkStep(@Nonnull Vector3i position, @Nonnull SignalState signalState) {
}
