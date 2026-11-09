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
     * Saves a game record to the history file.
     * The record must not already have an assigned game ID. A new game ID is
     * assigned based on the existing history contents, and the resulting record is
     * appended as a CSV line.
     *
     * @param record the game record to save; its game ID must be
     *               {@link GameRecord#UNASSIGNED_ID}
     * @throws NullPointerException if record is null
     * @throws IllegalArgumentException if the record already has an assigned game ID
     * @throws IOException if the history file cannot be read or written, or if the
     *                     history file is malformed
     */
    public void saveRecord(GameRecord record) throws IOException {
        Objects.requireNonNull(record, "You cannot save a null record");
        if (record.gameID() != GameRecord.UNASSIGNED_ID) {
            throw new IllegalArgumentException("Got id: " + record.gameID() +
                    " Expected id: " + GameRecord.UNASSIGNED_ID);
        }

        createFileIfNotExists();

        List<String> csvLines = Files.readAllLines(path);
        if (csvLines.isEmpty() || !csvLines.getFirst().equals(HEADER)) {
            throw new IOException("History file is malformed (missing or wrong header): " + path );
        }

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

    /**
     * Determines the next available positive game ID from the game-history CSV lines.
     * The first line is treated as the header and ignored. Each remaining line must
     * have a positive integer game ID in the first comma-separated column.
     *
     * @param history the complete game-history file contents, including the header line
     * @return one greater than the largest game ID found, or 1 if there are no records
     * @throws IOException if a record line contains a missing, non-numeric, or non-positive game ID
     */
    static int nextGameID(List<String> history) throws IOException {
        int maxID = 0;
        int currentID;

        // the first line is the header, so we take all lines except the first one
        var recordLines = history.subList(1, history.size());

        for (String line : recordLines) {
            String[] fields = line.split(",");
            try {
                currentID = Integer.parseInt(fields[0]); //gameID is the first column
            } catch (NumberFormatException e) {
                throw new IOException("History file is malformed (invalid game ID): " + line, e);
            }
            if (currentID <= 0) {
                throw new IOException("History file is malformed (invalid game ID): " + line);
            }
            if (currentID > maxID) {
                maxID = currentID;
            }
        }
        return maxID + 1;
    }
}
