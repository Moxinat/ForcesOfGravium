package dev.moxinat.forcesofgravium.commands;

import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.World;
import dev.moxinat.forcesofgravium.data.GravityPowderBlockDataStore;
import dev.moxinat.forcesofgravium.data.GravityPowderBlockDataStore.GravityPowderBlockData;
import dev.moxinat.forcesofgravium.data.InverterDataStore;
import dev.moxinat.forcesofgravium.data.InverterDataStore.InverterData;
import dev.moxinat.forcesofgravium.persistence.WorldSaveFileService;

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
        if (args.length - startIndex >= 2 && "invdist".equalsIgnoreCase(args[startIndex])) {
            handleInverterDistances(context, args, startIndex);
            return CompletableFuture.completedFuture(null);
        }
        if (args.length - startIndex >= 1 && "saveinfo".equalsIgnoreCase(args[startIndex])) {
            handleSaveInfo(context);
            return CompletableFuture.completedFuture(null);
        }
        if (args.length - startIndex >= 1 && "saveworld".equalsIgnoreCase(args[startIndex])) {
            handleSaveWorld(context);
            return CompletableFuture.completedFuture(null);
        }
        context.sendMessage(Message.raw("Usage: /fog gpdist all | /fog gpdist here | /fog gpdist <x> <y> <z> | /fog invdist all | /fog invdist here | /fog invdist <x> <y> <z> | /fog saveinfo | /fog saveworld"));
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

    private void handleInverterDistances(@Nonnull CommandContext context, String[] args, int startIndex) {
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
            context.sendMessage(Message.raw("Usage: /fog invdist all | /fog invdist here | /fog invdist <x> <y> <z>"));
            return;
        }

        if ("all".equalsIgnoreCase(args[startIndex + 1])) {
            sendAllInverterDistances(context, world);
            return;
        }

        if ("here".equalsIgnoreCase(args[startIndex + 1])) {
            Vector3i position = getPlayerBlockBelowPosition(player);
            sendSingleInverterDistance(context, world, position);
            return;
        }

        if (args.length - startIndex < 4) {
            context.sendMessage(Message.raw("Usage: /fog invdist all | /fog invdist here | /fog invdist <x> <y> <z>"));
            return;
        }

        try {
            int x = Integer.parseInt(args[startIndex + 1]);
            int y = Integer.parseInt(args[startIndex + 2]);
            int z = Integer.parseInt(args[startIndex + 3]);
            sendSingleInverterDistance(context, world, new Vector3i(x, y, z));
        } catch (NumberFormatException exception) {
            context.sendMessage(Message.raw("Coordinates must be integers."));
        }
    }

    private void handleSaveInfo(@Nonnull CommandContext context) {
        if (!context.isPlayer()) {
            context.sendMessage(Message.raw("This command currently requires a player sender."));
            return;
        }

        Player player = context.senderAs(Player.class);
        if (player.getWorld() == null) {
            context.sendMessage(Message.raw("Could not resolve player world."));
            return;
        }

        context.sendMessage(Message.raw("World save path: " + WorldSaveFileService.debugSaveFilePath(player.getWorld())));
    }

    private void handleSaveWorld(@Nonnull CommandContext context) {
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
        WorldSaveFileService.forceSaveWorld(world);
        context.sendMessage(Message.raw("Triggered forced save for world '" + world.getName() + "'."));
        context.sendMessage(Message.raw("World save path: " + WorldSaveFileService.debugSaveFilePath(world)));
        context.sendMessage(Message.raw("Save file exists: " + WorldSaveFileService.debugSaveFileExists(world)));
        String lastError = WorldSaveFileService.debugLastSaveError(world);
        if (!lastError.isBlank()) {
            context.sendMessage(Message.raw("Last save error: " + lastError));
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

    private void sendAllInverterDistances(@Nonnull CommandContext context, @Nonnull World world) {
        Map<Vector3i, InverterData> snapshot = InverterDataStore.snapshotForWorld(world);
        if (snapshot.isEmpty()) {
            context.sendMessage(Message.raw("No inverter data found in world '" + world.getName() + "'."));
            return;
        }

        List<Map.Entry<Vector3i, InverterData>> entries = snapshot.entrySet().stream()
                .sorted(Comparator
                        .comparingInt((Map.Entry<Vector3i, InverterData> entry) -> entry.getKey().getY())
                        .thenComparingInt(entry -> entry.getKey().getX())
                        .thenComparingInt(entry -> entry.getKey().getZ()))
                .toList();

        context.sendMessage(Message.raw("Inverter entries in world '" + world.getName() + "': " + entries.size()));
        for (Map.Entry<Vector3i, InverterData> entry : entries) {
            context.sendMessage(Message.raw(formatEntry(entry.getKey(), entry.getValue())));
        }
    }

    private void sendSingleInverterDistance(@Nonnull CommandContext context, @Nonnull World world, @Nonnull Vector3i position) {
        InverterData data = InverterDataStore.get(world, position);
        if (data == null) {
            context.sendMessage(Message.raw("No inverter data at " + formatPosition(position) + "."));
            return;
        }

        context.sendMessage(Message.raw(formatEntry(position, data)));
    }

    private static String formatEntry(Vector3i position, GravityPowderBlockData data) {
        return formatPosition(position)
                + " push=" + data.push()
                + " pull=" + data.pull()
                + " mode=" + GravityPowderBlockDataStore.effectiveMode(data)
                + " connectionsMask=" + data.connectionsMask();
    }

    private static String formatEntry(Vector3i position, InverterData data) {
        return formatPosition(position)
                + " mode=" + data.currentMode()
                + " invertEnabled=" + data.invertEnabled()
                + " toggleInputActive=" + data.toggleInputActive();
    }

    private static String formatPosition(Vector3i position) {
        return "(" + position.getX() + "," + position.getY() + "," + position.getZ() + ")";
    }

    @SuppressWarnings("removal")
    private static Vector3i getPlayerBlockPosition(Player player) {
        return player.getTransformComponent().getPosition().toVector3i();
    }

    @SuppressWarnings("removal")
    private static Vector3i getPlayerBlockBelowPosition(Player player) {
        Vector3i position = player.getTransformComponent().getPosition().toVector3i();
        return new Vector3i(position.getX(), position.getY() - 1, position.getZ());
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
