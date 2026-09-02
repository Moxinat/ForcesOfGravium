package dev.moxinat.forcesofgravium.data;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import javax.annotation.Nonnull;

public final class SiphonComponent
        implements Component<ChunkStore> {

    public static final BuilderCodec<SiphonComponent> CODEC =
            BuilderCodec.builder(
                            SiphonComponent.class,
                            SiphonComponent::new
                    )
                    .append(
                            new KeyedCodec<>(
                                    "CooldownTicks",
                                    Codec.INTEGER
                            ),
                            (component, value) ->
                                    component.cooldownTicks = value,
                            component ->
                                    component.cooldownTicks
                    )
                    .add()
                    .build();


    private int cooldownTicks;


    public SiphonComponent() {
        this.cooldownTicks = 0;
    }

    private SiphonComponent(
            @Nonnull SiphonComponent other
    ) {
        this.cooldownTicks =
                other.cooldownTicks;
    }


    public int cooldownTicks() {
        return cooldownTicks;
    }

    public void setCooldownTicks(
            int cooldownTicks
    ) {
        this.cooldownTicks =
                Math.max(0, cooldownTicks);
    }

    public boolean onCooldown() {
        return cooldownTicks > 0;
    }

    public void tickCooldown() {
        if (cooldownTicks > 0) {
            cooldownTicks--;
        }
    }


    @Override
    public @Nonnull SiphonComponent clone() {
        return new SiphonComponent(this);
    }
}