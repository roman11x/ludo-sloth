package Model;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * One row of the game-history CSV.
 */
public record GameRecord(int gameID,
                         Difficulty difficulty,
                         LocalDateTime startTime,
                         List<String> rankedNames,
                         Duration duration) {

    private static final int MIN_RANKED_NAMES = 2;
    private static final int MAX_RANKED_NAMES = 4;

    /**
     * Creates a game history record with validated values.
     * The ranked names list is defensively copied and stored as an unmodifiable list.
     *
     * @param gameID the game ID, or 0 when no ID has been assigned yet
     * @param difficulty the difficulty level of the game
     * @param startTime the date and time when the game started
     * @param rankedNames the player names in finishing-rank order
     * @param duration the total duration of the game
     * @throws IllegalArgumentException if the game ID is negative, the difficulty is null,
     *                                  the start time is null, the ranked names count is outside
     *                                  the supported range, the duration is null, or the duration is negative
     * @throws NullPointerException if the ranked names list is null
     */
    public GameRecord {
        // the record keeps its own unchangeable copy
        rankedNames = List.copyOf(rankedNames);
        validateId(gameID);
        validateDifficulty(difficulty);
        validateStartTime(startTime);
        validateRankedNames(rankedNames);
        validateDuration(duration);
    }

    /**
     * Creates a record without an assigned ID. The ID is set to 0 as a
     * placeholder because valid IDs are positive. When saving, the history
     * writer checks the file, creates a record with a valid ID, and appends it.
     */
    public GameRecord(Difficulty difficulty,
                      LocalDateTime startTime,
                      List<String> rankedNames,
                      Duration duration) {
        this(0, difficulty, startTime, rankedNames, duration);
    }

    private static void validateId(int id) {
        if (id < 0) {
            throw new IllegalArgumentException("ID must be non-negative");
        }
    }

    private static void validateDifficulty(Difficulty difficulty) {
        if (difficulty == null) {
            throw new IllegalArgumentException("Difficulty must not be null");
        }
    }

    private static void validateStartTime(LocalDateTime startTime) {
        if (startTime == null) {
            throw new IllegalArgumentException("Start time must not be null");
        }
    }

    private static void validateDuration(Duration duration) {
        if (duration == null) {
            throw new IllegalArgumentException("Duration must not be null");
        }
        if (duration.isNegative()) {
            throw new IllegalArgumentException("Duration must not be negative");
        }
    }

    private static void validateRankedNames(List<String> rankedNames) {
        var size = rankedNames.size();

        if (size < MIN_RANKED_NAMES || size > MAX_RANKED_NAMES) {
            throw new IllegalArgumentException("Ranked names must have between "
                    + MIN_RANKED_NAMES + " and " +
                      MAX_RANKED_NAMES + " names");
        }
    }

}
