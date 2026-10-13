package dev.moxinat.forcesofgravium.dispatcher;

import dev.moxinat.forcesofgravium.spatial.ConnectableNeighborResolver;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import dev.moxinat.forcesofgravium.block.sensor.SensorLogic;
import dev.moxinat.forcesofgravium.block.shifter.ShifterLogic;
import dev.moxinat.forcesofgravium.registry.ConnectableRegistry;
import org.joml.Vector3i;

import javax.annotation.Nonnull;

public final class NodeStateDispatcher {

    private NodeStateDispatcher() {
    }

    public static void dispatch(
            @Nonnull World world,
            @Nonnull Vector3i position
    ) {
        BlockType blockType =
                ConnectableNeighborResolver.getBlockType(world, position);

        if (blockType == null) {
            return;
        }

        String blockId =
                ConnectableRegistry.rawBlockId(
                        blockType.getId()
                );

        switch (blockId) {
            case ConnectableRegistry.GRAVIUM_SENSOR_BLOCK_ID ->
                    SensorLogic.handleStateChange(world, position);
            case ConnectableRegistry.GRAVIUM_SHIFTER_BLOCK_ID ->
                    ShifterLogic.handleStateChange(world, position);

            default -> {
            }
        }

        SensorLogic.compareSensorsObserving(
                world,
                position,
                false
        );
    }
}
