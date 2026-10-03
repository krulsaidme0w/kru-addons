package dev.krul.cobblemon;

import dev.krul.cobblemon.gui.overlay.OverlayHost;
import dev.krul.cobblemon.hover.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;

/** Independent read-only PC/GTS hover inspector, exposed in the Cobblemon module category. */
public final class PokemonHoverInspector extends Module {
    private final PcHoverReader pc = new PcHoverReader();
    private final PokemonHoverReader pokemon = new PokemonHoverReader();
    private final GtsHoverReader gts = new GtsHoverReader();

    public PokemonHoverInspector() {
        super(CobblemonAddon.CATEGORY, "pokemon-hover-inspector",
            "Show Pokémon details and subtle IV quality tint when hovering PC or GTS listings.");
        autoSubscribe = false;
    }

    public static void renderActive() {
        Modules modules = Modules.get();
        if (modules == null) return;
        PokemonHoverInspector inspector = modules.get(PokemonHoverInspector.class);
        if (inspector == null || !inspector.isActive()) return;
        inspector.render(MinecraftClient.getInstance());
    }

    private void render(MinecraftClient client) {
        Screen screen = client.currentScreen;
        if (screen == null || client.player == null || client.world == null) return;
        int mouseX = (int) OverlayHost.mouseX(), mouseY = (int) OverlayHost.mouseY();
        DrawContext context = new DrawContext(client, client.getBufferBuilders().getEntityVertexConsumers());
        if (screen instanceof HandledScreen<?> handled) {
            gts.readAt(handled, mouseX, mouseY).ifPresent(hover -> PokemonHoverRenderer.render(context, hover.details(), hover.bounds(),
                mouseX, mouseY, screen.width, screen.height));
        } else {
            pc.read(screen, mouseX, mouseY).ifPresent(hover -> {
                PokemonHoverDetails details = pokemon.read(hover.pokemon());
                if (details != null) PokemonHoverRenderer.render(context, details, hover.bounds(), mouseX, mouseY, screen.width, screen.height);
            });
        }
        context.draw();
    }
}
