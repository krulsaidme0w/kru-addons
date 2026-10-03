package dev.krul.cobblemon.hover;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.Slot;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Read-only reader for the exact server-authored GTS listing below the pointer. */
public final class GtsHoverReader {
    private static final String DELTA_GTS_SCREEN = "com.symstudios.deltaclient.gui.gts.GtsScreen";
    private static final Pattern LEVEL = Pattern.compile("\\bLv\\.\\s*(\\d+)");
    private static final Pattern STAT = Pattern.compile("(?i)^(โ|เ|แ|ใ|ไ|ๅ|HP|Atk|Attack|Def|Defense|SpA|SpAtk|Sp\\.? Atk|SpD|SpDef|Sp\\.? Def|Spe|Speed)\\s*:?\\s*(\\d+)$");
    private static final List<String> STAT_NAMES = List.of("HP", "Atk", "Def", "SpA", "SpD", "Spe");

    public Optional<Hovered> readAt(HandledScreen<?> screen, int mouseX, int mouseY) {
        if (screen == null || !isGtsTitle(screen.getTitle().getString())) return Optional.empty();
        try {
            Method getSlotAt = method(screen.getClass(), "getSlotAt", 2);
            Object value = getSlotAt.invoke(screen, (double) mouseX, (double) mouseY);
            if (!(value instanceof Slot slot) || MinecraftClient.getInstance().player == null
                || slot.inventory == MinecraftClient.getInstance().player.getInventory()) return Optional.empty();
            ItemStack stack = slot.getStack();
            if (stack.isEmpty()) return Optional.empty();
            List<String> lines = Screen.getTooltipFromItem(MinecraftClient.getInstance(), stack)
                .stream().map(text -> text.getString()).toList();
            int x = field(screen, "x"), y = field(screen, "y");
            Object component = pokemonItemComponent(stack);
            if (component == null) return Optional.empty();
            String species = String.valueOf(invoke(component, "getSpecies"));
            Object rawAspects = invoke(component, "getAspects");
            List<String> aspects = rawAspects instanceof Iterable<?> values
                ? java.util.stream.StreamSupport.stream(values.spliterator(), false).filter(String.class::isInstance).map(String.class::cast).toList()
                : List.of();
            return fromListing(Registries.ITEM.getId(stack.getItem()).toString(), stack.getName().getString(), species, aspects, lines).map(details -> new Hovered(details,
                new HoverCardLayout.Rect(x + slot.x, y + slot.y, 16, 16)));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) { return Optional.empty(); }
    }

    /** DeltaClient GTS is a custom Screen, not a HandledScreen; read its immutable listing packet. */
    public Optional<Hovered> readDeltaAt(Screen screen, int mouseX, int mouseY) {
        if (screen == null || !DELTA_GTS_SCREEN.equals(screen.getClass().getName())) return Optional.empty();
        try {
            Object state = fieldValue(screen, "state");
            Object listings = invoke(state, "getListings");
            if (!(listings instanceof List<?> values)) return Optional.empty();
            double scale = ((Number) invoke(screen, "canvasScale")).doubleValue();
            double originX = ((Number) invoke(screen, "screenOriginX", new Class<?>[]{float.class}, (float) scale)).doubleValue();
            double originY = ((Number) invoke(screen, "screenOriginY", new Class<?>[]{float.class}, (float) scale)).doubleValue();
            double canvasMouseX = (mouseX - originX) / scale, canvasMouseY = (mouseY - originY) / scale;
            for (int i = 0; i < Math.min(values.size(), 30); i++) {
                Object rect = invoke(screen, "cardRect", new Class<?>[]{int.class}, i);
                double left = number(rect, "getLeft"), top = number(rect, "getTop");
                double width = number(rect, "getWidth"), height = number(rect, "getHeight");
                if (canvasMouseX < left || canvasMouseY < top || canvasMouseX >= left + width || canvasMouseY >= top + height) continue;
                Object pokemon = invoke(values.get(i), "getPokemon");
                PokemonHoverDetails details = fromDeltaPokemon(pokemon);
                if (details == null) return Optional.empty();
                return Optional.of(new Hovered(details, new HoverCardLayout.Rect(originX + left * scale,
                    originY + top * scale, width * scale, height * scale)));
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) { }
        return Optional.empty();
    }

    public static PokemonHoverDetails fromDeltaPokemon(Object pokemon) {
        if (pokemon == null) return null;
        try {
            String speciesId = String.valueOf(invoke(pokemon, "getSpeciesId"));
            if (!speciesId.startsWith("cobblemon:")) return null;
            String display = string(pokemon, "getName");
            if (display.isBlank()) display = title(speciesId.substring(speciesId.indexOf(':') + 1));
            Object aspectsValue = invoke(pokemon, "getAspects");
            List<String> aspects = aspectsValue instanceof Iterable<?> items ?
                java.util.stream.StreamSupport.stream(items.spliterator(), false).map(String::valueOf).toList() : List.of();
            List<String> types = new ArrayList<>();
            String primary = string(pokemon, "getPrimaryType"), secondary = string(pokemon, "getSecondaryType");
            if (!primary.isBlank()) types.add(primary);
            if (!secondary.isBlank()) types.add(secondary);
            Object rawIvs = invoke(pokemon, "getIvs");
            List<Integer> ivs = rawIvs instanceof List<?> list && list.size() == 6 && list.stream().allMatch(Number.class::isInstance)
                ? list.stream().map(Number.class::cast).map(Number::intValue).toList() : List.of();
            List<String> moves = strings(invoke(pokemon, "getMoves"));
            String form = aspects.stream().filter(a -> !a.equalsIgnoreCase("shiny") && !a.contains(":"))
                .findFirst().orElse("");
            Object size = invoke(pokemon, "getSize");
            return new PokemonHoverDetails(display, title(speciesId.substring(speciesId.indexOf(':') + 1)), form,
                integer(pokemon, "getLevel"), string(pokemon, "getGender"), aspects.stream().anyMatch(a -> a.equalsIgnoreCase("shiny")),
                types, string(pokemon, "getTeraType"), string(pokemon, "getAbility"), string(pokemon, "getNature"),
                string(pokemon, "getHeldItem"), string(pokemon, "getCaughtBall"), size instanceof Number n ? String.format(Locale.ROOT, "%.2fx", n.doubleValue()) : "",
                string(pokemon, "getSkin"), ivs, List.of(), moves);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) { return null; }
    }

    private static List<String> strings(Object value) { return value instanceof Iterable<?> values ? java.util.stream.StreamSupport.stream(values.spliterator(), false).map(String::valueOf).limit(4).toList() : List.of(); }
    private static String string(Object value, String method) throws ReflectiveOperationException { Object result = invoke(value, method); return result == null ? "" : String.valueOf(result); }
    private static int integer(Object value, String method) throws ReflectiveOperationException { Object result = invoke(value, method); return result instanceof Number n ? n.intValue() : 0; }
    private static double number(Object value, String method) throws ReflectiveOperationException { Object result = invoke(value, method); return result instanceof Number n ? n.doubleValue() : 0; }
    private static Object fieldValue(Object receiver, String name) throws ReflectiveOperationException {
        for (Class<?> c = receiver.getClass(); c != null; c = c.getSuperclass()) try { Field f = c.getDeclaredField(name); f.setAccessible(true); return f.get(receiver); } catch (NoSuchFieldException ignored) { }
        throw new NoSuchFieldException(name);
    }
    private static Object invoke(Object receiver, String name) throws ReflectiveOperationException { return invoke(receiver, name, new Class<?>[0]); }
    private static Object invoke(Object receiver, String name, Class<?>[] types, Object... args) throws ReflectiveOperationException {
        for (Class<?> c = receiver.getClass(); c != null; c = c.getSuperclass()) try { Method m = c.getDeclaredMethod(name, types); m.setAccessible(true); return m.invoke(receiver, args); } catch (NoSuchMethodException ignored) { }
        throw new NoSuchMethodException(name);
    }

    public static boolean isGtsTitle(String raw) {
        String title = raw == null ? "" : raw.toLowerCase(Locale.ROOT);
        return title.contains("global trade") || title.equals("gts") || title.startsWith("gts ");
    }

    public static Optional<PokemonHoverDetails> fromListing(String itemId, String itemName, String speciesId,
                                                             Collection<String> rawAspects, List<String> tooltip) {
        if (!"cobblemon:pokemon_model".equals(itemId) || speciesId == null || !speciesId.startsWith("cobblemon:")) return Optional.empty();
        List<String> lines = tooltip == null ? List.of() : tooltip.stream().filter(Objects::nonNull)
            .map(line -> line.replaceAll("§.", "").strip()).filter(s -> !s.isEmpty()).toList();
        if (lines.isEmpty()) return Optional.empty();
        String aspects = rawAspects == null ? "" : String.join(",", rawAspects).toLowerCase(Locale.ROOT);
        int level = capture(LEVEL, lines.getFirst()).map(Integer::parseInt).orElse(0);
        List<String> types = lines.stream().skip(1).filter(line -> !line.contains(":") && line.contains("/")).findFirst()
            .map(value -> Arrays.stream(value.split("\\s*/\\s*", 2)).toList()).orElse(List.of());
        List<Integer> ivs = sectionStats(lines, "IVs:", 31), evs = sectionStats(lines, "EVs:", 252);
        List<String> moves = moves(lines);
        String speciesName = title(speciesId.substring(speciesId.indexOf(':') + 1));
        String display = lines.getFirst().replaceFirst("^⭐\\s*", "").replaceFirst("\\s*[♀♂]\\s*", " ").strip();
        if (display.isBlank()) display = itemName == null ? "" : itemName.strip();
        display = display.replaceFirst("(?i)\\s*Lv\\.\\s*\\d+.*$", "").strip();
        if (display.isBlank()) display = speciesName;
        String gender = aspects.contains("female") ? "female" : aspects.contains("male") ? "male" : aspects.contains("genderless") ? "genderless" : "";
        return Optional.of(new PokemonHoverDetails(display, speciesName, field(lines, "Form:"), level, gender,
            aspects.contains("shiny") || lines.getFirst().startsWith("⭐"), types, field(lines, "Tera Type:"), field(lines, "Ability:"),
            field(lines, "Nature:"), field(lines, "Held Item:"), field(lines, "Caught Ball:"), field(lines, "Size:"),
            field(lines, "Skin:"), ivs, evs, moves));
    }

    private static List<Integer> sectionStats(List<String> lines, String header, int maximum) {
        int start = -1;
        for (int i = 0; i < lines.size(); i++) if (lines.get(i).regionMatches(true, 0, header, 0, header.length())) { start = i + 1; break; }
        if (start < 0) return List.of();
        Map<String, Integer> found = new HashMap<>();
        for (int i = start; i < lines.size(); i++) {
            String line = lines.get(i).strip();
            if (line.matches("(?i)^(EVs|Moves|Ability|Nature|Held Item|Price|Seller|Tera Type|Form|Skin):.*")) break;
            Matcher matcher = STAT.matcher(line);
            if (!matcher.matches()) { if (line.contains(":")) break; continue; }
            String key = normalize(matcher.group(1));
            int value = Integer.parseInt(matcher.group(2));
            if (value < 0 || value > maximum || found.putIfAbsent(key, value) != null) return List.of();
        }
        List<String> keys = List.of("hp", "atk", "def", "spa", "spd", "spe");
        if (keys.stream().anyMatch(key -> !found.containsKey(key))) return List.of();
        return keys.stream().map(found::get).toList();
    }

    private static String normalize(String value) {
        String v = value.toLowerCase(Locale.ROOT).replaceAll("[\\s.]", "");
        return switch (v) { case "โ" -> "hp"; case "เ" -> "atk"; case "แ" -> "def"; case "ใ" -> "spa"; case "ไ" -> "spd"; case "ๅ" -> "spe"; case "attack" -> "atk"; case "defense" -> "def"; case "spatk" -> "spa"; case "spdef" -> "spd"; case "speed" -> "spe"; default -> v; };
    }
    private static List<String> moves(List<String> lines) {
        for (int i = 0; i < lines.size(); i++) if (lines.get(i).regionMatches(true, 0, "Moves:", 0, 6)) {
            List<String> result = new ArrayList<>();
            String inline = lines.get(i).substring(6).strip();
            addMoves(result, inline);
            for (int j = i + 1; j < lines.size() && !lines.get(j).contains(":"); j++) addMoves(result, lines.get(j));
            return result.stream().limit(4).toList();
        }
        return List.of();
    }
    private static void addMoves(List<String> out, String line) { for (String move : line.split("\\s+-\\s+")) if (!move.isBlank() && !move.equalsIgnoreCase("Empty")) out.add(move.strip()); }
    private static String field(List<String> lines, String prefix) {
        List<String> values = lines.stream().filter(line -> line.regionMatches(true, 0, prefix, 0, prefix.length()))
            .map(line -> line.substring(prefix.length()).strip()).filter(s -> !s.isBlank() && !s.equals("?")).distinct().toList();
        return values.size() == 1 ? values.getFirst() : "";
    }
    private static Optional<String> capture(Pattern pattern, String source) { Matcher m = pattern.matcher(source); return m.find() ? Optional.of(m.group(1)) : Optional.empty(); }
    private static Object pokemonItemComponent(ItemStack stack) throws ReflectiveOperationException {
        Class<?> components = Class.forName("com.cobblemon.mod.common.CobblemonItemComponents");
        Object key = components.getField("POKEMON_ITEM").get(null);
        for (Method method : stack.getClass().getMethods()) if (method.getName().equals("get") && method.getParameterCount() == 1
            && method.getParameterTypes()[0].isInstance(key)) {
            try { return method.invoke(stack, key); } catch (IllegalArgumentException ignored) { }
        }
        throw new NoSuchMethodException("ItemStack.get(POKEMON_ITEM)");
    }
    private static String title(String id) { String[] words = id.replace('-', ' ').replace('_', ' ').split("\\s+"); StringJoiner j = new StringJoiner(" "); for (String w : words) if (!w.isBlank()) j.add(Character.toUpperCase(w.charAt(0)) + w.substring(1)); return j.toString(); }
    private static Method method(Class<?> type, String name, int count) throws NoSuchMethodException {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) for (Method m : c.getDeclaredMethods()) if (m.getName().equals(name) && m.getParameterCount() == count) { m.setAccessible(true); return m; }
        throw new NoSuchMethodException(name);
    }
    private static int field(Object receiver, String name) throws ReflectiveOperationException {
        for (Class<?> c = receiver.getClass(); c != null; c = c.getSuperclass()) try { Field f = c.getDeclaredField(name); f.setAccessible(true); return ((Number) f.get(receiver)).intValue(); } catch (NoSuchFieldException ignored) { }
        throw new NoSuchFieldException(name);
    }
    public record Hovered(PokemonHoverDetails details, HoverCardLayout.Rect bounds) {}
}
