package dev.moxinat.forcesofgravium.system;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.PlaceBlockEvent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.Set;

public final class BlockPlacementRotationSystem extends EntityEventSystem<EntityStore, PlaceBlockEvent> {

    private static final Set<String> ROTATION_CONTROLLED_BLOCKS = Set.of(
            "Inverter_Block",
            "Gravium_Siphon_Block"
    );
    private static final double VERTICAL_DIRECTION_THRESHOLD = 0.9D;

    public BlockPlacementRotationSystem() {
        super(PlaceBlockEvent.class);
    }

    @Override
    public @Nonnull Query<EntityStore> getQuery() {
        return Player.getComponentType();
    }

    @Override
    public void handle(int index, @Nonnull ArchetypeChunk<EntityStore> chunk, @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer, @Nonnull PlaceBlockEvent event) {
        ItemStack itemInHand = event.getItemInHand();
        if (itemInHand == null || !ROTATION_CONTROLLED_BLOCKS.contains(itemInHand.getItemId())) {
            return;
        }

        Ref<EntityStore> entityRef = chunk.getReferenceTo(index);
        Player player = store.getComponent(entityRef, Player.getComponentType());
        HeadRotation headRotation = store.getComponent(entityRef, HeadRotation.getComponentType());
        if (player == null || headRotation == null) {
            System.out.println("[FoG] PlaceBlockEvent missing player/head rotation for entityIndex=" + entityRef.getIndex());
            return;
        }

        Vector3f rotation = headRotation.getRotation();
        Vector3d direction = headRotation.getDirection();
        Rotation desiredPitch = verticalPitchFromLook(headRotation);
        if (desiredPitch == null) {
            System.out.println(
                    "[FoG] PlaceBlockEvent kept vanilla rotation "
                            + event.getRotation()
                            + " placed=" + event.getTargetBlock()
                            + " pitch=" + Math.round(rotation.getPitch())
                            + " yaw=" + Math.round(rotation.getYaw())
                            + " direction=" + direction
                            + " entityIndex=" + entityRef.getIndex()
            );
            return;
        }

        RotationTuple currentRotation = event.getRotation();
        RotationTuple desiredRotation = RotationTuple.of(currentRotation.yaw(), desiredPitch, currentRotation.roll());
        event.setRotation(desiredRotation);
        System.out.println(
                "[FoG] PlaceBlockEvent applied rotation " + desiredRotation
                        + " placed=" + event.getTargetBlock()
                        + " pitch=" + Math.round(rotation.getPitch())
                        + " yaw=" + Math.round(rotation.getYaw())
                        + " direction=" + direction
                        + " horizontal=" + headRotation.getHorizontalAxisDirection()
                        + " axis=" + headRotation.getAxisDirection()
                        + " entityIndex=" + entityRef.getIndex()
        );
    }

    private static Rotation verticalPitchFromLook(HeadRotation headRotation) {
        Vector3d direction = headRotation.getDirection();

        if (direction.getY() >= VERTICAL_DIRECTION_THRESHOLD) {
            return Rotation.TwoSeventy;
        }

        if (direction.getY() <= -VERTICAL_DIRECTION_THRESHOLD) {
            return Rotation.Ninety;
        }

        return null;
    }
}
