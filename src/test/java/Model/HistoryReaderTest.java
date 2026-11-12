package Model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class HistoryReaderTest {

    private final String HEADER = "gameID,difficulty,"
            + "timestamp(DD/MM/YYYY; HH:MM:SS),"
            + "1st_place_name,2nd_place_name,3rd_place_name,4rd_place_name,"
            + "game_duration(HH:MM:SS)";

    @Test
    void readingValidFileShowsGames(@TempDir Path tempDir) throws IOException, HistoryReader.CorruptHistoryException {
        Path file = tempDir.resolve("test.csv");
        String validRow = "1,easy,12/11/2026; 14:23:37,Roman,Maor,,,02:34:56";
        String secondValidRow = "2,easy,12/11/2026; 14:30:37,Roman,Maor,,,02:34:56";
        Files.write(file, List.of(HEADER, validRow, secondValidRow));
        var reader = new HistoryReader(file);

        List<GameRecord> games = reader.readAll();
        var expected1 = new GameRecord(1, Difficulty.EASY,
                LocalDateTime.of(2026, 11, 12, 14, 23, 37),
                List.of("Roman", "Maor"),
                Duration.ofHours(2).plusMinutes(34).plusSeconds(56));
        var expected2 = new GameRecord(2, Difficulty.EASY,
                LocalDateTime.of(2026, 11, 12, 14, 30, 37),
                List.of("Roman", "Maor"),
                Duration.ofHours(2).plusMinutes(34).plusSeconds(56));

        assertEquals(List.of(expected2, expected1), games);
    }

    @Test
    void missingFileIsCreatedWithHeaderAndReturnsNoGames(@TempDir Path tempDir) throws HistoryReader.CorruptHistoryException, IOException {
        Path file = tempDir.resolve("test.csv");
        var reader = new HistoryReader(file);

        List<GameRecord> games = reader.readAll();
        List<String> lines = Files.readAllLines(file);


        assertEquals(List.of(HEADER), lines);
        assertTrue(games.isEmpty());
    }

    @Test
    void malformedHistoryThrowsErrorWithoutOverwritingFile(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("test.csv");

        Files.write(file, List.of(HEADER, "1,easy,12/11/2026; 14:23:37,Roman,Maor,,,02:34:56",
                "-2,easy,12/11/2026; 14:30:37,Roman,Maor,,,02:34:56"));
        var malformedId = "-2";

        List<String> expectedLines = Files.readAllLines(file);
        var reader = new HistoryReader(file);

        var e = assertThrows(HistoryReader.CorruptHistoryException.class,
                reader::readAll);
        var actualLines = Files.readAllLines(file);

        assertEquals(expectedLines, actualLines);
        assertTrue(e.getMessage().contains(malformedId));
    }

    @Test
    void courseTemplateFileIsReadable(@TempDir Path tempDir) throws IOException, HistoryReader.CorruptHistoryException {
        Path file = tempDir.resolve("course.csv");
        var courseTemplate =
                "gameID,difficulty,timestamp(DD/MM/YYYY; HH:MM:SS),1st_place_name,2nd_place_name,3rd_place_name,4rd_place_name,game_duration(HH:MM:SS)\r\n"
                        + "1,hard,19/10/2026;12:01:00 ,Mor,Noy,Safa,Muhammed,01:05:04";
        var expectedRecord = new GameRecord(1, Difficulty.HARD,
                LocalDateTime.of(2026, 10, 19, 12, 1, 0),
                List.of("Mor", "Noy", "Safa", "Muhammed"),
                Duration.ofHours(1).plusMinutes(5).plusSeconds(4));

        Files.writeString(file, courseTemplate);

        var reader = new HistoryReader(file);
        List<GameRecord> games = reader.readAll();
        var contents = Files.readString(file);


        assertEquals(courseTemplate, contents);
        assertEquals(List.of(expectedRecord), games);
    }

}
