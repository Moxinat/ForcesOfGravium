package dev.moxinat.forcesofgravium.block.gravity;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.math.Axis;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.UseBlockEvent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockOperations;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.moxinat.forcesofgravium.signal.SignalState;
import dev.moxinat.forcesofgravium.dispatcher.ConnectableVisualDispatcher;
import dev.moxinat.forcesofgravium.network.ConnectableNetworkManager;
import dev.moxinat.forcesofgravium.signal.ConnectableSignalRecalculator;
import dev.moxinat.forcesofgravium.spatial.ConnectableNeighborResolver;
import dev.moxinat.forcesofgravium.signal.ConnectablePropagationScheduler;
import dev.moxinat.forcesofgravium.data.Nodes;
import org.joml.Vector3i;

import javax.annotation.Nonnull;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class CurveCasedGravityPowderRotationSystem {

    private static final Axis LOCAL_INTERACTION_ROTATION_AXIS = Axis.Y;

    private CurveCasedGravityPowderRotationSystem() {
    }

    public static final class UseSystem extends EntityEventSystem<EntityStore, UseBlockEvent.Post> {

        public UseSystem() {
            super(UseBlockEvent.Post.class);
        }

        @Override
        public @Nonnull Query<EntityStore> getQuery() {
            return Player.getComponentType();
        }

        @Override
        public void handle(int index, @Nonnull ArchetypeChunk<EntityStore> chunk, @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer, @Nonnull UseBlockEvent.Post event) {
            BlockType blockType = event.getBlockType();

            Ref<EntityStore> entityRef = chunk.getReferenceTo(index);
            Player player = store.getComponent(entityRef, Player.getComponentType());
            if (player == null || player.getWorld() == null) {
                return;
            }

            World world = player.getWorld();
            Vector3i position = new Vector3i(event.getTargetBlock());
            Nodes.Node node = Nodes.get(world, position);

            if (node == null || !NodeTypes.CURVE_CASED_GRAVITY_POWDER
                    .blockId()
                    .equals(node.blockId())) {
                return;
            }

            RotationTuple nextRotation =
                    rotateAroundLocalAxis(
                            node.rotation(),
                            LOCAL_INTERACTION_ROTATION_AXIS
                    );

            ChunkStore chunkStore = world.getChunkStore();
            Ref<ChunkStore> sectionRef =
                    chunkStore.getChunkSectionReferenceAtBlock(
                            position.x(),
                            position.y(),
                            position.z()
                    );

            if (sectionRef == null) {
                return;
            }

            int blockId = BlockType.getAssetMap().getIndex(blockType.getId());
            if (blockId < 0) {
                return;
            }

            long oldNetworkId = node.networkId();

            Set<Vector3i> formerNetworkNeighbors =
                    ConnectableNeighborResolver.allNetworkNeighbors(
                            world,
                            position
                    );

            Set<Vector3i> formerForwardNeighbors =
                    ConnectableNeighborResolver.allForwardSignalNeighbors(
                            world,
                            position
                    );

            Map<Vector3i, SignalState> oldInstantStates =
                    new LinkedHashMap<>();

            oldInstantStates.put(
                    position,
                    node.instantState()
            );

            for (Vector3i adjacent :
                    ConnectableNeighborResolver.positionsAround(position)) {

                if (adjacent.equals(position)) {
                    continue;
                }

                Nodes.Node adjacentNode =
                        Nodes.get(world, adjacent);

                if (adjacentNode != null) {
                    oldInstantStates.put(
                            adjacent,
                            adjacentNode.instantState()
                    );
                }
            }

            Nodes.remove(
                    world,
                    position
            );

            ConnectableNetworkManager.onNodeBroken(
                    world,
                    oldNetworkId,
                    formerNetworkNeighbors
            );

            Nodes.put(
                    world,
                    node
                            .withRotation(nextRotation)
                            .withNetworkId(Nodes.Node.NO_NETWORK)
            );

            BlockOperations.setBlock(
                    chunkStore,
                    sectionRef,
                    position.x(),
                    position.y(),
                    position.z(),
                    blockId,
                    blockType,
                    nextRotation.index(),
                    0,
                    0
            );

            ConnectableNetworkManager.onNodePlaced(
                    world,
                    position
            );

            ConnectableSignalRecalculator.recompute(
                    world,
                    position
            );

            for (Vector3i formerForwardNeighbor :
                    formerForwardNeighbors) {

                ConnectableSignalRecalculator.recompute(
                        world,
                        formerForwardNeighbor
                );
            }

            for (Map.Entry<Vector3i, SignalState> entry :
                    oldInstantStates.entrySet()) {

                Nodes.Node currentNode =
                        Nodes.get(
                                world,
                                entry.getKey()
                        );

                if (currentNode == null) {
                    continue;
                }

                if (currentNode.instantState()
                        != entry.getValue()) {

                    ConnectablePropagationScheduler
                            .scheduleAdoption(
                                    world,
                                    entry.getKey()
                            );
                }
            }

            ConnectableVisualDispatcher.refreshTopologyAround(world, position);
        }

        private static @Nonnull RotationTuple rotateAroundLocalAxis(@Nonnull RotationTuple currentRotation, @Nonnull Axis localAxis) {
            return RotationTuple.compose(currentRotation, localRotationStep(localAxis));
        }

        private static @Nonnull RotationTuple localRotationStep(@Nonnull Axis localAxis) {
            return switch (localAxis) {
                case X -> RotationTuple.of(Rotation.None, Rotation.Ninety, Rotation.None);
                case Y -> RotationTuple.of(Rotation.Ninety, Rotation.None, Rotation.None);
                case Z -> RotationTuple.of(Rotation.None, Rotation.None, Rotation.Ninety);
            };
        }
    }
}
