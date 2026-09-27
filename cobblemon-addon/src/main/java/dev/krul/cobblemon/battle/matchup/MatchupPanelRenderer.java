package dev.krul.cobblemon.battle.matchup;

import java.util.*;
import dev.krul.cobblemon.battle.*;
import dev.krul.cobblemon.gui.CobblemonPanelGeometry;
import dev.krul.cobblemon.gui.CobblemonUiStyle;
import static dev.krul.cobblemon.battle.matchup.MatchupPanelLayout.Rect;
import static dev.krul.cobblemon.battle.matchup.MatchupSnapshot.Fighter;

/** Native-style icon matrix with bounded hover content and a local revealed-move card. */
public final class MatchupPanelRenderer {
    private static final int BACKGROUND = CobblemonUiStyle.PANEL_BACKGROUND_SOLID;
    private static final int BORDER = CobblemonUiStyle.PANEL_BORDER;
    private static final int FOREGROUND = CobblemonUiStyle.TEXT_PRIMARY;
    private static final int MUTED = CobblemonUiStyle.TEXT_MUTED;
    private static final int GOOD = CobblemonUiStyle.POSITIVE;
    private static final int BAD = CobblemonUiStyle.NEGATIVE;
    private static final int WARNING = CobblemonUiStyle.WARNING;
    public interface Canvas {
        int width(String text);
        void fill(Rect rect, int color);
        void border(Rect rect, int color);
        void text(String text, int x, int y, int color);
        void clip(Rect rect);
        void unclip();
        default void beginOverlay() {}
        default void endOverlay() {}
        default boolean portrait(Rect rect, Fighter fighter) { return false; }
        default boolean type(Rect rect, PokemonType type) { return false; }
        default boolean tera(Rect rect, Fighter fighter) { return false; }
    }
    private record Chip(String text, PokemonType type) {}
    private record Line(String text, PokemonType type, int color, List<Chip> chips, int indent) {
        Line(String text) { this(text, null, FOREGROUND); }
        Line(String text, PokemonType type, int color) { this(text, type, color, List.of()); }
        Line(String text, PokemonType type, int color, List<Chip> chips) { this(text, type, color, chips, 0); }
    }
    public record CardControls(Rect box, Rect close, Rect previous, Rect next, int page, int pages) {
        private static final Rect EMPTY_RECT = new Rect(0, 0, 0, 0);
        public static final CardControls EMPTY = new CardControls(EMPTY_RECT, EMPTY_RECT, EMPTY_RECT, EMPTY_RECT, 0, 0);
    }

    public static void render(Canvas canvas, MatchupPanelLayout layout, MatchupSnapshot snapshot,
                              List<MatchupCalculator.Cell> cells, boolean matrix, int mouseX, int mouseY) {
        render(canvas, layout, snapshot, cells, matrix, null, 0, mouseX, mouseY);
    }
    public static CardControls render(Canvas canvas, MatchupPanelLayout layout, MatchupSnapshot snapshot,
                                      List<MatchupCalculator.Cell> cells, boolean matrix, String selectedFighter,
                                      int page, int mouseX, int mouseY) {
        button(canvas, layout.historyTab(), "History", !matrix, mouseX, mouseY);
        button(canvas, layout.matchupTab(), "Matchups", matrix, mouseX, mouseY);
        if (!matrix) return CardControls.EMPTY;
        Rect body = layout.body();
        canvas.fill(layout.backdrop(), BACKGROUND);
        List<Line> hover = List.of();
        canvas.clip(body);
        try {
            if (body.height() < 12) return CardControls.EMPTY;
            if (snapshot.own().isEmpty() || snapshot.enemies().isEmpty()) {
                text(canvas, "Waiting for battle teams", body.x() + 2, body.y() + 2, body.width() - 4, FOREGROUND);
                return CardControls.EMPTY;
            }
            if (layout.visibleColumns() == 0 || layout.visibleRows() == 0) {
                text(canvas, "Enlarge log", body.x() + 2, body.y() + 2, body.width() - 4, FOREGROUND);
                if (body.height() >= 24) text(canvas, "Drag lower-right corner", body.x() + 2, body.y() + 13, body.width() - 4, MUTED);
                return CardControls.EMPTY;
            }
            int[] hit = layout.hit(mouseX, mouseY);
            for (int c = 0; c < layout.visibleColumns(); c++) {
                Rect r = layout.ownHeader(c);
                header(canvas, r, snapshot.own().get(c), true);
                if (r.contains(mouseX, mouseY)) hover = fighterHover(snapshot.own().get(c), true);
            }
            for (int r = 0; r < layout.visibleRows(); r++) {
                Fighter enemy = snapshot.enemies().get(r);
                Rect portrait = layout.enemyHeader(r);
                header(canvas, portrait, enemy, false);
                if (portrait.contains(mouseX, mouseY)) hover = fighterHover(enemy, false);
                for (int c = 0; c < layout.visibleColumns(); c++) {
                    var cell = cells.get(r * snapshot.own().size() + c);
                    Rect rect = layout.cell(c, r);
                    boolean hovered = hit != null && hit[0] == c && hit[1] == r;
                    boolean cross = hit != null && (hit[0] == c || hit[1] == r);
                    canvas.fill(new Rect(rect.x() + 1, rect.y() + 1, rect.width() - 2, rect.height() - 2),
                        hovered ? 0xFF414141 : cross ? 0xFF303030 : 0xFF242424);
                    int y = rect.y() + (rect.height() - 9) / 2;
                    if (cell.unavailable()) {
                        canvas.fill(new Rect(rect.x() + 6, y + 4, rect.width() - 12, 1), BORDER);
                    } else {
                        indicator(canvas, new Rect(rect.x() + 3, y, 9, 9), cell.outgoing().multiplier(), false, false);
                        indicator(canvas, new Rect(rect.x() + rect.width() - 12, y, 9, 9), cell.incoming().multiplier(), true, false);
                        potentialMarker(canvas, rect, cell);
                    }
                    if (hovered) {
                        canvas.border(rect, FOREGROUND);
                        hover = pairHover(snapshot.own().get(c), enemy, cell);
                    }
                }
            }
            int footer = layout.cell(0, layout.visibleRows() - 1).y() + layout.cellWidth() + 2;
            String scope = scopeNote(snapshot);
            if (!scope.isEmpty()) {
                text(canvas, scope, body.x() + 2, footer, body.width() - 4, WARNING);
                if (new Rect(body.x(), footer, body.width(), 10).contains(mouseX, mouseY))
                    hover = List.of(new Line(scope), new Line("Only available battle data is shown"), new Line("Multi battle: per-target types, not switch legality"));
            }
        } finally { canvas.unclip(); }
        Fighter selected = java.util.stream.Stream.concat(snapshot.enemies().stream(), snapshot.own().stream())
            .filter(f -> f.id().equals(selectedFighter)).findFirst().orElse(null);
        if (selected != null) return moveCard(canvas, layout, selected, snapshot.own().contains(selected), page, mouseX, mouseY);
        if (!hover.isEmpty()) tooltip(canvas, layout, hover, mouseX, mouseY);
        return CardControls.EMPTY;
    }
    private static void header(Canvas canvas, Rect rect, Fighter fighter, boolean mine) {
        Rect box = new Rect(rect.x() + 1, rect.y() + 1, rect.width() - 2, rect.height() - 3);
        canvas.fill(box, fighter.active() ? mine ? 0xFF34494F : 0xFF4F3535 : 0xFF2C2C2C);
        if (!canvas.portrait(new Rect(box.x() + 1, box.y() + 1, box.width() - 2, box.height() - 3), fighter))
            text(canvas, "?", box.x() + (box.width() - canvas.width("?")) / 2, box.y() + 5, box.width(), MUTED);
        if (fighter.teraActive()) {
            Rect badge = new Rect(box.x() + box.width() - 12, box.y() + 1, 10, 10);
            if (!canvas.tera(badge, fighter)) {
                int color = fighter.teraType() == null ? WARNING : fighter.teraType().argb();
                canvas.fill(badge, color);
                canvas.border(badge, FOREGROUND);
                text(canvas, "T", badge.x() + 2, badge.y() + 1, badge.width() - 2, BACKGROUND);
            }
        }
        if (fighter.active()) canvas.border(box, mine ? 0xFFA0CBD7 : 0xFFE5AAA3);
        if (fighter.fainted()) {
            canvas.fill(new Rect(box.x(), box.y() + box.height() / 2, box.width(), 2), BAD);
        } else if (fighter.hpPercent() >= 0) {
            canvas.fill(new Rect(box.x(), rect.y() + rect.height() - 2, box.width(), 1), BORDER);
            canvas.fill(new Rect(box.x(), rect.y() + rect.height() - 2, (int) Math.round(box.width() * fighter.hpPercent() / 100), 1),
                fighter.hpPercent() <= 25 ? BAD : GOOD);
        }
    }
    private static void indicator(Canvas canvas, Rect r, Double value, boolean incoming, boolean potential) {
        int color = color(value, incoming);
        if (value == null) {
            text(canvas, "?", r.x() + 1, r.y(), r.width(), MUTED);
            return;
        }
        if (potential) canvas.border(r, color);
        else canvas.fill(r, value == 1 ? 0xFF454545 : color);
        int ink = potential || value == 1 ? color : BACKGROUND;
        String glyph = MatchupIndicator.label(value, incoming);
        text(canvas, glyph, r.x() + (r.width() - canvas.width(glyph)) / 2, r.y(), r.width(), ink);
    }

    private static void potentialMarker(Canvas canvas, Rect cell, MatchupCalculator.Cell matchup) {
        Double value = matchup.potential().multiplier();
        if (value == null || value == 1) return;
        int markerColor = color(value, true);
        int width = Math.min(18, Math.max(12, cell.width() - 6));
        Rect marker = new Rect(cell.x() + (cell.width() - width) / 2, cell.y() + cell.height() - 11, width, 9);
        if (MatchupCalculator.potentialDominates(matchup)) canvas.fill(marker, markerColor);
        else canvas.border(marker, markerColor);
        PokemonType type = matchup.potential().type();
        if (type != null) canvas.type(new Rect(marker.x() + 1, marker.y(), 8, 8), type);
        String glyph = MatchupIndicator.label(value, true);
        text(canvas, glyph, marker.x() + marker.width() - canvas.width(glyph) - 2, marker.y(), canvas.width(glyph) + 2,
            MatchupCalculator.potentialDominates(matchup) ? BACKGROUND : markerColor);
    }
    private static List<Line> pairHover(Fighter mine, Fighter enemy, MatchupCalculator.Cell cell) {
        List<Line> lines = new ArrayList<>();
        lines.add(new Line(mine.name() + " vs " + enemy.name()));
        if (cell.unavailable()) return List.of(lines.getFirst(), new Line("Fainted Pokemon: unavailable pair"));
        if (mine.teraActive()) lines.add(new Line("Your Tera: " + teraLabel(mine) + " | current type used", mine.teraType(), WARNING));
        if (enemy.teraActive()) lines.add(new Line("Enemy Tera: " + teraLabel(enemy) + " | risk uses current type", enemy.teraType(), WARNING));
        lines.add(new Line("Hit: " + attackLabel(cell.outgoing()),
            moveType(mine, cell.outgoing()), color(cell.outgoing().multiplier(), false)));
        var potential = cell.potential();
        if (MatchupCalculator.potentialDominates(cell)) {
            lines.add(new Line("Risk: " + potential.type().label() + " " + MatchupCalculator.label(potential.multiplier()) + " | potential",
                potential.type(), color(potential.multiplier(), true)));
            if (cell.incoming().multiplier() != null) lines.add(new Line("Shown: " + cell.incoming().move() + " " + MatchupCalculator.label(cell.incoming().multiplier()), moveType(enemy, cell.incoming()), MUTED));
            lines.add(new Line("Type threat; that move is not confirmed", null, MUTED));
        } else {
            lines.add(new Line("Risk: " + attackLabel(cell.incoming())
                + (cell.incoming().multiplier() == null ? " | not known" : " | shown"),
                moveType(enemy, cell.incoming()), color(cell.incoming().multiplier(), true)));
        }
        lines.add(new Line("Lv." + mine.level() + " / Lv." + enemy.level() + " | HP " + health(mine) + " / " + health(enemy)));
        for (String line : cell.details()) if (line.startsWith("Careful:")) lines.add(new Line(line, null, WARNING));
        String immune = immunityNote(cell.outgoing()) + immunityNote(cell.incoming());
        if (!immune.isBlank()) lines.add(new Line(immune.strip(), null, MUTED));
        lines.add(new Line("Type only; not damage / safe switch", null, MUTED));
        return List.copyOf(lines);
    }
    private static String attackLabel(MatchupCalculator.Attack attack) {
        if (attack.multiplier() != null) return attack.move() + " " + MatchupCalculator.label(attack.multiplier());
        return attack.reason().contains("types unknown") ? "Target types unknown" : "No usable attack known";
    }
    private static String teraLabel(Fighter fighter) { return fighter.teraType() == null ? "unknown" : fighter.teraType().label(); }
    private static String immunityNote(MatchupCalculator.Attack attack) {
        return attack.reason().startsWith("Known current ") ? attack.reason().split("\\.")[0] + " " : "";
    }
    private static PokemonType moveType(Fighter fighter, MatchupCalculator.Attack attack) {
        return fighter.moves().stream().filter(m -> m.data().name().equals(attack.move())).map(m -> m.data().type()).filter(Objects::nonNull).findFirst().orElse(null);
    }
    private static List<Line> fighterHover(Fighter fighter, boolean mine) {
        List<Line> lines = fighterDetails(fighter, mine || hasRevealedIdentity(fighter));
        lines.add(new Line(mine ? "Type chart only; click for full details" : "Type chart only; click for revealed moves", null, MUTED));
        return lines;
    }
    private static List<Line> fighterDetails(Fighter fighter) {
        return fighterDetails(fighter, true);
    }
    private static List<Line> fighterDetails(Fighter fighter, boolean allowPotential) {
        List<Line> lines = new ArrayList<>();
        lines.add(new Line(fighter.name() + " Lv." + fighter.level() + " | HP " + health(fighter)));
        MatchupTypes.Resolved resolved = allowPotential ? MatchupTypes.resolve(fighter)
            : new MatchupTypes.Resolved(fighter.types(), false);
        if (resolved.types().isEmpty()) lines.add(new Line("Current types unknown", null, WARNING));
        else {
            int typeColor = resolved.potential() ? WARNING : FOREGROUND;
            String heading = resolved.potential() ? "Base types · potential" : "Current types";
            lines.add(new Line(heading, null, typeColor, resolved.types().stream()
                .map(type -> new Chip(type.label(), type)).toList()));
            chart(lines, resolved.types(), "Weak", BAD, 4, 2);
            chart(lines, resolved.types(), "Resists", GOOD, .25, .5);
            chart(lines, resolved.types(), "Immune", GOOD, 0);
        }
        if (fighter.teraActive()) lines.add(new Line("Tera: " + (fighter.teraType() == null ? "unknown" : fighter.teraType().label())
            + " (active)", fighter.teraType(), WARNING));
        else if (fighter.teraType() != null) lines.add(new Line("Tera type: " + fighter.teraType().label() + " (not active)", fighter.teraType(), MUTED));
        if (!fighter.ability().isBlank()) lines.add(new Line("Ability: " + fighter.ability() + (fighter.currentEffects() ? "" : " (reported)")));
        if (!fighter.status().isBlank()) lines.add(new Line("Status: " + fighter.status(), null, WARNING));
        if (!fighter.active()) lines.add(new Line("Bench: last reported HP / types", null, MUTED));
        return lines;
    }
    private static void chart(List<Line> lines, List<PokemonType> types, String label, int color, double... values) {
        List<Chip> parts = new ArrayList<>();
        for (double value : values) {
            for (PokemonType type : PokemonType.values()) if (TypeEffectiveness.multiplier(type, types.toArray(PokemonType[]::new)) == value)
                parts.add(new Chip(type.label() + " " + MatchupCalculator.label(value), type));
        }
        if (!parts.isEmpty()) lines.add(new Line(label, null, color, parts));
    }
    private static String health(Fighter fighter) { return fighter.fainted() ? "FNT" : fighter.hpPercent() < 0 ? "?" : (fighter.active() ? "" : "~") + Math.round(fighter.hpPercent()) + "%"; }
    private static List<Line> revealedDetails(Fighter fighter) {
        List<Line> source = new ArrayList<>();
        source.add(new Line(fighter.name() + " Lv." + fighter.level() + " | HP " + health(fighter)));
        if (fighter.moves().isEmpty()) source.add(new Line("No moves revealed yet", null, MUTED));
        for (MatchupSnapshot.Move move : fighter.moves()) {
            BattleMove data = move.data();
            source.add(new Line(data.name(), data.type(), FOREGROUND));
            String category = switch (data.category()) {
                case PHYSICAL -> "Physical";
                case SPECIAL -> "Special";
                case STATUS -> "Status";
                case UNKNOWN -> "Unknown category";
            };
            String power = data.category() == MoveCategory.STATUS ? "No direct damage"
                : "Power " + (move.special() || data.power() <= 1 ? "special / ?" : data.power());
            source.add(new Line((data.type() == null ? "?" : data.type().label()) + " | " + category
                + " | " + power + " | PP " + (move.pp() < 0 ? "?" : move.pp())
                + (move.disabled() ? " | disabled" : ""), null, MUTED));
        }
        source.add(new Line("Revealed only; not the full enemy moveset", null, MUTED));
        source.add(new Line("Type chart", null, MUTED));
        // A clicked card is also the no-wheel fallback for a clipped hover on tiny screens.
        List<Line> details = fighterDetails(fighter, hasRevealedIdentity(fighter));
        source.addAll(details.subList(1, details.size()));
        return source;
    }
    private static boolean hasRevealedIdentity(Fighter fighter) {
        return !fighter.portrait().speciesId().isBlank();
    }
    private static CardControls moveCard(Canvas canvas, MatchupPanelLayout layout, Fighter fighter, boolean mine, int requestedPage, int mouseX, int mouseY) {
        int width = Math.min(268, layout.viewportWidth() - 8);
        int capacity = Math.min(12, (layout.viewportHeight() - 52) / 12);
        if (width < 100 || capacity < 1) return CardControls.EMPTY;
        List<Line> source = mine ? fighterDetails(fighter) : revealedDetails(fighter);
        List<Line> rows = wrap(canvas, source, width - 10);
        int pages = Math.max(1, (rows.size() + capacity - 1) / capacity);
        int page = Math.clamp(requestedPage, 0, pages - 1);
        List<Line> visible = rows.subList(page * capacity, Math.min(rows.size(), (page + 1) * capacity));
        int height = 28 + visible.size() * 12 + (pages > 1 ? 18 : 0);
        // Prefer a sidecar, falling back inside the viewport. Never resize the native pane.
        int x = layout.body().x() - width - 12;
        if (x < 4) x = layout.body().x() + layout.body().width() + 12;
        x = Math.clamp(x, 4, layout.viewportWidth() - width - 4);
        int y = Math.clamp(layout.body().y(), 4, Math.max(4, layout.viewportHeight() - height - 4));
        Rect box = new Rect(x, y, width, height);
        Rect close = new Rect(x + width - 20, y + 4, 16, 14);
        Rect prev = pages > 1 ? new Rect(x + 5, y + height - 18, 48, 14) : CardControls.EMPTY_RECT;
        Rect next = pages > 1 ? new Rect(x + width - 53, y + height - 18, 48, 14) : CardControls.EMPTY_RECT;
        canvas.beginOverlay();
        canvas.clip(box);
        try {
            canvas.fill(box, CobblemonUiStyle.PANEL_BACKGROUND_SOLID);
            canvas.border(box, BORDER);
            text(canvas, mine ? "Pokemon details" : "Revealed moves", x + 5, y + 6, width - 30, FOREGROUND);
            button(canvas, close, "x", false, mouseX, mouseY);
            rows(canvas, box, visible, 23);
            if (pages > 1) {
                button(canvas, prev, "Prev", false, mouseX, mouseY);
                button(canvas, next, "Next", false, mouseX, mouseY);
                text(canvas, (page + 1) + "/" + pages, x + 61, y + height - 15, width - 122, MUTED);
            }
        } finally { canvas.unclip(); canvas.endOverlay(); }
        return new CardControls(box, close, prev, next, page, pages);
    }
    private static String scopeNote(MatchupSnapshot snapshot) {
        boolean partial = snapshot.notes().stream().anyMatch(note -> note.contains("only currently visible enemies"));
        boolean multi = snapshot.notes().stream().anyMatch(note -> note.startsWith("Multi battle:"));
        return (partial ? "Partial roster" : "") + (partial && multi ? " | " : "") + (multi ? "Multi battle" : "");
    }
    private static void tooltip(Canvas canvas, MatchupPanelLayout layout, List<Line> source, int mouseX, int mouseY) {
        int width = Math.min(Math.max(0, layout.viewportWidth() - 8), Math.min(288,
            source.stream().mapToInt(l -> l.chips().isEmpty() ? canvas.width(l.text()) + 24 : 288).max().orElse(100)));
        int capacity = Math.max(0, (layout.viewportHeight() - 16) / 12);
        List<Line> wrapped = wrap(canvas, source, width - 10);
        if (wrapped.size() > capacity) {
            // Keep all type icons/multipliers visible before sacrificing explanatory copy.
            // Full type names and remaining lines stay reachable in the clicked card.
            source = source.stream().map(line -> new Line(line.text(), line.type(), line.color(), line.chips().stream()
                .map(chip -> new Chip(chip.text().startsWith(chip.type().label() + " ")
                    ? chip.text().substring(chip.type().label().length() + 1) : chip.text(), chip.type())).toList())).toList();
            wrapped = wrap(canvas, source, width - 10);
        }
        if (wrapped.size() > capacity && capacity > 0) {
            wrapped = new ArrayList<>(wrapped.subList(0, capacity));
            wrapped.set(capacity - 1, new Line("Click portrait for full details", null, WARNING));
        }
        source = wrapped;
        int count = Math.min(source.size(), capacity);
        if (width < 24 || count == 0) return;
        int height = count * 12 + 8;
        var tooltip = CobblemonPanelGeometry.tooltip(
            new CobblemonPanelGeometry.Bounds(layout.body().x(), layout.body().y(),
                layout.body().width(), layout.body().height()),
            mouseX, mouseY, width, height, layout.viewportWidth(), layout.viewportHeight(), 4);
        if (tooltip.width() <= 0 || tooltip.height() <= 0) return;
        Rect box = new Rect(tooltip.x(), tooltip.y(), tooltip.width(), tooltip.height());
        canvas.beginOverlay();
        canvas.clip(box);
        try {
            canvas.fill(box, CobblemonUiStyle.PANEL_BACKGROUND_SOLID);
            canvas.border(box, BORDER);
            rows(canvas, box, source.subList(0, count), 4);
        } finally { canvas.unclip(); canvas.endOverlay(); }
    }
    private static void rows(Canvas canvas, Rect box, List<Line> rows, int top) {
        for (int i = 0; i < rows.size(); i++) {
            Line line = rows.get(i);
            int y = box.y() + top + i * 12, x = box.x() + 5;
            if (line.type() != null) { typeIcon(canvas, x, y, line.type()); x += 13; }
            text(canvas, line.text(), x, y + 1, box.x() + box.width() - x - 5, line.color());
            if (line.indent() > 0) x += line.indent();
            else if (!line.text().isEmpty()) x += canvas.width(line.text()) + 8;
            for (Chip chip : line.chips()) {
                typeIcon(canvas, x, y, chip.type());
                text(canvas, chip.text(), x + 13, y + 1, box.x() + box.width() - x - 18, line.color());
                x += 13 + canvas.width(chip.text()) + 8;
            }
        }
    }
    private static void typeIcon(Canvas canvas, int x, int y, PokemonType type) {
        if (!canvas.type(new Rect(x, y, 10, 10), type)) canvas.fill(new Rect(x + 1, y + 1, 8, 8), type.argb());
    }
    private static List<Line> wrap(Canvas canvas, List<Line> source, int width) {
        List<Line> result = new ArrayList<>();
        if (width < 24) return result;
        int groupIndent = source.stream().filter(l -> !l.chips().isEmpty() && !l.text().isEmpty())
            .mapToInt(l -> canvas.width(l.text()) + 8).max().orElse(0);
        for (Line line : source) {
            if (!line.chips().isEmpty()) {
                String label = line.text();
                int indent = label.isEmpty() ? 0 : groupIndent;
                // Tiny viewports may need the group caption on its own line.
                int widestChip = line.chips().stream().mapToInt(c -> 13 + canvas.width(c.text())).max().orElse(0);
                if (indent + widestChip > width) {
                    if (!label.isEmpty()) result.add(new Line(label, null, line.color()));
                    label = ""; indent = 0;
                }
                int used = indent;
                List<Chip> row = new ArrayList<>();
                for (Chip chip : line.chips()) {
                    int size = 13 + canvas.width(chip.text());
                    if (used + size > width && (used > 0 || !row.isEmpty())) {
                        result.add(new Line(label, null, line.color(), List.copyOf(row), indent));
                        label = ""; row.clear(); used = indent;
                    }
                    row.add(chip); used += size + 8;
                }
                if (!row.isEmpty()) result.add(new Line(label, null, line.color(), List.copyOf(row), indent));
            } else {
                String rest = line.text();
                int available = width - (line.type() == null ? 0 : 13);
                while (canvas.width(rest) > available && !rest.isEmpty()) {
                    int end = rest.length();
                    while (end > 0 && canvas.width(rest.substring(0, end)) > available) end = rest.offsetByCodePoints(end, -1);
                    if (end == 0) break;
                    int space = rest.lastIndexOf(' ', end);
                    if (space > 0) end = space;
                    result.add(new Line(rest.substring(0, end), line.type(), line.color()));
                    rest = rest.substring(end).stripLeading();
                }
                if (!rest.isEmpty()) result.add(new Line(rest, line.type(), line.color()));
            }
        }
        return result;
    }
    private static int color(Double multiplier, boolean incoming) {
        if (multiplier == null || multiplier == 1) return MUTED;
        return (incoming ? multiplier < 1 : multiplier > 1) ? GOOD : BAD;
    }
    private static void button(Canvas canvas, Rect rect, String label, boolean active, int mouseX, int mouseY) {
        if (rect.width() < 10 || rect.height() < 12) return;
        canvas.fill(rect, active ? 0xFF484848 : rect.contains(mouseX, mouseY) ? 0xFF363636 : BACKGROUND);
        canvas.border(rect, active ? FOREGROUND : BORDER);
        text(canvas, label, rect.x() + 4, rect.y() + (rect.height() - 9) / 2, rect.width() - 8, FOREGROUND);
    }
    private static void text(Canvas canvas, String value, int x, int y, int width, int color) {
        if (width <= 0) return;
        if (canvas.width(value) > width) {
            int budget = width - canvas.width("…");
            if (budget < 0) return;
            int low = 0, high = value.codePointCount(0, value.length());
            while (low < high) {
                int mid = (low + high + 1) / 2;
                if (canvas.width(value.substring(0, value.offsetByCodePoints(0, mid))) <= budget) low = mid;
                else high = mid - 1;
            }
            value = value.substring(0, value.offsetByCodePoints(0, low)) + "…";
        }
        canvas.text(value, x, y, color);
    }
}
