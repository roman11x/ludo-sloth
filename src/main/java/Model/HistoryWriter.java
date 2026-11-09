package Model;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Appends a completed game to the history CSV.
 */
public class HistoryWriter {
    //history csv template header
    private static final String HEADER = "gameID,difficulty,"
            + "timestamp(DD/MM/YYYY; HH:MM:SS),"
            + "1st_place_name,2nd_place_name,3rd_place_name,4rd_place_name,"
            + "game_duration(HH:MM:SS)";
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy; HH:mm:ss"); // Format the timestamp as dd/MM/yyyy; HH:mm:ss
    private static final int NUM_PLACE_COLUMNS = 4; // Maximum number of place columns to pad the ranked names
    private final Path path; // the path to the history csv file

    /**
     * @param path a path that includes a parent directory.
     */
    public HistoryWriter(Path path) {
        this.path = path;
    }

    public void createFileIfNotExists() throws IOException {
        if (!Files.exists(path)) {
            // Create the parent directory and any missing directories above it.
            Files.createDirectories(path.getParent());
            Files.writeString(path, HEADER + "\n");
        }
    }

    /**
     * Saves a new game record to the history file.
     * The record must not already have an assigned game ID. A new ID is determined
     * from the existing history entries, assigned to a copy of the record, and the
     * formatted record is appended to the file. If the history file does not exist,
     * it is created before writing.
     *
     * @param record the game record to save; must have an unassigned game ID
     * @throws NullPointerException     if record is null
     * @throws IllegalArgumentException if record already has an assigned game ID
     * @throws IOException              if the history file cannot be created, read, or written
     */
    public void saveRecord(GameRecord record) throws IOException {
        Objects.requireNonNull(record, "You cannot save a null record");

        if (record.gameID() != GameRecord.UNASSIGNED_ID) {
            throw new IllegalArgumentException("Got id: " + record.gameID() +
                    " Expected id: " + GameRecord.UNASSIGNED_ID);
        }

        createFileIfNotExists();

        List<String> csvLines = Files.readAllLines(path);
        var nextID = nextGameID(csvLines);
        var recordToBeWritten = new GameRecord(nextID,
                record.difficulty(),
                record.startTime(),
                record.rankedNames(),
                record.duration());

        var formattedRecord = formatRecordForSaving(recordToBeWritten);
        Files.writeString(path, formattedRecord, StandardOpenOption.APPEND);
    }

    /**
     * Formats a game record as a single CSV line for saving to the history file.
     * The record includes the game ID, difficulty in lowercase, formatted start time,
     * ranked player names padded to four place columns, and duration formatted as hours,
     * minutes, and seconds.
     *
     * @param record the game record to format for saving
     * @return the formatted CSV line ending with a newline character
     */
    static String formatRecordForSaving(GameRecord record) {
        var commas = ",".repeat(NUM_PLACE_COLUMNS - record.rankedNames().size()); // Pad the ranked names with commas

        var id = record.gameID();
        var difficulty = record.difficulty().name().toLowerCase(Locale.ROOT);
        var startTime = record.startTime().format(TIMESTAMP_FORMATTER);
        var names = String.join(",", record.rankedNames()) + commas;
        var duration = String.format("%02d:%02d:%02d", record.duration().toHours(),
                record.duration().toMinutesPart(),
                record.duration().toSecondsPart());


        return id + "," + difficulty + "," + startTime + "," + names + "," + duration + "\n";
    }

    // Package-private to allow access to tests in the same package.

    /**
     * Determines the next available game ID from the history CSV lines.
     * The first line is treated as the header, and each remaining line is expected
     * to contain the game ID as the first comma-separated field.
     *
     * @param history the history CSV lines; must contain at least the header as the first line
     * @return one greater than the highest game ID found, or 1 if there are no records
     */
    static int nextGameID(List<String> history) {
        int maxID = 0;

        // the first line is the header, so we take all lines except the first one
        var recordLines = history.subList(1, history.size());

        for (String line : recordLines) {
            String[] fields = line.split(",");
            var currentID = Integer.parseInt(fields[0]); //gameID is the first column
            if (currentID > maxID) {
                maxID = currentID;
            }
        }
        return maxID + 1;
    }
}
