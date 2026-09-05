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
import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public final class NetworkResource implements Resource<ChunkStore> {

    private static final ArrayCodec<NetworkData> NETWORK_ARRAY_CODEC =
            ArrayCodec.ofBuilderCodec(
                    NetworkData.CODEC,
                    NetworkData[]::new
            );

    public static final BuilderCodec<NetworkResource> CODEC =
            BuilderCodec.builder(
                            NetworkResource.class,
                            NetworkResource::new
                    )
                    .append(
                            new KeyedCodec<>(
                                    "NextNetworkId",
                                    Codec.LONG
                            ),
                            (resource, value) ->
                                    resource.nextNetworkId = value,
                            resource ->
                                    resource.nextNetworkId
                    )
                    .add()
                    .append(
                            new KeyedCodec<>(
                                    "Networks",
                                    NETWORK_ARRAY_CODEC
                            ),
                            NetworkResource::setNetworks,
                            NetworkResource::getNetworks
                    )
                    .add()
                    .build();


    private long nextNetworkId = 1L;

    private final Map<Long, NetworkData> networks =
            new HashMap<>();


    public NetworkResource() {
    }

    private NetworkResource(
            @Nonnull NetworkResource other
    ) {
        this.nextNetworkId =
                other.nextNetworkId;

        for (Map.Entry<Long, NetworkData> entry
                : other.networks.entrySet()) {

            this.networks.put(
                    entry.getKey(),
                    new NetworkData(entry.getValue())
            );
        }
    }


    // --------------------------------------------------
    // SERIALIZATION
    // --------------------------------------------------

    private NetworkData[] getNetworks() {
        return networks.values()
                .toArray(NetworkData[]::new);
    }

    private void setNetworks(
            NetworkData[] loadedNetworks
    ) {
        networks.clear();

        if (loadedNetworks == null) {
            return;
        }

        for (NetworkData network : loadedNetworks) {

            if (network == null) {
                continue;
            }

            networks.put(
                    network.id(),
                    network
            );
        }
    }


    // --------------------------------------------------
    // NETWORK CREATION
    // --------------------------------------------------

    public long createNetwork() {

        long networkId =
                nextNetworkId++;

        networks.put(
                networkId,
                new NetworkData(networkId)
        );

        return networkId;
    }


    // --------------------------------------------------
    // NETWORK ACCESS
    // --------------------------------------------------

    public @Nullable NetworkData getNetwork(
            long networkId
    ) {
        return networks.get(networkId);
    }

    public boolean containsNetwork(
            long networkId
    ) {
        return networks.containsKey(networkId);
    }

    public void removeNetwork(
            long networkId
    ) {
        networks.remove(networkId);
    }

    public @Nonnull Set<Long> networkIds() {
        return Set.copyOf(
                networks.keySet()
        );
    }


    // --------------------------------------------------
    // MEMBERS / GRAPH NODES
    // --------------------------------------------------

    public void addMember(
            long networkId,
            @Nonnull Vector3i position
    ) {
        NetworkData network =
                requireNetwork(networkId);

        Vector3i key =
                new Vector3i(position);

        network.nodes.putIfAbsent(
                key,
                new NetworkNodeData(position)
        );
    }

    public void removeMember(
            long networkId,
            @Nonnull Vector3i position
    ) {
        NetworkData network =
                networks.get(networkId);

        if (network == null) {
            return;
        }

        if (network.nodes.remove(position) == null) {
            return;
        }

        for (NetworkNodeData node :
                network.nodes.values()) {

            node.neighbours.remove(position);
        }
    }

    public boolean containsMember(
            long networkId,
            @Nonnull Vector3i position
    ) {
        NetworkData network =
                networks.get(networkId);

        return network != null
                && network.nodes.containsKey(position);
    }

    public @Nonnull Set<Vector3i> members(
            long networkId
    ) {
        NetworkData network =
                networks.get(networkId);

        if (network == null) {
            return Set.of();
        }

        LinkedHashSet<Vector3i> copy =
                new LinkedHashSet<>();

        for (Vector3i position :
                network.nodes.keySet()) {

            copy.add(
                    new Vector3i(position)
            );
        }

        return Set.copyOf(copy);
    }

    public @Nullable NetworkNodeData node(
            long networkId,
            @Nonnull Vector3i position
    ) {
        NetworkData network =
                networks.get(networkId);

        if (network == null) {
            return null;
        }

        return network.nodes.get(position);
    }


    // --------------------------------------------------
    // GRAPH EDGES
    // --------------------------------------------------

    public void addEdge(
            long networkId,
            @Nonnull Vector3i first,
            @Nonnull Vector3i second
    ) {
        NetworkData network =
                requireNetwork(networkId);

        NetworkNodeData firstNode =
                requireNode(
                        network,
                        first
                );

        NetworkNodeData secondNode =
                requireNode(
                        network,
                        second
                );

        firstNode.neighbours.add(
                new Vector3i(second)
        );

        secondNode.neighbours.add(
                new Vector3i(first)
        );
    }

    public void removeEdge(
            long networkId,
            @Nonnull Vector3i first,
            @Nonnull Vector3i second
    ) {
        NetworkData network =
                networks.get(networkId);

        if (network == null) {
            return;
        }

        NetworkNodeData firstNode =
                network.nodes.get(first);

        NetworkNodeData secondNode =
                network.nodes.get(second);

        if (firstNode != null) {
            firstNode.neighbours.remove(second);
        }

        if (secondNode != null) {
            secondNode.neighbours.remove(first);
        }
    }

    public @Nonnull Set<Vector3i> neighbours(
            long networkId,
            @Nonnull Vector3i position
    ) {
        NetworkNodeData node =
                node(
                        networkId,
                        position
                );

        if (node == null) {
            return Set.of();
        }

        return node.neighbours();
    }


    // --------------------------------------------------
    // NODE ENERGY
    // --------------------------------------------------

    public int energyDelta(
            long networkId,
            @Nonnull Vector3i position
    ) {
        NetworkNodeData node =
                node(
                        networkId,
                        position
                );

        return node == null
                ? 0
                : node.energyDelta;
    }

    public void setEnergyDelta(
            long networkId,
            @Nonnull Vector3i position,
            int energyDelta
    ) {
        NetworkData network =
                requireNetwork(networkId);

        NetworkNodeData node =
                requireNode(
                        network,
                        position
                );

        node.energyDelta = energyDelta;
    }


    // --------------------------------------------------
    // ENERGY / FAILURE
    // --------------------------------------------------

    public int energy(
            long networkId
    ) {
        NetworkData network =
                networks.get(networkId);

        return network == null
                ? 0
                : network.energy;
    }

    public void setEnergy(
            long networkId,
            int energy
    ) {
        NetworkData network =
                requireNetwork(networkId);

        network.energy = energy;
    }

    public boolean isFailing(
            long networkId
    ) {
        NetworkData network =
                networks.get(networkId);

        return network != null
                && network.failureStep >= 0;
    }

    public int failureStep(
            long networkId
    ) {
        NetworkData network =
                networks.get(networkId);

        return network == null
                ? -1
                : network.failureStep;
    }

    public long failureRemainingTicks(
            long networkId
    ) {
        NetworkData network =
                networks.get(networkId);

        return network == null
                ? 0L
                : network.failureRemainingTicks;
    }

    public void setFailureState(
            long networkId,
            int step,
            long remainingTicks
    ) {
        NetworkData network =
                networks.get(networkId);

        if (network == null) {
            return;
        }

        network.failureStep = step;
        network.failureRemainingTicks = remainingTicks;
    }

    public void clearFailure(
            long networkId
    ) {
        NetworkData network =
                networks.get(networkId);

        if (network == null) {
            return;
        }

        network.failureStep = -1;
        network.failureRemainingTicks = 0L;
    }

    public void addPendingFailureOff(
            long networkId,
            @Nonnull Vector3i position
    ) {
        NetworkData network =
                networks.get(networkId);

        if (network == null) {
            return;
        }

        network.pendingFailureOff.add(
                new Vector3i(position)
        );
    }

    public void removePendingFailureOff(
            long networkId,
            @Nonnull Vector3i position
    ) {
        NetworkData network =
                networks.get(networkId);

        if (network == null) {
            return;
        }

        network.pendingFailureOff.remove(position);
    }

    public @Nonnull Set<Vector3i> pendingFailureOff(
            long networkId
    ) {
        NetworkData network =
                networks.get(networkId);

        if (network == null) {
            return Set.of();
        }

        LinkedHashSet<Vector3i> copy =
                new LinkedHashSet<>();

        for (Vector3i position :
                network.pendingFailureOff) {

            copy.add(
                    new Vector3i(position)
            );
        }

        return Set.copyOf(copy);
    }

    public void clearPendingFailureOff(
            long networkId
    ) {
        NetworkData network =
                networks.get(networkId);

        if (network == null) {
            return;
        }

        network.pendingFailureOff.clear();
    }


    // --------------------------------------------------
    // INTERNAL ACCESS
    // --------------------------------------------------

    private @Nonnull NetworkData requireNetwork(
            long networkId
    ) {
        NetworkData network =
                networks.get(networkId);

        if (network == null) {
            throw new IllegalArgumentException(
                    "Unknown network id: "
                            + networkId
            );
        }

        return network;
    }

    private static @Nonnull NetworkNodeData requireNode(
            @Nonnull NetworkData network,
            @Nonnull Vector3i position
    ) {
        NetworkNodeData node =
                network.nodes.get(position);

        if (node == null) {
            throw new IllegalArgumentException(
                    "Unknown network node: "
                            + position
            );
        }

        return node;
    }


    // --------------------------------------------------
    // CLONE
    // --------------------------------------------------

    @Override
    public @Nonnull NetworkResource clone() {
        return new NetworkResource(this);
    }


    // --------------------------------------------------
    // NETWORK DATA
    // --------------------------------------------------

    public static final class NetworkData {

        private static final ArrayCodec<Vector3i>
                POSITION_ARRAY_CODEC =
                new ArrayCodec<>(
                        Vector3iUtil.CODEC,
                        Vector3i[]::new
                );

        private static final ArrayCodec<NetworkNodeData>
                NODE_ARRAY_CODEC =
                ArrayCodec.ofBuilderCodec(
                        NetworkNodeData.CODEC,
                        NetworkNodeData[]::new
                );

        public static final BuilderCodec<NetworkData> CODEC =
                BuilderCodec.builder(
                                NetworkData.class,
                                NetworkData::new
                        )
                        .append(
                                new KeyedCodec<>(
                                        "Id",
                                        Codec.LONG
                                ),
                                (network, value) ->
                                        network.id = value,
                                network ->
                                        network.id
                        )
                        .add()
                        .append(
                                new KeyedCodec<>(
                                        "Nodes",
                                        NODE_ARRAY_CODEC
                                ),
                                NetworkData::setNodes,
                                NetworkData::getNodes
                        )
                        .add()
                        .append(
                                new KeyedCodec<>(
                                        "Energy",
                                        Codec.INTEGER
                                ),
                                (network, value) ->
                                        network.energy = value,
                                network ->
                                        network.energy
                        )
                        .add()
                        .append(
                                new KeyedCodec<>(
                                        "FailureStep",
                                        Codec.INTEGER
                                ),
                                (network, value) ->
                                        network.failureStep = value,
                                network ->
                                        network.failureStep
                        )
                        .add()
                        .append(
                                new KeyedCodec<>(
                                        "FailureRemainingTicks",
                                        Codec.LONG
                                ),
                                (network, value) ->
                                        network.failureRemainingTicks = value,
                                network ->
                                        network.failureRemainingTicks
                        )
                        .add()
                        .append(
                                new KeyedCodec<>(
                                        "PendingFailureOff",
                                        POSITION_ARRAY_CODEC
                                ),
                                NetworkData::setPendingFailureOff,
                                NetworkData::getPendingFailureOff
                        )
                        .add()
                        .build();


        private long id;
        private int failureStep = -1;
        private long failureRemainingTicks = 0L;

        private final Map<Vector3i, NetworkNodeData> nodes =
                new HashMap<>();

        private final Set<Vector3i> pendingFailureOff =
                new LinkedHashSet<>();

        private int energy;


        public NetworkData() {
        }

        private NetworkData(
                long id
        ) {
            this.id = id;
        }

        private NetworkData(
                @Nonnull NetworkData other
        ) {
            this.id = other.id;
            this.energy = other.energy;

            for (NetworkNodeData node :
                    other.nodes.values()) {

                NetworkNodeData copy =
                        new NetworkNodeData(node);

                this.nodes.put(
                        new Vector3i(copy.position),
                        copy
                );
            }

            for (Vector3i position :
                    other.pendingFailureOff) {

                this.pendingFailureOff.add(
                        new Vector3i(position)
                );
            }

            this.failureStep =
                    other.failureStep;

            this.failureRemainingTicks =
                    other.failureRemainingTicks;
        }


        // ----------------------------------------------
        // SERIALIZATION
        // ----------------------------------------------

        private NetworkNodeData[] getNodes() {
            return nodes.values()
                    .toArray(NetworkNodeData[]::new);
        }

        private void setNodes(
                NetworkNodeData[] loadedNodes
        ) {
            nodes.clear();

            if (loadedNodes == null) {
                return;
            }

            for (NetworkNodeData node :
                    loadedNodes) {

                if (node == null
                        || node.position == null) {
                    continue;
                }

                nodes.put(
                        new Vector3i(node.position),
                        node
                );
            }
        }

        private Vector3i[] getPendingFailureOff() {
            return pendingFailureOff.toArray(
                    Vector3i[]::new
            );
        }

        private void setPendingFailureOff(
                Vector3i[] loadedPositions
        ) {
            pendingFailureOff.clear();

            if (loadedPositions == null) {
                return;
            }

            for (Vector3i position :
                    loadedPositions) {

                if (position != null) {
                    pendingFailureOff.add(
                            new Vector3i(position)
                    );
                }
            }
        }


        // ----------------------------------------------
        // ACCESS
        // ----------------------------------------------

        public long id() {
            return id;
        }

        public int energy() {
            return energy;
        }

        public int size() {
            return nodes.size();
        }
    }


    // --------------------------------------------------
    // NETWORK NODE DATA
    // --------------------------------------------------

    public static final class NetworkNodeData {

        private static final ArrayCodec<Vector3i>
                NEIGHBOUR_ARRAY_CODEC =
                new ArrayCodec<>(
                        Vector3iUtil.CODEC,
                        Vector3i[]::new
                );

        public static final BuilderCodec<NetworkNodeData> CODEC =
                BuilderCodec.builder(
                                NetworkNodeData.class,
                                NetworkNodeData::new
                        )
                        .append(
                                new KeyedCodec<>(
                                        "Position",
                                        Vector3iUtil.CODEC
                                ),
                                (node, value) ->
                                        node.position = value == null
                                                ? null
                                                : new Vector3i(value),
                                node ->
                                        node.position
                        )
                        .add()
                        .append(
                                new KeyedCodec<>(
                                        "Neighbours",
                                        NEIGHBOUR_ARRAY_CODEC
                                ),
                                NetworkNodeData::setNeighbours,
                                NetworkNodeData::getNeighbours
                        )
                        .add()
                        .append(
                                new KeyedCodec<>(
                                        "EnergyDelta",
                                        Codec.INTEGER
                                ),
                                (node, value) ->
                                        node.energyDelta = value,
                                node ->
                                        node.energyDelta
                        )
                        .add()
                        .build();


        private Vector3i position;

        private final Set<Vector3i> neighbours =
                new LinkedHashSet<>();

        private int energyDelta;


        public NetworkNodeData() {
        }

        private NetworkNodeData(
                @Nonnull Vector3i position
        ) {
            this.position =
                    new Vector3i(position);
        }

        private NetworkNodeData(
                @Nonnull NetworkNodeData other
        ) {
            this.position =
                    new Vector3i(other.position);

            this.energyDelta =
                    other.energyDelta;

            for (Vector3i neighbour :
                    other.neighbours) {

                this.neighbours.add(
                        new Vector3i(neighbour)
                );
            }
        }


        // ----------------------------------------------
        // SERIALIZATION
        // ----------------------------------------------

        private Vector3i[] getNeighbours() {
            return neighbours.toArray(
                    Vector3i[]::new
            );
        }

        private void setNeighbours(
                Vector3i[] loadedNeighbours
        ) {
            neighbours.clear();

            if (loadedNeighbours == null) {
                return;
            }

            for (Vector3i neighbour :
                    loadedNeighbours) {

                if (neighbour != null) {
                    neighbours.add(
                            new Vector3i(neighbour)
                    );
                }
            }
        }


        // ----------------------------------------------
        // ACCESS
        // ----------------------------------------------

        public @Nonnull Vector3i position() {
            return new Vector3i(position);
        }

        public @Nonnull Set<Vector3i> neighbours() {
            LinkedHashSet<Vector3i> copy =
                    new LinkedHashSet<>();

            for (Vector3i neighbour :
                    neighbours) {

                copy.add(
                        new Vector3i(neighbour)
                );
            }

            return Set.copyOf(copy);
        }

        public int energyDelta() {
            return energyDelta;
        }
    }
}
