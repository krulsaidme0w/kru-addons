package dev.krul.cobblemon.pcui.daycare;

import java.util.*;
import static dev.krul.cobblemon.pcui.daycare.DaycarePort.*;
import static dev.krul.cobblemon.pcui.daycare.DaycareReflection.get;

final class DaycareStorageReader {
    static Storage read(Object manager) {
        try {
            Object party = get(manager, "getParty");
            Object rawParty = get(party, "getSlots"), rawStores = get(manager, "getPcStores");
            if (!(rawParty instanceof List<?> partySlots) || partySlots.size() != 6
                || !(rawStores instanceof Map<?, ?> stores) || stores.size() != 1) return null;
            Object pc = stores.values().iterator().next();
            Object rawBoxes = get(pc, "getBoxes");
            if (!(rawBoxes instanceof List<?> boxes) || boxes.isEmpty()) return null;
            var partyPokemon = decode(partySlots);
            var pcPokemon = new ArrayList<Pokemon>();
            boolean capacity = partyPokemon.size() < partySlots.size();
            for (Object box : boxes) {
                Object raw = get(box, "getSlots");
                if (!(raw instanceof List<?> slots) || slots.size() != 30) return null;
                var decoded = decode(slots);
                capacity |= decoded.size() < slots.size();
                pcPokemon.addAll(decoded);
            }
            var snapshot = new Storage(partyPokemon, pcPokemon, capacity, party, pc);
            return snapshot.all() == null ? null : snapshot;
        } catch (RuntimeException | LinkageError unavailable) { return null; }
    }

    private static List<Pokemon> decode(List<?> slots) {
        var result = new ArrayList<Pokemon>();
        for (Object pokemon : slots) {
            if (pokemon == null) continue;
            Object id = get(pokemon, "getUuid"), level = get(pokemon, "getLevel");
            Object species = get(get(pokemon, "getSpecies"), "getResourceIdentifier");
            Object rawAspects = get(pokemon, "getAspects");
            if (!(id instanceof UUID uuid) || !(level instanceof Number n) || species == null
                || !(rawAspects instanceof Set<?> aspects)) throw new IllegalStateException("Incomplete Pokemon");
            var strings = new HashSet<String>();
            for (Object aspect : aspects) {
                if (!(aspect instanceof String s)) throw new IllegalStateException("Invalid aspect");
                strings.add(s);
            }
            String name = species.toString();
            if (name.startsWith("cobblemon:")) name = name.substring(10);
            if (name.isBlank()) throw new IllegalStateException("Missing species");
            result.add(new Pokemon(uuid, name, strings, n.intValue()));
        }
        return List.copyOf(result);
    }
}
