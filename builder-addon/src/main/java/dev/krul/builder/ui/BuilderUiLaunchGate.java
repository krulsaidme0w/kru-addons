package dev.krul.builder.ui;

/** Pure readiness gate for restoring the Builder panel during Meteor startup. */
public final class BuilderUiLaunchGate {
    private BuilderUiLaunchGate() {}

    public static boolean shouldOpen(boolean pending, boolean themeReady, boolean screenOccupied) {
        return pending && themeReady && !screenOccupied;
    }
}
