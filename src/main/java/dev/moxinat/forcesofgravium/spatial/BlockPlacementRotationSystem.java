package dev.moxinat.forcesofgravium.spatial;

import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import org.joml.Vector3d;

import javax.annotation.Nonnull;
import java.util.Set;

public final class BlockPlacementRotationSystem {

    private static final Set<String> ROTATION_CONTROLLED_BLOCKS = Set.of(
            "Inverter_Block",
            "Gravium_Siphon_Block",
            "Straight_Cased_Gravity_Powder"
    );
    private static final double VERTICAL_DIRECTION_THRESHOLD = 0.9D;

    private BlockPlacementRotationSystem() {
    }

    public static @Nonnull RotationTuple resolveRotation(
            @Nonnull String blockId,
            @Nonnull RotationTuple currentRotation,
            @Nonnull HeadRotation headRotation
    ) {
        if (!ROTATION_CONTROLLED_BLOCKS.contains(blockId)) {
            return currentRotation;
        }

        Rotation desiredPitch = verticalPitchFromLook(headRotation);
        if (desiredPitch == null) {
            return currentRotation;
        }

        return RotationTuple.of(
                currentRotation.yaw(),
                desiredPitch,
                currentRotation.roll()
        );
    }

    private static Rotation verticalPitchFromLook(@Nonnull HeadRotation headRotation) {
        Vector3d direction = headRotation.getDirection();

        if (direction.y() >= VERTICAL_DIRECTION_THRESHOLD) {
            return Rotation.TwoSeventy;
        }

        if (direction.y() <= -VERTICAL_DIRECTION_THRESHOLD) {
            return Rotation.Ninety;
        }

        return null;
    }
}
