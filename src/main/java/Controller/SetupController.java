package Controller;

import java.util.Objects;
import java.util.function.Consumer;

import View.SetupView;
import javafx.scene.Parent;

/**
 * Handles game-setup input, validation and the Start Game action.
 */
public class SetupController {

    private final Consumer<Parent> showScreen;
    private final SetupView view = new SetupView();

    /**
     * @param showScreen puts a screen's root node into the application window
     * @param onBack     what "Back to Main Menu" does
     */
    public SetupController(Consumer<Parent> showScreen, Runnable onBack) {
        this.showScreen = Objects.requireNonNull(showScreen, "showScreen");
        view.setOnBack(Objects.requireNonNull(onBack, "onBack"));
    }

    /**
     * Shows a fresh Setup screen in the window.
     */
    public void show() {
        showScreen.accept(view.getRoot());
    }
}
