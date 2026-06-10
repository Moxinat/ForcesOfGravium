package dev.moxinat.forcesofgravium.connectable.propagation;

import dev.moxinat.forcesofgravium.connectable.spatial.ConnectableNeighborResolver;

import org.joml.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import dev.moxinat.forcesofgravium.connectable.core.ConnectableRuntimeAccessor;
import dev.moxinat.forcesofgravium.block.gravity.GravityPowderSpecialStateStore;
import dev.moxinat.forcesofgravium.block.inverter.InverterSpecialStateStore;
import dev.moxinat.forcesofgravium.block.inverter.InverterSpecialStateStore.InverterData;
import dev.moxinat.forcesofgravium.connectable.registry.ConnectableRegistry;

import javax.annotation.Nonnull;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class ConnectableSignalRecalculator {

    private ConnectableSignalRecalculator() {
    }

    public static void recompute(@Nonnull World world, @Nonnull Set<Vector3i> affectedPositions) {
        recompute(new WorldSignalAdapter(world, affectedPositions));
    }

    public static void recompute(@Nonnull SignalAdapter adapter) {
        Objects.requireNonNull(adapter, "adapter");
        Map<Vector3i, Boolean> invertEnabled = new LinkedHashMap<>();
        Map<Vector3i, SignalState> lastToggleInputModes = new LinkedHashMap<>();
        for (Vector3i inverter : adapter.inverterPositions()) {
            invertEnabled.put(inverter, adapter.isInvertEnabled(inverter));
            lastToggleInputModes.put(inverter, adapter.lastToggleInputMode(inverter));
        }

        PropagationResult propagation = propagate(adapter, invertEnabled);
        for (int remainingPasses = adapter.inverterPositions().size(); remainingPasses >= 0; remainingPasses--) {
            boolean toggled = false;
            Map<Vector3i, SignalState> nextToggleInputModes = new LinkedHashMap<>();
            for (Vector3i inverter : adapter.inverterPositions()) {
                SignalState sideInputMode = sideInputMode(adapter, inverter, propagation.inverterOutputs());
                SignalState previousSideInputMode = lastToggleInputModes.getOrDefault(inverter, SignalState.OFF);
                nextToggleInputModes.put(inverter, sideInputMode);
                if (sideInputMode != SignalState.OFF && sideInputMode != previousSideInputMode) {
                    invertEnabled.put(inverter, !invertEnabled.getOrDefault(inverter, true));
                    toggled = true;
                }
            }
            lastToggleInputModes = nextToggleInputModes;
            if (!toggled || remainingPasses == 0) {
                break;
            }
            propagation = propagate(adapter, invertEnabled);
        }

        for (Map.Entry<Vector3i, SignalState> entry : propagation.cableSignals().entrySet()) {
            adapter.setCableSignal(entry.getKey(), entry.getValue());
        }
        for (Map.Entry<Vector3i, SignalState> entry : propagation.inverterOutputs().entrySet()) {
            adapter.setInverterState(
                    entry.getKey(),
                    stateForSignal(entry.getValue()),
                    invertEnabled.getOrDefault(entry.getKey(), true),
                    lastToggleInputModes.getOrDefault(entry.getKey(), SignalState.OFF)
            );
        }
    }

    private static @Nonnull PropagationResult propagate(@Nonnull SignalAdapter adapter, @Nonnull Map<Vector3i, Boolean> invertEnabled) {
        Map<Vector3i, SignalState> cableSignals = new LinkedHashMap<>();
        Map<Vector3i, SignalState> inverterOutputs = new LinkedHashMap<>();
        for (Vector3i cable : adapter.cablePositions()) {
            cableSignals.put(cable, SignalState.OFF);
        }
        for (Vector3i inverter : adapter.inverterPositions()) {
            inverterOutputs.put(inverter, SignalState.OFF);
        }

        ArrayDeque<SignalStep> queue = new ArrayDeque<>();
        Set<SignalStep> visited = new LinkedHashSet<>();
        seedSources(adapter, queue, visited);

        while (!queue.isEmpty()) {
            SignalStep step = queue.removeFirst();
            if (adapter.isCable(step.position())) {
                boolean changed = setSignal(cableSignals, step.position(), step.signalState());
                if (!changed) {
                    continue;
                }
                addCableOutputs(adapter, step, queue, visited);
                continue;
            }

            if (!adapter.isInverter(step.position())) {
                continue;
            }

            SignalState outputState = outputSignalState(step.signalState(), invertEnabled.getOrDefault(step.position(), true));
            if (outputState == SignalState.OFF) {
                continue;
            }
            boolean changed = setSignal(inverterOutputs, step.position(), outputState);
            if (!changed) {
                continue;
            }
            addInverterOutput(adapter, step.position(), outputState, queue, visited);
        }

        return new PropagationResult(cableSignals, inverterOutputs);
    }

    private static @Nonnull SignalState sideInputMode(
            SignalAdapter adapter,
            Vector3i inverter,
            Map<Vector3i, SignalState> inverterOutputs
    ) {
        SignalState result = SignalState.OFF;
        Vector3i back = adapter.inverterBack(inverter);
        Vector3i front = adapter.inverterFront(inverter);
        for (Vector3i neighbor : adapter.positionsAround(inverter)) {
            if (neighbor.equals(inverter) || neighbor.equals(back) || neighbor.equals(front)) {
                continue;
            }
            SignalState cableSignal = adapter.isCable(neighbor)
                    && adapter.areMutuallyConnected(neighbor, inverter)
                    ? adapter.cableEffectiveSignal(neighbor)
                    : SignalState.OFF;
            if (isActive(cableSignal)) {
                result = merge(result, cableSignal);
            }
            SignalState inverterSignal = inverterOutputs.getOrDefault(neighbor, SignalState.OFF);
            if (isActive(inverterSignal) && inverter.equals(adapter.inverterFront(neighbor))) {
                result = merge(result, inverterSignal);
            }
            if (adapter.hasSourceFacingPosition(neighbor, inverter)) {
                result = merge(result, SignalState.PUSH);
            }
        }
        return result;
    }

    private static void seedSources(SignalAdapter adapter, ArrayDeque<SignalStep> queue, Set<SignalStep> visited) {
        for (Vector3i cable : adapter.cablePositions()) {
            if (adapter.hasAdjacentSourceForCable(cable)) {
                enqueue(queue, visited, cable, SignalState.PUSH);
            }
        }
        for (Vector3i inverter : adapter.inverterPositions()) {
            if (adapter.hasSourceAtInverterBack(inverter)) {
                enqueue(queue, visited, inverter, SignalState.PUSH);
            }
        }
    }

    private static void addCableOutputs(SignalAdapter adapter, SignalStep step, ArrayDeque<SignalStep> queue, Set<SignalStep> visited) {
        for (Vector3i neighbor : adapter.positionsAround(step.position())) {
            if (neighbor.equals(step.position())) {
                continue;
            }
            if (adapter.isCable(neighbor) && adapter.areMutuallyConnected(step.position(), neighbor)) {
                enqueue(queue, visited, neighbor, step.signalState());
                continue;
            }
            if (adapter.isInverter(neighbor)
                    && step.position().equals(adapter.inverterBack(neighbor))
                    && adapter.areMutuallyConnected(step.position(), neighbor)
                    && adapter.cableHasEffectiveSignal(step.position(), step.signalState())) {
                enqueue(queue, visited, neighbor, step.signalState());
            }
        }
    }

    private static void addInverterOutput(SignalAdapter adapter, Vector3i inverter, SignalState mode, ArrayDeque<SignalStep> queue, Set<SignalStep> visited) {
        Vector3i front = adapter.inverterFront(inverter);
        if (adapter.isCable(front) && adapter.areMutuallyConnected(inverter, front)) {
            enqueue(queue, visited, front, mode);
            return;
        }
        if (adapter.isInverter(front) && inverter.equals(adapter.inverterBack(front))) {
            enqueue(queue, visited, front, mode);
        }
    }

    private static void enqueue(ArrayDeque<SignalStep> queue, Set<SignalStep> visited, Vector3i position, SignalState mode) {
        SignalStep step = new SignalStep(position, mode);
        if (visited.add(step)) {
            queue.addLast(step);
        }
    }

    private static boolean setSignal(Map<Vector3i, SignalState> signals, Vector3i position, SignalState mode) {
        SignalState current = signals.getOrDefault(position, SignalState.OFF);
        SignalState next = merge(current, mode);
        signals.put(position, next);
        return !next.equals(current);
    }

    private static @Nonnull SignalState merge(@Nonnull SignalState current, @Nonnull SignalState incoming) {
        if (current == SignalState.PUSH || incoming == SignalState.PUSH) {
            return SignalState.PUSH;
        }
        if (current == SignalState.PULL || incoming == SignalState.PULL) {
            return SignalState.PULL;
        }
        return SignalState.OFF;
    }

    private static @Nonnull SignalState outputSignalState(@Nonnull SignalState signalState, boolean invertEnabled) {
        return invertEnabled ? signalState.inverted() : signalState;
    }

    private static boolean isActive(@Nonnull SignalState mode) {
        return mode != SignalState.OFF;
    }

    private static @Nonnull String stateForSignal(@Nonnull SignalState mode) {
        return switch (mode) {
            case PUSH -> GravityPowderSpecialStateStore.STATE_PUSH;
            case PULL -> GravityPowderSpecialStateStore.STATE_PULL;
            case OFF -> GravityPowderSpecialStateStore.STATE_OFF;
        };
    }

    public interface SignalAdapter {
        @Nonnull Set<Vector3i> cablePositions();

        @Nonnull Set<Vector3i> inverterPositions();

        boolean isCable(@Nonnull Vector3i position);

        boolean isInverter(@Nonnull Vector3i position);

        boolean hasAdjacentSourceForCable(@Nonnull Vector3i cable);

        boolean hasSourceAtInverterBack(@Nonnull Vector3i inverter);

        boolean hasSourceFacingPosition(@Nonnull Vector3i sourcePosition, @Nonnull Vector3i targetPosition);

        default boolean cableHasEffectiveSignal(@Nonnull Vector3i cable, @Nonnull SignalState state) {
            return true;
        }

        default @Nonnull SignalState cableEffectiveSignal(@Nonnull Vector3i cable) {
            return SignalState.OFF;
        }

        boolean isInvertEnabled(@Nonnull Vector3i inverter);

        @Nonnull SignalState lastToggleInputMode(@Nonnull Vector3i inverter);

        @Nonnull Vector3i inverterBack(@Nonnull Vector3i inverter);

        @Nonnull Vector3i inverterFront(@Nonnull Vector3i inverter);

        @Nonnull List<Vector3i> positionsAround(@Nonnull Vector3i position);

        default boolean areMutuallyConnected(@Nonnull Vector3i first, @Nonnull Vector3i second) {
            return true;
        }

        void setCableSignal(@Nonnull Vector3i position, @Nonnull SignalState mode);

        void setInverterState(@Nonnull Vector3i position, @Nonnull String mode, boolean invertEnabled, @Nonnull SignalState lastToggleInputMode);
    }

    private record SignalStep(@Nonnull Vector3i position, @Nonnull SignalState signalState) {
        private SignalStep {
            Objects.requireNonNull(position, "position");
            Objects.requireNonNull(signalState, "signalState");
        }
    }

    private record PropagationResult(
            @Nonnull Map<Vector3i, SignalState> cableSignals,
            @Nonnull Map<Vector3i, SignalState> inverterOutputs
    ) {
        private PropagationResult {
            cableSignals = Map.copyOf(Objects.requireNonNull(cableSignals, "cableSignals"));
            inverterOutputs = Map.copyOf(Objects.requireNonNull(inverterOutputs, "inverterOutputs"));
        }
    }

    private static final class WorldSignalAdapter implements SignalAdapter {
        private final World world;
        private final Set<Vector3i> cables;
        private final Set<Vector3i> inverters;

        private WorldSignalAdapter(World world) {
            this.world = Objects.requireNonNull(world, "world");
            this.cables = new LinkedHashSet<>(ConnectableRuntimeAccessor.carrierPositionsForWorld(world));
            this.inverters = new LinkedHashSet<>(ConnectableRuntimeAccessor.inverterPositionsForWorld(world));
        }

        private WorldSignalAdapter(World world, Set<Vector3i> affectedPositions) {
            this(world);
            Objects.requireNonNull(affectedPositions, "affectedPositions");
            this.cables.retainAll(affectedPositions);
            this.inverters.retainAll(affectedPositions);
        }

        @Override
        public @Nonnull Set<Vector3i> cablePositions() {
            return Set.copyOf(cables);
        }

        @Override
        public @Nonnull Set<Vector3i> inverterPositions() {
            return Set.copyOf(inverters);
        }

        @Override
        public boolean isCable(@Nonnull Vector3i position) {
            if (!cables.contains(position)) {
                return false;
            }

            BlockType blockType = world.getBlockType(position.x(), position.y(), position.z());
            return blockType != null && ConnectableRegistry.isGravityPowderCarrierId(blockType.getId());
        }

        @Override
        public boolean isInverter(@Nonnull Vector3i position) {
            if (!inverters.contains(position)) {
                return false;
            }

            BlockType blockType = world.getBlockType(position.x(), position.y(), position.z());
            return blockType != null && ConnectableRegistry.isInverterId(blockType.getId());
        }

        @Override
        public boolean hasAdjacentSourceForCable(@Nonnull Vector3i cable) {
            for (Vector3i source : ConnectableNeighborResolver.sourceNeighbors(world, cable, null)) {
                if (ConnectableNeighborResolver.hasConnectableSideFacing(world, cable, source)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean hasSourceAtInverterBack(@Nonnull Vector3i inverter) {
            Vector3i back = inverterBack(inverter);
            return hasSourceFacingPosition(back, inverter);
        }

        @Override
        public boolean hasSourceFacingPosition(@Nonnull Vector3i sourcePosition, @Nonnull Vector3i targetPosition) {
            return ConnectableNeighborResolver.isSourceNeighborOf(world, sourcePosition, targetPosition);
        }

        @Override
        public boolean cableHasEffectiveSignal(@Nonnull Vector3i cable, @Nonnull SignalState state) {
            return ConnectableRuntimeAccessor.effectiveState(world, cable).equals(stateForSignal(state));
        }

        @Override
        public @Nonnull SignalState cableEffectiveSignal(@Nonnull Vector3i cable) {
            return signalForState(ConnectableRuntimeAccessor.effectiveState(world, cable));
        }

        @Override
        public boolean isInvertEnabled(@Nonnull Vector3i inverter) {
            return ConnectableRuntimeAccessor.invertEnabled(world, inverter);
        }

        @Override
        public @Nonnull SignalState lastToggleInputMode(@Nonnull Vector3i inverter) {
            InverterData data = InverterSpecialStateStore.get(world, inverter);
            if (data == null) {
                return SignalState.OFF;
            }
            return signalForState(data.lastToggleInputMode());
        }

        @Override
        public @Nonnull Vector3i inverterBack(@Nonnull Vector3i inverter) {
            return ConnectableNeighborResolver.adjacentPositionForLocalSide(world, inverter, ConnectableRegistry.SIDE_BACK);
        }

        @Override
        public @Nonnull Vector3i inverterFront(@Nonnull Vector3i inverter) {
            return ConnectableNeighborResolver.adjacentPositionForLocalSide(world, inverter, ConnectableRegistry.SIDE_FRONT);
        }

        @Override
        public @Nonnull List<Vector3i> positionsAround(@Nonnull Vector3i position) {
            return ConnectableNeighborResolver.positionsAround(position);
        }

        @Override
        public boolean areMutuallyConnected(@Nonnull Vector3i first, @Nonnull Vector3i second) {
            return ConnectableNeighborResolver.areMutuallyConnected(world, first, second);
        }

        @Override
        public void setCableSignal(@Nonnull Vector3i position, @Nonnull SignalState mode) {
            String previousInstantState = ConnectableRuntimeAccessor.instantState(world, position);
            ConnectableRuntimeAccessor.setInstantState(world, position, stateForSignal(mode));
            if (!ConnectableRuntimeAccessor.instantState(world, position).equals(previousInstantState)) {
                ConnectableRuntimeAccessor.setDirty(world, position, true);
            }
        }

        @Override
        public void setInverterState(@Nonnull Vector3i position, @Nonnull String mode, boolean invertEnabled, @Nonnull SignalState lastToggleInputMode) {
            String previousMode = ConnectableRuntimeAccessor.instantState(world, position);
            String outputMode = mode;
            ConnectableRuntimeAccessor.setInverterState(world, position, outputMode, outputMode, invertEnabled, stateForSignal(lastToggleInputMode));
            if (!outputMode.equals(previousMode)) {
                ConnectableRuntimeAccessor.setDirty(world, position, true);
            }
        }
    }

    private static @Nonnull SignalState signalForState(@Nonnull String state) {
        return switch (GravityPowderSpecialStateStore.normalizeState(state)) {
            case GravityPowderSpecialStateStore.STATE_PUSH -> SignalState.PUSH;
            case GravityPowderSpecialStateStore.STATE_PULL -> SignalState.PULL;
            default -> SignalState.OFF;
        };
    }
}
