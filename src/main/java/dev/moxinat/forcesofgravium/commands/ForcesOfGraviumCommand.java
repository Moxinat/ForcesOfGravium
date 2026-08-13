package dev.moxinat.forcesofgravium.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.accessor.BlockAccessor;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.moxinat.forcesofgravium.data.Nodes;
import dev.moxinat.forcesofgravium.persistence.WorldSaveFileService;
import org.joml.Vector3d;
import org.joml.Vector3i;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
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

        if (args.length - startIndex >= 1
                && "saveinfo".equalsIgnoreCase(args[startIndex])) {
            handleSaveInfo(context);
            return CompletableFuture.completedFuture(null);
        }

        if (args.length - startIndex >= 1
                && "saveworld".equalsIgnoreCase(args[startIndex])) {
            handleSaveWorld(context);
            return CompletableFuture.completedFuture(null);
        }

        if (args.length - startIndex >= 2
                && "rotation".equalsIgnoreCase(args[startIndex])) {
            return handleRotationDebug(context, args, startIndex);
        }

        context.sendMessage(Message.raw(
                "Usage: /fog rotation here | /fog rotation <x> <y> <z> | /fog saveinfo | /fog saveworld"
        ));

        return CompletableFuture.completedFuture(null);
    }

    private void handleSaveInfo(@Nonnull CommandContext context) {
        PlayerCommandState playerState = playerState(context);

        if (playerState == null) {
            context.sendMessage(Message.raw(
                    "This command currently requires a player sender."
            ));
            return;
        }

        context.sendMessage(Message.raw(
                "World save path: "
                        + WorldSaveFileService.debugSaveFilePath(
                                playerState.world()
                        )
        ));
    }

    private void handleSaveWorld(@Nonnull CommandContext context) {
        PlayerCommandState playerState = playerState(context);

        if (playerState == null) {
            context.sendMessage(Message.raw(
                    "This command currently requires a player sender."
            ));
            return;
        }

        World world = playerState.world();

        WorldSaveFileService.forceSaveWorld(world);

        context.sendMessage(Message.raw(
                "Triggered forced save for world '"
                        + world.getName()
                        + "'."
        ));

        context.sendMessage(Message.raw(
                "World save path: "
                        + WorldSaveFileService.debugSaveFilePath(world)
        ));

        context.sendMessage(Message.raw(
                "Save file exists: "
                        + WorldSaveFileService.debugSaveFileExists(world)
        ));

        String lastError =
                WorldSaveFileService.debugLastSaveError(world);

        if (!lastError.isBlank()) {
            context.sendMessage(Message.raw(
                    "Last save error: " + lastError
            ));
        }
    }

    private CompletableFuture<Void> handleRotationDebug(
            @Nonnull CommandContext context,
            @Nonnull String[] args,
            int startIndex
    ) {
        PlayerCommandState playerState = playerState(context);

        if (playerState == null) {
            context.sendMessage(Message.raw(
                    "This command currently requires a player sender."
            ));
            return CompletableFuture.completedFuture(null);
        }

        World world = playerState.world();
        Ref<EntityStore> playerRef = playerState.ref();

        if ("here".equalsIgnoreCase(args[startIndex + 1])) {
            return runRotationDebugOnWorldThread(
                    context,
                    world,
                    () -> getPlayerBlockBelowPosition(playerRef)
            );
        }

        if (args.length - startIndex < 4) {
            context.sendMessage(Message.raw(
                    "Usage: /fog rotation here | /fog rotation <x> <y> <z>"
            ));
            return CompletableFuture.completedFuture(null);
        }

        try {
            int x = Integer.parseInt(args[startIndex + 1]);
            int y = Integer.parseInt(args[startIndex + 2]);
            int z = Integer.parseInt(args[startIndex + 3]);

            Vector3i position = new Vector3i(x, y, z);

            return runRotationDebugOnWorldThread(
                    context,
                    world,
                    () -> position
            );

        } catch (NumberFormatException exception) {
            context.sendMessage(Message.raw(
                    "Coordinates must be integers."
            ));
            return CompletableFuture.completedFuture(null);
        }
    }

    private CompletableFuture<Void> runRotationDebugOnWorldThread(
            @Nonnull CommandContext context,
            @Nonnull World world,
            @Nonnull Supplier<Vector3i> positionSupplier
    ) {
        CompletableFuture<Void> future =
                new CompletableFuture<>();

        world.execute(() -> {
            try {
                sendRotationDebug(
                        context,
                        world,
                        positionSupplier.get()
                );

                future.complete(null);

            } catch (Exception exception) {
                context.sendMessage(Message.raw(
                        "Rotation debug failed: "
                                + exception.getClass().getSimpleName()
                                + ": "
                                + exception.getMessage()
                ));

                future.complete(null);
            }
        });

        return future;
    }

    private void sendRotationDebug(
            @Nonnull CommandContext context,
            @Nonnull World world,
            @Nonnull Vector3i position
    ) {
        BlockType blockType =
                world.getBlockType(
                        position.x(),
                        position.y(),
                        position.z()
                );

        Nodes.Node node =
                Nodes.get(
                        world,
                        position
                );

        RotationTuple storedRotation =
                node != null
                        ? node.rotation()
                        : null;

        RotationTuple worldRotation =
                getWorldRotation(
                        world,
                        position
                );

        context.sendMessage(Message.raw(
                "Rotation debug at "
                        + formatPosition(position)
        ));

        context.sendMessage(Message.raw(
                "block="
                        + (blockType != null
                                ? blockType.getId()
                                : "null")
        ));

        context.sendMessage(Message.raw(
                "storedRotation=" + storedRotation
        ));

        context.sendMessage(Message.raw(
                "worldRotation=" + worldRotation
        ));
    }

    private static @Nullable RotationTuple getWorldRotation(
            @Nonnull World world,
            @Nonnull Vector3i position
    ) {
        BlockAccessor blockAccessor =
                world.getChunk(
                        ChunkUtil.indexChunkFromBlock(
                                position.x(),
                                position.z()
                        )
                );

        if (blockAccessor == null) {
            return null;
        }

        return blockAccessor.getRotation(
                position.x(),
                position.y(),
                position.z()
        );
    }

    private static @Nonnull String formatPosition(
            @Nonnull Vector3i position
    ) {
        return "("
                + position.x()
                + ","
                + position.y()
                + ","
                + position.z()
                + ")";
    }

    private static @Nonnull Vector3i getPlayerBlockPosition(
            @Nonnull Ref<EntityStore> playerRef
    ) {
        TransformComponent transform =
                playerRef
                        .getStore()
                        .getComponent(
                                playerRef,
                                TransformComponent.getComponentType()
                        );

        return blockPosition(
                transform.getPosition()
        );
    }

    private static @Nonnull Vector3i getPlayerBlockBelowPosition(
            @Nonnull Ref<EntityStore> playerRef
    ) {
        Vector3i position =
                getPlayerBlockPosition(playerRef);

        return new Vector3i(
                position.x(),
                position.y() - 1,
                position.z()
        );
    }

    private static @Nonnull Vector3i blockPosition(
            @Nonnull Vector3d position
    ) {
        return new Vector3i(
                (int) Math.floor(position.x()),
                (int) Math.floor(position.y()),
                (int) Math.floor(position.z())
        );
    }

    private static @Nullable PlayerCommandState playerState(
            @Nonnull CommandContext context
    ) {
        if (!context.isPlayer()) {
            return null;
        }

        Ref<EntityStore> playerRef =
                context.senderAsPlayerRef();

        if (playerRef == null
                || !playerRef.isValid()) {
            return null;
        }

        World world =
                playerRef
                        .getStore()
                        .getExternalData()
                        .getWorld();

        if (world == null) {
            return null;
        }

        return new PlayerCommandState(
                world,
                playerRef
        );
    }

    private record PlayerCommandState(
            @Nonnull World world,
            @Nonnull Ref<EntityStore> ref
    ) {
    }

    private static @Nonnull String[] tokenize(
            @Nullable String input
    ) {
        if (input == null
                || input.isBlank()) {
            return new String[0];
        }

        return input
                .trim()
                .split("\\s+");
    }

    private int commandArgStartIndex(
            @Nonnull String[] args
    ) {
        if (args.length == 0) {
            return 0;
        }

        String commandName = getName();

        if (commandName != null
                && commandName.equalsIgnoreCase(args[0])) {
            return 1;
        }

        return 0;
    }
}
