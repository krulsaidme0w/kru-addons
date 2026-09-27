package dev.krul.cobblemon.gui;

import dev.krul.cobblemon.DungeonDiscovery;
import dev.krul.cobblemon.rules.ProfileDraft;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.GuiThemes;
import meteordevelopment.meteorclient.gui.WindowScreen;
import meteordevelopment.meteorclient.gui.widgets.containers.WHorizontalList;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import com.google.gson.JsonParser;
import com.google.gson.GsonBuilder;

/** Compact Meteor-native editor for one bounded profile document. */
public final class DiscoveryProfileScreen extends WindowScreen {
    private final DungeonDiscovery discovery;
    private WJsonEditor editor;
    private meteordevelopment.meteorclient.gui.widgets.WLabel status;

    public DiscoveryProfileScreen(DungeonDiscovery discovery) {
        super(GuiThemes.get(), "Discovery Profile · " + discovery.profileFileName());
        this.discovery = discovery;
    }

    @Override public void initWidgets() {
        add(theme.label("JSON profile · Ctrl+A / Ctrl+C / Ctrl+V · mouse wheel to scroll")).expandX();
        editor = add(new WJsonEditor(pretty(discovery.profileJson()))).expandX().widget();
        status = add(theme.label("Ready")).expandX().widget();
        WHorizontalList actions = theme.horizontalList();
        WButton validate = actions.add(theme.button("Validate")).expandX().widget();
        validate.action = () -> validate(false);
        WButton save = actions.add(theme.button("Save + apply")).expandX().widget();
        save.action = () -> validate(true);
        WButton close = actions.add(theme.button("Close")).expandX().widget();
        close.action = this::closeScreen;
        add(actions).expandX();
    }

    private void validate(boolean save) {
        ProfileDraft draft = ProfileDraft.parse(editor.get());
        if (!draft.valid()) {
            status.set("Invalid: " + String.join("; ", draft.diagnostics()));
            return;
        }
        if (!save) {
            status.set("Valid v" + draft.validate().sourceVersion() + " · " + draft.validate().size() + " rules");
            return;
        }
        String result = discovery.saveProfile(draft);
        status.set(result);
    }

    private void closeScreen() {
        meteordevelopment.meteorclient.MeteorClient.mc.setScreen(null);
    }

    private static String pretty(String json) {
        try { return new GsonBuilder().setPrettyPrinting().create().toJson(JsonParser.parseString(json)); }
        catch (RuntimeException ignored) { return json == null ? "" : json; }
    }
}
