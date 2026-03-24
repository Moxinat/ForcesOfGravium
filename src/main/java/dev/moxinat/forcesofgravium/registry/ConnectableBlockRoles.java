package dev.moxinat.forcesofgravium.registry;

import javax.annotation.Nullable;
import java.util.Set;

public final class ConnectableBlockRoles {

    private static final Set<String> SOURCE_BLOCK_IDS = Set.of(
            "Rock_Crystal_Blue_Block"
    );

    private static final Set<String> MACHINE_BLOCK_IDS = Set.of();

    private ConnectableBlockRoles() {
    }

    public static boolean isSource(@Nullable String blockId) {
        return blockId != null && SOURCE_BLOCK_IDS.contains(blockId);
    }

    public static boolean isMachine(@Nullable String blockId) {
        return blockId != null && MACHINE_BLOCK_IDS.contains(blockId);
    }
}
