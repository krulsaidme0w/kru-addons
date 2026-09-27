package dev.krul.cobblemon.pcui;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import meteordevelopment.meteorclient.gui.GuiThemes;
import meteordevelopment.meteorclient.gui.WindowScreen;
import meteordevelopment.meteorclient.gui.widgets.WLabel;
import meteordevelopment.meteorclient.gui.widgets.input.WDropdown;
import meteordevelopment.meteorclient.gui.widgets.input.WTextBox;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.c2s.play.ButtonClickC2SPacket;
import net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import java.util.ArrayList;
import static meteordevelopment.meteorclient.MeteorClient.mc;

/** Explicit sends only. The retained handler supplies local prediction, including restored saved GUIs. */
public final class PacketFabricatorScreen extends WindowScreen {
    private final Screen source;
    private final ScreenHandler handler;
    private final ClientConnection connection;
    private final Object world, player;
    private WTextBox sync, revision, slot, button, buttonId, repeat;
    private WDropdown<SlotActionType> action;
    private WLabel status;

    public PacketFabricatorScreen(Screen parent) {
        super(GuiThemes.get(), "Fabricate Packet");
        if (!(parent instanceof HandledScreen<?> handled)) throw new IllegalArgumentException("A handled screen is required");
        this.source = parent;
        this.parent = parent;
        handler = handled.getScreenHandler();
        connection = mc.getNetworkHandler() == null ? null : mc.getNetworkHandler().getConnection();
        world = mc.world;
        player = mc.player;
    }
    public Screen parentScreen() { return source; }

    @Override public void initWidgets() {
        add(theme.label("Manual inventory packets · sends obey Send / Delay controls"));
        sync = field("Sync ID", handler.syncId);
        revision = field("Revision", handler.getRevision());
        slot = field("Slot (-999 = outside)", 0);
        button = field("Slot button", 0);
        var actionRow = add(theme.horizontalList()).expandX().widget();
        actionRow.add(theme.label("Action")).expandX();
        action = actionRow.add(theme.dropdown(SlotActionType.PICKUP)).widget();
        buttonId = field("Button ID", 0);
        repeat = field("Repeat (1–100)", 1);
        var buttons = add(theme.horizontalList()).expandX().widget();
        buttons.add(theme.button("Slot")).expandX().widget().action = () -> submit(true);
        buttons.add(theme.button("Button")).expandX().widget().action = () -> submit(false);
        buttons.add(theme.button("Back")).expandX().widget().action = this::close;
        status = add(theme.label("Nothing sent")).expandX().widget();
    }
    private WTextBox field(String label, int value) {
        var row = add(theme.horizontalList()).expandX().widget();
        row.add(theme.label(label)).expandX();
        return row.add(theme.textBox(Integer.toString(value))).minWidth(120).widget();
    }
    private boolean sameContext() {
        return mc.world == world && mc.player == player && mc.getNetworkHandler() != null
            && mc.getNetworkHandler().getConnection() == connection && connection != null && connection.isOpen();
    }
    @Override public void close() {
        // Meteor returns locally to parent; no interaction-manager open/close request.
        // A disconnected/replaced world must never resurrect the retained handler.
        if (!sameContext() || !UiPacketTools.shared().available()) parent = null;
        super.close();
    }
    @Override protected void runAfterRenderTasks() {
        // Meteor's close schedules setScreen(parent) here, not inside close().
        if (mc.currentScreen != this) return;
        if (!sameContext() || !UiPacketTools.shared().available()) parent = null;
        UiPacketTools.shared().restoreLocally(super::runAfterRenderTasks);
    }
    private void submit(boolean slotPacket) {
        if (!mc.isOnThread() || !sameContext() || mc.currentScreen != this || !UiPacketTools.shared().available()) {
            status.set("Connection or module changed; no packet sent"); return;
        }
        try {
            Fields fields = slotPacket
                ? Fields.slot(sync.get(), revision.get(), slot.get(), button.get(), action.get().name(), repeat.get(), handler.slots.size())
                : Fields.button(sync.get(), buttonId.get(), repeat.get());
            for (int i = 0; i < fields.repeat(); i++) {
                if (!sameContext() || !UiPacketTools.shared().available()) break;
                if (slotPacket) sendSlot(fields);
                else connection.send(new ButtonClickC2SPacket(fields.syncId(), fields.button()));
            }
            status.set("Submitted " + fields.repeat() + " · queued " + UiPacketTools.shared().queued()
                + (UiPacketTools.shared().sendEnabled() ? "" : " · Send OFF (blocked)"));
        } catch (IllegalArgumentException invalid) {
            status.set(invalid.getMessage());
        } catch (RuntimeException failed) {
            status.set("Stopped: " + failed.getClass().getSimpleName() + "; already submitted packets are not retried");
        }
    }
    private void sendSlot(Fields fields) {
        var before = new ArrayList<ItemStack>(handler.slots.size());
        for (var s : handler.slots) before.add(s.getStack().copy());
        SlotActionType type = SlotActionType.valueOf(fields.action());
        handler.onSlotClick(fields.slot(), fields.button(), type, mc.player);
        var changed = new Int2ObjectOpenHashMap<ItemStack>();
        for (int i = 0; i < handler.slots.size(); i++) {
            ItemStack after = handler.slots.get(i).getStack();
            if (!ItemStack.areEqual(before.get(i), after)) changed.put(i, after.copy());
        }
        // One normal send path. Preserve editable revision instead of silently replacing it.
        connection.send(new ClickSlotC2SPacket(fields.syncId(), fields.revision(), fields.slot(), fields.button(),
            type, handler.getCursorStack().copy(), changed));
    }

    /** Pure parsing/validation kept independent of Minecraft bootstrap for regression tests. */
    public record Fields(int syncId, int revision, int slot, int button, String action, int repeat) {
        public static Fields slot(String sync, String revision, String slot, String button, String action, String repeat, int slots) {
            int s = number("Sync ID", sync, 0, 127);
            int r = number("Revision", revision, 0, Integer.MAX_VALUE);
            int index = number("Slot", slot, -999, Short.MAX_VALUE);
            if (index != -999 && (index < 0 || index >= slots)) throw new IllegalArgumentException("Slot is outside the retained handler");
            int b = number("Slot button", button, 0, 127);
            boolean valid = switch (action) {
                case "PICKUP", "QUICK_MOVE", "THROW", "PICKUP_ALL" -> b == 0 || b == 1;
                case "SWAP" -> b <= 8 || b == 40;
                case "CLONE" -> b == 2;
                case "QUICK_CRAFT" -> b == 0 || b == 1 || b == 2 || b == 4 || b == 5 || b == 6 || b == 8 || b == 9 || b == 10;
                default -> false;
            };
            if (!valid) throw new IllegalArgumentException("Invalid action / slot button combination");
            return new Fields(s, r, index, b, action, number("Repeat", repeat, 1, 100));
        }
        public static Fields button(String sync, String button, String repeat) {
            return new Fields(number("Sync ID", sync, 0, Integer.MAX_VALUE), 0, 0,
                number("Button ID", button, 0, Integer.MAX_VALUE), "", number("Repeat", repeat, 1, 100));
        }
        private static int number(String label, String input, int min, int max) {
            try {
                int value = Integer.parseInt(input.trim());
                if (value >= min && value <= max) return value;
            } catch (NumberFormatException | NullPointerException invalid) { /* report field, not parser internals */ }
            throw new IllegalArgumentException(label + " must be " + min + ".." + max);
        }
    }
}
