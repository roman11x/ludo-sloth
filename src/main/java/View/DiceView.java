package View;

import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * Displays the dice area with the current roll.
 */
public class DiceView {

    private final VBox root;

    public DiceView() {
        // Heading and the die available at the Easy level
        Label heading = new Label("Dice");
        heading.getStyleClass().add("ludo-section-label");
        Label available = new Label("Standard die, 1–6");
        available.getStyleClass().add("ludo-summary-text");

        // Result space, empty until the first roll
        Label resultCaption = new Label("Result");
        resultCaption.getStyleClass().add("ludo-hint");
        Label resultLabel = new Label();
        resultLabel.getStyleClass().add("dice-result-value");
        StackPane resultBox = new StackPane(resultLabel);
        resultBox.getStyleClass().add("dice-result");
        resultBox.setAccessibleText("Die result: no roll yet");

        // Primary action of the turn, same look as the other primary buttons
        Button rollButton = new Button("Roll (Space)");
        rollButton.getStyleClass().addAll("ludo-button", "ludo-button-primary", "ludo-button-compact");

        root = new VBox(10, heading, available, resultCaption, resultBox, rollButton);
        root.setAlignment(Pos.TOP_CENTER);
        root.getStyleClass().add("ludo-panel");
    }

    /**
     * Returns the root node of the dice area, ready to be placed inside the board screen.
     */
    public Parent getRoot() {
        return root;
    }
}
