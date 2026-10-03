package dev.krul.cobblemon.battle.matchup;

import dev.krul.cobblemon.battle.PokemonType;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;
import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.*;

class MatchupNativeIconsTest {
    private static final MatchupPanelLayout.Rect BOX = new MatchupPanelLayout.Rect(10, 20, 25, 25);

    @Test void uninitializedNativeLayerListIsAnIdleModelContext() {
        assertTrue(MatchupNativeIcons.idleLayerContext(null, null, null));
        assertTrue(MatchupNativeIcons.idleLayerContext(null, null, List.of()));
        assertFalse(MatchupNativeIcons.idleLayerContext(new Object(), null, null));
        assertFalse(MatchupNativeIcons.idleLayerContext(null, new Object(), null));
        assertFalse(MatchupNativeIcons.idleLayerContext(null, null, List.of(new Object())));
        assertFalse(MatchupNativeIcons.idleLayerContext(null, null, new Object()));
    }

    @Test void nativeFailureStillUnwindsAllStateInOrder() throws Exception {
        var order = new ArrayList<String>();
        assertEquals(false, isolated(order, true, -1));
        assertEquals(List.of("draw", "layers", "pose", "context", "lighting", "matrices", "scissor"), order);
    }

    @Test void failedCleanupDoesNotSkipRemainingCleanupOrReportSuccess() throws Exception {
        for (int failure = 0; failure < 6; failure++) {
            var order = new ArrayList<String>();
            assertEquals(false, isolated(order, false, failure));
            assertEquals(List.of("draw", "layers", "pose", "context", "lighting", "matrices", "scissor"), order);
        }
    }

    @Test void successfulDrawAlsoCleansSharedState() throws Exception {
        var order = new ArrayList<String>();
        assertEquals(true, isolated(order, false, -1));
        assertEquals(List.of("draw", "layers", "pose", "context", "lighting", "matrices", "scissor"), order);
    }

    @Test void failedNativePushAndPoseCleanupPreserveCallerMatricesAndClip() throws Exception {
        var matrices = new MatrixStack();
        matrices.translate(7, 11, 13);
        var entry = matrices.peek();
        var position = new org.joml.Matrix4f(entry.getPositionMatrix());
        var normal = new org.joml.Matrix3f(entry.getNormalMatrix());
        // The draw adds its own clip below the already active caller clip.
        var clips = new java.util.ArrayDeque<String>();
        clips.push("host");
        Runnable draw = () -> {
            matrices.push();
            clips.push("portrait");
            matrices.scale(2, 3, 4);
            matrices.push(); // Native layer failed before balancing its push.
            throw new IllegalStateException("native model");
        };
        var restore = bridge().getClass().getDeclaredMethod("restoreMatrices", MatrixStack.class, MatrixStack.Entry.class);
        restore.setAccessible(true);
        Runnable unwind = () -> {
            try { restore.invoke(null, matrices, entry); }
            catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        };
        assertEquals(false, runCallbacks(draw, () -> { throw new LinkageError("pose reset"); }, unwind, clips::pop));
        assertSame(entry, matrices.peek());
        assertEquals(position, matrices.peek().getPositionMatrix());
        assertEquals(normal, matrices.peek().getNormalMatrix());
        assertEquals(List.of("host"), List.copyOf(clips));
    }

    private static Object runCallbacks(Runnable draw, Runnable... cleanup) throws Exception {
        Class<?> action = Class.forName("dev.krul.cobblemon.battle.matchup.MatchupNativeIcons$RenderAction");
        var actions = java.lang.reflect.Array.newInstance(action, cleanup.length);
        for (int i = 0; i < cleanup.length; i++) {
            Runnable callback = cleanup[i];
            java.lang.reflect.Array.set(actions, i, Proxy.newProxyInstance(action.getClassLoader(), new Class<?>[]{action},
                (proxy, method, args) -> { callback.run(); return null; }));
        }
        Object nativeDraw = Proxy.newProxyInstance(action.getClassLoader(), new Class<?>[]{action},
            (proxy, method, args) -> { draw.run(); return null; });
        var run = bridge().getClass().getDeclaredMethod("renderIsolated", action, actions.getClass());
        run.setAccessible(true);
        return run.invoke(null, nativeDraw, actions);
    }

    private static Object isolated(List<String> order, boolean drawFails, int cleanupFailure) throws Exception {
        Class<?> action = Class.forName("dev.krul.cobblemon.battle.matchup.MatchupNativeIcons$RenderAction");
        var cleanup = java.lang.reflect.Array.newInstance(action, 6);
        String[] names = {"layers", "pose", "context", "lighting", "matrices", "scissor"};
        for (int i = 0; i < names.length; i++) {
            String name = names[i];
            boolean fail = i == cleanupFailure;
            java.lang.reflect.Array.set(cleanup, i, Proxy.newProxyInstance(action.getClassLoader(), new Class<?>[]{action},
                (proxy, method, args) -> { order.add(name); if (fail) throw new LinkageError("broken cleanup"); return null; }));
        }
        Object draw = Proxy.newProxyInstance(action.getClassLoader(), new Class<?>[]{action},
            (proxy, method, args) -> { order.add("draw"); if (drawFails) throw new java.lang.reflect.InvocationTargetException(new IllegalStateException("model layer")); return null; });
        var run = bridge().getClass().getDeclaredMethod("renderIsolated", action, cleanup.getClass());
        run.setAccessible(true);
        return run.invoke(null, draw, cleanup);
    }

    // A hard Cobblemon dependency or an escaping class/method resolution failure
    // would break this boundary. No Minecraft client or graphics context is fabricated.
    @Test void missingOptionalModLeavesBothCapabilitiesUnavailable() throws Exception {
        var icons = bridge();
        for (String capability : new String[]{"portraitApi", "assetApi", "typeApi"}) {
            var resolve = icons.getClass().getDeclaredMethod(capability);
            resolve.setAccessible(true);
            assertNull(resolve.invoke(icons));
            assertNull(resolve.invoke(icons));
            icons.getClass().getMethod("clear").invoke(icons);
            assertNull(resolve.invoke(icons));
        }
    }

    // Invalid input must remain a caller-owned fallback, including empty/overflowing rectangles.
    @Test void invalidInputIsANoDrawFallback() throws Exception {
        var icons = bridge();
        var valid = icons.getClass().getDeclaredMethod("valid", MatchupPanelLayout.Rect.class);
        valid.setAccessible(true);
        assertEquals(true, valid.invoke(null, BOX));
        assertEquals(true, valid.invoke(null, new MatchupPanelLayout.Rect(-5, -5, 10, 10)));
        for (var rect : new MatchupPanelLayout.Rect[]{null,
            new MatchupPanelLayout.Rect(0, 0, 0, 25), new MatchupPanelLayout.Rect(0, 0, 25, -1),
            new MatchupPanelLayout.Rect(Integer.MAX_VALUE, 0, 25, 25)}) {
            assertEquals(false, valid.invoke(null, rect));
        }
        assertEquals(false, portrait(icons, null, BOX, "cobblemon:ralts", Set.of()));
        assertEquals(false, type(icons, null, BOX, PokemonType.FAIRY));
        assertEquals(false, type(icons, null, BOX, null));
    }

    // Dropping aspects, interpreting names as properties, or retaining a mutable
    // caller set would reuse a portrait for the wrong visible species/form.
    @Test void portraitKeysUseOnlyResourceIdsAndImmutableAspects() throws Exception {
        var icons = bridge();
        var key = icons.getClass().getDeclaredMethod("profileKey", String.class, Set.class);
        key.setAccessible(true);
        var aspects = new HashSet<>(Set.of("shiny", "female"));
        var original = key.invoke(null, "ralts", aspects);
        assertNotNull(original);
        aspects.clear();
        assertEquals(original, key.invoke(null, "cobblemon:ralts", Set.of("female", "shiny")));
        assertNotEquals(original, key.invoke(null, "cobblemon:ralts", Set.of()));
        assertNotEquals(original, key.invoke(null, "other:ralts", Set.of("shiny", "female")));
        for (String value : new String[]{null, "", "?", "My Ralts", "Ralts", "cobblemon:", "ralts shiny=true"}) {
            assertNull(key.invoke(null, value, Set.of()));
        }
    }

    private static Object bridge() {
        return assertDoesNotThrow(() -> Class.forName(
            "dev.krul.cobblemon.battle.matchup.MatchupNativeIcons")
            .getConstructor().newInstance(), "The optional bridge must load without Cobblemon installed");
    }

    private static Object portrait(Object icons, DrawContext context, MatchupPanelLayout.Rect rect,
                                   String species, Set<String> aspects) throws Exception {
        return icons.getClass().getMethod("portrait", DrawContext.class, MatchupPanelLayout.Rect.class,
            String.class, Set.class).invoke(icons, context, rect, species, aspects);
    }

    private static Object type(Object icons, DrawContext context, MatchupPanelLayout.Rect rect,
                               PokemonType type) throws Exception {
        return icons.getClass().getMethod("type", DrawContext.class, MatchupPanelLayout.Rect.class,
            PokemonType.class).invoke(icons, context, rect, type);
    }
}
