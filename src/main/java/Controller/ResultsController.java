package Controller;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Random;
import java.util.function.Consumer;

import Model.Difficulty;
import Model.Game;
import Model.GameRecord;
import Model.MoveCandidate;
import Model.Player;
import View.ResultsView;
import javafx.scene.Parent;

/**
 * Shows final rankings when the game ends.
 * <p>
 * Saves the finished game to the history file exactly once and shows the Results screen with
 * the real ranking, duration and difficulty, plus whether the save worked.
 */
public class ResultsController {

    private static final Difficulty DIFFICULTY = Difficulty.EASY;
    private static final int SAMPLE_GAME_MAX_ROLLS = 100_000;

    private final Consumer<Parent> showScreen;
    private final Path historyFile;
    private final ResultsView view = new ResultsView();
    private Game shownGame;
    private ResultSave pendingSave;

    /**
     * Saves to the standard history file, {@link HistoryFile#path()}.
     *
     * @param showScreen puts a screen's root node into the application window
     * @param onMainMenu what "Main Menu" does
     * @param onNewGame  what "New Game" does
     */
    public ResultsController(Consumer<Parent> showScreen, Runnable onMainMenu, Runnable onNewGame) {
        this(showScreen, onMainMenu, onNewGame, HistoryFile.path());
    }

    /**
     * Saves to the given history file instead, e.g. for tests or a preview.
     */
    public ResultsController(Consumer<Parent> showScreen, Runnable onMainMenu, Runnable onNewGame,
                             Path historyFile) {
        this.showScreen = Objects.requireNonNull(showScreen, "showScreen");
        this.historyFile = Objects.requireNonNull(historyFile, "historyFile");
        Runnable mainMenu = Objects.requireNonNull(onMainMenu, "onMainMenu");
        Runnable newGame = Objects.requireNonNull(onNewGame, "onNewGame");
        view.setOnMainMenu(() -> navigate(mainMenu));
        view.setOnNewGame(() -> navigate(newGame));
        view.setOnRetry(this::retrySave);
        WindowCloseGuard.register(view.getRoot(), this::requestLeave);
    }

    /**
     * Opens the Results screen when the game played by {@code gameController} ends.
     */
    public void showWhenOver(GameController gameController) {
        gameController.setOnGameOver(this::show);
    }

    /**
     * Saves the finished game to the history (only the first time this game is given) and shows
     * the Results screen. A failed save is reported on the screen; the results stay shown.
     *
     * @throws IllegalStateException if the game has not ended
     */
    public void show(Game game) {
        Objects.requireNonNull(game, "game");
        if (!game.isFinished()) {
            throw new IllegalStateException("The game has not ended yet");
        }
        if (shownGame == null) {
            shownGame = game;
            List<String> names = new ArrayList<>();
            List<String> colours = new ArrayList<>();
            for (Player player : game.ranking()) {
                names.add(player.name());
                colours.add(GameController.colourName(player.color()));
            }
            view.showResults(names, colours, game.duration().toSeconds(), difficultyName());
            pendingSave = new ResultSave(
                    new GameRecord(DIFFICULTY, game.startTime(), names, game.duration()), historyFile);
            retrySave();
        } else if (shownGame != game) {
            throw new IllegalStateException("These results already show another game");
        }
        showScreen.accept(view.getRoot());
    }

    /**
     * Development preview: starts an Easy game for these players ({@code names.get(i)} plays
     * {@code colours.get(i)}), plays it to the end at once with random legal moves, then calls
     * {@link #show(Game)}, which saves it like a real game.
     */
    public void showRandomGame(List<String> names, List<String> colours) {
        Game game = new Game(GameController.players(names, colours));
        Random random = new Random();
        int rolls = 0;
        while (!game.isFinished()) {
            if (++rolls > SAMPLE_GAME_MAX_ROLLS) {
                throw new IllegalStateException("The sample game did not finish");
            }
            game.roll();
            List<MoveCandidate> moves = game.legalMoves();
            if (!moves.isEmpty()) {
                game.play(moves.get(random.nextInt(moves.size())).piece());
            }
        }
        show(game);
    }

    private boolean retrySave() {
        if (pendingSave == null) return false;
        boolean saved = pendingSave.save();
        view.setSaveStatus(saved);
        return saved;
    }

    private void navigate(Runnable action) {
        if (requestLeave()) action.run();
    }

    /** Shared by Main Menu, New Game and the application's window-close request. */
    private boolean requestLeave() {
        if (pendingSave == null || pendingSave.isSaved()) return true;
        while (true) {
            switch (view.confirmUnsavedResults()) {
                case STAY: return false;
                case LEAVE: return true;
                case RETRY:
                    if (retrySave()) return true;
                    // A failed retry leaves results intact and offers the same choices again.
            }
        }
    }

    private static String difficultyName() {
        String name = DIFFICULTY.name();
        return name.charAt(0) + name.substring(1).toLowerCase(Locale.ROOT);
    }
}
