package Controller;

import javafx.scene.layout.Pane;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class WindowCloseGuardTest {
    @Test
    void ordinaryScreensAllowClosingWithoutConfirmation() {
        assertTrue(WindowCloseGuard.canClose(new Pane()));
    }

    @Test
    void onlyCurrentScreensGuardControlsClosingAndReflectsLaterSaveSuccess() {
        Pane results = new Pane();
        Pane menu = new Pane();
        AtomicBoolean saved = new AtomicBoolean();
        AtomicInteger calls = new AtomicInteger();
        WindowCloseGuard.register(results, () -> { calls.incrementAndGet(); return saved.get(); });
        assertFalse(WindowCloseGuard.canClose(results));
        assertTrue(WindowCloseGuard.canClose(menu));
        assertEquals(1, calls.get());
        saved.set(true);
        assertTrue(WindowCloseGuard.canClose(results));
    }
}
