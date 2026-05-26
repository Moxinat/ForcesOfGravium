package dev.moxinat.forcesofgravium.persistence;

import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import com.hypixel.hytale.server.core.universe.world.World;
import dev.moxinat.forcesofgravium.data.ConnectableRotationStore;
import dev.moxinat.forcesofgravium.data.GravityPowderBlockDataStore;
import dev.moxinat.forcesofgravium.data.GravityPowderBlockDataStore.GravityPowderBlockData;
import dev.moxinat.forcesofgravium.data.GraviumSiphonStore;
import dev.moxinat.forcesofgravium.data.GraviumSiphonStore.GraviumSiphonData;
import dev.moxinat.forcesofgravium.data.InverterDataStore;
import dev.moxinat.forcesofgravium.data.InverterDataStore.InverterData;
import dev.moxinat.forcesofgravium.data.SourceBlockDataStore;
import dev.moxinat.forcesofgravium.data.SourceBlockDataStore.SourceBlockData;
import dev.moxinat.forcesofgravium.data.StateTimeline;
import org.bson.BsonArray;
import org.bson.BsonBoolean;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonString;
import org.bson.BsonValue;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class WorldSaveFileService {

    private static final String SAVE_FOLDER = "forcesofgravium";
    private static final String SAVE_FILE = "worldsave.json";
    private static final long SAVE_THROTTLE_MILLIS = 1000L;
    private static final Map<String, World> LOADED_WORLDS = new ConcurrentHashMap<>();
    private static final Map<String, Long> LAST_SAVE_ATTEMPT_MILLIS = new ConcurrentHashMap<>();
    private static final Map<String, String> LAST_SAVE_ERROR = new ConcurrentHashMap<>();
    private static final Set<String> DIRTY_WORLDS = ConcurrentHashMap.newKeySet();
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

            LOADED_WORLDS.put(key, world);
            LOADING_WORLDS.add(key);
            GravityPowderBlockDataStore.clearWorld(world);
            InverterDataStore.clearWorld(world);
            GraviumSiphonStore.clearWorld(world);
            ConnectableRotationStore.clearWorld(world);
            SourceBlockDataStore.clearWorld(world);

            Path saveFile = saveFile(world);
            if (!Files.exists(saveFile)) {
                LOADING_WORLDS.remove(key);
                return;
            }

            try {
                String content = Files.readString(saveFile, StandardCharsets.UTF_8);
                if (content.isBlank()) {
                    return;
                }

                BsonDocument root = BsonDocument.parse(content);
                loadGravityPowder(world, root.getArray("gravityPowder", new BsonArray()));
                loadInverters(world, root.getArray("inverters", new BsonArray()));
                loadGraviumSiphons(world, root.getArray("graviumSiphons", new BsonArray()));
                loadRotations(world, root.getArray("rotations", new BsonArray()));
                loadSources(world, root.getArray("sources", new BsonArray()));
            } catch (Exception exception) {
                System.err.println("[FoG] Failed to load world save for '" + world.getName() + "': " + exception.getMessage());
            } finally {
                LOADING_WORLDS.remove(key);
            }
        }
    }

    public static void markDirty(@Nonnull World world) {
        ensureLoaded(world);
        String key = worldKey(world);
        if (LOADING_WORLDS.contains(key)) {
            return;
        }

        DIRTY_WORLDS.add(key);

        long now = System.currentTimeMillis();
        long lastAttempt = LAST_SAVE_ATTEMPT_MILLIS.getOrDefault(key, 0L);
        if (now - lastAttempt >= SAVE_THROTTLE_MILLIS) {
            LAST_SAVE_ATTEMPT_MILLIS.put(key, now);
            saveWorld(world);
        }
    }

    public static void saveWorld(@Nonnull World world) {
        saveWorld(world, false);
    }

    public static void forceSaveWorld(@Nonnull World world) {
        saveWorld(world, true);
    }

    private static void saveWorld(@Nonnull World world, boolean force) {
        ensureLoaded(world);
        String key = worldKey(world);
        if (!force && !DIRTY_WORLDS.contains(key)) {
            return;
        }

        synchronized (WorldSaveFileService.class) {
            if (!force && !DIRTY_WORLDS.contains(key)) {
                return;
            }

            try {
                Path saveFile = saveFile(world);
                Files.createDirectories(saveFile.getParent());
                BsonDocument root = new BsonDocument();
                root.put("gravityPowder", serializeGravityPowder(world));
                root.put("inverters", serializeInverters(world));
                root.put("graviumSiphons", serializeGraviumSiphons(world));
                root.put("rotations", serializeRotations(world));
                root.put("sources", serializeSources(world));
                Files.writeString(saveFile, root.toJson(), StandardCharsets.UTF_8);
                DIRTY_WORLDS.remove(key);
                LAST_SAVE_ATTEMPT_MILLIS.put(key, System.currentTimeMillis());
                LAST_SAVE_ERROR.remove(key);
            } catch (IOException exception) {
                String message = exception.getClass().getSimpleName() + ": " + exception.getMessage();
                LAST_SAVE_ERROR.put(key, message);
                System.err.println("[FoG] Failed to save world save for '" + world.getName() + "': " + message);
            }
        }
    }

    public static void saveLoadedWorlds() {
        for (World world : List.copyOf(LOADED_WORLDS.values())) {
            saveWorld(world);
        }
    }

    public static void saveDirtyWorlds() {
        for (World world : List.copyOf(LOADED_WORLDS.values())) {
            if (DIRTY_WORLDS.contains(worldKey(world))) {
                saveWorld(world);
            }
        }
    }

    public static @Nonnull String debugSaveFilePath(@Nonnull World world) {
        return saveFile(world).toAbsolutePath().normalize().toString();
    }

    public static boolean debugSaveFileExists(@Nonnull World world) {
        return Files.exists(saveFile(world));
    }

    public static @Nonnull String debugLastSaveError(@Nonnull World world) {
        return LAST_SAVE_ERROR.getOrDefault(worldKey(world), "");
    }

    private static void loadGravityPowder(World world, BsonArray entries) {
        for (BsonValue value : entries) {
            if (!value.isDocument()) {
                continue;
            }
            BsonDocument entry = value.asDocument();
            Vector3i position = readPosition(entry.getDocument("position"));
            GravityPowderBlockDataStore.put(world, position, readGravityPowderData(entry));
        }
    }

    private static GravityPowderBlockData readGravityPowderData(BsonDocument entry) {
        GravityPowderBlockData data;
        if (entry.containsKey("instantState") || entry.containsKey("push") || entry.containsKey("pull")) {
            boolean push = entry.getBoolean("push", BsonBoolean.FALSE).getValue();
            boolean pull = entry.getBoolean("pull", BsonBoolean.FALSE).getValue();
            String instantState = entry.getString(
                    "instantState",
                    new BsonString(push ? GravityPowderBlockDataStore.STATE_PUSH : pull ? GravityPowderBlockDataStore.STATE_PULL : GravityPowderBlockDataStore.STATE_OFF)
            ).getValue();
            data = new GravityPowderBlockData(
                    entry.getInt32("connectionsMask", new BsonInt32(0)).getValue(),
                    new StateTimeline(
                            instantState,
                            entry.getString("waveState", new BsonString(instantState)).getValue(),
                            entry.getString("previousState", new BsonString(instantState)).getValue()
                    )
            );
        } else if (entry.containsKey("state")) {
            data = GravityPowderBlockDataStore.fromState(
                    entry.getInt32("connectionsMask", new BsonInt32(0)).getValue(),
                    entry.getString("state", new BsonString(GravityPowderBlockDataStore.STATE_OFF)).getValue()
            );
        } else {
            data = GravityPowderBlockDataStore.fromLegacyData(
                    entry.getInt32("connectionsMask", new BsonInt32(0)).getValue(),
                    entry.getString("currentMode", new BsonString(GravityPowderBlockDataStore.STATE_OFF)).getValue(),
                    entry.getString("decayMark", new BsonString("none")).getValue()
            );
        }
        return data.withDirty(entry.containsKey("dirty")
                ? entry.getBoolean("dirty", BsonBoolean.FALSE).getValue()
                : data.hasWaveMismatch());
    }

    private static void loadInverters(World world, BsonArray entries) {
        for (BsonValue value : entries) {
            if (!value.isDocument()) {
                continue;
            }
            BsonDocument entry = value.asDocument();
            Vector3i position = readPosition(entry.getDocument("position"));
            String currentMode = entry.getString("currentMode", new BsonString("off")).getValue();
            InverterData data = new InverterData(
                    currentMode,
                    entry.getString("nextMode", new BsonString("off")).getValue(),
                    entry.getBoolean("invertEnabled", BsonBoolean.TRUE).getValue(),
                    entry.getBoolean("toggleInputActive", BsonBoolean.FALSE).getValue(),
                    new StateTimeline(
                            currentMode,
                            entry.getString("waveState", new BsonString(currentMode)).getValue(),
                            entry.getString("previousState", new BsonString(currentMode)).getValue()
                    )
            );
            InverterDataStore.put(world, position, data);
        }
    }

    private static void loadGraviumSiphons(World world, BsonArray entries) {
        for (BsonValue value : entries) {
            if (!value.isDocument()) {
                continue;
            }
            BsonDocument entry = value.asDocument();
            GraviumSiphonStore.putIfAbsent(
                    world,
                    readPosition(entry.getDocument("position")),
                    new GraviumSiphonData(
                            entry.getBoolean("powered", BsonBoolean.FALSE).getValue(),
                            entry.getBoolean("locked", BsonBoolean.FALSE).getValue()
                    )
            );
        }
    }

    private static void loadRotations(World world, BsonArray entries) {
        for (BsonValue value : entries) {
            if (!value.isDocument()) {
                continue;
            }
            BsonDocument entry = value.asDocument();
            Vector3i position = readPosition(entry.getDocument("position"));
            RotationTuple rotation = RotationTuple.of(
                    readRotation(entry, "yaw"),
                    readRotation(entry, "pitch"),
                    readRotation(entry, "roll")
            );
            ConnectableRotationStore.put(world, position, rotation);
        }
    }

    private static void loadSources(World world, BsonArray entries) {
        for (BsonValue value : entries) {
            if (!value.isDocument()) {
                continue;
            }
            BsonDocument entry = value.asDocument();
            SourceBlockDataStore.put(
                    world,
                    readPosition(entry.getDocument("position")),
                    new SourceBlockData(entry.getBoolean("active", BsonBoolean.FALSE).getValue())
            );
        }
    }

    private static BsonArray serializeGravityPowder(World world) {
        BsonArray result = new BsonArray();
        for (Map.Entry<Vector3i, GravityPowderBlockData> entry : GravityPowderBlockDataStore.snapshotForWorld(world).entrySet()) {
            result.add(writeGravityPowderDocument(entry.getKey(), entry.getValue()));
        }
        return result;
    }

    private static BsonDocument writeGravityPowderDocument(Vector3i position, GravityPowderBlockData data) {
        BsonDocument document = new BsonDocument();
        document.put("position", writePosition(position));
        document.put("connectionsMask", new BsonInt32(data.connectionsMask()));
        document.put("instantState", new BsonString(data.instantState()));
        document.put("waveState", new BsonString(data.waveState()));
        document.put("effectiveState", new BsonString(data.effectiveState()));
        document.put("previousState", new BsonString(data.previousState()));
        document.put("dirty", new BsonBoolean(data.dirty()));
        return document;
    }

    private static BsonArray serializeInverters(World world) {
        BsonArray result = new BsonArray();
        for (Map.Entry<Vector3i, InverterData> entry : InverterDataStore.snapshotForWorld(world).entrySet()) {
            InverterData data = entry.getValue();
            BsonDocument document = new BsonDocument();
            document.put("position", writePosition(entry.getKey()));
            document.put("currentMode", new BsonString(data.currentMode()));
            document.put("nextMode", new BsonString(data.nextMode()));
            document.put("invertEnabled", new BsonBoolean(data.invertEnabled()));
            document.put("toggleInputActive", new BsonBoolean(data.toggleInputActive()));
            document.put("waveState", new BsonString(data.waveState()));
            document.put("effectiveState", new BsonString(data.effectiveState()));
            document.put("previousState", new BsonString(data.previousState()));
            result.add(document);
        }
        return result;
    }

    private static BsonArray serializeGraviumSiphons(World world) {
        BsonArray result = new BsonArray();
        for (Map.Entry<Vector3i, GraviumSiphonData> entry : GraviumSiphonStore.snapshotForWorld(world).entrySet()) {
            BsonDocument document = new BsonDocument();
            document.put("position", writePosition(entry.getKey()));
            document.put("powered", new BsonBoolean(entry.getValue().powered()));
            document.put("locked", new BsonBoolean(entry.getValue().locked()));
            result.add(document);
        }
        return result;
    }

    private static BsonArray serializeRotations(World world) {
        BsonArray result = new BsonArray();
        for (Map.Entry<Vector3i, RotationTuple> entry : ConnectableRotationStore.snapshotForWorld(world).entrySet()) {
            RotationTuple rotation = entry.getValue();
            BsonDocument document = new BsonDocument();
            document.put("position", writePosition(entry.getKey()));
            document.put("yaw", new BsonString(rotation.yaw().name()));
            document.put("pitch", new BsonString(rotation.pitch().name()));
            document.put("roll", new BsonString(rotation.roll().name()));
            result.add(document);
        }
        return result;
    }

    private static BsonArray serializeSources(World world) {
        BsonArray result = new BsonArray();
        for (Map.Entry<Vector3i, SourceBlockData> entry : SourceBlockDataStore.snapshotForWorld(world).entrySet()) {
            BsonDocument document = new BsonDocument();
            document.put("position", writePosition(entry.getKey()));
            document.put("active", new BsonBoolean(entry.getValue().active()));
            result.add(document);
        }
        return result;
    }

    private static BsonDocument writePosition(Vector3i position) {
        BsonDocument document = new BsonDocument();
        document.put("x", new BsonInt32(position.getX()));
        document.put("y", new BsonInt32(position.getY()));
        document.put("z", new BsonInt32(position.getZ()));
        return document;
    }

    private static Vector3i readPosition(BsonDocument document) {
        return new Vector3i(
                document.getInt32("x").getValue(),
                document.getInt32("y").getValue(),
                document.getInt32("z").getValue()
        );
    }

    private static Rotation readRotation(BsonDocument document, String key) {
        return Rotation.valueOf(document.getString(key, new BsonString(Rotation.None.name())).getValue());
    }

    private static Path saveFile(World world) {
        return world.getSavePath().resolve(SAVE_FOLDER).resolve(SAVE_FILE);
    }

    private static String worldKey(World world) {
        return world.getSavePath().toAbsolutePath().normalize().toString();
    }
}
