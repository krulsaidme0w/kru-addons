package dev.krul.cobblemon.pcui;

import dev.krul.cobblemon.gui.overlay.OverlayCanvas;
import dev.krul.cobblemon.gui.overlay.OverlayContent;
import net.minecraft.client.MinecraftClient;

import java.util.List;
import java.util.Objects;

/** PC-only UI Utils; the host owns placement, input routing and closing the overlay. */
public final class PcToolsContent implements OverlayContent {
    private final Runnable openChart;
    private final CobblemonPcAdapter adapter = new CobblemonPcAdapter();
    private final PcSessionScope scope = new PcSessionScope();
    private CobblemonPcAdapter.Context current;
    private boolean showSaved;
    private String status = "Save/Restore keeps a read-only facts view.";

    public PcToolsContent(Runnable openChart) { this.openChart = Objects.requireNonNull(openChart); }

    @Override public String title() { return "Cobblemon UI Utils"; }
    @Override public int width() { return 310; }
    @Override public int height() { return 224; }

    @Override public void tick() { refresh(); }

    private void refresh() {
        MinecraftClient mc = MinecraftClient.getInstance();
        current = mc.world != null && mc.getNetworkHandler() != null ? adapter.inspect(mc.currentScreen) : null;
        scope.observe(mc.world, mc.getNetworkHandler(), current == null ? null : current.screen(),
            current == null ? null : current.pc());
        if (showSaved && scope.restore(mc.world, mc.getNetworkHandler()).isEmpty()) {
            showSaved = false;
            status = "Saved view expired with its PC/session context.";
        }
    }

    private List<String> facts() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (showSaved) return scope.restore(mc.world, mc.getNetworkHandler())
            .map(PcSessionScope.View::facts).orElse(List.of("Saved view expired."));
        return current == null ? List.of("Open a Cobblemon PC to inspect its context.",
            "Other Pokemon screens are not supported yet.") : current.facts();
    }

    private boolean canSave() { return current != null && current.canSave(); }
    private boolean canRestore() {
        MinecraftClient mc = MinecraftClient.getInstance();
        return scope.restore(mc.world, mc.getNetworkHandler()).isPresent();
    }
    private boolean canCloseLocal() { return current != null && current.canCloseLocal(); }

    @Override public void render(OverlayCanvas canvas, double mouseX, double mouseY) {
        refresh();
        canvas.text(showSaved ? "Saved facts / read-only" : current == null ? "No supported PC screen" : "PC / client-known facts",
            8, 7, canvas.accent());
        canvas.text("Server link: unverified", 8, 21, canvas.muted());
        int y = 39;
        for (String fact : facts()) {
            canvas.textFit(fact, 8, y, width() - 16, canvas.text());
            y += 12;
        }
        button(canvas, showSaved ? "Save current PC" : "Save view", 8, 130, canSave(), mouseX, mouseY);
        button(canvas, "Restore view", 159, 130, canRestore(), mouseX, mouseY);
        button(canvas, "Close local", 8, 153, canCloseLocal(), mouseX, mouseY);
        button(canvas, "Desync unavailable", 159, 153, false, mouseX, mouseY);
        button(canvas, "Copy facts", 8, 176, current != null || showSaved, mouseX, mouseY);
        button(canvas, "Type Chart", 159, 176, true, mouseX, mouseY);

        String detail = status;
        String secondLine = "Restore never reopens PC or restores server access.";
        if (inside(mouseX, mouseY, 159, 153)) {
            detail = "Desync would unlink while leaving storage active.";
            secondLine = "Disabled: no verified guard for stale PC controls.";
        } else if (inside(mouseX, mouseY, 8, 153)) {
            detail = canCloseLocal() ? "Closes PC locally; does not send an unlink request."
                : "Requires a PC with closeNormally(boolean).";
            secondLine = "Server access remains unverified. Reopen PC normally.";
        } else if (inside(mouseX, mouseY, 159, 130) && !canRestore()) {
            detail = "No saved view for this world / connection / PC.";
        } else if (inside(mouseX, mouseY, 8, 130) && !canSave()) {
            detail = "Requires a PC with available PC and party readers.";
        }
        canvas.textFit(detail, 8, 202, width() - 16, canvas.muted());
        canvas.textFit(secondLine, 8, 214, width() - 16, canvas.muted());
    }

    private void button(OverlayCanvas canvas, String label, int x, int y, boolean enabled, double mouseX, double mouseY) {
        if (enabled) canvas.button(label, x, y, 143, 19, inside(mouseX, mouseY, x, y));
        else {
            canvas.fill(x, y, 143, 19, canvas.border());
            canvas.fill(x + 1, y + 1, 141, 17, canvas.background());
            canvas.textFit(label, x + 5, y + 5, 133, canvas.muted());
        }
    }

    private static boolean inside(double x, double y, int left, int top) {
        return x >= left && x < left + 143 && y >= top && y < top + 19;
    }

    @Override public boolean click(double x, double y, int button) {
        if (x < 0 || y < 0 || x >= width() || y >= height()) return false;
        // Consume non-left clicks and disabled controls too; never leak these into PC storage.
        if (button != 0) return true;
        refresh();
        MinecraftClient mc = MinecraftClient.getInstance();
        if (inside(x, y, 8, 130) && canSave()) {
            scope.save(mc.world, mc.getNetworkHandler(), current.screen(), current.pc(), current.facts());
            showSaved = false;
            status = "Saved client facts. Storage controls are not saved.";
        } else if (inside(x, y, 159, 130) && canRestore()) {
            showSaved = true;
            status = "Restored saved facts only; no server link requested.";
        } else if (inside(x, y, 8, 153) && canCloseLocal()) {
            // Check the live current screen at the action boundary, never invoke a saved Screen.
            boolean closed = adapter.closeLocal(mc.currentScreen);
            status = closed ? "PC closed locally. Server link remains unverified." : "Local close unavailable or failed.";
            refresh();
        } else if (inside(x, y, 8, 176) && (current != null || showSaved)) {
            mc.keyboard.setClipboard((showSaved ? "Saved PC facts (read-only)" : "Current PC client facts")
                + "\nServer link: unverified\n" + String.join("\n", facts()));
            status = "Client-known facts copied.";
        } else if (inside(x, y, 159, 176)) openChart.run();
        return true;
    }

    @Override public boolean scroll(double x, double y, double amount) {
        return x >= 0 && y >= 0 && x < width() && y < height();
    }

    @Override public void reset() {
        scope.reset();
        current = null;
        showSaved = false;
        status = "Save/Restore keeps a read-only facts view.";
    }
}
