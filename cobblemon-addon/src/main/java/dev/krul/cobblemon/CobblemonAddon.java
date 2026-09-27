package dev.krul.cobblemon;

import dev.krul.cobblemon.discovery.DiscoveryRarityRuntime;
import dev.krul.cobblemon.hud.CobblemonDiscoveryHud;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.item.Items;

/** Independent addon; does not replace Meteor systems or navigation. */
public final class CobblemonAddon extends MeteorAddon {
    public static final Category CATEGORY = new Category("Cobblemon", Items.COMPASS.getDefaultStack());
    @Override public void onRegisterCategories() { Modules.registerCategory(CATEGORY); }
    @Override public void onInitialize() {
        dev.krul.cobblemon.observation.SharedObservationRuntime.init();
        Modules modules = Modules.get();
        modules.add(new Discovery());
        modules.add(new DungeonDiscovery());
        modules.add(new TypeChart());
        modules.add(new BattleMatchups());
        modules.add(new CobblemonUiUtils());
        modules.add(new PcOrganizer());
        Hud.get().register(CobblemonDiscoveryHud.INFO);
        DiscoveryRarityRuntime.init();
        dev.krul.cobblemon.gui.overlay.OverlayHost.init();
    }
    @Override public String getPackage() { return "dev.krul.cobblemon"; }
}
