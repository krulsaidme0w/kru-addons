package dev.krul.cobblemon.battle.matchup;

import dev.krul.cobblemon.battle.PokemonType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import org.joml.Quaternionf;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Optional, render-thread-only native icons. No Cobblemon classes link at compile time.
 * Supply a registry species ID (never a display name/nickname) and the visible aspects.
 * False leaves the fallback to the caller. Own one instance per panel; call {@link #clear()}
 * after a resource reload or when discarding its battle. No world/Pokemon entities are retained.
 *
 * ABI and transforms checked against Abes-Hutts 1.21.1.1.7.3-1.0.5:
 * StorageSlot.renderPokemon uses a 25px slot, center X/top+1, scale(2.5,2.5,1),
 * Euler XYZ (13,35,0), and drawProfilePokemon scale=4.5. Its default mask 32648
 * supplies PROFILE, profileTransform=true, baseScale=false, RGBA=1 and head angles=0.
 * TypeIcon's small diameter is 18 texels at scale .5 (9 GUI pixels); this fork
 * additionally requires the final isStellar boolean, which must be false here.
 */
public final class MatchupNativeIcons {
    private static final String NATIVE = "com.cobblemon.mod.common.";
    private static final int MAX_PORTRAITS = 128;

    private record ProfileKey(Identifier species, Set<String> aspects) {}
    // A null renderable memoizes a failed asset for this exact registry species object.
    private record Profile(Object species, Object resolver, Object renderable, Object state) {}
    private record PortraitApi(Method species, Constructor<?> renderable, Constructor<?> state,
                               Object pose, Method draw, CleanupApi cleanup) {}
    private record CleanupApi(Method layers, Method pose, Field context, Method currentState,
                              Method buffer, Method currentLayers) {}
    private record AssetApi(Object repository, Method variations, Method aspects, Object profileSprite,
                            Method sprite, Method texture, Method poser, Method exists) {}
    private record TypeApi(Class<?> types, Constructor<?> icon, Method render) {}

    private final Map<ProfileKey, Profile> portraits = new LinkedHashMap<>(16, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<ProfileKey, Profile> eldest) {
            return size() > MAX_PORTRAITS;
        }
    };
    private final Map<PokemonType, Optional<Object>> types = new EnumMap<>(PokemonType.class);
    private boolean portraitChecked, assetChecked, typeChecked;
    private PortraitApi portraitApi;
    private AssetApi assetApi;
    private TypeApi typeApi;

    public boolean portrait(DrawContext ctx, MatchupPanelLayout.Rect rect, String speciesId, Set<String> aspects) {
        if (ctx == null || !valid(rect)) return false;
        ProfileKey key = profileKey(speciesId, aspects);
        if (key == null) return false;
        PortraitApi api = portraitApi();
        if (api == null) return false;
        AssetApi assets = assetApi();
        if (assets == null) return false;

        Profile profile;
        try {
            // Exact identifier lookup only. Do not parse PokemonProperties or use getByName.
            Object species = api.species.invoke(null, key.species);
            if (species == null) return false;
            Object variations = assets.variations.invoke(assets.repository);
            Object resolver = variations instanceof Map<?, ?> map ? map.get(key.species) : null;
            if (resolver == null) return false;
            profile = portraits.get(key);
            // Registry/resource reloads replace species/resolvers; discard old renderables and failures.
            if (profile == null || profile.species != species || profile.resolver != resolver) {
                portraits.put(key, new Profile(species, resolver, null, null));
                Object state = api.state.newInstance();
                assets.aspects.invoke(state, key.aspects);
                // Repository.getPoser substitutes an unrelated model on failure. Its exact
                // resolver throws instead; check it and the selected texture before any drawing.
                Object sprite = assets.sprite.invoke(resolver, state, assets.profileSprite);
                if (sprite != null) {
                    if (!Boolean.TRUE.equals(assets.exists.invoke(null, sprite))) return false;
                } else {
                    Object texture = assets.texture.invoke(resolver, state);
                    if (texture == null || !Boolean.TRUE.equals(assets.exists.invoke(null, texture))
                        || assets.poser.invoke(resolver, state) == null) return false;
                }
                profile = new Profile(species, resolver,
                    api.renderable.newInstance(species, key.aspects, ItemStack.EMPTY), state);
                portraits.put(key, profile);
            }
            if (profile.renderable == null) return false;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return false;
        }

        // Resolve the current exact poser again: the renderer resolves it on every call too.
        // Capture before drawProfilePokemon can replace context, animate, or throw before
        // setting state.currentModel. Never enter an already active native layer context.
        Object model, previousContext;
        try {
            Object sprite = assets.sprite.invoke(profile.resolver, profile.state, assets.profileSprite);
            model = sprite == null
                ? assets.poser.invoke(profile.resolver, profile.state) : null;
            if (sprite == null && model == null) return false;
            if (model != null) {
                if (api.cleanup.currentState.invoke(model) != null || api.cleanup.buffer.invoke(model) != null) return false;
                Object layers = api.cleanup.currentLayers.invoke(model);
                if (!(layers instanceof Iterable<?> iterable) || iterable.iterator().hasNext()) return false;
            }
            previousContext = model == null ? null : api.cleanup.context.get(model);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return false;
        }

        MatrixStack matrices = ctx.getMatrices();
        MatrixStack.Entry previous = matrices.peek();
        var dispatcher = MinecraftClient.getInstance().getEntityRenderDispatcher();
        Quaternionf previousRotation = new Quaternionf(dispatcher.getRotation());
        Profile selected = profile;
        boolean[] clipped = {false};
        boolean rendered = renderIsolated(() -> {
            matrices.push();
            dev.krul.cobblemon.gui.overlay.GuiScissor.enable(ctx, rect.x(), rect.y(), rect.x() + rect.width(), rect.y() + rect.height());
            clipped[0] = true;
            float side = Math.min(rect.width(), rect.height());
            float ratio = side / 25f;
            matrices.translate(rect.x() + rect.width() / 2.0, rect.y() + (rect.height() - side) / 2.0 + ratio, 0);
            matrices.scale(2.5f * ratio, 2.5f * ratio, 1f);
            // Native rendering conjugates its input quaternion, so never share a mutable rotation.
            Quaternionf rotation = new Quaternionf().rotationXYZ((float) Math.toRadians(13), (float) Math.toRadians(35), 0);
            api.draw.invoke(null, selected.renderable, matrices, rotation, api.pose, selected.state,
                0f, 4.5f, true, false, 1f, 1f, 1f, 1f, 0f, 0f);
        },
            () -> { if (model != null) api.cleanup.layers.invoke(model); },
            () -> { if (model != null) api.cleanup.pose.invoke(model); },
            // context is a public nullable backing field; its Kotlin getter throws when unset.
            () -> { if (model != null) api.cleanup.context.set(model, previousContext); },
            () -> dispatcher.setRotation(previousRotation),
            // Match the native success epilogue (class_308.method_24211), even after failure.
            DiffuseLighting::enableGuiDepthLighting,
            () -> restoreMatrices(matrices, previous),
            () -> { if (clipped[0]) ctx.disableScissor(); });
        if (!rendered) {
            portraits.put(key, new Profile(profile.species, profile.resolver, null, null));
        }
        return rendered;
    }

    /** Centers a native 9px icon, shrinking only when the supplied rectangle is smaller. */
    public boolean type(DrawContext ctx, MatchupPanelLayout.Rect rect, PokemonType type) {
        if (ctx == null || !valid(rect) || type == null) return false;
        TypeApi api = typeApi();
        if (api == null) return false;
        Optional<Object> icon = types.get(type);
        if (icon == null) {
            icon = Optional.empty();
            try {
                Object elementalType = api.types.getField(type.name()).get(null);
                // Null primary type selects the fork's Stellar texture, not an unknown icon.
                if (elementalType != null) {
                    icon = Optional.of(api.icon.newInstance(0, 0, elementalType, null,
                        false, true, 15f, 7.5f, 1f, false));
                }
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                // Cache missing fields/constructor failures as well as successful icons.
            }
            types.put(type, icon);
        }
        if (icon.isEmpty()) return false;

        MatrixStack matrices = ctx.getMatrices();
        MatrixStack.Entry previous = matrices.peek();
        matrices.push();
        try {
            float side = Math.min(9, Math.min(rect.width(), rect.height()));
            matrices.translate(rect.x() + (rect.width() - side) / 2.0, rect.y() + (rect.height() - side) / 2.0, 0);
            matrices.scale(side / 9f, side / 9f, 1f);
            api.render.invoke(icon.get(), ctx);
            return true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            types.put(type, Optional.empty());
            return false;
        } finally {
            restoreMatrices(matrices, previous);
        }
    }

    /** Drop renderable/asset failures and retry optional capabilities after a lifecycle change. */
    public void clear() {
        portraits.clear();
        types.clear();
        portraitChecked = assetChecked = typeChecked = false;
        portraitApi = null;
        assetApi = null;
        typeApi = null;
    }

    private static ProfileKey profileKey(String speciesId, Set<String> aspects) {
        if (speciesId == null || speciesId.isBlank() || speciesId.length() > 256) return null;
        Identifier species = Identifier.tryParse(speciesId.indexOf(':') < 0 ? "cobblemon:" + speciesId : speciesId);
        if (species == null || species.getPath().isEmpty()) return null;
        if (aspects == null) aspects = Set.of();
        // Bound both entry count and key size; never mutate or normalize aspect information.
        if (aspects.size() > 64) return null;
        for (String aspect : aspects) if (aspect == null || aspect.isBlank() || aspect.length() > 256) return null;
        return new ProfileKey(species, Set.copyOf(aspects));
    }

    private static boolean valid(MatchupPanelLayout.Rect rect) {
        return rect != null && rect.width() > 0 && rect.height() > 0
            && (long) rect.x() + rect.width() <= Integer.MAX_VALUE
            && (long) rect.y() + rect.height() <= Integer.MAX_VALUE;
    }

    private static void restoreMatrices(MatrixStack matrices, MatrixStack.Entry previous) {
        // Also unwind an unbalanced native push if an asset renderer threw mid-render.
        while (matrices.peek() != previous && !matrices.isEmpty()) matrices.pop();
    }

    @FunctionalInterface
    interface RenderAction { void run() throws ReflectiveOperationException; }

    /** No native cleanup failure may skip GUI cleanup or turn a partial render into success. */
    private static boolean renderIsolated(RenderAction draw, RenderAction... cleanup) {
        boolean success = true;
        try {
            draw.run();
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            success = false;
        } finally {
            for (RenderAction action : cleanup) {
                try { action.run(); }
                catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) { success = false; }
            }
        }
        return success;
    }

    private PortraitApi portraitApi() {
        if (!portraitChecked) {
            portraitChecked = true;
            try {
                Class<?> species = nativeClass("pokemon.Species");
                Class<?> renderable = nativeClass("pokemon.RenderablePokemon");
                Class<?> pose = nativeClass("entity.PoseType");
                Class<?> state = nativeClass("client.render.models.blockbench.PosableState");
                Class<?> model = nativeClass("client.render.models.blockbench.PosableModel");
                // Capability fails closed before any draw if the verified recovery ABI is absent.
                CleanupApi cleanup = new CleanupApi(model.getMethod("resetLayerContext"),
                    model.getMethod("setDefault"), model.getField("context"),
                    model.getMethod("getCurrentState"), model.getMethod("getBufferProvider"),
                    model.getMethod("getCurrentLayers"));
                if (cleanup.layers.getReturnType() != void.class || cleanup.pose.getReturnType() != void.class
                    || Modifier.isStatic(cleanup.layers.getModifiers()) || Modifier.isStatic(cleanup.pose.getModifiers())
                    || Modifier.isFinal(cleanup.context.getModifiers()) || Modifier.isStatic(cleanup.context.getModifiers())
                    || cleanup.context.getType() != nativeClass("client.render.models.blockbench.repository.RenderContext")) {
                    return null;
                }
                portraitApi = new PortraitApi(
                    nativeClass("api.pokemon.PokemonSpecies").getMethod("getByIdentifier", Identifier.class),
                    renderable.getConstructor(species, Set.class, ItemStack.class),
                    nativeClass("client.render.models.blockbench.FloatingState").getConstructor(),
                    pose.getField("PROFILE").get(null),
                    nativeClass("client.gui.PokemonGuiUtilsKt").getMethod("drawProfilePokemon", renderable,
                        MatrixStack.class, Quaternionf.class, pose, state, float.class, float.class,
                        boolean.class, boolean.class, float.class, float.class, float.class, float.class,
                        float.class, float.class), cleanup);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                portraitApi = null;
            }
        }
        return portraitApi;
    }

    private TypeApi typeApi() {
        if (!typeChecked) {
            typeChecked = true;
            try {
                Class<?> elementalType = nativeClass("api.types.ElementalType");
                Class<?> icon = nativeClass("client.gui.TypeIcon");
                typeApi = new TypeApi(nativeClass("api.types.ElementalTypes"),
                    icon.getConstructor(Number.class, Number.class, elementalType, elementalType,
                        boolean.class, boolean.class, float.class, float.class, float.class, boolean.class),
                    icon.getMethod("render", DrawContext.class));
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                typeApi = null;
            }
        }
        return typeApi;
    }

    private AssetApi assetApi() {
        if (!assetChecked) {
            assetChecked = true;
            try {
                Class<?> repository = nativeClass("client.render.models.blockbench.repository.VaryingModelRepository");
                Class<?> resolver = nativeClass("client.render.VaryingRenderableResolver");
                Class<?> state = nativeClass("client.render.models.blockbench.PosableState");
                Class<?> sprite = nativeClass("client.render.SpriteType");
                assetApi = new AssetApi(repository.getField("INSTANCE").get(null), repository.getMethod("getVariations"),
                    state.getMethod("setCurrentAspects", Set.class), sprite.getField("PROFILE").get(null),
                    resolver.getMethod("getSprite", state, sprite), resolver.getMethod("getTexture", state),
                    resolver.getMethod("getPoser", state),
                    nativeClass("client.util.ClientDistributionUtilsKt").getMethod("exists", Identifier.class));
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                assetApi = null;
            }
        }
        return assetApi;
    }

    private static Class<?> nativeClass(String name) throws ClassNotFoundException {
        return Class.forName(NATIVE + name, false, MatchupNativeIcons.class.getClassLoader());
    }
}
