package Model;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

public class GameRecordTest {

    @Test
    void validRecordAccepted() {
        var record = new GameRecord(1,
                Difficulty.EASY,
                LocalDateTime.now(),
                List.of("Player1", "Player2"),
                Duration.ofSeconds(1 * 3600 + 5 * 60 + 4)
                );
        assertEquals(Difficulty.EASY, record.difficulty());
    }

    @Test
    void invalidIdIsRejected() {
        var e = assertThrows(IllegalArgumentException.class, () -> new GameRecord(-1,
                Difficulty.EASY,
                LocalDateTime.now(),
                List.of("Player1", "Player2"),
                Duration.ofSeconds(1 * 3600 + 5 * 60 + 4)
                ));

        assertTrue(e.getMessage().contains("ID must be non-negative"));
    }

    @Test
    void invalidDifficultyIsRejected() {
        var e = assertThrows(IllegalArgumentException.class, () -> new GameRecord(1,
                null,
                LocalDateTime.now(),
                List.of("Player1", "Player2"),
                Duration.ofSeconds(1 * 3600 + 5 * 60 + 4)
                ));

        assertTrue(e.getMessage().contains("Difficulty must not be null"));
    }

    @Test
    void invalidStartTimeIsRejected() {
        var e = assertThrows(IllegalArgumentException.class, () -> new GameRecord(1,
                Difficulty.EASY,
                null,
                List.of("Player1", "Player2"),
                Duration.ofSeconds(1 * 3600 + 5 * 60 + 4)
                ));

        assertTrue(e.getMessage().contains("Start time must not be null"));
    }

    @Test
    void nullDurationIsRejected() {
        var e = assertThrows(IllegalArgumentException.class, () -> new GameRecord(1,
                Difficulty.EASY,
                LocalDateTime.now(),
                List.of("Player1", "Player2"),
                null
                ));

        assertTrue(e.getMessage().contains("Duration must not be null"));
    }

    @Test
    void negativeDurationIsRejected() {
        var e = assertThrows(IllegalArgumentException.class, () -> new GameRecord(1,
                Difficulty.EASY,
                LocalDateTime.now(),
                List.of("Player1", "Player2"),
                Duration.ofSeconds(-1)
                ));

        assertTrue(e.getMessage().contains("Duration must not be negative"));
    }

    @Test
    void lessThanMinPlayerNamesAreRejected() {
        var e = assertThrows(IllegalArgumentException.class, () -> new GameRecord(1,
                Difficulty.EASY,
                LocalDateTime.now(),
                List.of("Player2"),
                Duration.ofSeconds(1 * 3600 + 5 * 60 + 4)
                ));

        assertTrue(e.getMessage().contains("Ranked names must have between 2 and 4 names"));
    }

    @Test
    void moreThanMaxPlayerNamesAreRejected() {
        var e = assertThrows(IllegalArgumentException.class, () -> new GameRecord(1,
                Difficulty.EASY,
                LocalDateTime.now(),
                List.of("Player1", "Player2", "Player3", "Player4", "Player5"),
                Duration.ofSeconds(1 * 3600 + 5 * 60 + 4)
                ));

        assertTrue(e.getMessage().contains("Ranked names must have between 2 and 4 names"));
    }

    @Test
    void nullPlayerNameIsRejected() {
         assertThrows(NullPointerException.class, () -> new GameRecord(1,
                Difficulty.EASY,
                LocalDateTime.now(),
                Arrays.asList("Player1", null, "Player3"),
                Duration.ofSeconds(1 * 3600 + 5 * 60 + 4)
                ));
    }

    @Test
    void playerListIsCopied() {
        var original = new ArrayList<>(List.of("Player1", "Player2", "Player3"));
        int originalSize = original.size();
        var record = new GameRecord(1,
                Difficulty.EASY,
                LocalDateTime.now(),
                original,
                Duration.ofSeconds(1 * 3600 + 5 * 60 + 4)
                );
        original.add("Player4");
        assertEquals(originalSize, record.rankedNames().size());
    }

}
