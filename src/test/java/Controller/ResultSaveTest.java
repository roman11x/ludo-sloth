package Controller;

import Model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ResultSaveTest {
    @TempDir Path folder;

    private GameRecord record() {
        return new GameRecord(Difficulty.EASY, LocalDateTime.of(2026, 11, 12, 12, 0),
                List.of("Razi", "Roman"), Duration.ofMinutes(10));
    }

    @Test
    void startsUnsavedAndRepeatedSuccessfulSavesWriteExactlyOneRecord() throws Exception {
        Path file = folder.resolve("history.csv");
        ResultSave save = new ResultSave(record(), file);
        assertFalse(save.isSaved());
        assertTrue(save.save());
        assertTrue(save.isSaved());
        String firstWrite = Files.readString(file);
        assertTrue(save.save());
        assertTrue(save.save());
        assertEquals(firstWrite, Files.readString(file));
        assertEquals(1, new HistoryReader(file).readAll().size());
    }

    @Test
    void failedSaveCanBeRetriedAfterFileProblemIsFixed() throws Exception {
        Path file = folder.resolve("history.csv");
        Files.createDirectory(file);
        ResultSave save = new ResultSave(record(), file);
        assertFalse(save.save());
        assertFalse(save.isSaved());
        assertFalse(save.save());
        Files.delete(file);
        assertTrue(save.save());
        assertTrue(save.isSaved());
        assertTrue(save.save());
        var records = new HistoryReader(file).readAll();
        assertEquals(1, records.size());
        assertEquals(record().rankedNames(), records.getFirst().rankedNames());
        assertEquals(record().duration(), records.getFirst().duration());
    }

    @Test
    void rejectsMalformedHistoryWithoutChangingItAndCanRetryARepairedFile() throws Exception {
        Path file = folder.resolve("history.csv");
        Files.writeString(file, "not a valid history header\n");
        ResultSave save = new ResultSave(record(), file);
        String original = Files.readString(file);
        assertFalse(save.save());
        assertFalse(save.isSaved());
        assertEquals(original, Files.readString(file));
        // Simulate the user restoring a valid empty history file.
        Files.delete(file);
        new HistoryWriter(file).createFileIfNotExists();
        assertTrue(save.save());
        assertEquals(1, new HistoryReader(file).readAll().size());
    }

    @Test
    void retryPreservesEarlierGamesAndAllocatesOneNewId() throws Exception {
        Path file = folder.resolve("history.csv");
        new HistoryWriter(file).saveRecord(record());
        ResultSave next = new ResultSave(new GameRecord(Difficulty.EASY,
                LocalDateTime.of(2026, 11, 12, 13, 0), List.of("Maor", "Razi"), Duration.ofMinutes(4)), file);
        assertTrue(next.save());
        assertTrue(next.save());
        var records = new HistoryReader(file).readAll();
        assertEquals(2, records.size());
        assertEquals(List.of(2, 1), records.stream().map(GameRecord::gameID).toList());
        assertEquals(List.of("Maor", "Razi"), records.getFirst().rankedNames());
    }

    @Test
    void doesNotHideInvalidRecordProgrammingErrorsAsFileFailures() {
        GameRecord assigned = new GameRecord(10, Difficulty.EASY, record().startTime(),
                record().rankedNames(), record().duration());
        ResultSave save = new ResultSave(assigned, folder.resolve("history.csv"));
        assertThrows(IllegalArgumentException.class, save::save);
        assertFalse(save.isSaved());
    }
}
