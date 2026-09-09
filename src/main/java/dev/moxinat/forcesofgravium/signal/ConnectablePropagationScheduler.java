package dev.moxinat.forcesofgravium.signal;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.section.ChunkSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import dev.moxinat.forcesofgravium.ForcesOfGraviumPlugin;
import dev.moxinat.forcesofgravium.data.NodeComponent;
import dev.moxinat.forcesofgravium.data.Nodes;
import dev.moxinat.forcesofgravium.data.SignalRuntimeResource;
import dev.moxinat.forcesofgravium.dispatcher.ConnectableVisualDispatcher;
import dev.moxinat.forcesofgravium.dispatcher.NodeControlDispatcher;
import dev.moxinat.forcesofgravium.dispatcher.NodeStateDispatcher;
import dev.moxinat.forcesofgravium.spatial.ConnectableNeighborResolver;
import org.joml.Vector3i;

import javax.annotation.Nonnull;
import java.util.LinkedHashSet;
import java.util.Set;

public final class ConnectablePropagationScheduler {

    private static final boolean DEBUG_WAVE = true;
    private static long debugTickCounter;

    private ConnectablePropagationScheduler() {
    }

    public static void scheduleAdoption(
            @Nonnull World world,
            @Nonnull Vector3i position
    ) {
        SignalRuntimeResource signal = signalResource(world);
        boolean added = signal.currentWave().add(new Vector3i(position));

        debug(
                "SCHEDULE pos=" + position
                        + " added=" + added
                        + " current=" + signal.currentWave()
                        + " next=" + signal.nextWave()
        );
    }

    public static void tickWorld(@Nonnull World world) {
        SignalRuntimeResource signal =
                signalResource(world);

        if (signal.currentWave().isEmpty()) {
            if (!signal.nextWave().isEmpty()) {
                debug(
                        "TICK SKIP current=[] BUT next="
                                + signal.nextWave()
                );
            }
            return;
        }

        long debugTick = ++debugTickCounter;

        debug(
                debugTick,
                "TICK BEGIN current=" + signal.currentWave()
                        + " next=" + signal.nextWave()
        );

        Set<Vector3i> currentWave =
                new LinkedHashSet<>(
                        signal.currentWave()
                );

        signal.currentWave().clear();

        debug(
                debugTick,
                "SNAPSHOT=" + currentWave
                        + " afterClear current=" + signal.currentWave()
                        + " next=" + signal.nextWave()
        );

        for (Vector3i position : currentWave) {
            debug(
                    debugTick,
                    "PROCESS BEGIN pos=" + position
                            + " resourceCurrent=" + signal.currentWave()
                            + " resourceNext=" + signal.nextWave()
            );

            adoptInstantStateAndScheduleNeighbors(
                    world,
                    position,
                    debugTick
            );

            debug(
                    debugTick,
                    "PROCESS END pos=" + position
                            + " resourceCurrent=" + signal.currentWave()
                            + " resourceNext=" + signal.nextWave()
            );
        }

        debug(
                debugTick,
                "BEFORE PROMOTE current=" + signal.currentWave()
                        + " next=" + signal.nextWave()
        );

        if (!signal.nextWave().isEmpty()) {

            signal.currentWave().addAll(
                    signal.nextWave()
            );

            debug(
                    debugTick,
                    "PROMOTED next into current -> current="
                            + signal.currentWave()
                            + " nextBeforeClear=" + signal.nextWave()
            );

            signal.nextWave().clear();
        }

        debug(
                debugTick,
                "TICK END current=" + signal.currentWave()
                        + " next=" + signal.nextWave()
        );

        if (signal.currentWave().isEmpty()
                && signal.nextWave().isEmpty()) {
            debug(debugTick, "WAVE EMPTY AFTER TICK");
        }
    }

    private static void adoptInstantStateAndScheduleNeighbors(
            @Nonnull World world,
            @Nonnull Vector3i position,
            long debugTick
    ) {
        if (!isBubbleLoaded(world, position, debugTick)) {
            SignalRuntimeResource signal = signalResource(world);
            boolean added = signal.currentWave().add(new Vector3i(position));

            debug(
                    debugTick,
                    "WAIT BUBBLE pos=" + position
                            + " readded=" + added
                            + " current=" + signal.currentWave()
            );
            return;
        }

        NodeComponent node = nodeAt(world, position);

        if (node == null) {
            SignalRuntimeResource signal = signalResource(world);
            boolean added = signal.currentWave().add(new Vector3i(position));

            debug(
                    debugTick,
                    "WAIT NODE pos=" + position
                            + " readded=" + added
                            + " current=" + signal.currentWave()
            );
            return;
        }

        debug(
                debugTick,
                "NODE FOUND pos=" + position
                        + " dirty=" + node.dirty()
                        + " instant=" + node.instantState()
                        + " effective=" + node.effectiveState()
                        + " invert=" + node.invertEnabled()
        );

        if (!node.dirty()) {
            debug(
                    debugTick,
                    "DROP CLEAN pos=" + position
                            + " instant=" + node.instantState()
                            + " effective=" + node.effectiveState()
            );
            return;
        }

        SignalState previousEffectiveState = node.effectiveState();

        debug(
                debugTick,
                "ADOPT BEFORE pos=" + position
                        + " instant=" + node.instantState()
                        + " effective=" + node.effectiveState()
                        + " dirty=" + node.dirty()
        );

        Nodes.mutate(
                world,
                position,
                NodeComponent::adoptInstantState
        );

        debug(
                debugTick,
                "ADOPT AFTER pos=" + position
                        + " instant=" + node.instantState()
                        + " effective=" + node.effectiveState()
                        + " dirty=" + node.dirty()
                        + " changed="
                        + (node.effectiveState() != previousEffectiveState)
        );

        if (node.effectiveState() != previousEffectiveState) {

            debug(debugTick, "DISPATCH STATE pos=" + position);
            NodeStateDispatcher.dispatch(world, position);

            ConnectableVisualDispatcher.refreshAt(world, position);

            if (node.invertEnabled()) {
                Set<Vector3i> inverterForwardNeighbors =
                        ConnectableNeighborResolver.allForwardSignalNeighbors(
                                world,
                                position
                        );

                debug(
                        debugTick,
                        "INVERTER RECOMPUTE pos=" + position
                                + " forward=" + inverterForwardNeighbors
                );

                for (Vector3i forwardNeighbor : inverterForwardNeighbors) {
                    ConnectableSignalRecalculator.recompute(
                            world,
                            forwardNeighbor
                    );
                }
            }

            Set<Vector3i> controlNeighbors =
                    ConnectableNeighborResolver.allControlNeighbors(
                            world,
                            position
                    );

            if (!controlNeighbors.isEmpty()) {
                debug(
                        debugTick,
                        "CONTROL pos=" + position
                                + " neighbors=" + controlNeighbors
                );
            }

            for (Vector3i controlNeighbor : controlNeighbors) {

                NodeControlDispatcher.dispatch(world, controlNeighbor, position);
                ConnectableVisualDispatcher.refreshAt(world, controlNeighbor);
            }
        }

        Set<Vector3i> firstForwardNeighbors =
                ConnectableNeighborResolver.allForwardSignalNeighbors(
                        world,
                        position
                );

        debug(
                debugTick,
                "FORWARD FIRST pos=" + position
                        + " neighbors=" + firstForwardNeighbors
        );

        for (Vector3i signalNeighbor : firstForwardNeighbors) {

            NodeComponent neighbor = nodeAt(world, signalNeighbor);

            debug(
                    debugTick,
                    "NEIGHBOR FIRST from=" + position
                            + " to=" + signalNeighbor
                            + " node=" + (neighbor != null)
                            + " dirty=" + (neighbor != null && neighbor.dirty())
                            + (neighbor == null
                            ? ""
                            : " instant=" + neighbor.instantState()
                            + " effective=" + neighbor.effectiveState())
            );

            if (neighbor != null && neighbor.dirty()) {
                SignalRuntimeResource signal = signalResource(world);
                boolean added = signal.nextWave().add(new Vector3i(signalNeighbor));

                debug(
                        debugTick,
                        "ADD NEXT FIRST from=" + position
                                + " to=" + signalNeighbor
                                + " added=" + added
                                + " next=" + signal.nextWave()
                );
            }
        }

        Set<Vector3i> forwardNeighbors =
                ConnectableNeighborResolver.allForwardSignalNeighbors(
                        world,
                        position
                );

        debug(
                debugTick,
                "FORWARD SECOND pos=" + position
                        + " neighbors=" + forwardNeighbors
                        + " sameAsFirst="
                        + forwardNeighbors.equals(firstForwardNeighbors)
        );

        boolean scheduledAny = false;

        for (Vector3i signalNeighbor : forwardNeighbors) {

            NodeComponent neighbor = nodeAt(world, signalNeighbor);

            debug(
                    debugTick,
                    "NEIGHBOR SECOND from=" + position
                            + " to=" + signalNeighbor
                            + " node=" + (neighbor != null)
                            + " dirty=" + (neighbor != null && neighbor.dirty())
            );

            if (neighbor != null && neighbor.dirty()) {
                SignalRuntimeResource signal = signalResource(world);
                boolean added = signal.nextWave().add(new Vector3i(signalNeighbor));

                debug(
                        debugTick,
                        "ADD NEXT SECOND from=" + position
                                + " to=" + signalNeighbor
                                + " added=" + added
                                + " next=" + signal.nextWave()
                );

                scheduledAny = true;
            }
        }

        if (!scheduledAny && !forwardNeighbors.isEmpty()) {
            for (Vector3i signalNeighbor : forwardNeighbors) {

                NodeComponent neighbor = nodeAt(world, signalNeighbor);

                debug(
                        debugTick,
                        "STOP from=" + position
                                + " to=" + signalNeighbor
                                + " node=" + (neighbor != null)
                                + " dirty=" + (neighbor != null && neighbor.dirty())
                );
            }
        }
    }

    private static boolean isBubbleLoaded(
            @Nonnull World world,
            @Nonnull Vector3i position,
            long debugTick
    ) {
        var chunkStore = world.getChunkStore();

        Ref<ChunkStore> centerRef =
                chunkStore.getChunkSectionReferenceAtBlock(
                        position.x(),
                        position.y(),
                        position.z()
                );

        if (centerRef == null) {
            debug(
                    debugTick,
                    "BUBBLE FAIL centerRef=null pos=" + position
            );
            return false;
        }

        if (!centerRef.isValid()) {
            debug(
                    debugTick,
                    "BUBBLE FAIL centerRef invalid pos=" + position
            );
            return false;
        }

        ChunkSection centerSection =
                centerRef.getStore().getComponent(
                        centerRef,
                        ChunkSection.getComponentType()
                );

        if (centerSection == null) {
            debug(
                    debugTick,
                    "BUBBLE FAIL centerSection=null pos=" + position
            );
            return false;
        }

        int centerX = centerSection.getX();
        int centerY = centerSection.getY();
        int centerZ = centerSection.getZ();

        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {

                    int sectionX = centerX + dx;
                    int sectionY = centerY + dy;
                    int sectionZ = centerZ + dz;

                    Ref<ChunkStore> sectionRef =
                            chunkStore.getChunkSectionReference(
                                    sectionX,
                                    sectionY,
                                    sectionZ
                            );

                    if (sectionRef == null) {
                        debug(
                                debugTick,
                                "BUBBLE FAIL missing section=("
                                        + sectionX + ","
                                        + sectionY + ","
                                        + sectionZ + ")"
                                        + " center=("
                                        + centerX + ","
                                        + centerY + ","
                                        + centerZ + ")"
                                        + " pos=" + position
                        );
                        return false;
                    }

                    if (!sectionRef.isValid()) {
                        debug(
                                debugTick,
                                "BUBBLE FAIL invalid section=("
                                        + sectionX + ","
                                        + sectionY + ","
                                        + sectionZ + ")"
                                        + " pos=" + position
                        );
                        return false;
                    }

                    if (sectionRef.getStore().getComponent(
                            sectionRef,
                            ChunkStore.REGISTRY.getNonTickingComponentType()
                    ) != null) {
                        debug(
                                debugTick,
                                "BUBBLE FAIL nonTicking section=("
                                        + sectionX + ","
                                        + sectionY + ","
                                        + sectionZ + ")"
                                        + " pos=" + position
                        );
                        return false;
                    }
                }
            }
        }

        debug(
                debugTick,
                "BUBBLE OK pos=" + position
                        + " centerSection=("
                        + centerX + ","
                        + centerY + ","
                        + centerZ + ")"
        );

        return true;
    }

    public static void cancelPendingAdoption(
            World world,
            Vector3i position
    ) {
        SignalRuntimeResource signal =
                signalResource(world);

        boolean wasCurrent = signal.currentWave().contains(position);
        boolean wasNext = signal.nextWave().contains(position);

        boolean removedCurrent = signal.currentWave().remove(position);
        boolean removedNext = signal.nextWave().remove(position);

        debug(
                "CANCEL pos=" + position
                        + " wasCurrent=" + wasCurrent
                        + " wasNext=" + wasNext
                        + " removedCurrent=" + removedCurrent
                        + " removedNext=" + removedNext
                        + " current=" + signal.currentWave()
                        + " next=" + signal.nextWave()
        );
    }

    private static NodeComponent nodeAt(
            World world,
            Vector3i position
    ) {
        return BlockModule.getComponent(
                ForcesOfGraviumPlugin.NODE_COMPONENT_TYPE,
                world,
                position.x(),
                position.y(),
                position.z()
        );
    }

    private static SignalRuntimeResource signalResource(
            @Nonnull World world
    ) {
        return world
                .getChunkStore()
                .getStore()
                .getResource(
                        ForcesOfGraviumPlugin.SIGNAL_RESOURCE_TYPE
                );
    }

    private static void debug(
            long debugTick,
            @Nonnull String message
    ) {
        if (!DEBUG_WAVE) {
            return;
        }

        System.out.println(
                "[FoG Wave Debug][tick=" + debugTick + "] " + message
        );
    }

    private static void debug(@Nonnull String message) {
        if (!DEBUG_WAVE) {
            return;
        }

        System.out.println(
                "[FoG Wave Debug] " + message
        );
    }

}
