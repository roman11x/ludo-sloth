package Model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

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

}
