package dev.krul.cobblemon.pcorganizer;

import dev.krul.cobblemon.pcui.PcReflection;
import dev.krul.cobblemon.pokemon.CobblemonPokemonData;
import dev.krul.cobblemon.pokemon.CobblemonPokemonReader;
import java.util.*;

/** Read-only ABI boundary for the live Cobblemon PC; unavailable fields stay blank. */
public final class PcOrganizerLiveReader {
    private static final String PC_GUI = "com.cobblemon.mod.common.client.gui.pc.PCGUI";
    private String reason = "Open a Cobblemon PC.";
    public String reason() { return reason; }
    public Optional<PcOrganizerSnapshot> read(Object screen) {
        if (screen == null || !PC_GUI.equals(screen.getClass().getName())) return Optional.empty();
        try {
        Object pc = PcReflection.get(screen, "getPc");
        Object manager = Class.forName("com.cobblemon.mod.common.client.CobblemonClient").getField("INSTANCE").get(null);
        Object storage = PcReflection.get(manager, "getStorage");
        Object stores = PcReflection.get(storage, "getPcStores");
        if (!(stores instanceof Map<?, ?> map) || map.get(uuid(pc)) != pc) return unavailable("Waiting for the current PC to synchronize.");
        Object config = PcReflection.get(screen, "getConfiguration");
        var select = PcReflection.read(config, "getSelectOverride");
        if (!select.available() || select.value() != null) return unavailable("Selection-only PC: organizing is unavailable.");
        Object selected = PcReflection.get(PcReflection.get(screen, "getStorage"), "getBox");
        if (!(selected instanceof Integer box)) return unavailable("Current PC box is unavailable.");
        return readPc(pc, box);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            return unavailable("PC reader unavailable: " + failure.getClass().getSimpleName());
        }
    }
    Optional<PcOrganizerSnapshot> readPc(Object pc, int currentBox) {
        Object rawBoxes = PcReflection.get(pc, "getBoxes");
        if (!(rawBoxes instanceof List<?> boxes) || boxes.isEmpty()) return unavailable("Waiting for PC boxes.");
        UUID store = uuid(pc);
        if (store == null) return unavailable("PC store UUID is unavailable.");
        List<PcOrganizerPokemon> result = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (int box = 0; box < boxes.size(); box++) {
            Object rawBox = boxes.get(box);
            names.add(text(PcReflection.get(rawBox, "getName"), "Box " + (box + 1)));
            Object rawSlots = PcReflection.get(rawBox, "getSlots");
            if (!(rawSlots instanceof List<?> slots) || slots.size() != 30) return unavailable("Incomplete PC slots in box " + (box + 1));
            for (int slot = 0; slot < slots.size(); slot++) {
                Object pokemon = slots.get(slot);
                if (pokemon == null) continue;
                UUID id = uuid(pokemon);
                if (id == null) return unavailable("Pokemon UUID unavailable in box " + (box + 1) + ", slot " + (slot + 1));
                CobblemonPokemonData data = CobblemonPokemonReader.readPokemon(pokemon).orElse(null);
                if (data == null) return unavailable("Pokemon data unavailable in box " + (box + 1) + ", slot " + (slot + 1));
                result.add(toPokemon(id, box, slot, data, pokemon));
            }
        }
        reason = "Ready";
        return Optional.of(new PcOrganizerSnapshot(store, boxes.size(), names, result, currentBox));
    }
    private Optional<PcOrganizerSnapshot> unavailable(String message) { reason = message; return Optional.empty(); }
    static String intrinsicClass(Object pokemon) {
        Object labels = PcReflection.get(PcReflection.get(pokemon, "getSpecies"), "getLabels");
        if (!(labels instanceof Iterable<?> values)) return "";
        var classes = new TreeSet<String>();
        for (Object label : values) {
            if (!(label instanceof String value)) return "";
            dev.krul.cobblemon.species.SpeciesClass.fromLabel(value)
                .filter(dev.krul.cobblemon.species.SpeciesClass::special)
                .ifPresent(kind -> classes.add(kind.name().toLowerCase(Locale.ROOT)));
        }
        return classes.isEmpty() ? "ordinary" : String.join(",", classes);
    }
    private static PcOrganizerPokemon toPokemon(UUID id, int box, int slot, CobblemonPokemonData d, Object pokemon) {
        Map<String, String> x = d.details();
        return new PcOrganizerPokemon(id, box, slot, d.species(), typeNames(pokemon), x.getOrDefault("form", ""),
            Boolean.TRUE.equals(d.shiny()), d.radiant() != null && d.radiant(), d.shiny() == null ? "" : intrinsicClass(pokemon), x.getOrDefault("ability", ""),
            x.getOrDefault("held_item", ""), number(x.get("level")), total(x.get("ivs")), perfect(x.get("ivs")));
    }
    static String typeNames(Object pokemon) {
        List<String> types = new ArrayList<>();
        Object raw = PcReflection.get(pokemon, "getTypes");
        if (raw instanceof Iterable<?> values) for (Object type : values) {
            Object name = PcReflection.get(type, "getName");
            types.add(name instanceof String s ? s.toLowerCase(Locale.ROOT) : "");
        }
        return String.join("/", types);
    }
    private static int total(String value) {
        if (value == null) return 0;
        int sum = 0;
        for (String part : value.split(",")) { int equals = part.indexOf('='); if (equals >= 0) sum += number(part.substring(equals + 1)); }
        return sum;
    }
    private static int number(String value) { try { return value == null ? 0 : Integer.parseInt(value); } catch (NumberFormatException ignored) { return 0; } }
    private static int perfect(String value) {
        if (value == null) return 0;
        int count = 0; for (String part : value.split(",")) if (part.endsWith("=31")) count++;
        return count;
    }
    private static UUID uuid(Object value) { Object id = PcReflection.get(value, "getUuid"); return id instanceof UUID u ? u : null; }
    static String text(Object value, String fallback) {
        String name = value instanceof net.minecraft.text.Text text ? text.getString() : value instanceof String s ? s : "";
        return name.isBlank() ? fallback : name;
    }
}
