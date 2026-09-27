package dev.krul.cobblemon.rules;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/** Immutable editor value. Modify an editableJson() copy, then create a new draft with withJson(). */
public final class ProfileDraft {
    private final RuleProfile profile;
    private final boolean migration;
    private final JsonObject document;
    private final String rawJson;

    private ProfileDraft(RuleProfile profile, boolean migration, JsonObject document, String rawJson) {
        this.profile = profile;
        this.migration = migration;
        this.document = document.deepCopy();
        this.rawJson = rawJson;
    }

    public static ProfileDraft parse(String json) {
        RuleProfile parsed = RuleProfile.parse(json);
        boolean migration = parsed.diagnostics().isEmpty() && parsed.sourceVersion() == 1;
        // Editor import must also prove that the migrated v2 predicates satisfy today's schema.
        if (migration) parsed = RuleProfile.migrateV1(json, Map.of());
        JsonObject document = parsed.editableJson();
        if (!parsed.diagnostics().isEmpty() && json != null && json.getBytes(StandardCharsets.UTF_8).length <= 131072) {
            try { document = JsonParser.parseString(json).getAsJsonObject(); }
            catch (RuntimeException invalidJson) { /* Raw text remains available for JSON editor diagnostics. */ }
        }
        return new ProfileDraft(parsed, migration, document, json);
    }

    public static ProfileDraft fromJson(JsonObject document) { return parse(document == null ? null : document.toString()); }
    public static ProfileDraft migrateV1(String json, Map<String, RuleCategory> overrides) {
        RuleProfile profile = RuleProfile.migrateV1(json, overrides);
        return new ProfileDraft(profile, true, profile.editableJson(), json);
    }
    static ProfileDraft unavailable(String reason) { return new ProfileDraft(RuleProfile.unavailable(reason), false, new JsonObject(), null); }
    /** Carries migration provenance through native editor changes. */
    public ProfileDraft withJson(JsonObject document) {
        ProfileDraft changed = fromJson(document);
        return new ProfileDraft(changed.profile, migration || changed.migration, changed.document, changed.rawJson);
    }
    public RuleProfile validate() { return profile; }
    public boolean valid() { return profile.diagnostics().isEmpty(); }
    public List<String> diagnostics() { return profile.diagnostics(); }
    public boolean requiresMigrationConfirmation() { return migration; }
    public JsonObject editableJson() { return document.deepCopy(); }
    /** Original submitted text (possibly null/invalid); canonical v2 output is validate().canonicalJson(). */
    public String rawJson() { return rawJson; }
    public List<RuleExplanation> preview(Map<String, ?> scalarFacts) { return profile.explain(scalarFacts); }
}
