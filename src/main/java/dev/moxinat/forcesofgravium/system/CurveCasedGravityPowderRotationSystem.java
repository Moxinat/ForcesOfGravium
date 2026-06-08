package dev.moxinat.forcesofgravium.system;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.math.Axis;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.UseBlockEvent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.accessor.BlockAccessor;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.moxinat.forcesofgravium.data.ConnectableRotationStore;
import dev.moxinat.forcesofgravium.registry.ConnectableRegistry;
import org.joml.Vector3i;

import javax.annotation.Nonnull;

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
            if (blockType == null || !ConnectableRegistry.isCurveCasedGravityPowderId(blockType.getId())) {
                return;
            }

            Ref<EntityStore> entityRef = chunk.getReferenceTo(index);
            Player player = store.getComponent(entityRef, Player.getComponentType());
            if (player == null || player.getWorld() == null) {
                return;
            }

            World world = player.getWorld();
            Vector3i position = new Vector3i(event.getTargetBlock());
            RotationTuple currentRotation = ConnectableRotationStore.get(world, position);
            if (currentRotation == null) {
                return;
            }

            RotationTuple nextRotation = rotateAroundLocalAxis(currentRotation, LOCAL_INTERACTION_ROTATION_AXIS);
            BlockAccessor blockAccessor = world.getChunk(ChunkUtil.indexChunkFromBlock(position.x(), position.z()));
            if (blockAccessor == null) {
                return;
            }

            ConnectableRotationStore.put(world, position, nextRotation);
            blockAccessor.placeBlock(position.x(), position.y(), position.z(), blockType.getId(), nextRotation, 0, false);
        }
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
