package dev.moxinat.forcesofgravium.block.gravity;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.spatial.KDTree;
import com.hypixel.hytale.component.spatial.SpatialResource;
import com.hypixel.hytale.component.spatial.SpatialSystem;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.math.iterator.BlockIterator;
import com.hypixel.hytale.protocol.BlockMaterial;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.modules.entity.DespawnComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.modules.entity.item.ItemPhysicsSystem;
import com.hypixel.hytale.server.core.modules.entity.item.ItemPrePhysicsSystem;
import com.hypixel.hytale.server.core.modules.physics.component.Velocity;
import com.hypixel.hytale.server.core.modules.time.TimeResource;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.moxinat.forcesofgravium.ForcesOfGraviumPlugin;
import dev.moxinat.forcesofgravium.registry.ConnectableRegistry;
import dev.moxinat.forcesofgravium.spatial.ConnectableNeighborResolver;
import org.joml.Vector3d;
import org.joml.Vector3i;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class CableItemTransportSystem {

    private static final Query<ChunkStore> CABLE_QUERY =
            Query.and(
                    ForcesOfGraviumPlugin.NODE_COMPONENT_TYPE,
                    BlockModule.BlockStateInfo.getComponentType()
            );

    public static ResourceType<
            ChunkStore,
            SpatialResource<Ref<ChunkStore>, ChunkStore>
            > CABLE_SPATIAL_RESOURCE_TYPE;

    private static final Map<Ref<EntityStore>, Double> PRE_GRAVITY_Y =
            new ConcurrentHashMap<>();

    public static void register(
            @Nonnull ComponentRegistryProxy<ChunkStore> registry
    ) {
        CABLE_SPATIAL_RESOURCE_TYPE =
                registry.registerSpatialResource(
                        () -> new KDTree<>(Ref::isValid)
                );

        registry.registerSystem(
                new CableSpatialSystem(
                        CABLE_SPATIAL_RESOURCE_TYPE
                )
        );
    }

    private static Vector3d targetForSide(
            Vector3d center,
            ConnectableNeighborResolver.WorldSide side
    ) {
        return switch (side) {
            case EAST -> new Vector3d(center.x() + 0.75, center.y(), center.z());
            case WEST -> new Vector3d(center.x() - 0.75, center.y(), center.z());
            case UP -> new Vector3d(center.x(), center.y() + 0.75, center.z());
            case DOWN -> new Vector3d(center.x(), center.y() - 0.75, center.z());
            case SOUTH -> new Vector3d(center.x(), center.y(), center.z() + 0.75);
            case NORTH -> new Vector3d(center.x(), center.y(), center.z() - 0.75);
        };
    }

    public static final class CableSpatialSystem
            extends SpatialSystem<ChunkStore> {

        public CableSpatialSystem(
                @Nonnull ResourceType<
                                        ChunkStore,
                                        SpatialResource<Ref<ChunkStore>, ChunkStore>
                                        > resourceType
        ) {
            super(resourceType);
        }

        @Override
        public @Nonnull Query<ChunkStore> getQuery() {
            return CABLE_QUERY;
        }

        @Override
        public Vector3d getPosition(
                @Nonnull ArchetypeChunk<ChunkStore> archetypeChunk,
                int index
        ) {
            BlockModule.BlockStateInfo blockStateInfo =
                    archetypeChunk.getComponent(
                            index,
                            BlockModule.BlockStateInfo.getComponentType()
                    );

            if (blockStateInfo == null) {
                return null;
            }

            Ref<ChunkStore> sectionRef =
                    blockStateInfo.getSectionRef();

            if (!sectionRef.isValid()) {
                return null;
            }

            BlockSection blockSection =
                    sectionRef.getStore().getComponent(
                            sectionRef,
                            BlockSection.getComponentType()
                    );

            if (blockSection == null) {
                return null;
            }

            int blockId =
                    blockSection.get(
                            blockStateInfo.getIndex()
                    );

            BlockType blockType =
                    BlockType.getAssetMap()
                            .getAsset(blockId);

            if (blockType == null) {
                return null;
            }

            String rawBlockId =
                    ConnectableRegistry.rawBlockId(
                            blockType.getId()
                    );

            if (!ConnectableRegistry.GRAVITY_POWDER_BLOCK_ID.equals(rawBlockId)) {
                return null;
            }

            Vector3i position =
                    new Vector3i();

            if (!blockStateInfo.fillWorldPos(position)) {
                return null;
            }

            return new Vector3d(
                    position.x() + 0.5,
                    position.y() + 0.5,
                    position.z() + 0.5
            );
        }
    }

    public static final class PreGravityVelocityCaptureSystem
            extends EntityTickingSystem<EntityStore> {

        private static final Query<EntityStore> QUERY =
                Query.and(
                        ItemComponent.getComponentType(),
                        Velocity.getComponentType()
                );

        @Override
        public @Nonnull Query<EntityStore> getQuery() {
            return QUERY;
        }

        @Override
        public @Nonnull Set<Dependency<EntityStore>> getDependencies() {
            return Set.of(
                    new SystemDependency<>(
                            Order.BEFORE,
                            ItemPrePhysicsSystem.class
                    )
            );
        }

        @Override
        public void tick(
                float dt,
                int index,
                @Nonnull ArchetypeChunk<EntityStore> chunk,
                @Nonnull Store<EntityStore> store,
                @Nonnull CommandBuffer<EntityStore> commandBuffer
        ) {
            Velocity velocity =
                    chunk.getComponent(
                            index,
                            Velocity.getComponentType()
                    );

            if (velocity == null) {
                return;
            }

            Ref<EntityStore> ref =
                    chunk.getReferenceTo(index);

            PRE_GRAVITY_Y.put(
                    ref,
                    velocity.getY()
            );
        }
    }

    public static final class ItemTransportSystem
            extends EntityTickingSystem<EntityStore> {

        private static final Query<EntityStore> QUERY =
                Query.and(
                        ItemComponent.getComponentType(),
                        TransformComponent.getComponentType(),
                        Velocity.getComponentType()
                );

        @Override
        public @Nonnull Query<EntityStore> getQuery() {
            return QUERY;
        }

        @Override
        public @Nonnull Set<Dependency<EntityStore>> getDependencies() {
            return Set.of(
                    new SystemDependency<>(
                            Order.AFTER,
                            ItemPrePhysicsSystem.class
                    ),
                    new SystemDependency<>(
                            Order.BEFORE,
                            ItemPhysicsSystem.class
                    )
            );
        }

        @Override
        public void tick(
                float dt,
                int index,
                @Nonnull ArchetypeChunk<EntityStore> chunk,
                @Nonnull Store<EntityStore> store,
                @Nonnull CommandBuffer<EntityStore> commandBuffer
        ) {
            TransformComponent transform =
                    chunk.getComponent(
                            index,
                            TransformComponent.getComponentType()
                    );

            if (transform == null) {
                return;
            }

            Vector3d itemPosition =
                    transform.getPosition();

            World world =
                    store.getExternalData().getWorld();

            SpatialResource<Ref<ChunkStore>, ChunkStore> cableSpatial =
                    world.getChunkStore()
                            .getStore()
                            .getResource(CABLE_SPATIAL_RESOURCE_TYPE);

            List<Ref<ChunkStore>> nearbyCables =
                    new ArrayList<>();

            cableSpatial
                    .getSpatialStructure()
                    .collect(
                            itemPosition,
                            1.5,
                            nearbyCables
                    );

            Velocity velocity =
                    chunk.getComponent(
                            index,
                            Velocity.getComponentType()
                    );

            if (velocity == null) {
                return;
            }

            ItemComponent itemComponent =
                    chunk.getComponent(
                            index,
                            ItemComponent.getComponentType()
                    );

            DespawnComponent despawnComponent =
                    chunk.getComponent(
                            index,
                            DespawnComponent.getComponentType()
                    );

            Ref<EntityStore> itemRef =
                    chunk.getReferenceTo(index);

            for (Ref<ChunkStore> cableRef : nearbyCables) {

                if (!cableRef.isValid()) {
                    continue;
                }

                Store<ChunkStore> cableStore =
                        cableRef.getStore();

                BlockModule.BlockStateInfo blockStateInfo =
                        cableStore.getComponent(
                                cableRef,
                                BlockModule.BlockStateInfo.getComponentType()
                        );

                if (blockStateInfo == null) {
                    continue;
                }

                Vector3i cablePosition =
                        new Vector3i();

                if (!blockStateInfo.fillWorldPos(
                        cableStore,
                        cablePosition
                )) {
                    continue;
                }

                BlockType blockType =
                        world.getBlockType(
                                cablePosition.x(),
                                cablePosition.y(),
                                cablePosition.z()
                        );

                if (blockType == null) {
                    continue;
                }

                String stateId =
                        blockType.getId();

                if (!hasLineOfSight(world, itemPosition, cablePosition)) {
                    continue;
                }

                // PULL has priority.
                if (stateId.endsWith("Pull")) {

                    velocity.setZero();

                    if (itemComponent != null) {
                        TimeResource timeResource =
                                store.getResource(
                                        TimeResource.getResourceType()
                                );

                        float lifetime =
                                itemComponent.computeLifetimeSeconds(
                                        commandBuffer
                                );

                        DespawnComponent.trySetDespawn(
                                commandBuffer,
                                timeResource,
                                itemRef,
                                despawnComponent,
                                lifetime
                        );
                    }

                    return;
                }

                // PUSH
                if (stateId.endsWith("Push")) {
                    if (stateId.endsWith("OneConnectPush")) {
                        applyOneConnectPush(
                                world,
                                cablePosition,
                                itemPosition,
                                velocity,
                                dt
                        );
                        continue;
                    }
                    if (stateId.endsWith("StraightPush")) {
                        applyStraightPush(
                                world,
                                cablePosition,
                                itemPosition,
                                velocity,
                                itemRef,
                                dt
                        );
                        continue;
                    }

                }

                // OFF
            }
        }

        private static boolean hasLineOfSight(
                World world,
                Vector3d itemPosition,
                Vector3i cablePosition
        ) {
            Vector3d cableCenter =
                    new Vector3d(
                            cablePosition.x() + 0.5,
                            cablePosition.y() + 0.5,
                            cablePosition.z() + 0.5
                    );

            int startX = (int) Math.floor(itemPosition.x());
            int startY = (int) Math.floor(itemPosition.y());
            int startZ = (int) Math.floor(itemPosition.z());

            return BlockIterator.iterateFromTo(
                    itemPosition,
                    cableCenter,
                    (x, y, z, px, py, pz, qx, qy, qz) -> {

                        if (x == startX
                                && y == startY
                                && z == startZ) {
                            return true;
                        }

                        if (x == cablePosition.x()
                                && y == cablePosition.y()
                                && z == cablePosition.z()) {
                            return true;
                        }

                        BlockType blockType =
                                world.getBlockType(x, y, z);

                        if (blockType == null) {
                            return true;
                        }

                        return blockType.getMaterial()
                                != BlockMaterial.Solid;
                    }
            );
        }

        private static void applyOneConnectPush(
                World world,
                Vector3i cablePosition,
                Vector3d itemPosition,
                Velocity velocity,
                float dt
        ) {
            Set<Vector3i> neighbors =
                    ConnectableNeighborResolver.allNetworkNeighbors(
                            world,
                            cablePosition
                    );

            if (neighbors.size() != 1) {
                return;
            }

            Vector3i connectedNeighbor =
                    neighbors.iterator().next();

            ConnectableNeighborResolver.WorldSide connectedSide =
                    ConnectableNeighborResolver.worldSideFromSourceToTarget(
                            cablePosition,
                            connectedNeighbor
                    );

            Vector3d center =
                    new Vector3d(
                            cablePosition.x() + 0.5,
                            cablePosition.y() + 0.5,
                            cablePosition.z() + 0.5
                    );

            ConnectableNeighborResolver.WorldSide nearestTargetSide = null;
            Vector3d nearestTarget = null;
            double nearestDistanceSq = Double.MAX_VALUE;

            for (ConnectableNeighborResolver.WorldSide side :
                    ConnectableNeighborResolver.WorldSide.values()) {

                if (side == connectedSide) {
                    continue;
                }

                Vector3d target =
                        targetForSide(
                                center,
                                side
                        );

                double distanceSq =
                        itemPosition.distanceSquared(target);

                if (distanceSq < nearestDistanceSq) {
                    nearestDistanceSq = distanceSq;
                    nearestTarget = target;
                    nearestTargetSide = side;
                }
            }

            if (nearestTarget == null) {
                return;
            }

            Vector3d delta =
                    new Vector3d(nearestTarget)
                            .sub(itemPosition);

            double stiffness = 100.0;
            double damping = 20.0;

            double ax =
                    delta.x() * stiffness
                            - velocity.getX() * damping;

            double ay =
                    delta.y() * stiffness
                            - velocity.getY() * damping;

            double az =
                    delta.z() * stiffness
                            - velocity.getZ() * damping;

            ConnectableNeighborResolver.WorldSide oppositeSide =
                    connectedSide.opposite();

            if (nearestTargetSide != oppositeSide) {

                switch (connectedSide) {

                    case EAST -> {
                        ax = Math.max(0.0, ax);
                    }

                    case WEST -> {
                        ax = Math.min(0.0, ax);
                    }

                    case UP -> {
                        ay = Math.max(0.0, ay);
                    }

                    case DOWN -> {
                        ay = Math.min(0.0, ay);
                    }

                    case SOUTH -> {
                        az = Math.max(0.0, az);
                    }

                    case NORTH -> {
                        az = Math.min(0.0, az);
                    }
                }
            }


            velocity.addVelocity(
                    ax * dt,
                    ay * dt,
                    az * dt
            );
        }

        private static void applyStraightPush(
                World world,
                Vector3i cablePosition,
                Vector3d itemPosition,
                Velocity velocity,
                Ref<EntityStore> itemRef,
                float dt
        ) {

            RotationTuple rotation =
                    ConnectableNeighborResolver.rotationFor(
                            world,
                            cablePosition
                    );

            ConnectableNeighborResolver.WorldSide axisSide =
                    ConnectableNeighborResolver.worldSideForLocalSide(
                            rotation,
                            ConnectableRegistry.SIDE_FRONT
                    );

            ConnectableNeighborResolver.WorldSide oppositeAxisSide =
                    axisSide.opposite();

            Vector3d center =
                    new Vector3d(
                            cablePosition.x() + 0.5,
                            cablePosition.y() + 0.5,
                            cablePosition.z() + 0.5
                    );

            Vector3d nearestTarget = null;
            double nearestDistanceSq = Double.MAX_VALUE;

            for (ConnectableNeighborResolver.WorldSide side :
                    ConnectableNeighborResolver.WorldSide.values()) {

                if (side == axisSide
                        || side == oppositeAxisSide) {
                    continue;
                }

                Vector3d target =
                        targetForSide(center, side);

                double distanceSq =
                        itemPosition.distanceSquared(target);

                if (distanceSq < nearestDistanceSq) {
                    nearestDistanceSq = distanceSq;
                    nearestTarget = target;
                }
            }

            if (nearestTarget == null) {
                return;
            }

            Vector3d delta =
                    new Vector3d(nearestTarget)
                            .sub(itemPosition);

            double stiffness = 100.0;
            double damping = 20.0;

            double ax = 0.0;
            double ay = 0.0;
            double az = 0.0;

            switch (axisSide) {

                case EAST, WEST -> {
                    ay =
                            delta.y() * stiffness
                                    - velocity.getY() * damping;

                    az =
                            delta.z() * stiffness
                                    - velocity.getZ() * damping;
                }

                case NORTH, SOUTH -> {
                    ax =
                            delta.x() * stiffness
                                    - velocity.getX() * damping;

                    ay =
                            delta.y() * stiffness
                                    - velocity.getY() * damping;
                }

                case UP, DOWN -> {
                    ax =
                            delta.x() * stiffness
                                    - velocity.getX() * damping;

                    az =
                            delta.z() * stiffness
                                    - velocity.getZ() * damping;

                    Double originalY =
                            PRE_GRAVITY_Y.get(itemRef);

                    if (originalY != null) {
                        velocity.setY(originalY);
                    }
                }
            }

            velocity.addVelocity(
                    ax * dt,
                    ay * dt,
                    az * dt
            );
        }

    }
}
