package dev.krul.cobblemon.pcui.daycare;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.component.ComponentType;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import java.util.*;
import static dev.krul.cobblemon.pcui.daycare.DaycareReflection.get;

/** Native boundary, freshly checked before every semantic dispatch. Never opens a menu. */
final class NativeDaycarePort implements DaycarePort {
    static final String PICKER = "com.cobblemon.mod.common.client.gui.interact.partyselect.PartySelectGUI";
    static final String DIALOGUE = "com.cobblemon.mod.common.client.gui.dialogue.DialogueScreen";
    private static final String OVERLAY = "dev.krul.cobblemon.gui.overlay.OverlayInteractionScreen";
    private final AcademyDaycareProfile profile = AcademyDaycareProfile.load();
    private Object root;
    private boolean inserting;

    @Override public Frame read(Object retainedRoot) {
        Frame f = inspect(retainedRoot);
        if (!f.live()) return f;
        Storage storage;
        try {
            Object client = DaycareReflection.singleton("com.cobblemon.mod.common.client.CobblemonClient");
            storage = DaycareStorageReader.read(get(client, "getStorage"));
        } catch (RuntimeException | LinkageError unavailable) { storage = null; }
        return new Frame(f.screen(), f.handler(), f.kind(), f.pen(), f.left(), f.card(),
            f.rightFingerprint(), f.live(), storage, f.transitionIdentity());
    }

    @Override public Frame inspect(Object retainedRoot) {
        MinecraftClient mc = MinecraftClient.getInstance();
        Object screen = normalized(mc.currentScreen);
        if (mc.player == null || mc.world == null || mc.getNetworkHandler() == null)
            return unavailable(screen, null, false);
        Object handler = mc.player.currentScreenHandler;
        if (!(retainedRoot instanceof GenericContainerScreen original)) return unavailable(screen, handler, false);
        GenericContainerScreen visible = screen instanceof GenericContainerScreen container ? container
            : screen == null && handler == original.getScreenHandler() ? original : null;
        if (visible != null) return rootFrame(mc, screen, visible);
        boolean live = handler == original.getScreenHandler() || handler == mc.player.playerScreenHandler;
        if (!live) return unavailable(screen, handler, false);
        if (screen == null) return new Frame(null, handler, Kind.GAP, -1, Role.UNKNOWN, null, "", true, null);
        try {
            if (screen.getClass().getName().equals(PICKER)
                && profile.pickerTitle(text(get(get(screen, "getConfig"), "getTitle"))))
                return transition(screen, handler, Kind.PICKER);
            if (screen.getClass().getName().equals(DIALOGUE) && retainedRoot == root) {
                Dialogue input = dialogue(screen, inserting);
                if (input != null) return new Frame(screen, handler, Kind.CONFIRM, -1, Role.UNKNOWN,
                    null, "", true, null, input.id());
            }
        } catch (RuntimeException | LinkageError unavailable) { return unavailable(screen, handler, false); }
        return unavailable(screen, handler, true);
    }

    private Frame rootFrame(MinecraftClient mc, Object screen, GenericContainerScreen visible) {
        var handler = visible.getScreenHandler();
        if (handler != mc.player.currentScreenHandler || handler.getClass() != GenericContainerScreenHandler.class
            || !handler.getCursorStack().isEmpty()) return unavailable(screen, handler, false);
        int count = (int) handler.slots.stream().filter(s -> s.inventory != mc.player.getInventory()).count();
        boolean backpack = false;
        if (handler.slots.size() == profile.number("total-slots")) {
            for (int i = profile.number("backpack-first-slot"); i <= profile.number("backpack-last-slot"); i++)
                backpack |= !handler.getSlot(i).getStack().isEmpty();
        }
        int pen = profile.pen(visible.getTitle().getString(), count, handler.slots.size(), backpack);
        if (pen < 1) return unavailable(screen, handler, true);
        for (int i = 0; i < count; i++) {
            if (handler.getSlot(i).id != i || handler.getSlot(i).inventory == mc.player.getInventory())
                return unavailable(screen, handler, false);
        }
        ItemStack left = handler.getSlot(profile.number("left-slot")).getStack();
        ItemStack right = handler.getSlot(profile.number("right-slot")).getStack();
        Role role = profile.role(Registries.ITEM.getId(left.getItem()).toString(), left.isEmpty());
        Projection card = role == Role.OCCUPIED ? projection(left) : null;
        if (role == Role.OCCUPIED && card == null) role = Role.UNKNOWN;
        // Stack components preserve species/aspects; toString alone omits components on some MC builds.
        String rightFingerprint = Registries.ITEM.getId(right.getItem()) + ":" + right.getCount() + ":" + right.getComponents();
        return new Frame(screen, handler, Kind.ROOT, pen, role, card, rightFingerprint, true, null);
    }

    @Override public void openLeft(Object retainedRoot, boolean insert) {
        MinecraftClient mc = MinecraftClient.getInstance();
        Frame f = inspect(retainedRoot);
        if (!f.live() || f.kind() != Kind.ROOT || !(retainedRoot instanceof GenericContainerScreen original)
            || f.handler() != original.getScreenHandler() || (f.screen() != null && f.screen() != retainedRoot)
            || f.left() != (insert ? Role.EMPTY : Role.OCCUPIED) || mc.interactionManager == null)
            throw new IllegalStateException("Left role/handler changed before dispatch");
        root = retainedRoot;
        inserting = insert;
        mc.interactionManager.clickSlot(original.getScreenHandler().syncId, profile.number("left-slot"),
            profile.number(insert ? "insert-button" : "out-button"), clickType(profile, insert), mc.player);
    }

    static SlotActionType clickType(AcademyDaycareProfile profile, boolean insert) {
        return SlotActionType.valueOf(profile.value(insert ? "insert-action" : "out-action"));
    }

    @Override public boolean select(Object screen, Pokemon expected, Storage baseline) {
        Frame frame = read(root);
        if (!frame.live() || frame.screen() != screen || frame.kind() != Kind.PICKER
            || frame.storage() == null || !frame.storage().party().equals(baseline.party())
            || !frame.storage().pc().equals(baseline.pc())
            || frame.storage().partyIdentity() != baseline.partyIdentity() || frame.storage().pcIdentity() != baseline.pcIdentity()
            || Boolean.TRUE.equals(get(screen, "getClosed"))) return false;
        Object config = get(screen, "getConfig");
        Object raw = get(config, "getPokemon");
        if (!(raw instanceof List<?> entries) || entries.isEmpty() || entries.size() > 6) return false;
        var choices = new ArrayList<DaycareNativeRules.Choice>();
        for (Object dto : entries) {
            Object properties = get(dto, "getPokemonProperties");
            Object species = get(properties, "getSpecies"), level = get(properties, "getLevel");
            Object enabled = get(dto, "getEnabled");
            if (!(species instanceof String s) || !(level instanceof Number n) || !(enabled instanceof Boolean active)) return false;
            Projection projection = new Projection(species(s), aspects(get(dto, "getAspects")));
            choices.add(new DaycareNativeRules.Choice(projection, n.intValue(), active));
        }
        int index = DaycareNativeRules.pickIndex(expected, baseline.party(), choices);
        if (index < 0 || normalized(MinecraftClient.getInstance().currentScreen) != screen) return false;
        Object callback = get(config, "getOnSelect");
        try {
            Class.forName("kotlin.jvm.functions.Function2").getMethod("invoke", Object.class, Object.class)
                .invoke(callback, screen, entries.get(index));
            return true;
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException("Picker callback failed", failure); }
    }

    @Override public ConfirmationReadiness confirmationReadiness(Object screen, boolean insert) {
        Frame frame = inspect(root);
        if (!frame.live() || frame.screen() != screen || frame.kind() != Kind.CONFIRM
            || dialogue(screen, insert) == null) return ConfirmationReadiness.UNAVAILABLE;
        return inputReadiness(screen);
    }

    static ConfirmationReadiness inputReadiness(Object screen) {
        try {
            Object waiting = get(screen, "getWaitingForServerUpdate"), rendered = get(screen, "renderInput");
            if (!(waiting instanceof Boolean waitingForServer) || !(rendered instanceof Boolean renderInput))
                return ConfirmationReadiness.UNAVAILABLE;
            return waitingForServer || !renderInput ? ConfirmationReadiness.WAITING : ConfirmationReadiness.READY;
        } catch (RuntimeException | LinkageError unavailable) { return ConfirmationReadiness.UNAVAILABLE; }
    }

    @Override public boolean confirm(Object screen, boolean insert) {
        Frame frame = inspect(root);
        if (!frame.live() || frame.screen() != screen || frame.kind() != Kind.CONFIRM) return false;
        Dialogue input = dialogue(screen, insert);
        if (input == null || inputReadiness(screen) != ConfirmationReadiness.READY) return false;
        try {
            Class<?> packetType = Class.forName("com.cobblemon.mod.common.net.messages.server.dialogue.InputToDialoguePacket");
            Object packet = packetType.getConstructor(UUID.class, String.class).newInstance(input.id(), input.value());
            // Screen's own send boundary also tracks waitingForServerUpdate.
            Class<?> networkPacket = Class.forName("com.cobblemon.mod.common.api.net.NetworkPacket");
            screen.getClass().getMethod("sendToServer", networkPacket).invoke(screen, packet);
            return true;
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException("Dialogue dispatch failed", failure); }
    }

    private Dialogue dialogue(Object screen, boolean insert) {
        Object dto = get(screen, "getDialogueDTO");
        Object page = get(dto, "getCurrentPageDTO");
        Object rawLines = get(page, "getLines"), rawOptions = get(screen, "getDialogueOptionWidgets");
        if (!(rawLines instanceof List<?> lines) || !(rawOptions instanceof List<?> options)) return null;
        String content = String.join(" ", lines.stream().map(NativeDaycarePort::text).toList());
        List<String> labels = options.stream().map(o -> text(get(o, "getText"))).toList();
        int chosen = profile.confirmation(content, insert, labels);
        if (chosen < 0 || !Boolean.TRUE.equals(get(options.get(chosen), "getSelectable"))) return null;
        Object id = get(get(dto, "getDialogueInput"), "getInputId");
        Object value = get(options.get(chosen), "getValue");
        return id instanceof UUID uuid && value instanceof String s && !s.isBlank() ? new Dialogue(uuid, s) : null;
    }

    @SuppressWarnings("unchecked")
    private Projection projection(ItemStack stack) {
        try {
            Object key = Class.forName("com.cobblemon.mod.common.CobblemonItemComponents").getField("POKEMON_ITEM").get(null);
            Object component = stack.get((ComponentType<Object>) key);
            if (component == null) return null;
            return new Projection(species(String.valueOf(get(component, "getSpecies"))), aspects(get(component, "getAspects")));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) { return null; }
    }

    static Set<String> aspects(Object raw) {
        if (!(raw instanceof Set<?> set)) throw new IllegalStateException("Missing aspects");
        var result = new HashSet<String>();
        for (Object value : set) {
            if (!(value instanceof String s)) throw new IllegalStateException("Invalid aspect");
            result.add(s);
        }
        return Set.copyOf(result);
    }
    static String species(String id) {
        if (id == null || id.isBlank()) throw new IllegalStateException("Missing species");
        return id.startsWith("cobblemon:") ? id.substring(10) : id;
    }
    private static String text(Object value) { return value instanceof Text text ? text.getString() : ""; }
    private static Object normalized(Screen screen) {
        return screen == null || screen.getClass().getName().equals(OVERLAY) ? null : screen;
    }
    private static Frame transition(Object screen, Object handler, Kind kind) {
        return new Frame(screen, handler, kind, -1, Role.UNKNOWN, null, "", true, null);
    }
    private static Frame unavailable(Object screen, Object handler, boolean live) {
        return new Frame(screen, handler, Kind.UNRELATED, -1, Role.UNKNOWN, null, "", live, null);
    }
    private record Dialogue(UUID id, String value) {}
}
