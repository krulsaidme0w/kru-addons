package dev.krul.cobblemon.battle.matrix;

import dev.krul.cobblemon.battle.PokemonType;
import dev.krul.cobblemon.battle.matchup.CobblemonMatchupCatalog;
import dev.krul.cobblemon.battle.matchup.CobblemonMatchupReader;
import dev.krul.cobblemon.battle.matchup.MatchupNativeIcons;
import dev.krul.cobblemon.battle.matchup.MatchupSnapshot;
import dev.krul.cobblemon.battle.matchup.MatchupSnapshot.Fighter;
import dev.krul.cobblemon.battle.matchup.MatchupSnapshot.Move;
import dev.krul.cobblemon.battle.matchup.MatchupTypes;
import dev.krul.cobblemon.gui.overlay.OverlayCanvas;
import dev.krul.cobblemon.gui.overlay.OverlayContent;
import net.minecraft.client.MinecraftClient;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import static dev.krul.cobblemon.battle.matrix.BattleMatrixLayout.*;
import static dev.krul.cobblemon.battle.matchup.BattleReflection.singleton;

/** Battle-only Pokémon × Pokémon matrix of observed moves and confirmed type matchups. */
public final class BattleMatrixContent implements OverlayContent {
    private final CobblemonMatchupReader reader = new CobblemonMatchupReader(new CobblemonMatchupCatalog());
    private final MatchupNativeIcons icons = new MatchupNativeIcons();
    private final BattleMatrixContext context = BattleMatrixContext.nativeContext();
    private final BattleMatrixState state = new BattleMatrixState();
    private Object connection;
    private Object world;
    private UUID playerId;

    @Override public String title() { return "Battle Matchups"; }
    @Override public int width() { return BattleMatrixLayout.width(state.enemies().size()); }
    @Override public int height() { return BattleMatrixLayout.height(state.groups().size()); }
    @Override public double defaultScale() { return .8; }
    @Override public boolean contextual() { return true; }
    @Override public Object contextKey() { return context.currentId(); }

    @Override public void tick() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null || client.getNetworkHandler() == null) {
            clear();
            return;
        }
        UUID currentPlayer = client.player.getUuid();
        if (connection != client.getNetworkHandler() || world != client.world || !Objects.equals(playerId, currentPlayer)) {
            clear();
            connection = client.getNetworkHandler();
            world = client.world;
            playerId = currentPlayer;
        }
        Object battle = context.currentBattle();
        if (context.id(battle) == null) {
            clearSnapshot();
            return;
        }
        try {
            MatchupSnapshot next = reader.read(battle, currentPlayer,
                singleton("com.cobblemon.mod.common.client.battle.ClientBattleInformationRepository"));
            if (state.update(next)) icons.clear();
        } catch (RuntimeException | LinkageError ignored) {
            clearSnapshot();
        }
    }

    @Override public void render(OverlayCanvas canvas, double mouseX, double mouseY) {
        canvas.fill(0, 0, width(), height(), canvas.background());
        if (liveSnapshot() == null) return;
        for (int column = 0; column < state.enemies().size(); column++) {
            Fighter enemy = state.enemies().get(column);
            renderHeader(canvas, enemy, enemyHeader(column), enemyPortrait(column));
        }
        canvas.fill(LEFT, HEADER_HEIGHT, width() - LEFT * 2, 1, canvas.border());
        var rows = BattleMatrixLayout.rows(state.groups());
        if (rows.isEmpty()) {
            canvas.textFit("Waiting for your team.", LEFT + 3, BODY_TOP + 8, width() - 2 * LEFT - 6, canvas.muted());
        }
        if (state.enemies().isEmpty()) {
            canvas.textFit("Opponents unknown", MATRIX_X + 4, 13, COLUMN_WIDTH - 8, canvas.muted());
        }
        for (int row = 0; row < rows.size(); row++) {
            var group = rows.get(row);
            renderHeader(canvas, group.fighter(), ownHeader(row), ownPortrait(row));
            var labels = BattleMatrixLabels.abbreviations(group.moves().stream().map(move -> move.data().name()).toList());
            for (int column = 0; column < state.enemies().size(); column++) {
                Rect bounds = cell(row, column);
                canvas.fill(bounds.x(), bounds.y(), 1, bounds.height(), canvas.border());
                if (group.moves().isEmpty()) {
                    canvas.text("?", bounds.x() + COLUMN_WIDTH / 2 - 3, bounds.y() + 14, canvas.muted());
                }
                for (int slot = 0; slot < group.moves().size(); slot++) {
                    Move move = group.moves().get(slot);
                    BattleMatrixCell result = BattleMatrixModel.evaluate(move, state.enemies().get(column));
                    Rect chip = chip(row, column, slot);
                    if (chip.contains(mouseX, mouseY)) canvas.fill(chip.x(), chip.y(), chip.width(), chip.height(), canvas.border());
                    // Type is encoded by a small swatch; text stays in the readable Meteor foreground.
                    PokemonType type = typeBadge(move).equals("?") ? null : move.data().type();
                    canvas.fill(chip.x(), chip.bottom() - 2, chip.width(), 1, type == null ? canvas.muted() : type.argb());
                    String label = labels.get(slot) + " " + result.label() + (result.multiplier() == null ? "" : "×");
                    canvas.textFit(label, chip.x() + 2, chip.y() + 2, chip.width() - 4,
                        result.evidence() == BattleMatrixCell.Evidence.UNKNOWN ? canvas.muted() : canvas.text());
                }
            }
            canvas.fill(LEFT, group.bottom() - 1, width() - LEFT * 2, 1, canvas.border());
        }
    }

    private void renderHeader(OverlayCanvas canvas, Fighter fighter, Rect header, Rect portrait) {
        if (fighter.active()) canvas.fill(header.x() + 3, header.y(), header.width() - 6, 1, canvas.accent());
        var nativeBounds = new dev.krul.cobblemon.battle.matchup.MatchupPanelLayout.Rect(
            portrait.x(), portrait.y(), portrait.width(), portrait.height());
        if (fighter.portrait().speciesId().isBlank()
            || !icons.portrait(canvas.context(), nativeBounds, fighter.portrait().speciesId(), fighter.portrait().aspects())) {
            canvas.text("?", portrait.x() + 9, portrait.y() + 8, canvas.muted());
        }
        int x = portrait.right() + 4;
        int available = header.right() - x - 4;
        canvas.textFit(fighterName(fighter), x, header.y() + 9, available, canvas.text());
        canvas.textFit(typeText(fighter), x, header.y() + 21, available, canvas.muted());
    }

    static String typeBadge(Move move) {
        return move == null || move.data() == null || move.special() || move.data().type() == null
            ? "?" : move.data().type().abbreviation();
    }

    @Override public List<String> tooltip(double x, double y) {
        if (liveSnapshot() == null || !new Rect(0, 0, width(), height()).contains(x, y)) return List.of();
        Hit hit = BattleMatrixLayout.hit(state.groups(), state.enemies().size(), x, y);
        if (hit != null) {
            var group = state.groups().get(hit.row());
            Move move = group.moves().get(hit.move());
            List<String> lines = new ArrayList<>();
            lines.add(fighterName(group.fighter()));
            lines.addAll(BattleMatrixModel.evaluate(move, state.enemies().get(hit.column())).tooltip());
            lines.add("Move type: " + (typeBadge(move).equals("?") ? "unknown" : move.data().type().label()));
            return List.copyOf(lines);
        }
        for (int column = 0; column < state.enemies().size(); column++) {
            if (enemyHeader(column).contains(x, y)) return fighterTooltip(state.enemies().get(column));
        }
        for (int row = 0; row < state.groups().size(); row++) {
            var group = state.groups().get(row);
            if (ownHeader(row).contains(x, y)) return fighterTooltip(group.fighter());
            if (group.moves().isEmpty()) for (int column = 0; column < state.enemies().size(); column++) {
                if (cell(row, column).contains(x, y)) return List.of(fighterName(group.fighter()), "Moves unknown.");
            }
        }
        return List.of();
    }

    @Override public void reset() { clear(); }

    private void clear() {
        clearSnapshot();
        connection = world = null;
        playerId = null;
    }
    private void clearSnapshot() {
        if (state.snapshot() != null) icons.clear();
        state.reset();
    }
    private MatchupSnapshot liveSnapshot() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world != world || client.getNetworkHandler() != connection
            || !Objects.equals(client.player.getUuid(), playerId)) return null;
        return state.snapshot(context.currentId());
    }
    private static String fighterName(Fighter fighter) {
        return fighter.name() == null || fighter.name().isBlank() ? "Unknown" : fighter.name();
    }
    static String typeText(Fighter fighter) {
        var current = fighter.facts().types();
        if (current.known() && current.freshness() == MatchupSnapshot.Freshness.CURRENT && !current.value().isEmpty())
            return String.join("/", current.value().stream().map(PokemonType::label).toList());
        var resolved = MatchupTypes.resolve(fighter);
        return !resolved.types().isEmpty()
            ? String.join("/", resolved.types().stream().map(PokemonType::label).toList())
                + (resolved.potential() ? " · base" : "") : "Type ?";
    }
    static List<String> fighterTooltip(Fighter fighter) {
        List<String> lines = new ArrayList<>();
        lines.add(fighterName(fighter) + (fighter.active() ? " - active" : ""));
        lines.add(typeText(fighter).equals("Type ?") ? "Current battle typing unknown." : "Current types: " + typeText(fighter));
        lines.addAll(fighter.notes());
        return List.copyOf(lines);
    }
}
