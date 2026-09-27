package dev.krul.cobblemon.pcui;

/** Keep the unlink boolean explicit and reject a screen replaced before the invocation. */
final class PcCloseAction {
    @FunctionalInterface interface Closer { void close(boolean unlink) throws ReflectiveOperationException; }
    private PcCloseAction() {}
    static boolean invoke(Object expectedScreen, Object currentScreen, boolean local, Closer closer) throws ReflectiveOperationException {
        if (expectedScreen == null || expectedScreen != currentScreen) return false;
        closer.close(!local);
        return true;
    }
}
