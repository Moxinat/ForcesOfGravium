package dev.moxinat.forcesofgravium.logic.network;

import com.hypixel.hytale.math.vector.Vector3i;

import javax.annotation.Nonnull;

public record NetworkStep(@Nonnull Vector3i position, @Nonnull SignalMode mode) {
}
