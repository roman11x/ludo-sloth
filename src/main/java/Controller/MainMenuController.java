package Controller;

import java.util.Objects;
import java.util.function.Consumer;

import View.MainMenuView;
import javafx.scene.Parent;

/**
 * Handles main-menu navigation.
 */
public class MainMenuController {

    private final Consumer<Parent> showScreen;
    private final MainMenuView view = new MainMenuView();

    /**
     * @param showScreen puts a screen's root node into the application window
     */
    public MainMenuController(Consumer<Parent> showScreen) {
        this.showScreen = Objects.requireNonNull(showScreen, "showScreen");
        view.setOnStartNewGame(this::openSetup);
        view.setOnGameHistory(this::openHistory);
    }

    /**
     * Shows the Main Menu in the window.
     */
    public void show() {
        showScreen.accept(view.getRoot());
    }

    private void openSetup() {
        new SetupController(showScreen, this::show).show();
    }

    private void openHistory() {
        new HistoryController(showScreen, this::show).show();
    }
}
