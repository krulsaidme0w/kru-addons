package dev.krul.cobblemon.battle.matrix;

import dev.krul.cobblemon.battle.BattleMove;
import dev.krul.cobblemon.battle.MoveCategory;
import dev.krul.cobblemon.battle.PokemonType;
import dev.krul.cobblemon.battle.matchup.MatchupSnapshot;
import dev.krul.cobblemon.battle.matchup.MatchupSnapshot.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import javax.imageio.ImageIO;
import static dev.krul.cobblemon.battle.matrix.BattleMatrixLayout.*;

/** Manual geometry fixture, not a rendering test or a Minecraft screenshot.
 * Run main with a PNG output path after testClasses. Uses production geometry, labels and math;
 * AWT font and portrait placeholders deliberately do not pretend to verify native rendering.
 */
public final class BattleMatrixGeometryPreview {
    public static void main(String[] args) throws Exception {
        BufferedImage image = new BufferedImage(980, 390, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(0x292929));
        g.fillRect(0, 0, image.getWidth(), image.getHeight());
        g.setColor(Color.WHITE);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        g.drawString("6 × 6 · production geometry at 0.8 scale", 16, 23);
        g.drawString("2 × 2 · same scale", 674, 23);
        panel(g, 16, 40, 6);
        panel(g, 674, 40, 2);
        g.setColor(new Color(0xBBBBBB));
        g.drawString("Illustrative fixtures · AWT text · ? portrait placeholders · no Minecraft / Meteor rendering claim", 16, 350);
        g.drawString("K = confirmed revealed moves · ~ = species/form learnset potential; hover for evidence.", 16, 371);
        g.dispose();
        ImageIO.write(image, "png", Path.of(args[0]).toFile());
    }

    private static void panel(Graphics2D parent, int x, int y, int count) {
        Graphics2D g = (Graphics2D) parent.create();
        g.translate(x, y);
        g.scale(.8, .8);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 9));
        var snapshot = snapshot(count);
        var groups = BattleMatrixModel.groups(snapshot);
        var enemies = BattleMatrixModel.enemies(snapshot);
        List<PokemonType> possibleTypes = List.of(PokemonType.FIRE, PokemonType.WATER, PokemonType.ICE);
        fill(g, new Rect(0, 0, width(count), height(count)), 0x141414);
        for (int column = 0; column < count; column++) header(g, enemies.get(column), enemyHeader(column), enemyPortrait(column));
        fill(g, new Rect(LEFT, HEADER_HEIGHT, width(count) - 2 * LEFT, 1), 0x383838);
        for (int row = 0; row < count; row++) {
            var group = groups.get(row);
            var knownCoverage = BattleMatrixModel.coverage(group.moves(), enemies);
            var potentialCoverage = BattleMatrixModel.potentialCoverage(possibleTypes, enemies);
            header(g, group.fighter(), ownHeader(row), ownPortrait(row), typeLabel(group.fighter())
                + " · K " + knownCoverage.label() + " · ~ " + potentialCoverage.label());
            var labels = BattleMatrixLabels.abbreviations(group.moves().stream().map(move -> move.data().name()).toList());
            for (int column = 0; column < count; column++) {
                Rect cell = cell(row, column);
                fill(g, new Rect(cell.x(), cell.y(), 1, cell.height()), 0x383838);
                if (group.moves().isEmpty()) text(g, "?", cell.x() + 51, cell.y() + 14, 24, 0xAAAAAA);
                for (int slot = 0; slot < group.moves().size(); slot++) {
                    var move = group.moves().get(slot);
                    var result = BattleMatrixModel.evaluate(move, enemies.get(column));
                    Rect chip = chip(row, column, slot);
                    fill(g, new Rect(chip.x(), chip.bottom() - 2, chip.width(), 1), move.data().type().argb());
                    text(g, labels.get(slot) + " " + result.label() + (result.multiplier() == null ? "" : "×"),
                        chip.x() + 2, chip.y() + 2, chip.width() - 4,
                        result.evidence() == BattleMatrixCell.Evidence.UNKNOWN ? 0xAAAAAA : 0xF0F0F0);
                }
                var potential = BattleMatrixModel.potential(possibleTypes, enemies.get(column));
                if (potential.cell().multiplier() != null) text(g, "~" + potential.attackType().abbreviation() + " "
                    + potential.cell().label().substring(1) + "×", cell.x() + 4, cell.bottom() - 10, cell.width() - 8, 0xAAAAAA);
            }
            fill(g, new Rect(LEFT, BODY_TOP + (row + 1) * GROUP_ROW - 1, width(count) - 2 * LEFT, 1), 0x383838);
        }
        g.dispose();
    }

    private static void header(Graphics2D g, Fighter fighter, Rect header, Rect portrait) {
        header(g, fighter, header, portrait, typeLabel(fighter));
    }
    private static void header(Graphics2D g, Fighter fighter, Rect header, Rect portrait, String detail) {
        if (fighter.active()) fill(g, new Rect(header.x() + 3, header.y(), header.width() - 6, 1), 0x913DE2);
        text(g, "?", portrait.x() + 9, portrait.y() + 8, 24, 0xAAAAAA);
        int x = portrait.right() + 4, width = header.right() - x - 4;
        text(g, fighter.name(), x, header.y() + 9, width, 0xF0F0F0);
        text(g, detail, x, header.y() + 21, width, 0xAAAAAA);
    }
    private static String typeLabel(Fighter fighter) {
        var fact = fighter.facts().types();
        return fact.known() ? String.join("/", fact.value().stream().map(PokemonType::label).toList()) : "Type ?";
    }
    private static void fill(Graphics2D g, Rect rect, int rgb) {
        g.setColor(new Color(rgb));
        g.fillRect(rect.x(), rect.y(), rect.width(), rect.height());
    }
    private static void text(Graphics2D g, String label, int x, int y, int width, int rgb) {
        while (g.getFontMetrics().stringWidth(label) > width && label.length() > 3)
            label = label.substring(0, label.length() - (label.endsWith("…") ? 2 : 1)) + "…";
        g.setColor(new Color(rgb));
        g.drawString(label, x, y + 8);
    }
    private static MatchupSnapshot snapshot(int count) {
        String[] names = {"Charizard", "Gardevoir", "Rotom-Wash", "Kingambit", "Alolan Ninetales", "Unknown"};
        List<List<PokemonType>> types = List.of(List.of(PokemonType.FIRE, PokemonType.FLYING),
            List.of(PokemonType.PSYCHIC, PokemonType.FAIRY), List.of(PokemonType.WATER, PokemonType.ELECTRIC),
            List.of(PokemonType.DARK, PokemonType.STEEL), List.of(PokemonType.ICE, PokemonType.FAIRY), List.of());
        List<Move> moves = List.of(move("Fire Blast", PokemonType.FIRE, MoveCategory.SPECIAL),
            move("Fire Fang", PokemonType.FIRE, MoveCategory.PHYSICAL),
            move("Flame Burst", PokemonType.FIRE, MoveCategory.SPECIAL), move("Protect", PokemonType.NORMAL, MoveCategory.STATUS));
        List<Fighter> roster = IntStream.range(0, count).mapToObj(i -> new Fighter("slot-" + i, names[i],
            List.of(), List.of(), i == 5 ? List.of() : moves, 50, 100, i == 0, false, "", "", false, "", List.of(),
            Portrait.UNKNOWN, null, false, types.get(i).isEmpty() ? Facts.UNKNOWN
                : new Facts(null, new Fact<>(types.get(i), Freshness.CURRENT, "illustrative fixture"), null, null, null, null))).toList();
        return new MatchupSnapshot(UUID.randomUUID(), 1, roster, roster, List.of());
    }
    private static Move move(String name, PokemonType type, MoveCategory category) {
        return new Move(new BattleMove(name, name, type, category, 80), -1, false, false, false);
    }
}
