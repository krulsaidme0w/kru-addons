package dev.krul.cobblemon.pcorganizer;

/** Executes the frozen preview: role layout, Pokemon positions, then generated names. */
public final class PcOrganizerRun {
    public record Tick(boolean finished, boolean blocked, String message) {}
    private final PcOrganizerPreparation preview;
    private final PcOrganizerLayoutCoordinator layout;
    private final PcOrganizerCoordinator sorting;
    private final PcOrganizerLayoutCoordinator naming;
    private boolean started, layoutDone, sortingStarted, sortingDone, namingStarted;
    private Tick terminal;
    public PcOrganizerRun(PcOrganizerPreparation preview, PcOrganizerCoordinator.MutationPort moves,
                          PcOrganizerLayoutCoordinator.MutationPort layout) {
        this.preview = preview;
        this.layout = new PcOrganizerLayoutCoordinator(layout, preview.layout());
        sorting = new PcOrganizerCoordinator(preview.sortSettings(), moves, preview.afterSort());
        naming = new PcOrganizerLayoutCoordinator(layout, new PcOrganizerBoxMovePlanner.Plan(
            preview.layout().sourceOrder(), java.util.List.of(), preview.renames()));
    }
    public Tick tick(PcOrganizerSnapshot snapshot, long tick) {
        if (terminal != null) return terminal;
        if (!started) {
            if (!PcOrganizerPreparation.sameStorage(preview.before(), snapshot)) return finish(true, "PC changed; preview again");
            started = true;
        }
        if (!layoutDone) {
            var result = layout.tick(snapshot, tick);
            if (result.status() == PcOrganizerLayoutCoordinator.Status.BLOCKED) return finish(true, result.reason());
            if (result.status() == PcOrganizerLayoutCoordinator.Status.COMPLETE) {
                if (!PcOrganizerPreparation.sameStorage(preview.afterLayout(), snapshot)) return finish(true, "Box order differs from preview");
                layoutDone = true;
            }
            return new Tick(false, false, "1/3 Box order: " + result.reason());
        }
        if (!sortingStarted) {
            if (!PcOrganizerPreparation.sameStorage(preview.afterLayout(), snapshot)) return finish(true, "PC changed before sorting; preview again");
            sortingStarted = true;
        }
        if (!sortingDone) {
            var result = sorting.tick(snapshot, tick);
            if (result.status() == PcOrganizerCoordinator.Status.BLOCKED) return finish(true, result.reason());
            if (result.status() == PcOrganizerCoordinator.Status.COMPLETE) {
                if (!PcOrganizerPreparation.sameStorage(preview.afterSort(), snapshot)) return finish(true, "Positions differ from preview");
                sortingDone = true;
            }
            return new Tick(false, false, "2/3 Pokemon sort: " + result.reason());
        }
        if (!namingStarted) {
            if (!PcOrganizerPreparation.sameStorage(preview.afterSort(), snapshot)) return finish(true, "PC changed before naming; preview again");
            namingStarted = true;
        }
        var result = naming.tick(snapshot, tick);
        if (result.status() == PcOrganizerLayoutCoordinator.Status.BLOCKED) return finish(true, result.reason());
        if (result.status() == PcOrganizerLayoutCoordinator.Status.COMPLETE) {
            if (!PcOrganizerPreparation.sameStorage(preview.result(), snapshot)) return finish(true, "Result differs from preview; stopped");
            return finish(false, "Done: PC matches preview");
        }
        return new Tick(false, false, "3/3 Box names: " + result.reason());
    }
    private Tick finish(boolean blocked, String message) { return terminal = new Tick(true, blocked, message); }
}
