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
    private static final String LINE_BREAK = "\n";
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
     * The record must not already have an assigned game ID. A new ID is assigned
     * using the next available ID from the existing history records, and the
     * resulting record is appended to the file.
     *
     * @param record the game record to save; its game ID must be
     *               {@link GameRecord#UNASSIGNED_ID}
     * @throws NullPointerException if the record is null
     * @throws IllegalArgumentException if the record already has an assigned game ID
     * @throws IOException if the history file cannot be created, read, or written,
     *                     or if the existing history file is malformed
     */
    public void saveRecord(GameRecord record) throws IOException {
        Objects.requireNonNull(record, "You cannot save a null record");
        if (record.gameID() != GameRecord.UNASSIGNED_ID) {
            throw new IllegalArgumentException("Got id: " + record.gameID() +
                    " Expected id: " + GameRecord.UNASSIGNED_ID);
        }

        createFileIfNotExists();

        List<GameRecord> recordLines = readRecords(path);

        var nextID = nextGameID(recordLines);
        var recordToBeWritten = new GameRecord(nextID,
                record.difficulty(),
                record.startTime(),
                record.rankedNames(),
                record.duration());
        var formattedRecord = formatRecordForSaving(recordToBeWritten);

        String text = Files.readString(path); // read the entire file into a string
        if (!text.endsWith(LINE_BREAK)) {
            Files.writeString(path, LINE_BREAK, StandardOpenOption.APPEND);
        }

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
        var duration = String.format(Locale.ROOT,"%02d:%02d:%02d", record.duration().toHours(),
                record.duration().toMinutesPart(),
                record.duration().toSecondsPart());


        return id + "," + difficulty + "," + startTime + "," + names + "," + duration + "\n";
    }

    /**
     * Determines the next available game ID from the given records.
     * The returned ID is one greater than the highest game ID currently present,
     * or 1 if the list is empty.
     *
     * @param records the game records to inspect
     * @return the next available game ID
     */
    static int nextGameID(List<GameRecord> records) {
        int maxID = 0;

        for (GameRecord record: records) {
            if (record.gameID() > maxID) {
                maxID = record.gameID();
            }
        }
        return maxID + 1;
    }

    /**
     * Reads all game records from the CSV history file at the specified path.
     *
     * @param path the path to the history CSV file to read
     * @return the game records read from the history file
     * @throws IOException if the history file cannot be read or is malformed
     */
    private List<GameRecord> readRecords(Path path) throws IOException {
        try {
            return new HistoryReader(path).readAll();
        } catch (HistoryReader.CorruptHistoryException e) {
            throw new IOException("History file is malformed: " + path, e);
        }
    }
}
