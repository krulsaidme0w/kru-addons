package dev.krul.cobblemon.pcorganizer;

import java.util.Objects;

public record PcOrganizerSettings(PcOrganizerRunMode runMode, PcOrganizerFilter filter,
                                  PcOrganizerSortKey primary, PcOrganizerSortKey secondary,
                                  PcOrganizerSortKey tertiary, PcOrganizerBoxPolicy boxPolicy) {
    public PcOrganizerSettings {
        Objects.requireNonNull(runMode, "runMode");
        Objects.requireNonNull(filter, "filter");
        Objects.requireNonNull(primary, "primary");
        Objects.requireNonNull(secondary, "secondary");
        Objects.requireNonNull(tertiary, "tertiary");
        Objects.requireNonNull(boxPolicy, "boxPolicy");
    }

    public static PcOrganizerSettings defaults(int boxCount) {
        return new PcOrganizerSettings(PcOrganizerRunMode.PREVIEW, PcOrganizerFilter.empty(),
            PcOrganizerSortKey.SPECIES, PcOrganizerSortKey.IV_TOTAL, PcOrganizerSortKey.ORIGINAL_SLOT,
            new PcOrganizerBoxPolicy(boxCount, java.util.Set.of(), java.util.Map.of()));
    }
}
