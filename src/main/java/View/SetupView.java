package View;

import java.util.ArrayList;
import java.util.List;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;

/**
 * Displays the game-setup screen.
 */
public class SetupView {

    private static final String[] COLOUR_NAMES = {"Red", "Green", "Yellow", "Blue"};
    private static final int MIN_PLAYERS = 2;
    private static final int MAX_PLAYERS = 4;
    private static final int DEFAULT_PLAYER_COUNT = 2;
    private static final double SUMMARY_WIDTH = 220;
    private static final double PAGE_MAX_WIDTH = 960;

    private final ScrollPane root;
    private final List<PlayerRow> rows = new ArrayList<>();
    private final VBox summaryLines = new VBox(8);
    private final Button backButton;
    private final Button startButton;
    private int playerCount = MAX_PLAYERS;

    public SetupView() {
        // Screen title, centred like the Main Menu title
        Label title = new Label("New Game Setup");
        title.getStyleClass().add("ludo-screen-title");
        BorderPane.setAlignment(title, Pos.CENTER);
        BorderPane.setMargin(title, new Insets(0, 0, 16, 0));

        // Form on the left, live summary on the right
        VBox form = new VBox(16, buildPlayerCountRow(), buildPlayerRows(), buildDifficulty());
        HBox.setHgrow(form, Priority.ALWAYS);
        HBox middle = new HBox(24, form, buildSummary());

        // Secondary action on the left, primary action on the right
        backButton = new Button("Back to Main Menu");
        backButton.getStyleClass().addAll("ludo-button", "ludo-button-secondary");
        startButton = new Button("Start Game");
        startButton.getStyleClass().addAll("ludo-button", "ludo-button-primary");
        HBox buttons = new HBox(24, backButton, startButton);
        buttons.setAlignment(Pos.CENTER);
        BorderPane.setMargin(buttons, new Insets(20, 0, 0, 0));

        // Top, centre and bottom are set in reading order so Tab follows it
        BorderPane page = new BorderPane();
        page.setTop(title);
        page.setCenter(middle);
        page.setBottom(buttons);
        page.getStyleClass().add("ludo-setup-page");
        page.setMaxWidth(PAGE_MAX_WIDTH);
        page.setMaxHeight(Region.USE_PREF_SIZE);

        // Centred at any window size; scrollbars appear only if the window is smaller than the content
        root = new ScrollPane(new StackPane(page));
        root.setFitToWidth(true);
        root.setFitToHeight(true);
        root.setFocusTraversable(false);
        root.getStyleClass().addAll("ludo-setup", "ludo-setup-scroll");

        applyPlayerCount(DEFAULT_PLAYER_COUNT);
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
     * Sets what happens when "Start Game" is pressed. {@code null} means nothing happens.
     */
    public void setOnStartGame(Runnable action) {
        startButton.setOnAction(action == null ? null : event -> action.run());
    }

    /**
     * Returns the selected number of players (2, 3 or 4).
     */
    public int getPlayerCount() {
        return playerCount;
    }

    /**
     * Returns each player's name exactly as typed (possibly empty), Player 1 first.
     * The list has {@link #getPlayerCount()} entries and cannot be modified.
     */
    public List<String> getPlayerNames() {
        List<String> names = new ArrayList<>();
        for (PlayerRow row : rows) {
            if (row.isShown()) {
                names.add(row.nameField.getText());
            }
        }
        return List.copyOf(names);
    }

    /**
     * Returns each player's chosen colour as "Red", "Green", "Yellow" or "Blue", Player 1 first,
     * in the same order as {@link #getPlayerNames()}. The list cannot be modified.
     */
    public List<String> getPlayerColours() {
        List<String> colours = new ArrayList<>();
        for (PlayerRow row : rows) {
            if (row.isShown()) {
                colours.add(COLOUR_NAMES[row.selectedColour()]);
            }
        }
        return List.copyOf(colours);
    }

    private HBox buildPlayerCountRow() {
        HBox row = new HBox(16, sectionLabel("Number of players:"));
        row.setAlignment(Pos.CENTER_LEFT);
        ToggleGroup countGroup = new ToggleGroup();
        for (int count = MIN_PLAYERS; count <= MAX_PLAYERS; count++) {
            RadioButton option = new RadioButton(String.valueOf(count));
            option.getStyleClass().add("ludo-radio");
            option.setUserData(count);
            option.setToggleGroup(countGroup);
            option.setSelected(count == DEFAULT_PLAYER_COUNT);
            row.getChildren().add(option);
        }
        countGroup.selectedToggleProperty().addListener((obs, oldToggle, newToggle) -> {
            if (newToggle != null) {
                applyPlayerCount((Integer) newToggle.getUserData());
            }
        });
        return row;
    }

    private VBox buildPlayerRows() {
        VBox box = new VBox(8);
        for (int number = 1; number <= MAX_PLAYERS; number++) {
            PlayerRow row = new PlayerRow(number, number - 1);
            rows.add(row);
            box.getChildren().add(row.node);
        }
        return box;
    }

    private VBox buildDifficulty() {
        RadioButton easy = new RadioButton("Easy");
        easy.getStyleClass().add("ludo-radio");
        easy.setToggleGroup(new ToggleGroup());
        easy.setSelected(true);
        HBox row = new HBox(16, sectionLabel("Difficulty:"), easy);
        row.setAlignment(Pos.CENTER_LEFT);
        Label description = new Label("Classic board, one standard die.");
        description.getStyleClass().add("ludo-hint");
        return new VBox(4, row, description);
    }

    private VBox buildSummary() {
        VBox box = new VBox(10, sectionLabel("Summary"), summaryLines);
        box.getStyleClass().add("ludo-summary");
        box.setMinWidth(SUMMARY_WIDTH);
        box.setPrefWidth(SUMMARY_WIDTH);
        box.setMaxWidth(SUMMARY_WIDTH);
        return box;
    }

    /**
     * Shows exactly {@code count} player rows. A row that reappears keeps its name, and keeps its
     * colour unless another shown player now holds it, in which case it gets the first free colour.
     */
    private void applyPlayerCount(int count) {
        playerCount = count;
        for (PlayerRow row : rows) {
            boolean shown = row.number <= count;
            if (shown && !row.isShown()) {
                row.setShown(true);
                if (isTakenByOther(row, row.selectedColour())) {
                    row.selectColour(firstFreeColour(row));
                }
            } else if (!shown) {
                row.setShown(false);
            }
        }
        refreshColours();
    }

    /**
     * Disables, in every row, the colours currently held by other shown players.
     */
    private void refreshColours() {
        for (PlayerRow row : rows) {
            for (int colour = 0; colour < COLOUR_NAMES.length; colour++) {
                row.colourOptions.get(colour).setDisable(isTakenByOther(row, colour));
            }
        }
        refreshSummary();
    }

    private boolean isTakenByOther(PlayerRow row, int colour) {
        for (PlayerRow other : rows) {
            if (other != row && other.isShown() && other.selectedColour() == colour) {
                return true;
            }
        }
        return false;
    }

    private int firstFreeColour(PlayerRow row) {
        for (int colour = 0; colour < COLOUR_NAMES.length; colour++) {
            if (!isTakenByOther(row, colour)) {
                return colour;
            }
        }
        return row.selectedColour();
    }

    private void refreshSummary() {
        summaryLines.getChildren().setAll(
                summaryText("Difficulty: Easy"),
                summaryText("Players: " + playerCount));
        for (PlayerRow row : rows) {
            if (!row.isShown()) {
                continue;
            }
            String name = row.nameField.getText().strip();
            Label player = summaryText("Player " + row.number + ": " + (name.isEmpty() ? "(no name yet)" : name));
            int colour = row.selectedColour();
            HBox colourLine = new HBox(6, colourCircle(colour, 7), summaryText(COLOUR_NAMES[colour]));
            colourLine.setAlignment(Pos.CENTER_LEFT);
            colourLine.setPadding(new Insets(0, 0, 0, 16));
            summaryLines.getChildren().add(new VBox(2, player, colourLine));
        }
    }

    private static Label sectionLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("ludo-section-label");
        label.setMinWidth(Region.USE_PREF_SIZE);
        return label;
    }

    private static Label summaryText(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("ludo-summary-text");
        return label;
    }

    private static Circle colourCircle(int colour, double radius) {
        Circle circle = new Circle(radius);
        circle.getStyleClass().addAll("player-circle", "player-" + COLOUR_NAMES[colour].toLowerCase());
        return circle;
    }

    /**
     * Keeps a toggle group from ever being left with nothing selected, e.g. when the selected
     * toggle button is clicked again.
     */
    private static void keepOneSelected(ToggleGroup group) {
        group.selectedToggleProperty().addListener((obs, oldToggle, newToggle) -> {
            if (newToggle == null && oldToggle != null) {
                oldToggle.setSelected(true);
            }
        });
    }

    /**
     * One player's row: number, name field and the four colour options.
     */
    private final class PlayerRow {

        private final int number;
        private final HBox node;
        private final TextField nameField;
        private final ToggleGroup colourGroup = new ToggleGroup();
        private final List<ToggleButton> colourOptions = new ArrayList<>();

        private PlayerRow(int number, int startColour) {
            this.number = number;

            Label label = new Label("Player " + number);
            label.getStyleClass().add("ludo-player-label");
            label.setMinWidth(Region.USE_PREF_SIZE);

            nameField = new TextField();
            nameField.getStyleClass().add("ludo-text-field");
            nameField.setPromptText("Player " + number + " name");
            nameField.setAccessibleText("Player " + number + " name");
            nameField.setPrefColumnCount(10);
            nameField.setMinWidth(100);
            HBox.setHgrow(nameField, Priority.ALWAYS);

            HBox colours = new HBox(4);
            for (int colour = 0; colour < COLOUR_NAMES.length; colour++) {
                ToggleButton option = new ToggleButton(COLOUR_NAMES[colour], colourCircle(colour, 9));
                option.getStyleClass().add("colour-option");
                option.setUserData(colour);
                option.setToggleGroup(colourGroup);
                colourOptions.add(option);
                colours.getChildren().add(option);
            }
            colourOptions.get(startColour).setSelected(true);
            keepOneSelected(colourGroup);

            colourGroup.selectedToggleProperty().addListener((obs, oldToggle, newToggle) -> {
                if (newToggle != null) {
                    refreshColours();
                }
            });
            nameField.textProperty().addListener((obs, oldText, newText) -> refreshSummary());

            node = new HBox(10, label, nameField, colours);
            node.setAlignment(Pos.CENTER_LEFT);
        }

        private int selectedColour() {
            Toggle selected = colourGroup.getSelectedToggle();
            return selected == null ? -1 : (Integer) selected.getUserData();
        }

        private void selectColour(int colour) {
            colourOptions.get(colour).setSelected(true);
        }

        private boolean isShown() {
            return node.isVisible();
        }

        private void setShown(boolean shown) {
            node.setVisible(shown);
            node.setManaged(shown);
        }
    }
}
