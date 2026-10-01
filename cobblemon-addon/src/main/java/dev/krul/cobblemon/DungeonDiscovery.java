package dev.krul.cobblemon;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.events.world.BlockActivateEvent;
import meteordevelopment.meteorclient.events.game.OpenScreenEvent;
import net.minecraft.registry.Registries;
import net.minecraft.entity.Entity;
import meteordevelopment.meteorclient.renderer.Renderer2D;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.renderer.text.TextRenderer;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import dev.krul.cobblemon.CobblemonAddon;
import meteordevelopment.meteorclient.systems.modules.Module;
import dev.krul.cobblemon.discovery.DiscoveryRarityIndex;
import dev.krul.cobblemon.discovery.DiscoveryRarityLoader;
import dev.krul.cobblemon.dungeon.DungeonObservation;
import dev.krul.cobblemon.dungeon.DungeonObservationIndex;
import dev.krul.cobblemon.dungeon.DungeonArrowLayout;
import dev.krul.cobblemon.dungeon.DungeonProbeText;
import dev.krul.cobblemon.dungeon.DungeonRenderSelector;
import dev.krul.cobblemon.dungeon.DungeonRenderCache;
import dev.krul.cobblemon.dungeon.DungeonPerformance;
import dev.krul.cobblemon.dungeon.DungeonTargetFilters;
import dev.krul.cobblemon.dungeon.DungeonTargetIndex;
import dev.krul.cobblemon.dungeon.DungeonRenderTarget;
import dev.krul.cobblemon.dungeon.DungeonWorldProbe;
import meteordevelopment.meteorclient.utils.render.NametagUtils;
import meteordevelopment.meteorclient.utils.render.RenderUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.orbit.EventHandler;
import org.joml.Vector3d;

import dev.krul.cobblemon.rules.ProfileStore;
import dev.krul.cobblemon.rules.RuleProfile;
import dev.krul.cobblemon.rules.RuleCategory;
import dev.krul.cobblemon.rules.ProfileDraft;
import dev.krul.cobblemon.gui.DiscoveryProfileScreen;
import meteordevelopment.meteorclient.gui.GuiThemes;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import net.fabricmc.loader.api.FabricLoader;
import java.nio.file.Path;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.function.LongSupplier;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import dev.krul.cobblemon.dungeon.OpenedChestCache;

/** Separate, read-only loaded-world evidence collector for dungeon runs. */
public final class DungeonDiscovery extends Module {
    private static final int WORK_BUDGET_PER_TICK = 64;
    private static final int LOG_DRAIN_PER_TICK = 8;
    private final SettingGroup sgObservation = settings.createGroup("Observation");

    private final ProfileStore profileStore;
    private final Setting<Boolean> profileEnabled = sgObservation.add(new BoolSetting.Builder()
        .name("profile-enabled").description("Use the bundled CobbHelper v2-compatible dungeon recognition profile.")
        .defaultValue(true).onChanged(value -> reloadProfile()).build());
    private final Setting<String> profileFile = sgObservation.add(new StringSetting.Builder()
        .name("profile-file").description("JSON path relative to the Minecraft config directory. Reload after editing.")
        .defaultValue("academy-example.json").onChanged(value -> reloadProfile()).build());
    private Lifecycle lifecycle;
    private final DungeonRenderCache renderCache = new DungeonRenderCache();
    private final OpenedChestCache openedChests = new OpenedChestCache();
    private final DungeonPerformance performance = new DungeonPerformance();
    private Object observedWorld;
    private String observedDimension = "";
    private final Map<String, Entity> targetEntities = new HashMap<>();
    private long tick;

    public DungeonDiscovery() {
        super(CobblemonAddon.CATEGORY, "discovery", "Read-only generic world overlays using an explicitly enabled JSON profile.");
        ProfileStore store;
        try {
            store = new ProfileStore(FabricLoader.getInstance().getConfigDir().resolve("kru-cobblemon/profiles"));
        } catch (Exception error) {
            store = new ProfileStore();
        }
        profileStore = store;
        seedBundledProfile();
    }

    private void seedBundledProfile() {
        if (profileStore.current().sourceVersion() == 0) return;
        try (InputStream input = DungeonDiscovery.class.getResourceAsStream("/profiles/academy-example.json")) {
            if (input == null) return;
            String json = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            ProfileStore.Result result = profileStore.seedIfAbsent("academy-example.json", json, true);
            if (!result.success()) MeteorClient.LOG.warn("[Discovery] Could not create starter profile: {}", result.diagnostics());
        } catch (Exception error) {
            MeteorClient.LOG.warn("[Discovery] Could not read bundled starter profile", error);
        }
    }

    private void readProfile() {
        profileStore.disable();
        if (profileEnabled == null || !profileEnabled.get()) return;
        try {
            Path path = FabricLoader.getInstance().getConfigDir().resolve(profileFile.get()).normalize();
            if (profileStore.current().sourceVersion() == 1 || profileFile.get().contains("/")) {
                profileStore.load(path); // explicit legacy read-only compatibility path
            } else {
                var draft = profileStore.select(profileFile.get());
                if (!draft.valid()) warning("%s", String.join("; ", draft.diagnostics()));
            }
            for (String diagnostic : profileStore.current().diagnostics()) warning("%s", diagnostic);
        } catch (RuntimeException error) { warning("Profile unavailable: %s", error.getMessage()); }
    }

    private void reloadProfile() {
        if (isActive()) { onDeactivate(); onActivate(); }
    }

    @Override public WWidget getWidget(GuiTheme theme) {
        WVerticalList list = theme.verticalList();
        list.add(theme.label("JSON profile controls targets, visuals and scale."));
        list.add(theme.label("Profile: config/kru-cobblemon/profiles/" + profileFile.get()));
        WButton copyPath = list.add(theme.button("Copy profile path")).expandX().widget();
        copyPath.action = () -> mc.keyboard.setClipboard(FabricLoader.getInstance().getConfigDir()
            .resolve("kru-cobblemon/profiles").resolve(profileFile.get()).toString());
        WButton edit = list.add(theme.button("Edit JSON in game")).expandX().widget();
        edit.action = () -> mc.setScreen(new DiscoveryProfileScreen(this));
        WButton reload = list.add(theme.button("Reload profile")).expandX().widget();
        reload.action = this::reloadProfile;
        list.add(theme.label("Edit JSON, enable the profile, then reload."));
        return list;
    }

    public String profileFileName() { return profileFile.get(); }

    public String profileJson() {
        try {
            Path name = Path.of(profileFile.get());
            if (!name.isAbsolute() && name.getNameCount() == 1 && profileFile.get().endsWith(".json")) {
                Path path = FabricLoader.getInstance().getConfigDir().resolve("kru-cobblemon/profiles").resolve(name).normalize();
                if (java.nio.file.Files.isRegularFile(path)) return java.nio.file.Files.readString(path);
            }
        } catch (Exception ignored) { }
        return "{\"version\":2,\"categories\":[],\"rules\":[]}";
    }

    public String saveProfile(ProfileDraft draft) {
        ProfileStore.Result saved = profileStore.save(profileFile.get(), draft, true);
        if (!saved.success()) return "Not saved: " + String.join("; ", saved.diagnostics());
        profileStore.apply(draft, true);
        if (isActive()) readProfile();
        return "Saved and applied";
    }

    @Override
    public void onActivate() {
        renderCache.clear();
        openedChests.clear();
        performance.clear();
        observedWorld=mc.world;
        observedDimension = "";
        targetEntities.clear();
        readProfile();
        RuleProfile.Settings profileSettings = profileStore.current().settings();
        DiscoveryRarityIndex rarity = new DiscoveryRarityLoader().load(mc.getResourceManager());
        lifecycle = new Lifecycle(profileSettings.scanIntervalSeconds(), profileSettings.crosshairInspection(),
            index -> new MinecraftProbe(new DungeonWorldProbe(index), mc),
            line -> MeteorClient.LOG.info(line), System::currentTimeMillis, profileSettings.verboseLogs());
        lifecycle.activate(rarity, profileStore.current());
        tick = 0;
    }

    @Override
    public void onDeactivate() {
        renderCache.clear();
        openedChests.clear();
        performance.clear();
        if (lifecycle != null) lifecycle.deactivate();
        observedWorld=null;
        observedDimension = "";
        targetEntities.clear();
        tick = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        long started = System.nanoTime();
        if (observedWorld != mc.world) {
            observedWorld = mc.world;
            renderCache.clear();
            openedChests.clear();
            targetEntities.clear();
            observedDimension = "";
            if (lifecycle != null) lifecycle.worldChanged();
        }
        if (mc.world == null || mc.player == null) return;
        String dimension = mc.world.getRegistryKey().getValue().toString();
        if (!dimension.equals(observedDimension)) {
            observedDimension = dimension;
            renderCache.clear();
            openedChests.beginDimension(dimension);
        }
        tick++;
        if (lifecycle != null) lifecycle.tick(tick, dimension);
        performance.record(DungeonPerformance.Phase.TICK, System.nanoTime() - started);
        performance.report(System.nanoTime()).ifPresent(line -> MeteorClient.LOG.info(line + " targets="
            + getInfoString() + " fps=" + mc.getCurrentFps()));
    }

    @EventHandler
    private void onBlockActivate(BlockActivateEvent event) {
        if (mc.world == null || mc.player == null || !(mc.crosshairTarget instanceof BlockHitResult hit)
            || hit.getType() != HitResult.Type.BLOCK) {
            openedChests.cancelPending();
            return;
        }
        var position = hit.getBlockPos();
        if (!mc.world.getBlockState(position).equals(event.blockState)) {
            openedChests.cancelPending();
            return;
        }
        BlockEntity blockEntity = mc.world.getBlockEntity(position);
        if (blockEntity == null) {
            openedChests.cancelPending();
            return;
        }
        String dimension = mc.world.getRegistryKey().getValue().toString();
        String registryId = Registries.BLOCK_ENTITY_TYPE.getId(blockEntity.getType()).toString();
        var observation = new DungeonObservation(DungeonObservation.Kind.BLOCK_ENTITY, dimension,
            dimension + "|block-entity|" + registryId + "|" + position, registryId,
            blockEntity.getClass().getName(), "", position, Map.of(), System.currentTimeMillis());
        boolean chest = DungeonRenderSelector.classify(observation, 0, profileStore.current())
            .map(target -> target.categoryId().equalsIgnoreCase("chest")).orElse(false);
        if (chest) openedChests.noteActivation(dimension, position, tick);
        else openedChests.cancelPending();
    }

    @EventHandler
    private void onOpenScreen(OpenScreenEvent event) {
        if (event.screen instanceof GenericContainerScreen && mc.world != null)
            openedChests.containerOpened(mc.world.getRegistryKey().getValue().toString(), tick);
        else openedChests.cancelPending();
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (mc.options.hudHidden) return;
        List<DungeonRenderTarget> targets = renderTargets();
        long started = System.nanoTime();
        for (DungeonRenderTarget target : targets) {
            if (target.nametagOnly()) continue;
            Color color = color(target.categoryId());
            double x = target.position().getX() + 0.5;
            double y = target.position().getY() + 0.5;
            double z = target.position().getZ() + 0.5;
            RuleCategory visual = visual(target.categoryId());
            if (visual != null && visual.tracers()) {
                event.renderer.line(RenderUtils.center.x, RenderUtils.center.y, RenderUtils.center.z,
                    x, y, z, color);
            }
            if (visual != null && visual.boxes()) {
                event.renderer.box(target.position(), transparent(color), color, ShapeMode.Both, 0);
            }
        }
        performance.record(DungeonPerformance.Phase.RENDER_3D, System.nanoTime() - started);
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (mc.options.hudHidden) return;
        List<DungeonRenderTarget> targets = renderTargets();
        long started = System.nanoTime();
        renderArrows(targets);

        TextRenderer text = TextRenderer.get();
        for (DungeonRenderTarget target : targets) {
            Vector3d pos;
            RuleCategory visual = visual(target.categoryId());
            if (visual == null || !visual.nametags()) continue;
            double distance = target.distance();
            if (target.nametagOnly()) {
                Entity entity = targetEntities.get(target.identity());
                if (entity == null || entity.isRemoved() || entity.getWorld() != mc.world) {
                    continue;
                } else {
                    pos = Utils.set(new Vector3d(), entity, event.tickDelta).add(0, entity.getHeight() + 0.5, 0);
                    distance = mc.player.distanceTo(entity);
                }
                if (distance > profileStore.current().settings().visualRange()) continue;
            } else pos = worldPosition(target, 1.25);
            if (!NametagUtils.to2D(pos, 1.35)) continue;
            String label = target.label() + " [" + Math.round(distance) + "m]";
            NametagUtils.begin(pos, event.drawContext);
            text.beginBig();
            text.render(label, -text.getWidth(label, true) / 2, -text.getHeight(true), color(target.categoryId()), true);
            text.end();
            NametagUtils.end(event.drawContext);
        }
        performance.record(DungeonPerformance.Phase.RENDER_2D, System.nanoTime() - started);
    }

    private void renderArrows(List<DungeonRenderTarget> targets) {
        double width = mc.getWindow().getFramebufferWidth();
        double height = mc.getWindow().getFramebufferHeight();
        Renderer2D.COLOR.begin();
        for (DungeonRenderTarget target : targets) {
            RuleCategory visual = visual(target.categoryId());
            if (visual == null || !visual.arrows()) continue;
            if (target.nametagOnly()) continue;
            Vector3d projection = worldPosition(target, 0.5);
            boolean projected = NametagUtils.to2D(projection, 1, false, false);
            if (projected && projection.x >= 8 && projection.x <= width - 8
                && projection.y >= 8 && projection.y <= height - 8) continue;
            projection = worldPosition(target, 0.5);
            if (!NametagUtils.to2D(projection, 1, false, true)) continue;
            double[] triangle = DungeonArrowLayout.triangle(projection.x, projection.y, width, height, 18, 7);
            Renderer2D.COLOR.triangle(triangle[0], triangle[1], triangle[2], triangle[3], triangle[4], triangle[5],
                color(target.categoryId()));
        }
        Renderer2D.COLOR.render(null);
    }

    private List<DungeonRenderTarget> renderTargets() {
        if (lifecycle == null || mc.world == null || mc.player == null) return List.of();
        String dimension = mc.world.getRegistryKey().getValue().toString();
        RuleProfile profile = profileStore.current();
        var filters = new DungeonTargetFilters(categoryEnabled(profile, "CHEST", "chest"),
            categoryEnabled(profile, "POKE_BALL", "poke_ball"), categoryEnabled(profile, "WEAPON", "weapon"),
            categoryEnabled(profile, "GUARDIAN", "guardian"), categoryEnabled(profile, "STRONG_POKEMON", "strong_pokemon"), true);
        int visualRange = profile.settings().visualRange();
        return renderCache.get(tick, mc.world, new RenderOptions(dimension, visualRange, filters, openedChests.size()), () -> {
            long started = System.nanoTime();
            long now = System.currentTimeMillis();
            var selected = DungeonRenderSelector.select(loadedTargets(dimension, now), dimension,
                now, Long.MAX_VALUE,
                mc.player.getX(), mc.player.getY(), mc.player.getZ(), visualRange, filters, profile, openedChests);
            performance.record(DungeonPerformance.Phase.SELECT, System.nanoTime() - started);
            return selected;
        });
    }

    private record RenderOptions(String dimension, int range, DungeonTargetFilters filters, int openedCount) {}

    private RuleCategory visual(String categoryId) {
        return profileStore.current().categories().stream()
            .filter(category -> category.id().equals(categoryId)).findFirst().orElse(null);
    }

    private static boolean categoryEnabled(RuleProfile profile, String upper, String lower) {
        return profile.categories().stream().filter(category -> category.id().equals(upper) || category.id().equals(lower))
            .findFirst().map(RuleCategory::enabled).orElse(true);
    }

    /** One lightweight loaded-entity lookup per selection; no reflection or world traversal per label. */
    private List<DungeonObservation> loadedTargets(String dimension, long now) {
        Map<String, Entity> entities = new HashMap<>();
        for (Entity entity : mc.world.getEntities()) entities.put(entity.getUuidAsString(), entity);
        targetEntities.clear();
        List<DungeonObservation> loaded = new ArrayList<>();
        for (DungeonObservation observation : lifecycle.targets().observations()) {
            if (!dimension.equals(observation.dimension())) continue;
            var position = observation.position();
            if (observation.kind() == DungeonObservation.Kind.BLOCK_ENTITY) {
                if (position == null || !mc.world.getChunkManager().isChunkLoaded(position.getX() >> 4, position.getZ() >> 4)) continue;
                var blockEntity = mc.world.getBlockEntity(position);
                if (blockEntity == null || !Registries.BLOCK_ENTITY_TYPE.getId(blockEntity.getType()).toString().equals(observation.registryId())) continue;
            } else {
                String identity = observation.identity();
                Entity entity = entities.get(identity.substring(identity.lastIndexOf('|') + 1));
                if (entity == null || entity.isRemoved()) continue;
                targetEntities.put(identity, entity);
                position = entity.getBlockPos().toImmutable();
            }
            loaded.add(new DungeonObservation(observation.kind(), dimension, observation.identity(), observation.registryId(),
                observation.className(), observation.name(), position, observation.facts(), now));
        }
        lifecycle.targets().retainLoaded(loaded.stream().map(DungeonObservation::identity).collect(java.util.stream.Collectors.toSet()));
        return loaded;
    }

    private static Vector3d worldPosition(DungeonRenderTarget target, double yOffset) {
        return new Vector3d(target.position().getX() + 0.5,
            target.position().getY() + yOffset, target.position().getZ() + 0.5);
    }

    private static Color color(String categoryId) {
        return switch (categoryId) {
            case "chest", "CHEST" -> new Color(255, 190, 55, 220);
            case "poke_ball", "POKE_BALL" -> new Color(255, 85, 85, 230);
            case "weapon", "WEAPON" -> new Color(90, 220, 255, 230);
            case "guardian", "GUARDIAN" -> new Color(210, 110, 255, 225);
            case "strong_pokemon", "STRONG_POKEMON" -> new Color(120, 255, 145, 225);
            default -> new Color(255, 170, 110, 220);
        };
    }

    private static Color transparent(Color color) {
        return new Color(color.r, color.g, color.b, 35);
    }

    @Override
    public String getInfoString() {
        return Integer.toString(renderTargets().size());
    }

    /** Deterministic scheduler: a pass remains active until its probe reports complete. */
    public static final class ScanScheduler {
        private final int intervalTicks;
        private long nextTick;
        private boolean active;

        public ScanScheduler(int intervalSeconds) {
            intervalTicks = Math.max(1, intervalSeconds) * 20;
            nextTick = intervalTicks;
        }

        public boolean startIfDue(long tick) {
            if (active || tick < nextTick) return false;
            active = true;
            return true;
        }

        public void completeAt(long tick) {
            active = false;
            nextTick = tick + intervalTicks;
        }

        public boolean active() { return active; }
        public void reset() { active = false; nextTick = intervalTicks; }
    }

    @FunctionalInterface
    interface ProbeFactory { Probe create(DiscoveryRarityIndex index); }
    interface Probe {
        DungeonWorldProbe.BudgetedResult scan(String dimension, long now, DungeonWorldProbe.ScanCursor cursor, int budget, boolean crosshair);
        default void clearSession() {}
    }
    @FunctionalInterface
    interface LogSink { void accept(String line); }

    private static final class MinecraftProbe implements Probe {
        private final DungeonWorldProbe probe;
        private final net.minecraft.client.MinecraftClient client;

        private MinecraftProbe(DungeonWorldProbe probe, net.minecraft.client.MinecraftClient client) {
            this.probe = probe;
            this.client = client;
        }

        @Override
        public DungeonWorldProbe.BudgetedResult scan(String dimension, long now, DungeonWorldProbe.ScanCursor cursor, int budget, boolean crosshair) {
            return probe.scan(client, now, cursor, budget, crosshair);
        }

        @Override
        public void clearSession() { probe.clearSessionCache(); }
    }

    static final class Lifecycle {
        private final int intervalSeconds;
        private final boolean crosshair;
        private final ProbeFactory factory;
        private final LogSink sink;
        private final LongSupplier clock;
        private final boolean verboseLogs;
        private RuntimeState state;
        private Probe probe;

        Lifecycle(int intervalSeconds, boolean crosshair, ProbeFactory factory, LogSink sink, LongSupplier clock) {
            this(intervalSeconds, crosshair, factory, sink, clock, true);
        }

        Lifecycle(int intervalSeconds, boolean crosshair, ProbeFactory factory, LogSink sink, LongSupplier clock,
                  boolean verboseLogs) {
            this.intervalSeconds = intervalSeconds;
            this.crosshair = crosshair;
            this.factory = factory;
            this.sink = sink;
            this.clock = clock;
            this.verboseLogs = verboseLogs;
        }

        void activate(DiscoveryRarityIndex rarity) { activate(rarity, RuleProfile.disabled()); }
        void activate(DiscoveryRarityIndex rarity, RuleProfile profile) {
            state = new RuntimeState(rarity, intervalSeconds, profile);
            probe = factory.create(rarity);
            state.log(DungeonProbeText.session("pending"));
            emit(8);
        }

        void tick(long tick, String dimension) {
            if (state == null || probe == null) return;
            state.begin(dimension);
            if (!state.scheduler().active() && !state.scheduler().startIfDue(tick)) return;
            DungeonWorldProbe.BudgetedResult result = probe.scan(dimension, clock.getAsLong(), state.cursor(), WORK_BUDGET_PER_TICK, crosshair);
            state.setCursor(result.cursor());
            result.result().observations().forEach(state.index()::observe);
            result.result().observations().forEach(state.targets()::observe);
            if (result.complete()) {
                if (verboseLogs) state.log(DungeonProbeText.scan(result.result(), state.index().summary()));
                state.scheduler().completeAt(tick);
            }
            emit(LOG_DRAIN_PER_TICK);
        }

        void deactivate() {
            if (state != null) state.clear();
            if (probe != null) probe.clearSession();
            probe = null;
        }

        void worldChanged() {
            if (state != null) state.clear();
            if (probe != null) probe.clearSession();
        }

        String dimension() { return state == null ? "unavailable" : state.dimension(); }
        DungeonObservationIndex index() { return state.index(); }
        DungeonTargetIndex targets() { return state.targets(); }

        private void emit(int limit) {
            if (state == null || !verboseLogs) return;
            for (String line : state.drainLogLines(limit, true)) sink.accept(line);
        }
    }

    /** Small testable runtime owner; it contains no Minecraft or interaction API. */
    public static final class RuntimeState {
        private final DungeonObservationIndex index = new DungeonObservationIndex();
        private DungeonTargetIndex targets = new DungeonTargetIndex();
        private final ArrayDeque<String> logs = new ArrayDeque<>();
        private final DiscoveryRarityIndex rarityIndex;
        private DungeonWorldProbe.ScanCursor cursor = new DungeonWorldProbe.ScanCursor();
        private ScanScheduler scheduler = new ScanScheduler(5);
        private String dimension = "unavailable";

        public RuntimeState() { this(DiscoveryRarityIndex.empty()); }
        public RuntimeState(DiscoveryRarityIndex rarityIndex) { this.rarityIndex = rarityIndex; }
        public RuntimeState(DiscoveryRarityIndex rarityIndex, int intervalSeconds, RuleProfile profile) {
            this(rarityIndex, intervalSeconds);
            this.targets = new DungeonTargetIndex(profile);
        }
        public RuntimeState(DiscoveryRarityIndex rarityIndex, int intervalSeconds) {
            this.rarityIndex = rarityIndex;
            this.scheduler = new ScanScheduler(intervalSeconds);
        }

        public void begin(String dimension) {
            if (!this.dimension.equals(dimension)) {
                this.dimension = dimension;
                index.beginDimension(dimension);
                targets.clear();
                cursor = new DungeonWorldProbe.ScanCursor();
                scheduler.reset();
                log(DungeonProbeText.dimension(dimension));
            }
        }
        public String dimension() { return dimension; }
        public DungeonObservationIndex index() { return index; }
        public DungeonTargetIndex targets() { return targets; }
        public DiscoveryRarityIndex rarityIndex() { return rarityIndex; }
        public ScanScheduler scheduler() { return scheduler; }
        public DungeonWorldProbe.ScanCursor cursor() { return cursor; }
        public void setCursor(DungeonWorldProbe.ScanCursor cursor) { this.cursor = cursor; }
        public void log(String line) { if (logs.size() < 256) logs.addLast(line); }
        public List<String> logs() { return List.copyOf(logs); }
        public void clearLogs() { logs.clear(); }
        public List<String> drainLogLines(int limit, boolean includeObservations) {
            List<String> lines = new ArrayList<>();
            while (lines.size() < Math.max(0, limit) && !logs.isEmpty()) lines.add(logs.removeFirst());
            if (includeObservations && lines.size() < limit) {
                for (DungeonObservation observation : index.drainChanges(limit - lines.size())) lines.add(DungeonProbeText.observation(observation));
            }
            return List.copyOf(lines);
        }
        public void clear() { index.beginDimension("unavailable"); targets.clear(); logs.clear(); cursor = new DungeonWorldProbe.ScanCursor(); scheduler.reset(); dimension = "unavailable"; }
    }
}
