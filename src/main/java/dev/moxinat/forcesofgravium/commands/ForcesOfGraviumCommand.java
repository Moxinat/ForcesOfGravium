package dev.moxinat.forcesofgravium.commands;

import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.World;
import dev.moxinat.forcesofgravium.data.GravityPowderBlockDataStore;
import dev.moxinat.forcesofgravium.data.GravityPowderBlockDataStore.GravityPowderBlockData;
import dev.moxinat.forcesofgravium.data.GravityPowderBlockDataStore.PositionDistance;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class ForcesOfGraviumCommand extends AbstractCommand {

    public ForcesOfGraviumCommand(String name, String description) {
        super(name, description);
        setAllowsExtraArguments(true);
    }

    @Nullable
    @Override
    protected CompletableFuture<Void> execute(@Nonnull CommandContext context) {
        String[] args = tokenize(context.getInputString());
        int startIndex = commandArgStartIndex(args);
        if (args.length - startIndex >= 2 && "gpdist".equalsIgnoreCase(args[startIndex])) {
            handleGravityPowderDistances(context, args, startIndex);
            return CompletableFuture.completedFuture(null);
        }

        context.sendMessage(Message.raw("Usage: /fog gpdist all | /fog gpdist here | /fog gpdist <x> <y> <z>"));
        return CompletableFuture.completedFuture(null);
    }

    private void handleGravityPowderDistances(@Nonnull CommandContext context, String[] args, int startIndex) {
        if (!context.isPlayer()) {
            context.sendMessage(Message.raw("This command currently requires a player sender."));
            return;
        }

        Player player = context.senderAs(Player.class);
        if (player.getWorld() == null) {
            context.sendMessage(Message.raw("Could not resolve player world."));
            return;
        }

        World world = player.getWorld();
        if (args.length - startIndex < 2) {
            context.sendMessage(Message.raw("Usage: /fog gpdist all | /fog gpdist here | /fog gpdist <x> <y> <z>"));
            return;
        }

        if ("all".equalsIgnoreCase(args[startIndex + 1])) {
            sendAllGravityPowderDistances(context, world);
            return;
        }

        if ("here".equalsIgnoreCase(args[startIndex + 1])) {
            Vector3i position = getPlayerBlockPosition(player);
            sendSingleGravityPowderDistance(context, world, position);
            return;
        }

        if (args.length - startIndex < 4) {
            context.sendMessage(Message.raw("Usage: /fog gpdist all | /fog gpdist here | /fog gpdist <x> <y> <z>"));
            return;
        }

        try {
            int x = Integer.parseInt(args[startIndex + 1]);
            int y = Integer.parseInt(args[startIndex + 2]);
            int z = Integer.parseInt(args[startIndex + 3]);
            sendSingleGravityPowderDistance(context, world, new Vector3i(x, y, z));
        } catch (NumberFormatException exception) {
            context.sendMessage(Message.raw("Coordinates must be integers."));
        }
    }

    private void sendAllGravityPowderDistances(@Nonnull CommandContext context, @Nonnull World world) {
        Map<Vector3i, GravityPowderBlockData> snapshot = GravityPowderBlockDataStore.snapshotForWorld(world);
        if (snapshot.isEmpty()) {
            context.sendMessage(Message.raw("No gravity powder data found in world '" + world.getName() + "'."));
            return;
        }

        List<Map.Entry<Vector3i, GravityPowderBlockData>> entries = snapshot.entrySet().stream()
                .sorted(Comparator
                        .comparingInt((Map.Entry<Vector3i, GravityPowderBlockData> entry) -> entry.getKey().getY())
                        .thenComparingInt(entry -> entry.getKey().getX())
                        .thenComparingInt(entry -> entry.getKey().getZ()))
                .toList();

        context.sendMessage(Message.raw("Gravity powder entries in world '" + world.getName() + "': " + entries.size()));
        for (Map.Entry<Vector3i, GravityPowderBlockData> entry : entries) {
            context.sendMessage(Message.raw(formatEntry(entry.getKey(), entry.getValue())));
        }
    }

    private void sendSingleGravityPowderDistance(@Nonnull CommandContext context, @Nonnull World world, @Nonnull Vector3i position) {
        GravityPowderBlockData data = GravityPowderBlockDataStore.get(world, position);
        if (data == null) {
            context.sendMessage(Message.raw("No gravity powder data at " + formatPosition(position) + "."));
            return;
        }

        context.sendMessage(Message.raw(formatEntry(position, data)));
    }

    private static String formatEntry(Vector3i position, GravityPowderBlockData data) {
        return formatPosition(position)
                + " mode=" + data.currentMode()
                + " stable=" + data.stable()
                + " distances=" + formatDistances(data.positionDistances());
    }

    private static String formatDistances(List<PositionDistance> distances) {
        if (distances.isEmpty()) {
            return "[]";
        }

        return distances.stream()
                .map(distance -> "(" + distance.x() + "," + distance.y() + "," + distance.z() + " -> " + distance.distance() + ")")
                .reduce((left, right) -> left + ", " + right)
                .map(value -> "[" + value + "]")
                .orElse("[]");
    }

    private static String formatPosition(Vector3i position) {
        return "(" + position.getX() + "," + position.getY() + "," + position.getZ() + ")";
    }

    @SuppressWarnings("removal")
    private static Vector3i getPlayerBlockPosition(Player player) {
        return player.getTransformComponent().getPosition().toVector3i();
    }

    private static String[] tokenize(String input) {
        if (input == null || input.isBlank()) {
            return new String[0];
        }
        return input.trim().split("\\s+");
    }

    private int commandArgStartIndex(String[] args) {
        if (args.length == 0) {
            return 0;
        }
        String commandName = getName();
        if (commandName != null && commandName.equalsIgnoreCase(args[0])) {
            return 1;
        }
        return 0;
    }
}
