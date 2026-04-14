package dev.moxinat.forcesofgravium.registry;

import javax.annotation.Nullable;
import java.util.Set;

public final class ConnectableBlockRoles {

    private static final Set<String> SOURCE_BLOCK_IDS = Set.of(
            ConnectableRegistry.WIND_GENERATOR_BLOCK_ID
    );

    private static final Set<String> MACHINE_BLOCK_IDS = Set.of();
    private static final Set<String> CONSUMER_BLOCK_IDS = Set.of(
            ConnectableRegistry.GRAVIUM_SIPHON_BLOCK_ID
    );

    private ConnectableBlockRoles() {
    }

    public static boolean isSource(@Nullable String blockId) {
        return blockId != null && SOURCE_BLOCK_IDS.contains(blockId);
    }

    public static boolean isMachine(@Nullable String blockId) {
        return blockId != null && MACHINE_BLOCK_IDS.contains(blockId);
    }

    public static boolean isConsumer(@Nullable String blockId) {
        return blockId != null && (CONSUMER_BLOCK_IDS.contains(blockId) || ConnectableRegistry.isGraviumSiphonId(blockId));
    }
}
