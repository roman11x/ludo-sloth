package View;

import java.util.List;

import Controller.GameController;
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
        // Development only: --dev-setup, --dev-board, --dev-results, --dev-results-save-error,
        // --dev-history or --dev-history-empty opens that screen directly instead of the Main Menu
        List<String> launchArgs = getParameters().getRaw();
        // Build the first screen: the Main Menu, or a screen picked for development
        Parent firstScreen;
        if (launchArgs.contains("--dev-history-empty")) {
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
            // The board with Roll working through GameController, before Start Game is wired
            BoardView board = new BoardView();
            new GameController(board);
            firstScreen = board.getRoot();
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
