package dev.krul.cobblemon.hud;

import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.systems.modules.Modules;
import dev.krul.cobblemon.Discovery;
import dev.krul.cobblemon.discovery.DiscoveryText;
import dev.krul.cobblemon.discovery.PokemonObservation;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;

import java.util.ArrayList;
import java.util.List;

public class CobblemonDiscoveryHud extends HudElement {
    public static final HudElementInfo<CobblemonDiscoveryHud> INFO = new HudElementInfo<>(
        Hud.GROUP, "cobblemon-discovery", "Displays recently discovered Cobblemon.", CobblemonDiscoveryHud::new
    );

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgDisplay = settings.createGroup("Display");

    private final Setting<Integer> limit = sgGeneral.add(new IntSetting.Builder()
        .name("limit").description("Maximum observations to display.").defaultValue(5).range(1, 16).sliderRange(1, 10).build());
    private final Setting<Boolean> showRarity = sgDisplay.add(new BoolSetting.Builder()
        .name("rarity").description("Show the rarity.").defaultValue(true).build());
    private final Setting<Boolean> shadow = sgDisplay.add(new BoolSetting.Builder()
        .name("shadow").description("Render text shadows.").defaultValue(true).build());
    private final Setting<Boolean> background = sgDisplay.add(new BoolSetting.Builder()
        .name("background").description("Render a background behind the feed.").defaultValue(true).build());
    private final Setting<SettingColor> backgroundColor = sgDisplay.add(new ColorSetting.Builder()
        .name("background-color").description("Background color.").defaultValue(new SettingColor(0, 0, 0, 100)).visible(background::get).build());

    public CobblemonDiscoveryHud() {
        super(INFO);
    }

    @Override
    public void tick(HudRenderer renderer) {
        List<String> lines = lines();
        double width = 0;
        double height = renderer.textHeight(shadow.get());
        for (String line : lines) {
            width = Math.max(width, renderer.textWidth(line, shadow.get()));
            height += renderer.textHeight(shadow.get()) + 2;
        }
        setSize(Math.max(width, renderer.textWidth("Discovery", shadow.get())), height);
    }

    @Override
    public void render(HudRenderer renderer) {
        List<String> lines = lines();
        if (lines.isEmpty()) return;
        if (background.get()) renderer.quad(x, y, getWidth(), getHeight(), backgroundColor.get());

        double lineHeight = renderer.textHeight(shadow.get());
        double y = this.y;
        renderer.text("Discovery", this.x, y, new Color(255, 255, 255, 255), shadow.get());
        y += lineHeight + 2;

        for (String line : lines) {
            renderer.text(line, this.x, y, new Color(230, 230, 230, 255), shadow.get());
            y += lineHeight + 2;
        }
    }

    private List<String> lines() {
        Discovery module = Modules.get().get(Discovery.class);
        if (module == null || !module.isActive()) return List.of();

        List<String> lines = new ArrayList<>();
        for (PokemonObservation observation : module.visibleObservations().stream().limit(limit.get()).toList()) {
            lines.addAll(DiscoveryText.lines(observation, showRarity.get()));
        }
        return lines;
    }
}

