package dev.krul.cobblemon.gui.overlay;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import java.util.*;
import static org.lwjgl.glfw.GLFW.*;

/** Single z-order, scale transform and input owner for all Cobblemon panels. */
public final class OverlayHost {
    private static final int BAR = 22;
    private static final List<OverlayModule> panels = new ArrayList<>();
    private static final PointerCapture pointer = new PointerCapture();
    private static final OverlayKeys ownedKeys = new OverlayKeys();
    private static OverlayModule focused, dragging;
    private static double dragX, dragY;
    private static Object world, connection;
    private static boolean initialized;
    private static final OverlayHost INSTANCE = new OverlayHost();
    private static MinecraftClient mc() { return MinecraftClient.getInstance(); }
    /** This cursor-only screen does not replace a retained native menu session. */
    public static boolean isInteractionScreen(Object screen) { return screen instanceof OverlayInteractionScreen; }
    public static void init() {
        if (initialized) return;
        initialized = true;
        MeteorClient.EVENT_BUS.subscribe(INSTANCE);
    }
    public static void register(OverlayModule panel) { panels.add(panel); }
    public static void front(OverlayModule panel) {
        if (focused != null && focused != panel) focused.content.blur();
        panels.remove(panel); panels.add(panel); focused = panel;
    }
    public static void closed(OverlayModule panel) {
        panel.content.blur();
        if (focused == panel) focused = null;
        if (dragging == panel) dragging = null;
    }
    private static boolean ready() {
        return initialized && mc().world != null && mc().player != null && mc().getOverlay() == null && !mc().options.hudHidden;
    }
    private static boolean shown(OverlayModule panel) {
        if (!panel.isActive()) return false;
        try { panel.refreshVisibility(); }
        catch (RuntimeException | LinkageError failure) { fail(panel, failure); return false; }
        return panel.visible;
    }
    private static OverlayGeometry geometry(OverlayModule p) {
        double x = p.panelX.get();
        if (x == -1) x = mc().getWindow().getScaledWidth() - p.content.width() * p.panelScale.get() - 8;
        return OverlayGeometry.fit(x, p.panelY.get(), p.content.width(),
            BAR + (p.collapsed.get() ? 0 : p.content.height()), p.panelScale.get(),
            mc().getWindow().getScaledWidth(), mc().getWindow().getScaledHeight());
    }
    private static boolean clipped(OverlayModule p, OverlayGeometry g) {
        return !p.collapsed.get() && (p.content.height() > g.height() - BAR || p.content.width() > g.width());
    }
    private static int bodyHeight(OverlayModule p, OverlayGeometry g) {
        return Math.max(1, (int) g.height() - BAR - (clipped(p, g) ? 13 : 0));
    }
    public static double mouseX() { return mc().mouse.getX() * mc().getWindow().getScaledWidth() / mc().getWindow().getWidth(); }
    public static double mouseY() { return mc().mouse.getY() * mc().getWindow().getScaledHeight() / mc().getWindow().getHeight(); }
    @EventHandler private void tick(TickEvent.Post event) {
        if (!mc().isWindowFocused()) {
            dragging = null;
            if (focused != null) focused.content.blur();
        }
        if (world != mc().world || connection != mc().getNetworkHandler()) {
            world = mc().world; connection = mc().getNetworkHandler();
            dragging = focused = null;
            for (var p : panels) { p.resetContext(); p.content.reset(); }
        }
        for (var p : List.copyOf(panels)) if (p.isActive()) {
            try { p.content.tick(); p.refreshVisibility(); }
            catch (RuntimeException | LinkageError failure) { fail(p, failure); }
        }
    }
    private static void fail(OverlayModule p, Throwable failure) {
        p.error("Overlay disabled: %s", failure.getClass().getSimpleName());
        if (p.isActive()) p.toggle();
    }
    public static void render() {
        if (!ready() || mc().options.hudHidden || panels.stream().noneMatch(OverlayHost::shown)) return;
        double mx = mouseX(), my = mouseY();
        if (dragging != null && shown(dragging)) {
            var g = geometry(dragging);
            var next = OverlayGeometry.fit(mx - dragX, my - dragY, g.width(), g.height(), g.scale(),
                mc().getWindow().getScaledWidth(), mc().getWindow().getScaledHeight());
            dragging.panelX.set((int) next.x()); dragging.panelY.set((int) next.y());
        }
        DrawContext context = new DrawContext(mc(), mc().getBufferBuilders().getEntityVertexConsumers());
        OverlayModule hovered = mc().currentScreen == null ? null : hit(mx, my);
        for (var p : List.copyOf(panels)) {
            if (!shown(p)) continue;
            var g = geometry(p);
            var matrices = context.getMatrices();
            matrices.push();
            try {
                matrices.translate(g.x(), g.y(), 400);
                matrices.scale((float) g.scale(), (float) g.scale(), 1);
                var c = new OverlayCanvas(context);
                int width = (int) g.width();
                c.fill(0, 0, width, (int) g.height(), c.background());
                c.outline(0, 0, width, (int) g.height(), c.border());
                c.fill(0, 0, width, BAR, c.accent());
                c.textFit(p.content.title(), 7, 6, width - 140, 0xFFFFFFFF);
                String[] labels = {"-", "+", "1:1", p.collapsed.get() ? "v" : "^", "x"};
                for (int i = 0; i < 5; i++) {
                    int x = width - 127 + i * 25;
                    c.button(labels[i], x, 3, 23, 16, hovered == p && g.localY(my) < BAR && g.localX(mx) >= x && g.localX(mx) < x + 23);
                }
                c.flush();
                if (!p.collapsed.get()) {
                    matrices.translate(0, BAR, 0);
                    int visibleHeight = bodyHeight(p, g);
                    p.scrollX = Math.clamp(p.scrollX, 0, Math.max(0, p.content.width() - g.width()));
                    p.scrollY = Math.clamp(p.scrollY, 0, Math.max(0, p.content.height() - visibleHeight));
                    GuiScissor.enable(context, 0, 0, width, visibleHeight);
                    matrices.push();
                    try {
                        matrices.translate(-p.scrollX, -p.scrollY, 0);
                        boolean insideBody = hovered == p && g.localY(my) >= BAR && g.localY(my) < BAR + visibleHeight;
                        var point = g.contentPoint(mx, my, BAR, p.scrollX, p.scrollY);
                        p.content.render(c, insideBody ? point.x() : -1, insideBody ? point.y() : -1);
                        c.flush();
                    } finally { context.draw(); matrices.pop(); context.disableScissor(); }
                    if (clipped(p, g)) {
                        c.fill(0, visibleHeight, width, 13, c.background());
                        c.textFit("Scroll panel | Shift + wheel: horizontal", 4, visibleHeight + 2, width - 8, c.muted());
                        c.flush();
                    }
                }
                context.draw();
            } catch (RuntimeException | LinkageError failure) { fail(p, failure); }
            finally { matrices.pop(); }
        }
        renderTooltip(context, hovered, mx, my);
        context.draw();
    }
    private static void renderTooltip(DrawContext context, OverlayModule hovered, double mx, double my) {
        if (hovered == null || !shown(hovered) || hovered.collapsed.get()) return;
        var g = geometry(hovered);
        double x = g.localX(mx), y = g.localY(my) - BAR;
        if (x < 0 || x >= g.width() || y < 0 || y >= bodyHeight(hovered, g)) return;
        try {
            var point = g.contentPoint(mx, my, BAR, hovered.scrollX, hovered.scrollY);
            var lines = hovered.content.tooltip(point.x(), point.y());
            if (lines.isEmpty()) return;
            lines = OverlayTooltip.wrap(lines, Math.max(1, Math.min(348, mc().getWindow().getScaledWidth() - 16)), OverlayCanvas::textWidth);
            int width = Math.min(360, lines.stream().mapToInt(OverlayCanvas::textWidth).max().orElse(0) + 12);
            var box = OverlayTooltip.place(mx, my, width, lines.size() * 13 + 8,
                mc().getWindow().getScaledWidth(), mc().getWindow().getScaledHeight());
            context.getMatrices().push();
            try {
                context.getMatrices().translate(0, 0, 600);
                var c = new OverlayCanvas(context);
                c.fill(box.x(), box.y(), box.width(), box.height(), c.background() | 0xFF000000);
                c.outline(box.x(), box.y(), box.width(), box.height(), c.border());
                for (int i = 0; i < lines.size() && 4 + i * 13 + 9 <= box.height(); i++)
                    c.textFit(lines.get(i), box.x() + 6, box.y() + 4 + i * 13, box.width() - 12, c.text());
                c.flush();
            } finally { context.getMatrices().pop(); }
        } catch (RuntimeException | LinkageError failure) { fail(hovered, failure); }
    }
    private static OverlayModule hit(double x, double y) {
        for (int i = panels.size() - 1; i >= 0; i--) {
            var p = panels.get(i);
            if (shown(p) && geometry(p).contains(x, y)) return p;
        }
        return null;
    }
    public static boolean mouseButton(int button, int action, int mods) {
        if (!initialized) return false;
        if (action == GLFW_RELEASE) {
            if (button == GLFW_MOUSE_BUTTON_LEFT) dragging = null;
            return pointer.release(button);
        }
        if (!ready() || action != GLFW_PRESS) return false;
        if (!textFocused() && !OverlayTextFocus.blocks(mc().currentScreen) && shortcut(false, button, mods)) {
            pointer.capture(button); return true;
        }
        if (mc().currentScreen == null) return false;
        var p = hit(mouseX(), mouseY());
        if (p == null) {
            if (focused != null) focused.content.blur();
            focused = null; return false;
        }
        pointer.capture(button);
        front(p);
        var g = geometry(p);
        double x = g.localX(mouseX()), y = g.localY(mouseY());
        if (button == GLFW_MOUSE_BUTTON_LEFT && y < BAR) {
            p.content.blur();
            int control = (int) ((x - (g.width() - 127)) / 25);
            if (x >= g.width() - 127 && control >= 0 && control < 5) {
                switch (control) {
                    case 0 -> p.panelScale.set(Math.max(.5, p.panelScale.get() - .1));
                    case 1 -> p.panelScale.set(Math.min(2, p.panelScale.get() + .1));
                    case 2 -> { p.panelScale.set(1.0); p.panelX.reset(); p.panelY.reset(); }
                    case 3 -> p.collapsed.set(!p.collapsed.get());
                    case 4 -> p.hidePanel();
                }
            } else {
                dragging = p; dragX = mouseX() - g.x(); dragY = mouseY() - g.y();
            }
        } else if (!p.collapsed.get() && y >= BAR && y < BAR + bodyHeight(p, g)) {
            var point = g.contentPoint(mouseX(), mouseY(), BAR, p.scrollX, p.scrollY);
            try { p.content.click(point.x(), point.y(), button); }
            catch (RuntimeException | LinkageError failure) { fail(p, failure); }
        }
        return true;
    }
    public static boolean scroll(double amount) {
        if (!ready() || mc().currentScreen == null) return false;
        var p = hit(mouseX(), mouseY());
        if (p == null) return false;
        var g = geometry(p);
        if (!p.collapsed.get()) {
            double maxX = Math.max(0, p.content.width() - g.width());
            double maxY = Math.max(0, p.content.height() - bodyHeight(p, g));
            if (clipped(p, g)) {
                if (net.minecraft.client.gui.screen.Screen.hasShiftDown() || maxY == 0)
                    p.scrollX = Math.clamp(p.scrollX - amount * 24, 0, maxX);
                else p.scrollY = Math.clamp(p.scrollY - amount * 24, 0, maxY);
            } else {
                var point = g.contentPoint(mouseX(), mouseY(), BAR, p.scrollX, p.scrollY);
                try { p.content.scroll(point.x(), point.y(), amount); }
                catch (RuntimeException | LinkageError failure) { fail(p, failure); }
            }
        }
        return true;
    }
    public static boolean key(int key, int action, int mods) {
        if (!initialized) return false;
        if (action == GLFW_RELEASE) return ownedKeys.release(key);
        if (ready() && textFocused()) {
            var owner = focused;
            try { if (ownedKeys.text(owner.content, key, action, mods)) return true; }
            catch (RuntimeException | LinkageError failure) { fail(owner, failure); ownedKeys.capture(key); return true; }
        }
        if (ownedKeys.held(key)) return true;
        if (!ready() || action != GLFW_PRESS || OverlayTextFocus.blocks(mc().currentScreen)) return false;
        if (shortcut(true, key, mods)) { ownedKeys.capture(key); return true; }
        if (mc().currentScreen != null && focused != null && shown(focused)) {
            var owner = focused;
            if (owner == null) return false;
            try {
                boolean used = owner.content.key(key, mods);
                if (!used && key == GLFW_KEY_ESCAPE) { owner.hidePanel(); used = true; }
                if (used) { ownedKeys.capture(key); return true; }
            } catch (RuntimeException | LinkageError failure) { fail(owner, failure); ownedKeys.capture(key); return true; }
        }
        return false;
    }
    private static boolean textFocused() {
        var owner = focused;
        return mc().currentScreen != null && owner != null && shown(owner) && !owner.collapsed.get() && owner.content.textFocused();
    }
    public static boolean charTyped(int codePoint, int mods) {
        if (!ready() || !textFocused()) return false;
        var owner = focused;
        try { owner.content.charTyped(codePoint, mods); }
        catch (RuntimeException | LinkageError failure) { fail(owner, failure); }
        return true;
    }
    private static boolean shortcut(boolean keyboard, int value, int mods) {
        for (var p : List.copyOf(panels)) {
            if (p.overlayKey.get().matches(keyboard, value, mods)) { p.togglePanel(); return true; }
        }
        for (var p : panels) if ((shown(p) || mc().currentScreen instanceof OverlayInteractionScreen)
            && p.interactKey.get().matches(keyboard, value, mods)) {
            if (mc().currentScreen == null) mc().setScreen(new OverlayInteractionScreen());
            else if (mc().currentScreen instanceof OverlayInteractionScreen) mc().setScreen(null);
            return true;
        }
        return false;
    }
}
