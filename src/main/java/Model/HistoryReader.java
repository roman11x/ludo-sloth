package Model;

import java.io.IOException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the history CSV into GameRecord objects.
 */
public class HistoryReader {

    // Must match the template header written by HistoryWriter, field by field (including "4rd").
    private static final List<String> HEADER_FIELDS = List.of(
            "gameID",
            "difficulty",
            "timestamp(DD/MM/YYYY; HH:MM:SS)",
            "1st_place_name",
            "2nd_place_name",
            "3rd_place_name",
            "4rd_place_name",
            "game_duration(HH:MM:SS)");
    private static final int COLUMN_COUNT = HEADER_FIELDS.size();
    private static final int FIRST_PLACE_COLUMN = 3;
    private static final int PLACE_COLUMNS = 4;
    private static final int MIN_PLAYERS = 2;

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT);
    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("HH:mm:ss").withResolverStyle(ResolverStyle.STRICT);
    private static final Pattern DURATION_FORMAT = Pattern.compile("(\\d{2,}):([0-5]\\d):([0-5]\\d)");
    private static final char BYTE_ORDER_MARK = '\uFEFF';

    private final Path path; // the path to the history csv file

    /**
     * @param path a path that includes a parent directory.
     */
    public HistoryReader(Path path) {
        this.path = Objects.requireNonNull(path, "path");
    }

    /**
     * Reads every saved game, newest first (latest start time first; equal start times by
     * higher game ID first).
     * <p>
     * If the file does not exist it is created with only the template header and the result is
     * empty. Blank lines are ignored. The file is never changed when it exists.
     *
     * @return the saved games; an unmodifiable list, empty if there are none
     * @throws CorruptHistoryException if the header does not match the template or any row
     *                                 cannot be read
     * @throws IOException             if the file cannot be created or read
     */
    public List<GameRecord> readAll() throws IOException, CorruptHistoryException {
        if (!Files.exists(path)) {
            new HistoryWriter(path).createFileIfNotExists();
            return List.of();
        }

        List<String> lines;
        try {
            lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        } catch (CharacterCodingException e) {
            throw new CorruptHistoryException("The history file is not a readable text file.", e);
        }

        List<GameRecord> games = new ArrayList<>();
        boolean headerSeen = false;
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            if (index == 0 && !line.isEmpty() && line.charAt(0) == BYTE_ORDER_MARK) {
                line = line.substring(1);
            }
            if (line.isBlank()) {
                continue;
            }
            int lineNumber = index + 1;
            if (!headerSeen) {
                checkHeader(line, lineNumber);
                headerSeen = true;
            } else {
                games.add(parseRow(line, lineNumber));
            }
        }
        if (!headerSeen) {
            throw new CorruptHistoryException("The history file is empty: its header line is missing.");
        }

        games.sort(Comparator.comparing(GameRecord::startTime)
                .thenComparingInt(GameRecord::gameID)
                .reversed());
        return List.copyOf(games);
    }

    private static void checkHeader(String line, int lineNumber) throws CorruptHistoryException {
        if (!trimmedFields(line).equals(HEADER_FIELDS)) {
            throw new CorruptHistoryException("Line " + lineNumber
                    + " of the history file is not the expected header, so the file cannot be read.");
        }
    }

    private static GameRecord parseRow(String line, int lineNumber) throws CorruptHistoryException {
        List<String> fields = trimmedFields(line);
        if (fields.size() != COLUMN_COUNT) {
            throw rowError(lineNumber, "it has " + fields.size() + " columns instead of " + COLUMN_COUNT);
        }
        int gameID = parseGameID(fields.get(0), lineNumber);
        Difficulty difficulty = parseDifficulty(fields.get(1), lineNumber);
        LocalDateTime startTime = parseTimestamp(fields.get(2), lineNumber);
        List<String> rankedNames = parseRankedNames(fields, lineNumber);
        Duration duration = parseDuration(fields.get(COLUMN_COUNT - 1), lineNumber);
        return new GameRecord(gameID, difficulty, startTime, rankedNames, duration);
    }

    private static int parseGameID(String text, int lineNumber) throws CorruptHistoryException {
        try {
            int gameID = Integer.parseInt(text);
            if (gameID > 0) {
                return gameID;
            }
        } catch (NumberFormatException ignored) {
            // reported below
        }
        throw rowError(lineNumber, "the game ID \"" + text + "\" is not a positive whole number");
    }

    private static Difficulty parseDifficulty(String text, int lineNumber) throws CorruptHistoryException {
        try {
            return Difficulty.valueOf(text.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw rowError(lineNumber, "the difficulty \"" + text + "\" is not Easy, Medium or Hard");
        }
    }

    /**
     * Reads "DD/MM/YYYY; HH:MM:SS", with or without spaces around the semicolon.
     */
    private static LocalDateTime parseTimestamp(String text, int lineNumber) throws CorruptHistoryException {
        String[] parts = text.split(";", -1);
        if (parts.length == 2) {
            try {
                LocalDate date = LocalDate.parse(parts[0].trim(), DATE_FORMAT);
                LocalTime time = LocalTime.parse(parts[1].trim(), TIME_FORMAT);
                return LocalDateTime.of(date, time);
            } catch (DateTimeParseException ignored) {
                // reported below
            }
        }
        throw rowError(lineNumber, "the timestamp \"" + text + "\" is not a valid DD/MM/YYYY; HH:MM:SS");
    }

    /**
     * Returns the names in place order. Places are filled from 1st onwards; unused places at the
     * end are empty, and there are at least two names.
     */
    private static List<String> parseRankedNames(List<String> fields, int lineNumber) throws CorruptHistoryException {
        List<String> names = new ArrayList<>();
        boolean emptySeen = false;
        for (int column = FIRST_PLACE_COLUMN; column < FIRST_PLACE_COLUMN + PLACE_COLUMNS; column++) {
            String name = fields.get(column);
            if (name.isEmpty()) {
                emptySeen = true;
            } else if (emptySeen) {
                throw rowError(lineNumber, "a place has a name after an empty place");
            } else {
                names.add(name);
            }
        }
        if (names.size() < MIN_PLAYERS) {
            throw rowError(lineNumber, "it has " + names.size() + " player name(s); a game has 2 to 4 players");
        }
        return List.copyOf(names);
    }

    private static Duration parseDuration(String text, int lineNumber) throws CorruptHistoryException {
        Matcher matcher = DURATION_FORMAT.matcher(text);
        if (!matcher.matches()) {
            throw rowError(lineNumber, "the duration \"" + text + "\" is not a valid HH:MM:SS");
        }
        try {
            return Duration.ofHours(Long.parseLong(matcher.group(1)))
                    .plusMinutes(Long.parseLong(matcher.group(2)))
                    .plusSeconds(Long.parseLong(matcher.group(3)));
        } catch (NumberFormatException | ArithmeticException e) {
            throw rowError(lineNumber, "the duration \"" + text + "\" is too large");
        }
    }

    private static List<String> trimmedFields(String line) {
        List<String> fields = new ArrayList<>();
        for (String field : line.split(",", -1)) {
            fields.add(field.trim());
        }
        return fields;
    }

    private static CorruptHistoryException rowError(int lineNumber, String reason) {
        return new CorruptHistoryException("Line " + lineNumber + " of the history file cannot be read: "
                + reason + ".");
    }

    /**
     * The history file exists but cannot be read as game history: its header does not match the
     * template or one of its rows is malformed. The message is plain language and names the line,
     * so it can be shown to the player. The file is left unchanged.
     */
    public static class CorruptHistoryException extends Exception {

        public CorruptHistoryException(String message) {
            super(message);
        }

        public CorruptHistoryException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
