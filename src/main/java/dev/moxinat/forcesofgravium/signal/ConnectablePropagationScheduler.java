package dev.moxinat.forcesofgravium.signal;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.section.ChunkSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import dev.moxinat.forcesofgravium.ForcesOfGraviumPlugin;
import dev.moxinat.forcesofgravium.data.NodeComponent;
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

    private ConnectablePropagationScheduler() {
    }

    public static void scheduleAdoption(
            @Nonnull World world,
            @Nonnull Vector3i position
    ) {
        signalResource(world)
                .currentWave()
                .add(new Vector3i(position));
    }

    public static void tickWorld(@Nonnull World world) {
        SignalRuntimeResource signal =
                signalResource(world);

        if (signal.currentWave().isEmpty()) {
            return;
        }

        Set<Vector3i> currentWave =
                new LinkedHashSet<>(
                        signal.currentWave()
                );

        signal.currentWave().clear();

        for (Vector3i position : currentWave) {
            adoptInstantStateAndScheduleNeighbors(world, position);
        }

        if (!signal.nextWave().isEmpty()) {

            signal.currentWave().addAll(
                    signal.nextWave()
            );

            signal.nextWave().clear();
        }
    }

    private static void adoptInstantStateAndScheduleNeighbors(
            @Nonnull World world,
            @Nonnull Vector3i position
    ) {
        if (!isBubbleLoaded(world, position)) {
            System.out.println("[FoG Wave] WAIT BUBBLE " + position);

            signalResource(world)
                    .currentWave()
                    .add(new Vector3i(position));
            return;
        }

        NodeComponent node = nodeAt(world, position);

        if (node == null) {
            System.out.println("[FoG Wave] WAIT NODE " + position);

            signalResource(world)
                    .currentWave()
                    .add(new Vector3i(position));
            return;
        }

        if (!node.dirty()) {
            System.out.println(
                    "[FoG Wave] DROP CLEAN "
                            + position
                            + " instant=" + node.instantState()
                            + " effective=" + node.effectiveState()
            );
            return;
        }

        SignalState previousEffectiveState = node.effectiveState();

        node.adoptInstantState();

        if (node.effectiveState() != previousEffectiveState) {

            NodeStateDispatcher.dispatch(world, position);

            ConnectableVisualDispatcher.refreshAt(world, position);

            if (node.invertEnabled()) {
                for (Vector3i forwardNeighbor :
                        ConnectableNeighborResolver.allForwardSignalNeighbors(
                                world,
                                position
                        )) {

                    ConnectableSignalRecalculator.recompute(
                            world,
                            forwardNeighbor
                    );
                }
            }

            for (Vector3i controlNeighbor :
                    ConnectableNeighborResolver.allControlNeighbors(
                            world,
                            position
                    )) {

                NodeControlDispatcher.dispatch(world, controlNeighbor, position);
                ConnectableVisualDispatcher.refreshAt(world, controlNeighbor);
            }
        }

        System.out.println(
                "[FoG Wave] " + position
                        + " -> " + ConnectableNeighborResolver.allForwardSignalNeighbors(world, position)
        );

        for (Vector3i signalNeighbor :
                ConnectableNeighborResolver.allForwardSignalNeighbors(
                        world,
                        position
                )) {

            NodeComponent neighbor = nodeAt(world, signalNeighbor);

            if (neighbor != null && neighbor.dirty()) {
                signalResource(world)
                        .nextWave()
                        .add(new Vector3i(signalNeighbor));
            }
        }

        Set<Vector3i> forwardNeighbors =
                ConnectableNeighborResolver.allForwardSignalNeighbors(
                        world,
                        position
                );

        boolean scheduledAny = false;

        for (Vector3i signalNeighbor : forwardNeighbors) {

            NodeComponent neighbor = nodeAt(world, signalNeighbor);

            if (neighbor != null && neighbor.dirty()) {
                signalResource(world)
                        .nextWave()
                        .add(new Vector3i(signalNeighbor));

                scheduledAny = true;
            }
        }

        if (!scheduledAny && !forwardNeighbors.isEmpty()) {
            for (Vector3i signalNeighbor : forwardNeighbors) {

                NodeComponent neighbor = nodeAt(world, signalNeighbor);

                System.out.println(
                        "[FoG Wave] STOP "
                                + position
                                + " -> " + signalNeighbor
                                + " node=" + (neighbor != null)
                                + " dirty=" + (neighbor != null && neighbor.dirty())
                );
            }
        }
    }

    private static boolean isBubbleLoaded(
            @Nonnull World world,
            @Nonnull Vector3i position
    ) {
        var chunkStore = world.getChunkStore();

        Ref<ChunkStore> centerRef =
                chunkStore.getChunkSectionReferenceAtBlock(
                        position.x(),
                        position.y(),
                        position.z()
                );

        if (centerRef == null || !centerRef.isValid()) {
            return false;
        }

        ChunkSection centerSection =
                centerRef.getStore().getComponent(
                        centerRef,
                        ChunkSection.getComponentType()
                );

        if (centerSection == null) {
            return false;
        }

        int centerX = centerSection.getX();
        int centerY = centerSection.getY();
        int centerZ = centerSection.getZ();

        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {

                    Ref<ChunkStore> sectionRef =
                            chunkStore.getChunkSectionReference(
                                    centerX + dx,
                                    centerY + dy,
                                    centerZ + dz
                            );

                    if (sectionRef == null || !sectionRef.isValid()) {
                        return false;
                    }

                    if (sectionRef.getStore().getComponent(
                            sectionRef,
                            ChunkStore.REGISTRY.getNonTickingComponentType()
                    ) != null) {
                        return false;
                    }
                }
            }
        }

        return true;
    }

    public static void cancelPendingAdoption(
            World world,
            Vector3i position
    ) {
        SignalRuntimeResource signal =
                signalResource(world);

        signal.currentWave().remove(position);
        signal.nextWave().remove(position);
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

}
