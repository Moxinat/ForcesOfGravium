package dev.moxinat.forcesofgravium.data;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import dev.moxinat.forcesofgravium.ForcesOfGraviumPlugin;
import org.joml.Vector3i;

import javax.annotation.Nonnull;
import java.util.Objects;
import java.util.function.Consumer;

public final class Nodes {

    private Nodes() {
    }

    public static boolean mutate(
            @Nonnull World world,
            @Nonnull Vector3i position,
            @Nonnull Consumer<NodeComponent> mutation
    ) {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(mutation, "mutation");

        Ref<ChunkStore> blockRef =
                BlockModule.getBlockEntity(
                        world,
                        position.x(),
                        position.y(),
                        position.z()
                );

        if (blockRef == null || !blockRef.isValid()) {
            return false;
        }

        Store<ChunkStore> store = blockRef.getStore();

        NodeComponent node =
                store.getComponent(
                        blockRef,
                        ForcesOfGraviumPlugin.NODE_COMPONENT_TYPE
                );

        BlockModule.BlockStateInfo blockStateInfo =
                store.getComponent(
                        blockRef,
                        BlockModule.BlockStateInfo.getComponentType()
                );

        if (node == null || blockStateInfo == null) {
            return false;
        }

        mutation.accept(node);
        blockStateInfo.markNeedsSaving();

        return true;
    }
}
