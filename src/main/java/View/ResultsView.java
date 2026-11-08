package View;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
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

    // Sample game shown until ResultsController supplies the real one; lists are in finishing order
    private static final String[] SAMPLE_NAMES = {"Maya", "Noa", "Daniel", "Omer"};
    private static final int[] SAMPLE_COLOURS = {0, 2, 1, 3};
    private static final String SAMPLE_DURATION = "00:42:17";
    private static final String SAMPLE_DIFFICULTY = "Easy";

    private final ScrollPane root;
    private final HBox saveStatus = new HBox(10);

    public ResultsView() {
        // Title and winner line
        Label title = text("Game Over", "ludo-screen-title");
        HBox winnerLine = new HBox(10, colourCircle(SAMPLE_COLOURS[0], 12),
                text(SAMPLE_NAMES[0] + " wins!", "results-winner-line"));
        winnerLine.setAlignment(Pos.CENTER);
        VBox titleBox = new VBox(4, title, winnerLine);
        titleBox.setAlignment(Pos.CENTER);

        // Final ranking, first place first
        VBox ranking = new VBox(6);
        for (int place = 0; place < SAMPLE_NAMES.length; place++) {
            ranking.getChildren().add(rankingRow(place, SAMPLE_NAMES[place], SAMPLE_COLOURS[place]));
        }

        // Duration and difficulty side by side
        HBox info = new HBox(40, infoItem("Duration", SAMPLE_DURATION), infoItem("Difficulty", SAMPLE_DIFFICULTY));
        info.setAlignment(Pos.CENTER);

        saveStatus.setAlignment(Pos.CENTER_LEFT);
        setSaveStatus(true);

        // Secondary action on the left, primary action on the right
        Button menuButton = new Button("Main Menu");
        menuButton.getStyleClass().addAll("ludo-button", "ludo-button-secondary");
        Button newGameButton = new Button("New Game");
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
     * Shows whether the finished game was saved to the history. A failure only changes this line;
     * the results stay on screen.
     */
    public void setSaveStatus(boolean saved) {
        Label tag = text(saved ? "Saved" : "Not saved", saved ? "results-tag-outline" : "ludo-tag");
        tag.setMinWidth(Region.USE_PREF_SIZE);
        Label message = text(saved
                ? "This game was saved to the game history."
                : "This game could not be saved to the game history. Your results are still shown above.",
                "results-status-text");
        message.setWrapText(true);
        HBox.setHgrow(message, Priority.ALWAYS);
        saveStatus.getChildren().setAll(tag, message);
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

    private static VBox infoItem(String caption, String value) {
        VBox item = new VBox(0, text(caption, "ludo-hint"), text(value, "results-info-value"));
        item.setAlignment(Pos.CENTER);
        return item;
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
