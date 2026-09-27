package dev.krul.builder.ui;

import dev.krul.builder.minecraft.BuilderModule;
import meteordevelopment.meteorclient.gui.GuiThemes;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.systems.modules.Modules;

import java.util.List;

/** Compact passive progress display; it never handles keyboard or chat input. */
public final class BuilderHud extends HudElement {
    public static final HudElementInfo<BuilderHud> INFO = new HudElementInfo<>(
        Hud.GROUP, "builder-progress", "Displays Builder connection and accepted-plan progress.", BuilderHud::new);

    public BuilderHud() { super(INFO); }

    private List<String> lines() {
        BuilderModule module = Modules.get().get(BuilderModule.class);
        if (module == null || !module.isActive()) return List.of();
        BuilderUiState state = module.uiState();
        if (state == null) return List.of("Builder · select and scan an area");
        String accepted = state.accepted().map(plan -> "accepted r" + plan.revision()).orElse("no accepted plan");
        return List.of("Builder · " + state.phase(), accepted,
            "matched " + state.progress().matching() + " · place " + state.progress().place()
                + " · replace " + state.progress().replace() + " · unknown " + state.progress().unknown());
    }

    @Override public void tick(HudRenderer renderer) {
        double width = 0, height = 0;
        for (String line : lines()) { width = Math.max(width, renderer.textWidth(line, true)); height += renderer.textHeight(true) + 2; }
        setSize(width, height);
    }

    @Override public void render(HudRenderer renderer) {
        double y = this.y;
        var color = GuiThemes.get().textColor();
        for (String line : lines()) { renderer.text(line, this.x, y, color, true); y += renderer.textHeight(true) + 2; }
    }
}
