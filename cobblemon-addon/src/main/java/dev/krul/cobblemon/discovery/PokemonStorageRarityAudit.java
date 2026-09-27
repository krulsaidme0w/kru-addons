package dev.krul.cobblemon.discovery;

import dev.krul.cobblemon.pokemon.CobblemonPokemonData;
import dev.krul.cobblemon.pokemon.CobblemonPokemonReader;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Read-only party/PC rarity audit owned by Pokemon Discovery. */
public final class PokemonStorageRarityAudit {
    private PokemonStorageRarityAudit() {}

    public record Entry(String location, CobblemonPokemonData pokemon,
                        DiscoveryRarityEvidence.Resolution resolution) {}

    public record Report(List<Entry> entries, int unreadable, String unavailableReason) {
        public Report {
            entries = List.copyOf(entries);
            unavailableReason = unavailableReason == null ? "" : unavailableReason;
        }
        public boolean available() { return unavailableReason.isBlank(); }
    }

    public static List<Entry> resolve(List<CobblemonPokemonData> pokemon,
                                      DiscoveryRarityEvidence evidence, DiscoveryRarityIndex local) {
        if (pokemon == null) return List.of();
        DiscoveryRarityEvidence safeEvidence = evidence == null ? new DiscoveryRarityEvidence() : evidence;
        List<Entry> result = new ArrayList<>();
        for (CobblemonPokemonData data : pokemon) {
            if (data != null) result.add(new Entry("storage", data, safeEvidence.resolve(data.species(), local)));
        }
        return List.copyOf(result);
    }

    public static Report readLive(DiscoveryRarityEvidence evidence, DiscoveryRarityIndex local) {
        int unreadable = 0;
        try {
            Object client = singleton("com.cobblemon.mod.common.client.CobblemonClient");
            Object storage = invoke(client, "getStorage");
            Object party = invoke(storage, "getParty");
            Object rawParty = invoke(party, "getSlots");
            if (!(rawParty instanceof List<?> partySlots)) return unavailable("Party slots unavailable");

            List<Located> located = new ArrayList<>();
            unreadable += collect(located, "party", partySlots);
            Object rawStores = invoke(storage, "getPcStores");
            if (!(rawStores instanceof Map<?, ?> stores) || stores.isEmpty()) return unavailable("PC stores unavailable");
            for (Object pc : stores.values()) {
                Object rawBoxes = invoke(pc, "getBoxes");
                if (!(rawBoxes instanceof List<?> boxes)) return unavailable("PC boxes unavailable");
                for (int box = 0; box < boxes.size(); box++) {
                    Object rawSlots = invoke(boxes.get(box), "getSlots");
                    if (!(rawSlots instanceof List<?> slots)) return unavailable("PC box slots unavailable");
                    unreadable += collect(located, "box[" + box + "]", slots);
                }
            }

            List<CobblemonPokemonData> data = located.stream().map(Located::data).toList();
            List<Entry> resolved = resolve(data, evidence, local);
            List<Entry> result = new ArrayList<>();
            for (int i = 0; i < resolved.size(); i++) {
                Entry entry = resolved.get(i);
                result.add(new Entry(located.get(i).location(), entry.pokemon(), entry.resolution()));
            }
            return new Report(result, unreadable, "");
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            return new Report(List.of(), unreadable, "Storage reader unavailable: " + error.getClass().getSimpleName());
        }
    }

    private static Report unavailable(String reason) { return new Report(List.of(), 0, reason); }
    private record Located(String location, CobblemonPokemonData data) {}

    private static int collect(List<Located> result, String location, List<?> slots) {
        int unreadable = 0;
        for (int slot = 0; slot < slots.size(); slot++) {
            Object raw = slots.get(slot);
            if (raw == null) continue;
            var decoded = CobblemonPokemonReader.readPokemon(raw);
            if (decoded.isPresent()) result.add(new Located(location + " slot " + slot, decoded.get()));
            else unreadable++;
        }
        return unreadable;
    }

    public static String format(Report report) {
        if (report == null) return "Pokemon storage rarity audit unavailable";
        StringBuilder out = new StringBuilder("Pokemon storage rarity audit\n");
        if (!report.available()) out.append("Status: ").append(report.unavailableReason()).append('\n');
        for (Entry entry : report.entries()) {
            var data = entry.pokemon();
            var resolution = entry.resolution();
            out.append(entry.location()).append(" | ").append(data.species())
                .append(" | form=").append(data.details().getOrDefault("form", "unknown"))
                .append(" | aspects=").append(data.aspects())
                .append(" | rarity=").append(resolution.rarity())
                .append(" | source=").append(resolution.source())
                .append(" | server=").append(resolution.serverBuckets())
                .append(" | local=").append(resolution.localBuckets()).append('\n');
        }
        return out.append("Readable: ").append(report.entries().size())
            .append(" | unreadable: ").append(report.unreadable()).toString();
    }

    private static Object singleton(String name) throws ReflectiveOperationException {
        return Class.forName(name).getField("INSTANCE").get(null);
    }

    private static Object invoke(Object receiver, String name) throws ReflectiveOperationException {
        if (receiver == null) return null;
        Method method = receiver.getClass().getMethod(name);
        return method.invoke(receiver);
    }
}
