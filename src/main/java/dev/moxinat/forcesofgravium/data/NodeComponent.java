package dev.moxinat.forcesofgravium.data;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.EnumCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import dev.moxinat.forcesofgravium.signal.SignalState;

import javax.annotation.Nonnull;

public final class NodeComponent implements Component<ChunkStore> {

    public static final long NO_NETWORK = 0L;

    private static final EnumCodec<SignalState> SIGNAL_STATE_CODEC =
            new EnumCodec<>(SignalState.class);

    public static final BuilderCodec<NodeComponent> CODEC =
            BuilderCodec.builder(
                            NodeComponent.class,
                            NodeComponent::new
                    )

                    .append(
                            new KeyedCodec<>("SignalInputSides", Codec.INTEGER),
                            (component, value) -> component.signalInputSides = value,
                            component -> component.signalInputSides
                    )
                    .add()

                    .append(
                            new KeyedCodec<>("SignalOutputSides", Codec.INTEGER),
                            (component, value) -> component.signalOutputSides = value,
                            component -> component.signalOutputSides
                    )
                    .add()

                    .append(
                            new KeyedCodec<>("ControlInputSides", Codec.INTEGER),
                            (component, value) -> component.controlInputSides = value,
                            component -> component.controlInputSides
                    )
                    .add()

                    .append(
                            new KeyedCodec<>(
                                    "PreviousInstantState",
                                    SIGNAL_STATE_CODEC
                            ),
                            (component, value) ->
                                    component.previousInstantState = value,
                            component -> component.previousInstantState
                    )
                    .add()

                    .append(
                            new KeyedCodec<>(
                                    "InstantState",
                                    SIGNAL_STATE_CODEC
                            ),
                            (component, value) ->
                                    component.instantState = value,
                            component -> component.instantState
                    )
                    .add()

                    .append(
                            new KeyedCodec<>(
                                    "PreviousEffectiveState",
                                    SIGNAL_STATE_CODEC
                            ),
                            (component, value) ->
                                    component.previousEffectiveState = value,
                            component -> component.previousEffectiveState
                    )
                    .add()

                    .append(
                            new KeyedCodec<>(
                                    "EffectiveState",
                                    SIGNAL_STATE_CODEC
                            ),
                            (component, value) ->
                                    component.effectiveState = value,
                            component -> component.effectiveState
                    )
                    .add()

                    .append(
                            new KeyedCodec<>("Dirty", Codec.BOOLEAN),
                            (component, value) -> component.dirty = value,
                            component -> component.dirty
                    )
                    .add()

                    .append(
                            new KeyedCodec<>("InvertEnabled", Codec.BOOLEAN),
                            (component, value) -> component.invertEnabled = value,
                            component -> component.invertEnabled
                    )
                    .add()

                    .append(
                            new KeyedCodec<>("Passing", Codec.BOOLEAN),
                            (component, value) -> component.passing = value,
                            component -> component.passing
                    )
                    .add()

                    .append(
                            new KeyedCodec<>("EnergyDelta", Codec.INTEGER),
                            (component, value) -> component.energyDelta = value,
                            component -> component.energyDelta
                    )
                    .add()

                    .append(
                            new KeyedCodec<>("NetworkId", Codec.LONG),
                            (component, value) -> component.networkId = value,
                            component -> component.networkId
                    )
                    .add()

                    .build();


    private int signalInputSides;
    private int signalOutputSides;
    private int controlInputSides;

    @Nonnull
    private SignalState previousInstantState;

    @Nonnull
    private SignalState instantState;

    @Nonnull
    private SignalState previousEffectiveState;

    @Nonnull
    private SignalState effectiveState;

    private boolean dirty;
    private boolean invertEnabled;
    private boolean passing;

    private int energyDelta;
    private long networkId;


    public NodeComponent() {
        signalInputSides = 0;
        signalOutputSides = 0;
        controlInputSides = 0;

        previousInstantState = SignalState.OFF;
        instantState = SignalState.OFF;

        previousEffectiveState = SignalState.OFF;
        effectiveState = SignalState.OFF;

        dirty = false;
        invertEnabled = false;
        passing = true;

        energyDelta = 0;
        networkId = NO_NETWORK;
    }

    private NodeComponent(@Nonnull NodeComponent other) {
        signalInputSides = other.signalInputSides;
        signalOutputSides = other.signalOutputSides;
        controlInputSides = other.controlInputSides;

        previousInstantState = other.previousInstantState;
        instantState = other.instantState;

        previousEffectiveState = other.previousEffectiveState;
        effectiveState = other.effectiveState;

        dirty = other.dirty;
        invertEnabled = other.invertEnabled;
        passing = other.passing;

        energyDelta = other.energyDelta;
        networkId = other.networkId;
    }


    public int signalInputSides() {
        return signalInputSides;
    }

    public void setSignalInputSides(int signalInputSides) {
        this.signalInputSides = signalInputSides;
    }

    public int signalOutputSides() {
        return signalOutputSides;
    }

    public void setSignalOutputSides(int signalOutputSides) {
        this.signalOutputSides = signalOutputSides;
    }

    public int controlInputSides() {
        return controlInputSides;
    }

    public void setControlInputSides(int controlInputSides) {
        this.controlInputSides = controlInputSides;
    }


    public @Nonnull SignalState previousInstantState() {
        return previousInstantState;
    }

    public @Nonnull SignalState instantState() {
        return instantState;
    }

    public void setInstantState(@Nonnull SignalState instantState) {
        this.previousInstantState = this.instantState;
        this.instantState = instantState;
    }


    public @Nonnull SignalState previousEffectiveState() {
        return previousEffectiveState;
    }

    public @Nonnull SignalState effectiveState() {
        return effectiveState;
    }

    public void setEffectiveState(@Nonnull SignalState effectiveState) {
        this.previousEffectiveState = this.effectiveState;
        this.effectiveState = effectiveState;
    }

    public void adoptInstantState() {
        previousEffectiveState = effectiveState;
        effectiveState = instantState;
        dirty = false;
    }


    public boolean dirty() {
        return dirty;
    }

    public void setDirty(boolean dirty) {
        this.dirty = dirty;
    }


    public boolean invertEnabled() {
        return invertEnabled;
    }

    public void setInvertEnabled(boolean invertEnabled) {
        this.invertEnabled = invertEnabled;
    }


    public boolean passing() {
        return passing;
    }

    public void setPassing(boolean passing) {
        this.passing = passing;
    }


    public int energyDelta() {
        return energyDelta;
    }

    public void setEnergyDelta(int energyDelta) {
        this.energyDelta = energyDelta;
    }


    public long networkId() {
        return networkId;
    }

    public void setNetworkId(long networkId) {
        this.networkId = networkId;
    }


    public boolean canReceiveSignalFrom(int localSide) {
        return (signalInputSides & localSide) != 0;
    }

    public boolean canOutputSignalTo(int localSide) {
        return passing && (signalOutputSides & localSide) != 0;
    }

    public boolean canReceiveControlFrom(int localSide) {
        return (controlInputSides & localSide) != 0;
    }


    @Override
    @Nonnull
    public NodeComponent clone() {
        return new NodeComponent(this);
    }
}