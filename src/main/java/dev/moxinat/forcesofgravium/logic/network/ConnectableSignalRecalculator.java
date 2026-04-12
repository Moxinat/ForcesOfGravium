package dev.moxinat.forcesofgravium.logic.network;

import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import dev.moxinat.forcesofgravium.data.GravityPowderBlockDataStore;
import dev.moxinat.forcesofgravium.data.InverterDataStore;
import dev.moxinat.forcesofgravium.data.InverterDataStore.InverterData;
import dev.moxinat.forcesofgravium.logic.gravity.GravityPowderStateCalculator;
import dev.moxinat.forcesofgravium.registry.ConnectableBlockRoles;
import dev.moxinat.forcesofgravium.registry.ConnectableRegistry;

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

    public static @Nonnull RecomputeResult recompute(@Nonnull World world) {
        return recompute(new WorldSignalAdapter(world));
    }

    public static @Nonnull RecomputeResult recompute(@Nonnull World world, @Nonnull Set<Vector3i> affectedPositions) {
        return recompute(new WorldSignalAdapter(world, affectedPositions));
    }

    public static @Nonnull RecomputeResult recompute(@Nonnull SignalAdapter adapter) {
        Objects.requireNonNull(adapter, "adapter");
        Map<Vector3i, Boolean> invertEnabled = new LinkedHashMap<>();
        Map<Vector3i, Boolean> toggleInputActive = new LinkedHashMap<>();
        for (Vector3i inverter : adapter.inverterPositions()) {
            invertEnabled.put(inverter, adapter.isInvertEnabled(inverter));
            toggleInputActive.put(inverter, adapter.isToggleInputActive(inverter));
        }

        PropagationResult propagation = propagate(adapter, invertEnabled);
        for (int remainingPasses = adapter.inverterPositions().size(); remainingPasses >= 0; remainingPasses--) {
            boolean toggled = false;
            Map<Vector3i, Boolean> nextToggleInputActive = new LinkedHashMap<>();
            for (Vector3i inverter : adapter.inverterPositions()) {
                boolean sideInputActive = hasSideInput(adapter, inverter, propagation.cableSignals(), propagation.inverterOutputs());
                boolean wasSideInputActive = toggleInputActive.getOrDefault(inverter, false);
                nextToggleInputActive.put(inverter, sideInputActive);
                if (sideInputActive && !wasSideInputActive) {
                    invertEnabled.put(inverter, !invertEnabled.getOrDefault(inverter, true));
                    toggled = true;
                }
            }
            toggleInputActive = nextToggleInputActive;
            if (!toggled || remainingPasses == 0) {
                break;
            }
            propagation = propagate(adapter, invertEnabled);
        }

        for (Map.Entry<Vector3i, SignalFlags> entry : propagation.cableSignals().entrySet()) {
            adapter.setCableSignals(entry.getKey(), entry.getValue().push(), entry.getValue().pull());
        }
        for (Map.Entry<Vector3i, SignalFlags> entry : propagation.inverterOutputs().entrySet()) {
            adapter.setInverterState(
                    entry.getKey(),
                    effectiveMode(entry.getValue()),
                    invertEnabled.getOrDefault(entry.getKey(), true),
                    toggleInputActive.getOrDefault(entry.getKey(), false)
            );
        }
        return new RecomputeResult(propagation.cableSignals(), propagation.inverterOutputs());
    }

    private static @Nonnull PropagationResult propagate(@Nonnull SignalAdapter adapter, @Nonnull Map<Vector3i, Boolean> invertEnabled) {
        Map<Vector3i, SignalFlags> cableSignals = new LinkedHashMap<>();
        Map<Vector3i, SignalFlags> inverterOutputs = new LinkedHashMap<>();
        for (Vector3i cable : adapter.cablePositions()) {
            cableSignals.put(cable, new SignalFlags(false, false));
        }
        for (Vector3i inverter : adapter.inverterPositions()) {
            inverterOutputs.put(inverter, new SignalFlags(false, false));
        }

        ArrayDeque<SignalStep> queue = new ArrayDeque<>();
        Set<SignalStep> visited = new LinkedHashSet<>();
        seedSources(adapter, queue, visited);

        while (!queue.isEmpty()) {
            SignalStep step = queue.removeFirst();
            if (adapter.isCable(step.position())) {
                if (!setSignal(cableSignals, step.position(), step.mode())) {
                    continue;
                }
                addCableOutputs(adapter, step, queue, visited);
                continue;
            }

            if (!adapter.isInverter(step.position())) {
                continue;
            }

            String outputMode = outputMode(step.mode(), invertEnabled.getOrDefault(step.position(), true));
            if (GravityPowderStateCalculator.MODE_OFF.equals(outputMode)) {
                continue;
            }
            if (!setSignal(inverterOutputs, step.position(), outputMode)) {
                continue;
            }
                addInverterOutput(adapter, step.position(), outputMode, queue, visited);
        }

        return new PropagationResult(cableSignals, inverterOutputs);
    }

    private static boolean hasSideInput(
            SignalAdapter adapter,
            Vector3i inverter,
            Map<Vector3i, SignalFlags> cableSignals,
            Map<Vector3i, SignalFlags> inverterOutputs
    ) {
        Vector3i back = adapter.inverterBack(inverter);
        Vector3i front = adapter.inverterFront(inverter);
        for (Vector3i neighbor : adapter.positionsAround(inverter)) {
            if (neighbor.equals(inverter) || neighbor.equals(back) || neighbor.equals(front)) {
                continue;
            }
            SignalFlags cableSignal = cableSignals.getOrDefault(neighbor, new SignalFlags(false, false));
            if (cableSignal.active()) {
                return true;
            }
            SignalFlags inverterSignal = inverterOutputs.getOrDefault(neighbor, new SignalFlags(false, false));
            if (inverterSignal.active() && inverter.equals(adapter.inverterFront(neighbor))) {
                return true;
            }
            if (adapter.hasSourceFacingPosition(neighbor, inverter)) {
                return true;
            }
        }
        return false;
    }

    private static void seedSources(SignalAdapter adapter, ArrayDeque<SignalStep> queue, Set<SignalStep> visited) {
        for (Vector3i cable : adapter.cablePositions()) {
            if (adapter.hasAdjacentSourceForCable(cable)) {
                enqueue(queue, visited, cable, GravityPowderStateCalculator.MODE_PUSH);
            }
        }
        for (Vector3i inverter : adapter.inverterPositions()) {
            if (adapter.hasSourceAtInverterBack(inverter)) {
                enqueue(queue, visited, inverter, GravityPowderStateCalculator.MODE_PUSH);
            }
        }
    }

    private static void addCableOutputs(SignalAdapter adapter, SignalStep step, ArrayDeque<SignalStep> queue, Set<SignalStep> visited) {
        for (Vector3i neighbor : adapter.positionsAround(step.position())) {
            if (neighbor.equals(step.position())) {
                continue;
            }
            if (adapter.isCable(neighbor)) {
                enqueue(queue, visited, neighbor, step.mode());
                continue;
            }
            if (adapter.isInverter(neighbor) && step.position().equals(adapter.inverterBack(neighbor))) {
                enqueue(queue, visited, neighbor, step.mode());
            }
        }
    }

    private static void addInverterOutput(SignalAdapter adapter, Vector3i inverter, String mode, ArrayDeque<SignalStep> queue, Set<SignalStep> visited) {
        Vector3i front = adapter.inverterFront(inverter);
        if (adapter.isCable(front)) {
            enqueue(queue, visited, front, mode);
            return;
        }
        if (adapter.isInverter(front) && inverter.equals(adapter.inverterBack(front))) {
            enqueue(queue, visited, front, mode);
        }
    }

    private static void enqueue(ArrayDeque<SignalStep> queue, Set<SignalStep> visited, Vector3i position, String mode) {
        SignalStep step = new SignalStep(position, mode);
        if (visited.add(step)) {
            queue.addLast(step);
        }
    }

    private static boolean setSignal(Map<Vector3i, SignalFlags> signals, Vector3i position, String mode) {
        SignalFlags current = signals.getOrDefault(position, new SignalFlags(false, false));
        SignalFlags next = switch (mode) {
            case GravityPowderStateCalculator.MODE_PUSH -> new SignalFlags(true, current.pull());
            case GravityPowderStateCalculator.MODE_PULL -> new SignalFlags(current.push(), true);
            default -> current;
        };
        signals.put(position, next);
        return !next.equals(current);
    }

    private static String outputMode(String mode, boolean invertEnabled) {
        if (!invertEnabled) {
            return mode;
        }
        if (GravityPowderStateCalculator.MODE_PUSH.equals(mode)) {
            return GravityPowderStateCalculator.MODE_PULL;
        }
        if (GravityPowderStateCalculator.MODE_PULL.equals(mode)) {
            return GravityPowderStateCalculator.MODE_PUSH;
        }
        return GravityPowderStateCalculator.MODE_OFF;
    }

    private static String effectiveMode(SignalFlags signals) {
        if (signals.push()) {
            return GravityPowderStateCalculator.MODE_PUSH;
        }
        if (signals.pull()) {
            return GravityPowderStateCalculator.MODE_PULL;
        }
        return GravityPowderStateCalculator.MODE_OFF;
    }

    public interface SignalAdapter {
        @Nonnull Set<Vector3i> cablePositions();

        @Nonnull Set<Vector3i> inverterPositions();

        boolean isCable(@Nonnull Vector3i position);

        boolean isInverter(@Nonnull Vector3i position);

        boolean hasAdjacentSourceForCable(@Nonnull Vector3i cable);

        boolean hasSourceAtInverterBack(@Nonnull Vector3i inverter);

        boolean hasSourceFacingPosition(@Nonnull Vector3i sourcePosition, @Nonnull Vector3i targetPosition);

        boolean isInvertEnabled(@Nonnull Vector3i inverter);

        boolean isToggleInputActive(@Nonnull Vector3i inverter);

        @Nonnull Vector3i inverterBack(@Nonnull Vector3i inverter);

        @Nonnull Vector3i inverterFront(@Nonnull Vector3i inverter);

        @Nonnull List<Vector3i> positionsAround(@Nonnull Vector3i position);

        void setCableSignals(@Nonnull Vector3i position, boolean push, boolean pull);

        void setInverterState(@Nonnull Vector3i position, @Nonnull String mode, boolean invertEnabled, boolean toggleInputActive);
    }

    public record SignalFlags(boolean push, boolean pull) {
        public boolean active() {
            return push || pull;
        }
    }

    public record RecomputeResult(
            @Nonnull Map<Vector3i, SignalFlags> cableSignals,
            @Nonnull Map<Vector3i, SignalFlags> inverterOutputs
    ) {
        public RecomputeResult {
            cableSignals = Map.copyOf(Objects.requireNonNull(cableSignals, "cableSignals"));
            inverterOutputs = Map.copyOf(Objects.requireNonNull(inverterOutputs, "inverterOutputs"));
        }
    }

    private record SignalStep(@Nonnull Vector3i position, @Nonnull String mode) {
        private SignalStep {
            Objects.requireNonNull(position, "position");
            Objects.requireNonNull(mode, "mode");
        }
    }

    private record PropagationResult(
            @Nonnull Map<Vector3i, SignalFlags> cableSignals,
            @Nonnull Map<Vector3i, SignalFlags> inverterOutputs
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
            this.cables = new LinkedHashSet<>(GravityPowderBlockDataStore.snapshotForWorld(world).keySet());
            this.inverters = new LinkedHashSet<>(InverterDataStore.snapshotForWorld(world).keySet());
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
            return cables.contains(position);
        }

        @Override
        public boolean isInverter(@Nonnull Vector3i position) {
            return inverters.contains(position);
        }

        @Override
        public boolean hasAdjacentSourceForCable(@Nonnull Vector3i cable) {
            return !ConnectableNeighborResolver.sourceNeighbors(world, cable, null).isEmpty();
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
        public boolean isInvertEnabled(@Nonnull Vector3i inverter) {
            InverterData data = InverterDataStore.get(world, inverter);
            return data == null || data.invertEnabled();
        }

        @Override
        public boolean isToggleInputActive(@Nonnull Vector3i inverter) {
            InverterData data = InverterDataStore.get(world, inverter);
            return data != null && data.toggleInputActive();
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
        public void setCableSignals(@Nonnull Vector3i position, boolean push, boolean pull) {
            GravityPowderBlockDataStore.setSignals(world, position, push, pull);
        }

        @Override
        public void setInverterState(@Nonnull Vector3i position, @Nonnull String mode, boolean invertEnabled, boolean toggleInputActive) {
            InverterDataStore.setState(world, position, mode, mode, invertEnabled, toggleInputActive);
        }
    }
}
