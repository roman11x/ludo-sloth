package Controller;

import java.util.Objects;
import java.util.function.Consumer;

import View.HistoryView;
import javafx.scene.Parent;

/**
 * Connects the history screen to HistoryReader.
 */
public class HistoryController {

    private final Consumer<Parent> showScreen;
    private final HistoryView view = new HistoryView();

    /**
     * @param showScreen puts a screen's root node into the application window
     * @param onBack     what "Back to Main Menu" does
     */
    public HistoryController(Consumer<Parent> showScreen, Runnable onBack) {
        this.showScreen = Objects.requireNonNull(showScreen, "showScreen");
        view.setOnBack(Objects.requireNonNull(onBack, "onBack"));
    }

    /**
     * Shows the History screen in the window.
     */
    public void show() {
        showScreen.accept(view.getRoot());
    }
}
