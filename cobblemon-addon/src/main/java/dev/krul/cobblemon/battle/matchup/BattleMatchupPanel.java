package dev.krul.cobblemon.battle.matchup;

import meteordevelopment.meteorclient.MeteorClient;
import dev.krul.cobblemon.BattleMatchups;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import java.util.*;
import static dev.krul.cobblemon.battle.matchup.MatchupPanelLayout.Rect;
import static dev.krul.cobblemon.battle.matchup.BattleReflection.*;

/** Native battle-log companion. Local tab geometry and read-only battle facts only. */
public final class BattleMatchupPanel {
    private final CobblemonMatchupReader reader = new CobblemonMatchupReader(new CobblemonMatchupCatalog());
    private final MatchupPaneGeometry geometry = new MatchupPaneGeometry();
    private final MatchupNativeIcons icons = new MatchupNativeIcons();
    private MatchupPanelState state = new MatchupPanelState();
    private MatchupSnapshot snapshot;
    private List<MatchupCalculator.Cell> cells = List.of();
    private MatchupPanelLayout layout;
    private MatchupPanelRenderer.CardControls card = MatchupPanelRenderer.CardControls.EMPTY;
    private Object paneObject;
    private MatchupNativePane nativePane;
    private long nextRead;
    private boolean logged, failed;

    /** Before the native pane paints: no oversized background or one-frame stale hit regions. */
    public void beforeRender(Screen screen) {
        layout = null;
        if (!refreshEnabled() || failed) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.currentScreen != screen) { close(); return; }
        try {
            Object pane = get(screen, "getMessagePane");
            if (pane == null) return;
            if (paneObject != pane) {
                close();
                nativePane = new MatchupNativePane(pane);
                paneObject = pane;
            }
            readSnapshot(client.player.getUuid());
            Rect current = nativePane.read();
            boolean visibleMatrix = state.matrix() && snapshot != null && snapshot.battleId() != null;
            Rect desired = geometry.update(visibleMatrix, current, screen.width, screen.height,
                snapshot == null ? 0 : snapshot.own().size(), snapshot == null ? 0 : snapshot.enemies().size());
            if (!current.equals(desired)) nativePane.apply(desired);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            disable(exception);
        }
    }

    public void render(Screen screen, DrawContext context, int mouseX, int mouseY) {
        layout = null;
        if (!refreshEnabled()) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (failed || client.player == null || client.currentScreen != screen || nativePane == null) return;
        try {
            Object pane = get(screen, "getMessagePane");
            if (pane == null || number(get(pane, "getOpacity"), 0) < .95) return;
            if (snapshot == null || snapshot.battleId() == null) return;
            layout = MatchupPanelLayout.fit(nativePane.read(), screen.width, screen.height, snapshot.own().size(), snapshot.enemies().size());
            if (!logged) {
                MeteorClient.LOG.info("[BATTLE_MATCHUP] compact portrait matrix attached; read-only battle={}", snapshot.battleId());
                logged = true;
            }
            var canvas = new MatchupPanelRenderer.Canvas() {
                public int width(String text) { return client.textRenderer.getWidth(text); }
                public void fill(Rect r, int color) { context.fill(r.x(), r.y(), r.x() + r.width(), r.y() + r.height(), color); }
                public void border(Rect r, int color) { context.drawBorder(r.x(), r.y(), r.width(), r.height(), color); }
                public void text(String text, int px, int py, int color) { context.drawText(client.textRenderer, text, px, py, color, false); }
                public void clip(Rect r) { context.enableScissor(r.x(), r.y(), r.x() + r.width(), r.y() + r.height()); }
                public void unclip() { context.disableScissor(); }
                public void beginOverlay() {
                    // Profile models have depth beyond the base 2D plane. Finish prior GUI work,
                    // then put the entire tooltip (background, text AND type icons) in front.
                    context.draw();
                    context.getMatrices().push();
                    context.getMatrices().translate(0, 0, 400);
                }
                public void endOverlay() {
                    context.draw();
                    context.getMatrices().pop();
                }
                public boolean portrait(Rect r, MatchupSnapshot.Fighter fighter) {
                    return icons.portrait(context, r, fighter.portrait().speciesId(), fighter.portrait().aspects());
                }
                public boolean type(Rect r, dev.krul.cobblemon.battle.PokemonType type) {
                    return icons.type(context, r, type);
                }
                public boolean tera(Rect r, MatchupSnapshot.Fighter fighter) {
                    if (!fighter.teraActive()) return false;
                    int color = fighter.teraType() == null ? 0xFFFFD28A : fighter.teraType().argb();
                    context.fill(r.x(), r.y(), r.x() + r.width(), r.y() + r.height(), color);
                    context.drawBorder(r.x(), r.y(), r.width(), r.height(), 0xFFFFFFFF);
                    context.drawText(client.textRenderer, "T", r.x() + 2, r.y() + 1, 0xFF191919, false);
                    return true;
                }
            };
            context.getMatrices().push();
            try {
                context.getMatrices().translate(0, 0, 300);
                card = MatchupPanelRenderer.render(canvas, layout, snapshot, cells, state.matrix(), state.selectedFighter(), state.page(), mouseX, mouseY);
            } finally { context.getMatrices().pop(); }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            disable(exception);
        }
    }

    public boolean click(double x, double y, int button) {
        if (!refreshEnabled() || layout == null) return false;
        // The mixin forwards screen clicks globally. Never consume a click that
        // is outside the native pane, its two tabs, or the rendered panel.
        if (!layout.backdrop().contains(x, y)
            && !layout.historyTab().contains(x, y)
            && !layout.matchupTab().contains(x, y)) return false;
        boolean previous = state.matrix();
        boolean consumed = state.click(layout, snapshot, card, x, y, button);
        if (previous != state.matrix()) layout = null;
        return consumed;
    }

    public void close() {
        layout = null;
        card = MatchupPanelRenderer.CardControls.EMPTY;
        state.clearCard();
        icons.clear();
        Rect history = geometry.restore();
        if (history != null && nativePane != null) {
            try { nativePane.apply(history); }
            catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
                MeteorClient.LOG.warn("[BATTLE_MATCHUP] could not restore native log geometry", exception);
            }
        }
    }
    private void readSnapshot(UUID player) {
        long now = System.nanoTime();
        if (snapshot != null && now < nextRead) return;
        nextRead = now + 200_000_000L;
        Object battle = get(singleton("com.cobblemon.mod.common.client.CobblemonClient"), "getBattle");
        Object repository = singleton("com.cobblemon.mod.common.client.battle.ClientBattleInformationRepository");
        MatchupSnapshot next = reader.read(battle, player, repository);
        if (snapshot != null && !Objects.equals(snapshot.battleId(), next.battleId())) {
            close();
            state = new MatchupPanelState();
            state.setEnabled(true);
        }
        if (!next.equals(snapshot)) cells = MatchupCalculator.calculate(next);
        snapshot = next;
        state.retain(next);
    }
    private void disable(Throwable exception) {
        close();
        failed = true;
        MeteorClient.LOG.warn("[BATTLE_MATCHUP] extension disabled for this screen; native battle retained", exception);
        BattleMatchups.reportUnavailable("native GUI capability failed (" + exception.getClass().getSimpleName() + ")");
    }
    private boolean refreshEnabled() {
        if (state.setEnabled(BattleMatchups.isEnabled())) return true;
        close();
        snapshot = null;
        cells = List.of();
        nextRead = 0;
        failed = false;
        return false;
    }
}
