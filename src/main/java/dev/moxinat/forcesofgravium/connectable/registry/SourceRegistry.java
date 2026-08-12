package dev.moxinat.forcesofgravium.connectable.registry;

import java.util.Map;

public final class SourceRegistry {

    public static final int WOODEN_BUTTON_POWER = 1;
    public static final int WIND_GENERATOR_POWER = 1;

    private static final Map<String, Integer> SOURCE_POWER_BY_BLOCK_ID = Map.of(
            NodeTypes.WOODEN_BUTTON.blockId(), WOODEN_BUTTON_POWER,
            NodeTypes.WIND_GENERATOR.blockId(), WIND_GENERATOR_POWER
    );

    private SourceRegistry() {
    }

    public static int powerFor(String blockId) {
        return SOURCE_POWER_BY_BLOCK_ID.getOrDefault(blockId, 0);
    }
}