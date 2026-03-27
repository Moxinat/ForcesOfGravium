package dev.moxinat.forcesofgravium.logic.gravity;

import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import dev.moxinat.forcesofgravium.data.GravityPowderBlockDataStore;
import dev.moxinat.forcesofgravium.data.GravityPowderBlockDataStore.GravityPowderBlockData;
import dev.moxinat.forcesofgravium.data.InverterDataStore;
import dev.moxinat.forcesofgravium.data.InverterDataStore.InverterData;
import dev.moxinat.forcesofgravium.logic.network.ConnectableNeighborResolver;
import dev.moxinat.forcesofgravium.registry.ConnectableBlockRoles;
import dev.moxinat.forcesofgravium.registry.ConnectableRegistry;

import java.util.Objects;

public final class GravityPowderStateCalculator {
    public static final String MODE_OFF = "off";
    public static final String MODE_PUSH = "push";
    public static final String MODE_PULL = "pull";
    public static final int WAVE_TICKS = 2;

    private GravityPowderStateCalculator() {
    }

    public static GravityPowderStateUpdate computeStateUpdate(World world, Vector3i position) {
        GravityPowderBlockData selfData = GravityPowderBlockDataStore.getOrCreate(world, position);
        if (GravityPowderBlockDataStore.hasActiveWave(selfData)) {
            return updateForActiveWave(world, position, selfData);
        }
        return updateForStableCable(world, position, selfData);
    }

    private static GravityPowderStateUpdate updateForActiveWave(World world, Vector3i position, GravityPowderBlockData selfData) {
        String overrideState = overrideWaveState(selfData.state(), resolveDrivenMode(world, position));
        if (overrideState != null) {
            return new GravityPowderStateUpdate(position, overrideState, WAVE_TICKS);
        }

        int remainingTicks = Math.max(0, selfData.stateTicksRemaining() - 1);
        if (remainingTicks == 0) {
            String resolvedMode = GravityPowderBlockDataStore.modeForState(selfData.state());
            return new GravityPowderStateUpdate(
                    position,
                    GravityPowderBlockDataStore.stableStateForMode(resolvedMode),
                    0
            );
        }
        return new GravityPowderStateUpdate(
                position,
                selfData.state(),
                remainingTicks
        );
    }

    static String overrideWaveState(String currentState, String drivenMode) {
        if (GravityPowderBlockDataStore.STATE_PULL_WAVE.equals(currentState)
                && MODE_PUSH.equals(drivenMode)) {
            return GravityPowderBlockDataStore.STATE_PUSH_WAVE;
        }
        return null;
    }

    private static GravityPowderStateUpdate updateForStableCable(World world, Vector3i position, GravityPowderBlockData selfData) {
        String drivenMode = resolveDrivenMode(world, position);
        String currentMode = GravityPowderBlockDataStore.effectiveMode(selfData);
        if (drivenMode == null || drivenMode.equals(currentMode)) {
            return new GravityPowderStateUpdate(
                    position,
                    selfData.state(),
                    0
            );
        }

        String waveState = GravityPowderBlockDataStore.waveStateForMode(drivenMode);
        return new GravityPowderStateUpdate(
                position,
                waveState == null ? selfData.state() : waveState,
                waveState == null ? 0 : WAVE_TICKS
        );
    }

    static String resolveDrivenMode(World world, Vector3i position) {
        boolean seesOffWave = false;
        boolean seesSource = false;
        boolean seesPushDriver = false;
        boolean seesPullDriver = false;

        for (Vector3i neighborPosition : ConnectableNeighborResolver.positionsAround(position)) {
            if (neighborPosition.equals(position)) {
                continue;
            }

            BlockType neighborType = world.getBlockType(
                    neighborPosition.getX(),
                    neighborPosition.getY(),
                    neighborPosition.getZ()
            );
            if (neighborType == null) {
                continue;
            }

            if (ConnectableBlockRoles.isSource(neighborType.getId())) {
                seesSource = true;
                continue;
            }

            if (ConnectableRegistry.isGravityPowderId(neighborType.getId())) {
                GravityPowderBlockData neighborData = GravityPowderBlockDataStore.get(world, neighborPosition);
                if (neighborData == null) {
                    continue;
                }
                if (GravityPowderBlockDataStore.STATE_OFF_WAVE.equals(neighborData.state())) {
                    seesOffWave = true;
                    continue;
                }
                if (GravityPowderBlockDataStore.STATE_PUSH_WAVE.equals(neighborData.state())) {
                    seesPushDriver = true;
                    continue;
                }
                if (GravityPowderBlockDataStore.STATE_PULL_WAVE.equals(neighborData.state())) {
                    seesPullDriver = true;
                }
                continue;
            }

            if (!ConnectableRegistry.isInverterId(neighborType.getId())) {
                continue;
            }

            Vector3i frontPosition = ConnectableNeighborResolver.adjacentPositionForLocalSide(
                    world,
                    neighborPosition,
                    ConnectableRegistry.SIDE_FRONT
            );
            if (!frontPosition.equals(position)) {
                continue;
            }

            InverterData inverterData = InverterDataStore.get(world, neighborPosition);
            if (inverterData == null) {
                continue;
            }
            if (MODE_PUSH.equals(inverterData.currentMode())) {
                seesPushDriver = true;
            } else if (MODE_PULL.equals(inverterData.currentMode())) {
                seesPullDriver = true;
            }
        }

        return resolveDrivenMode(seesOffWave, seesSource, seesPushDriver, seesPullDriver);
    }

    static String resolveDrivenMode(
            boolean seesOffWave,
            boolean seesSource,
            boolean seesPushDriver,
            boolean seesPullDriver
    ) {
        if (seesOffWave) {
            return MODE_OFF;
        }
        if (seesSource || seesPushDriver) {
            return MODE_PUSH;
        }
        if (seesPullDriver) {
            return MODE_PULL;
        }
        return null;
    }

    public record GravityPowderStateUpdate(
            Vector3i position,
            String nextState,
            int nextStateTicksRemaining
    ) {
        public GravityPowderStateUpdate {
            Objects.requireNonNull(position, "position");
            Objects.requireNonNull(nextState, "nextState");
        }
    }
}
