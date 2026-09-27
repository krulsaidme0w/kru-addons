package dev.krul.cobblemon.pcui.daycare;

/** Public Cobblemon ABI only. Reflection failures never become empty data. */
final class DaycareReflection {
    private DaycareReflection() {}
    static Object get(Object receiver, String name) {
        if (receiver == null) throw new IllegalStateException("Missing " + name + " receiver");
        try { return receiver.getClass().getMethod(name).invoke(receiver); }
        catch (ReflectiveOperationException failure) { throw new IllegalStateException("Unavailable " + name, failure); }
    }
    static Object singleton(String type) {
        try { return Class.forName(type).getField("INSTANCE").get(null); }
        catch (ReflectiveOperationException failure) { throw new IllegalStateException("Unavailable " + type, failure); }
    }
}
