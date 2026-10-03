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
    private static Object invoke(Object receiver, String method) throws ReflectiveOperationException {
        return receiver.getClass().getMethod(method).invoke(receiver);
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
