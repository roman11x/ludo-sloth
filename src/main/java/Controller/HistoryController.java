package Controller;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;

import Model.GameRecord;
import Model.HistoryReader;
import View.HistoryView;
import javafx.scene.Parent;

/**
 * Connects the history screen to HistoryReader.
 * <p>
 * Every time the screen is shown, the history file is read again, so it always shows the games
 * saved so far. The file is only read here, never changed (HistoryReader creates a missing file
 * with just its header).
 */
public class HistoryController {

    private static final String UNREADABLE_MESSAGE =
            "The history file could not be opened. It may be open in another program, or the "
                    + "folder may not be accessible.";

    private final Consumer<Parent> showScreen;
    private final Path historyFile;
    private final HistoryView view = new HistoryView();

    /**
     * Reads the standard history file, {@link HistoryFile#path()}.
     *
     * @param showScreen puts a screen's root node into the application window
     * @param onBack     what "Back to Main Menu" does
     */
    public HistoryController(Consumer<Parent> showScreen, Runnable onBack) {
        this(showScreen, onBack, HistoryFile.path());
    }

    /**
     * Reads the given history file instead, e.g. for tests or a preview.
     */
    public HistoryController(Consumer<Parent> showScreen, Runnable onBack, Path historyFile) {
        this.showScreen = Objects.requireNonNull(showScreen, "showScreen");
        this.historyFile = Objects.requireNonNull(historyFile, "historyFile");
        view.setOnBack(Objects.requireNonNull(onBack, "onBack"));
    }

    /**
     * Reads the history file and shows the History screen in the window: the saved games newest
     * first, the "no games yet" message, or an error notice if the file cannot be read.
     */
    public void show() {
        load();
        showScreen.accept(view.getRoot());
    }

    private void load() {
        List<GameRecord> games;
        try {
            games = new HistoryReader(historyFile).readAll();
        } catch (HistoryReader.CorruptHistoryException e) {
            view.showLoadError(e.getMessage());
            return;
        } catch (IOException | RuntimeException e) {
            view.showLoadError(UNREADABLE_MESSAGE);
            return;
        }
        view.clearGames();
        if (games.isEmpty()) {
            view.showNoGames();
            return;
        }
        for (GameRecord game : games) {
            view.addGame(game.gameID(), game.startTime(), difficultyName(game),
                    game.rankedNames(), game.duration().toSeconds());
        }
    }

    private static String difficultyName(GameRecord game) {
        String name = game.difficulty().name();
        return name.charAt(0) + name.substring(1).toLowerCase(Locale.ROOT);
    }
}
