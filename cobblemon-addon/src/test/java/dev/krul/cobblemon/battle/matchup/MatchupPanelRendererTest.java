package dev.krul.cobblemon.battle.matchup;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static dev.krul.cobblemon.battle.matchup.MatchupPanelLayout.*;
import static dev.krul.cobblemon.battle.matchup.MatchupCalculatorTest.*;
import static dev.krul.cobblemon.battle.PokemonType.*;

class MatchupPanelRendererTest {
    static class Surface implements MatchupPanelRenderer.Canvas {
        record Text(String value, int x, int y, int color) {}
        final List<Text> texts = new ArrayList<>();
        record Portrait(Rect rect, MatchupSnapshot.Fighter fighter) {}
        final List<Portrait> portraits = new ArrayList<>();
        record TypeIcon(Rect rect, dev.krul.cobblemon.battle.PokemonType type) {}
        final List<TypeIcon> types = new ArrayList<>();
        final List<MatchupSnapshot.Fighter> teraIcons = new ArrayList<>();
        final List<Rect> fills = new ArrayList<>();
        final List<Text> overlayTexts = new ArrayList<>();
        boolean overlay;
        Rect clip;
        public int width(String text) { return text.codePointCount(0, text.length()) * 6; }
        public void fill(Rect r, int color) { check(r); fills.add(r); }
        public void border(Rect r, int color) { check(r); }
        public void text(String s, int x, int y, int color) { check(new Rect(x, y, width(s), 9)); var text = new Text(s, x, y, color); texts.add(text); if (overlay) overlayTexts.add(text); }
        public void beginOverlay() { assertFalse(overlay); overlay = true; }
        public void endOverlay() { assertTrue(overlay); overlay = false; }
        public void clip(Rect r) { clip = r; }
        public void unclip() { clip = null; }
        public boolean portrait(Rect r, MatchupSnapshot.Fighter fighter) { check(r); portraits.add(new Portrait(r, fighter)); return false; }
        public boolean type(Rect r, dev.krul.cobblemon.battle.PokemonType type) { check(r); types.add(new TypeIcon(r, type)); return true; }
        public boolean tera(Rect r, MatchupSnapshot.Fighter fighter) { check(r); teraIcons.add(fighter); return true; }
        private void check(Rect r) {
            if (clip == null) return;
            assertTrue(r.x() >= clip.x() && r.y() >= clip.y() && r.x() + r.width() <= clip.x() + clip.width()
                && r.y() + r.height() <= clip.y() + clip.height(), "Drawing must fit its native pane: " + r);
        }
        boolean contains(String s) { return texts.stream().anyMatch(t -> t.value().contains(s)); }
    }
    private static MatchupSnapshot snapshot() {
        return new MatchupSnapshot(UUID.randomUUID(), 1,
            List.of(fighter("mine", List.of(FIRE), List.of(move("Flamethrower", FIRE, 10, false)))),
            List.of(fighter("enemy", List.of(GRASS), List.of())), List.of());
    }
    @Test void hoverExplainsThePairWithoutClickingOrOpeningAnInlineCard() {
        var layout = fit(new Rect(0, 20, 650, 390), 784, 420, 1, 1, 78);
        var snapshot = snapshot();
        var surface = new Surface();
        var cell = layout.cell(0, 0);
        MatchupPanelRenderer.render(surface, layout, snapshot, MatchupCalculator.calculate(snapshot), true, cell.x() + 2, cell.y() + 2);
        assertTrue(surface.contains("Flamethrower"), "Hover is the primary explanation, not a click-only card");
        assertFalse(surface.contains("Moves & abilities"));
        surface.texts.clear();
        MatchupPanelRenderer.render(surface, layout, snapshot, MatchupCalculator.calculate(snapshot), true, 700, 400);
        assertFalse(surface.contains("Flamethrower"), "Explanation disappears when pointer leaves the grid");
        assertFalse(surface.contains("Hit x2"), "Cells carry icons rather than repeated text");
    }

    @Test void unrevealedMovesStayUnknownWhileKnownTypesShowASeparatePotentialMarker() {
        var snapshot = new MatchupSnapshot(UUID.randomUUID(), 1,
            List.of(fighter("mine", List.of(FIRE), List.of())),
            List.of(fighter("enemy", List.of(GRASS), List.of())), List.of());
        var layout = fit(new Rect(0, 20, 250, 240), 784, 420, 1, 1);
        var surface = new Surface();
        MatchupPanelRenderer.render(surface, layout, snapshot, MatchupCalculator.calculate(snapshot), true, 700, 400);
        assertTrue(surface.contains("?"), "No unrevealed move may be invented");
        assertTrue(surface.types.stream().anyMatch(icon -> icon.type() == GRASS),
            "Known enemy type must remain visible as a potential marker");
        assertTrue(surface.contains("↑"), "Potential effectiveness must remain visible separately from moves");
    }

    @Test void compactPaneExplainsResizeAndCannotSelectInvisibleCells() {
        var layout = fit(new Rect(0, 20, 180, 100), 784, 420, 6, 6);
        var surface = new Surface();
        var snapshot = snapshot();
        MatchupPanelRenderer.render(surface, layout, snapshot, MatchupCalculator.calculate(snapshot), true, 100, 100);
        assertTrue(surface.contains("Enlarge"));
        assertNull(layout.hit(100, 100));
    }

    @Test void idleMatrixHasNoTurnAnnotationAboveThePortraits() {
        var snapshot = MatchupRenderPreviewTest.teams();
        var layout = fit(new Rect(100, 100, 218, 218), 784, 420, 6, 6);
        var surface = new Surface();
        MatchupPanelRenderer.render(surface, layout, snapshot, MatchupCalculator.calculate(snapshot), true, 0, 0);
        assertEquals(12, surface.portraits.size());
        assertTrue(surface.texts.stream().noneMatch(t -> t.y() >= layout.body().y()
            && t.y() < layout.ownHeader(0).y()), "Space above portraits stays clear of redundant turn labels");
    }

    @Test void spaceBelowTheGridHasNoPermanentLegendOrInvisibleHelpHover() {
        var snapshot = snapshot();
        var layout = fit(new Rect(100, 100, 297, 302), 784, 420, 1, 1);
        var lastCell = layout.cell(0, 0);
        int gridBottom = lastCell.y() + lastCell.height();
        var surface = new Surface();

        MatchupPanelRenderer.render(surface, layout, snapshot, MatchupCalculator.calculate(snapshot),
            true, layout.body().x() + 160, gridBottom + 3);

        assertTrue(surface.overlayTexts.isEmpty(), "Empty space must not open the removed legend's help");
        assertTrue(surface.texts.stream().noneMatch(t -> t.y() >= gridBottom),
            "Idle matrix ends at the grid, without helper labels below it");
        assertTrue(surface.fills.stream().noneMatch(r -> r.y() >= gridBottom),
            "Legend pictograms must disappear with their labels");
    }

    @Test void smallestNativeLogPrioritizesTheResizeHint() {
        var layout = fit(new Rect(100, 100, 153, 46), 784, 420, 1, 1);
        var surface = new Surface();
        var snapshot = snapshot();
        MatchupPanelRenderer.render(surface, layout, snapshot, MatchupCalculator.calculate(snapshot), true, 0, 0);
        assertTrue(surface.contains("Enlarge"));
    }

    @Test void partialRosterAndMultiBattleLimitationsStayVisibleWithoutHover() {
        var initial = snapshot();
        var snapshot = new MatchupSnapshot(initial.battleId(), 1, initial.own(), initial.enemies(), List.of(
            "Waiting for battle information; only currently visible enemies shown.",
            "Multi battle: per-target type chart only; adjacency and switch legality not evaluated."));
        var layout = fit(new Rect(0, 20, 700, 390), 784, 420, 1, 1);
        var surface = new Surface();
        MatchupPanelRenderer.render(surface, layout, snapshot, MatchupCalculator.calculate(snapshot), true, 0, 0);
        assertTrue(surface.contains("Partial roster"));
        assertTrue(surface.contains("Multi battle"));
    }

    @Test void allTwelvePortraitTargetsAndThirtySixCellsExplainOnHoverInsideViewport() {
        var snapshot = MatchupRenderPreviewTest.teams();
        Rect pane = compactPane(new Rect(610, 300, 153, 46), 784, 420, 6, 6);
        var layout = fit(pane, 784, 420, 6, 6);
        assertEquals(6, layout.visibleColumns());
        for (int c = 0; c < 6; c++) for (int r = 0; r < 6; r++) {
            var surface = new Surface();
            var cell = layout.cell(c, r);
            MatchupPanelRenderer.render(surface, layout, snapshot, MatchupCalculator.calculate(snapshot), true, cell.x() + 2, cell.y() + 2);
            assertEquals(12, surface.portraits.size());
            assertTrue(surface.contains(snapshot.own().get(c).name() + " vs " + snapshot.enemies().get(r).name()));
            assertTrue(surface.overlayTexts.size() <= 14, "Hover is a compact summary, not a full battle dump");
            for (var text : surface.texts) MatchupPanelLayoutTest.inside(new Rect(text.x(), text.y(), surface.width(text.value()), 9), 784, 420);
        }
        var surface = new Surface();
        var header = layout.ownHeader(1);
        MatchupPanelRenderer.render(surface, layout, snapshot, MatchupCalculator.calculate(snapshot), true, header.x() + 2, header.y() + 2);
        assertTrue(surface.contains("Garchomp"));
        assertTrue(surface.contains("Ice x4"));
    }

    @Test void unknownHitExplainsWhetherTypesOrUsableMovesAreMissing() {
        var layout = fit(new Rect(10, 20, 228, 102), 784, 420, 1, 1);
        var cell = layout.cell(0, 0);
        for (boolean unknownTypes : List.of(true, false)) {
            var snapshot = new MatchupSnapshot(UUID.randomUUID(), 1,
                List.of(fighter("m", List.of(FIRE), List.of())),
                List.of(fighter("e", unknownTypes ? List.of() : List.of(GRASS), List.of())), List.of());
            var surface = new Surface();
            MatchupPanelRenderer.render(surface, layout, snapshot, MatchupCalculator.calculate(snapshot), true, cell.x() + 2, cell.y() + 2);
            assertTrue(surface.contains(unknownTypes ? "Target types unknown" : "No usable attack known"));
            assertFalse(surface.contains("Hit: ? ?"));
        }
    }

    @Test void portraitHoverShowsUsefulBaseTypesAsPotentialChips() {
        var mine = fighterWithTypes("mine", List.of(), List.of(FIRE, FLYING), List.of());
        var snapshot = new MatchupSnapshot(UUID.randomUUID(), 1, List.of(mine), List.of(fighter("enemy", List.of(GRASS), List.of())), List.of());
        var layout = fit(new Rect(500, 160, 228, 232), 784, 420, 1, 1);
        var surface = new Surface();

        MatchupPanelRenderer.render(surface, layout, snapshot, MatchupCalculator.calculate(snapshot), true,
            layout.ownHeader(0).x() + 2, layout.ownHeader(0).y() + 2);

        assertTrue(surface.contains("Base types · potential"));
        assertTrue(surface.contains("Fire"));
        assertTrue(surface.contains("Flying"));
        assertFalse(surface.contains("Current types unknown"));
        assertTrue(surface.texts.stream().filter(t -> t.value().equals("Base types · potential") || t.value().equals("Fire") || t.value().equals("Flying"))
            .allMatch(t -> t.color() == 0xFFFFD28A));
    }

    @Test void portraitHoverPrefersCurrentTypesOverBaseTypes() {
        var mine = fighterWithTypes("mine", List.of(WATER), List.of(FIRE), List.of());
        var snapshot = new MatchupSnapshot(UUID.randomUUID(), 1, List.of(mine), List.of(fighter("enemy", List.of(GRASS), List.of())), List.of());
        var layout = fit(new Rect(500, 160, 228, 232), 784, 420, 1, 1);
        var surface = new Surface();

        MatchupPanelRenderer.render(surface, layout, snapshot, MatchupCalculator.calculate(snapshot), true,
            layout.ownHeader(0).x() + 2, layout.ownHeader(0).y() + 2);

        assertTrue(surface.contains("Current types"));
        assertTrue(surface.contains("Water"));
        assertFalse(surface.contains("Base types · potential"));
        int waterY = surface.texts.stream().filter(t -> t.value().equals("Water")).findFirst().orElseThrow().y();
        assertFalse(surface.texts.stream().anyMatch(t -> t.value().equals("Fire") && t.y() == waterY));
    }

    @Test void portraitHoverExplainsThatTheCurrentTypeComesFromActiveTera() {
        var tera = new MatchupSnapshot.Fighter("mine", "Kyurem-Black", List.of(FAIRY), List.of(DRAGON, ICE), List.of(),
            100, 100, true, false, "Teravolt", "", true, "", List.of(), MatchupSnapshot.Portrait.UNKNOWN, FAIRY, true);
        var snapshot = new MatchupSnapshot(UUID.randomUUID(), 1, List.of(tera), List.of(fighter("enemy", List.of(FIRE), List.of())), List.of());
        var layout = fit(new Rect(500, 160, 228, 232), 784, 420, 1, 1);
        var surface = new Surface();

        MatchupPanelRenderer.render(surface, layout, snapshot, MatchupCalculator.calculate(snapshot), true,
            layout.ownHeader(0).x() + 2, layout.ownHeader(0).y() + 2);

        assertTrue(surface.contains("Tera: Fairy (active)"));
        assertTrue(surface.contains("Current types"));

        surface.texts.clear();
        var cell = layout.cell(0, 0);
        MatchupPanelRenderer.render(surface, layout, snapshot, MatchupCalculator.calculate(snapshot), true,
            cell.x() + 2, cell.y() + 2);
        assertTrue(surface.contains("Your Tera: Fairy"));
    }

    @Test void activeOwnPokemonGetsATeraBadgeOnItsPortrait() {
        var tera = new MatchupSnapshot.Fighter("mine", "Kyurem-Black", List.of(FAIRY), List.of(DRAGON, ICE), List.of(),
            100, 100, true, false, "Teravolt", "", true, "", List.of(), MatchupSnapshot.Portrait.UNKNOWN, FAIRY, true);
        var snapshot = new MatchupSnapshot(UUID.randomUUID(), 1, List.of(tera), List.of(fighter("enemy", List.of(FIRE), List.of())), List.of());
        var layout = fit(new Rect(500, 160, 228, 232), 784, 420, 1, 1);
        var surface = new Surface();

        MatchupPanelRenderer.render(surface, layout, snapshot, MatchupCalculator.calculate(snapshot), true, 0, 0);

        assertEquals(List.of(tera), surface.teraIcons);
    }

    @Test void portraitHoverKeepsUnknownCopyWhenNoCurrentOrBaseTypesExist() {
        var mine = fighterWithTypes("mine", List.of(), List.of(), List.of());
        var snapshot = new MatchupSnapshot(UUID.randomUUID(), 1, List.of(mine), List.of(fighter("enemy", List.of(GRASS), List.of())), List.of());
        var layout = fit(new Rect(500, 160, 228, 232), 784, 420, 1, 1);
        var surface = new Surface();

        MatchupPanelRenderer.render(surface, layout, snapshot, MatchupCalculator.calculate(snapshot), true,
            layout.ownHeader(0).x() + 2, layout.ownHeader(0).y() + 2);

        assertTrue(surface.contains("Current types unknown"));
        assertFalse(surface.contains("Base types · potential"));
    }

    @Test void hiddenEnemyStillUsesRevealedMovesCard() {
        var enemy = fighterWithTypes("enemy", List.of(), List.of(FIRE), List.of());
        var snapshot = new MatchupSnapshot(UUID.randomUUID(), 1, snapshot().own(), List.of(enemy), List.of());
        var layout = fit(new Rect(500, 160, 228, 232), 784, 420, 1, 1);
        var hover = new Surface();
        var header = layout.enemyHeader(0);
        MatchupPanelRenderer.render(hover, layout, snapshot, MatchupCalculator.calculate(snapshot), true,
            header.x() + 2, header.y() + 2);
        assertHiddenEnemyTypesAbsent(hover);

        var state = new MatchupPanelState(); state.setEnabled(true);
        assertTrue(state.click(layout, snapshot, MatchupPanelRenderer.CardControls.EMPTY, header.x() + 2, header.y() + 2, 0));
        var surface = new Surface();

        MatchupPanelRenderer.render(surface, layout, snapshot, MatchupCalculator.calculate(snapshot), true,
            state.selectedFighter(), state.page(), 0, 0);

        assertTrue(surface.contains("Revealed moves"));
        assertFalse(surface.contains("Pokemon details"));
        assertHiddenEnemyTypesAbsent(surface);
    }

    @Test void identifiedEnemyUsesKnownBaseTypesAsPotentialWhenCurrentTypesAreMissing() {
        var enemy = new MatchupSnapshot.Fighter("enemy", "Charizard", List.of(), List.of(FIRE, FLYING), List.of(),
            50, 100, true, false, "", "", false, "", List.of(),
            new MatchupSnapshot.Portrait("charizard", Set.of()));
        var snapshot = new MatchupSnapshot(UUID.randomUUID(), 1, snapshot().own(), List.of(enemy), List.of());
        var layout = fit(new Rect(500, 160, 228, 232), 784, 420, 1, 1);
        var header = layout.enemyHeader(0);
        var hover = new Surface();

        MatchupPanelRenderer.render(hover, layout, snapshot, MatchupCalculator.calculate(snapshot), true,
            header.x() + 2, header.y() + 2);

        assertTrue(hover.contains("Base types · potential"));
        assertTrue(hover.contains("Fire"));
        assertTrue(hover.contains("Flying"));
        assertTrue(hover.contains("Weak"));
        assertTrue(hover.contains("Resists"));
        assertTrue(hover.contains("Immune"));

        var card = new Surface();
        MatchupPanelRenderer.render(card, layout, snapshot, MatchupCalculator.calculate(snapshot), true,
            enemy.id(), 0, 0, 0);
        assertTrue(card.contains("Base types · potential"));
        assertTrue(card.contains("Fire"));
        assertTrue(card.contains("Flying"));
        assertTrue(card.contains("Weak"));
        assertTrue(card.contains("Resists"));
    }

    private static void assertHiddenEnemyTypesAbsent(Surface surface) {
        assertFalse(surface.contains("Base types · potential"));
        assertFalse(surface.contains("Fire"));
        assertFalse(surface.contains("Weak"));
        assertFalse(surface.contains("Resists"));
        assertFalse(surface.contains("Immune"));
    }

    @Test void resistanceRowsUseEveryTypeIconWithoutTruncatingTheList() {
        var enemy = fighter("Emolga", List.of(ELECTRIC, FLYING), List.of());
        var snapshot = new MatchupSnapshot(UUID.randomUUID(), 1, List.of(enemy), List.of(enemy), List.of());
        var layout = fit(new Rect(500, 160, 228, 232), 784, 420, 1, 1);
        var header = layout.ownHeader(0);
        var surface = new Surface();
        MatchupPanelRenderer.render(surface, layout, snapshot, MatchupCalculator.calculate(snapshot), true, header.x() + 3, header.y() + 3);
        for (var type : List.of(ICE, ROCK, GROUND, GRASS, FIGHTING, FLYING, BUG, STEEL)) {
            assertTrue(surface.types.stream().anyMatch(icon -> icon.type() == type), "Every weakness, resistance and immunity must be readable: " + type);
        }
        assertFalse(surface.texts.stream().anyMatch(t -> t.value().contains("…")));
        assertTrue(surface.contains("Immune"));
    }

    @Test void tinyViewportStillShowsCorviknightImmunityIcons() {
        var fighter = fighter("Corviknight", List.of(STEEL, FLYING), List.of());
        var snapshot = new MatchupSnapshot(UUID.randomUUID(), 1, List.of(fighter), List.of(fighter), List.of());
        var layout = fit(new Rect(0, 16, 200, 144), 200, 160, 1, 1);
        var header = layout.ownHeader(0);
        var surface = new Surface();
        MatchupPanelRenderer.render(surface, layout, snapshot, MatchupCalculator.calculate(snapshot), true, header.x() + 2, header.y() + 2);
        assertTrue(surface.types.stream().anyMatch(i -> i.type() == POISON));
        assertTrue(surface.types.stream().anyMatch(i -> i.type() == GROUND));
    }

    @Test void ownPortraitPinsTheCompleteChartWithFullNamesReachableOnTinyScreen() {
        var fighter = fighter("Corviknight", List.of(STEEL, FLYING), List.of());
        var foe = fighter("foe", List.of(ICE), List.of());
        var snapshot = new MatchupSnapshot(UUID.randomUUID(), 1, List.of(fighter), List.of(foe), List.of());
        var layout = fit(new Rect(0, 16, 200, 144), 200, 160, 1, 1);
        var state = new MatchupPanelState(); state.setEnabled(true);
        var header = layout.ownHeader(0);
        assertTrue(state.click(layout, snapshot, MatchupPanelRenderer.CardControls.EMPTY, header.x() + 2, header.y() + 2, 0));
        assertEquals(fighter.id(), state.selectedFighter());
        Set<String> names = new HashSet<>();
        for (int page = 0; page < 10; page++) {
            var surface = new Surface();
            var card = MatchupPanelRenderer.render(surface, layout, snapshot, MatchupCalculator.calculate(snapshot), true, state.selectedFighter(), state.page(), 0, 0);
            assertTrue(surface.contains("Pokemon details"));
            surface.overlayTexts.forEach(t -> names.add(t.value()));
            MatchupPanelLayoutTest.inside(card.box(), 200, 160);
            if (card.page() + 1 == card.pages()) break;
            assertTrue(state.click(layout, snapshot, card, card.next().x() + 1, card.next().y() + 1, 0));
        }
        assertTrue(names.contains("Poison x0"));
        assertTrue(names.contains("Ground x0"));
        assertTrue(names.contains("Fire x2"));
        assertTrue(names.contains("Electric x2"));
        assertTrue(names.contains("Grass x0.25"));
    }

    @Test void quietGridHasNoAxisSentencesAndNeutralIsOneNotAnAmbiguousDash() {
        var enemy = fighter("e", List.of(NORMAL), List.of(move("Tackle", NORMAL, -1, false)));
        var snapshot = new MatchupSnapshot(UUID.randomUUID(), 1, List.of(enemy), List.of(enemy), List.of());
        var layout = fit(new Rect(500, 160, 228, 232), 784, 420, 1, 1);
        var surface = new Surface();
        MatchupPanelRenderer.render(surface, layout, snapshot, MatchupCalculator.calculate(snapshot), true, 0, 0);
        assertFalse(surface.contains("Your team"));
        assertFalse(surface.contains("Outline ="));
        assertEquals(2, surface.texts.stream().filter(t -> t.value().equals("1")).count());
        assertTrue(surface.fills.stream().anyMatch(r -> r.x() < layout.body().x() && r.y() <= layout.body().y()
            && r.x() + r.width() >= layout.body().x() + layout.body().width()), "Cover native history text in the inner left margin too");
    }

    @Test void selectedFighterCardListsRevealedMoveFactsWithoutInventingRemainingPp() {
        var known = new MatchupSnapshot.Move(new dev.krul.cobblemon.battle.BattleMove(
            "icebeam", "Ice Beam", ICE, dev.krul.cobblemon.battle.MoveCategory.SPECIAL, 90), -1, false, false, false);
        var enemy = fighter("enemy", List.of(ICE), List.of(known));
        var snapshot = new MatchupSnapshot(UUID.randomUUID(), 1, snapshot().own(), List.of(enemy), List.of());
        var layout = fit(new Rect(500, 160, 228, 232), 784, 420, 1, 1);
        var surface = new Surface();
        MatchupPanelRenderer.render(surface, layout, snapshot, MatchupCalculator.calculate(snapshot), true, enemy.id(), 0, 0, 0);
        assertTrue(surface.contains("Revealed moves"));
        assertTrue(surface.contains("Ice Beam"));
        assertTrue(surface.contains("Special"));
        assertTrue(surface.contains("Power 90"));
        assertTrue(surface.contains("PP ?"));
        assertFalse(surface.contains("uses left"));
        assertFalse(surface.overlay);
    }

    @Test void smallViewportMoveCardPagesAreFullyReachableByButtonsAndStayInsideViewport() {
        var snapshot = MatchupRenderPreviewTest.teams();
        var known = java.util.stream.IntStream.range(0, 4).mapToObj(i -> move("Revealed move " + i, ICE, -1, false)).toList();
        var enemy = fighter("e", List.of(ICE), known);
        snapshot = new MatchupSnapshot(snapshot.battleId(), 1, snapshot.own(), List.of(enemy), List.of());
        var layout = fit(new Rect(70, 20, 228, 136), 320, 160, 6, 1);
        var state = new MatchupPanelState(); state.setEnabled(true);
        var header = layout.enemyHeader(0);
        state.click(layout, snapshot, MatchupPanelRenderer.CardControls.EMPTY, header.x() + 2, header.y() + 2, 0);
        Set<String> text = new HashSet<>();
        MatchupPanelRenderer.CardControls last = null;
        for (int page = 0; page < 8; page++) {
            var surface = new Surface();
            last = MatchupPanelRenderer.render(surface, layout, snapshot, MatchupCalculator.calculate(snapshot), true, state.selectedFighter(), state.page(), 0, 0);
            surface.overlayTexts.forEach(t -> text.add(t.value()));
            MatchupPanelLayoutTest.inside(last.box(), 320, 160);
            assertTrue(surface.texts.stream().noneMatch(t -> t.value().contains("…")));
            if (last.page() == last.pages() - 1) break;
            assertTrue(state.click(layout, snapshot, last, last.next().x() + 2, last.next().y() + 2, 0));
        }
        for (int i = 0; i < 4; i++) assertTrue(text.contains("Revealed move " + i));
        assertNotNull(last);
        assertTrue(last.pages() > 1, "Small viewports must use buttons, not drop the bottom moves");
        assertTrue(state.click(layout, snapshot, last, last.previous().x() + 2, last.previous().y() + 2, 0));
        assertEquals(last.page() - 1, state.page());
    }
}
