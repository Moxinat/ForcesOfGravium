package dev.moxinat.forcesofgravium.connectable.propagation;

import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import dev.moxinat.forcesofgravium.connectable.core.ConnectableDefinition;
import dev.moxinat.forcesofgravium.connectable.core.ConnectableRuntimeData;
import org.joml.Vector3i;

import javax.annotation.Nonnull;
import java.util.Objects;

public record ConnectableNode(
        @Nonnull Vector3i position,
        @Nonnull String blockId,
        @Nonnull ConnectableDefinition definition,
        @Nonnull ConnectableRuntimeData runtimeData
) {
    public ConnectableNode {
        position = new Vector3i(Objects.requireNonNull(position, "position"));
        blockId = Objects.requireNonNull(blockId, "blockId");
        definition = Objects.requireNonNull(definition, "definition");
        runtimeData = Objects.requireNonNull(runtimeData, "runtimeData");
    }

    public @Nonnull RotationTuple rotation() {
        return runtimeData.rotation();
    }

    public int signalInputSides() {
        return definition.signalInputSidesMask();
    }

    public int signalOutputSides() {
        return definition.signalOutputSidesMask();
    }

    public int controlInputSides() {
        return definition.controlInputSidesMask();
    }

    public boolean invertCapable() {
        return definition.invertCapable();
    }

    public boolean passBehaviorCapable() {
        return definition.passBehaviorCapable();
    }

    public boolean invertEnabled() {
        return runtimeData.invertEnabled();
    }

    public boolean passing() {
        return runtimeData.passing();
    }

    public @Nonnull String instantState() {
        return runtimeData.instantState();
    }

    public @Nonnull String effectiveState() {
        return runtimeData.effectiveState();
    }

    public boolean canReceiveSignalFrom(int localSide) {
        return (signalInputSides() & localSide) != 0;
    }

    public boolean canOutputSignalTo(int localSide) {
        return (signalOutputSides() & localSide) != 0;
    }

    public boolean canReceiveControlFrom(int localSide) {
        return (controlInputSides() & localSide) != 0;
    }

    public boolean isSignalRuntimeNode() {
        return signalOutputSides() != 0 || passBehaviorCapable() || invertCapable();
    }

    public boolean shouldStorePropagatedInstantState() {
        return signalInputSides() != 0 && isSignalRuntimeNode();
    }
}
