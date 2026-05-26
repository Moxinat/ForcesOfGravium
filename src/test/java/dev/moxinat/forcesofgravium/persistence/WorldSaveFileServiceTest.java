package dev.moxinat.forcesofgravium.persistence;

import com.hypixel.hytale.math.vector.Vector3i;
import dev.moxinat.forcesofgravium.data.GravityPowderBlockDataStore;
import dev.moxinat.forcesofgravium.data.GravityPowderBlockDataStore.GravityPowderBlockData;
import dev.moxinat.forcesofgravium.data.StateTimeline;
import org.bson.BsonBoolean;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonString;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldSaveFileServiceTest {

    @Test
    void gravityPowderSaveWritesDirtyTrue() throws Exception {
        BsonDocument document = writeGravityPowderDocument(
                new GravityPowderBlockData(
                        3,
                        new StateTimeline(
                                GravityPowderBlockDataStore.STATE_PUSH,
                                GravityPowderBlockDataStore.STATE_OFF,
                                GravityPowderBlockDataStore.STATE_OFF
                        ),
                        true
                )
        );

        assertTrue(document.getBoolean("dirty").getValue());
    }

    @Test
    void gravityPowderSaveWritesDirtyFalse() throws Exception {
        BsonDocument document = writeGravityPowderDocument(GravityPowderBlockData.defaultData());

        assertFalse(document.getBoolean("dirty").getValue());
    }

    @Test
    void gravityPowderLoadReadsDirtyTrue() throws Exception {
        GravityPowderBlockData data = readGravityPowderData(currentGravityPowderEntry(BsonBoolean.TRUE));

        assertTrue(data.dirty());
    }

    @Test
    void gravityPowderLoadReadsDirtyFalse() throws Exception {
        GravityPowderBlockData data = readGravityPowderData(currentGravityPowderEntry(BsonBoolean.FALSE));

        assertFalse(data.dirty());
    }

    @Test
    void gravityPowderLoadWithoutDirtyUsesWaveMismatchDefault() throws Exception {
        BsonDocument entry = currentGravityPowderEntry(null);

        GravityPowderBlockData data = readGravityPowderData(entry);

        assertTrue(data.dirty());
        assertEquals(GravityPowderBlockDataStore.STATE_PUSH, data.instantState());
        assertEquals(GravityPowderBlockDataStore.STATE_OFF, data.waveState());
    }

    @Test
    void legacyStateGravityPowderLoadWithoutDirtyStillWorks() throws Exception {
        BsonDocument entry = baseEntry();
        entry.put("state", new BsonString(GravityPowderBlockDataStore.STATE_PULL));

        GravityPowderBlockData data = readGravityPowderData(entry);

        assertFalse(data.dirty());
        assertEquals(GravityPowderBlockDataStore.STATE_PULL, data.instantState());
        assertEquals(GravityPowderBlockDataStore.STATE_PULL, data.waveState());
    }

    @Test
    void legacyCurrentModeGravityPowderLoadWithoutDirtyStillWorks() throws Exception {
        BsonDocument entry = baseEntry();
        entry.put("currentMode", new BsonString(GravityPowderBlockDataStore.STATE_PUSH));
        entry.put("decayMark", new BsonString("none"));

        GravityPowderBlockData data = readGravityPowderData(entry);

        assertFalse(data.dirty());
        assertEquals(GravityPowderBlockDataStore.STATE_PUSH, data.instantState());
        assertEquals(GravityPowderBlockDataStore.STATE_PUSH, data.waveState());
    }

    @Test
    void legacyPushPullGravityPowderLoadWithoutDirtyStillWorks() throws Exception {
        BsonDocument entry = baseEntry();
        entry.put("push", BsonBoolean.TRUE);
        entry.put("pull", BsonBoolean.FALSE);

        GravityPowderBlockData data = readGravityPowderData(entry);

        assertFalse(data.dirty());
        assertEquals(GravityPowderBlockDataStore.STATE_PUSH, data.instantState());
        assertEquals(GravityPowderBlockDataStore.STATE_PUSH, data.waveState());
    }

    private static BsonDocument currentGravityPowderEntry(BsonBoolean dirty) {
        BsonDocument entry = baseEntry();
        entry.put("instantState", new BsonString(GravityPowderBlockDataStore.STATE_PUSH));
        entry.put("waveState", new BsonString(GravityPowderBlockDataStore.STATE_OFF));
        entry.put("previousState", new BsonString(GravityPowderBlockDataStore.STATE_OFF));
        if (dirty != null) {
            entry.put("dirty", dirty);
        }
        return entry;
    }

    private static BsonDocument baseEntry() {
        BsonDocument entry = new BsonDocument();
        entry.put("position", positionDocument());
        entry.put("connectionsMask", new BsonInt32(3));
        return entry;
    }

    private static BsonDocument positionDocument() {
        BsonDocument position = new BsonDocument();
        position.put("x", new BsonInt32(1));
        position.put("y", new BsonInt32(2));
        position.put("z", new BsonInt32(3));
        return position;
    }

    private static BsonDocument writeGravityPowderDocument(GravityPowderBlockData data) throws Exception {
        Method method = WorldSaveFileService.class.getDeclaredMethod(
                "writeGravityPowderDocument",
                Vector3i.class,
                GravityPowderBlockData.class
        );
        method.setAccessible(true);
        return (BsonDocument) method.invoke(null, new Vector3i(1, 2, 3), data);
    }

    private static GravityPowderBlockData readGravityPowderData(BsonDocument entry) throws Exception {
        Method method = WorldSaveFileService.class.getDeclaredMethod("readGravityPowderData", BsonDocument.class);
        method.setAccessible(true);
        return (GravityPowderBlockData) method.invoke(null, entry);
    }
}
