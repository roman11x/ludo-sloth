package Controller;

import java.nio.file.Path;

/**
 * The one location of the game-history CSV, shared by everything that saves or reads the history.
 */
public final class HistoryFile {

    private HistoryFile() {
    }

    /**
     * Returns {@code <user home>/Ludo Sloth/history.csv}. The file and folder are created on first
     * use by the history writer.
     */
    public static Path path() {
        return Path.of(System.getProperty("user.home"), "Ludo Sloth", "history.csv");
    }
}
