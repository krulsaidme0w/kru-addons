package dev.krul.cobblemon.battle.matchup;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static dev.krul.cobblemon.battle.matchup.MatchupPanelLayout.*;
import static dev.krul.cobblemon.battle.matchup.MatchupSnapshot.*;
import static dev.krul.cobblemon.battle.matchup.MatchupCalculatorTest.*;
import static dev.krul.cobblemon.battle.PokemonType.*;

/** Production drawing commands on a substitute font; explicitly NOT Minecraft framebuffer evidence. */
class MatchupRenderPreviewTest {
    @Test void potentialBaseTypePreviewUsesPotentialHeadingAndWarningCopy() {
        var mine = new Fighter("mine", "Ralts", List.of(), List.of(PSYCHIC, FAIRY), List.of(),
            1, 100, true, false, "", "", true, "", List.of());
        var enemy = new Fighter("enemy", "Sewaddle", List.of(BUG, GRASS), List.of(BUG, GRASS), List.of(),
            10, 100, true, false, "", "", true, "", List.of());
        var snapshot = new MatchupSnapshot(UUID.randomUUID(), 1, List.of(mine), List.of(enemy), List.of());
        var surface = new MatchupPanelRendererTest.Surface();
        var layout = fit(new Rect(500, 160, 228, 232), 784, 420, 1, 1);
        var header = layout.ownHeader(0);

        MatchupPanelRenderer.render(surface, layout, snapshot, MatchupCalculator.calculate(snapshot), true,
            header.x() + 2, header.y() + 2);

        assertTrue(surface.contains("Base types · potential"));
        assertTrue(surface.contains("Psychic"));
        assertTrue(surface.contains("Fairy"));
        assertTrue(surface.texts.stream().filter(t -> t.value().equals("Base types · potential")
                || t.value().equals("Psychic") || t.value().equals("Fairy"))
            .allMatch(t -> t.color() == 0xFFFFD28A));
    }

    @Test void rendererFitsSmallLargeAndOffscreenPanesWithEveryPairReachable() throws Exception {
        var snapshot = teams();
        for (Rect pane : List.of(new Rect(0, 20, 70, 40), new Rect(0, 20, 150, 90),
            new Rect(0, 20, 210, 180), new Rect(20, 40, 500, 360), new Rect(20, 40, 740, 386), new Rect(-40, -30, 900, 500))) {
            var surface = new MatchupPanelRendererTest.Surface();
            var layout = fit(pane, 800, 440, 6, 6, surface.width("Reply x0.25") + 6);
            MatchupPanelRenderer.render(surface, layout, snapshot, MatchupCalculator.calculate(snapshot), true, 100, 100);
            if (layout.visibleColumns() > 0) {
                assertEquals(0, surface.texts.stream().filter(t -> t.value().startsWith("Hit x")).count());
                for (int c = 0; c < 6; c++) for (int r = 0; r < 6; r++) {
                    Rect cell = layout.cell(c, r);
                    assertArrayEquals(new int[]{c, r}, layout.hit(cell.x() + 3, cell.y() + 3));
                }
            } else assertNull(layout.hit(100, 100));
        }
        render("compact-six-by-six", snapshot, 0);
        render("compact-ice-threat-hover", snapshot, 1);
        render("compact-portrait-hover", snapshot, 2);
        render("compact-enemy-moves", snapshot, 3);
        render("compact-six-by-one", new MatchupSnapshot(snapshot.battleId(), 7, snapshot.own(), List.of(snapshot.enemies().getFirst()), List.of()), 0);
        var mine = new Fighter("mine0", "Ralts", List.of(PSYCHIC, FAIRY), List.of(PSYCHIC, FAIRY),
            List.of(move("Disarming Voice", FAIRY, 14, false)), 1, 9, true, false, "synchronize", "", true, "", List.of());
        var enemy = new Fighter("enemy0", "Sewaddle", List.of(BUG, GRASS), List.of(BUG, GRASS),
            List.of(move("Tackle", NORMAL, -1, false)), 10, 97, true, false, "", "", false, "", List.of());
        render("compact-single", new MatchupSnapshot(UUID.randomUUID(), 1, List.of(mine), List.of(enemy), List.of()), 1);
    }

    private static void render(String name, MatchupSnapshot snapshot, int hover) throws Exception {
        try (var surface = new Raster()) {
            var pane = new MatchupPaneGeometry().update(true, new Rect(540, 300, 153, 46), 800, 440, snapshot.own().size(), snapshot.enemies().size());
            surface.fill(pane, 0xFF999999);
            var layout = fit(pane, 800, 440, snapshot.own().size(), snapshot.enemies().size());
            var target = hover == 2 ? layout.ownHeader(1) : layout.cell(Math.min(1, snapshot.own().size() - 1), Math.min(1, snapshot.enemies().size() - 1));
            MatchupPanelRenderer.render(surface, layout, snapshot, MatchupCalculator.calculate(snapshot), true,
                hover == 3 ? snapshot.enemies().get(1).id() : null, 0,
                hover > 0 ? target.x() + 3 : 795, hover > 0 ? target.y() + 3 : 435);
            var output = Path.of("build", "matchup-previews", name + ".png");
            Files.createDirectories(output.getParent());
            assertTrue(ImageIO.write(surface.image, "png", output.toFile()));
        }
    }
    static MatchupSnapshot teams() {
        var types = List.of(List.of(PSYCHIC, FAIRY), List.of(DRAGON, GROUND), List.of(FLYING, STEEL), List.of(FIRE, GHOST), List.of(WATER, GROUND), List.of(NORMAL));
        var enemyTypes = List.of(List.of(BUG, GRASS), List.of(ICE, GROUND), List.of(FIRE, STEEL), List.of(WATER, ELECTRIC), List.of(ROCK, DARK), List.of(GHOST, POISON));
        String[] names = {"Gardevoir", "Garchomp", "Corviknight", "Skeledirge", "Gastrodon", "Blissey"};
        String[] foes = {"Sewaddle", "Mamoswine", "Heatran", "Rotom-Wash", "Tyranitar", "Gengar"};
        var moveTypes = List.of(FAIRY, GROUND, FLYING, FIRE, WATER, FIRE);
        String[] moves = {"Moonblast", "Earthquake", "Brave Bird", "Torch Song", "Surf", "Flamethrower"};
        List<Fighter> own = new ArrayList<>(), enemies = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            own.add(new Fighter("mine" + i, names[i], types.get(i), types.get(i), List.of(move(moves[i], moveTypes.get(i), 10, false)),
                100, 90 - 10 * i, i == 0, false, "", "", false, "", List.of()));
            enemies.add(new Fighter("enemy" + i, foes[i], enemyTypes.get(i), enemyTypes.get(i), i == 1 ? List.of(new Move(
                new dev.krul.cobblemon.battle.BattleMove("tackle", "Tackle", NORMAL,
                    dev.krul.cobblemon.battle.MoveCategory.PHYSICAL, 40), -1, false, false, false)) : List.of(),
                100, i == 5 ? 0 : 100, i == 0, i == 5, "", "", false, "", List.of()));
        }
        return new MatchupSnapshot(UUID.randomUUID(), 7, own, enemies, List.of());
    }
    private static class Raster extends MatchupPanelRendererTest.Surface implements AutoCloseable {
        final BufferedImage image = new BufferedImage(800, 440, BufferedImage.TYPE_INT_RGB);
        final Graphics2D graphics = image.createGraphics();
        Raster() {
            graphics.setColor(new Color(0x20262A));
            graphics.fillRect(0, 0, 800, 440);
            graphics.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 10));
            graphics.setColor(Color.WHITE);
            graphics.drawString("OFFLINE | synthetic teams / substitute font + icon placeholders", 20, 14);
        }
        public int width(String s) { return graphics.getFontMetrics().stringWidth(s); }
        public void fill(Rect r, int color) { super.fill(r, color); graphics.setColor(new Color(color, true)); graphics.fillRect(r.x(), r.y(), r.width(), r.height()); }
        public void border(Rect r, int color) { super.border(r, color); graphics.setColor(new Color(color, true)); graphics.drawRect(r.x(), r.y(), r.width() - 1, r.height() - 1); }
        public void text(String s, int x, int y, int color) { super.text(s, x, y, color); graphics.setColor(new Color(color, true)); graphics.drawString(s, x, y + 8); }
        public void clip(Rect r) { super.clip(r); graphics.setClip(r.x(), r.y(), r.width(), r.height()); }
        public void unclip() { super.unclip(); graphics.setClip(null); }
        public boolean portrait(Rect r, Fighter fighter) {
            super.portrait(r, fighter);
            fill(r, fighter.types().isEmpty() ? 0xFF444444 : fighter.types().getFirst().argb());
            String label = fighter.name().substring(0, Math.min(2, fighter.name().length()));
            text(label, r.x() + 2, r.y() + 5, 0xFF111111);
            return true;
        }
        public boolean type(Rect r, dev.krul.cobblemon.battle.PokemonType type) {
            super.type(r, type);
            fill(new Rect(r.x() + 1, r.y() + 1, 8, 8), type.argb());
            return true;
        }
        public void close() { graphics.dispose(); }
    }
}

