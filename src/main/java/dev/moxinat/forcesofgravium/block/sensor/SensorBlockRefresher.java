package dev.moxinat.forcesofgravium.block.sensor;

import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import dev.moxinat.forcesofgravium.data.Nodes;
import dev.moxinat.forcesofgravium.registry.NodeTypes;
import dev.moxinat.forcesofgravium.signal.SignalState;
import org.joml.Vector3i;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class SensorBlockRefresher {

    private SensorBlockRefresher() {
    }

    private static final Map<World, Map<Vector3i, PendingAnimation>> PENDING =
            new ConcurrentHashMap<>();

    private static final Map<World, Map<Vector3i, SignalState>> LAST_STATE =
            new ConcurrentHashMap<>();

    private static final int PUSH_OPEN_TICKS = 75;
    private static final int PULL_OPEN_TICKS = 125;

    private static final int PUSH_CLOSE_TICKS = 23;
    private static final int PULL_CLOSE_TICKS = 23;

    private record PendingAnimation(
            String state,
            long executeTick
    ) {
    }

    public static void tickWorld(World world) {
        Map<Vector3i, PendingAnimation> pending =
                PENDING.get(world);

        if (pending == null) {
            return;
        }

        long currentTick = world.getTick();

        for (Map.Entry<Vector3i, PendingAnimation> entry :
                pending.entrySet()) {

            Vector3i position = entry.getKey();
            PendingAnimation animation = entry.getValue();

            if (animation.executeTick() > currentTick) {
                continue;
            }

            if (!pending.remove(position, animation)) {
                continue;
            }

            Nodes.Node node = Nodes.get(
                    world,
                    position
            );

            if (node == null
                    || !NodeTypes.GRAVIUM_SENSOR
                    .blockId()
                    .equals(node.blockId())) {
                continue;
            }

            setState(
                    world,
                    position,
                    animation.state()
            );

            switch (animation.state()) {
                case "PushOpen" -> schedule(
                        world,
                        position,
                        "Push",
                        PUSH_OPEN_TICKS
                );

                case "PullOpen" -> schedule(
                        world,
                        position,
                        "Pull",
                        PULL_OPEN_TICKS
                );

                default -> {
                }
            }
        }

        if (pending.isEmpty()) {
            PENDING.remove(
                    world,
                    pending
            );
        }
    }

    private static void schedule(
            World world,
            Vector3i position,
            String state,
            int delayTicks
    ) {
        PENDING
                .computeIfAbsent(
                        world,
                        ignored -> new ConcurrentHashMap<>()
                )
                .put(
                        new Vector3i(position),
                        new PendingAnimation(
                                state,
                                world.getTick() + delayTicks
                        )
                );
    }

    private static void setState(
            World world,
            Vector3i position,
            String stateName
    ) {

        BlockType baseType = BlockType.fromString(
                NodeTypes.GRAVIUM_SENSOR.blockId()
        );

        if (baseType == null) {
            return;
        }

        WorldChunk chunk = world.getChunk(
                ChunkUtil.indexChunkFromBlock(
                        position.x(),
                        position.z()
                )
        );

        if (chunk == null) {
            return;
        }

        chunk.setBlockInteractionState(
                position.x(),
                position.y(),
                position.z(),
                baseType,
                stateName,
                true
        );
    }

    public static void refreshAt(
            World world,
            Vector3i position
    ) {
        Nodes.Node node = Nodes.get(
                world,
                position
        );

        if (node == null
                || !NodeTypes.GRAVIUM_SENSOR
                .blockId()
                .equals(node.blockId())) {
            return;
        }

        SignalState currentState =
                node.effectiveState();

        Map<Vector3i, SignalState> worldStates =
                LAST_STATE.computeIfAbsent(
                        world,
                        ignored -> new ConcurrentHashMap<>()
                );

        SignalState rememberedState =
                worldStates.put(
                        new Vector3i(position),
                        currentState
                );

        SignalState previousState;

        if (rememberedState == null) {
            previousState = node.previousEffectiveState();

            if (previousState == currentState) {
                setState(
                        world,
                        position,
                        switch (currentState) {
                            case OFF -> "Off";
                            case PUSH -> "Push";
                            case PULL -> "Pull";
                        }
                );
                return;
            }
        } else {
            previousState = rememberedState;

            if (previousState == currentState) {
                return;
            }
        }

        switch (previousState) {
            case OFF -> {
                switch (currentState) {
                    case PUSH -> {
                        setState(world, position, "PushOpen");
                        schedule(
                                world,
                                position,
                                "Push",
                                PUSH_OPEN_TICKS
                        );
                    }

                    case PULL -> {
                        setState(world, position, "PullOpen");
                        schedule(
                                world,
                                position,
                                "Pull",
                                PULL_OPEN_TICKS
                        );
                    }

                    default -> {
                    }
                }
            }

            case PUSH -> {
                setState(
                        world,
                        position,
                        "PushClose"
                );

                schedule(
                        world,
                        position,
                        currentState == SignalState.PULL
                                ? "PullOpen"
                                : "Off",
                        PUSH_CLOSE_TICKS
                );
            }

            case PULL -> {
                setState(
                        world,
                        position,
                        "PullClose"
                );

                schedule(
                        world,
                        position,
                        currentState == SignalState.PUSH
                                ? "PushOpen"
                                : "Off",
                        PULL_CLOSE_TICKS
                );
            }
        }
    }

    public static void handleBroken(
            World world,
            Vector3i position
    ) {
        Map<Vector3i, PendingAnimation> pending =
                PENDING.get(world);

        if (pending != null) {
            pending.remove(position);

            if (pending.isEmpty()) {
                PENDING.remove(world, pending);
            }
        }

        Map<Vector3i, SignalState> states =
                LAST_STATE.get(world);

        if (states != null) {
            states.remove(position);

            if (states.isEmpty()) {
                LAST_STATE.remove(world, states);
            }
        }
    }

    public static void restoreAt(
            World world,
            Vector3i position
    ) {
        Nodes.Node node = Nodes.get(
                world,
                position
        );

        if (node == null
                || !NodeTypes.GRAVIUM_SENSOR.blockId()
                .equals(node.blockId())) {
            return;
        }

        Map<Vector3i, PendingAnimation> pending =
                PENDING.get(world);

        if (pending != null) {
            pending.remove(position);

            if (pending.isEmpty()) {
                PENDING.remove(world, pending);
            }
        }

        SignalState state = node.effectiveState();

        LAST_STATE
                .computeIfAbsent(
                        world,
                        ignored -> new ConcurrentHashMap<>()
                )
                .put(
                        new Vector3i(position),
                        state
                );

        setState(
                world,
                position,
                switch (state) {
                    case OFF -> "Off";
                    case PUSH -> "Push";
                    case PULL -> "Pull";
                }
        );
    }
}
