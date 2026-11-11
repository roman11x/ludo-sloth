package View;

import java.util.List;
import java.util.function.Consumer;

import Controller.MainMenuController;
import javafx.application.Application;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.Region;
import javafx.stage.Stage;

/**
 * The JavaFX application: creates the primary window and shows the first scene.
 */
public class LudoApp extends Application {

    @Override
    public void start(Stage stage) {
        // Set the window title
        stage.setTitle("Ludo – Team Sloth");
        // Create an 800x600 scene; its root is replaced by whichever screen is shown
        Scene scene = new Scene(new Region(), 800, 600);
        // Apply the shared look (white background, chunky square buttons)
        scene.getStylesheets().add(getClass().getResource("/View/ludo.css").toExternalForm());
        // Screens switch by replacing the scene's root, so the window keeps its size and maximised state
        Consumer<Parent> showScreen = scene::setRoot;
        // Show the first screen: a screen picked for development, or the Main Menu
        Parent devScreen = devScreen(getParameters().getRaw());
        if (devScreen != null) {
            showScreen.accept(devScreen);
        } else {
            new MainMenuController(showScreen).show();
        }
        // Attach the scene to the window
        stage.setScene(scene);
        // Never let the window shrink below the size the menu needs, so nothing is cut off
        stage.setMinWidth(500);
        stage.setMinHeight(480);
        // Show the window
        stage.show();
    }

    /**
     * Development only: --dev-setup, --dev-board, --dev-results, --dev-results-save-error,
     * --dev-history or --dev-history-empty opens that screen directly instead of the Main Menu.
     * Returns {@code null} for a normal launch.
     */
    private static Parent devScreen(List<String> launchArgs) {
        if (launchArgs.contains("--dev-history-empty")) {
            HistoryView history = new HistoryView();
            history.showNoGames();
            return history.getRoot();
        } else if (launchArgs.contains("--dev-history")) {
            return new HistoryView().getRoot();
        } else if (launchArgs.contains("--dev-results-save-error")) {
            ResultsView results = new ResultsView();
            results.setSaveStatus(false);
            return results.getRoot();
        } else if (launchArgs.contains("--dev-results")) {
            return new ResultsView().getRoot();
        } else if (launchArgs.contains("--dev-board")) {
            return new BoardView().getRoot();
        } else if (launchArgs.contains("--dev-setup")) {
            return new SetupView().getRoot();
        }
        return null;
    }

    public static void main(String[] args) {
        // Launch the JavaFX runtime, which calls start()
        launch(args);
    }
}
