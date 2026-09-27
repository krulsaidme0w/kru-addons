package dev.krul.builder;

import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.modules.Modules;

public final class BuilderAddon extends MeteorAddon {
    @Override
    public void onInitialize() {
        // Task 1 provides the model and category only; no building module yet.
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
