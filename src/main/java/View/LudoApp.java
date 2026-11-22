package View;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.ObjIntConsumer;

import Controller.GameController;
import Controller.HistoryController;
import Controller.MainMenuController;
import Controller.ResultsController;
import Controller.SetupController;
import Controller.WindowCloseGuard;
import javafx.application.Application;
import javafx.application.Platform;
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
        stage.setOnCloseRequest(event -> {
            if (!WindowCloseGuard.canClose(scene.getRoot())) event.consume();
        });
        // Never let the window shrink below the size the menu needs, so nothing is cut off
        stage.setMinWidth(500);
        stage.setMinHeight(480);
        // Show the window
        stage.show();
    }

    /**
     * Development only: --dev-setup, --dev-setup-errors, --dev-board, --dev-surprise, --dev-results,
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
            // A real Easy game with four sample players; Results open at the end
            BoardView board = new BoardView();
            GameController game = new GameController(board, sampleNames, sampleColours);
            game.setOnMainMenu(mainMenu::show);
            new ResultsController(showScreen, mainMenu::show, newGame).showWhenOver(game);
            showScreen.accept(board.getRoot());
        } else if (launchArgs.contains("--dev-surprise")) {
            // A real Easy board with the surprise screens over it, once the window is showing
            BoardView board = new BoardView();
            GameController game = new GameController(board, sampleNames, sampleColours);
            game.setOnMainMenu(mainMenu::show);
            showScreen.accept(board.getRoot());
            Platform.runLater(() -> devSurprisePreview(board));
        } else if (launchArgs.contains("--dev-setup")) {
            showScreen.accept(new SetupView().getRoot());
        } else {
            return false;
        }
        return true;
    }

    /**
     * Development only: the six surprise events in turn, each after Continue, then "Opponent moves
     * back" twice more: with three opponents and four eligible pieces (the screen scrolls), and with
     * no opponent that qualifies. In the first one the first opponent has no piece that can move
     * back, and one piece stops at its Start Square; "Back 3 squares" also stops there. The preview,
     * not the screen, supplies the eligibility, roll and distances; what the screen reports back is
     * shown in the board's message line.
     */
    private static void devSurprisePreview(BoardView board) {
        Parent screen = board.getRoot();
        DiceView dice = board.getDiceView();
        SurpriseView surprise = new SurpriseView();
        List<String> opponents = List.of("Daniel", "Noa", "Omer");
        List<String> colours = List.of("Green", "Yellow", "Blue");
        BiFunction<Runnable, String, ObjIntConsumer<String>> reportPick = (next, preview) -> (name, piece) -> {
            board.showMessage("Preview (" + preview + "): the surprise screen reported " + name + "'s piece " + piece + ".");
            next.run();
        };
        Function<Runnable, Runnable> reportCancel = next -> () -> {
            board.showMessage("Preview: the surprise screen reported that the event was cancelled.");
            next.run();
        };
        List<Consumer<Runnable>> events = List.of(
                next -> surprise.showStraightToFinish(screen, "Maya", "Red", 2, next),
                next -> surprise.showOpponentMovesBack(screen, "Maya", opponents, colours,
                        List.of(false, true, false), List.of(Map.of(), Map.of(1, 3, 3, 2), Map.of()), 5,
                        reportPick.apply(next, "one opponent eligible"), reportCancel.apply(next)),
                next -> surprise.showBackToCastle(screen, "Maya", "Red", 4, next),
                next -> surprise.showBackThreeSquares(screen, "Maya", "Red", 1, 2, next),
                next -> surprise.showLoseNextTurn(screen, "Maya", next),
                next -> surprise.showExtraTurnWithUltraBoost(screen, "Maya", next),
                next -> surprise.showOpponentMovesBack(screen, "Maya", opponents, colours,
                        List.of(true, true, true),
                        List.of(Map.of(1, 3, 2, 3, 3, 1, 4, 2), Map.of(2, 3), Map.of(4, 1)), 6,
                        reportPick.apply(next, "three opponents, four pieces"), reportCancel.apply(next)),
                next -> surprise.showOpponentMovesBack(screen, "Maya", opponents, colours,
                        List.of(false, false, false), List.of(Map.of(), Map.of(), Map.of()), 2,
                        reportPick.apply(next, "no opponent eligible"), reportCancel.apply(next)));
        int[] current = {0};
        Runnable[] showNext = new Runnable[1];
        showNext[0] = () -> {
            if (current[0] < events.size()) {
                events.get(current[0]++).accept(showNext[0]);
            } else {
                board.showMessage("Surprise preview finished: all six events and the extra cases of event 2 were shown.");
                dice.setRollEnabled(true);
            }
        };
        dice.setRollEnabled(false);
        showNext[0].run();
    }

    public static void main(String[] args) {
        // Launch the JavaFX runtime, which calls start()
        launch(args);
    }
}
