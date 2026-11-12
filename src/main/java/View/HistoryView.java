package View;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Pos;
import javafx.scene.AccessibleRole;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;

/**
 * Displays the game-history screen.
 */
public class HistoryView {

    private static final double ROW_HEIGHT = 34;
    private static final double HEADER_HEIGHT = 40;
    private static final double MIN_TABLE_HEIGHT = 120;
    private static final double CONTENT_MAX_WIDTH = 900;
    private static final double HEADER_FONT_SIZE = 14;
    private static final double HEADER_PADDING = 20;

    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final int PLACE_COLUMNS = 4;

    private final StackPane root;
    private final StackPane listArea;
    private final TableView<GameRow> table = new TableView<>();
    private final Button backButton;

    public HistoryView() {
        Label title = new Label("Game History");
        title.getStyleClass().add("ludo-screen-title");
        title.setMinHeight(Region.USE_PREF_SIZE);

        // Completed games table; only the table shrinks and scrolls when space runs out
        table.getColumns().setAll(List.of(
                column("Game no.", GameRow::number, 64),
                column("Date and time", GameRow::dateTime, 140),
                column("Difficulty", GameRow::difficulty, 90),
                column("1st place", GameRow::first, 92),
                column("2nd place", GameRow::second, 92),
                column("3rd place", GameRow::third, 92),
                column("4th place", GameRow::fourth, 92),
                column("Duration", GameRow::duration, 96)));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        table.setFixedCellSize(ROW_HEIGHT);
        table.getStyleClass().add("history-table");
        table.setMinHeight(MIN_TABLE_HEIGHT);
        table.prefHeightProperty().bind(Bindings.size(table.getItems()).multiply(ROW_HEIGHT).add(HEADER_HEIGHT));
        table.setAccessibleText("Completed games, newest first");
        listArea = new StackPane(table);

        backButton = new Button("Back to Main Menu");
        backButton.getStyleClass().addAll("ludo-button", "ludo-button-secondary");
        backButton.setMinHeight(Region.USE_PREF_SIZE);

        // One centred group: compact when there are few games, filling the height when there are many
        VBox content = new VBox(14, title, listArea, backButton);
        content.setAlignment(Pos.TOP_CENTER);
        content.setMaxWidth(CONTENT_MAX_WIDTH);
        content.setMaxHeight(Region.USE_PREF_SIZE);

        root = new StackPane(content);
        root.getStyleClass().addAll("ludo-setup", "history-screen");
    }

    /**
     * Returns the root node of this screen, ready to be placed in a scene.
     */
    public Parent getRoot() {
        return root;
    }

    /**
     * Sets what happens when "Back to Main Menu" is pressed. {@code null} means nothing happens.
     */
    public void setOnBack(Runnable action) {
        backButton.setOnAction(action == null ? null : event -> action.run());
    }

    /**
     * Adds one completed game as the next table row (call newest first) and shows the table.
     *
     * @param gameNumber      the game's number in the history
     * @param startTime       when the game started, shown as DD/MM/YYYY HH:MM
     * @param difficulty      the difficulty as shown to the player, e.g. "Easy"
     * @param rankedNames     the players in finishing order, 1st place first (2 to 4 names);
     *                        unused places are shown empty
     * @param durationSeconds the game's duration, shown as HH:MM:SS
     */
    public void addGame(int gameNumber, LocalDateTime startTime, String difficulty,
                        List<String> rankedNames, long durationSeconds) {
        if (rankedNames.size() > PLACE_COLUMNS) {
            throw new IllegalArgumentException("At most " + PLACE_COLUMNS + " places");
        }
        String[] places = new String[PLACE_COLUMNS];
        for (int i = 0; i < PLACE_COLUMNS; i++) {
            places[i] = i < rankedNames.size() ? rankedNames.get(i) : "";
        }
        long seconds = Math.max(0, durationSeconds);
        String duration = String.format(Locale.ROOT, "%02d:%02d:%02d", seconds / 3600, (seconds / 60) % 60, seconds % 60);
        table.getItems().add(new GameRow(String.valueOf(gameNumber), startTime.format(DATE_TIME_FORMAT),
                difficulty, places[0], places[1], places[2], places[3], duration));
        listArea.getChildren().setAll(table);
    }

    /**
     * Removes all rows and shows the (empty) table again.
     */
    public void clearGames() {
        table.getItems().clear();
        listArea.getChildren().setAll(table);
    }

    /**
     * Replaces the table with the empty-history message, for when no game has been finished yet.
     */
    public void showNoGames() {
        table.getItems().clear();
        Label message = new Label("No games played yet. Finished games will appear here.");
        message.getStyleClass().add("history-empty-text");
        message.setWrapText(true);
        StackPane panel = new StackPane(message);
        panel.getStyleClass().addAll("ludo-panel", "history-empty");
        listArea.getChildren().setAll(panel);
    }

    /**
     * Replaces the table with an error notice, for when the history file cannot be read.
     * {@code message} is plain text describing the problem. Back to Main Menu keeps working.
     */
    public void showLoadError(String message) {
        table.getItems().clear();
        Label tag = new Label("Error");
        tag.getStyleClass().add("ludo-tag");
        tag.setMinWidth(Region.USE_PREF_SIZE);
        Label heading = new Label("The game history could not be read.");
        heading.getStyleClass().add("history-error-heading");
        heading.setWrapText(true);
        HBox headingRow = new HBox(10, tag, heading);
        headingRow.setAlignment(Pos.CENTER_LEFT);
        Label detail = new Label(message == null ? "" : message);
        detail.getStyleClass().add("history-error-text");
        detail.setWrapText(true);
        VBox panel = new VBox(10, headingRow, detail);
        panel.getStyleClass().add("history-error");
        panel.setAccessibleRole(AccessibleRole.TEXT);
        panel.setAccessibleText(heading.getText() + " " + detail.getText());
        listArea.getChildren().setAll(panel);
    }

    private static TableColumn<GameRow, String> column(String header, Function<GameRow, String> value, double width) {
        TableColumn<GameRow, String> column = new TableColumn<>(header);
        column.setCellValueFactory(cell -> new SimpleStringProperty(value.apply(cell.getValue())));
        // Never narrower than its bold 14px header text plus padding, so the header is never cut off
        Text headerText = new Text(header);
        headerText.setFont(Font.font(Font.getDefault().getFamily(), FontWeight.BOLD, HEADER_FONT_SIZE));
        double headerWidth = Math.ceil(headerText.getLayoutBounds().getWidth()) + HEADER_PADDING;
        column.setMinWidth(headerWidth);
        column.setPrefWidth(Math.max(width, headerWidth));
        column.setSortable(false);
        column.setReorderable(false);
        return column;
    }

    /**
     * One completed game as shown in the table; unused places are empty strings.
     */
    private record GameRow(String number, String dateTime, String difficulty,
                           String first, String second, String third, String fourth, String duration) {
    }
}
