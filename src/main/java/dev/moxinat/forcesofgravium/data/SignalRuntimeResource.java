package dev.moxinat.forcesofgravium.data;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.array.ArrayCodec;
import com.hypixel.hytale.component.Resource;
import com.hypixel.hytale.math.vector.Vector3iUtil;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import org.joml.Vector3i;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public final class SignalRuntimeResource implements Resource<ChunkStore> {

    private static final ArrayCodec<Vector3i> POSITION_ARRAY_CODEC =
            ArrayCodec.ofBuilderCodec(
                    Vector3iUtil.CODEC,
                    Vector3i[]::new
            );

    private static final ArrayCodec<ActiveSourceData> ACTIVE_SOURCE_ARRAY_CODEC =
            ArrayCodec.ofBuilderCodec(
                    ActiveSourceData.CODEC,
                    ActiveSourceData[]::new
            );


    public static final BuilderCodec<SignalRuntimeResource> CODEC =
            BuilderCodec.builder(
                            SignalRuntimeResource.class,
                            SignalRuntimeResource::new
                    )

                    .append(
                            new KeyedCodec<>(
                                    "CurrentWave",
                                    POSITION_ARRAY_CODEC
                            ),
                            SignalRuntimeResource::setCurrentWave,
                            SignalRuntimeResource::getCurrentWave
                    )
                    .add()

                    .append(
                            new KeyedCodec<>(
                                    "NextWave",
                                    POSITION_ARRAY_CODEC
                            ),
                            SignalRuntimeResource::setNextWave,
                            SignalRuntimeResource::getNextWave
                    )
                    .add()

                    .append(
                            new KeyedCodec<>(
                                    "ActiveSources",
                                    ACTIVE_SOURCE_ARRAY_CODEC
                            ),
                            SignalRuntimeResource::setActiveSources,
                            SignalRuntimeResource::getActiveSources
                    )
                    .add()

                    .build();


    private final Set<Vector3i> currentWave =
            new LinkedHashSet<>();

    private final Set<Vector3i> nextWave =
            new LinkedHashSet<>();

    /**
     * Position -> remaining active ticks.
     */
    private final Map<Vector3i, Long> activeSources =
            new HashMap<>();


    public SignalRuntimeResource() {
    }


    private SignalRuntimeResource(
            @Nonnull SignalRuntimeResource other
    ) {

        for (Vector3i position : other.currentWave) {
            currentWave.add(
                    new Vector3i(position)
            );
        }

        for (Vector3i position : other.nextWave) {
            nextWave.add(
                    new Vector3i(position)
            );
        }

        for (Map.Entry<Vector3i, Long> entry
                : other.activeSources.entrySet()) {

            activeSources.put(
                    new Vector3i(entry.getKey()),
                    entry.getValue()
            );
        }
    }


    // --------------------------------------------------
    // RUNTIME ACCESS
    // --------------------------------------------------

    public @Nonnull Set<Vector3i> currentWave() {
        return currentWave;
    }

    public @Nonnull Set<Vector3i> nextWave() {
        return nextWave;
    }

    public @Nonnull Map<Vector3i, Long> activeSources() {
        return activeSources;
    }


    // --------------------------------------------------
    // CURRENT WAVE SERIALIZATION
    // --------------------------------------------------

    private Vector3i[] getCurrentWave() {
        return currentWave.toArray(
                Vector3i[]::new
        );
    }

    private void setCurrentWave(
            Vector3i[] positions
    ) {
        currentWave.clear();

        if (positions == null) {
            return;
        }

        for (Vector3i position : positions) {

            if (position != null) {
                currentWave.add(
                        new Vector3i(position)
                );
            }
        }
    }


    // --------------------------------------------------
    // NEXT WAVE SERIALIZATION
    // --------------------------------------------------

    private Vector3i[] getNextWave() {
        return nextWave.toArray(
                Vector3i[]::new
        );
    }

    private void setNextWave(
            Vector3i[] positions
    ) {
        nextWave.clear();

        if (positions == null) {
            return;
        }

        for (Vector3i position : positions) {

            if (position != null) {
                nextWave.add(
                        new Vector3i(position)
                );
            }
        }
    }


    // --------------------------------------------------
    // ACTIVE SOURCE SERIALIZATION
    // --------------------------------------------------

    private ActiveSourceData[] getActiveSources() {

        ActiveSourceData[] result =
                new ActiveSourceData[activeSources.size()];

        int index = 0;

        for (Map.Entry<Vector3i, Long> entry
                : activeSources.entrySet()) {

            result[index++] =
                    new ActiveSourceData(
                            entry.getKey(),
                            entry.getValue()
                    );
        }

        return result;
    }


    private void setActiveSources(
            ActiveSourceData[] sources
    ) {
        activeSources.clear();

        if (sources == null) {
            return;
        }

        for (ActiveSourceData source : sources) {

            if (source == null
                    || source.position == null) {
                continue;
            }

            activeSources.put(
                    new Vector3i(source.position),
                    source.remainingTicks
            );
        }
    }


    // --------------------------------------------------
    // CLONE
    // --------------------------------------------------

    @Override
    public @Nonnull SignalRuntimeResource clone() {
        return new SignalRuntimeResource(this);
    }


    // --------------------------------------------------
    // SERIALIZED ACTIVE SOURCE
    // --------------------------------------------------

    public static final class ActiveSourceData {

        public static final BuilderCodec<ActiveSourceData> CODEC =
                BuilderCodec.builder(
                                ActiveSourceData.class,
                                ActiveSourceData::new
                        )

                        .append(
                                new KeyedCodec<>(
                                        "Position",
                                        Vector3iUtil.CODEC
                                ),
                                (data, value) ->
                                        data.position = value,
                                data ->
                                        data.position
                        )
                        .add()

                        .append(
                                new KeyedCodec<>(
                                        "RemainingTicks",
                                        Codec.LONG
                                ),
                                (data, value) ->
                                        data.remainingTicks = value,
                                data ->
                                        data.remainingTicks
                        )
                        .add()

                        .build();


        private Vector3i position =
                new Vector3i();

        private long remainingTicks;


        public ActiveSourceData() {
        }


        private ActiveSourceData(
                @Nonnull Vector3i position,
                long remainingTicks
        ) {
            this.position =
                    new Vector3i(position);

            this.remainingTicks =
                    remainingTicks;
        }
    }
}