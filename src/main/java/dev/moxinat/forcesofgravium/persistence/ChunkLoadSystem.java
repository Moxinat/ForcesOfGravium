package dev.moxinat.forcesofgravium.persistence;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefChangeSystem;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import dev.moxinat.forcesofgravium.ForcesOfGraviumPlugin;
import dev.moxinat.forcesofgravium.block.sensor.SensorLogic;
import dev.moxinat.forcesofgravium.data.SensorComponent;
import org.joml.Vector3i;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class ChunkLoadSystem {

    private ChunkLoadSystem() {
    }

    public static final class SensorLoadSystem
            extends RefChangeSystem<ChunkStore, SensorComponent> {

        @Override
        public @Nonnull ComponentType<ChunkStore, SensorComponent> componentType() {
            return ForcesOfGraviumPlugin.SENSOR_COMPONENT_TYPE;
        }

        @Override
        public @Nonnull Query<ChunkStore> getQuery() {
            return Query.and(
                    ForcesOfGraviumPlugin.SENSOR_COMPONENT_TYPE,
                    ForcesOfGraviumPlugin.NODE_COMPONENT_TYPE,
                    BlockModule.BlockStateInfo.getComponentType()
            );
        }

        @Override
        public void onComponentAdded(
                @Nonnull Ref<ChunkStore> ref,
                @Nonnull SensorComponent component,
                @Nonnull Store<ChunkStore> store,
                @Nonnull CommandBuffer<ChunkStore> commandBuffer
        ) {
            BlockModule.BlockStateInfo blockStateInfo =
                    store.getComponent(
                            ref,
                            BlockModule.BlockStateInfo.getComponentType()
                    );

            if (blockStateInfo == null) {
                return;
            }

            Vector3i position =
                    new Vector3i();

            if (!blockStateInfo.fillWorldPos(
                    store,
                    position
            )) {
                return;
            }

            World world =
                    store.getExternalData().getWorld();

            System.out.println(
                    "[FoG] SensorComponent added at "
                            + position
                            + " tick="
                            + world.getTick()
            );

            SensorLogic.restoreRuntime(
                    world,
                    position
            );
        }

        @Override
        public void onComponentSet(
                @Nonnull Ref<ChunkStore> ref,
                @Nullable SensorComponent oldComponent,
                @Nonnull SensorComponent newComponent,
                @Nonnull Store<ChunkStore> store,
                @Nonnull CommandBuffer<ChunkStore> commandBuffer
        ) {
        }

        @Override
        public void onComponentRemoved(
                @Nonnull Ref<ChunkStore> ref,
                @Nonnull SensorComponent component,
                @Nonnull Store<ChunkStore> store,
                @Nonnull CommandBuffer<ChunkStore> commandBuffer
        ) {
        }
    }
}