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
        int i = commandArgStartIndex(args);

        if (args.length - i >= 1 && "saveinfo".equalsIgnoreCase(args[i])) {
            handleSaveInfo(context);
            return CompletableFuture.completedFuture(null);
        }
        if (args.length - i >= 1 && "saveworld".equalsIgnoreCase(args[i])) {
            handleSaveWorld(context);
            return CompletableFuture.completedFuture(null);
        }
        if (args.length - i >= 2 && "node".equalsIgnoreCase(args[i])) {
            return handleDebug(context, args, i, true);
        }
        if (args.length - i >= 2 && "rotation".equalsIgnoreCase(args[i])) {
            return handleDebug(context, args, i, false);
        }

        context.sendMessage(Message.raw("Usage: /fog node here|under|<x y z> | /fog rotation here|under|<x y z> | /fog saveinfo | /fog saveworld"));
        return CompletableFuture.completedFuture(null);
    }

    private void handleSaveInfo(CommandContext context) {
        PlayerCommandState state = playerState(context);
        if (state == null) {
            context.sendMessage(Message.raw("This command requires a player sender."));
            return;
        }
        context.sendMessage(Message.raw("World save path: " + WorldSaveFileService.debugSaveFilePath(state.world())));
    }

    private void handleSaveWorld(CommandContext context) {
        PlayerCommandState state = playerState(context);
        if (state == null) {
            context.sendMessage(Message.raw("This command requires a player sender."));
            return;
        }
        World world = state.world();
        WorldSaveFileService.forceSaveWorld(world);
        context.sendMessage(Message.raw("Triggered forced save for world '" + world.getName() + "'."));
        context.sendMessage(Message.raw("World save path: " + WorldSaveFileService.debugSaveFilePath(world)));
        context.sendMessage(Message.raw("Save file exists: " + WorldSaveFileService.debugSaveFileExists(world)));
        String error = WorldSaveFileService.debugLastSaveError(world);
        if (!error.isBlank()) context.sendMessage(Message.raw("Last save error: " + error));
    }

    private CompletableFuture<Void> handleDebug(CommandContext context, String[] args, int i, boolean nodeDebug) {
        PlayerCommandState state = playerState(context);
        if (state == null) {
            context.sendMessage(Message.raw("This command requires a player sender."));
            return CompletableFuture.completedFuture(null);
        }

        Supplier<Vector3i> target = resolveTarget(context, state.ref(), args, i, nodeDebug ? "node" : "rotation");
        if (target == null) return CompletableFuture.completedFuture(null);

        CompletableFuture<Void> future = new CompletableFuture<>();
        state.world().execute(() -> {
            try {
                Vector3i position = target.get();
                if (nodeDebug) sendNodeDebug(context, state.world(), position);
                else sendRotationDebug(context, state.world(), position);
            } catch (Exception exception) {
                context.sendMessage(Message.raw("Debug failed: " + exception.getClass().getSimpleName() + ": " + exception.getMessage()));
            } finally {
                future.complete(null);
            }
        });
        return future;
    }

    private @Nullable Supplier<Vector3i> resolveTarget(CommandContext context, Ref<EntityStore> ref, String[] args, int i, String name) {
        String target = args[i + 1];
        if ("here".equalsIgnoreCase(target)) return () -> playerBlockPosition(ref);
        if ("under".equalsIgnoreCase(target)) return () -> {
            Vector3i p = playerBlockPosition(ref);
            return new Vector3i(p.x(), p.y() - 1, p.z());
        };
        if (args.length - i < 4) {
            context.sendMessage(Message.raw("Usage: /fog " + name + " here | /fog " + name + " under | /fog " + name + " <x> <y> <z>"));
            return null;
        }
        try {
            Vector3i p = new Vector3i(Integer.parseInt(args[i + 1]), Integer.parseInt(args[i + 2]), Integer.parseInt(args[i + 3]));
            return () -> p;
        } catch (NumberFormatException exception) {
            context.sendMessage(Message.raw("Coordinates must be integers."));
            return null;
        }
    }

    private void sendNodeDebug(CommandContext context, World world, Vector3i p) {
        Nodes.Node n = Nodes.get(world, p);
        if (n == null) {
            context.sendMessage(Message.raw("No FoG node at " + formatPosition(p) + "."));
            return;
        }
        context.sendMessage(Message.raw("Node at " + formatPosition(p) + " blockId=" + n.blockId()));
        context.sendMessage(Message.raw("signalInputSides=" + n.signalInputSides() + " signalOutputSides=" + n.signalOutputSides() + " controlInputSides=" + n.controlInputSides()));
        context.sendMessage(Message.raw("invertEnabled=" + n.invertEnabled() + " passing=" + n.passing()));
        context.sendMessage(Message.raw("rotation=" + n.rotation()));
        context.sendMessage(Message.raw("previousInstantState=" + n.previousInstantState() + " instantState=" + n.instantState()));
        context.sendMessage(Message.raw("previousEffectiveState=" + n.previousEffectiveState() + " effectiveState=" + n.effectiveState()));
        context.sendMessage(Message.raw("dirty=" + n.dirty() + " energyDelta=" + n.energyDelta() + " networkId=" + n.networkId()));
    }

    private void sendRotationDebug(CommandContext context, World world, Vector3i p) {
        BlockType block = world.getBlockType(p.x(), p.y(), p.z());
        Nodes.Node node = Nodes.get(world, p);
        RotationTuple stored = node != null ? node.rotation() : null;
        RotationTuple actual = getWorldRotation(world, p);
        context.sendMessage(Message.raw("Rotation debug at " + formatPosition(p)));
        context.sendMessage(Message.raw("block=" + (block != null ? block.getId() : "null")));
        context.sendMessage(Message.raw("storedRotation=" + stored));
        context.sendMessage(Message.raw("worldRotation=" + actual));
    }

    private static @Nullable RotationTuple getWorldRotation(World world, Vector3i p) {
        BlockAccessor accessor = world.getChunk(ChunkUtil.indexChunkFromBlock(p.x(), p.z()));
        return accessor == null ? null : accessor.getRotation(p.x(), p.y(), p.z());
    }

    private static Vector3i playerBlockPosition(Ref<EntityStore> ref) {
        TransformComponent transform = ref.getStore().getComponent(ref, TransformComponent.getComponentType());
        Vector3d p = transform.getPosition();
        return new Vector3i((int) Math.floor(p.x()), (int) Math.floor(p.y()), (int) Math.floor(p.z()));
    }

    private static String formatPosition(Vector3i p) {
        return "(" + p.x() + "," + p.y() + "," + p.z() + ")";
    }

    private static @Nullable PlayerCommandState playerState(CommandContext context) {
        if (!context.isPlayer()) return null;
        Ref<EntityStore> ref = context.senderAsPlayerRef();
        if (ref == null || !ref.isValid()) return null;
        World world = ref.getStore().getExternalData().getWorld();
        return world == null ? null : new PlayerCommandState(world, ref);
    }

    private record PlayerCommandState(World world, Ref<EntityStore> ref) {}

    private static String[] tokenize(@Nullable String input) {
        return input == null || input.isBlank() ? new String[0] : input.trim().split("\\s+");
    }

    private int commandArgStartIndex(String[] args) {
        if (args.length == 0) return 0;
        String name = getName();
        return name != null && name.equalsIgnoreCase(args[0]) ? 1 : 0;
    }
}
