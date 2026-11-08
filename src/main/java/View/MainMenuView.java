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

    public MainMenuView() {
        // Game name with the team name underneath
        Label title = new Label("Ludo");
        title.getStyleClass().add("ludo-title");
        Label subtitle = new Label("Team Sloth");
        subtitle.getStyleClass().add("ludo-subtitle");
        VBox titleBox = new VBox(4, title, subtitle);
        titleBox.setAlignment(Pos.CENTER);

        // Primary action first and largest, secondary action below it
        Button startButton = new Button("Start New Game");
        startButton.getStyleClass().addAll("ludo-button", "ludo-button-primary");
        Button historyButton = new Button("Game History");
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
}
