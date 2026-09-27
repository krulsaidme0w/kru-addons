package dev.krul.cobblemon;

import dev.krul.cobblemon.gui.overlay.OverlayModule;
import dev.krul.cobblemon.pcorganizer.*;
import meteordevelopment.meteorclient.settings.*;

/** Explicit PC sorting overlay. UI Utils remains a generic container utility. */
public final class PcOrganizer extends OverlayModule {
    public enum Scope { CURRENT_BOX, ALL_UNPROTECTED }
    public enum Mode { COLLECTION, MANUAL_SORT }
    public final Setting<Mode> mode;
    public final Setting<Scope> scope;
    public final Setting<String> protectedBoxes, species, type;
    public final Setting<PcOrganizerSortKey> primary, secondary, tertiary;

    public PcOrganizer() {
        super("pc-organizer", "Preview and explicitly apply deterministic sorting to unprotected PC boxes.",
            new PcOrganizerContent(null), -1, 110);
        ((PcOrganizerContent) content).attach(this);
        var behavior = settings.createGroup("Organizer");
        mode = behavior.add(new EnumSetting.Builder<Mode>().name("layout-mode")
            .description("Collection creates named groups across PC, auto-fills Shinies/Special and allocates boxes. Manual sort uses filters and scope.")
            .defaultValue(Mode.COLLECTION).build());
        scope = behavior.add(new EnumSetting.Builder<Scope>().name("scope").description("Sort only the current box or reorder named zones and sort all ordinary boxes.").defaultValue(Scope.ALL_UNPROTECTED).build());
        protectedBoxes = behavior.add(new StringSetting.Builder().name("protected-boxes").description("Comma-separated 1-based box numbers. These boxes are never touched.").defaultValue("").build());
        species = behavior.add(new StringSetting.Builder().name("species").description("Optional species filter; combined with all other filters.").defaultValue("").build());
        type = behavior.add(new StringSetting.Builder().name("type").description("Optional type filter; combined with all other filters.").defaultValue("").build());
        var sorting = settings.createGroup("Sort");
        primary = sorting.add(new EnumSetting.Builder<PcOrganizerSortKey>().name("primary").defaultValue(PcOrganizerSortKey.TYPE).build());
        secondary = sorting.add(new EnumSetting.Builder<PcOrganizerSortKey>().name("secondary").defaultValue(PcOrganizerSortKey.SPECIES).build());
        tertiary = sorting.add(new EnumSetting.Builder<PcOrganizerSortKey>().name("tertiary").defaultValue(PcOrganizerSortKey.ORIGINAL_SLOT).build());
    }
}
