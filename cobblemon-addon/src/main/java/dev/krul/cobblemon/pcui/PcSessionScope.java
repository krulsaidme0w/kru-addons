package dev.krul.cobblemon.pcui;

import java.util.List;
import java.util.Optional;

/** Process-local identity scope. Restoration exposes values, never the saved Screen or storage. */
public final class PcSessionScope {
    public record View(List<String> facts) {
        public View { facts = List.copyOf(facts); }
        public boolean serverLinkVerified() { return false; }
        public boolean storageInteractionAllowed() { return false; }
    }

    private Object world, network, screen, pc;
    private View saved;

    public boolean save(Object world, Object network, Object screen, Object pc, List<String> facts) {
        reset();
        if (world == null || network == null || screen == null || pc == null) return false;
        this.world = world;
        this.network = network;
        this.screen = screen;
        this.pc = pc;
        saved = new View(facts);
        return true;
    }

    /** A null current PC means a local close/other screen, not evidence of a renewed link. */
    public void observe(Object world, Object network, Object currentPcScreen, Object currentPc) {
        if (saved == null) return;
        if (world == null || network == null || this.world != world || this.network != network
            || (currentPcScreen != null && (screen != currentPcScreen || pc != currentPc))) reset();
    }

    public Optional<View> restore(Object world, Object network) {
        observe(world, network, null, null);
        return Optional.ofNullable(saved);
    }

    public void reset() {
        saved = null;
        world = network = screen = pc = null;
    }
}
