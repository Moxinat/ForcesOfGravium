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
                    .build();

    private int power;

    public SourceComponent() {
        power = 0;
    }

    private SourceComponent(SourceComponent other) {
        power = other.power;
    }

    public int power() {
        return power;
    }

    public void setPower(int power) {
        this.power = power;
    }

    @Override
    public @Nonnull SourceComponent clone() {
        return new SourceComponent(this);
    }
}