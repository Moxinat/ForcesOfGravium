package dev.moxinat.forcesofgravium.data;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import javax.annotation.Nonnull;

public final class SourceComponent implements Component<ChunkStore> {

    public static final BuilderCodec<SourceComponent> CODEC =
            BuilderCodec.builder(
                            SourceComponent.class,
                            SourceComponent::new
                    )
                    .append(
                            new KeyedCodec<>("Power", Codec.INTEGER),
                            (component, value) -> component.power = value,
                            component -> component.power
                    )
                    .add()
                    .append(
                            new KeyedCodec<>("RemainingActiveTicks", Codec.LONG),
                            (component, value) -> component.remainingActiveTicks = value,
                            component -> component.remainingActiveTicks
                    )
                    .add()
                    .build();

    private int power;
    private long remainingActiveTicks;

    public SourceComponent() {
        power = 0;
        remainingActiveTicks = 0;
    }

    private SourceComponent(SourceComponent other) {
        power = other.power;
        remainingActiveTicks = other.remainingActiveTicks;
    }

    public int power() {
        return power;
    }

    public void setPower(int power) {
        this.power = power;
    }

    public long remainingActiveTicks() {
        return remainingActiveTicks;
    }

    public void setRemainingActiveTicks(long remainingActiveTicks) {
        this.remainingActiveTicks = remainingActiveTicks;
    }

    public void tickRemainingActiveTicks() {
        if (remainingActiveTicks > 0) {
            remainingActiveTicks--;
        }
    }

    @Override
    public @Nonnull SourceComponent clone() {
        return new SourceComponent(this);
    }
}