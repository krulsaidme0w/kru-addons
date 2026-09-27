package dev.krul.builder.agent;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

class CodexProtocolTest {
    @Test void readsSplitJsonlFramesAndKeepsInterleavedNotificationsDistinct() throws Exception {
        byte[] wire = ("{\"method\":\"item/agentMessage/delta\",\"params\":{\"delta\":\"hi\"}}\n"
            + "{\"id\":4,\"result\":{\"thread\":{\"id\":\"t1\"}}}\n").getBytes(StandardCharsets.UTF_8);
        var input = new ChunkedInput(wire, 3);
        var first = CodexProtocol.readFrame(input);
        var second = CodexProtocol.readFrame(input);
        assertEquals("item/agentMessage/delta", first.get("method").getAsString());
        assertEquals(4, second.get("id").getAsInt());
        assertNull(CodexProtocol.readFrame(input));
    }

    @Test void rejectsMalformedJsonAndOversizedFrames() {
        assertThrows(IOException.class, () -> CodexProtocol.readFrame(bytes("{bad}\n")));
        var huge = new byte[PlanResponseCodec.MAX_LINE_BYTES + 2];
        java.util.Arrays.fill(huge, (byte) ' '); huge[huge.length - 1] = '\n';
        assertThrows(IOException.class, () -> CodexProtocol.readFrame(new ByteArrayInputStream(huge)));
    }

    @Test void rejectsPartialFrameAtEof() {
        assertThrows(IOException.class, () -> CodexProtocol.readFrame(bytes("{\"id\":1}")));
    }

    private static ByteArrayInputStream bytes(String value) { return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8)); }
    private static final class ChunkedInput extends ByteArrayInputStream {
        private final int max;
        ChunkedInput(byte[] bytes, int max) { super(bytes); this.max = max; }
        @Override public synchronized int read(byte[] buffer, int offset, int length) {
            return super.read(buffer, offset, Math.min(length, max));
        }
    }
}
