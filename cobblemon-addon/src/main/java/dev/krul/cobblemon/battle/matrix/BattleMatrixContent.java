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
    private final CobblemonMatchupCatalog catalog = new CobblemonMatchupCatalog();
    private final CobblemonMatchupReader reader = new CobblemonMatchupReader(catalog);
    private final MatchupNativeIcons icons = new MatchupNativeIcons();
    private final BattleMatrixContext context = BattleMatrixContext.nativeContext();
    private final BattleMatrixState state = new BattleMatrixState();
    private Object connection;
    private Object world;
    private UUID playerId;
    private java.util.Map<String, List<PokemonType>> possibleAttackTypes = java.util.Map.of();
    private java.util.Map<String, String> possibleAttackSignatures = java.util.Map.of();

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
            updatePossibleAttackTypes(next);
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
            var coverage = BattleMatrixModel.coverage(group.moves(), state.enemies());
            var potentialCoverage = BattleMatrixModel.potentialCoverage(possibleTypes(group.fighter()), state.enemies());
            renderHeader(canvas, group.fighter(), ownHeader(row), ownPortrait(row),
                typeText(group.fighter()) + " · K " + coverage.label() + " · ~ " + potentialCoverage.label());
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
                var potential = potential(row, state.enemies().get(column));
                if (potential.cell().multiplier() != null) {
                    String possible = "~" + (potential.attackType() == null ? "?" : potential.attackType().abbreviation())
                        + " " + potential.cell().label().substring(1) + "×";
                    canvas.textFit(possible, bounds.x() + 4, bounds.bottom() - 10, bounds.width() - 8, canvas.muted());
                }
            }
            canvas.fill(LEFT, group.bottom() - 1, width() - LEFT * 2, 1, canvas.border());
        }
    }

    private void renderHeader(OverlayCanvas canvas, Fighter fighter, Rect header, Rect portrait) {
        renderHeader(canvas, fighter, header, portrait, typeText(fighter));
    }

    private void renderHeader(OverlayCanvas canvas, Fighter fighter, Rect header, Rect portrait, String detail) {
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
        canvas.textFit(detail, x, header.y() + 21, available, canvas.muted());
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
            lines.addAll(potential(hit.row(), state.enemies().get(hit.column())).cell().tooltip());
            lines.add("Move type: " + (typeBadge(move).equals("?") ? "unknown" : move.data().type().label()));
            return List.copyOf(lines);
        }
        for (int column = 0; column < state.enemies().size(); column++) {
            if (enemyHeader(column).contains(x, y)) return fighterTooltip(state.enemies().get(column));
        }
        for (int row = 0; row < state.groups().size(); row++) {
            var group = state.groups().get(row);
            if (ownHeader(row).contains(x, y)) {
                List<String> lines = new ArrayList<>(fighterTooltip(group.fighter()));
                var coverage = BattleMatrixModel.coverage(group.moves(), state.enemies());
                var potentialCoverage = BattleMatrixModel.potentialCoverage(possibleTypes(group.fighter()), state.enemies());
                lines.add("Confirmed-move coverage: " + coverage.covered() + " of " + coverage.revealed()
                    + " opponents with confirmed current types.");
                lines.add("Potential learnset coverage: " + potentialCoverage.covered() + " of " + potentialCoverage.revealed()
                    + " opponents with current or species-baseline types; advisory only.");
                lines.add("Type chart only; not a damage or safe-switch prediction.");
                return List.copyOf(lines);
            }
            if (group.moves().isEmpty()) for (int column = 0; column < state.enemies().size(); column++) {
                if (cell(row, column).contains(x, y)) {
                    List<String> lines = new ArrayList<>(List.of(fighterName(group.fighter()), "Known moves unknown."));
                    lines.addAll(potential(row, state.enemies().get(column)).cell().tooltip());
                    return List.copyOf(lines);
                }
            }
            for (int column = 0; column < state.enemies().size(); column++) if (cell(row, column).contains(x, y))
                return potential(row, state.enemies().get(column)).cell().tooltip();
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
        possibleAttackTypes = java.util.Map.of();
        possibleAttackSignatures = java.util.Map.of();
    }

    private void updatePossibleAttackTypes(MatchupSnapshot snapshot) {
        java.util.Map<String, String> signatures = new java.util.HashMap<>();
        for (Fighter fighter : snapshot.own()) signatures.put(fighter.id(), potentialSignature(fighter));
        if (signatures.equals(possibleAttackSignatures)) return;
        java.util.Map<String, List<PokemonType>> next = new java.util.HashMap<>();
        for (Fighter fighter : snapshot.own()) {
            if (signatures.get(fighter.id()).equals(possibleAttackSignatures.get(fighter.id()))) {
                next.put(fighter.id(), possibleAttackTypes.getOrDefault(fighter.id(), List.of()));
                continue;
            }
            String species = fighter.portrait().speciesId();
            if (species.isBlank()) continue;
            next.put(fighter.id(), catalog.possibleAttackTypes(species, fighter.portrait().aspects(), fighter.level()));
        }
        possibleAttackTypes = java.util.Map.copyOf(next);
        possibleAttackSignatures = java.util.Map.copyOf(signatures);
    }

    private static String potentialSignature(Fighter fighter) {
        return fighter.portrait().speciesId() + "|" + fighter.portrait().aspects().stream().sorted().toList() + "|" + fighter.level();
    }

    private List<PokemonType> possibleTypes(Fighter fighter) {
        return possibleAttackTypes.getOrDefault(fighter.id(), List.of());
    }

    private BattleMatrixModel.Potential potential(int row, Fighter enemy) {
        var fighter = state.groups().get(row).fighter();
        return BattleMatrixModel.potential(possibleTypes(fighter), enemy);
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
