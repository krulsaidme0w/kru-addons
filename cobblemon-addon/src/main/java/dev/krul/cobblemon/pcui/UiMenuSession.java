package dev.krul.cobblemon.pcui;

/** A retained authorization scope, never a saved inventory or restored screen. */
final class UiMenuSession {
    private UiActionScope scope;
    private Object suppressedScreen;
    private Object previousScreen;
    private boolean detached;
    private long revision;

    UiActionScope scope() { return scope; }
    boolean detached() { return detached; }
    long revision() { return revision; }
    boolean matches(UiActionScope expected) { return scope != null && scope.equals(expected); }

    UiActionScope observe(Object world, Object connection, Object screen, UiActionScope candidate,
                          boolean rootLive, boolean ownedTransition, long revision) {
        return observe(world, connection, screen, candidate, rootLive, ownedTransition, revision, false);
    }
    UiActionScope observe(Object world, Object connection, Object screen, UiActionScope candidate,
                          boolean rootLive, boolean ownedTransition, long revision, boolean allowContainerHandoff) {
        try {
            return next(world, connection, screen, candidate, rootLive, ownedTransition, revision, allowContainerHandoff);
        } finally { previousScreen = screen; }
    }
    private UiActionScope next(Object world, Object connection, Object screen, UiActionScope candidate,
                              boolean rootLive, boolean ownedTransition, long revision, boolean allowContainerHandoff) {
        if (world == null || connection == null) { reset(); return null; }
        if (suppressedScreen != screen) suppressedScreen = null;
        if (scope != null) {
            if (scope.world() != world || scope.connection() != connection) {
                reset();
            } else if (!ownedTransition && candidate != null && !scope.equals(candidate)) {
                scope = null; detached = false;
            } else if (this.revision != revision) {
                if (allowContainerHandoff && ownedTransition && screen != null && screen != previousScreen
                    && revision == this.revision + 1) {
                    this.revision = revision;
                    return scope;
                }
                exit(screen); this.revision = revision; return null;
            } else if (ownedTransition) {
                return scope;
            } else if (rootLive && ((screen == scope.screen() && scope.equals(candidate))
                || detached && screen == null)) {
                return scope;
            } else {
                scope = null; detached = false;
            }
        }
        if (candidate != null && screen != null && screen != suppressedScreen
            && candidate.matches(world, connection, screen, candidate.target())) {
            scope = candidate;
            this.revision = revision;
        }
        return scope;
    }

    boolean detach(UiActionScope expected) {
        if (!matches(expected)) return false;
        detached = true;
        return true;
    }

    void exit(Object screen) {
        scope = null; detached = false; suppressedScreen = screen;
    }

    void reset() { scope = null; detached = false; suppressedScreen = previousScreen = null; revision = 0; }
}
