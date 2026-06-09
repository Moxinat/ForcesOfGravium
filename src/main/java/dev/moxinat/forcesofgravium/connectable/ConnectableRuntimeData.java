package dev.moxinat.forcesofgravium.connectable;

import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import dev.moxinat.forcesofgravium.data.GravityPowderBlockDataStore;

import javax.annotation.Nonnull;
import java.util.Objects;

public record ConnectableRuntimeData(
        @Nonnull RotationTuple rotation,
        @Nonnull String previousInstantState,
        @Nonnull String instantState,
        @Nonnull String previousEffectiveState,
        @Nonnull String effectiveState,
        boolean dirty,
        boolean invertEnabled,
        boolean passing,
        int energyDelta,
        long networkId
) {
    public static final long NO_NETWORK = 0L;

    public ConnectableRuntimeData {
        rotation = Objects.requireNonNull(rotation, "rotation");
        previousInstantState = GravityPowderBlockDataStore.normalizeState(previousInstantState);
        instantState = GravityPowderBlockDataStore.normalizeState(instantState);
        previousEffectiveState = GravityPowderBlockDataStore.normalizeState(previousEffectiveState);
        effectiveState = GravityPowderBlockDataStore.normalizeState(effectiveState);
    }

    public static @Nonnull ConnectableRuntimeData defaultData() {
        return new ConnectableRuntimeData(
                RotationTuple.NONE,
                GravityPowderBlockDataStore.STATE_OFF,
                GravityPowderBlockDataStore.STATE_OFF,
                GravityPowderBlockDataStore.STATE_OFF,
                GravityPowderBlockDataStore.STATE_OFF,
                false,
                false,
                false,
                0,
                NO_NETWORK
        );
    }

    public @Nonnull ConnectableRuntimeData withRotation(@Nonnull RotationTuple value) {
        return new ConnectableRuntimeData(
                value,
                previousInstantState,
                instantState,
                previousEffectiveState,
                effectiveState,
                dirty,
                invertEnabled,
                passing,
                energyDelta,
                networkId
        );
    }

    public @Nonnull ConnectableRuntimeData withInstantState(@Nonnull String value) {
        return new ConnectableRuntimeData(
                rotation,
                instantState,
                value,
                previousEffectiveState,
                effectiveState,
                dirty,
                invertEnabled,
                passing,
                energyDelta,
                networkId
        );
    }

    public @Nonnull ConnectableRuntimeData withDirty(boolean value) {
        return new ConnectableRuntimeData(
                rotation,
                previousInstantState,
                instantState,
                previousEffectiveState,
                effectiveState,
                value,
                invertEnabled,
                passing,
                energyDelta,
                networkId
        );
    }

    public @Nonnull ConnectableRuntimeData withInvertEnabled(boolean value) {
        return new ConnectableRuntimeData(
                rotation,
                previousInstantState,
                instantState,
                previousEffectiveState,
                effectiveState,
                dirty,
                value,
                passing,
                energyDelta,
                networkId
        );
    }

    public @Nonnull ConnectableRuntimeData withNetworkId(long value) {
        return new ConnectableRuntimeData(
                rotation,
                previousInstantState,
                instantState,
                previousEffectiveState,
                effectiveState,
                dirty,
                invertEnabled,
                passing,
                energyDelta,
                value
        );
    }

    public @Nonnull ConnectableRuntimeData withPassing(boolean value) {
        return new ConnectableRuntimeData(
                rotation,
                previousInstantState,
                instantState,
                previousEffectiveState,
                effectiveState,
                dirty,
                invertEnabled,
                value,
                energyDelta,
                networkId
        );
    }

    public @Nonnull ConnectableRuntimeData withEnergyDelta(int value) {
        return new ConnectableRuntimeData(
                rotation,
                previousInstantState,
                instantState,
                previousEffectiveState,
                effectiveState,
                dirty,
                invertEnabled,
                passing,
                value,
                networkId
        );
    }
}
