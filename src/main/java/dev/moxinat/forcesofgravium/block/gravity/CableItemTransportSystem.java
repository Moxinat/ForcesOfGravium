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
import java.util.*;
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

            DampingCandidate dampingCandidate =
                    new DampingCandidate();

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

            Vector3d originalVelocity =
                    new Vector3d(
                            velocity.getX(),
                            velocity.getY(),
                            velocity.getZ()
                    );

            Vector3d totalAcceleration =
                    new Vector3d();

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
                    Set<Vector3i> neighbors =
                            ConnectableNeighborResolver.allNetworkNeighbors(
                                    world,
                                    cablePosition
                            );

                    Set<ConnectableNeighborResolver.WorldSide> connectionSides =
                            new HashSet<>();

                    for (Vector3i neighbor : neighbors) {
                        connectionSides.add(
                                ConnectableNeighborResolver.worldSideFromSourceToTarget(
                                        cablePosition,
                                        neighbor
                                )
                        );
                    }

                    applyCablePush(
                            cablePosition,
                            itemPosition,
                            connectionSides,
                            velocity,
                            itemRef,
                            totalAcceleration,
                            dampingCandidate
                    );

                    continue;
                }

                // OFF
            }

            if (dampingCandidate.target != null) {
                double damping = 8.0;

                Vector3d dampingAcceleration =
                        new Vector3d(originalVelocity)
                                .mul(-damping);

                for (ConnectableNeighborResolver.WorldSide openSide :
                        dampingCandidate.openSides) {
                    openTowards(
                            dampingAcceleration,
                            itemPosition,
                            dampingCandidate.target,
                            openSide
                    );
                }

                totalAcceleration.add(dampingAcceleration);
            }

            velocity.addVelocity(
                    totalAcceleration.x() * dt,
                    totalAcceleration.y() * dt,
                    totalAcceleration.z() * dt
            );

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

            if (itemPosition.distanceSquared(cableCenter) < 0.000001) {
                return true;
            }

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

        private static void applyCablePush(
                Vector3i cablePosition,
                Vector3d itemPosition,
                Set<ConnectableNeighborResolver.WorldSide> connectionSides,
                Velocity velocity,
                Ref<EntityStore> itemRef,
                Vector3d totalAcceleration,
                DampingCandidate dampingCandidate
        ) {
            int itemBlockX =
                    (int) Math.floor(itemPosition.x());

            int itemBlockY =
                    (int) Math.floor(itemPosition.y());

            int itemBlockZ =
                    (int) Math.floor(itemPosition.z());

            for (ConnectableNeighborResolver.WorldSide connectionSide :
                    connectionSides) {

                switch (connectionSide) {

                    case EAST -> {
                        if (itemBlockX > cablePosition.x()) {
                            return;
                        }
                    }

                    case WEST -> {
                        if (itemBlockX < cablePosition.x()) {
                            return;
                        }
                    }

                    case UP -> {
                        if (itemBlockY > cablePosition.y()) {
                            return;
                        }
                    }

                    case DOWN -> {
                        if (itemBlockY < cablePosition.y()) {
                            return;
                        }
                    }

                    case SOUTH -> {
                        if (itemBlockZ > cablePosition.z()) {
                            return;
                        }
                    }

                    case NORTH -> {
                        if (itemBlockZ < cablePosition.z()) {
                            return;
                        }
                    }
                }
            }

            Vector3d target =
                    new Vector3d(
                            cablePosition.x() + 0.5,
                            cablePosition.y() + 0.5,
                            cablePosition.z() + 0.5
                    );

            Vector3d acceleration =
                    new Vector3d(target)
                            .sub(itemPosition)
                            .mul(100.0);

            for (ConnectableNeighborResolver.WorldSide connectionSide :
                    connectionSides) {

                openTowards(
                        acceleration,
                        itemPosition,
                        target,
                        connectionSide
                );
            }

            if (connectionSides.contains(
                    ConnectableNeighborResolver.WorldSide.UP
            ) || connectionSides.contains(
                    ConnectableNeighborResolver.WorldSide.DOWN
            )) {
                Double preGravityY =
                        PRE_GRAVITY_Y.get(itemRef);

                if (preGravityY != null) {
                    velocity.setY(preGravityY);
                }
            }

            dampingCandidate.consider(
                    itemPosition,
                    target,
                    connectionSides.toArray(
                            ConnectableNeighborResolver.WorldSide[]::new
                    )
            );

            totalAcceleration.add(acceleration);
        }


        private static void openTowards(
                Vector3d acceleration,
                Vector3d itemPosition,
                Vector3d target,
                ConnectableNeighborResolver.WorldSide openSide
        ) {
            switch (openSide) {

                case EAST -> {
                    if (itemPosition.x() >= target.x()) {
                        acceleration.x = 0.0;
                    }
                }

                case WEST -> {
                    if (itemPosition.x() <= target.x()) {
                        acceleration.x = 0.0;
                    }
                }

                case UP -> {
                    if (itemPosition.y() >= target.y()) {
                        acceleration.y = 0.0;
                    }
                }

                case DOWN -> {
                    if (itemPosition.y() <= target.y()) {
                        acceleration.y = 0.0;
                    }
                }

                case SOUTH -> {
                    if (itemPosition.z() >= target.z()) {
                        acceleration.z = 0.0;
                    }
                }

                case NORTH -> {
                    if (itemPosition.z() <= target.z()) {
                        acceleration.z = 0.0;
                    }
                }
            }
        }

    }

    private static final class DampingCandidate {

        private Vector3d target;
        private double distanceSquared = Double.MAX_VALUE;

        private ConnectableNeighborResolver.WorldSide[] openSides =
                new ConnectableNeighborResolver.WorldSide[0];

        private void consider(
                Vector3d itemPosition,
                Vector3d target,
                ConnectableNeighborResolver.WorldSide... openSides
        ) {
            double distanceSquared =
                    itemPosition.distanceSquared(target);

            if (distanceSquared >= this.distanceSquared) {
                return;
            }

            this.distanceSquared = distanceSquared;
            this.target = new Vector3d(target);
            this.openSides = openSides.clone();
        }
    }
}
