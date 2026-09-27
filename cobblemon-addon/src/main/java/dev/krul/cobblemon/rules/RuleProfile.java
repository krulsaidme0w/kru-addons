package dev.krul.cobblemon.rules;

import com.google.gson.*;
import meteordevelopment.starscript.Script;
import meteordevelopment.starscript.Starscript;
import meteordevelopment.starscript.compiler.Compiler;
import meteordevelopment.starscript.compiler.Parser;
import meteordevelopment.starscript.value.Value;
import meteordevelopment.starscript.value.ValueMap;

import java.util.*;
import java.util.regex.Pattern;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import dev.krul.cobblemon.observation.FactSchema;
import dev.krul.cobblemon.observation.FactValue;

/** Immutable, bounded recognition configuration. Predicates are data; only labels use Starscript. */
public final class RuleProfile {
    public enum Truth { MATCH, NO_MATCH, UNKNOWN }
    public record Match(String ruleId, String target, String label) {}
    private static final Set<String> TARGETS = Set.of("CHEST", "POKE_BALL", "WEAPON", "GUARDIAN", "STRONG_POKEMON");
    private static final Set<String> OPERATORS = Set.of("equals", "min", "max", "suffix", "contains", "token");
    private static final Pattern FIELD = Pattern.compile("[a-zA-Z_][a-zA-Z0-9_]{0,63}");
    private static final BigDecimal MAX_SAFE_NUMBER = new BigDecimal("9007199254740991");
    private final List<Rule> rules;
    private final List<String> diagnostics;
    private final List<RuleCategory> categories;
    private final JsonObject document;
    private final int sourceVersion;
    private final Settings settings;

    public record Settings(int scanIntervalSeconds, int visualRange, boolean crosshairInspection, boolean verboseLogs) {
        public static final Settings DEFAULT = new Settings(5, 1024, false, false);

        public Settings {
            if (scanIntervalSeconds < 1 || scanIntervalSeconds > 3600)
                throw new IllegalArgumentException("scanIntervalSeconds must be between 1 and 3600");
            if (visualRange < 1 || visualRange > 1024)
                throw new IllegalArgumentException("visualRange must be between 1 and 1024");
        }
    }

    private RuleProfile(List<Rule> rules, List<String> diagnostics) {
        this(rules, diagnostics, List.of(), new JsonObject(), 2, Settings.DEFAULT);
    }

    private RuleProfile(List<Rule> rules, List<String> diagnostics, List<RuleCategory> categories,
                        JsonObject document, int sourceVersion, Settings settings) {
        this.rules = List.copyOf(rules);
        this.diagnostics = List.copyOf(diagnostics);
        this.categories = List.copyOf(categories);
        this.document = document.deepCopy();
        this.sourceVersion = sourceVersion;
        this.settings = settings;
    }

    public static RuleProfile disabled() { return new RuleProfile(List.of(), List.of()); }
    public static RuleProfile unavailable(String reason) { return new RuleProfile(List.of(), List.of(reason)); }
    public int size() { return rules.size(); }
    public List<String> diagnostics() { return diagnostics; }
    public List<RuleCategory> categories() { return categories; }
    public int sourceVersion() { return sourceVersion; }
    public Settings settings() { return settings; }
    /** Defensive v2 document in original file order, not priority evaluation order. */
    public JsonObject editableJson() { return document.deepCopy(); }
    public String canonicalJson() { return document.toString(); }

    public static RuleProfile parse(String json) {
        try {
            if (json == null || json.getBytes(StandardCharsets.UTF_8).length > 131072) throw new IllegalArgumentException("Profile exceeds 128 KiB");
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            int version = readVersion(root);
            if (version != 1 && version != 2) throw new IllegalArgumentException("Expected profile version 1 or 2");
            requireKeys(root, version == 1 ? Set.of("version", "rules") : Set.of("version", "settings", "categories", "rules"));
            if (version == 1) root = migrateDocument(root, Map.of());
            Settings settings = readSettings(root);
            List<RuleCategory> categories = readCategories(root);
            Map<String, RuleCategory> byId = new LinkedHashMap<>();
            for (RuleCategory category : categories) byId.put(category.id(), category);
            JsonArray entries = root.getAsJsonArray("rules");
            if (entries == null || entries.size() > 128) throw new IllegalArgumentException("Expected at most 128 rules");
            List<Rule> rules = new ArrayList<>();
            Set<String> ids = new HashSet<>();
            for (JsonElement entry : entries) {
                JsonObject object = entry.getAsJsonObject();
                requireKeys(object, Set.of("id", "enabled", "categoryId", "priority", "label", "all"));
                String id = text(object, "id", 64);
                if (!ids.add(id)) throw new IllegalArgumentException("Duplicate rule: " + id);
                String target = text(object, "categoryId", 64);
                if (!byId.containsKey(target)) throw new IllegalArgumentException("Unknown category: " + target);
                boolean enabled = bool(object, "enabled");
                int priority = integer(object, "priority");
                String label = text(object, "label", 512);
                Parser.Result parsed = Parser.parse(label);
                if (parsed.hasErrors()) throw new IllegalArgumentException("Invalid Starscript label in " + id + ": " + parsed.errors);
                JsonArray all = object.getAsJsonArray("all");
                if (all == null || all.isEmpty() || all.size() > 16)
                    throw new IllegalArgumentException("Rule " + id + " requires 1–16 predicates");
                List<Predicate> predicates = new ArrayList<>();
                for (JsonElement condition : all) {
                    JsonObject p = condition.getAsJsonObject();
                    String field = text(p, "field", 64);
                    if (!FIELD.matcher(field).matches()) throw new IllegalArgumentException("Invalid fact field " + field);
                    List<String> operators = p.keySet().stream().filter(OPERATORS::contains).toList();
                    if (p.size() != 2 || operators.size() != 1) throw new IllegalArgumentException("Expected field and exactly one supported predicate");
                    String operator = operators.getFirst();
                    JsonPrimitive value = p.getAsJsonPrimitive(operator);
                    if (value == null) throw new IllegalArgumentException("Predicate requires a scalar");
                    if (value.isNumber() && value.getAsBigDecimal().abs().compareTo(MAX_SAFE_NUMBER) > 0)
                        throw new IllegalArgumentException("Numeric predicate exceeds the safe Starscript number range");
                    Object expected = value.isBoolean() ? value.getAsBoolean() : value.isNumber() ? value.getAsDouble() : value.getAsString();
                    if (Set.of("min", "max").contains(operator) && !(expected instanceof Number)
                        || Set.of("suffix", "contains", "token").contains(operator) && !(expected instanceof String))
                        throw new IllegalArgumentException("Invalid value for " + operator);
                    if (expected instanceof Number n && !Double.isFinite(n.doubleValue())) throw new IllegalArgumentException("Non-finite number");
                    if (expected instanceof String s && (s.isEmpty() || s.length() > 256)) throw new IllegalArgumentException("Invalid predicate string length");
                    if (version == 2) {
                        FactSchema.Field schema = FactSchema.field(field).orElseThrow(() -> new IllegalArgumentException("Unknown fact: " + field));
                        if (!hasType(expected, schema.type())) throw new IllegalArgumentException("Wrong predicate type for " + field);
                    }
                    predicates.add(new Predicate(field, operator, expected));
                }
                rules.add(new Rule(id, target, enabled && byId.get(target).enabled(), priority, Compiler.compile(parsed), List.copyOf(predicates)));
            }
            rules.sort(Comparator.comparingInt(Rule::priority).reversed());
            return new RuleProfile(rules, List.of(), categories, root, version, settings);
        } catch (RuntimeException error) {
            return unavailable("Invalid profile: " + Objects.toString(error.getMessage(), error.getClass().getSimpleName()));
        }
    }

    public Optional<Match> match(Map<String, ?> facts) {
        if (rules.isEmpty()) return Optional.empty();
        Map<String, Object> bounded = snapshot(facts);
        for (Rule rule : rules) {
            if (rule.enabled && rule.predicates.stream().allMatch(p -> p.test(bounded) == Truth.MATCH))
                return Optional.of(new Match(rule.id, rule.target, label(rule, bounded)));
        }
        return Optional.empty();
    }

    public List<RuleExplanation> explain(Map<String, ?> facts) {
        if (rules.isEmpty()) return List.of();
        Map<String, Object> bounded = snapshot(facts);
        List<RuleExplanation> result = new ArrayList<>();
        for (Rule rule : rules) {
            List<RuleExplanation.Condition> conditions = rule.predicates.stream().map(p ->
                new RuleExplanation.Condition(p.field, p.operator, p.expected, bounded.get(p.field), p.test(bounded))).toList();
            Truth truth = conditions.stream().anyMatch(c -> c.truth() == Truth.NO_MATCH) ? Truth.NO_MATCH
                : conditions.stream().anyMatch(c -> c.truth() == Truth.UNKNOWN) ? Truth.UNKNOWN : Truth.MATCH;
            result.add(new RuleExplanation(rule.id, rule.target, rule.enabled, truth, conditions));
        }
        return List.copyOf(result);
    }

    private Map<String, Object> snapshot(Map<String, ?> facts) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (facts == null) return Map.of();
        var entries = facts.entrySet().iterator();
        for (int examined = 0; examined < 64 && entries.hasNext(); examined++) {
            var entry = entries.next();
            String key = entry.getKey();
            Object value = entry.getValue();
            if (key == null || !FIELD.matcher(key).matches() || value == null) continue;
            if (sourceVersion == 2 && (FactSchema.field(key).isEmpty() || !hasType(value, FactSchema.field(key).orElseThrow().type()))) continue;
            if (value instanceof Boolean) result.put(key, value);
            else if (value instanceof Number n && safeNumber(n)) result.put(key, n.doubleValue());
            else if (value instanceof String s && s.length() <= 256) result.put(key, s);
        }
        return Map.copyOf(result);
    }

    private static boolean safeNumber(Number number) {
        if (!(number instanceof Byte || number instanceof Short || number instanceof Integer || number instanceof Long
            || number instanceof Float || number instanceof Double || number instanceof BigInteger || number instanceof BigDecimal)) return false;
        if (!Double.isFinite(number.doubleValue())) return false;
        try { return new BigDecimal(number.toString()).abs().compareTo(MAX_SAFE_NUMBER) <= 0; }
        catch (NumberFormatException invalid) { return false; }
    }

    private static String label(Rule rule, Map<String, Object> facts) {
        // No Meteor global context, Entity wrappers, functions, commands or retained live objects.
        Starscript script = new Starscript();
        ValueMap observation = new ValueMap();
        facts.forEach((key, value) -> observation.set(key, value instanceof Boolean b ? Value.bool(b)
            : value instanceof Number n ? Value.number(n.doubleValue()) : Value.string(value.toString())));
        script.set("observation", observation);
        try {
            String result = script.run(rule.label).toString().replaceAll("[\\r\\n\\p{Cntrl}]", " ");
            return result.isBlank() ? rule.id : result.substring(0, Math.min(128, result.length()));
        } catch (RuntimeException error) { return rule.id; }
    }

    private static String text(JsonObject object, String key, int maximum) {
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString())
            throw new IllegalArgumentException("Expected string " + key);
        String value = element.getAsString();
        if (value.isBlank() || value.length() > maximum) throw new IllegalArgumentException("Invalid " + key + " length");
        return value;
    }

    private static void requireKeys(JsonObject object, Set<String> allowed) {
        for (String key : object.keySet()) if (!allowed.contains(key)) throw new IllegalArgumentException("Unknown key: " + key);
    }

    private static boolean hasType(Object value, FactValue.Type type) {
        return switch (type) {
            case STRING -> value instanceof String;
            case NUMBER -> value instanceof Number;
            case BOOLEAN -> value instanceof Boolean;
        };
    }

    private static boolean bool(JsonObject object, String key) {
        JsonElement e = object.get(key);
        if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isBoolean())
            throw new IllegalArgumentException("Expected boolean " + key);
        return e.getAsBoolean();
    }

    private static int integer(JsonObject object, String key) {
        return number(object.get(key), key).intValueExact();
    }

    /** Shared with persistence detection; deliberately independent of rule validity. */
    static int readVersion(JsonObject root) { return integer(root, "version"); }

    private static BigDecimal number(JsonElement e, String key) {
        if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber())
            throw new IllegalArgumentException("Expected number " + key);
        return e.getAsBigDecimal();
    }

    private static List<RuleCategory> readCategories(JsonObject root) {
        JsonArray array = root.getAsJsonArray("categories");
        if (array == null || array.size() > 64) throw new IllegalArgumentException("Expected at most 64 categories");
        List<RuleCategory> result = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (JsonElement entry : array) {
            JsonObject c = entry.getAsJsonObject();
            requireKeys(c, Set.of("id", "name", "enabled", "color", "boxes", "tracers", "nametags", "arrows", "maxDistance"));
            String id = text(c, "id", 64);
            if (!ids.add(id)) throw new IllegalArgumentException("Duplicate category: " + id);
            JsonArray rgba = c.getAsJsonArray("color");
            if (rgba == null || rgba.size() != 4) throw new IllegalArgumentException("Expected RGBA color");
            List<Integer> color = new ArrayList<>();
            for (JsonElement channel : rgba) color.add(number(channel, "color").intValueExact());
            result.add(new RuleCategory(id, text(c, "name", 128), bool(c, "enabled"), color,
                bool(c, "boxes"), bool(c, "tracers"), bool(c, "nametags"), bool(c, "arrows"), number(c.get("maxDistance"), "maxDistance").doubleValue()));
        }
        return result;
    }

    private static Settings readSettings(JsonObject root) {
        JsonObject object = root.getAsJsonObject("settings");
        if (object == null) return Settings.DEFAULT;
        requireKeys(object, Set.of("scanIntervalSeconds", "visualRange", "crosshairInspection", "verboseLogs"));
        int interval = object.has("scanIntervalSeconds") ? integer(object, "scanIntervalSeconds") : Settings.DEFAULT.scanIntervalSeconds();
        int range = object.has("visualRange") ? integer(object, "visualRange") : Settings.DEFAULT.visualRange();
        boolean crosshair = object.has("crosshairInspection") ? bool(object, "crosshairInspection") : Settings.DEFAULT.crosshairInspection();
        boolean verbose = object.has("verboseLogs") ? bool(object, "verboseLogs") : Settings.DEFAULT.verboseLogs();
        return new Settings(interval, range, crosshair, verbose);
    }

    /** Converts a valid v1 source to editable v2; caller supplies saved legacy visual settings by target ID. */
    public static RuleProfile migrateV1(String json, Map<String, RuleCategory> overrides) {
        RuleProfile legacy = parse(json);
        if (!legacy.diagnostics.isEmpty()) return legacy;
        if (legacy.sourceVersion != 1) return unavailable("Expected version 1 migration source");
        try {
            return parse(migrateDocument(JsonParser.parseString(json).getAsJsonObject(), Map.copyOf(overrides)).toString());
        } catch (RuntimeException error) { return unavailable("Invalid migration: " + error.getMessage()); }
    }

    private static JsonObject migrateDocument(JsonObject source, Map<String, RuleCategory> overrides) {
        if (!TARGETS.containsAll(overrides.keySet())) throw new IllegalArgumentException("Unknown legacy override");
        JsonObject root = new JsonObject();
        root.addProperty("version", 2);
        JsonObject settings = new JsonObject();
        settings.addProperty("scanIntervalSeconds", Settings.DEFAULT.scanIntervalSeconds());
        settings.addProperty("visualRange", Settings.DEFAULT.visualRange());
        settings.addProperty("crosshairInspection", Settings.DEFAULT.crosshairInspection());
        settings.addProperty("verboseLogs", Settings.DEFAULT.verboseLogs());
        root.add("settings", settings);
        JsonArray categories = new JsonArray();
        String[] targets = {"CHEST", "POKE_BALL", "WEAPON", "GUARDIAN", "STRONG_POKEMON"};
        int[][] colors = {{255,190,55,220},{255,85,85,230},{90,220,255,230},{210,110,255,225},{120,255,145,225}};
        for (int i = 0; i < targets.length; i++) {
            String id = targets[i];
            RuleCategory c = overrides.getOrDefault(id, new RuleCategory(id, id, true,
                Arrays.stream(colors[i]).boxed().toList(), i < 3, false, true, i < 3, 1024));
            if (!c.id().equals(id)) throw new IllegalArgumentException("Override ID mismatch: " + id);
            JsonObject object = new JsonObject();
            object.addProperty("id", c.id()); object.addProperty("name", c.name()); object.addProperty("enabled", c.enabled());
            JsonArray color = new JsonArray(); c.color().forEach(color::add); object.add("color", color);
            object.addProperty("boxes", c.boxes()); object.addProperty("tracers", c.tracers());
            object.addProperty("nametags", c.nametags()); object.addProperty("arrows", c.arrows());
            object.addProperty("maxDistance", c.maxDistance()); categories.add(object);
        }
        root.add("categories", categories);
        JsonArray entries = source.getAsJsonArray("rules");
        if (entries == null || entries.size() > 128) throw new IllegalArgumentException("Expected at most 128 rules");
        JsonArray rules = new JsonArray();
        for (JsonElement entry : entries) {
            JsonObject r = entry.getAsJsonObject().deepCopy();
            requireKeys(r, Set.of("id", "target", "label", "all"));
            String target = text(r, "target", 32);
            if (!TARGETS.contains(target)) throw new IllegalArgumentException("Unknown target: " + target);
            r.remove("target"); r.addProperty("categoryId", target);
            r.addProperty("enabled", true); r.addProperty("priority", 0); rules.add(r);
        }
        root.add("rules", rules);
        return root;
    }

    private record Rule(String id, String target, boolean enabled, int priority, Script label, List<Predicate> predicates) {}
    private record Predicate(String field, String operator, Object expected) {
        Truth test(Map<String, Object> facts) {
            Object actual = facts.get(field);
            if (actual == null) return Truth.UNKNOWN;
            boolean match = switch (operator) {
                case "equals" -> actual instanceof Number a && expected instanceof Number b
                    ? Double.compare(a.doubleValue(), b.doubleValue()) == 0 : actual.equals(expected);
                case "min" -> actual instanceof Number a && a.doubleValue() >= ((Number) expected).doubleValue();
                case "max" -> actual instanceof Number a && a.doubleValue() <= ((Number) expected).doubleValue();
                case "suffix" -> actual instanceof String s && s.endsWith((String) expected);
                case "contains" -> actual instanceof String s && s.contains((String) expected);
                case "token" -> actual instanceof String s && Arrays.asList(s.split(",", -1)).contains(expected);
                default -> false;
            };
            return match ? Truth.MATCH : Truth.NO_MATCH;
        }
    }

    /** Replacing a profile clears old rules even when its replacement is unavailable. */
    public static final class State {
        private RuleProfile current = disabled();
        public RuleProfile current() { return current; }
        public void reload(String json) { current = parse(json); }
        void replace(RuleProfile profile) { current = profile; }
        public void fail(String reason) { current = unavailable(reason); }
        public void disable() { current = disabled(); }
    }
}
