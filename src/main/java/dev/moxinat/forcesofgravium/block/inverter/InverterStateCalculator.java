package dev.moxinat.forcesofgravium.block.inverter;

import com.hypixel.hytale.server.core.universe.world.World;
import dev.moxinat.forcesofgravium.connectable.SignalState;
import dev.moxinat.forcesofgravium.data.Nodes;
import org.joml.Vector3i;

public final class InverterStateCalculator {

    private InverterStateCalculator() {
    }

    public static void handleControlChange(
            World world,
            Vector3i inverterPosition,
            Vector3i controlSourcePosition
    ) {
        Nodes.Node controlSource = Nodes.get(world, controlSourcePosition);

        if (controlSource == null
                || controlSource.effectiveState() == SignalState.OFF) {
            return;
        }

        Nodes.Node inverter = Nodes.get(world, inverterPosition);

        if (inverter == null) {
            return;
        }

        Nodes.put(
                world,
                inverter.withInvertEnabled(!inverter.invertEnabled())
        );
    }
}
