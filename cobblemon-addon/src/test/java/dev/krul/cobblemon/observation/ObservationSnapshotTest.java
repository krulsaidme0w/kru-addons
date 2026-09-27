package dev.krul.cobblemon.observation;

import org.junit.jupiter.api.Test;
import java.math.BigInteger;
import java.util.*;
import static dev.krul.cobblemon.observation.FactValue.*;
import static org.junit.jupiter.api.Assertions.*;

class ObservationSnapshotTest {
    static final ObservationSnapshot.Scope SCOPE = new ObservationSnapshot.Scope(new UUID(0, 1), new UUID(0, 2));
    static ObservationSnapshot snapshot(Map<String, FactValue> facts) {
        return new ObservationSnapshot(ObservationSnapshot.Identity.entity(SCOPE, new UUID(0, 3)), 123, facts);
    }

    @Test void rulesReceiveScalarsWithoutUnknownDefaultsOrProvenanceObjects() {
        var input = new LinkedHashMap<String, FactValue>();
        input.put("shiny", known(Type.BOOLEAN, Source.LIVE, false));
        input.put("level", unavailable(Type.NUMBER, Source.LIVE, Availability.NOT_SENT));
        input.put("serverBucket", known(Type.STRING, Source.SERVER_MAP, "rare"));
        var snapshot = snapshot(input);
        input.clear();
        assertEquals(Map.of("shiny", false, "serverBucket", "rare"), snapshot.scalarFacts());
        assertEquals(Availability.NOT_SENT, snapshot.facts().get("level").availability());
        assertEquals(Source.SERVER_MAP, snapshot.facts().get("serverBucket").source());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.facts().clear());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.scalarFacts().clear());
    }

    @Test void identitiesSeparateReconnectsWorldInstancesAndEntityFromBlock() {
        var a = ObservationSnapshot.Identity.entity(SCOPE, new UUID(0, 3));
        assertNotEquals(a, ObservationSnapshot.Identity.entity(new ObservationSnapshot.Scope(new UUID(0, 4), new UUID(0, 2)), new UUID(0, 3)));
        assertNotEquals(a, ObservationSnapshot.Identity.entity(new ObservationSnapshot.Scope(new UUID(0, 1), new UUID(0, 4)), new UUID(0, 3)));
        assertNotEquals(a, new ObservationSnapshot.Identity(SCOPE, ObservationSnapshot.Kind.BLOCK_ENTITY, a.subjectId()));
        assertNotEquals(ObservationSnapshot.Identity.block(SCOPE, 1, 23, 4), ObservationSnapshot.Identity.block(SCOPE, 12, 3, 4));
        assertThrows(IllegalArgumentException.class, () -> new ObservationSnapshot.Identity(SCOPE, ObservationSnapshot.Kind.ENTITY, "x".repeat(257)));
    }

    @Test void scalarsRejectLiveObjectsNonFiniteUnsafeOrOverlongValues() {
        for (Object bad : List.of(new Object(), List.of("value"), new StringBuilder("value"), "x".repeat(257)))
            assertThrows(IllegalArgumentException.class, () -> known(Type.STRING, Source.LIVE, bad));
        for (Object bad : List.of(Double.NaN, Double.POSITIVE_INFINITY, 9007199254740992L, new BigInteger("9007199254740992")))
            assertThrows(IllegalArgumentException.class, () -> known(Type.NUMBER, Source.LIVE, bad));
        assertEquals(9007199254740991d, known(Type.NUMBER, Source.LIVE, 9007199254740991L).value());
        assertEquals("x".repeat(256), known(Type.STRING, Source.LIVE, "x".repeat(256)).value());
        assertThrows(IllegalArgumentException.class, () -> new FactValue(Type.BOOLEAN, Source.LIVE, Availability.NOT_SENT, false));
    }

    @Test void schemaAcceptsExistingTypedFieldsAndKeepsRaritySourcesSeparate() {
        var input = new LinkedHashMap<String, FactValue>();
        for (String key : List.of("kind", "dimension", "registry_id", "name", "item_id", "head_item_id", "aspects", "species", "form", "ownerUuid"))
            input.put(key, known(Type.STRING, Source.LIVE, "value"));
        for (String key : List.of("x", "y", "z", "model_data", "head_model_data", "level"))
            input.put(key, known(Type.NUMBER, Source.LIVE, 42));
        for (String key : List.of("opened", "shiny", "wild")) input.put(key, known(Type.BOOLEAN, Source.LIVE, true));
        input.put("speciesClass", known(Type.STRING, Source.REGISTRY_REFERENCE, "legendary"));
        input.put("referenceTypes", known(Type.STRING, Source.REGISTRY_REFERENCE, "psychic,fairy"));
        input.put("serverBucket", known(Type.STRING, Source.SERVER_MAP, "rare"));
        input.put("referenceBucket", known(Type.STRING, Source.REGISTRY_REFERENCE, "common"));
        input.put("profileLabel", known(Type.STRING, Source.PROFILE, "wanted"));
        assertEquals(24, snapshot(input).scalarFacts().size());
        assertEquals(Type.NUMBER, FactSchema.field("level").orElseThrow().type());
        assertTrue(FactSchema.field("typo").isEmpty());
        assertThrows(IllegalArgumentException.class, () -> snapshot(Map.of("typo", known(Type.STRING, Source.LIVE, "x"))));
        assertThrows(IllegalArgumentException.class, () -> snapshot(Map.of("level", known(Type.STRING, Source.LIVE, "42"))));
        assertThrows(IllegalArgumentException.class, () -> snapshot(Map.of("referenceTypes", known(Type.STRING, Source.LIVE, "fire"))));
        assertThrows(IllegalArgumentException.class, () -> snapshot(Map.of("serverBucket", known(Type.STRING, Source.PROFILE, "rare"))));
    }

    @Test void oversizedSnapshotsAreRejectedInsteadOfSilentlyDroppingEvidence() {
        var input = new LinkedHashMap<String, FactValue>();
        for (int i = 0; i < 65; i++) input.put("field" + i, known(Type.BOOLEAN, Source.LIVE, false));
        var error = assertThrows(IllegalArgumentException.class, () -> snapshot(input));
        assertTrue(error.getMessage().contains("64"));
    }
}
