package dev.moxinat.forcesofgravium.persistence;

import org.joml.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import dev.moxinat.forcesofgravium.connectable.core.ConnectableDataStore;
import dev.moxinat.forcesofgravium.connectable.core.ConnectableRuntimeData;
import dev.moxinat.forcesofgravium.block.gravity.GravityPowderSpecialStateStore;
import dev.moxinat.forcesofgravium.block.gravity.GravityPowderSpecialStateStore.GravityPowderBlockData;
import dev.moxinat.forcesofgravium.block.siphon.GraviumSiphonStore;
import dev.moxinat.forcesofgravium.block.siphon.GraviumSiphonStore.GraviumSiphonData;
import dev.moxinat.forcesofgravium.block.inverter.InverterSpecialStateStore;
import dev.moxinat.forcesofgravium.block.inverter.InverterSpecialStateStore.InverterData;
import dev.moxinat.forcesofgravium.data.StateTimeline;
import dev.moxinat.forcesofgravium.connectable.network.ConnectableNetworkIndexer;
import dev.moxinat.forcesofgravium.connectable.registry.ConnectableRegistry;
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
            clearWorldStores(world);

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
                if (root.containsKey("connectables")) {
                    loadConnectables(world, root.getArray("connectables", new BsonArray()));
                } else {
                    System.err.println("[FoG] Legacy worldsave without connectables is no longer supported in this alpha build.");
                }
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
                root.put("connectables", serializeConnectables(world));
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

    private static void clearWorldStores(World world) {
        ConnectableDataStore.clearWorld(world);
        GravityPowderSpecialStateStore.clearWorld(world);
        InverterSpecialStateStore.clearWorld(world);
        GraviumSiphonStore.clearWorld(world);
        ConnectableNetworkIndexer.clearWorld(world);
    }

    private static void loadConnectables(World world, BsonArray entries) {
        for (BsonValue value : entries) {
            if (!value.isDocument()) {
                continue;
            }
            BsonDocument entry = value.asDocument();
            Vector3i position = readPosition(entry.getDocument("position"));
            String blockId = entry.getString("blockId", new BsonString(blockIdAt(world, position))).getValue();
            RotationTuple rotation = readRotationTuple(entry.getDocument("rotation", new BsonDocument()));
            ConnectableRuntimeData runtimeData = readConnectableRuntimeData(entry, rotation);

            ConnectableDataStore.put(world, position, runtimeData);
            restoreCompatibilityStores(world, position, blockId, entry, runtimeData);
        }
    }

    private static ConnectableRuntimeData readConnectableRuntimeData(BsonDocument entry, RotationTuple rotation) {
        return new ConnectableRuntimeData(
                rotation,
                entry.getString("previousInstantState", new BsonString(GravityPowderSpecialStateStore.STATE_OFF)).getValue(),
                entry.getString("instantState", new BsonString(GravityPowderSpecialStateStore.STATE_OFF)).getValue(),
                entry.getString("previousEffectiveState", new BsonString(GravityPowderSpecialStateStore.STATE_OFF)).getValue(),
                entry.getString("effectiveState", new BsonString(GravityPowderSpecialStateStore.STATE_OFF)).getValue(),
                entry.getBoolean("dirty", BsonBoolean.FALSE).getValue(),
                entry.getBoolean("invertEnabled", BsonBoolean.FALSE).getValue(),
                entry.getBoolean("passing", BsonBoolean.FALSE).getValue(),
                entry.getInt32("energyDelta", new BsonInt32(0)).getValue(),
                ConnectableRuntimeData.NO_NETWORK
        );
    }

    private static void restoreCompatibilityStores(
            World world,
            Vector3i position,
            String blockId,
            BsonDocument entry,
            ConnectableRuntimeData runtimeData
    ) {
        if (ConnectableRegistry.isGravityPowderCarrierId(blockId)) {
            GravityPowderSpecialStateStore.put(
                    world,
                    position,
                    new GravityPowderBlockData(
                            entry.getInt32("connectionsMask", new BsonInt32(0)).getValue(),
                            timelineForRuntime(runtimeData),
                            runtimeData.dirty()
                    )
            );
        }

        if (ConnectableRegistry.isInverterId(blockId)) {
            InverterSpecialStateStore.put(
                    world,
                    position,
                    new InverterData(
                            runtimeData.instantState(),
                            entry.getString("nextMode", new BsonString(runtimeData.instantState())).getValue(),
                            runtimeData.invertEnabled(),
                            entry.getString("lastToggleInputMode", new BsonString(GravityPowderSpecialStateStore.STATE_OFF)).getValue(),
                            timelineForRuntime(runtimeData),
                            runtimeData.dirty()
                    )
            );
        }

        if (ConnectableRegistry.isGraviumSiphonId(blockId)) {
            GraviumSiphonStore.putIfAbsent(
                    world,
                    position,
                    new GraviumSiphonData(
                            entry.getBoolean("siphonPowered", BsonBoolean.FALSE).getValue(),
                            entry.getBoolean("siphonLocked", BsonBoolean.FALSE).getValue()
                    )
            );
        }
    }

    private static BsonArray serializeConnectables(World world) {
        BsonArray result = new BsonArray();
        for (Vector3i position : ConnectableDataStore.snapshotForWorld(world).keySet()) {
            String blockId = blockIdAt(world, position);
            if (blockId.isBlank()) {
                continue;
            }

            ConnectableRuntimeData data = ConnectableDataStore.get(world, position);
            if (data != null) {
                result.add(writeConnectableDocument(world, position, blockId, data));
            }
        }
        return result;
    }

    private static BsonDocument writeConnectableDocument(
            World world,
            Vector3i position,
            String blockId,
            ConnectableRuntimeData data
    ) {
        BsonDocument document = new BsonDocument();
        document.put("position", writePosition(position));
        document.put("blockId", new BsonString(blockId));
        document.put("rotation", writeRotation(data.rotation()));
        document.put("previousInstantState", new BsonString(data.previousInstantState()));
        document.put("instantState", new BsonString(data.instantState()));
        document.put("previousEffectiveState", new BsonString(data.previousEffectiveState()));
        document.put("effectiveState", new BsonString(data.effectiveState()));
        document.put("dirty", new BsonBoolean(data.dirty()));
        document.put("invertEnabled", new BsonBoolean(data.invertEnabled()));
        document.put("passing", new BsonBoolean(data.passing()));
        document.put("energyDelta", new BsonInt32(data.energyDelta()));

        GravityPowderBlockData powderData = GravityPowderSpecialStateStore.get(world, position);
        if (powderData != null) {
            document.put("connectionsMask", new BsonInt32(powderData.connectionsMask()));
        }

        InverterData inverterData = InverterSpecialStateStore.get(world, position);
        if (inverterData != null) {
            document.put("nextMode", new BsonString(inverterData.nextMode()));
            document.put("lastToggleInputMode", new BsonString(inverterData.lastToggleInputMode()));
        }

        GraviumSiphonData siphonData = GraviumSiphonStore.get(world, position);
        if (siphonData != null) {
            document.put("siphonPowered", new BsonBoolean(siphonData.powered()));
            document.put("siphonLocked", new BsonBoolean(siphonData.locked()));
        }

        return document;
    }

    private static BsonDocument writePosition(Vector3i position) {
        BsonDocument document = new BsonDocument();
        document.put("x", new BsonInt32(position.x()));
        document.put("y", new BsonInt32(position.y()));
        document.put("z", new BsonInt32(position.z()));
        return document;
    }

    private static Vector3i readPosition(BsonDocument document) {
        return new Vector3i(
                document.getInt32("x").getValue(),
                document.getInt32("y").getValue(),
                document.getInt32("z").getValue()
        );
    }

    private static BsonDocument writeRotation(RotationTuple rotation) {
        BsonDocument document = new BsonDocument();
        document.put("yaw", new BsonString(rotation.yaw().name()));
        document.put("pitch", new BsonString(rotation.pitch().name()));
        document.put("roll", new BsonString(rotation.roll().name()));
        return document;
    }

    private static RotationTuple readRotationTuple(BsonDocument document) {
        return RotationTuple.of(
                readRotation(document, "yaw"),
                readRotation(document, "pitch"),
                readRotation(document, "roll")
        );
    }

    private static Rotation readRotation(BsonDocument document, String key) {
        return Rotation.valueOf(document.getString(key, new BsonString(Rotation.None.name())).getValue());
    }

    private static StateTimeline timelineForRuntime(ConnectableRuntimeData data) {
        return new StateTimeline(
                data.instantState(),
                data.effectiveState(),
                data.previousEffectiveState()
        );
    }

    private static String blockIdAt(World world, Vector3i position) {
        BlockType blockType = world.getBlockType(position.x(), position.y(), position.z());
        return blockType == null ? "" : blockType.getId();
    }

    private static Path saveFile(World world) {
        return world.getSavePath().resolve(SAVE_FOLDER).resolve(SAVE_FILE);
    }

    private static String worldKey(World world) {
        return world.getSavePath().toAbsolutePath().normalize().toString();
    }
}
