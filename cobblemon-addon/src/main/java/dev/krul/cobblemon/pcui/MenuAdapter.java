package dev.krul.cobblemon.pcui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;

/** Only screen-specific capabilities live here; commands and layout are shared. */
interface MenuAdapter {
    Object target(MinecraftClient client, Screen screen);
    String describe(Screen screen);
    boolean canCloseLocal(Screen screen);
    boolean close(Screen screen, boolean local);
    /** End a verified hidden session without restoring its old widgets. */
    boolean endSession(Screen screen);
}
