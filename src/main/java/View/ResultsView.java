package View;

import java.util.List;
import java.util.Locale;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;

/**
 * Displays the final-results screen.
 */
public class ResultsView {

    private static final String[] COLOUR_NAMES = {"Red", "Green", "Yellow", "Blue"};
    private static final String[] PLACES = {"1st", "2nd", "3rd", "4th"};
    private static final double CONTENT_WIDTH = 520;

    private final ScrollPane root;
    private final HBox winnerLine = new HBox(10);
    private final VBox ranking = new VBox(6);
    private final Label durationValue = text("", "results-info-value");
    private final Label difficultyValue = text("", "results-info-value");
    private final HBox saveStatus = new HBox(10);
    private final Button retryButton = new Button("Retry saving");

    public enum LeaveChoice { RETRY, STAY, LEAVE }
    private final Button menuButton;
    private final Button newGameButton;

    public ResultsView() {
        // Title and winner line (filled in by showResults)
        Label title = text("Game Over", "ludo-screen-title");
        winnerLine.setAlignment(Pos.CENTER);
        VBox titleBox = new VBox(4, title, winnerLine);
        titleBox.setAlignment(Pos.CENTER);

        // Duration and difficulty side by side
        HBox info = new HBox(40, infoItem("Duration", durationValue), infoItem("Difficulty", difficultyValue));
        info.setAlignment(Pos.CENTER);

        // Save line, hidden until the save outcome is known
        saveStatus.setAlignment(Pos.CENTER_LEFT);
        saveStatus.setVisible(false);
        saveStatus.setManaged(false);
        retryButton.getStyleClass().addAll("ludo-button", "ludo-button-compact");
        retryButton.setMinWidth(Region.USE_PREF_SIZE);
        retryButton.setVisible(false);
        retryButton.setManaged(false);

        // Secondary action on the left, primary action on the right
        menuButton = new Button("Main Menu");
        menuButton.getStyleClass().addAll("ludo-button", "ludo-button-secondary");
        newGameButton = new Button("New Game");
        newGameButton.getStyleClass().addAll("ludo-button", "ludo-button-primary");
        // Start keyboard focus on the primary action once the screen is shown
        newGameButton.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                Platform.runLater(newGameButton::requestFocus);
            }
        });
        HBox buttons = new HBox(24, menuButton, newGameButton);
        buttons.setAlignment(Pos.CENTER);
        VBox.setMargin(buttons, new Insets(4, 0, 0, 0));

        VBox content = new VBox(10, titleBox, ranking, info, saveStatus, buttons);
        content.setAlignment(Pos.TOP_CENTER);
        content.getStyleClass().add("ludo-setup-page");
        content.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        ranking.setPrefWidth(CONTENT_WIDTH);
        saveStatus.setPrefWidth(CONTENT_WIDTH);

        // One centred group at any window size; scrollbars appear only if the window is smaller than the content
        root = new ScrollPane(new StackPane(content));
        root.setFitToWidth(true);
        root.setFitToHeight(true);
        root.setFocusTraversable(false);
        root.getStyleClass().addAll("ludo-setup", "ludo-setup-scroll");
    }

    /**
     * Returns the root node of this screen, ready to be placed in a scene.
     */
    public Parent getRoot() {
        return root;
    }

    /**
     * Sets what happens when "Main Menu" is pressed. {@code null} means nothing happens.
     */
    public void setOnMainMenu(Runnable action) {
        menuButton.setOnAction(action == null ? null : event -> action.run());
    }

    /**
     * Sets what happens when "New Game" is pressed. {@code null} means nothing happens.
     */
    public void setOnNewGame(Runnable action) {
        newGameButton.setOnAction(action == null ? null : event -> action.run());
    }

    public void setOnRetry(Runnable action) {
        retryButton.setOnAction(action == null ? null : event -> action.run());
    }

    /** UI only: the controller performs retries and decides whether navigation proceeds. */
    public LeaveChoice confirmUnsavedResults() {
        ButtonType retry = new ButtonType("Retry saving", ButtonBar.ButtonData.YES);
        ButtonType stay = new ButtonType("Stay here", ButtonBar.ButtonData.CANCEL_CLOSE);
        ButtonType leave = new ButtonType("Continue without saving", ButtonBar.ButtonData.NO);
        Alert alert = new Alert(Alert.AlertType.WARNING,
                "Your game results have not been saved. Leaving now will lose them.", retry, stay, leave);
        alert.setTitle("Unsaved game results");
        alert.setHeaderText("Save results before leaving?");
        if (root.getScene() != null && root.getScene().getWindow() != null) {
            alert.initOwner(root.getScene().getWindow());
        }
        ButtonType chosen = alert.showAndWait().orElse(stay);
        if (chosen == retry) return LeaveChoice.RETRY;
        return chosen == leave ? LeaveChoice.LEAVE : LeaveChoice.STAY;
    }

    /**
     * Shows the final results. {@code names} and {@code colours} ("Red", "Green", "Yellow" or
     * "Blue") are in finishing order, first place first, one entry per player (2 to 4).
     *
     * @param durationSeconds the game's duration, shown as HH:MM:SS
     * @param difficulty      the difficulty as shown to the player, e.g. "Easy"
     */
    public void showResults(List<String> names, List<String> colours, long durationSeconds, String difficulty) {
        if (names.size() != colours.size() || names.isEmpty() || names.size() > PLACES.length) {
            throw new IllegalArgumentException("Need 1 to 4 players, each with exactly one colour");
        }
        int winnerColour = colourIndex(colours.getFirst());
        winnerLine.getChildren().setAll(colourCircle(winnerColour, 12),
                text(names.getFirst() + " wins!", "results-winner-line"));
        ranking.getChildren().clear();
        for (int place = 0; place < names.size(); place++) {
            ranking.getChildren().add(rankingRow(place, names.get(place), colourIndex(colours.get(place))));
        }
        long seconds = Math.max(0, durationSeconds);
        durationValue.setText(String.format(Locale.ROOT, "%02d:%02d:%02d", seconds / 3600, (seconds / 60) % 60, seconds % 60));
        difficultyValue.setText(difficulty);
    }

    /**
     * Shows whether the finished game was saved to the history. A failure only changes this line;
     * the results stay on screen.
     */
    public void setSaveStatus(boolean saved) {
        saveStatus.setVisible(true);
        saveStatus.setManaged(true);
        Label tag = text(saved ? "Saved" : "Not saved", saved ? "results-tag-outline" : "ludo-tag");
        tag.setMinWidth(Region.USE_PREF_SIZE);
        Label message = text(saved
                ? "This game was saved to the game history."
                : "This game could not be saved to the game history. Your results are still shown above.",
                "results-status-text");
        message.setWrapText(true);
        HBox.setHgrow(message, Priority.ALWAYS);
        retryButton.setVisible(!saved);
        retryButton.setManaged(!saved);
        saveStatus.getChildren().setAll(tag, message, retryButton);
        saveStatus.getStyleClass().setAll("results-status", saved ? "results-status-saved" : "results-status-failed");
        saveStatus.setAccessibleText(message.getText());
    }

    /**
     * One ranking row: place, colour circle, colour name and player name. The winner's row is
     * larger, heavily outlined and tagged "Winner", so first place never depends on colour.
     */
    private static HBox rankingRow(int place, String name, int colour) {
        boolean winner = place == 0;
        Label placeLabel = text(PLACES[place], winner ? "results-place-winner" : "results-place");
        placeLabel.setMinWidth(56);
        Label colourLabel = text(COLOUR_NAMES[colour], "results-colour");
        colourLabel.setMinWidth(64);
        Label nameLabel = text(name, winner ? "results-name-winner" : "results-name");
        nameLabel.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(nameLabel, Priority.ALWAYS);

        HBox row = new HBox(12, placeLabel, colourCircle(colour, winner ? 13 : 10), colourLabel, nameLabel);
        if (winner) {
            row.getChildren().add(text("Winner", "ludo-tag"));
        }
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().addAll("results-row", winner ? "results-row-winner" : "results-row-other");
        row.setAccessibleText(PLACES[place] + " place: " + name + ", " + COLOUR_NAMES[colour]);
        return row;
    }

    private static VBox infoItem(String caption, Label value) {
        VBox item = new VBox(0, text(caption, "ludo-hint"), value);
        item.setAlignment(Pos.CENTER);
        return item;
    }

    private static int colourIndex(String colour) {
        for (int i = 0; i < COLOUR_NAMES.length; i++) {
            if (COLOUR_NAMES[i].equalsIgnoreCase(colour)) {
                return i;
            }
        }
        throw new IllegalArgumentException("Unknown colour: " + colour);
    }

    private static Circle colourCircle(int colour, double radius) {
        Circle circle = new Circle(radius);
        circle.getStyleClass().addAll("player-circle", "player-" + COLOUR_NAMES[colour].toLowerCase());
        return circle;
    }

    private static Label text(String value, String styleClass) {
        Label label = new Label(value);
        label.getStyleClass().add(styleClass);
        return label;
    }
}
