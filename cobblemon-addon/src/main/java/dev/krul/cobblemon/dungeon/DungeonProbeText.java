package dev.krul.cobblemon.dungeon;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;

/** Stable one-line log records for offline dungeon evidence parsing. */
public final class DungeonProbeText {
    private DungeonProbeText() {}

    public static String session(String dimension) {
        return "DUNGEON_SESSION dimension=" + value(dimension);
    }

    public static String dimension(String dimension) {
        return "DUNGEON_DIMENSION dimension=" + value(dimension);
    }

    public static String scan(DungeonWorldProbe.ProbeResult result, int pending) {
        Map<DungeonObservation.Kind, Integer> counts = new java.util.EnumMap<>(DungeonObservation.Kind.class);
        result.observations().forEach(observation -> counts.merge(observation.kind(), 1, Integer::sum));
        return scan(result, new DungeonObservationIndex.Summary(result.dimension(), result.observations().size(), pending, 0, counts));
    }

    public static String scan(DungeonWorldProbe.ProbeResult result, DungeonObservationIndex.Summary summary) {
        return "DUNGEON_SCAN dimension=" + value(result.dimension())
            + " loadedChunks=" + result.loadedChunks()
            + " chunks=" + summary.count(DungeonObservation.Kind.CHUNK)
            + " blockEntities=" + summary.count(DungeonObservation.Kind.BLOCK_ENTITY)
            + " entities=" + summary.count(DungeonObservation.Kind.ENTITY)
            + " pokemon=" + summary.count(DungeonObservation.Kind.POKEMON)
            + " crosshair=" + summary.count(DungeonObservation.Kind.CROSSHAIR)
            + " complete=" + result.complete() + " pending=" + summary.pendingChanges();
    }

    public static String observation(DungeonObservation observation) {
        String marker = observation.kind() == DungeonObservation.Kind.CROSSHAIR
            ? "DUNGEON_CROSSHAIR" : "DUNGEON_OBSERVATION";
        StringJoiner facts = new StringJoiner(",", "[", "]");
        observation.facts().entrySet().stream().sorted(Map.Entry.comparingByKey())
            .forEach(entry -> facts.add(value(entry.getKey()) + ":" + value(entry.getValue())));
        return marker + " kind=" + observation.kind().name().toLowerCase(Locale.ROOT)
            + " dimension=" + value(observation.dimension())
            + " identity=" + value(observation.identity())
            + " registry=" + value(observation.registryId())
            + " class=" + value(observation.className())
            + " name=" + value(observation.name())
            + " position=" + value(observation.position() == null ? "unknown" : observation.position().toShortString())
            + " facts=" + facts;
    }

    public static String encode(String value) {
        if (value == null || value.isEmpty()) return "";
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        StringBuilder encoded = new StringBuilder(bytes.length);
        for (byte byteValue : bytes) {
            int unsigned = byteValue & 0xFF;
            if ((unsigned >= 'A' && unsigned <= 'Z') || (unsigned >= 'a' && unsigned <= 'z')
                || (unsigned >= '0' && unsigned <= '9') || unsigned == '-' || unsigned == '.'
                || unsigned == '_' || unsigned == '~') encoded.append((char) unsigned);
            else encoded.append('%').append(String.format(Locale.ROOT, "%02X", unsigned));
        }
        return encoded.toString();
    }

    public static String decode(String encoded) {
        if (encoded == null || encoded.isEmpty()) return "";
        byte[] bytes = new byte[encoded.length()];
        int count = 0;
        for (int i = 0; i < encoded.length(); i++) {
            char value = encoded.charAt(i);
            if (value != '%') {
                if (value > 0x7F) throw new IllegalArgumentException("non-ASCII unescaped log value");
                bytes[count++] = (byte) value;
                continue;
            }
            if (i + 2 >= encoded.length()) throw new IllegalArgumentException("truncated percent escape");
            int high = Character.digit(encoded.charAt(++i), 16);
            int low = Character.digit(encoded.charAt(++i), 16);
            if (high < 0 || low < 0) throw new IllegalArgumentException("invalid percent escape");
            bytes[count++] = (byte) ((high << 4) | low);
        }
        return new String(bytes, 0, count, StandardCharsets.UTF_8);
    }

    static String value(String value) {
        return encode(value);
    }
}

