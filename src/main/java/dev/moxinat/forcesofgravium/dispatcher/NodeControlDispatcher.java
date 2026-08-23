package dev.moxinat.forcesofgravium.dispatcher;

import com.hypixel.hytale.server.core.universe.world.World;
import dev.moxinat.forcesofgravium.block.inverter.InverterStateCalculator;
import dev.moxinat.forcesofgravium.block.sensor.SensorLogic;
import dev.moxinat.forcesofgravium.registry.ConnectableRegistry;
import dev.moxinat.forcesofgravium.data.Nodes;
import org.joml.Vector3i;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class NodeControlDispatcher {

    private NodeControlDispatcher() {
    }

    private static final Map<World, Map<Vector3i, Set<Vector3i>>> CURRENT =
            new ConcurrentHashMap<>();

    private static final Map<World, Map<Vector3i, Set<Vector3i>>> NEXT =
            new ConcurrentHashMap<>();

    public static void dispatch(
            @Nonnull World world,
            @Nonnull Vector3i targetPosition,
            @Nonnull Vector3i sourcePosition
    ) {
        NEXT
                .computeIfAbsent(
                        world,
                        ignored -> new ConcurrentHashMap<>()
                )
                .computeIfAbsent(
                        new Vector3i(targetPosition),
                        ignored -> ConcurrentHashMap.newKeySet()
                )
                .add(new Vector3i(sourcePosition));
    }

    public static void tickWorld(
            @Nonnull World world
    ) {
        Map<Vector3i, Set<Vector3i>> current =
                CURRENT.remove(world);

        if (current != null) {
            for (Map.Entry<Vector3i, Set<Vector3i>> entry : current.entrySet()) {
                Vector3i targetPosition = entry.getKey();

                for (Vector3i sourcePosition : entry.getValue()) {
                    execute(
                            world,
                            targetPosition,
                            sourcePosition
                    );
                }
            }
        }

        Map<Vector3i, Set<Vector3i>> next =
                NEXT.remove(world);

        if (next != null && !next.isEmpty()) {
            CURRENT.put(
                    world,
                    next
            );
        }
    }

    private static void execute(
            @Nonnull World world,
            @Nonnull Vector3i targetPosition,
            @Nonnull Vector3i sourcePosition
    ) {
        Nodes.Node node = Nodes.get(
                world,
                targetPosition
        );

        if (node == null) {
            return;
        }

        switch (node.blockId()) {
            case ConnectableRegistry.INVERTER_BLOCK_ID ->
                    InverterStateCalculator.handleControlChange(
                            world,
                            targetPosition,
                            sourcePosition
                    );

            default -> {
            }
        }

        SensorLogic.compareSensorsObserving(
                world,
                targetPosition,
                false
        );

        ConnectableVisualDispatcher.refreshAt(
                world,
                targetPosition
        );
    }
}
