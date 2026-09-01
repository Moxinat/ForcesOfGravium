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
    // MEMBERS
    // --------------------------------------------------

    public void addMember(
            long networkId,
            @Nonnull Vector3i position
    ) {
        NetworkData network =
                networks.get(networkId);

        if (network == null) {
            throw new IllegalArgumentException(
                    "Unknown network id: "
                            + networkId
            );
        }

        network.members.add(
                new Vector3i(position)
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

        network.members.remove(position);
    }

    public boolean containsMember(
            long networkId,
            @Nonnull Vector3i position
    ) {
        NetworkData network =
                networks.get(networkId);

        return network != null
                && network.members.contains(position);
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

        for (Vector3i position
                : network.members) {

            copy.add(
                    new Vector3i(position)
            );
        }

        return Set.copyOf(copy);
    }


    // --------------------------------------------------
    // ENERGY
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
                networks.get(networkId);

        if (network == null) {
            throw new IllegalArgumentException(
                    "Unknown network id: "
                            + networkId
            );
        }

        network.energy = energy;
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
                MEMBER_ARRAY_CODEC =
                new ArrayCodec<>(
                        Vector3iUtil.CODEC,
                        Vector3i[]::new
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
                                        "Members",
                                        MEMBER_ARRAY_CODEC
                                ),
                                NetworkData::setMembers,
                                NetworkData::getMembers
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
                        .build();


        private long id;

        private final Set<Vector3i> members =
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

            for (Vector3i position
                    : other.members) {

                this.members.add(
                        new Vector3i(position)
                );
            }
        }


        // ----------------------------------------------
        // SERIALIZATION
        // ----------------------------------------------

        private Vector3i[] getMembers() {
            return members.toArray(
                    Vector3i[]::new
            );
        }

        private void setMembers(
                Vector3i[] loadedMembers
        ) {
            members.clear();

            if (loadedMembers == null) {
                return;
            }

            for (Vector3i position
                    : loadedMembers) {

                if (position != null) {
                    members.add(
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
            return members.size();
        }
    }
}