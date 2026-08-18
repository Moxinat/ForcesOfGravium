package dev.moxinat.forcesofgravium.persistence;

import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import com.hypixel.hytale.server.core.universe.world.World;
import dev.moxinat.forcesofgravium.block.source.SourceActivationScheduler;
import dev.moxinat.forcesofgravium.connectable.SignalState;
import dev.moxinat.forcesofgravium.connectable.propagation.ConnectablePropagationScheduler;
import dev.moxinat.forcesofgravium.data.Nodes;
import org.bson.BsonArray;
import org.bson.BsonBoolean;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonInt64;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.joml.Vector3i;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class WorldSaveFileService {

    private static final int SAVE_VERSION = 1;
    private static final String SAVE_FOLDER = "forcesofgravium";
    private static final String SAVE_FILE = "worldsave.json";
    private static final long SAVE_THROTTLE_MILLIS = 1000L;

    private static final Map<String, World> LOADED_WORLDS = new ConcurrentHashMap<>();
    private static final Map<String, Long> LAST_SAVE_ATTEMPT_MILLIS = new ConcurrentHashMap<>();
    private static final Map<String, String> LAST_SAVE_ERROR = new ConcurrentHashMap<>();
    private static final Set<String> LOADING_WORLDS = ConcurrentHashMap.newKeySet();

    private WorldSaveFileService() {
    }

    public static void ensureLoaded(@Nonnull World world) {
        String key = worldKey(world);

        if (LOADED_WORLDS.containsKey(key)) {
            return;
        }

        synchronized (WorldSaveFileService.class) {
            if (LOADED_WORLDS.containsKey(key)) {
                return;
            }

            LOADING_WORLDS.add(key);
            clearWorldRuntime(world);

            try {
                Path saveFile = saveFile(world);

                if (Files.exists(saveFile)) {
                    String content =
                            Files.readString(
                                    saveFile,
                                    StandardCharsets.UTF_8
                            );

                    if (!content.isBlank()) {
                        BsonDocument root =
                                BsonDocument.parse(content);

                        if (!root.containsKey("nodes")) {
                            throw new IllegalStateException(
                                    "Legacy worldsave is no longer supported."
                            );
                        }

                        int version = root
                                .getInt32(
                                        "version",
                                        new BsonInt32(1)
                                )
                                .getValue();

                        if (version != SAVE_VERSION) {
                            throw new IllegalStateException(
                                    "Unsupported worldsave version: "
                                            + version
                            );
                        }

                        loadNodes(
                                world,
                                root.getArray(
                                        "nodes",
                                        new BsonArray()
                                )
                        );

                        loadPropagation(
                                world,
                                root.getDocument(
                                        "propagation",
                                        new BsonDocument()
                                )
                        );

                        loadTimedSources(
                                world,
                                root.getArray(
                                        "timedSources",
                                        new BsonArray()
                                )
                        );
                    }
                }

                LOADED_WORLDS.put(key, world);

            } catch (Exception exception) {
                clearWorldRuntime(world);

                System.err.println(
                        "[FoG] Failed to load world save for '"
                                + world.getName()
                                + "': "
                                + exception.getMessage()
                );

            } finally {
                LOADING_WORLDS.remove(key);
            }
        }
    }

    private static void loadNodes(
            @Nonnull World world,
            @Nonnull BsonArray entries
    ) {
        for (BsonValue value : entries) {
            if (!value.isDocument()) {
                continue;
            }

            BsonDocument document = value.asDocument();

            Vector3i position = readPosition(document.getDocument("position"));
            String blockId = document.getString("blockId").getValue();
            int signalInputSides = document.getInt32("signalInputSides", new BsonInt32(0)).getValue();
            int signalOutputSides = document.getInt32("signalOutputSides", new BsonInt32(0)).getValue();
            int controlInputSides = document.getInt32("controlInputSides", new BsonInt32(0)).getValue();
            RotationTuple rotation = readRotationTuple(document.getDocument("rotation", new BsonDocument()));
            SignalState previousInstantState = readSignalState(document, "previousInstantState");
            SignalState instantState = readSignalState(document, "instantState");
            SignalState previousEffectiveState = readSignalState(document, "previousEffectiveState");
            SignalState effectiveState = readSignalState(document, "effectiveState");
            boolean dirty = document.getBoolean("dirty", BsonBoolean.FALSE).getValue();
            boolean invertEnabled = document.getBoolean("invertEnabled", BsonBoolean.FALSE).getValue();
            boolean passing = document.getBoolean("passing", BsonBoolean.FALSE).getValue();
            int energyDelta = document.getInt32("energyDelta", new BsonInt32(0)).getValue();
            long networkId = readLong(
                    document,
                    "networkId",
                    Nodes.Node.NO_NETWORK
            );

            Nodes.createNode(
                    world,
                    position,
                    blockId,
                    signalInputSides,
                    signalOutputSides,
                    controlInputSides,
                    rotation,
                    previousInstantState,
                    instantState,
                    previousEffectiveState,
                    effectiveState,
                    dirty,
                    invertEnabled,
                    passing,
                    energyDelta,
                    networkId
            );
        }

        System.out.println(
                "[FoG LOAD] loaded nodes="
                        + Nodes.sizeForWorld(world)
        );
    }

    private static void loadPropagation(
            @Nonnull World world,
            @Nonnull BsonDocument document
    ) {
        Set<Vector3i> currentWave = readPositions(
                document.getArray("currentWave", new BsonArray())
        );
        Set<Vector3i> nextWave = readPositions(
                document.getArray("nextWave", new BsonArray())
        );

        ConnectablePropagationScheduler.restoreWaves(
                world,
                currentWave,
                nextWave
        );
    }

    private static void loadTimedSources(
            @Nonnull World world,
            @Nonnull BsonArray entries
    ) {
        Map<Vector3i, Long> remainingTicks = new HashMap<>();

        for (BsonValue value : entries) {
            if (!value.isDocument()) {
                continue;
            }

            BsonDocument document = value.asDocument();
            Vector3i position = readPosition(document.getDocument("position"));
            long remaining = readLong(
                    document,
                    "remainingTicks",
                    0L
            );

            remainingTicks.put(position, Math.max(0L, remaining));
        }

        SourceActivationScheduler.restoreTimedSources(world, remainingTicks);
    }

    public static void saveWorld(@Nonnull World world) {
        saveWorld(world, false);
    }

    public static void forceSaveWorld(@Nonnull World world) {
        saveWorld(world, true);
    }

    private static void saveWorld(
            @Nonnull World world,
            boolean force
    ) {
        ensureLoaded(world);

        String key = worldKey(world);

        if (!LOADED_WORLDS.containsKey(key)) {
            return;
        }

        long now = System.currentTimeMillis();
        long lastAttempt = LAST_SAVE_ATTEMPT_MILLIS.getOrDefault(key, 0L);

        if (!force && now - lastAttempt < SAVE_THROTTLE_MILLIS) {
            return;
        }

        synchronized (WorldSaveFileService.class) {
            LAST_SAVE_ATTEMPT_MILLIS.put(key, now);

            try {
                System.out.println(
                        "[FoG SAVE] world=" + world.getName()
                                + " nodes=" + Nodes.sizeForWorld(world)
                                + " force=" + force
                );

                Thread.dumpStack();

                writeSaveFile(world, createSaveDocument(world));
                LAST_SAVE_ERROR.remove(key);
            } catch (Exception exception) {
                String message = exception.getClass().getSimpleName()
                        + ": "
                        + exception.getMessage();

                LAST_SAVE_ERROR.put(key, message);

                System.err.println(
                        "[FoG] Failed to save world save for '"
                                + world.getName()
                                + "': "
                                + message
                );
            }
        }
    }

    private static @Nonnull BsonDocument createSaveDocument(
            @Nonnull World world
    ) {
        BsonDocument root = new BsonDocument();
        root.put("version", new BsonInt32(SAVE_VERSION));
        root.put("nodes", serializeNodes(world));
        root.put("propagation", serializePropagation(world));
        root.put("timedSources", serializeTimedSources(world));
        return root;
    }

    private static @Nonnull BsonArray serializeNodes(
            @Nonnull World world
    ) {
        BsonArray result = new BsonArray();

        for (Nodes.Node node : Nodes.snapshotForWorld(world).values()) {
            result.add(writeNode(node));
        }

        return result;
    }

    private static @Nonnull BsonDocument writeNode(
            @Nonnull Nodes.Node node
    ) {
        BsonDocument document = new BsonDocument();

        document.put("position", writePosition(node.position()));
        document.put("blockId", new BsonString(node.blockId()));
        document.put("signalInputSides", new BsonInt32(node.signalInputSides()));
        document.put("signalOutputSides", new BsonInt32(node.signalOutputSides()));
        document.put("controlInputSides", new BsonInt32(node.controlInputSides()));
        document.put("rotation", writeRotation(node.rotation()));
        document.put("previousInstantState", new BsonString(node.previousInstantState().name()));
        document.put("instantState", new BsonString(node.instantState().name()));
        document.put("previousEffectiveState", new BsonString(node.previousEffectiveState().name()));
        document.put("effectiveState", new BsonString(node.effectiveState().name()));
        document.put("dirty", new BsonBoolean(node.dirty()));
        document.put("invertEnabled", new BsonBoolean(node.invertEnabled()));
        document.put("passing", new BsonBoolean(node.passing()));
        document.put("energyDelta", new BsonInt32(node.energyDelta()));
        document.put("networkId", new BsonInt64(node.networkId()));

        return document;
    }

    private static @Nonnull BsonDocument serializePropagation(
            @Nonnull World world
    ) {
        BsonDocument document = new BsonDocument();
        document.put(
                "currentWave",
                writePositions(ConnectablePropagationScheduler.snapshotCurrentWave(world))
        );
        document.put(
                "nextWave",
                writePositions(ConnectablePropagationScheduler.snapshotNextWave(world))
        );
        return document;
    }

    private static @Nonnull BsonArray serializeTimedSources(
            @Nonnull World world
    ) {
        BsonArray result = new BsonArray();
        Map<Vector3i, Long> remainingTicks = SourceActivationScheduler.snapshotRemainingTicks(world);

        for (Map.Entry<Vector3i, Long> entry : remainingTicks.entrySet()) {
            BsonDocument document = new BsonDocument();
            document.put("position", writePosition(entry.getKey()));
            document.put("remainingTicks", new BsonInt64(entry.getValue()));
            result.add(document);
        }

        return result;
    }

    public static void saveLoadedWorlds() {
        for (World world : List.copyOf(LOADED_WORLDS.values())) {
            forceSaveWorld(world);
        }
    }

    private static @Nonnull BsonDocument writePosition(
            @Nonnull Vector3i position
    ) {
        BsonDocument document = new BsonDocument();
        document.put("x", new BsonInt32(position.x()));
        document.put("y", new BsonInt32(position.y()));
        document.put("z", new BsonInt32(position.z()));
        return document;
    }

    private static @Nonnull Vector3i readPosition(
            @Nonnull BsonDocument document
    ) {
        return new Vector3i(
                document.getInt32("x").getValue(),
                document.getInt32("y").getValue(),
                document.getInt32("z").getValue()
        );
    }

    private static @Nonnull BsonArray writePositions(
            @Nonnull Set<Vector3i> positions
    ) {
        BsonArray result = new BsonArray();

        for (Vector3i position : positions) {
            result.add(writePosition(position));
        }

        return result;
    }

    private static @Nonnull Set<Vector3i> readPositions(
            @Nonnull BsonArray array
    ) {
        Set<Vector3i> positions = ConcurrentHashMap.newKeySet();

        for (BsonValue value : array) {
            if (value.isDocument()) {
                positions.add(readPosition(value.asDocument()));
            }
        }

        return Set.copyOf(positions);
    }

    private static @Nonnull BsonDocument writeRotation(
            @Nonnull RotationTuple rotation
    ) {
        BsonDocument document = new BsonDocument();
        document.put("yaw", new BsonString(rotation.yaw().name()));
        document.put("pitch", new BsonString(rotation.pitch().name()));
        document.put("roll", new BsonString(rotation.roll().name()));
        return document;
    }

    private static @Nonnull RotationTuple readRotationTuple(
            @Nonnull BsonDocument document
    ) {
        return RotationTuple.of(
                readRotation(document, "yaw"),
                readRotation(document, "pitch"),
                readRotation(document, "roll")
        );
    }

    private static @Nonnull Rotation readRotation(
            @Nonnull BsonDocument document,
            @Nonnull String key
    ) {
        return Rotation.valueOf(
                document
                        .getString(key, new BsonString(Rotation.None.name()))
                        .getValue()
        );
    }

    private static @Nonnull SignalState readSignalState(
            @Nonnull BsonDocument document,
            @Nonnull String key
    ) {
        return SignalState.valueOf(
                document
                        .getString(key, new BsonString(SignalState.OFF.name()))
                        .getValue()
        );
    }

    private static void writeSaveFile(
            @Nonnull World world,
            @Nonnull BsonDocument root
    ) throws IOException {
        Path saveFile = saveFile(world);
        Files.createDirectories(saveFile.getParent());

        Path temporaryFile = saveFile.resolveSibling(SAVE_FILE + ".tmp");

        Files.writeString(
                temporaryFile,
                root.toJson(),
                StandardCharsets.UTF_8
        );

        try {
            Files.move(
                    temporaryFile,
                    saveFile,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
            );
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(
                    temporaryFile,
                    saveFile,
                    StandardCopyOption.REPLACE_EXISTING
            );
        }
    }

    private static void clearWorldRuntime(
            @Nonnull World world
    ) {
        Nodes.clearWorld(world);
        ConnectablePropagationScheduler.clearWorld(world);
        SourceActivationScheduler.clearWorld(world);
    }

    public static @Nonnull String debugSaveFilePath(
            @Nonnull World world
    ) {
        return saveFile(world)
                .toAbsolutePath()
                .normalize()
                .toString();
    }

    public static boolean debugSaveFileExists(
            @Nonnull World world
    ) {
        return Files.exists(saveFile(world));
    }

    public static @Nonnull String debugLastSaveError(
            @Nonnull World world
    ) {
        return LAST_SAVE_ERROR.getOrDefault(worldKey(world), "");
    }

    private static @Nonnull Path saveFile(
            @Nonnull World world
    ) {
        return world
                .getSavePath()
                .resolve(SAVE_FOLDER)
                .resolve(SAVE_FILE);
    }

    private static @Nonnull String worldKey(
            @Nonnull World world
    ) {
        return world
                .getSavePath()
                .toAbsolutePath()
                .normalize()
                .toString();
    }

    private static long readLong(
            @Nonnull BsonDocument document,
            @Nonnull String key,
            long defaultValue
    ) {
        BsonValue value = document.get(key);

        if (value == null) {
            return defaultValue;
        }

        if (value.isInt64()) {
            return value.asInt64().getValue();
        }

        if (value.isInt32()) {
            return value.asInt32().getValue();
        }

        return defaultValue;
    }
}
