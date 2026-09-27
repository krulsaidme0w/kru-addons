package dev.krul.cobblemon.pcui;

import dev.krul.cobblemon.gui.overlay.GuiScissor;
import dev.krul.cobblemon.gui.overlay.OverlayCanvas;
import dev.krul.cobblemon.gui.overlay.OverlayContent;
import dev.krul.cobblemon.gui.overlay.OverlayHost;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.text.Text;
import java.util.List;

/** One compact contextual utility surface for native containers and Cobblemon PC. */
public final class UiToolsContent implements OverlayContent {
    private static final int WIDTH = 310, FIELD_X = 8, FIELD_Y = 8, FIELD_WIDTH = 236, FIELD_HEIGHT = 20;
    private final MenuAccess access = new MenuAccess();
    private final UiToolsState state = new UiToolsState(this::send);
    private final UiMenuSession session = new UiMenuSession();
    private final UiSessionSignals signals = UiSessionSignals.shared();
    private final ManualPcActions pc = new ManualPcActions();
    private final SavedGuiController saved = new SavedGuiController();
    private final UiPacketTools packets = UiPacketTools.shared();
    private boolean advanced;
    private Screen debugScreen;
    public void configure(java.util.function.BooleanSupplier active,
                          java.util.function.Supplier<meteordevelopment.meteorclient.utils.misc.Keybind> loadKey) {
        packets.configure(active); saved.configure(loadKey);
    }
    private final CommandLine.Clipboard clipboard = new CommandLine.Clipboard() {
        public String get() { return client().keyboard.getClipboard(); }
        public void set(String value) { client().keyboard.setClipboard(value); }
    };
    private MenuAccess.Current displayed;
    private String status = "";
    private int textOffset;
    private boolean pcMode;
    private long acceptedPcRevision;
    private static MinecraftClient client() { return MinecraftClient.getInstance(); }
    @Override public String title() { return "UI Utils"; }
    @Override public int width() { return WIDTH; }
    @Override public int height() { return UiToolsLayout.height(advanced, pcMode); }
    @Override public boolean contextual() { return true; }
    @Override public Object contextKey() {
        refresh();
        return session.scope();
    }
    @Override public void tick() {
        boolean loaded = saved.tick();
        if (loaded) { session.reset(); debugScreen = client().currentScreen; }
        refresh();
        if (loaded) status = "Saved client GUI loaded; server session may be closed";
        boolean pcPending = pc.busy();
        pc.tick();
        if (pcPending) status = pc.status();
        refresh();
    }
    private Object nativeScreen() {
        return OverlayHost.isInteractionScreen(client().currentScreen) ? null : client().currentScreen;
    }
    private void refresh() {
        var mc = client();
        if (mc.world == null || mc.player == null || mc.getNetworkHandler() == null) { reset(); return; }
        var candidate = access.current();
        if (candidate != null && candidate.adapter() instanceof PcMenuAdapter && !pc.live(candidate.screen())
            && candidate.screen() != debugScreen && !saved.isRestored(candidate.screen())) candidate = null;
        Object screen = nativeScreen();
        boolean live = displayed != null && displayed.adapter().target(mc, displayed.screen()) == displayed.scope().target()
            && (!(displayed.adapter() instanceof PcMenuAdapter) || pc.live(displayed.screen())
                || displayed.screen() == debugScreen || saved.isRestored(displayed.screen()));
        long pcRevision = signals.pcRevision();
        long revision = pcRevision + signals.containerRevision();
        var before = session.scope();
        var next = session.observe(mc.world, mc.getNetworkHandler(), screen,
            candidate == null ? null : candidate.scope(), live, false,
            screen == debugScreen ? session.revision() : revision, pcRevision == acceptedPcRevision);
        if (!java.util.Objects.equals(before, next)) {
            pc.reset(); status = ""; textOffset = 0;
            acceptedPcRevision = pcRevision;
        }
        if (next == null) displayed = null;
        else if (candidate != null && next.equals(candidate.scope())) displayed = candidate;
        state.update(next);
        pcMode = displayed != null && displayed.adapter() instanceof PcMenuAdapter;
    }
    @Override public void render(OverlayCanvas c, double mx, double my) {
        if (displayed == null) return;
        renderField(c);
        for (var control : UiToolsLayout.controls(advanced, pcMode)) {
            if (enabled(control.action())) c.button(label(control.action()), control.x(), control.y(), control.width(), 20, control.contains(mx, my));
            else { c.fill(control.x(), control.y(), control.width(), 20, c.border());
                c.textFit(label(control.action()), control.x() + 5, control.y() + 6, control.width() - 10, c.muted()); }
        }
        String ids = displayed.screen() instanceof HandledScreen<?> handled
            ? "Sync " + handled.getScreenHandler().syncId + " | Rev " + handled.getScreenHandler().getRevision()
            : "Cobblemon PC packets";
        c.textFit(ids + " | Queued " + packets.queued(), 8, 116, WIDTH - 16, c.muted());
        String detail = status.isEmpty()
            ? (displayed.screen() == debugScreen ? "Client GUI restored/desynced | " : session.detached() ? "Menu hidden | " : "") + displayed.adapter().describe(displayed.screen())
            : status;
        c.textFit(detail, 8, height() - 17, WIDTH - 16, c.muted());
    }
    private String label(UiToolsLayout.Action action) {
        return switch (action) {
            case SEND -> "Send"; case COPY -> "Copy Title JSON"; case CLOSE -> "Close without packet";
            case SAVE -> "Save GUI"; case LOAD -> "Load GUI (" + saved.keyName() + ")";
            case PACKETS -> "Send packets: " + packets.sendEnabled(); case DELAY -> "Delay packets: " + packets.delayEnabled();
            case MORE -> advanced ? "Advanced -" : "Advanced +";
            case DESYNC -> "De-sync"; case EXIT -> "Close normally"; case FLUSH -> "Send queued";
            case CLEAR -> "Discard queued"; case FABRICATE -> "Fabricate Packet"; case DISCONNECT -> "Send + disconnect";
            case PC_IN -> "PC 1st In"; case PC_OUT -> "PC 1st Out";
        };
    }
    private boolean enabled(UiToolsLayout.Action action) {
        return switch (action) {
            case LOAD -> saved.available() && !pc.busy();
            case FABRICATE -> displayed != null && UiPacketTools.supports(displayed.screen()) && !session.detached();
            case FLUSH, CLEAR -> packets.queued() > 0;
            case CLOSE -> displayed != null && displayed.adapter().canCloseLocal(displayed.screen());
            case PC_IN, PC_OUT -> !pc.busy() && packets.sendEnabled() && !packets.delayEnabled() && displayed != null && displayed.screen() != debugScreen;
            default -> true;
        };
    }
    private void renderField(OverlayCanvas c) {
        var line = state.line;
        c.fill(FIELD_X, FIELD_Y, FIELD_WIDTH, FIELD_HEIGHT, c.background());
        c.outline(FIELD_X, FIELD_Y, FIELD_WIDTH, FIELD_HEIGHT, line.focused() ? c.accent() : c.border());
        c.flush();
        int left = FIELD_X + 4, top = FIELD_Y + 5, available = FIELD_WIDTH - 8;
        int caret = OverlayCanvas.textWidth(line.text().substring(0, line.cursor()));
        textOffset = Math.clamp(textOffset, Math.max(0, caret - available + 2), Math.max(0, caret));
        GuiScissor.enable(c.context(), left, FIELD_Y + 1, left + available, FIELD_Y + FIELD_HEIGHT - 1);
        try {
            if (line.selectionStart() != line.selectionEnd() && line.focused()) {
                int start = OverlayCanvas.textWidth(line.text().substring(0, line.selectionStart()));
                int end = OverlayCanvas.textWidth(line.text().substring(0, line.selectionEnd()));
                c.fill(left + start - textOffset, top - 1, end - start, 12, (c.accent() & 0xFFFFFF) | 0x66000000);
            }
            if (line.text().isEmpty()) c.text("Chat or /command", left, top, c.muted());
            else c.text(line.text(), left - textOffset, top, c.text());
            if (line.focused()) c.fill(left + caret - textOffset, top - 1, 1, 12, c.text());
            c.flush();
        } finally { c.context().disableScissor(); }
    }
    @Override public boolean click(double x, double y, int button) {
        if (button != 0) return false;
        if (!validCurrent()) { state.line.blur(); return true; }
        if (inside(x, y, FIELD_X, FIELD_Y, FIELD_WIDTH, FIELD_HEIGHT)) {
            state.line.focus();
            double point = x - FIELD_X - 4 + textOffset;
            String text = state.line.text();
            int position = 0;
            while (position < text.length()) {
                int next = text.offsetByCodePoints(position, 1);
                double middle = (OverlayCanvas.textWidth(text.substring(0, position)) + OverlayCanvas.textWidth(text.substring(0, next))) / 2.0;
                if (point < middle) break;
                position = next;
            }
            state.line.placeCursor(position, Screen.hasShiftDown());
            return true;
        }
        state.line.blur();
        for (var control : UiToolsLayout.controls(advanced, pcMode)) if (control.contains(x, y) && enabled(control.action())) {
            try { perform(control.action()); }
            catch (RuntimeException failure) { status = "Action failed: " + failure.getMessage(); }
            break;
        }
        return true;
    }
    private void perform(UiToolsLayout.Action operation) {
        switch (operation) {
            case SEND -> state.line.submit();
            case COPY -> action(current -> {
                client().keyboard.setClipboard(Text.Serialization.toJsonString(current.screen().getTitle(), client().world.getRegistryManager()));
                status = "GUI title JSON copied";
            });
            case CLOSE -> closeLocal();
            case SAVE -> action(current -> { saved.save(current.screen()); status = "GUI saved; " + saved.keyName() + " restores it while playing"; });
            case LOAD -> { if (saved.load()) { session.reset(); debugScreen = client().currentScreen; refresh(); status = "Saved client GUI loaded; server session may be closed"; } }
            case PACKETS -> packets.setSendEnabled(!packets.sendEnabled());
            case DELAY -> packets.setDelayEnabled(!packets.delayEnabled());
            case MORE -> advanced = !advanced;
            case DESYNC -> action(current -> { String result = packets.desync(current.screen()); debugScreen = current.screen(); session.reset(); refresh(); status = result; });
            case EXIT -> exitSession();
            case FLUSH -> { packets.flush(); status = "Queued packets sent"; }
            case CLEAR -> { packets.clear(); status = "Queued packets discarded"; }
            case FABRICATE -> action(current -> packets.openFabricator(current.screen()));
            case DISCONNECT -> packets.disconnectAndSend();
            case PC_IN -> transfer(true);
            case PC_OUT -> transfer(false);
        }
    }
    private boolean validCurrent() {
        var expected = state.scope();
        refresh();
        if (expected == null || !session.matches(expected) || displayed == null) {
            status = "Menu changed; try again";
            return false;
        }
        return true;
    }
    private boolean action(java.util.function.Consumer<MenuAccess.Current> operation) {
        if (!validCurrent()) return false;
        operation.accept(displayed);
        return true;
    }
    private boolean send(String command) {
        if (pc.busy()) { status = "Wait for the pending action, or Exit"; return false; }
        return action(current -> {
            if (command.startsWith("/")) client().getNetworkHandler().sendChatCommand(command.substring(1));
            else client().getNetworkHandler().sendChatMessage(command);
            status = "Sent";
        });
    }
    private void closeLocal() {
        if (pc.busy()) { status = "Wait for the pending action, or Exit"; return; }
        action(current -> {
            if (session.detached()) { status = "Menu already hidden"; return; }
            if (!current.adapter().canCloseLocal(current.screen())) { status = "Local close unavailable for this screen"; return; }
            // Arm before native removal; do not restore a screen if the operation fails.
            if (!session.detach(current.scope())) return;
            if (!current.adapter().close(current.screen(), true)) {
                session.reset(); displayed = null; status = "Local close failed; reopen menu";
            } else status = "Menu hidden; Exit ends this session";
        });
    }
    private void exitSession() {
        action(current -> {
            pc.reset();
            current.adapter().endSession(current.screen());
            session.exit(nativeScreen());
            displayed = null; state.reset(); status = "";
            pcMode = false;
        });
    }
    private boolean sameSession(UiActionScope expected) {
        return session.matches(expected) && client().world == expected.world()
            && client().getNetworkHandler() == expected.connection()
            && signals.pcRevision() == acceptedPcRevision
            && acceptedPcRevision + signals.containerRevision() == session.revision();
    }
    private void transfer(boolean into) {
        if (pc.busy() || !pcMode) return;
        action(current -> {
            var expected = current.scope();
            if (pcMode) status = pc.request(current.screen(), into, () -> {
                refresh(); return sameSession(expected);
            });
        });
    }
    @Override public boolean textFocused() { return state.line.focused(); }
    @Override public void blur() { state.line.blur(); }
    @Override public boolean key(int key, int mods) {
        if (!validCurrent()) { blur(); return false; }
        return state.line.key(key, mods, clipboard);
    }
    @Override public boolean charTyped(int codePoint, int mods) {
        if (!validCurrent() || !state.line.focused()) { blur(); return false; }
        if (Character.isValidCodePoint(codePoint)) state.line.insert(new String(Character.toChars(codePoint)));
        return true;
    }
    @Override public List<String> tooltip(double x, double y) {
        if (displayed == null) return List.of();
        if (inside(x, y, FIELD_X, FIELD_Y, FIELD_WIDTH, FIELD_HEIGHT)) return List.of("Enter or Send: /command runs a command; other text is chat", "Typing stays in this field, not the menu underneath");
        for (var control : UiToolsLayout.controls(advanced, pcMode)) if (control.contains(x, y)) return switch (control.action()) {
            case SAVE, LOAD -> List.of("Save/restore the actual client screen and its handler", "Hotkey: " + saved.keyName() + " while no other screen is open", "Does not reopen the server session", "Cleared on disconnect, world change or module disable");
            case CLOSE -> List.of("Hide locally without intentionally closing the server container", "Save first to restore with " + saved.keyName());
            case DESYNC -> List.of("Tell the server to close this container; keep the client window", "Server may reject later actions or close the window");
            case PACKETS, DELAY -> List.of("Inventory packets only; chat and connection traffic continue normally", "Delay ON buffers clicks; OFF sends the queue in order", "Queue is shared across menus in this connection", "Do not enable the separate Tools UI Utils packet delay at the same time");
            case FABRICATE -> UiPacketTools.supports(displayed.screen())
                ? List.of("Edit native Slot/Button packet fields and send explicitly")
                : List.of("PC uses Cobblemon packets, not vanilla Sync ID/Revision", "Vanilla packet fabrication is unavailable for PC");
            case PC_IN, PC_OUT -> List.of("One Pokemon per request; wait for server confirmation", "Disabled while packets are blocked/delayed or this is a restored GUI");
            case DISCONNECT -> List.of("Send the current queue, then disconnect", "Packet delivery is not proof that the server applied it");
            default -> List.of(label(control.action()));
        };
        if (y >= height() - 23 && y < height()) return status.isBlank()
            ? List.of(displayed.screen().getTitle().getString(), displayed.adapter().describe(displayed.screen()))
            : List.of(status, displayed.screen().getTitle().getString(), displayed.adapter().describe(displayed.screen()));
        return List.of();
    }
    @Override public void reset() { packets.reset(); saved.clear(); session.reset(); pc.reset(); state.reset(); displayed = null; debugScreen = null; status = ""; textOffset = 0; pcMode = false; }
    private static boolean inside(double x, double y, int left, int top, int width, int height) {
        return x >= left && y >= top && x < left + width && y < top + height;
    }
}
