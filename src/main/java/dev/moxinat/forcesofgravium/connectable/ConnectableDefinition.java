package dev.moxinat.forcesofgravium.connectable;

import javax.annotation.Nonnull;
import java.util.Objects;

public record ConnectableDefinition(
        @Nonnull String blockId,
        @Nonnull String statePrefix,
        int signalInputSidesMask,
        int signalOutputSidesMask,
        int controlInputSidesMask,
        boolean invertCapable,
        boolean passBehaviorCapable
) {
    public ConnectableDefinition {
        blockId = Objects.requireNonNull(blockId, "blockId");
        statePrefix = Objects.requireNonNull(statePrefix, "statePrefix");
    }

    public boolean matchesBlockId(String candidate) {
        return blockId.equals(candidate) || (candidate != null && !statePrefix.isBlank() && candidate.startsWith(statePrefix));
    }
}
