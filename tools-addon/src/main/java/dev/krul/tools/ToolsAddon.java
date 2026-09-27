package dev.krul.tools;

import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.modules.Modules;
import dev.krul.tools.packet.PacketDelayModule;

/** Independent integration entrypoint. Upstream tools are added only after their audit. */
public final class ToolsAddon extends MeteorAddon {
    public static ToolsAddon INSTANCE;

    @Override public void onRegisterCategories() {
        Modules.registerCategory(ToolsCategory.INSTANCE);
    }

    @Override public void onInitialize() {
        INSTANCE = this;
        Modules.get().add(new PacketDelayModule());
    }
    @Override public String getPackage() { return "dev.krul.tools"; }
}
