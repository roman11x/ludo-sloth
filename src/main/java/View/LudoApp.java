package View;

import java.util.List;

import javafx.application.Application;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * The JavaFX application: creates the primary window and shows the first scene.
 */
public class LudoApp extends Application {

    @Override
    public void start(Stage stage) {
        // Set the window title
        stage.setTitle("Ludo – Team Sloth");
        // Development only: --dev-setup, --dev-setup-errors, --dev-board, --dev-results,
        // --dev-results-save-error, --dev-history, --dev-history-empty or --dev-history-error
        // opens that screen directly instead of the Main Menu
        List<String> launchArgs = getParameters().getRaw();
        // Build the first screen: the Main Menu, or a screen picked for development
        Parent firstScreen;
        if (launchArgs.contains("--dev-history-error")) {
            HistoryView history = new HistoryView();
            history.showLoadError("Line 4 of the history file cannot be read: "
                    + "the difficulty \"expert\" is not Easy, Medium or Hard.");
            firstScreen = history.getRoot();
        } else if (launchArgs.contains("--dev-setup-errors")) {
            SetupView setup = new SetupView();
            setup.showValidationMessages(List.of(
                    "Please use unique player names: \"Maya\" is used twice.",
                    "Please enter a name for Player 2."));
            firstScreen = setup.getRoot();
        } else if (launchArgs.contains("--dev-history-empty")) {
            HistoryView history = new HistoryView();
            history.showNoGames();
            firstScreen = history.getRoot();
        } else if (launchArgs.contains("--dev-history")) {
            firstScreen = new HistoryView().getRoot();
        } else if (launchArgs.contains("--dev-results-save-error")) {
            ResultsView results = new ResultsView();
            results.setSaveStatus(false);
            firstScreen = results.getRoot();
        } else if (launchArgs.contains("--dev-results")) {
            firstScreen = new ResultsView().getRoot();
        } else if (launchArgs.contains("--dev-board")) {
            firstScreen = new BoardView().getRoot();
        } else if (launchArgs.contains("--dev-setup")) {
            firstScreen = new SetupView().getRoot();
        } else {
            firstScreen = new MainMenuView().getRoot();
        }
        // Create an 800x600 scene showing the first screen
        Scene scene = new Scene(firstScreen, 800, 600);
        // Apply the shared look (white background, chunky square buttons)
        scene.getStylesheets().add(getClass().getResource("/View/ludo.css").toExternalForm());
        // Attach the scene to the window
        stage.setScene(scene);
        // Never let the window shrink below the size the menu needs, so nothing is cut off
        stage.setMinWidth(500);
        stage.setMinHeight(480);
        // Show the window
        stage.show();
    }

    public static void main(String[] args) {
        // Launch the JavaFX runtime, which calls start()
        launch(args);
    }
}
