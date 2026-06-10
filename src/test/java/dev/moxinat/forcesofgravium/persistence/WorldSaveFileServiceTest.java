package dev.moxinat.forcesofgravium.persistence;

import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import dev.moxinat.forcesofgravium.connectable.core.ConnectableRuntimeData;
import dev.moxinat.forcesofgravium.block.gravity.GravityPowderSpecialStateStore;
import org.bson.BsonBoolean;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonString;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldSaveFileServiceTest {

    @Test
    void connectableRuntimeLoadPreservesSharedFieldsButNotNetworkId() throws Exception {
        BsonDocument entry = baseEntry();
        entry.put("previousInstantState", new BsonString(GravityPowderSpecialStateStore.STATE_OFF));
        entry.put("instantState", new BsonString(GravityPowderSpecialStateStore.STATE_PUSH));
        entry.put("previousEffectiveState", new BsonString(GravityPowderSpecialStateStore.STATE_OFF));
        entry.put("effectiveState", new BsonString(GravityPowderSpecialStateStore.STATE_OFF));
        entry.put("dirty", BsonBoolean.TRUE);
        entry.put("invertEnabled", BsonBoolean.TRUE);
        entry.put("passing", BsonBoolean.TRUE);
        entry.put("energyDelta", new BsonInt32(1));
        entry.put("networkId", new BsonInt32(99));

        ConnectableRuntimeData data = readConnectableRuntimeData(
                entry,
                RotationTuple.of(Rotation.Ninety, Rotation.None, Rotation.OneEighty)
        );

        assertEquals(GravityPowderSpecialStateStore.STATE_PUSH, data.instantState());
        assertEquals(GravityPowderSpecialStateStore.STATE_OFF, data.effectiveState());
        assertTrue(data.dirty());
        assertTrue(data.invertEnabled());
        assertTrue(data.passing());
        assertEquals(1, data.energyDelta());
        assertEquals(ConnectableRuntimeData.NO_NETWORK, data.networkId());
        assertEquals(Rotation.Ninety, data.rotation().yaw());
        assertEquals(Rotation.OneEighty, data.rotation().roll());
    }

    private static BsonDocument baseEntry() {
        BsonDocument entry = new BsonDocument();
        BsonDocument position = new BsonDocument();
        position.put("x", new BsonInt32(1));
        position.put("y", new BsonInt32(2));
        position.put("z", new BsonInt32(3));
        entry.put("position", position);
        return entry;
    }

    private static ConnectableRuntimeData readConnectableRuntimeData(BsonDocument entry, RotationTuple rotation) throws Exception {
        Method method = WorldSaveFileService.class.getDeclaredMethod(
                "readConnectableRuntimeData",
                BsonDocument.class,
                RotationTuple.class
        );
        method.setAccessible(true);
        return (ConnectableRuntimeData) method.invoke(null, entry, rotation);
    }
}
