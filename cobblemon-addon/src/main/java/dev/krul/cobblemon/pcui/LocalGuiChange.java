package dev.krul.cobblemon.pcui;

/** Synchronous GUI lifecycle guard; unrelated network traffic is never suppressed. */
final class LocalGuiChange {
    private final ThreadLocal<Boolean> changing = new ThreadLocal<>();
    boolean suppresses(boolean guiPacket) { return guiPacket && Boolean.TRUE.equals(changing.get()); }
    void run(Runnable change) {
        Boolean previous = changing.get();
        changing.set(true);
        try { change.run(); }
        finally { if (previous == null) changing.remove(); else changing.set(previous); }
    }
}
