package dev.krul.cobblemon.gui;

import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.WindowScreen;

/** Normal Meteor window: Minecraft owns keyboard/mouse focus for its lifetime. */
public final class TypeChartScreen extends WindowScreen {
    public TypeChartScreen(GuiTheme theme) { super(theme, "Pokémon Type Chart"); }
    @Override public void initWidgets() {
        add(theme.label("Standard chart · attack rows · defence columns")).center();
        add(new WTypeMatrix()).center();
    }
}
