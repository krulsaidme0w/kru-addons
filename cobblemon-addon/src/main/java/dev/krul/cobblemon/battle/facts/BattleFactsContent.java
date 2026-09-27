package dev.krul.cobblemon.battle.facts;

import dev.krul.cobblemon.battle.matchup.CobblemonMatchupCatalog;
import dev.krul.cobblemon.battle.matchup.CobblemonMatchupReader;
import dev.krul.cobblemon.battle.matchup.MatchupNativeIcons;
import dev.krul.cobblemon.battle.matchup.MatchupPanelLayout.Rect;
import dev.krul.cobblemon.battle.matchup.MatchupSnapshot;
import dev.krul.cobblemon.battle.matchup.MatchupSnapshot.Fighter;
import dev.krul.cobblemon.gui.overlay.OverlayCanvas;
import dev.krul.cobblemon.gui.overlay.OverlayContent;
import net.minecraft.client.MinecraftClient;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static dev.krul.cobblemon.battle.matchup.BattleReflection.get;
import static dev.krul.cobblemon.battle.matchup.BattleReflection.singleton;

/** Host-owned overlay; reads the battle without touching its screen, message pane or packets. */
public final class BattleFactsContent implements OverlayContent {
    private static final int WIDTH = 400, HEIGHT = 250;
    private static final int LABEL_WIDTH = 52, TEAM_WIDTH = 172, COLUMN_WIDTH = 86;
    private static final int TABLE_TOP = 26, TABLE_BOTTOM = 160, DETAILS_TOP = 194, DETAIL_LINES = 4;
    private final CobblemonMatchupReader reader = new CobblemonMatchupReader(new CobblemonMatchupCatalog());
    private final MatchupNativeIcons icons = new MatchupNativeIcons();
    private final BattleFactsState state = new BattleFactsState();
    private Object connection, world;
    private UUID playerId;
    private List<String> detailLines = List.of();
    private String availability = "No active battle";

    public BattleFactsContent() {}
    @Override public String title() { return "Battle Facts"; }
    @Override public int width() { return WIDTH; }
    @Override public int height() { return HEIGHT; }

    @Override public void tick() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null || client.getNetworkHandler() == null) {
            reset();
            return;
        }
        UUID id = client.player.getUuid();
        if (connection != client.getNetworkHandler() || world != client.world || !Objects.equals(playerId, id)) {
            reset();
            connection = client.getNetworkHandler();
            world = client.world;
            playerId = id;
        }
        try {
            Object battle = get(singleton("com.cobblemon.mod.common.client.CobblemonClient"), "getBattle");
            Object repository = singleton("com.cobblemon.mod.common.client.battle.ClientBattleInformationRepository");
            MatchupSnapshot next = reader.read(battle, id, repository);
            if (state.update(next)) icons.clear();
            availability = next.battleId() == null ? "No active battle"
                : next.own().isEmpty() && next.enemies().isEmpty() ? "Battle data unavailable for this player" : "";
            refreshDetails();
        } catch (RuntimeException | LinkageError ignored) {
            // Discard stale facts rather than leaving them displayed as current after capability failure.
            state.reset();
            icons.clear();
            detailLines = List.of();
            availability = "Battle data unavailable";
        }
    }

    @Override public void render(OverlayCanvas canvas, double mouseX, double mouseY) {
        canvas.fill(0, 0, WIDTH, HEIGHT, canvas.background());
        if (!availability.isEmpty()) {
            canvas.textFit(availability, 8, 12, WIDTH - 16, canvas.text());
            canvas.textFit("Only client-visible battle facts are shown.", 8, 30, WIDTH - 16, canvas.muted());
            return;
        }
        MatchupSnapshot snapshot = state.snapshot();
        if (snapshot == null) return;
        canvas.text("Turn " + (snapshot.turn() > 0 ? snapshot.turn() : "?"), 5, 8, canvas.muted());
        for (int side = 0; side < 2; side++) renderTeam(canvas, side, mouseX, mouseY);
        String[] labels = {"Level", "Types", "HP", "Status"};
        for (int row = 0; row < labels.length; row++) {
            int y = 99 + row * 15;
            canvas.text(labels[row], 5, y, canvas.muted());
            canvas.fill(LABEL_WIDTH, y + 12, WIDTH - LABEL_WIDTH - 4, 1, canvas.border());
        }
        canvas.textFit("? unknown   ~ last observed/profile   Click a column for details", 5, 165, WIDTH - 10, canvas.muted());
        canvas.fill(4, 179, WIDTH - 8, 1, canvas.border());
        Fighter selected = state.selected();
        if (selected == null) {
            canvas.textFit("Select a Pokemon to inspect its revealed facts.", 8, 188, WIDTH - 16, canvas.muted());
            canvas.textFit("Scroll over either team to browse its columns.", 8, 205, WIDTH - 16, canvas.muted());
            return;
        }
        canvas.textFit(BattleFactsModel.name(selected) + " | " + (state.selectedSide() == 0 ? "Your team" : "Opponents"),
            6, 183, WIDTH - 104, canvas.text());
        int end = Math.min(detailLines.size(), state.detailOffset() + DETAIL_LINES);
        canvas.textFit((state.detailOffset() + 1) + "-" + end + "/" + detailLines.size(), WIDTH - 94, 183, 88, canvas.muted());
        for (int i = state.detailOffset(); i < end; i++) {
            canvas.textFit(detailLines.get(i), 8, DETAILS_TOP + (i - state.detailOffset()) * 11, WIDTH - 18, canvas.text());
        }
        canvas.textFit("Scroll details | Left/Right: Pokemon | Up/Down: details", 6, 240, WIDTH - 12, canvas.muted());
    }

    private void renderTeam(OverlayCanvas canvas, int side, double mouseX, double mouseY) {
        int start = LABEL_WIDTH + side * TEAM_WIDTH;
        List<Fighter> team = state.team(side);
        int offset = state.offset(side);
        canvas.textFit((side == 0 ? "Yours " : "Foes ") + (team.isEmpty() ? "?"
            : (offset + 1) + "-" + Math.min(offset + 2, team.size()) + "/" + team.size()), start + 2, 8, TEAM_WIDTH - 46, canvas.text());
        for (int direction = -1; direction <= 1; direction += 2) {
            int x = start + TEAM_WIDTH - (direction < 0 ? 42 : 21);
            boolean enabled = direction < 0 ? offset > 0 : offset + BattleFactsState.COLUMNS < team.size();
            // ASCII labels use the host's font; no missing Unicode arrow glyphs.
            String label = direction < 0 ? "<" : ">";
            if (enabled) canvas.button(label, x, 3, 19, 18, inside(mouseX, mouseY, x, 3, 19, 18));
            else canvas.text(label, x + 4, 8, canvas.muted());
        }
        if (team.isEmpty()) {
            canvas.textFit("? No roster received", start + 4, 55, TEAM_WIDTH - 8, canvas.muted());
            return;
        }
        for (int col = 0; col < BattleFactsState.COLUMNS && offset + col < team.size(); col++) {
            Fighter fighter = team.get(offset + col);
            int x = start + col * COLUMN_WIDTH;
            boolean selected = state.isSelected(side, fighter);
            if (selected || inside(mouseX, mouseY, x, TABLE_TOP, COLUMN_WIDTH, TABLE_BOTTOM - TABLE_TOP)) {
                canvas.fill(x + 1, TABLE_TOP, COLUMN_WIDTH - 2, 1, selected ? canvas.accent() : canvas.border());
            }
            Rect portrait = new Rect(x + 28, 29, 30, 30);
            if (!icons.portrait(canvas.context(), portrait, fighter.portrait().speciesId(), fighter.portrait().aspects())) {
                canvas.text("?", x + 40, 39, canvas.muted());
            }
            canvas.textFit(BattleFactsModel.name(fighter), x + 4, 62, COLUMN_WIDTH - 8, canvas.text());
            canvas.textFit(BattleFactsModel.position(fighter), x + 4, 76, COLUMN_WIDTH - 8, canvas.muted());
            String[] values = {BattleFactsModel.level(fighter), BattleFactsModel.types(fighter.facts().types()),
                BattleFactsModel.health(fighter.facts().health()), BattleFactsModel.value(fighter.facts().status())};
            for (int row = 0; row < values.length; row++) {
                canvas.textFit(values[row], x + 4, 99 + row * 15, COLUMN_WIDTH - 8, canvas.text());
            }
            canvas.fill(x, TABLE_TOP, 1, TABLE_BOTTOM - TABLE_TOP, canvas.border());
        }
    }

    @Override public boolean click(double x, double y, int button) {
        if (button != 0 || !availability.isEmpty() || !inside(x, y, 0, 0, WIDTH, HEIGHT)) return false;
        for (int side = 0; side < 2; side++) {
            int start = LABEL_WIDTH + side * TEAM_WIDTH;
            if (inside(x, y, start + TEAM_WIDTH - 42, 3, 19, 18)) { state.page(side, -1); return true; }
            if (inside(x, y, start + TEAM_WIDTH - 21, 3, 19, 18)) { state.page(side, 1); return true; }
            if (inside(x, y, start, TABLE_TOP, TEAM_WIDTH, TABLE_BOTTOM - TABLE_TOP)) {
                state.select(side, state.offset(side) + (int) ((x - start) / COLUMN_WIDTH));
                refreshDetails();
                return true;
            }
        }
        return false;
    }

    @Override public boolean scroll(double x, double y, double amount) {
        if (!Double.isFinite(amount) || amount == 0 || !availability.isEmpty() || !inside(x, y, 0, 0, WIDTH, HEIGHT)) return false;
        int direction = amount > 0 ? -1 : 1;
        if (y >= 179 && state.selected() != null) {
            state.scrollDetails(direction, detailLines.size(), DETAIL_LINES);
            return true;
        }
        if (x >= LABEL_WIDTH && x < WIDTH - 4 && y < TABLE_BOTTOM) {
            state.page(x < LABEL_WIDTH + TEAM_WIDTH ? 0 : 1, direction);
            return true;
        }
        return false;
    }

    @Override public boolean key(int key) {
        if (!availability.isEmpty() || state.selected() == null) return false;
        // GLFW arrows only. Escape and focus belong to the host.
        if (key == 263 || key == 262) {
            state.moveSelection(key == 263 ? -1 : 1);
            refreshDetails();
            return true;
        }
        if (key == 265 || key == 264) {
            state.scrollDetails(key == 265 ? -1 : 1, detailLines.size(), DETAIL_LINES);
            return true;
        }
        return false;
    }

    private void refreshDetails() {
        Fighter selected = state.selected();
        detailLines = selected == null ? List.of() : BattleFactsModel.wrap(
            BattleFactsModel.details(selected, state.selectedSide() == 0), WIDTH - 18,
            text -> (int) Math.ceil(OverlayCanvas.textWidth(text)));
        state.clampDetails(detailLines.size(), DETAIL_LINES);
    }

    @Override public void reset() {
        state.reset();
        icons.clear();
        detailLines = List.of();
        connection = world = null;
        playerId = null;
        availability = "No active battle";
    }

    private static boolean inside(double x, double y, int left, int top, int width, int height) {
        return x >= left && x < left + width && y >= top && y < top + height;
    }
}
