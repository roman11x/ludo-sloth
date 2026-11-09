package View;

import java.util.List;
import java.util.function.Function;

import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
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

    // Sample history shown until HistoryController supplies the real records; newest first
    private static final List<GameRow> SAMPLE_GAMES = List.of(
            new GameRow("6", "07/11/2026 20:15", "Easy", "Maya", "Noa", "Daniel", "Omer", "00:42:17"),
            new GameRow("5", "07/11/2026 18:40", "Easy", "Noa", "Maya", "", "", "00:18:05"),
            new GameRow("4", "05/11/2026 21:02", "Easy", "Omer", "Noa", "Daniel", "", "00:31:48"),
            new GameRow("3", "03/11/2026 19:30", "Easy", "Daniel", "Maya", "", "", "00:15:22"),
            new GameRow("2", "01/11/2026 17:45", "Easy", "Maya", "Omer", "Daniel", "Noa", "00:47:09"),
            new GameRow("1", "30/10/2026 20:10", "Easy", "Noa", "Daniel", "Maya", "", "00:28:56"));

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
        table.getItems().setAll(SAMPLE_GAMES);
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
