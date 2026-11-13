package Controller;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import Model.GameRecord;
import Model.HistoryWriter;

/** One game's pending save; retry file failures, but never repeat a successful save. */
final class ResultSave {
    private final GameRecord record;
    private final HistoryWriter writer;
    private boolean saved;

    ResultSave(GameRecord record, Path historyFile) {
        this.record = Objects.requireNonNull(record, "record");
        writer = new HistoryWriter(Objects.requireNonNull(historyFile, "historyFile"));
    }

    boolean isSaved() {
        return saved;
    }

    boolean save() {
        if (saved) return true;
        try {
            writer.saveRecord(record);
            saved = true;
            return true;
        } catch (IOException fileError) {
            return false;
        }
    }
}
