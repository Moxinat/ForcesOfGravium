package dev.moxinat.forcesofgravium.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.util.ChunkUtil;
import org.joml.Vector3d;
import org.joml.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.accessor.BlockAccessor;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.moxinat.forcesofgravium.connectable.core.ConnectableRuntimeAccessor;
import dev.moxinat.forcesofgravium.block.gravity.GravityPowderSpecialStateStore;
import dev.moxinat.forcesofgravium.block.gravity.GravityPowderSpecialStateStore.GravityPowderBlockData;
import dev.moxinat.forcesofgravium.block.siphon.GraviumSiphonStore;
import dev.moxinat.forcesofgravium.block.siphon.GraviumSiphonBlockRefresher;
import dev.moxinat.forcesofgravium.block.inverter.InverterSpecialStateStore;
import dev.moxinat.forcesofgravium.block.inverter.InverterSpecialStateStore.InverterData;
import dev.moxinat.forcesofgravium.block.siphon.GraviumSiphonLogic;
import dev.moxinat.forcesofgravium.persistence.WorldSaveFileService;
import dev.moxinat.forcesofgravium.connectable.registry.ConnectableRegistry;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

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
        if (args.length - startIndex >= 2 && "rotation".equalsIgnoreCase(args[startIndex])) {
            return handleRotationDebug(context, args, startIndex);
        }
        if (args.length - startIndex >= 2 && "siphon".equalsIgnoreCase(args[startIndex])) {
            return handleSiphon(context, args, startIndex);
        }
        context.sendMessage(Message.raw("Usage: /fog gpdist all | /fog gpdist here | /fog gpdist <x> <y> <z> | /fog invdist all | /fog invdist here | /fog invdist <x> <y> <z> | /fog rotation here | /fog rotation <x> <y> <z> | /fog siphon here | /fog siphon <x> <y> <z> | /fog siphon powered here <true|false> | /fog siphon powered <x> <y> <z> <true|false> | /fog siphon locked here <true|false> | /fog siphon locked <x> <y> <z> <true|false> | /fog saveinfo | /fog saveworld"));
        return CompletableFuture.completedFuture(null);
    }

    private void handleGravityPowderDistances(@Nonnull CommandContext context, String[] args, int startIndex) {
        if (!context.isPlayer()) {
            context.sendMessage(Message.raw("This command currently requires a player sender."));
            return;
        }

        PlayerCommandState playerState = playerState(context);
        if (playerState == null) {
            context.sendMessage(Message.raw("Could not resolve player world."));
            return;
        }

        World world = playerState.world();
        if (args.length - startIndex < 2) {
            context.sendMessage(Message.raw("Usage: /fog gpdist all | /fog gpdist here | /fog gpdist <x> <y> <z>"));
            return;
        }

        if ("all".equalsIgnoreCase(args[startIndex + 1])) {
            sendAllGravityPowderDistances(context, world);
            return;
        }

        if ("here".equalsIgnoreCase(args[startIndex + 1])) {
            Vector3i position = getPlayerBlockPosition(playerState.ref());
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

        PlayerCommandState playerState = playerState(context);
        if (playerState == null) {
            context.sendMessage(Message.raw("Could not resolve player world."));
            return;
        }

        World world = playerState.world();
        if (args.length - startIndex < 2) {
            context.sendMessage(Message.raw("Usage: /fog invdist all | /fog invdist here | /fog invdist <x> <y> <z>"));
            return;
        }

        if ("all".equalsIgnoreCase(args[startIndex + 1])) {
            sendAllInverterDistances(context, world);
            return;
        }

        if ("here".equalsIgnoreCase(args[startIndex + 1])) {
            Vector3i position = getPlayerBlockBelowPosition(playerState.ref());
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

        PlayerCommandState playerState = playerState(context);
        if (playerState == null) {
            context.sendMessage(Message.raw("Could not resolve player world."));
            return;
        }

        context.sendMessage(Message.raw("World save path: " + WorldSaveFileService.debugSaveFilePath(playerState.world())));
    }

    private void handleSaveWorld(@Nonnull CommandContext context) {
        if (!context.isPlayer()) {
            context.sendMessage(Message.raw("This command currently requires a player sender."));
            return;
        }

        PlayerCommandState playerState = playerState(context);
        if (playerState == null) {
            context.sendMessage(Message.raw("Could not resolve player world."));
            return;
        }

        World world = playerState.world();
        WorldSaveFileService.forceSaveWorld(world);
        context.sendMessage(Message.raw("Triggered forced save for world '" + world.getName() + "'."));
        context.sendMessage(Message.raw("World save path: " + WorldSaveFileService.debugSaveFilePath(world)));
        context.sendMessage(Message.raw("Save file exists: " + WorldSaveFileService.debugSaveFileExists(world)));
        String lastError = WorldSaveFileService.debugLastSaveError(world);
        if (!lastError.isBlank()) {
            context.sendMessage(Message.raw("Last save error: " + lastError));
        }
    }

    private CompletableFuture<Void> handleRotationDebug(@Nonnull CommandContext context, String[] args, int startIndex) {
        if (!context.isPlayer()) {
            context.sendMessage(Message.raw("This command currently requires a player sender."));
            return CompletableFuture.completedFuture(null);
        }

        Ref<EntityStore> playerRef = context.senderAsPlayerRef();
        if (playerRef == null || !playerRef.isValid()) {
            context.sendMessage(Message.raw("Could not resolve player world."));
            return CompletableFuture.completedFuture(null);
        }

        World world = playerRef.getStore().getExternalData().getWorld();
        if (world == null) {
            context.sendMessage(Message.raw("Could not resolve player world."));
            return CompletableFuture.completedFuture(null);
        }

        if ("here".equalsIgnoreCase(args[startIndex + 1])) {
            return runRotationDebugOnWorldThread(context, world, () -> getPlayerBlockBelowPosition(playerRef));
        }

        if (args.length - startIndex < 4) {
            context.sendMessage(Message.raw("Usage: /fog rotation here | /fog rotation <x> <y> <z>"));
            return CompletableFuture.completedFuture(null);
        }

        try {
            int x = Integer.parseInt(args[startIndex + 1]);
            int y = Integer.parseInt(args[startIndex + 2]);
            int z = Integer.parseInt(args[startIndex + 3]);
            Vector3i position = new Vector3i(x, y, z);
            return runRotationDebugOnWorldThread(context, world, () -> position);
        } catch (NumberFormatException exception) {
            context.sendMessage(Message.raw("Coordinates must be integers."));
            return CompletableFuture.completedFuture(null);
        }
    }

    private CompletableFuture<Void> runRotationDebugOnWorldThread(@Nonnull CommandContext context, @Nonnull World world, @Nonnull Supplier<Vector3i> positionSupplier) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        world.execute(() -> {
            try {
                sendRotationDebug(context, world, positionSupplier.get());
                future.complete(null);
            } catch (Exception exception) {
                context.sendMessage(Message.raw("Rotation debug failed: " + exception.getClass().getSimpleName() + ": " + exception.getMessage()));
                future.complete(null);
            }
        });
        return future;
    }

    private void sendRotationDebug(@Nonnull CommandContext context, @Nonnull World world, @Nonnull Vector3i position) {
        BlockType blockType = world.getBlockType(position.x(), position.y(), position.z());
        RotationTuple storedRotation = ConnectableRuntimeAccessor.getRuntimeData(world, position)
                .map(data -> data.rotation())
                .orElse(null);
        RotationTuple worldRotation = getWorldRotation(world, position);

        context.sendMessage(Message.raw("Rotation debug at " + formatPosition(position)));
        context.sendMessage(Message.raw("block=" + (blockType != null ? blockType.getId() : "null")));
        context.sendMessage(Message.raw("storedRotation=" + storedRotation));
        context.sendMessage(Message.raw("worldRotation=" + worldRotation));
    }

    private static @Nullable RotationTuple getWorldRotation(@Nonnull World world, @Nonnull Vector3i position) {
        BlockAccessor blockAccessor = world.getChunk(ChunkUtil.indexChunkFromBlock(position.x(), position.z()));
        if (blockAccessor == null) {
            return null;
        }
        return blockAccessor.getRotation(position.x(), position.y(), position.z());
    }

    private CompletableFuture<Void> handleSiphon(@Nonnull CommandContext context, String[] args, int startIndex) {
        if (!context.isPlayer()) {
            context.sendMessage(Message.raw("This command currently requires a player sender."));
            return CompletableFuture.completedFuture(null);
        }

        PlayerCommandState playerState = playerState(context);
        if (playerState == null) {
            context.sendMessage(Message.raw("Could not resolve player world."));
            return CompletableFuture.completedFuture(null);
        }

        World world = playerState.world();
        if ("powered".equalsIgnoreCase(args[startIndex + 1])) {
            return handleSiphonBooleanState(context, world, playerState.ref(), args, startIndex, "powered");
        }
        if ("locked".equalsIgnoreCase(args[startIndex + 1])) {
            return handleSiphonBooleanState(context, world, playerState.ref(), args, startIndex, "locked");
        }

        if ("here".equalsIgnoreCase(args[startIndex + 1])) {
            return runOnWorldThread(context, world, () -> getPlayerBlockBelowPosition(playerState.ref()));
        }

        if (args.length - startIndex < 4) {
            context.sendMessage(Message.raw("Usage: /fog siphon here | /fog siphon <x> <y> <z>"));
            return CompletableFuture.completedFuture(null);
        }

        try {
            int x = Integer.parseInt(args[startIndex + 1]);
            int y = Integer.parseInt(args[startIndex + 2]);
            int z = Integer.parseInt(args[startIndex + 3]);
            Vector3i position = new Vector3i(x, y, z);
            return runOnWorldThread(context, world, () -> position);
        } catch (NumberFormatException exception) {
            context.sendMessage(Message.raw("Coordinates must be integers."));
            return CompletableFuture.completedFuture(null);
        }
    }

    private CompletableFuture<Void> handleSiphonBooleanState(@Nonnull CommandContext context, @Nonnull World world, @Nonnull Ref<EntityStore> playerRef, String[] args, int startIndex, @Nonnull String stateName) {
        if (args.length - startIndex < 4) {
            context.sendMessage(Message.raw("Usage: /fog siphon " + stateName + " here <true|false> | /fog siphon " + stateName + " <x> <y> <z> <true|false>"));
            return CompletableFuture.completedFuture(null);
        }

        if ("here".equalsIgnoreCase(args[startIndex + 2])) {
            Boolean value = parseBoolean(args[startIndex + 3]);
            if (value == null) {
                context.sendMessage(Message.raw(stateName + " must be true or false."));
                return CompletableFuture.completedFuture(null);
            }
            return runSiphonBooleanStateOnWorldThread(context, world, () -> getPlayerBlockBelowPosition(playerRef), stateName, value);
        }

        if (args.length - startIndex < 6) {
            context.sendMessage(Message.raw("Usage: /fog siphon " + stateName + " here <true|false> | /fog siphon " + stateName + " <x> <y> <z> <true|false>"));
            return CompletableFuture.completedFuture(null);
        }

        try {
            int x = Integer.parseInt(args[startIndex + 2]);
            int y = Integer.parseInt(args[startIndex + 3]);
            int z = Integer.parseInt(args[startIndex + 4]);
            Boolean value = parseBoolean(args[startIndex + 5]);
            if (value == null) {
                context.sendMessage(Message.raw(stateName + " must be true or false."));
                return CompletableFuture.completedFuture(null);
            }
            Vector3i position = new Vector3i(x, y, z);
            return runSiphonBooleanStateOnWorldThread(context, world, () -> position, stateName, value);
        } catch (NumberFormatException exception) {
            context.sendMessage(Message.raw("Coordinates must be integers."));
            return CompletableFuture.completedFuture(null);
        }
    }

    private CompletableFuture<Void> runSiphonBooleanStateOnWorldThread(@Nonnull CommandContext context, @Nonnull World world, @Nonnull Supplier<Vector3i> positionSupplier, @Nonnull String stateName, boolean value) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        world.execute(() -> {
            try {
                setSiphonBooleanState(context, world, positionSupplier.get(), stateName, value);
                future.complete(null);
            } catch (Exception exception) {
                context.sendMessage(Message.raw("Siphon " + stateName + " update failed: " + exception.getClass().getSimpleName() + ": " + exception.getMessage()));
                future.complete(null);
            }
        });
        return future;
    }

    private void setSiphonBooleanState(@Nonnull CommandContext context, @Nonnull World world, @Nonnull Vector3i position, @Nonnull String stateName, boolean value) {
        BlockType blockType = world.getBlockType(position.x(), position.y(), position.z());
        if (blockType == null || !ConnectableRegistry.isGraviumSiphonId(blockType.getId())) {
            context.sendMessage(Message.raw("No gravium siphon at " + formatPosition(position) + "."));
            return;
        }

        if ("powered".equals(stateName)) {
            GraviumSiphonStore.setPowered(world, position, value);
        } else if ("locked".equals(stateName)) {
            GraviumSiphonStore.setLocked(world, position, value);
        }
        GraviumSiphonBlockRefresher.refreshAt(world, position);
        context.sendMessage(Message.raw("Siphon at " + formatPosition(position) + " " + stateName + "=" + value));
    }

    private CompletableFuture<Void> runOnWorldThread(@Nonnull CommandContext context, @Nonnull World world, @Nonnull Supplier<Vector3i> positionSupplier) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        world.execute(() -> {
            try {
                sendSiphonResult(context, world, positionSupplier.get());
                future.complete(null);
            } catch (Exception exception) {
                context.sendMessage(Message.raw("Siphon debug failed: " + exception.getClass().getSimpleName() + ": " + exception.getMessage()));
                future.complete(null);
            }
        });
        return future;
    }

    private void sendSiphonResult(@Nonnull CommandContext context, @Nonnull World world, @Nonnull Vector3i position) {
        BlockType blockType = world.getBlockType(position.x(), position.y(), position.z());
        if (blockType != null && ConnectableRegistry.isGraviumSiphonId(blockType.getId())) {
            GraviumSiphonStore.add(world, position);
        }
        GraviumSiphonLogic.SiphonMoveResult result = GraviumSiphonLogic.transferOneItem(world, position);
        context.sendMessage(Message.raw("Siphon at " + formatPosition(position) + ": " + result));
    }

    private void sendAllGravityPowderDistances(@Nonnull CommandContext context, @Nonnull World world) {
        Map<Vector3i, GravityPowderBlockData> snapshot = GravityPowderSpecialStateStore.snapshotForWorld(world);
        if (snapshot.isEmpty()) {
            context.sendMessage(Message.raw("No gravity powder data found in world '" + world.getName() + "'."));
            return;
        }

        List<Map.Entry<Vector3i, GravityPowderBlockData>> entries = snapshot.entrySet().stream()
                .sorted(Comparator
                        .comparingInt((Map.Entry<Vector3i, GravityPowderBlockData> entry) -> entry.getKey().y())
                        .thenComparingInt(entry -> entry.getKey().x())
                        .thenComparingInt(entry -> entry.getKey().z()))
                .toList();

        context.sendMessage(Message.raw("Gravity powder entries in world '" + world.getName() + "': " + entries.size()));
        for (Map.Entry<Vector3i, GravityPowderBlockData> entry : entries) {
            context.sendMessage(Message.raw(formatEntry(entry.getKey(), entry.getValue())));
        }
    }

    private void sendSingleGravityPowderDistance(@Nonnull CommandContext context, @Nonnull World world, @Nonnull Vector3i position) {
        GravityPowderBlockData data = GravityPowderSpecialStateStore.get(world, position);
        if (data == null) {
            context.sendMessage(Message.raw("No gravity powder data at " + formatPosition(position) + "."));
            return;
        }

        context.sendMessage(Message.raw(formatEntry(position, data)));
    }

    private void sendAllInverterDistances(@Nonnull CommandContext context, @Nonnull World world) {
        Map<Vector3i, InverterData> snapshot = InverterSpecialStateStore.snapshotForWorld(world);
        if (snapshot.isEmpty()) {
            context.sendMessage(Message.raw("No inverter data found in world '" + world.getName() + "'."));
            return;
        }

        List<Map.Entry<Vector3i, InverterData>> entries = snapshot.entrySet().stream()
                .sorted(Comparator
                        .comparingInt((Map.Entry<Vector3i, InverterData> entry) -> entry.getKey().y())
                        .thenComparingInt(entry -> entry.getKey().x())
                        .thenComparingInt(entry -> entry.getKey().z()))
                .toList();

        context.sendMessage(Message.raw("Inverter entries in world '" + world.getName() + "': " + entries.size()));
        for (Map.Entry<Vector3i, InverterData> entry : entries) {
            context.sendMessage(Message.raw(formatEntry(entry.getKey(), entry.getValue())));
        }
    }

    private void sendSingleInverterDistance(@Nonnull CommandContext context, @Nonnull World world, @Nonnull Vector3i position) {
        InverterData data = InverterSpecialStateStore.get(world, position);
        if (data == null) {
            context.sendMessage(Message.raw("No inverter data at " + formatPosition(position) + "."));
            return;
        }

        context.sendMessage(Message.raw(formatEntry(position, data)));
    }

    private static String formatEntry(Vector3i position, GravityPowderBlockData data) {
        return formatPosition(position)
                + " instantState=" + data.instantState()
                + " waveState=" + data.waveState()
                + " effectiveState=" + data.effectiveState()
                + " connectionsMask=" + data.connectionsMask();
    }

    private static String formatEntry(Vector3i position, InverterData data) {
        return formatPosition(position)
                + " mode=" + data.currentMode()
                + " invertEnabled=" + data.invertEnabled()
                + " toggleInputActive=" + data.toggleInputActive()
                + " lastToggleInputMode=" + data.lastToggleInputMode();
    }

    private static String formatPosition(Vector3i position) {
        return "(" + position.x() + "," + position.y() + "," + position.z() + ")";
    }

    private static @Nullable Boolean parseBoolean(String value) {
        if ("true".equalsIgnoreCase(value)) {
            return Boolean.TRUE;
        }
        if ("false".equalsIgnoreCase(value)) {
            return Boolean.FALSE;
        }
        return null;
    }

    private static Vector3i getPlayerBlockPosition(Ref<EntityStore> playerRef) {
        TransformComponent transform = playerRef.getStore().getComponent(playerRef, TransformComponent.getComponentType());
        return blockPosition(transform.getPosition());
    }

    private static Vector3i getPlayerBlockBelowPosition(Ref<EntityStore> playerRef) {
        Vector3i position = getPlayerBlockPosition(playerRef);
        return new Vector3i(position.x(), position.y() - 1, position.z());
    }

    private static Vector3i blockPosition(Vector3d position) {
        return new Vector3i(
                (int) Math.floor(position.x()),
                (int) Math.floor(position.y()),
                (int) Math.floor(position.z())
        );
    }

    private static @Nullable PlayerCommandState playerState(CommandContext context) {
        Ref<EntityStore> playerRef = context.senderAsPlayerRef();
        if (playerRef == null || !playerRef.isValid()) {
            return null;
        }

        Player player = playerRef.getStore().getComponent(playerRef, Player.getComponentType());
        World world = playerRef.getStore().getExternalData().getWorld();
        if (player == null || world == null) {
            return null;
        }
        return new PlayerCommandState(world, playerRef, player);
    }

    private record PlayerCommandState(@Nonnull World world, @Nonnull Ref<EntityStore> ref, @Nonnull Player player) {
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
