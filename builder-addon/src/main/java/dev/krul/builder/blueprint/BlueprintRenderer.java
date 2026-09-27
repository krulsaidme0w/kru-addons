package dev.krul.builder.blueprint;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.krul.builder.minecraft.MinecraftPlanRules;
import dev.krul.builder.model.*;
import dev.krul.builder.project.*;
import java.util.*;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import org.lwjgl.opengl.GL11;

/** Renders a read-only textured target ghost and a separate outline for replacement/removal. */
public final class BlueprintRenderer {
    private final BuildPlan plan;
    private final Bounds bounds;
    private final BlueprintDiff.Selection selection;
    private final Map<BlockState, List<BakedQuad>> quads = new HashMap<>();
    private static final MinecraftClient MC = MinecraftClient.getInstance();

    public BlueprintRenderer(BuildPlan plan, Bounds bounds, BlueprintDiff.Selection selection) {
        this.plan = Objects.requireNonNull(plan);
        this.bounds = Objects.requireNonNull(bounds);
        this.selection = Objects.requireNonNull(selection);
        var errors = PlanValidator.validate(plan, bounds, new MinecraftPlanRules());
        if (!errors.isEmpty()) throw new IllegalArgumentException(String.join("; ", errors));
    }

    public void render(Render3DEvent event, WorldSnapshot snapshot, boolean throughWalls) {
        if (snapshot == null || MC.world == null || dev.krul.builder.minecraft.WorldCapture.suppressPreviewThisFrame()) return;
        if (!snapshot.bounds().equals(bounds)) return;
        var differences = BlueprintDiff.cells(plan, snapshot, selection);
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        quads.clear();
        boolean textured = false;
        var removals = new ArrayList<BlueprintDiff.Difference>();
        for (var difference : differences) {
            BlockState target = resolve(difference.target());
            if (difference.kind() == BlueprintDiff.Kind.REMOVE) { removals.add(difference); continue; }
            if (difference.kind() != BlueprintDiff.Kind.PLACE && difference.kind() != BlueprintDiff.Kind.REPLACE) continue;
            if (target == null) continue;
            textured |= append(buffer, event, target, difference.absolute(), difference.kind() == BlueprintDiff.Kind.REPLACE ? .42f : .58f);
        }
        if (textured) draw(buffer, throughWalls);
        for (var difference : differences) {
            if (difference.kind() == BlueprintDiff.Kind.REPLACE) outline(event, difference.absolute(), 0xffff5050);
        }
        for (var difference : removals) outline(event, difference.absolute(), 0xffffb347);
    }

    private BlockState resolve(StateKey key) {
        var id = Identifier.of(key.id());
        if (!Registries.BLOCK.containsId(id)) return null;
        var block = Registries.BLOCK.get(id);
        if (block.getStateManager().getProperties().size() != key.properties().size()) return null;
        BlockState state = block.getDefaultState();
        for (Property<?> property : block.getStateManager().getProperties()) {
            String value = key.properties().get(property.getName());
            if (value == null) return null;
            state = applyProperty(state, property, value);
            if (state == null) return null;
        }
        return state;
    }

    private static <T extends Comparable<T>> BlockState applyProperty(BlockState state, Property<T> property, String value) {
        var parsed = property.parse(value);
        if (parsed.isEmpty() || !property.name(parsed.get()).equals(value)) return null;
        return state.with(property, parsed.get());
    }

    private boolean append(BufferBuilder buffer, Render3DEvent event, BlockState state, Cell cell, float alpha) {
        var model = quads.computeIfAbsent(state, value -> {
            var baked = MC.getBlockRenderManager().getModel(value);
            var result = new ArrayList<BakedQuad>();
            for (Direction direction : Direction.values()) result.addAll(baked.getQuads(value, direction, Random.create(42)));
            result.addAll(baked.getQuads(value, null, Random.create(42)));
            return result;
        });
        BlockPos pos = new BlockPos(cell.x(), cell.y(), cell.z());
        for (BakedQuad quad : model) {
            int tint = quad.hasColor() ? MC.getBlockColors().getColor(state, MC.world, pos, quad.getColorIndex()) : 0xffffff;
            float shade = switch (quad.getFace()) { case DOWN -> .55f; case NORTH, SOUTH -> .8f; case EAST, WEST -> .7f; default -> 1f; };
            int[] data = quad.getVertexData(); int stride = data.length / 4;
            for (int i = 0; i < 4; i++) {
                int n = i * stride;
                float x = Float.intBitsToFloat(data[n]), y = Float.intBitsToFloat(data[n + 1]), z = Float.intBitsToFloat(data[n + 2]);
                buffer.vertex(event.matrices.peek().getPositionMatrix(), (float) (cell.x() - event.offsetX + x),
                        (float) (cell.y() - event.offsetY + y), (float) (cell.z() - event.offsetZ + z))
                    .texture(Float.intBitsToFloat(data[n + 4]), Float.intBitsToFloat(data[n + 5]))
                    .color(((tint >> 16) & 255) / 255f * shade, ((tint >> 8) & 255) / 255f * shade,
                        (tint & 255) / 255f * shade, alpha);
            }
        }
        return !model.isEmpty();
    }

    private static void draw(BufferBuilder buffer, boolean throughWalls) {
        var built = buffer.endNullable();
        if (built == null) return;
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND), depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE), depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        try {
            RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc(); RenderSystem.depthMask(false);
            if (throughWalls) RenderSystem.disableDepthTest(); else RenderSystem.enableDepthTest();
            RenderSystem.disableCull(); RenderSystem.setShader(GameRenderer::getPositionTexColorProgram);
            RenderSystem.setShaderTexture(0, SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE);
            BufferRenderer.drawWithGlobalProgram(built);
        } finally {
            RenderSystem.depthMask(depthMask);
            if (depth) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
            if (cull) RenderSystem.enableCull(); else RenderSystem.disableCull();
            if (blend) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
        }
    }

    private static void outline(Render3DEvent event, Cell cell, int color) {
        var c = new meteordevelopment.meteorclient.utils.render.color.Color(color);
        event.renderer.box(cell.x(), cell.y(), cell.z(), cell.x() + 1d, cell.y() + 1d, cell.z() + 1d,
            new meteordevelopment.meteorclient.utils.render.color.Color(0, 0, 0, 0), c, ShapeMode.Lines, 0);
    }
}
