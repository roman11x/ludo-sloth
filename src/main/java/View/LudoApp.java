package View;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

import Controller.GameController;
import Controller.HistoryController;
import Controller.MainMenuController;
import Controller.ResultsController;
import Controller.SetupController;
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
        MainMenuController mainMenu = new MainMenuController(showScreen);
        if (!showDevScreen(getParameters().getRaw(), showScreen, mainMenu)) {
            mainMenu.show();
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
     * Development only: --dev-setup, --dev-setup-errors, --dev-board, --dev-results,
     * --dev-results-save-error, --dev-history, --dev-history-empty or --dev-history-error
     * opens that screen directly instead of the Main Menu.
     * Returns {@code false} for a normal launch.
     */
    private static boolean showDevScreen(List<String> launchArgs, Consumer<Parent> showScreen,
                                         MainMenuController mainMenu) {
        List<String> sampleNames = List.of("Maya", "Daniel", "Noa", "Omer");
        List<String> sampleColours = List.of("Red", "Green", "Yellow", "Blue");
        Runnable newGame = () -> new SetupController(showScreen, mainMenu::show).show();
        if (launchArgs.contains("--dev-history-error")) {
            HistoryView history = new HistoryView();
            history.showLoadError("Line 4 of the history file cannot be read: "
                    + "the difficulty \"expert\" is not Easy, Medium or Hard.");
            showScreen.accept(history.getRoot());
        } else if (launchArgs.contains("--dev-setup-errors")) {
            SetupView setup = new SetupView();
            setup.showValidationMessages(List.of(
                    "Please use unique player names: \"Maya\" is used twice.",
                    "Please enter a name for Player 2."));
            showScreen.accept(setup.getRoot());
        } else if (launchArgs.contains("--dev-history-empty")) {
            HistoryView history = new HistoryView();
            history.showNoGames();
            showScreen.accept(history.getRoot());
        } else if (launchArgs.contains("--dev-history")) {
            // The real history file, read through HistoryController
            new HistoryController(showScreen, mainMenu::show).show();
        } else if (launchArgs.contains("--dev-results-save-error")) {
            // A random game played to the end at once; the "history file" is a folder, so saving fails
            new ResultsController(showScreen, mainMenu::show, newGame,
                    Path.of(System.getProperty("java.io.tmpdir")))
                    .showRandomGame(sampleNames, sampleColours);
        } else if (launchArgs.contains("--dev-results")) {
            // A random game played to the end at once and saved to a preview file in the temp folder,
            // never to the real history file
            new ResultsController(showScreen, mainMenu::show, newGame,
                    Path.of(System.getProperty("java.io.tmpdir"), "Ludo Sloth preview", "history.csv"))
                    .showRandomGame(sampleNames, sampleColours);
        } else if (launchArgs.contains("--dev-board")) {
            // A real Easy game with four sample players, before Start Game is wired; Results open at the end
            BoardView board = new BoardView();
            GameController game = new GameController(board, sampleNames, sampleColours);
            new ResultsController(showScreen, mainMenu::show, newGame).showWhenOver(game);
            showScreen.accept(board.getRoot());
        } else if (launchArgs.contains("--dev-setup")) {
            showScreen.accept(new SetupView().getRoot());
        } else {
            return false;
        }
        return true;
    }

    public static void main(String[] args) {
        // Launch the JavaFX runtime, which calls start()
        launch(args);
    }
}
