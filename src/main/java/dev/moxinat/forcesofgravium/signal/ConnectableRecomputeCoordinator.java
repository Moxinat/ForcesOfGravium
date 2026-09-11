package dev.moxinat.forcesofgravium.signal;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.WorldEventSystem;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.section.ChunkSection;
import com.hypixel.hytale.server.core.universe.world.events.ecs.SectionUnloadEvent;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import dev.moxinat.forcesofgravium.ForcesOfGraviumPlugin;
import dev.moxinat.forcesofgravium.data.NetworkResource;
import dev.moxinat.forcesofgravium.data.SignalRuntimeResource;
import org.joml.Vector3i;

import javax.annotation.Nonnull;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class ConnectableRecomputeCoordinator {

    private ConnectableRecomputeCoordinator() {
    }

    private static final Map<World, Set<Long>> ACTIVE_NETWORKS =
            new ConcurrentHashMap<>();

    private static final Map<World, Set<Long>> READY_NETWORKS =
            new ConcurrentHashMap<>();

    private static final Map<World, Set<Vector3i>> PINNED_SECTIONS =
            new ConcurrentHashMap<>();

    private static Set<Long> activeNetworks(
            @Nonnull World world
    ) {
        return ACTIVE_NETWORKS.computeIfAbsent(
                world,
                ignored -> ConcurrentHashMap.newKeySet()
        );
    }

    private static Set<Long> readyNetworks(
            @Nonnull World world
    ) {
        return READY_NETWORKS.computeIfAbsent(
                world,
                ignored -> ConcurrentHashMap.newKeySet()
        );
    }

    private static Set<Vector3i> pinnedSections(
            @Nonnull World world
    ) {
        return PINNED_SECTIONS.computeIfAbsent(
                world,
                ignored -> ConcurrentHashMap.newKeySet()
        );
    }

    public static void request(
            @Nonnull World world,
            @Nonnull Vector3i position
    ) {
        NetworkResource networks =
                networkResource(world);

        long networkId =
                networks.networkAt(position);

        if (networkId == NetworkResource.NO_NETWORK) {
            return;
        }

        if (isNetworkReady(world, networkId)) {
            ConnectableSignalRecalculator.recomputeLoaded(
                    world,
                    position
            );
            return;
        }

        SignalRuntimeResource signal =
                signalResource(world);

        signal.pendingRecomputes().add(
                new Vector3i(position)
        );

        activeNetworks(world).add(networkId);
    }


    public static void tickWorld(
            @Nonnull World world
    ) {
        SignalRuntimeResource signal =
                signalResource(world);

        if (signal.pendingRecomputes().isEmpty()) {
            return;
        }

        NetworkResource networks =
                networkResource(world);

        Set<Long> activeNetworks =
                activeNetworks(world);

        for (Vector3i position :
                new LinkedHashSet<>(
                        signal.pendingRecomputes()
                )) {

            long networkId =
                    networks.networkAt(position);

            if (networkId == NetworkResource.NO_NETWORK) {
                signal.pendingRecomputes().remove(position);
                continue;
            }

            activeNetworks.add(networkId);
        }

        for (long networkId :
                new LinkedHashSet<>(activeNetworks)) {

            startPendingRecompute(
                    world,
                    networkId
            );
        }
    }


    public static void shutdown() {
    }


    private static void startPendingRecompute(
            @Nonnull World world,
            long networkId
    ) {
        NetworkResource networks =
                networkResource(world);

        if (!networks.containsNetwork(networkId)) {
            activeNetworks(world).remove(networkId);
            return;
        }

        Set<Vector3i> sections =
                requiredSections(
                        world,
                        networkId
                );

        pinSections(
                world,
                sections
        );

        ChunkStore chunkStore =
                world.getChunkStore();

        CompletableFuture<?>[] loads =
                sections.stream()
                        .map(section ->
                                chunkStore
                                        .getChunkSectionReferenceAsync(
                                                section.x(),
                                                section.y(),
                                                section.z()
                                        )
                        )
                        .toArray(CompletableFuture[]::new);

        CompletableFuture
                .allOf(loads)
                .thenRun(() ->
                        readyNetworks(world)
                                .add(networkId)
                );
    }


    private static void finishPendingRecompute(
            @Nonnull World world,
            @Nonnull Vector3i position
    ) {
    }


    private static Set<Vector3i> requiredSections(
            @Nonnull World world,
            long networkId
    ) {
        NetworkResource networks =
                networkResource(world);

        Set<Vector3i> sections =
                new LinkedHashSet<>();

        for (Vector3i position :
                networks.members(networkId)) {

            sections.add(
                    new Vector3i(
                            ChunkUtil.chunkCoordinate(position.x()),
                            ChunkUtil.chunkCoordinate(position.y()),
                            ChunkUtil.chunkCoordinate(position.z())
                    )
            );
        }

        return sections;
    }

    private static void pinSections(
            @Nonnull World world,
            @Nonnull Set<Vector3i> sections
    ) {
        Set<Vector3i> pinned =
                pinnedSections(world);

        for (Vector3i section : sections) {
            pinned.add(
                    new Vector3i(section)
            );
        }
    }


    private static void unpinSections(
            @Nonnull World world,
            @Nonnull Set<Vector3i> sections
    ) {
        Set<Vector3i> pinned =
                pinnedSections(world);

        for (Vector3i section : sections) {
            pinned.remove(section);
        }

        if (pinned.isEmpty()) {
            PINNED_SECTIONS.remove(world);
        }
    }

    private static boolean isNetworkReady(
            @Nonnull World world,
            long networkId
    ) {
        Set<Vector3i> sections =
                requiredSections(
                        world,
                        networkId
                );

        // hier direkt prüfen,
        // ob jede Section geladen ist
        return false;
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

    private static NetworkResource networkResource(
            @Nonnull World world
    ) {
        return world
                .getChunkStore()
                .getStore()
                .getResource(
                        ForcesOfGraviumPlugin.NETWORK_RESOURCE_TYPE
                );
    }

    public static final class SectionUnloadSystem
            extends WorldEventSystem<ChunkStore, SectionUnloadEvent> {

        public SectionUnloadSystem() {
            super(SectionUnloadEvent.class);
        }

        @Override
        public void handle(
                @Nonnull Store<ChunkStore> store,
                @Nonnull CommandBuffer<ChunkStore> commandBuffer,
                @Nonnull SectionUnloadEvent event
        ) {
            World world =
                    store.getExternalData().getWorld();

            ChunkSection section =
                    event.getSection();

            Set<Vector3i> pinned =
                    PINNED_SECTIONS.get(world);

            if (pinned == null) {
                return;
            }

            Vector3i sectionPosition =
                    new Vector3i(
                            section.getX(),
                            section.getY(),
                            section.getZ()
                    );

            if (!pinned.contains(sectionPosition)) {
                return;
            }

            event.setCancelled(true);
            event.setResetKeepAlive(true);
        }
    }

}