package dev.moxinat.forcesofgravium.connectable.propagation;

import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import dev.moxinat.forcesofgravium.connectable.core.ConnectableDataStore;
import dev.moxinat.forcesofgravium.connectable.core.ConnectableDefinition;
import dev.moxinat.forcesofgravium.connectable.core.ConnectableRuntimeAccessor;
import dev.moxinat.forcesofgravium.connectable.core.ConnectableRuntimeData;
import org.joml.Vector3i;

import javax.annotation.Nonnull;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class ConnectableNodeProvider {

    private ConnectableNodeProvider() {
    }

    public static @Nonnull Set<Vector3i> connectableNodePositionsForWorld(@Nonnull World world) {
        return Set.copyOf(nodesForWorld(world).keySet());
    }

    public static @Nonnull Map<Vector3i, ConnectableNode> nodesForWorld(@Nonnull World world) {
        Map<Vector3i, ConnectableNode> result = new LinkedHashMap<>();
        for (Vector3i position : ConnectableRuntimeAccessor.positionsForWorld(world)) {
            Optional<ConnectableNode> node = nodeAt(world, position);
            node.ifPresent(value -> result.put(value.position(), value));
        }
        return Map.copyOf(result);
    }

    public static @Nonnull Optional<ConnectableNode> nodeAt(@Nonnull World world, @Nonnull Vector3i position) {
        ConnectableRuntimeData runtimeData = ConnectableDataStore.get(world, position);
        if (runtimeData == null) {
            return Optional.empty();
        }

        BlockType blockType = world.getBlockType(position.x(), position.y(), position.z());
        if (blockType == null) {
            return Optional.empty();
        }

        Optional<ConnectableDefinition> definition = ConnectableRuntimeAccessor.definitionFor(blockType.getId());
        if (definition.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new ConnectableNode(position, blockType.getId(), definition.get(), runtimeData));
    }

    public static @Nonnull Set<Vector3i> retainNodes(@Nonnull World world, @Nonnull Set<Vector3i> positions) {
        LinkedHashSet<Vector3i> result = new LinkedHashSet<>();
        for (Vector3i position : positions) {
            if (nodeAt(world, position).isPresent()) {
                result.add(position);
            }
        }
        return Set.copyOf(result);
    }
}
