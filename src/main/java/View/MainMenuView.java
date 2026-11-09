package View;

import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * Displays the main menu screen.
 */
public class MainMenuView {

    private final StackPane root;
    private final Button startButton;
    private final Button historyButton;

    public MainMenuView() {
        // Game name with the team name underneath
        Label title = new Label("Ludo");
        title.getStyleClass().add("ludo-title");
        Label subtitle = new Label("Team Sloth");
        subtitle.getStyleClass().add("ludo-subtitle");
        VBox titleBox = new VBox(4, title, subtitle);
        titleBox.setAlignment(Pos.CENTER);

        // Primary action first and largest, secondary action below it
        startButton = new Button("Start New Game");
        startButton.getStyleClass().addAll("ludo-button", "ludo-button-primary");
        historyButton = new Button("Game History");
        historyButton.getStyleClass().addAll("ludo-button", "ludo-button-secondary");
        VBox buttons = new VBox(24, startButton, historyButton);
        buttons.setAlignment(Pos.CENTER);

        // Title above the buttons, kept in the centre of the window at any size
        VBox content = new VBox(40, titleBox, buttons);
        content.setAlignment(Pos.CENTER);
        root = new StackPane(content);
        root.getStyleClass().add("ludo-root");
    }

    /**
     * Returns the root node of this screen, ready to be placed in a scene.
     */
    public Parent getRoot() {
        return root;
    }

    /**
     * Sets what happens when "Start New Game" is pressed. {@code null} means nothing happens.
     */
    public void setOnStartNewGame(Runnable action) {
        startButton.setOnAction(action == null ? null : event -> action.run());
    }

    /**
     * Sets what happens when "Game History" is pressed. {@code null} means nothing happens.
     */
    public void setOnGameHistory(Runnable action) {
        historyButton.setOnAction(action == null ? null : event -> action.run());
    }
}
