package dev.moxinat.forcesofgravium.logic.network;

import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.World;
import dev.moxinat.forcesofgravium.data.GravityPowderBlockDataStore;
import dev.moxinat.forcesofgravium.data.InverterDataStore;
import dev.moxinat.forcesofgravium.logic.gravity.GravityPowderBlockRefresher;
import dev.moxinat.forcesofgravium.logic.inverter.InverterBlockRefresher;
import dev.moxinat.forcesofgravium.registry.ConnectableRegistry;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class ConnectablePropagationScheduler {

    private static final Set<World> PENDING_RECOMPUTE = ConcurrentHashMap.newKeySet();

    private ConnectablePropagationScheduler() {
    }

    public static void onConnectablePlaced(World world, Vector3i target) {
        enqueueRecompute(world);
    }

    public static void onConnectablePlaced(World world, Vector3i target, Player debugPlayer) {
        enqueueRecompute(world);
    }

    public static void onConnectableBroken(World world, Vector3i target) {
        enqueueRecompute(world);
    }

    public static void tickPropagation() {
        Set<World> worlds = new HashSet<>(PENDING_RECOMPUTE);
        PENDING_RECOMPUTE.removeAll(worlds);
        for (World world : worlds) {
            tickWorld(world);
        }
    }

    public static boolean isNotGravityPowder(BlockType blockType) {
        return blockType == null || !ConnectableRegistry.isGravityPowderId(blockType.getId());
    }

    public static boolean isNotInverter(BlockType blockType) {
        return blockType == null || !ConnectableRegistry.isInverterId(blockType.getId());
    }

    private static void tickWorld(World world) {
        ConnectableSignalRecalculator.recompute(world);
        for (Vector3i cable : GravityPowderBlockDataStore.snapshotForWorld(world).keySet()) {
            GravityPowderBlockRefresher.refreshAt(world, cable);
        }
        for (Vector3i inverter : InverterDataStore.snapshotForWorld(world).keySet()) {
            InverterBlockRefresher.refreshAt(world, inverter);
        }
    }

    private static void enqueueRecompute(World world) {
        PENDING_RECOMPUTE.add(world);
    }
}
