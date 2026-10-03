package dev.krul.builder.mcp;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.*;

/**
 * Cheap, deterministic architectural sanity check run before a plan reaches the
 * Minecraft hologram. It is intentionally heuristic: it rejects flat, random
 * block dumps without pretending to replace a human visual review.
 */
public final class ArchitectureReview {
    private ArchitectureReview() {}

    public record Result(int score, boolean publishable, List<String> passed, List<String> warnings) {
        public Result { passed = List.copyOf(passed); warnings = List.copyOf(warnings); }
        public String json() {
            JsonObject root = new JsonObject();
            root.addProperty("score", score); root.addProperty("publishable", publishable);
            JsonArray good = new JsonArray(); passed.forEach(good::add); root.add("passed", good);
            JsonArray issues = new JsonArray(); warnings.forEach(issues::add); root.add("warnings", issues);
            return root.toString();
        }
    }

    public static Result review(JsonObject plan) {
        Objects.requireNonNull(plan);
        List<JsonObject> operations = new ArrayList<>();
        Set<String> paletteIds = new HashSet<>();
        var palette = plan.getAsJsonObject("palette");
        if (palette != null) for (var entry : palette.entrySet()) {
            if (entry.getValue().isJsonObject() && entry.getValue().getAsJsonObject().has("id"))
                paletteIds.add(entry.getValue().getAsJsonObject().get("id").getAsString());
        }
        var stages = plan.getAsJsonArray("stages");
        if (stages != null) for (var stage : stages) {
            if (!stage.isJsonObject()) continue;
            var ops = stage.getAsJsonObject().getAsJsonArray("operations");
            if (ops != null) for (var value : ops) if (value.isJsonObject()) operations.add(value.getAsJsonObject());
        }
        List<String> passed = new ArrayList<>(), warnings = new ArrayList<>();
        int score = 0;
        if (stages != null && stages.size() >= 3) { score += 15; passed.add("staged composition"); }
        else warnings.add("Use at least three phases: massing, details, furnishing/lighting.");
        if (operations.size() >= 40) { score += 15; passed.add("substantial composition"); }
        else warnings.add("The plan is too small to establish a readable architectural composition.");

        Set<String> ids = new HashSet<>(paletteIds);
        boolean wood = ids.stream().anyMatch(ArchitectureReview::wood);
        boolean stone = ids.stream().anyMatch(ArchitectureReview::stone);
        boolean glass = ids.stream().anyMatch(id -> id.contains("glass"));
        boolean accent = ids.stream().anyMatch(id -> id.contains("lantern") || id.contains("copper") || id.contains("leaf") || id.contains("plant"));
        int materialRoles = (wood ? 1 : 0) + (stone ? 1 : 0) + (glass ? 1 : 0) + (accent ? 1 : 0);
        if (materialRoles >= 3) { score += 20; passed.add("palette has structural contrast"); }
        else warnings.add("Palette needs distinct wood, stone, glass and/or accent roles; avoid one-color builds.");
        if (paletteIds.size() >= 3 && paletteIds.size() <= 10) { score += 10; passed.add("restrained palette"); }
        else warnings.add("Keep the palette between 3 and 10 verified blocks.");

        Set<String> furniture = new HashSet<>(), accents = new HashSet<>();
        for (JsonObject op : operations) {
            String target = op.has("target") ? op.get("target").getAsString() : "";
            if (target.toLowerCase(Locale.ROOT).matches(".*(chair|table|bench|sofa|desk|drawer|cabinet|shelf|bed|furniture).*")) furniture.add(target);
            if (target.toLowerCase(Locale.ROOT).matches(".*(lantern|lamp|plant|flower|trapdoor|stair|slab|fence|shutter).*")) accents.add(target);
        }
        if (furniture.size() >= 2) { score += 15; passed.add("furniture vocabulary is present"); }
        else warnings.add("Add at least two furniture types and arrange them into functional zones.");
        if (!accents.isEmpty()) { score += 10; passed.add("small-scale details are present"); }
        else warnings.add("Add trim, lighting, shutters, stairs or plants to create depth and rhythm.");

        if (hasSpatialDepth(operations)) { score += 15; passed.add("multi-layer silhouette"); }
        else warnings.add("The plan is too flat; add setbacks, overhangs, a roof profile and a terrace transition.");
        if (hasRhythm(operations)) { score += 10; passed.add("repeated architectural rhythm"); }
        else warnings.add("The composition has no clear repeated rhythm; mirror bays, beams or window groups around an axis.");
        boolean publishable = score >= 70 && warnings.size() <= 2;
        return new Result(Math.min(100, score), publishable, passed, warnings);
    }

    private static boolean hasSpatialDepth(List<JsonObject> operations) {
        Set<Integer> xs = new HashSet<>(), ys = new HashSet<>(), zs = new HashSet<>();
        for (JsonObject op : operations) {
            var p = op.getAsJsonObject("position"); if (p == null) continue;
            xs.add(p.get("x").getAsInt()); ys.add(p.get("y").getAsInt()); zs.add(p.get("z").getAsInt());
        }
        return xs.size() >= 4 && zs.size() >= 4 && ys.size() >= 3;
    }
    private static boolean hasRhythm(List<JsonObject> operations) {
        Map<Integer, Integer> byX = new HashMap<>(), byZ = new HashMap<>();
        for (JsonObject op : operations) {
            var p = op.getAsJsonObject("position"); if (p == null) continue;
            byX.merge(p.get("x").getAsInt(), 1, Integer::sum);
            byZ.merge(p.get("z").getAsInt(), 1, Integer::sum);
        }
        long repeatedX = byX.values().stream().filter(count -> count >= 3).count();
        long repeatedZ = byZ.values().stream().filter(count -> count >= 3).count();
        return repeatedX >= 2 || repeatedZ >= 2;
    }
    private static boolean wood(String id) { return id.contains("wood") || id.contains("log") || id.contains("plank") || id.contains("pine") || id.contains("spruce") || id.contains("oak"); }
    private static boolean stone(String id) { return id.contains("stone") || id.contains("deepslate") || id.contains("brick") || id.contains("concrete"); }
}
