package dev.krul.cobblemon.hover;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import java.util.ArrayList;
import java.util.List;

/** Native small detail card with a restrained quality accent; never changes GUI state. */
public final class PokemonHoverRenderer {
    private static final int WIDTH = 224, ROW = 10;
    private PokemonHoverRenderer() {}

    public static void render(DrawContext context, PokemonHoverDetails details, HoverCardLayout.Rect slot,
                              int mouseX, int mouseY, int viewportWidth, int viewportHeight) {
        List<String> rows = rows(details);
        int visible = Math.max(1, (viewportHeight - 16) / ROW - 2);
        if (rows.size() > visible) rows = new ArrayList<>(rows.subList(0, visible));
        int height = 9 + (rows.size() + 1) * ROW + 5;
        var card = HoverCardLayout.place(mouseX, mouseY, WIDTH, height, viewportWidth, viewportHeight, slot);
        int tint = tint(details.ivTier());
        var matrices = context.getMatrices();
        matrices.push();
        matrices.translate(0, 0, 450);
        try {
        if (slot != null && tint != 0) context.fill((int) slot.x(), (int) slot.y(), (int) (slot.x() + slot.width()),
            (int) (slot.y() + slot.height()), tint);
        int x = (int) card.x(), y = (int) card.y(), right = x + (int) card.width(), bottom = y + (int) card.height();
        context.fill(x, y, right, bottom, 0xE812151B);
        context.fill(x, y, right, y + 2, tint == 0 ? 0xFF66717F : tint | 0xFF000000);
        context.fill(x, bottom - 1, right, bottom, 0xFF3A414B);
        var font = MinecraftClient.getInstance().textRenderer;
        draw(context, font.trimToWidth(details.displayName() + (details.level() > 0 ? "  Lv. " + details.level() : ""), WIDTH - 14), x + 7, y + 6, 0xFFFFFFFF);
        int textY = y + 6;
        for (String row : rows) { textY += ROW; draw(context, font.trimToWidth(row, WIDTH - 14), x + 7, textY, row.startsWith("IVs") ? 0xFFDCE6F5 : 0xFFB8C0CC); }
        } finally { matrices.pop(); }
    }

    private static List<String> rows(PokemonHoverDetails d) {
        List<String> rows = new ArrayList<>();
        if (!d.species().equalsIgnoreCase(d.displayName())) rows.add("Species: " + d.species());
        if (!d.form().isBlank()) rows.add("Form: " + d.form());
        if (!d.gender().isBlank() || d.shiny()) rows.add((d.gender().isBlank() ? "" : d.gender()) + (d.shiny() ? " · Shiny" : ""));
        if (!d.types().isEmpty()) rows.add("Type: " + String.join(" / ", d.types()));
        if (!d.teraType().isBlank()) rows.add("Tera: " + d.teraType());
        if (!d.ability().isBlank()) rows.add("Ability: " + d.ability());
        if (!d.nature().isBlank()) rows.add("Nature: " + d.nature());
        if (!d.heldItem().isBlank()) rows.add("Held item: " + d.heldItem());
        if (!d.caughtBall().isBlank()) rows.add("Caught ball: " + d.caughtBall());
        if (!d.size().isBlank()) rows.add("Size: " + d.size());
        if (!d.skin().isBlank()) rows.add("Skin: " + d.skin());
        stats(rows, "IVs", d.ivs()); stats(rows, "EVs", d.evs());
        if (!d.moves().isEmpty()) rows.add("Moves: " + String.join(" · ", d.moves()));
        return rows;
    }
    private static void stats(List<String> rows, String label, List<Integer> values) {
        if (values.size() != 6) return;
        String[] names = {"HP", "ATK", "DEF", "SpA", "SpD", "SPE"};
        for (int start = 0; start < 6; start += 3) {
            StringBuilder line = new StringBuilder(start == 0 ? label + "  " : "     ");
            for (int i = start; i < start + 3; i++) { if (i > start) line.append("  "); line.append(names[i]).append(' ').append(values.get(i)); }
            rows.add(line.toString());
        }
    }
    private static int tint(IvTintTier tier) {
        return switch (tier) { case NONE -> 0; case IV_60 -> 0x383DA77A; case IV_80 -> 0x403F9FBC; case IV_90 -> 0x484A7CC3; case IV_95 -> 0x50C49745; };
    }
    private static void draw(DrawContext context, String text, int x, int y, int color) {
        context.drawTextWithShadow(MinecraftClient.getInstance().textRenderer, Text.literal(text), x, y, color);
    }
}
