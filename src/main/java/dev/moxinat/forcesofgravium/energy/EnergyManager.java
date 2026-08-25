package dev.moxinat.forcesofgravium.energy;

import com.hypixel.hytale.server.core.universe.world.World;
import dev.moxinat.forcesofgravium.signal.SignalState;
import dev.moxinat.forcesofgravium.dispatcher.ConnectableVisualDispatcher;
import dev.moxinat.forcesofgravium.data.Nodes;
import org.joml.Vector3i;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class EnergyManager {

    private static final long FAILURE_STATE_TICKS = 5L;
    private static final SignalState[] FAILURE_SEQUENCE = {
            SignalState.PUSH,
            SignalState.PULL,
            SignalState.PUSH,
            SignalState.PULL,
            SignalState.OFF
    };

    private static final Map<World, Map<Long, FailureState>> FAILING_NETWORKS =
            new ConcurrentHashMap<>();

    private EnergyManager() {
    }

    public static void checkNetwork(
            @Nonnull World world,
            @Nonnull Vector3i position
    ) {
        Nodes.Node node = Nodes.get(world, position);

        if (node == null || node.networkId() == Nodes.Node.NO_NETWORK) {
            return;
        }

        long networkId = node.networkId();

        int energy = 0;
        boolean hasEnergySource = false;

        for (Nodes.Node networkNode : Nodes.snapshotForWorld(world).values()) {
            if (networkNode.networkId() != networkId) {
                continue;
            }

            energy += networkNode.energyDelta();

            if (networkNode.energyDelta() > 0) {
                hasEnergySource = true;
            }
        }

        if (hasEnergySource && energy < 0) {
            failNetwork(world, networkId);
        }
    }

    public static int remainingEnergy(
            @Nonnull World world,
            @Nonnull Vector3i position
    ) {
        Nodes.Node node = Nodes.get(world, position);

        if (node == null || node.networkId() == Nodes.Node.NO_NETWORK) {
            return 0;
        }

        long networkId = node.networkId();
        int energy = 0;

        for (Nodes.Node networkNode : Nodes.snapshotForWorld(world).values()) {
            if (networkNode.networkId() == networkId) {
                energy += networkNode.energyDelta();
            }
        }

        return energy;
    }

    public static void tickWorld(@Nonnull World world) {
        Map<Long, FailureState> failingNetworks = FAILING_NETWORKS.get(world);

        if (failingNetworks == null || failingNetworks.isEmpty()) {
            return;
        }

        long currentTick = world.getTick();

        for (Map.Entry<Long, FailureState> entry :
                Map.copyOf(failingNetworks).entrySet()) {

            long networkId = entry.getKey();
            FailureState failureState = entry.getValue();

            if (currentTick < failureState.nextChangeTick()) {
                continue;
            }

            SignalState state = FAILURE_SEQUENCE[failureState.step()];

            for (Nodes.Node node : Nodes.snapshotForWorld(world).values()) {
                if (node.networkId() != networkId) {
                    continue;
                }

                Nodes.Node updatedNode = node
                        .withEffectiveState(state)
                        .withDirty(false);

                if (state == SignalState.OFF) {
                    updatedNode = updatedNode
                            .withInstantState(SignalState.OFF)
                            .withDirty(false);
                }

                Nodes.put(world, updatedNode);
                ConnectableVisualDispatcher.refreshAt(world, updatedNode.position());
            }

            int nextStep = failureState.step() + 1;

            if (nextStep >= FAILURE_SEQUENCE.length) {
                failingNetworks.remove(networkId);
            } else {
                failingNetworks.put(
                        networkId,
                        new FailureState(
                                nextStep,
                                currentTick + FAILURE_STATE_TICKS
                        )
                );
            }
        }

        if (failingNetworks.isEmpty()) {
            FAILING_NETWORKS.remove(world, failingNetworks);
        }
    }

    private static void failNetwork(
            @Nonnull World world,
            long networkId
    ) {
        FAILING_NETWORKS
                .computeIfAbsent(
                        world,
                        ignored -> new ConcurrentHashMap<>()
                )
                .putIfAbsent(
                        networkId,
                        new FailureState(0, world.getTick())
                );
    }

    private record FailureState(
            int step,
            long nextChangeTick
    ) {
    }
}
