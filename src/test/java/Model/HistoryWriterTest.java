package Model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static Model.HistoryWriter.formatRecordForSaving;
import static Model.HistoryWriter.nextGameID;
import static org.junit.jupiter.api.Assertions.*;

public class HistoryWriterTest {

    private final String HEADER = "gameID,difficulty,"
            + "timestamp(DD/MM/YYYY; HH:MM:SS),"
            + "1st_place_name,2nd_place_name,3rd_place_name,4rd_place_name,"
            + "game_duration(HH:MM:SS)";

    @Test
    void createsFileIfNotExists(@TempDir Path tempDir) throws IOException {
        // resolve(...) = "an address inside this folder"; the file isn't created yet
        Path file = tempDir.resolve("history.csv");


        var writer = new HistoryWriter(file);
        writer.createFileIfNotExists();

        List<String> lines = Files.readAllLines(file);

        assertTrue(Files.exists(file));
        assertEquals(HEADER, lines.getFirst());
        assertEquals(1, lines.size()); // make sure the file only has the header
    }

    @Test
    void createsDirectoriesIfNotExists(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("folder1")
                .resolve("folder2")
                .resolve("history.csv");

        var writer = new HistoryWriter(file);
        writer.createFileIfNotExists();

        assertTrue(Files.exists(file));
    }

    @Test
    void ifFileExistsDoesNothing(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("history.csv");

        Files.writeString(file, "some content");

        var writer = new HistoryWriter(file);
        writer.createFileIfNotExists();

        List<String> lines = Files.readAllLines(file);

        assertEquals("some content", lines.getFirst());
        assertEquals(1, lines.size()); // make sure the file only has the sample
    }

    @Test
    void returnsOneIfThereAreNoRecords() throws IOException {
        var headerLine = List.of(HEADER);

        var id = nextGameID(headerLine);

        assertEquals(1, id);
    }

    @Test
    void returnsNextIdForSingleRecord() throws IOException {
        var records = List.of(HEADER,
                "5,medium,07/11/2026; 15:57:00,Alice,Bob,Carol,Dan,00:30:00");

        var id = nextGameID(records);

        assertEquals(6, id);
    }

    @Test
    void returnsNextIdFromHighestRecordId() throws IOException {
        var records = List.of(HEADER,
                "2,medium,07/11/2026; 15:57:00,Alice,Bob,Carol,Dan,00:30:00",
                "5,medium,07/11/2026; 15:57:00,Alice,Bob,Carol,Dan,00:30:00",
                "1,medium,07/11/2026; 15:57:00,Alice,Bob,Carol,Dan,00:30:00");

        var id = nextGameID(records);

        assertEquals(6, id);
    }

    @Test
    void returnsCorrectFourPlayerFormat() {
        var expected = "1,hard,19/10/2026; 12:01:00,Mor,Noy,Safa,Muhammed,01:05:04\n";

        var record = new GameRecord(1, Difficulty.HARD,
                LocalDateTime.of(2026, 10, 19, 12, 1, 0),
                List.of("Mor", "Noy", "Safa", "Muhammed"),
                Duration.ofSeconds(1 * 3600 + 5 * 60 + 4));

        var actual = formatRecordForSaving(record);

        assertEquals(expected, actual);
    }

    @Test
    void returnsCorrectTwoPlayerFormat() {
        var expected = "1,hard,19/10/2026; 12:01:00,Mor,Noy,,,00:00:00\n";

        var record = new GameRecord(1, Difficulty.HARD,
                LocalDateTime.of(2026, 10, 19, 12, 1, 0),
                List.of("Mor", "Noy"),
                Duration.ofSeconds(0));

        var actual = formatRecordForSaving(record);

        assertEquals(expected, actual);
    }

    @Test
    void nullRecordIsRejected(@TempDir Path tempDir) {
        Path file = tempDir.resolve("test.csv");

        var writer = new HistoryWriter(file);
        var e = assertThrows(NullPointerException.class, () -> writer.saveRecord(null));
        var expectedMessage = "You cannot save a null record";

        boolean there = Files.exists(file);

        assertEquals(expectedMessage, e.getMessage());
        assertFalse(there);
    }

    @Test
    void signedGameIdIsRejected(@TempDir Path tempDir) {
        Path file = tempDir.resolve("test.csv");

        var writer = new HistoryWriter(file);

        var e = assertThrows(IllegalArgumentException.class, () -> writer.saveRecord(new GameRecord(5,
                Difficulty.EASY,
                LocalDateTime.now(),
                List.of("Mor", "Player2"),
                Duration.ofSeconds(0))));

        var expectedMessage = "Got id: " + 5 +
                " Expected id: " + GameRecord.UNASSIGNED_ID;

        assertEquals(expectedMessage, e.getMessage());
    }

    @Test
    void newFileMeansHeaderPlusRecord(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("test.csv");

        var writer = new HistoryWriter(file);
        writer.saveRecord(new GameRecord(GameRecord.UNASSIGNED_ID,
                Difficulty.EASY,
                LocalDateTime.of(2023, 4, 1, 0, 0),
                List.of("Mor", "Player2"),
                Duration.ofSeconds(0)));
        List<String> actual = Files.readAllLines(file);
        List<String> expected = List.of(HEADER,
                "1,easy,01/04/2023; 00:00:00,Mor,Player2,,,00:00:00");

        assertEquals(expected, actual);
    }

    @Test
    void existingRecordsDoNotGetOverridden(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("test.csv");
        Files.write(file, List.of(HEADER,
                        "1,easy,01/04/2023; 00:00:00,Mor,Player2,,,00:00:00",
                        "2,medium,07/11/2026; 15:57:00,Alice,Bob,Carol,Dan,00:30:00",
                        "3,hard,19/10/2026; 12:01:00,Mor,Noy,Safa,Muhammed,01:05:04"),
                StandardOpenOption.CREATE);

        var writer = new HistoryWriter(file);
        writer.saveRecord(new GameRecord(GameRecord.UNASSIGNED_ID,
                Difficulty.MEDIUM,
                LocalDateTime.of(2026, 11, 7, 15, 57),
                List.of("Alice", "Bob", "Carol", "Dan"),
                Duration.ofMinutes(30)));

        List<String> actual = Files.readAllLines(file);
        List<String> expected = List.of(HEADER,
                "1,easy,01/04/2023; 00:00:00,Mor,Player2,,,00:00:00",
                "2,medium,07/11/2026; 15:57:00,Alice,Bob,Carol,Dan,00:30:00",
                "3,hard,19/10/2026; 12:01:00,Mor,Noy,Safa,Muhammed,01:05:04",
                "4,medium,07/11/2026; 15:57:00,Alice,Bob,Carol,Dan,00:30:00");

        assertEquals(expected, actual);
    }

    @Test
    void emptyFileIsRejected(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("test.csv");
        Files.write(file, List.of());
        List<String> expected = Files.readAllLines(file);

        var writer = new HistoryWriter(file);
        var e = assertThrows(IOException.class, () -> writer.saveRecord(new GameRecord(GameRecord.UNASSIGNED_ID,
                Difficulty.MEDIUM,
                LocalDateTime.of(2026, 11, 7, 15, 57),
                List.of("Alice", "Bob", "Carol", "Dan"),
                Duration.ofMinutes(30))));
        List<String> actual = Files.readAllLines(file);

        assertTrue(e.getMessage().contains("History file is malformed (missing or wrong header): " + file));
        assertEquals(expected, actual);
    }

    @Test
    void wrongHeaderIsRejected(@TempDir Path tempdir) throws  IOException {
        Path file = tempdir.resolve("test.csv");
        Files.writeString(file, "wrong header");
        List<String> expected = Files.readAllLines(file);

        var writer = new HistoryWriter(file);
        var e = assertThrows(IOException.class, () -> writer.saveRecord(new GameRecord(GameRecord.UNASSIGNED_ID,
                Difficulty.MEDIUM,
                LocalDateTime.of(2026, 11, 7, 15, 57),
                List.of("Alice", "Bob", "Carol", "Dan"),
                Duration.ofMinutes(30))));

        List<String> actual = Files.readAllLines(file);
        assertEquals(expected, actual);
        assertTrue(e.getMessage().contains("History file is malformed (missing or wrong header): " + file));
    }

    @Test
    void fileWithInvalidIdIsRejected(@TempDir Path tempdir) throws IOException {
        Path file = tempdir.resolve("test.csv");
        String invalidRecord = "abc,easy,01/04/2023; 00:00:00,Mor,Noy,,,00:10:00";
        Files.write(file, List.of(HEADER, invalidRecord));
        List<String> expected = Files.readAllLines(file);

        var writer = new HistoryWriter(file);
        var e = assertThrows(IOException.class, () -> writer.saveRecord(new GameRecord(GameRecord.UNASSIGNED_ID,
                Difficulty.MEDIUM,
                LocalDateTime.of(2026, 11, 7, 15, 57),
                List.of("Alice", "Bob", "Carol", "Dan"),
                Duration.ofMinutes(30))));

        List<String> actual = Files.readAllLines(file);
        assertEquals(expected, actual);
        assertTrue(e.getMessage().contains("History file is malformed (invalid game ID): " + invalidRecord));
    }

    @Test
    void appendsRecordWhenFileLacksTrailingNewline(@TempDir Path tempdir) throws IOException {
        Path file = tempdir.resolve("test.csv");
        var courseTemplate =
                "gameID,difficulty,timestamp(DD/MM/YYYY; HH:MM:SS),1st_place_name,2nd_place_name,3rd_place_name,4rd_place_name,game_duration(HH:MM:SS)\r\n"
                        + "1,hard,19/10/2026;12:01:00 ,Mor,Noy,Safa,Muhammed,01:05:04";
        Files.writeString(file, courseTemplate);

        var writer = new HistoryWriter(file);
        writer.saveRecord(new GameRecord(GameRecord.UNASSIGNED_ID,
                Difficulty.MEDIUM,
                LocalDateTime.of(2026, 11, 7, 15, 57),
                List.of("Alice", "Bob", "Carol", "Dan"),
                Duration.ofMinutes(30)));

        assertEquals(3, Files.readAllLines(file).size());
    }


}
