package dev.krul.builder.agent;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class BuilderDynamicToolsTest {
    @Test void registersFourStrictReadOnlyToolsIncludingBoundedPlanInspection() {
        JsonArray tools = BuilderDynamicTools.definitions();
        assertEquals(4, tools.size());
        assertEquals("list_block_mods", name(tools, 0));
        assertEquals("search_blocks", name(tools, 1));
        assertEquals("describe_block", name(tools, 2));
        assertEquals("inspect_build_plan", name(tools, 3));
        JsonObject inspectSchema = tools.get(3).getAsJsonObject().getAsJsonObject("inputSchema");
        assertEquals(List.of("plan"), inspectSchema.getAsJsonArray("required").asList().stream().map(JsonElement::getAsString).toList());
        assertEquals(Boolean.FALSE, inspectSchema.getAsJsonObject("properties").getAsJsonObject("plan").get("additionalProperties").getAsBoolean());
        for (JsonElement tool : tools) {
            JsonObject schema = tool.getAsJsonObject().getAsJsonObject("inputSchema");
            assertEquals(Boolean.FALSE, schema.get("additionalProperties").getAsBoolean());
        }
    }

    @Test void rejectsUnknownToolAndUnexpectedArgumentsBeforeHandler() {
        AtomicInteger calls = new AtomicInteger();
        var tools = new BuilderDynamicTools((name, args, current) -> { calls.incrementAndGet(); return text("ok"); });
        assertFalse(tools.dispatch("run_shell", new JsonObject()).get("success").getAsBoolean());
        assertFalse(tools.dispatch("list_block_mods", obj("page", 1, "extra", true)).get("success").getAsBoolean());
        assertFalse(tools.dispatch("list_block_mods", obj("page", -1)).get("success").getAsBoolean());
        assertFalse(tools.dispatch("search_blocks", obj("query", "a", "mod", "x", "page", 10_001)).get("success").getAsBoolean());
        assertFalse(tools.dispatch("describe_block", obj("id", 7)).get("success").getAsBoolean());
        assertFalse(tools.dispatch("describe_block", new JsonObject()).get("success").getAsBoolean());
        assertFalse(tools.dispatch("inspect_build_plan", obj("plan", "not-an-object")).get("success").getAsBoolean());
        assertFalse(tools.dispatch("inspect_build_plan", new JsonObject()).get("success").getAsBoolean());
        assertFalse(tools.dispatch("inspect_build_plan", obj("plan", new JsonObject(), "extra", true)).get("success").getAsBoolean());
        assertFalse(tools.dispatch("list_block_mods", obj("page", 0), () -> false).get("success").getAsBoolean());
        assertEquals(0, calls.get());
    }

    @Test void validatesToolOutputAndCapsImagesAndResponse() {
        var tools = new BuilderDynamicTools((name, args, current) -> text("ok"));
        JsonObject good = new JsonObject(); good.addProperty("success", true);
        JsonArray items = new JsonArray(); JsonObject t = new JsonObject(); t.addProperty("type", "inputText"); t.addProperty("text", "block"); items.add(t); good.add("contentItems", items);
        assertTrue(tools.validateResult(good).get("success").getAsBoolean());
        JsonObject bad = good.deepCopy(); JsonObject image = new JsonObject(); image.addProperty("type", "inputImage"); image.addProperty("imageUrl", "https://invalid"); bad.getAsJsonArray("contentItems").add(image);
        assertFalse(tools.validateResult(bad).get("success").getAsBoolean());
        byte[] png = new byte[24]; ByteBuffer b = ByteBuffer.wrap(png).order(ByteOrder.BIG_ENDIAN); b.putInt(0x89504e47); b.putInt(0x0d0a1a0a); b.position(12); b.putInt(0x49484452); b.putInt(256); b.putInt(224);
        JsonObject withImage = good.deepCopy(); JsonObject imageItem = new JsonObject(); imageItem.addProperty("type", "inputImage"); imageItem.addProperty("imageUrl", "data:image/png;base64," + Base64.getEncoder().encodeToString(png)); withImage.getAsJsonArray("contentItems").add(imageItem);
        assertTrue(tools.validateResult(withImage).get("success").getAsBoolean());
        JsonObject huge = text("x".repeat(BuilderDynamicTools.MAX_RESPONSE_BYTES));
        assertFalse(tools.validateResult(huge).get("success").getAsBoolean());
    }

    private static String name(JsonArray array, int i) { return array.get(i).getAsJsonObject().get("name").getAsString(); }
    private static JsonObject text(String value) { JsonObject item = new JsonObject(); item.addProperty("type", "inputText"); item.addProperty("text", value); JsonArray a = new JsonArray(); a.add(item); JsonObject out = new JsonObject(); out.addProperty("success", true); out.add("contentItems", a); return out; }
    private static JsonObject obj(String key, Object value, Object... more) { JsonObject o = new JsonObject(); put(o,key,value); for(int i=0;i<more.length;i+=2) put(o,(String)more[i],more[i+1]); return o; }
    private static void put(JsonObject o,String k,Object v) { if(v instanceof Number n)o.addProperty(k,n); else if(v instanceof Boolean b)o.addProperty(k,b); else o.addProperty(k,String.valueOf(v)); }
}
