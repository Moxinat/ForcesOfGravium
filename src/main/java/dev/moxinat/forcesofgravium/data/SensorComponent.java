package dev.moxinat.forcesofgravium.data;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import dev.moxinat.forcesofgravium.signal.SignalState;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class SensorComponent implements Component<ChunkStore> {

    public static final BuilderCodec<SensorComponent> CODEC =
            BuilderCodec.builder(
                            SensorComponent.class,
                            SensorComponent::new
                    )

                    // Does this sensor currently have a captured snapshot?
                    .append(
                            new KeyedCodec<>("HasSnapshot", Codec.BOOLEAN),
                            (component, value) -> component.hasSnapshot = value,
                            component -> component.hasSnapshot
                    )
                    .add()

                    // Observed block
                    .append(
                            new KeyedCodec<>("BlockId", Codec.STRING),
                            (component, value) -> component.blockId = value,
                            component -> component.blockId
                    )
                    .add()
                    .append(
                            new KeyedCodec<>("BlockStateId", Codec.STRING),
                            (component, value) -> component.blockStateId = value,
                            component -> component.blockStateId
                    )
                    .add()
                    .append(
                            new KeyedCodec<>("BlockUsed", Codec.BOOLEAN),
                            (component, value) -> component.blockUsed = value,
                            component -> component.blockUsed
                    )
                    .add()

                    // Observed FoG node
                    .append(
                            new KeyedCodec<>("HasNodeSnapshot", Codec.BOOLEAN),
                            (component, value) -> component.hasNodeSnapshot = value,
                            component -> component.hasNodeSnapshot
                    )
                    .add()
                    .append(
                            new KeyedCodec<>("NodeEffectiveState", Codec.STRING),
                            (component, value) ->
                                    component.nodeEffectiveState =
                                            SignalState.valueOf(value),
                            component -> component.nodeEffectiveState.name()
                    )
                    .add()
                    .append(
                            new KeyedCodec<>("NodeInvertEnabled", Codec.BOOLEAN),
                            (component, value) ->
                                    component.nodeInvertEnabled = value,
                            component -> component.nodeInvertEnabled
                    )
                    .add()
                    .append(
                            new KeyedCodec<>("NodePassing", Codec.BOOLEAN),
                            (component, value) ->
                                    component.nodePassing = value,
                            component -> component.nodePassing
                    )
                    .add()

                    // Container
                    .append(
                            new KeyedCodec<>(
                                    "HasContainerItemCount",
                                    Codec.BOOLEAN
                            ),
                            (component, value) ->
                                    component.hasContainerItemCount = value,
                            component -> component.hasContainerItemCount
                    )
                    .add()
                    .append(
                            new KeyedCodec<>(
                                    "ContainerItemCount",
                                    Codec.INTEGER
                            ),
                            (component, value) ->
                                    component.containerItemCount = value,
                            component -> component.containerItemCount
                    )
                    .add()

                    // Entities
                    .append(
                            new KeyedCodec<>("EntityCount", Codec.INTEGER),
                            (component, value) -> component.entityCount = value,
                            component -> component.entityCount
                    )
                    .add()

                    .build();


    private boolean hasSnapshot;

    @Nonnull
    private String blockId;

    @Nullable
    private String blockStateId;

    private boolean blockUsed;

    private boolean hasNodeSnapshot;

    @Nonnull
    private SignalState nodeEffectiveState;

    private boolean nodeInvertEnabled;
    private boolean nodePassing;

    private boolean hasContainerItemCount;
    private int containerItemCount;

    private int entityCount;


    public SensorComponent() {
        this.hasSnapshot = false;

        this.blockId = "";
        this.blockStateId = null;
        this.blockUsed = false;

        this.hasNodeSnapshot = false;
        this.nodeEffectiveState = SignalState.OFF;
        this.nodeInvertEnabled = false;
        this.nodePassing = false;

        this.hasContainerItemCount = false;
        this.containerItemCount = 0;

        this.entityCount = 0;
    }

    private SensorComponent(SensorComponent other) {
        this.hasSnapshot = other.hasSnapshot;

        this.blockId = other.blockId;
        this.blockStateId = other.blockStateId;
        this.blockUsed = other.blockUsed;

        this.hasNodeSnapshot = other.hasNodeSnapshot;
        this.nodeEffectiveState = other.nodeEffectiveState;
        this.nodeInvertEnabled = other.nodeInvertEnabled;
        this.nodePassing = other.nodePassing;

        this.hasContainerItemCount = other.hasContainerItemCount;
        this.containerItemCount = other.containerItemCount;

        this.entityCount = other.entityCount;
    }


    public boolean hasSnapshot() {
        return hasSnapshot;
    }

    public @Nonnull String blockId() {
        return blockId;
    }

    public @Nullable String blockStateId() {
        return blockStateId;
    }

    public boolean blockUsed() {
        return blockUsed;
    }

    public boolean hasNodeSnapshot() {
        return hasNodeSnapshot;
    }

    public @Nonnull SignalState nodeEffectiveState() {
        return nodeEffectiveState;
    }

    public boolean nodeInvertEnabled() {
        return nodeInvertEnabled;
    }

    public boolean nodePassing() {
        return nodePassing;
    }

    public boolean hasContainerItemCount() {
        return hasContainerItemCount;
    }

    public int containerItemCount() {
        return containerItemCount;
    }

    public int entityCount() {
        return entityCount;
    }


    public void setSnapshot(
            @Nonnull String blockId,
            @Nullable String blockStateId,
            boolean blockUsed,
            boolean hasNodeSnapshot,
            @Nonnull SignalState nodeEffectiveState,
            boolean nodeInvertEnabled,
            boolean nodePassing,
            boolean hasContainerItemCount,
            int containerItemCount,
            int entityCount
    ) {
        this.hasSnapshot = true;

        this.blockId = blockId;
        this.blockStateId = blockStateId;
        this.blockUsed = blockUsed;

        this.hasNodeSnapshot = hasNodeSnapshot;
        this.nodeEffectiveState = nodeEffectiveState;
        this.nodeInvertEnabled = nodeInvertEnabled;
        this.nodePassing = nodePassing;

        this.hasContainerItemCount = hasContainerItemCount;
        this.containerItemCount = containerItemCount;

        this.entityCount = entityCount;
    }

    public void setBlockUsed(boolean blockUsed) {
        this.blockUsed = blockUsed;
    }

    public void clearSnapshot() {
        this.hasSnapshot = false;

        this.blockId = "";
        this.blockStateId = null;
        this.blockUsed = false;

        this.hasNodeSnapshot = false;
        this.nodeEffectiveState = SignalState.OFF;
        this.nodeInvertEnabled = false;
        this.nodePassing = false;

        this.hasContainerItemCount = false;
        this.containerItemCount = 0;

        this.entityCount = 0;
    }


    @SuppressWarnings("MethodDoesntCallSuperMethod")
    @Override
    @Nonnull
    public SensorComponent clone() {
        return new SensorComponent(this);
    }
}