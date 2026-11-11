package View;

import java.util.Random;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.event.EventHandler;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/**
 * Displays the dice area with the current roll.
 */
public class DiceView {

    private final VBox root;
    private final Button rollButton;
    // Rolling animation: faces change at these gaps, each a little slower, then the real value
    // lands FINAL_GAP_MS after the last face (about 680 ms in all, inside the 1-second limit).
    private static final int[] FRAME_GAPS_MS = {0, 55, 60, 65, 75, 85, 100, 120};
    private static final int FINAL_GAP_MS = 120;
    private static final double MAX_TILT = 14;

    private final Label resultLabel;
    private final StackPane resultBox;
    private final Random animationFaces = new Random();
    private Timeline rolling; // the running animation, or null when the die is not rolling
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
        resultLabel.setAccessibleText("No roll yet");
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
        rollButton.setOnAction(action == null ? null : event -> {
            if (rolling == null) {
                action.run();
            }
        });
    }

    /**
     * Shows the result of the latest roll: a short rolling animation, then {@code value}, about
     * 0.7 seconds after the call. Presses of Roll during the animation are ignored. Screen readers
     * get only the final value.
     */
    public void showResult(int value) {
        showResult(value, null);
    }

    /**
     * Like {@link #showResult(int)}, then runs {@code afterShown} (if not {@code null}) once the
     * final value is on screen.
     */
    public void showResult(int value, Runnable afterShown) {
        if (rolling != null) {
            rolling.stop();
        }
        if (!resultBox.getStyleClass().contains("dice-rolling")) {
            resultBox.getStyleClass().add("dice-rolling");
        }
        Timeline animation = new Timeline();
        Duration time = Duration.ZERO;
        int face = 0;
        for (int frame = 0; frame < FRAME_GAPS_MS.length; frame++) {
            time = time.add(Duration.millis(FRAME_GAPS_MS[frame]));
            face = randomFaceOtherThan(face);
            String faceText = String.valueOf(face);
            double tilt = (frame % 2 == 0 ? 1 : -1) * MAX_TILT * (1 - (double) frame / FRAME_GAPS_MS.length);
            animation.getKeyFrames().add(new KeyFrame(time, event -> {
                resultLabel.setText(faceText);
                resultBox.setRotate(tilt);
            }));
        }
        animation.getKeyFrames().add(new KeyFrame(time.add(Duration.millis(FINAL_GAP_MS)), event -> {
            settle(value);
            if (afterShown != null) {
                afterShown.run();
            }
        }));
        rolling = animation;
        animation.play();
    }

    /**
     * Makes Roll available or unavailable (button and Space). When made available, keyboard focus
     * moves to the Roll button.
     */
    public void setRollEnabled(boolean enabled) {
        rollButton.setDisable(!enabled);
        if (enabled) {
            rollButton.requestFocus();
        }
    }

    private void settle(int value) {
        String text = String.valueOf(value);
        resultLabel.setText(text);
        resultLabel.setAccessibleText(text);
        resultBox.setAccessibleText("Die result: " + text);
        resultBox.setRotate(0);
        resultBox.getStyleClass().remove("dice-rolling");
        rolling = null;
    }

    /**
     * A face for the animation only, never the result: 1–6, different from the previous frame.
     */
    private int randomFaceOtherThan(int previous) {
        int face;
        do {
            face = animationFaces.nextInt(6) + 1;
        } while (face == previous);
        return face;
    }

    /**
     * While Roll is available, Space presses it wherever the focus is, and the scene-level filter
     * consumes the key so a focused Roll button is not also pressed by its own Space handling.
     * While Roll is unavailable, a fresh Space press reaches the focused control (e.g. a piece to
     * choose). Repeats of a held key never act, so one press is one action.
     */
    private void installSpaceShortcut() {
        EventHandler<KeyEvent> spaceFilter = event -> {
            if (event.getCode() != KeyCode.SPACE) {
                return;
            }
            if (event.getEventType() == KeyEvent.KEY_PRESSED) {
                boolean repeat = spaceHeld;
                spaceHeld = true;
                if (rollButton.isDisabled()) {
                    if (repeat) {
                        event.consume();
                    }
                    return;
                }
                if (!repeat) {
                    rollButton.fire();
                }
                event.consume();
            } else {
                spaceHeld = false;
                if (!rollButton.isDisabled()) {
                    event.consume();
                }
            }
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
