package dev.krul.cobblemon.pcui;

/** Identity, not a title/sync-id match, authorizes an action against the displayed menu. */
record UiActionScope(Object world, Object connection, Object screen, Object target) {
    boolean matches(Object world, Object connection, Object screen, Object target) {
        return this.world != null && this.connection != null && this.screen != null && this.target != null
            && this.world == world && this.connection == connection && this.screen == screen && this.target == target;
    }
    boolean runIfCurrent(UiActionScope current, Runnable action) {
        if (current == null || !matches(current.world, current.connection, current.screen, current.target)) return false;
        action.run();
        return true;
    }
    @Override public boolean equals(Object other) {
        return other instanceof UiActionScope scope && world == scope.world && connection == scope.connection
            && screen == scope.screen && target == scope.target;
    }
    @Override public int hashCode() {
        return ((System.identityHashCode(world) * 31 + System.identityHashCode(connection)) * 31
            + System.identityHashCode(screen)) * 31 + System.identityHashCode(target);
    }
}
