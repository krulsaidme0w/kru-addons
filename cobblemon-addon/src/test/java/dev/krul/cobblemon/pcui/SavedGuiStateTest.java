package dev.krul.cobblemon.pcui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SavedGuiStateTest {
    @Test void restoresExactScreenAndHandlerRepeatedlyWithinConnection() {
        var saved = new SavedGuiState<Object, Object>();
        Object world = new Object(), connection = new Object(), screen = new Object(), handler = new Object();
        saved.save(world, connection, screen, handler);
        assertSame(screen, saved.load(world, connection).screen());
        assertSame(handler, saved.load(world, connection).handler());
        assertSame(screen, saved.load(world, connection).screen());
    }
    @Test void plainPcScreenDoesNotNeedVanillaHandler() {
        var saved = new SavedGuiState<Object, Object>();
        Object world = new Object(), connection = new Object(), screen = new Object();
        saved.save(world, connection, screen, null);
        assertSame(screen, saved.load(world, connection).screen());
        assertNull(saved.load(world, connection).handler());
    }
    @Test void contextChangeDiscardsSavedReferencesPermanently() {
        for (boolean changeWorld : new boolean[]{false, true}) {
            var saved = new SavedGuiState<Object, Object>();
            Object world = new Object(), connection = new Object();
            saved.save(world, connection, new Object(), new Object());
            assertNull(saved.load(changeWorld ? new Object() : world, changeWorld ? connection : new Object()));
            assertNull(saved.load(world, connection));
        }
    }
    @Test void lastSaveReplacesPreviousAndResetForgetsIt() {
        var saved = new SavedGuiState<Object, Object>();
        Object world = new Object(), connection = new Object(), latest = new Object();
        saved.save(world, connection, new Object(), null);
        saved.save(world, connection, latest, null);
        assertSame(latest, saved.load(world, connection).screen());
        saved.clear();
        assertNull(saved.load(world, connection));
    }
}
