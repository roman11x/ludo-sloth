package Controller;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import javafx.scene.Parent;

/** Associates the current screen with its optional window-close confirmation. */
public final class WindowCloseGuard {
    private static final Object KEY = new Object();

    private WindowCloseGuard() { }

    static void register(Parent screen, BooleanSupplier guard) {
        Objects.requireNonNull(screen, "screen").getProperties()
                .put(KEY, Objects.requireNonNull(guard, "guard"));
    }

    /** Screens without unsaved data allow closing immediately. */
    public static boolean canClose(Parent screen) {
        Object guard = Objects.requireNonNull(screen, "screen").getProperties().get(KEY);
        return guard == null || ((BooleanSupplier) guard).getAsBoolean();
    }
}
