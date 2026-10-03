package dev.krul.cobblemon.pcorganizer;

import dev.krul.cobblemon.PcOrganizer;
import dev.krul.cobblemon.gui.overlay.OverlayCanvas;
import dev.krul.cobblemon.gui.overlay.OverlayContent;
import net.minecraft.client.MinecraftClient;
import java.util.List;
import static dev.krul.cobblemon.pcorganizer.PcOrganizerButtons.*;

/** Small control surface kept separate from UI Utils; PC mutation is deliberately explicit. */
public class PcOrganizerContent implements OverlayContent {
    private PcOrganizer owner;
    private String status = "Open a Cobblemon PC to preview its layout.";
    private final PcOrganizerLiveReader reader = new PcOrganizerLiveReader();
    private PcOrganizerSnapshot snapshot;
    private PcOrganizerRun run;
    private Object pcScreen;
    private long ticks;
    private PcOrganizerPreparation preparation;
    private PcOrganizerSnapshot previewSnapshot;
    private PcOrganizerSettings previewSettings;
    private java.util.Set<Integer> previewLocks = java.util.Set.of();
    private PcOrganizer.Scope previewScope;
    private PcOrganizer.Mode previewMode;
    private List<PcOrganizerGroups.Group> groups = List.of();
    private String selectedGroup = "";
    public PcOrganizerContent(PcOrganizer owner) { this.owner = owner; }
    public void attach(PcOrganizer owner) { this.owner = owner; }
    @Override public String title() { return "PC Organizer"; }
    @Override public int width() { return 330; }
    @Override public int height() { return PcOrganizerGroups.detailsY(groups.size()) + selectedBoxes().size() * 14 + 12; }
    @Override public boolean contextual() { return true; }
    @Override public Object contextKey() {
        Object screen = MinecraftClient.getInstance().currentScreen;
        return screen != null && screen.getClass().getName().equals("com.cobblemon.mod.common.client.gui.pc.PCGUI") ? screen : null;
    }
    @Override public void tick() {
        Object current = contextKey();
        if (pcScreen != current) { reset(); pcScreen = current; }
        ticks++;
        snapshot = reader.read(MinecraftClient.getInstance().currentScreen).orElse(null);
        if (snapshot == null) {
            run = null; previewSnapshot = null; preparation = null;
            status = reader.reason();
        }
        if (snapshot != null && run != null) {
            var tick = run.tick(snapshot, ticks);
            status = (tick.blocked() ? "Stopped: " : "") + tick.message();
            if (tick.finished()) { run = null; preparation = null; previewSnapshot = null; }
        }
        refreshGroups();
    }
    @Override public void render(OverlayCanvas c, double mx, double my) {
        boolean pc = contextKey() != null;
        c.text(snapshot != null ? snapshot.pokemon().size() + " Pokemon | " + snapshot.boxCount() + " boxes" : "Waiting for PC data", 8, 8, pc ? c.accent() : c.muted());
        c.text(collection() ? "Collection: whole PC" : "Manual sort: " + scopeLabel(), 8, 26, c.text());
        c.textFit(collection() ? "SHINIES / SPECIAL: automatic routing + capacity" : "Named zones stay together; ordinary boxes are sorted", 8, 44, width() - 16, c.text());
        c.textFit(collection() ? "TRADE / BREEDING: sorted in-zone; never reclassified" : "Filters: AND | species=" + blank(owner.species.get()) + " | type=" + blank(owner.type.get()), 8, 62, width() - 16, c.muted());
        c.textFit(collection() ? "Other Pokemon: primary type > species > form > IV" : "Sort: " + owner.primary.get() + " > " + owner.secondary.get() + " > " + owner.tertiary.get(), 8, 80, width() - 16, c.muted());
        if (snapshot != null) c.textFit("Current: " + (snapshot.currentBox() + 1) + " " + snapshot.boxNames().get(snapshot.currentBox()), 8, 96, width() - 16, c.accent());
        button(c, "Preview", PREVIEW, snapshot != null && !running(), mx, my);
        button(c, "Apply preview", APPLY, previewSnapshot != null && !running(), mx, my);
        button(c, running() ? "Stop" : "Back to PC", STOP, running() || preparation != null, mx, my);
        button(c, "Current lock: " + (currentLocked() ? "ON" : "OFF"), LOCK, snapshot != null && !running(), mx, my);
        button(c, "Mode: " + (collection() ? "Collection" : "Manual"), SCOPE, !running(), mx, my);
        if (!collection()) button(c, "Sort: " + owner.primary.get(), SORT, !running(), mx, my);
        else c.text("BREEDING: box 6", 177, 183, c.muted());
        c.textFit(status, 8, 212, width() - 16, c.muted());
        for (int i = 0; i < groups.size(); i++) {
            var group = groups.get(i);
            int x = 8 + (i % 3) * 106, y = 238 + (i / 3) * 24;
            c.button(group.label() + " " + group.count(), x, y, 102, 21, PcOrganizerGroups.at(mx, my, groups.size()) == i);
            if (group.label().equals(selectedGroup)) c.outline(x, y, 102, 21, c.accent());
        }
        var shown = shownSnapshot();
        var boxes = selectedBoxes();
        for (int i = 0; shown != null && i < boxes.size(); i++) {
            int box = boxes.get(i);
            long count = shown.pokemon().stream().filter(p -> p.box() == box).count();
            String change = "";
            if (preparation != null) {
                int source = preparation.layout().sourceOrder().get(box);
                change = (source == box ? "" : " <- " + (source + 1))
                    + (preparation.sortSettings().boxPolicy().isProtected(box) ? " | kept" : "");
            }
            c.textFit((preparation == null ? "" : "Preview: ") + (box + 1) + " " + shown.boxNames().get(box) + change + " | " + count + "/30",
                8, PcOrganizerGroups.detailsY(groups.size()) + i * 14, width() - 16, c.text());
        }
    }
    private static String blank(String s) { return s.isBlank() ? "any" : s; }
    private void button(OverlayCanvas c, String label, PcOrganizerButtons button, boolean enabled, double mx, double my) {
        if (enabled) c.button(label, button.x, button.y, WIDTH, HEIGHT, PcOrganizerButtons.at(mx, my) == button);
        else { c.fill(button.x, button.y, WIDTH, HEIGHT, c.border()); c.textFit(label, button.x + 6, button.y + 6, WIDTH - 12, c.muted()); }
    }
    @Override public boolean click(double x, double y, int button) {
        if (button != 0 || contextKey() == null || x < 8 || x >= 322) return true;
        int groupIndex = PcOrganizerGroups.at(x, y, groups.size());
        if (groupIndex >= 0 && !running() && snapshot != null) {
            var group = groups.get(groupIndex); selectedGroup = group.label();
            if (preparation == null) navigate(group.nextBox(snapshot.currentBox()));
            return true;
        }
        var action = PcOrganizerButtons.at(x, y);
        if (action == null) return true;
        if (action == STOP && (running() || preparation != null)) {
            boolean wasRunning = running();
            run = null; previewSnapshot = null; preparation = null; refreshGroups();
            status = wasRunning ? "Stopped: partial changes remain; a sent move may finish." : "Live PC: click a group to open its boxes";
            return true;
        }
        if (running() || snapshot == null) return true;
        try {
            if (action == SCOPE || action == SORT) {
                if (action == SCOPE) owner.mode.set(collection() ? PcOrganizer.Mode.MANUAL_SORT : PcOrganizer.Mode.COLLECTION);
                else if (!collection()) { var keys = PcOrganizerSortKey.values(); owner.primary.set(keys[(owner.primary.get().ordinal() + 1) % keys.length]); }
                else return true;
                previewSnapshot = null; preparation = null; refreshGroups();
                status = "Settings changed; click Preview";
            } else if (action == LOCK) {
                var locked = new java.util.HashSet<>(protectedBoxes());
                if (!locked.remove(snapshot.currentBox())) locked.add(snapshot.currentBox());
                owner.protectedBoxes.set(locked.stream().sorted().map(i -> Integer.toString(i + 1)).collect(java.util.stream.Collectors.joining(",")));
                previewSnapshot = null; preparation = null; refreshGroups();
                status = "Locked boxes: " + blank(owner.protectedBoxes.get()) + "; named zones also stay together";
            } else if (action == PREVIEW) {
                previewSnapshot = null; preparation = null;
                previewSettings = settings(PcOrganizerRunMode.APPLY);
                previewLocks = java.util.Set.copyOf(protectedBoxes()); previewScope = owner.scope.get();
                previewMode = owner.mode.get();
                preparation = collection() ? PcOrganizerPreparation.prepareCollection(snapshot, previewLocks)
                    : PcOrganizerPreparation.prepare(snapshot, previewSettings, previewScope == PcOrganizer.Scope.ALL_UNPROTECTED, previewLocks);
                status = preparation.layout().moves().size() + " box moves | " + preparation.sort().moves().size()
                    + " placements | " + (preparation.layout().renames().size() + preparation.renames().size()) + " names (preview)";
                previewSnapshot = snapshot;
                refreshGroups();
            } else if (action == APPLY && previewSnapshot != null) {
                var fresh = reader.read(contextKey()).orElse(null);
                if (!PcOrganizerPreparation.sameStorage(previewSnapshot, fresh) || !previewSettings.equals(settings(PcOrganizerRunMode.APPLY))
                    || !previewLocks.equals(protectedBoxes()) || previewScope != owner.scope.get() || previewMode != owner.mode.get()) throw new IllegalStateException("PC/settings changed. Preview again.");
                var sender = new PcOrganizerMutationSender();
                run = new PcOrganizerRun(preparation, sender, sender);
                previewSnapshot = null;
            }
        } catch (RuntimeException failure) { previewSnapshot = null; preparation = null; refreshGroups(); status = "Blocked: " + failure.getMessage(); }
        return true;
    }
    private boolean running() { return run != null; }
    private boolean collection() { return owner.mode.get() == PcOrganizer.Mode.COLLECTION; }
    private PcOrganizerSnapshot shownSnapshot() { return preparation == null ? snapshot : preparation.result(); }
    private void refreshGroups() {
        groups = PcOrganizerGroups.from(shownSnapshot());
        if (groups.stream().noneMatch(g -> g.label().equals(selectedGroup))) selectedGroup = groups.isEmpty() ? "" : groups.getFirst().label();
    }
    private List<Integer> selectedBoxes() {
        return groups.stream().filter(g -> g.label().equals(selectedGroup)).findFirst().map(PcOrganizerGroups.Group::boxes).orElse(List.of());
    }
    private void navigate(int box) {
        try {
            var live = reader.read(contextKey()).orElseThrow(() -> new IllegalStateException(reader.reason()));
            if (box < 0 || box >= live.boxCount()) throw new IllegalArgumentException("Box unavailable");
            Object storage = dev.krul.cobblemon.pcui.PcReflection.get(contextKey(), "getStorage");
            storage.getClass().getMethod("setBox", int.class).invoke(storage, box);
            if (!Integer.valueOf(box).equals(dev.krul.cobblemon.pcui.PcReflection.get(storage, "getBox"))) throw new IllegalStateException("Box switch not confirmed");
            status = "Box " + (box + 1) + "; click the group again for its next box";
        } catch (ReflectiveOperationException | RuntimeException failure) { status = "Cannot switch box: " + failure.getMessage(); }
    }
    private String scopeLabel() { return owner.scope.get() == PcOrganizer.Scope.CURRENT_BOX ? "Current box" : "All ordinary boxes"; }
    private boolean currentLocked() {
        return snapshot != null && java.util.Arrays.stream(owner.protectedBoxes.get().split(","))
            .anyMatch(token -> token.trim().equals(Integer.toString(snapshot.currentBox() + 1)));
    }
    private PcOrganizerSettings settings(PcOrganizerRunMode mode) {
        return new PcOrganizerSettings(mode, new PcOrganizerFilter(owner.species.get(), owner.type.get(), "", false, false, "", "", "", ""),
            owner.primary.get(), owner.secondary.get(), owner.tertiary.get(), PcOrganizerBoxPolicy.forSnapshot(snapshot, protectedBoxes(),
                owner.scope.get() == PcOrganizer.Scope.CURRENT_BOX ? snapshot.currentBox() : null));
    }
    private java.util.Set<Integer> protectedBoxes() {
        java.util.Set<Integer> result = new java.util.HashSet<>();
        for (String token : owner.protectedBoxes.get().split(",")) if (!token.isBlank()) result.add(Integer.parseInt(token.trim()) - 1);
        return result;
    }
    @Override public List<String> tooltip(double x, double y) {
        if (y >= 212 && y < 228) return List.of(status);
        if (y >= 120 && y < 141) return List.of("Preview creates no changes. Apply uses exactly this plan.",
            "Collection: automatic SHINIES, SPECIAL and primary-type boxes",
            "Shiny wins over Special; TRADE/BREEDING sort internally without cross-zone moves",
            "MAIN/UTILITY remain untouched; explicitly locked boxes are not changed",
            "Overflow gets additional boxes after box 6; insufficient space blocks before moving",
            "Keep PC open until done; stopping can leave a partial layout");
        if (y >= 148 && y < 169 && x >= 169) return List.of("Toggle a fixed lock on the currently viewed box",
            "Locked box numbers: " + blank(owner.protectedBoxes.get()), "A locked box is not moved, sorted or used as temporary storage");
        if (PcOrganizerButtons.at(x, y) == SCOPE) return List.of("Collection organizes the entire PC and generates box names",
            "Manual uses module filters and scope; it does not create dynamic zones");
        int index = PcOrganizerGroups.at(x, y, groups.size());
        if (index >= 0) {
            var group = groups.get(index);
            return List.of(group.label() + ": " + group.count() + " Pokemon",
                "Boxes: " + group.boxes().stream().map(b -> Integer.toString(b + 1)).collect(java.util.stream.Collectors.joining(", ")),
                preparation == null ? "Click to open; click again for next box" : "Planned layout only; click to inspect its boxes below");
        }
        int top = PcOrganizerGroups.detailsY(groups.size());
        int row = (int) ((y - top) / 14);
        var boxes = selectedBoxes();
        var shown = shownSnapshot();
        if (shown != null && y >= top && row < boxes.size()) {
            int box = boxes.get(row);
            var details = new java.util.ArrayList<String>();
            details.add((preparation == null ? "" : "Preview: ") + "Box " + (box + 1) + " " + shown.boxNames().get(box));
            shown.pokemon().stream().filter(p -> p.box() == box).forEach(p -> details.add("Slot " + (p.slot() + 1) + ": " + p.species() + " (" + p.type() + ")"));
            return List.copyOf(details);
        }
        return List.of();
    }
    @Override public void reset() { status = "Open a Cobblemon PC to preview its layout."; snapshot = null; run = null; pcScreen = null; previewSnapshot = null; previewSettings = null; preparation = null; groups = List.of(); selectedGroup = ""; ticks = 0; }
}
