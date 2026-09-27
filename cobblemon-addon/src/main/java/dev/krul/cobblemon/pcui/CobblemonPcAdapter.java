package dev.krul.cobblemon.pcui;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * ABI checked with javap against Abes-Hutts-Cobblemon-1.21.1.1.7.3-1.0.5.jar:
 * PCGUI extends Screen, has getPc/getParty/getPreviewPokemon$common, and
 * closeNormally(false) plays PC_OFF and calls setScreen(null). The true branch also
 * sends UnlinkPlayerFromPCPacket; its server handler removes the PC/pasture links.
 * PCGUI has no removed() override in that jar. No vanilla handled-screen API applies.
 * Reattaching PCGUI would recreate active StorageWidget controls without relinking.
 * This adapter therefore never restores a Screen or sends an unlink-only request.
 */
final class CobblemonPcAdapter {
    private static final String PC_GUI = "com.cobblemon.mod.common.client.gui.pc.PCGUI";
    record Context(Object screen, Object pc, Object party, List<String> facts, boolean canCloseLocal) {
        Context { facts = List.copyOf(facts); }
        boolean canSave() { return pc != null && party != null; }
    }

    Context inspect(Object screen) {
        if (screen == null || !PC_GUI.equals(screen.getClass().getName())) return null;
        Object pc = PcReflection.get(screen, "getPc"), party = PcReflection.get(screen, "getParty");
        var facts = new ArrayList<String>();
        facts.add("PC: " + PcReflection.value(PcReflection.get(pc, "getUuid")));
        facts.add("Party: " + PcReflection.value(PcReflection.get(party, "getUuid")));
        var preview = PcReflection.read(screen, "getPreviewPokemon$common");
        facts.addAll(preview.available() ? PcPreviewFacts.read(preview.value())
            : List.of("Selected Pokemon reader unavailable."));
        return new Context(screen, pc, party, facts, pc != null && party != null && localCloseMethod(screen) != null);
    }

    boolean closeLocal(Object currentScreen) {
        if (currentScreen == null || !PC_GUI.equals(currentScreen.getClass().getName())) return false;
        Context context = inspect(currentScreen);
        if (context == null || !context.canCloseLocal()) return false;
        try {
            // Explicit false is essential: the default overload unlinks on the server.
            localCloseMethod(currentScreen).invoke(currentScreen, false);
            return true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) {
            return false;
        }
    }

    private Method localCloseMethod(Object screen) {
        try {
            Method method = screen.getClass().getMethod("closeNormally", boolean.class);
            return method.getReturnType() == void.class ? method : null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) {
            return null;
        }
    }
}
