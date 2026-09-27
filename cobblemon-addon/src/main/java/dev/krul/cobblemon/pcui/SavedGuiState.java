package dev.krul.cobblemon.pcui;

/** References to one saved client screen; deliberately independent of live menu sessions. */
final class SavedGuiState<S, H> {
    record Entry<S, H>(S screen, H handler) {}
    private Object world, connection;
    private Entry<S, H> entry;
    void save(Object world, Object connection, S screen, H handler) {
        if (world == null || connection == null || screen == null) throw new IllegalArgumentException("No screen to save");
        this.world = world; this.connection = connection;
        entry = new Entry<>(screen, handler);
    }
    Entry<S, H> load(Object world, Object connection) {
        if (this.world != world || this.connection != connection) clear();
        return entry;
    }
    void clear() { entry = null; world = connection = null; }
}
