package dev.krul.cobblemon;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.renderer.text.TextRenderer;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import dev.krul.cobblemon.CobblemonAddon;
import meteordevelopment.meteorclient.systems.modules.Module;
import dev.krul.cobblemon.discovery.CobblemonObservationAdapter;
import dev.krul.cobblemon.discovery.DiscoveryAppearanceFilter;
import dev.krul.cobblemon.discovery.DiscoveryIndex;
import dev.krul.cobblemon.discovery.DiscoveryMinimumRarity;
import dev.krul.cobblemon.discovery.DiscoveryObservationFilter;
import dev.krul.cobblemon.discovery.SpawnRarity;
import dev.krul.cobblemon.discovery.DiscoveryRarityIndex;
import dev.krul.cobblemon.discovery.DiscoveryRarityLoader;
import dev.krul.cobblemon.discovery.DiscoveryRenderSelector;
import dev.krul.cobblemon.discovery.DiscoveryRarityEvidence;
import dev.krul.cobblemon.discovery.DiscoveryRarityRuntime;
import dev.krul.cobblemon.discovery.DiscoveryScanner;
import dev.krul.cobblemon.discovery.DiscoverySpeciesClassFilter;
import dev.krul.cobblemon.discovery.DiscoveryText;
import dev.krul.cobblemon.discovery.DiscoveryTarget;
import dev.krul.cobblemon.discovery.PokemonStorageRarityAudit;
import dev.krul.cobblemon.discovery.PokemonObservation;
import dev.krul.cobblemon.species.LiveCobblemonSpeciesFactsResolver;
import dev.krul.cobblemon.species.SpeciesClass;
import dev.krul.cobblemon.species.SpeciesFacts;
import dev.krul.cobblemon.species.SpeciesFactsResolver;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import dev.krul.cobblemon.hud.CobblemonDiscoveryHud;
import meteordevelopment.meteorclient.systems.hud.XAnchor;
import meteordevelopment.meteorclient.systems.hud.YAnchor;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.render.NametagUtils;
import meteordevelopment.meteorclient.utils.render.RenderUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import org.joml.Vector3d;

import java.util.List;
import java.util.HashSet;
import java.util.Set;

public class Discovery extends Module {
    private static final DiscoveryIndex INDEX = new DiscoveryIndex(128, 300_000);
    private static final double SCAN_RADIUS = 256;
    /** One bounded history for the module; the HUD setting is the only display limit. */
    public static final int MAX_REMEMBERED = 128;
    public static final long DISPLAY_MILLIS = 30_000L;

    private final SettingGroup sgSearch = settings.createGroup("Search");
    private final SettingGroup sgFilters = settings.createGroup("Filters");
    private final SettingGroup sgVisuals = settings.createGroup("Visuals");
    private final SettingGroup sgAutomation = settings.createGroup("Diagnostics");

    public final Setting<Integer> scanInterval = sgSearch.add(new IntSetting.Builder()
        .name("interval")
        .description("Ticks between world scans.")
        .defaultValue(10)
        .range(1, 100)
        .sliderRange(1, 40)
        .build());

    public final Setting<String> targetSpecies = sgSearch.add(new StringSetting.Builder()
        .name("species")
        .description("Optional species id. When set, it is combined with every filter below.")
        .defaultValue("")
        .onChanged(this::onTargetSpeciesChanged)
        .build());

    public final Setting<DiscoveryMinimumRarity> minimumRarity = sgFilters.add(new EnumSetting.Builder<DiscoveryMinimumRarity>()
        .name("rarity")
        .description("Minimum known spawn rarity, combined with the other filters.")
        .defaultValue(DiscoveryMinimumRarity.ALL)
        .onChanged(value -> onDiscoveryFilterChanged())
        .build());

    public final Setting<DiscoverySpeciesClassFilter> speciesClassFilter = sgFilters.add(
        new EnumSetting.Builder<DiscoverySpeciesClassFilter>()
            .name("class")
            .description("Intrinsic species class, combined with the other filters.")
            .defaultValue(DiscoverySpeciesClassFilter.ANY)
            .onChanged(value -> onDiscoveryFilterChanged())
            .build());

    public final Setting<DiscoveryAppearanceFilter> appearanceFilter = sgFilters.add(
        new EnumSetting.Builder<DiscoveryAppearanceFilter>()
        .name("appearance")
        .description("Appearance condition, combined with the other filters.")
        .defaultValue(DiscoveryAppearanceFilter.ANY)
        .onChanged(value -> onDiscoveryFilterChanged())
        .build());

    public final Setting<Boolean> verboseLogs = sgAutomation.add(new BoolSetting.Builder()
        .name("verbose-logs")
        .description("Write detailed discovery diagnostics to chat and the client log.")
        .defaultValue(false)
        .build());

    public final Setting<Boolean> showTracers = sgVisuals.add(new BoolSetting.Builder()
        .name("tracers")
        .description("Draw Discovery-owned lines to found Pokémon.")
        .defaultValue(true)
        .build());

    public final Setting<Boolean> showNametags = sgVisuals.add(new BoolSetting.Builder()
        .name("nametags")
        .description("Draw Discovery-owned information above found Pokémon.")
        .defaultValue(true)
        .build());

    private final DiscoveryScanner scanner = new DiscoveryScanner(new CobblemonObservationAdapter());
    private SpeciesFactsResolver speciesFacts = new LiveCobblemonSpeciesFactsResolver();
    private DiscoveryRarityIndex rarityIndex = DiscoveryRarityIndex.builder().build();
    private final DiscoveryRarityEvidence rarityEvidence = DiscoveryRarityRuntime.evidence();
    private final Vector3d nametagPos = new Vector3d();
    private int ticks;
    private int lastScanCount = -1;
    private int lastWorldEntityCount = -1;
    private int lastPokemonEntityCount = -1;
    private Object scanWorld;
    private boolean unreadableReported;
    private final Set<String> unknownSpeciesReported = new HashSet<>();
    private final Set<String> speciesFactsReported = new HashSet<>();
    private String storageAuditStatus = "No storage audit run.";

    public static DiscoveryIndex index() {
        // Inspecting the index must not acquire a ClientWorld while the module is disabled.
        return INDEX;
    }

    public Discovery() {
        super(CobblemonAddon.CATEGORY, "pokemon-discovery", "Discover client-visible Pokémon. Never sends commands.");
    }

    @Override public WWidget getWidget(GuiTheme theme) {
        WVerticalList list = theme.verticalList();
        list.add(theme.label("Read-only party + PC rarity diagnostics.")).expandX();
        WButton audit = list.add(theme.button("Audit Pokémon storage")).expandX().widget();
        audit.action = () -> {
            PokemonStorageRarityAudit.Report report = PokemonStorageRarityAudit.readLive(
                rarityEvidence, new DiscoveryRarityLoader().load(mc.getResourceManager()));
            mc.keyboard.setClipboard(PokemonStorageRarityAudit.format(report));
            storageAuditStatus = report.available()
                ? "Copied: " + report.entries().size() + " readable, " + report.unreadable() + " unreadable."
                : report.unavailableReason();
        };
        list.add(theme.label(storageAuditStatus)).expandX();
        return list;
    }

    @Override
    public void onActivate() {
        ticks = 0;
        lastScanCount = -1;
        lastWorldEntityCount = -1;
        lastPokemonEntityCount = -1;
        INDEX.clear();
        unknownSpeciesReported.clear();
        speciesFactsReported.clear();
        speciesFacts = new LiveCobblemonSpeciesFactsResolver();
        rarityIndex = new DiscoveryRarityLoader().load(mc.getResourceManager());
        logSearchMode(targetSpecies.get());
        if (verboseLogs.get()) MeteorClient.LOG.info("Discovery enabled: loaded {} Cobblemon rarity entries", rarityIndex.size());
        Hud.get().active = true;
        ensureHudElement();
    }

    @Override
    public void onDeactivate() {
        scanWorld = null;
        INDEX.bindWorld(null);
        INDEX.clear();
    }

    @EventHandler private void onGameLeft(GameLeftEvent event) {
        scanWorld = null;
        INDEX.bindWorld(null);
        INDEX.clear();
        rarityIndex = DiscoveryRarityIndex.empty();
        speciesFacts = new LiveCobblemonSpeciesFactsResolver();
    }

    @EventHandler private void onGameJoined(GameJoinedEvent event) {
        INDEX.clear();
        speciesFacts = new LiveCobblemonSpeciesFactsResolver();
        rarityIndex = new DiscoveryRarityLoader().load(mc.getResourceManager());
        unknownSpeciesReported.clear();
        speciesFactsReported.clear();
        ticks = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        INDEX.bindWorld(mc.world);
        if (scanWorld != mc.world) {
            scanWorld = mc.world;
            ticks = scanInterval.get();
            lastScanCount = lastPokemonEntityCount = lastWorldEntityCount = -1;
            unreadableReported = false;
        }
        if (mc.world == null || mc.player == null) return;
        if (++ticks >= scanInterval.get()) {
            ticks = 0;
            long now = System.currentTimeMillis();
            int worldEntityCount = 0;
            int pokemonEntityCount = 0;
            for (Entity entity : mc.world.getEntities()) {
                worldEntityCount++;
                if (entity.getClass().getName().contains("PokemonEntity")) pokemonEntityCount++;
            }
            List<PokemonObservation> scanned = scanner.scan(mc, SCAN_RADIUS, now);
            if (scanned.isEmpty() && pokemonEntityCount > 0 && !unreadableReported) {
                MeteorClient.LOG.info("[Discovery] No readable Pokemon within {} blocks; loaded candidates={}, dimension={}",
                    SCAN_RADIUS, pokemonEntityCount, mc.world.getRegistryKey().getValue());
                unreadableReported = true;
            } else if (!scanned.isEmpty()) unreadableReported = false;
            if (scanned.size() != lastScanCount || worldEntityCount != lastWorldEntityCount
                || pokemonEntityCount != lastPokemonEntityCount) {
                lastScanCount = scanned.size();
                lastWorldEntityCount = worldEntityCount;
                lastPokemonEntityCount = pokemonEntityCount;
                if (verboseLogs.get()) {
                    MeteorClient.LOG.info("Discovery scan: {} world entities, {} PokemonEntity candidates, {} readable Pokemon",
                        worldEntityCount, pokemonEntityCount, scanned.size());
                }
            }
            for (PokemonObservation observation : scanned) {
                SpawnRarity rarity = observation.spawnRarity();
                DiscoveryRarityEvidence.Resolution rarityResolution = rarityEvidence.resolve(observation.species(), rarityIndex);
                if (rarity == SpawnRarity.UNKNOWN) rarity = rarityResolution.rarity();
                if (rarity == SpawnRarity.UNKNOWN && unknownSpeciesReported.add(observation.species())) {
                    if (verboseLogs.get()) {
                        MeteorClient.LOG.info("Discovery: no authoritative rarity data for {}; likely server-only/custom spawn data", observation.species());
                    }
                }
                SpeciesFacts facts = speciesFacts.resolve(observation.species());
                PokemonObservation resolved = observation.withDiscoveryFacts(
                    rarity, facts.classes(), facts.labels());
                if (verboseLogs.get() && speciesFactsReported.add(observation.species())) {
                    String classSource = facts.authoritative() ? "species-registry" : "unavailable";
                    String raritySource = rarityResolution.source().name().toLowerCase().replace('_', '-');
                    MeteorClient.LOG.info("DISCOVERY_CLASS species={} labels={} source={}",
                        observation.species(), facts.labels(), classSource);
                    MeteorClient.LOG.info("DISCOVERY_SPAWN_RARITY species={} value={} source={}",
                        observation.species(), rarity.name().toLowerCase().replace('_', '-'), raritySource);
                }
                boolean changed = INDEX.observe(resolved);
                if (changed && matchesDiscoveryFilter(resolved)) {
                    String flags = (Boolean.TRUE.equals(resolved.shiny()) ? " shiny" : "") + (Boolean.TRUE.equals(resolved.radiant()) ? " radiant" : "");
                    if (verboseLogs.get()) {
                        info("Found %s [%s]%s at %.0f %.0f %.0f.", resolved.species(),
                            resolved.spawnRarity().name().toLowerCase().replace('_', '-'), flags,
                            resolved.x(), resolved.y(), resolved.z());
                    }
                }
            }
        }
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (!showTracers.get() || mc.world == null || mc.player == null) return;
        for (PokemonObservation observation : renderObservations()) {
            Entity entity = findEntity(observation.entityId());
            if (entity == null) continue;
            event.renderer.line(RenderUtils.center.x, RenderUtils.center.y, RenderUtils.center.z,
                entity.getX(), entity.getY() + entity.getHeight() / 2, entity.getZ(), observationColor(observation));
        }
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (!showNametags.get() || mc.world == null) return;
        TextRenderer text = TextRenderer.get();
        for (PokemonObservation observation : renderObservations()) {
            Entity entity = findEntity(observation.entityId());
            if (entity == null) continue;
            Utils.set(nametagPos, entity, event.tickDelta);
            nametagPos.add(0, entity.getHeight() + 0.5, 0);
            if (!NametagUtils.to2D(nametagPos, 1.5)) continue;

            String label = DiscoveryText.lines(observation, true).getFirst();

            NametagUtils.begin(nametagPos, event.drawContext);
            text.beginBig();
            text.render(label, -text.getWidth(label, true) / 2, -text.getHeight(true),
                observationColor(observation), true);
            text.end();
            NametagUtils.end(event.drawContext);
        }
    }

    public List<PokemonObservation> visibleObservations() {
        if (!isActive()) return List.of();
        INDEX.bindWorld(mc.world);
        return INDEX.recent(System.currentTimeMillis(), SpawnRarity.UNKNOWN, INDEX.size(), DISPLAY_MILLIS)
            .stream()
            .filter(this::matchesDiscoveryFilter)
            .toList();
    }

    @Override public String getInfoString() {
        if (mc.world == null || mc.player == null || scanWorld != mc.world || lastScanCount < 0) return "Waiting for world";
        if (lastPokemonEntityCount == 0) return "No Pokemon loaded";
        if (lastScanCount == 0) return "0 readable / " + lastPokemonEntityCount + " loaded";
        return visibleObservations().size() + " shown / " + lastScanCount + " readable";
    }

    private List<PokemonObservation> renderObservations() {
        INDEX.bindWorld(mc.world);
        if (mc.world == null || mc.player == null) return List.of();
        Set<Integer> loadedEntityIds = new HashSet<>();
        for (Entity entity : mc.world.getEntities()) loadedEntityIds.add(entity.getId());
        List<PokemonObservation> matching = INDEX.recent(
                System.currentTimeMillis(), SpawnRarity.UNKNOWN, INDEX.size(), DISPLAY_MILLIS)
            .stream()
            .filter(this::matchesDiscoveryFilter)
            .toList();
        return DiscoveryRenderSelector.select(matching, loadedEntityIds,
            mc.player.getX(), mc.player.getY(), mc.player.getZ());
    }

    private boolean matchesDiscoveryFilter(PokemonObservation observation) {
        return DiscoveryObservationFilter.matches(observation, targetSpecies.get(),
            speciesClassFilter.get(), appearanceFilter.get(), minimumRarity.get(),
            rarityEvidence.resolve(observation.species(), rarityIndex).rarity()
                .atLeast(minimumRarity.get().spawnRarity()));
    }

    private void onTargetSpeciesChanged(String value) {
        if (isActive()) logSearchMode(value);
    }


    private void onDiscoveryFilterChanged() {
        if (isActive()) {
            logSearchMode(targetSpecies.get());
        }
    }

    private void logSearchMode(String targetValue) {
        MeteorClient.LOG.info("[Discovery] DISCOVERY_SEARCH_MODE target={} mode={} minimumRarity={} class={} appearance={}",
            displayTarget(targetValue), DiscoveryTarget.active(targetValue) ? "exact-species" : "filters",
            minimumRarity.get(), speciesClassFilter.get(), appearanceFilter.get());
    }

    private String displayTarget() {
        return displayTarget(targetSpecies.get());
    }

    private static String displayTarget(String value) {
        String target = DiscoveryTarget.normalize(value);
        return target.isEmpty() ? "normal-filters" : target;
    }

    private Entity findEntity(int id) {
        for (Entity entity : mc.world.getEntities()) if (entity.getId() == id) return entity;
        return null;
    }

    private void ensureHudElement() {
        for (HudElement element : Hud.get()) {
            if (element.info.name.equals(CobblemonDiscoveryHud.INFO.name)) {
                element.box.xAnchor = XAnchor.Left;
                element.box.yAnchor = YAnchor.Top;
                element.box.setPos(8, 80);
                element.box.updateAnchors();
                element.updatePos();
                return;
            }
        }
        Hud.get().add(CobblemonDiscoveryHud.INFO, 8, 80, XAnchor.Left, YAnchor.Top);
    }

    private static Color rarityColor(SpawnRarity rarity) {
        return switch (rarity) {
            case COMMON -> new Color(180, 180, 180, 180);
            case UNCOMMON -> new Color(80, 220, 100, 180);
            case RARE -> new Color(80, 150, 255, 180);
            case ULTRA_RARE -> new Color(220, 80, 255, 180);
            case LEGENDARY -> new Color(255, 170, 40, 200);
            case UNKNOWN -> new Color(220, 220, 220, 180);
        };
    }

    private static Color observationColor(PokemonObservation observation) {
        if (Boolean.TRUE.equals(observation.shiny()) || Boolean.TRUE.equals(observation.radiant())) {
            return new Color(255, 215, 0, 220);
        }
        if (observation.speciesClasses().contains(SpeciesClass.MYTHICAL)) {
            return new Color(255, 105, 180, 210);
        }
        if (observation.speciesClasses().contains(SpeciesClass.LEGENDARY)
            || observation.speciesClasses().contains(SpeciesClass.RESTRICTED)) {
            return new Color(255, 170, 40, 210);
        }
        if (observation.speciesClasses().contains(SpeciesClass.ULTRA_BEAST)
            || observation.speciesClasses().contains(SpeciesClass.PARADOX)) {
            return new Color(220, 80, 255, 200);
        }
        return rarityColor(observation.spawnRarity());
    }
}
