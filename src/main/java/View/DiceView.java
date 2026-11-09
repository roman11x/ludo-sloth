package View;

import javafx.event.EventHandler;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * Displays the dice area with the current roll.
 */
public class DiceView {

    private final VBox root;
    private final Button rollButton;
    private final Label resultLabel;
    private final StackPane resultBox;
    private boolean spaceHeld;

    public DiceView() {
        // Heading and the die available at the Easy level
        Label heading = new Label("Dice");
        heading.getStyleClass().add("ludo-section-label");
        Label available = new Label("Standard die, 1–6");
        available.getStyleClass().add("ludo-summary-text");

        // Result space, empty until the first roll
        Label resultCaption = new Label("Result");
        resultCaption.getStyleClass().add("ludo-hint");
        resultLabel = new Label();
        resultLabel.getStyleClass().add("dice-result-value");
        resultBox = new StackPane(resultLabel);
        resultBox.getStyleClass().add("dice-result");
        resultBox.setAccessibleText("Die result: no roll yet");

        // Primary action of the turn, same look as the other primary buttons
        rollButton = new Button("Roll (Space)");
        rollButton.getStyleClass().addAll("ludo-button", "ludo-button-primary", "ludo-button-compact");
        installSpaceShortcut();

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

    /**
     * Sets what happens when the player rolls, by clicking the button or pressing Space anywhere
     * on the screen. {@code null} means rolling does nothing.
     */
    public void setOnRoll(Runnable action) {
        rollButton.setOnAction(action == null ? null : event -> action.run());
    }

    /**
     * Shows the result of the latest roll in the result area, for sighted users and screen readers.
     */
    public void showResult(int value) {
        String text = String.valueOf(value);
        resultLabel.setText(text);
        resultBox.setAccessibleText("Die result: " + text);
    }

    /**
     * Space presses the Roll button wherever the focus is, once per key press (holding the key
     * does not repeat). The scene-level filter consumes both key events, so a focused Roll button
     * is not also pressed by its own Space handling.
     */
    private void installSpaceShortcut() {
        EventHandler<KeyEvent> spaceFilter = event -> {
            if (event.getCode() != KeyCode.SPACE) {
                return;
            }
            if (event.getEventType() == KeyEvent.KEY_PRESSED) {
                if (!spaceHeld && !rollButton.isDisabled()) {
                    rollButton.fire();
                }
                spaceHeld = true;
            } else {
                spaceHeld = false;
            }
            event.consume();
        };
        rollButton.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (oldScene != null) {
                oldScene.removeEventFilter(KeyEvent.KEY_PRESSED, spaceFilter);
                oldScene.removeEventFilter(KeyEvent.KEY_RELEASED, spaceFilter);
            }
            if (newScene != null) {
                newScene.addEventFilter(KeyEvent.KEY_PRESSED, spaceFilter);
                newScene.addEventFilter(KeyEvent.KEY_RELEASED, spaceFilter);
            }
        });
    }
}
