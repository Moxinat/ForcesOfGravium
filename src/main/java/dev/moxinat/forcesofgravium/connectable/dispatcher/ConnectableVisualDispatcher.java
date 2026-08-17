package dev.moxinat.forcesofgravium.connectable.dispatcher;

import com.hypixel.hytale.server.core.universe.world.World;
import dev.moxinat.forcesofgravium.block.gravity.CasedGravityPowderBlockRefresher;
import dev.moxinat.forcesofgravium.block.gravity.GravityPowderBlockRefresher;
import dev.moxinat.forcesofgravium.block.inverter.InverterBlockRefresher;
import dev.moxinat.forcesofgravium.block.siphon.GraviumSiphonBlockRefresher;
import dev.moxinat.forcesofgravium.connectable.registry.ConnectableRegistry;
import dev.moxinat.forcesofgravium.connectable.spatial.ConnectableNeighborResolver;
import dev.moxinat.forcesofgravium.data.Nodes;
import org.joml.Vector3i;

import javax.annotation.Nonnull;

public final class ConnectableVisualDispatcher {

    private ConnectableVisualDispatcher() {
    }

    public static void refreshAt(
            @Nonnull World world,
            @Nonnull Vector3i position
    ) {
        Nodes.Node node = Nodes.get(world, position);

        if (node == null) {
            return;
        }

        switch (node.blockId()) {
            case ConnectableRegistry.GRAVIUM_SIPHON_BLOCK_ID ->
                    GraviumSiphonBlockRefresher.refreshAt(world, position);
            case ConnectableRegistry.INVERTER_BLOCK_ID ->
                    InverterBlockRefresher.refreshAt(world, position);
            case ConnectableRegistry.STRAIGHT_CASED_GRAVITY_POWDER_BLOCK_ID,
                 ConnectableRegistry.CURVE_CASED_GRAVITY_POWDER_BLOCK_ID ->
                    CasedGravityPowderBlockRefresher.refreshAt(world, position);
            case ConnectableRegistry.GRAVITY_POWDER_BLOCK_ID ->
                    GravityPowderBlockRefresher.refreshAt(world, position);

            default -> {
            }
        }
    }

    public static void refreshTopologyAround(
            @Nonnull World world,
            @Nonnull Vector3i position
    ) {
        refreshAt(
                world,
                position
        );

        for (Vector3i neighbor :
                ConnectableNeighborResolver.positionsAround(position)) {

            if (neighbor.equals(position)) {
                continue;
            }

            refreshAt(
                    world,
                    neighbor
            );
        }
    }
}
