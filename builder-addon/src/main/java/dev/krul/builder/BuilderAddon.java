package dev.krul.builder;

import dev.krul.builder.minecraft.BuilderModule;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.modules.Modules;

public final class BuilderAddon extends MeteorAddon {
    @Override
    public void onInitialize() {
        Modules.get().add(new BuilderModule());
    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(BuilderCategory.BUILDER);
    }

    @Override
    public String getPackage() {
        return "dev.krul.builder";
    }
}
