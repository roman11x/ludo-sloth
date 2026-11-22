package View;

import java.util.Random;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.event.EventHandler;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/**
 * Displays the dice area with the current roll.
 * <p>
 * Easy shows one Standard die. Medium shows the Standard die (1–6) and the Boost die (1–3) with
 * the move they give; during a surprise turn the Boost die is replaced by the UltraBoost die (1–6).
 * The controller chooses the mode with {@link #setEasyMode()}, {@link #setMediumMode()} and
 * {@link #setUltraBoostTurn()}; the area starts in Easy mode.
 */
public class DiceView {

    private enum Mode { EASY, MEDIUM, ULTRABOOST }

    private final VBox root;
    private final Button rollButton;
    // Rolling animation: faces change at these gaps, each a little slower, then the real value
    // lands FINAL_GAP_MS after the last face (about 680 ms in all, inside the 1-second limit).
    private static final int[] FRAME_GAPS_MS = {0, 55, 60, 65, 75, 85, 100, 120};
    private static final int FINAL_GAP_MS = 120;
    private static final double MAX_TILT = 14;
    private static final int BOOST_FACES = 3;
    private static final int STANDARD_FACES = 6;

    private final Label available;
    private final Label surpriseTag;
    private final Label standardName;
    private final Label resultLabel;
    private final StackPane resultBox;
    private final VBox specialColumn;
    private final Label specialName;
    private final Label specialLabel;
    private final StackPane specialBox;
    private final Label moveLabel;
    private final Label reasonLabel;
    private final Random animationFaces = new Random();
    private Timeline rolling; // the running animation, or null when the die is not rolling
    private boolean spaceHeld;
    private Mode mode = Mode.EASY;
    private boolean rollEnabled = true;
    private boolean effectResolving;

    public DiceView() {
        // Heading and the dice available at the current level
        Label heading = new Label("Dice");
        heading.getStyleClass().add("ludo-section-label");
        available = new Label();
        available.getStyleClass().add("ludo-summary-text");
        available.setWrapText(true);

        // Shown only during a surprise turn, so the UltraBoost die never relies on its look alone
        surpriseTag = new Label("SURPRISE TURN: UltraBoost");
        surpriseTag.getStyleClass().add("ludo-tag");
        hideable(surpriseTag);

        // Result space, empty until the first roll
        Label resultCaption = new Label("Result");
        resultCaption.getStyleClass().add("ludo-hint");
        standardName = new Label("Standard");
        standardName.getStyleClass().add("dice-name");
        hideable(standardName);
        resultLabel = new Label();
        resultLabel.getStyleClass().add("dice-result-value");
        resultLabel.setAccessibleText("No roll yet");
        resultBox = new StackPane(resultLabel);
        resultBox.getStyleClass().add("dice-result");
        resultBox.setAccessibleText("Die result: no roll yet");
        VBox standardColumn = new VBox(4, standardName, resultBox);
        standardColumn.setAlignment(Pos.TOP_CENTER);

        // The second die of a Medium roll: Boost, or UltraBoost during a surprise turn
        specialName = new Label();
        specialName.getStyleClass().add("dice-name");
        specialLabel = new Label();
        specialLabel.getStyleClass().add("dice-result-value");
        specialBox = new StackPane(specialLabel);
        specialBox.getStyleClass().add("dice-result");
        specialColumn = new VBox(4, specialName, specialBox);
        specialColumn.setAlignment(Pos.TOP_CENTER);
        hideable(specialColumn);

        HBox diceRow = new HBox(16, standardColumn, specialColumn);
        diceRow.setAlignment(Pos.TOP_CENTER);

        // The move a Medium roll gives, and why it is 0 when the Boost die shows 1
        moveLabel = new Label();
        moveLabel.getStyleClass().add("dice-move");
        moveLabel.setWrapText(true);
        hideable(moveLabel);
        reasonLabel = new Label();
        reasonLabel.getStyleClass().add("dice-move-reason");
        reasonLabel.setWrapText(true);
        hideable(reasonLabel);

        // Primary action of the turn, same look as the other primary buttons
        rollButton = new Button("Roll (Space)");
        rollButton.getStyleClass().addAll("ludo-button", "ludo-button-primary", "ludo-button-compact");
        installSpaceShortcut();

        root = new VBox(10, heading, available, surpriseTag, resultCaption, diceRow, moveLabel, reasonLabel, rollButton);
        root.setAlignment(Pos.TOP_CENTER);
        root.getStyleClass().add("ludo-panel");
        applyMode(Mode.EASY);
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
     * Easy: one Standard die (1–6). The dice are cleared.
     *
     * @throws IllegalStateException while a roll is animating
     */
    public void setEasyMode() {
        applyMode(Mode.EASY);
    }

    /**
     * Medium: the Standard die (1–6) and the Boost die (1–3). Also ends a surprise turn's
     * UltraBoost mode. The dice are cleared.
     *
     * @throws IllegalStateException while a roll is animating
     */
    public void setMediumMode() {
        applyMode(Mode.MEDIUM);
    }

    /**
     * A surprise turn: the Boost die is replaced by the UltraBoost die (1–6) and a "SURPRISE TURN"
     * label is shown, until {@link #setMediumMode()} is called after that turn. The dice are cleared.
     *
     * @throws IllegalStateException while a roll is animating
     */
    public void setUltraBoostTurn() {
        applyMode(Mode.ULTRABOOST);
    }

    /**
     * Shows the result of the latest Easy roll: a short rolling animation, then {@code value},
     * about 0.7 seconds after the call. Presses of Roll during the animation are ignored. Screen
     * readers get only the final value.
     *
     * @throws IllegalStateException if the area is not in Easy mode
     */
    public void showResult(int value) {
        showResult(value, null);
    }

    /**
     * Like {@link #showResult(int)}, then runs {@code afterShown} (if not {@code null}) once the
     * final value is on screen.
     */
    public void showResult(int value, Runnable afterShown) {
        if (mode != Mode.EASY) {
            throw new IllegalStateException("A Medium roll is shown with showMediumResult");
        }
        animate(value, 0, afterShown);
    }

    /**
     * Shows the result of the latest Medium roll, both dice rolling together and landing about
     * 0.7 seconds after the call: the Standard die, the Boost die (or UltraBoost die during a
     * surprise turn) and the move, e.g. "6 × (3 − 1) = 12 steps". A move of 0 is shown with its
     * reason.
     *
     * @param standard the Standard die, 1–6
     * @param special  the Boost die (1–3), or the UltraBoost die (1–6) during a surprise turn
     * @param steps    the move the game decided; shown as given
     * @throws IllegalStateException    if the area is in Easy mode
     * @throws IllegalArgumentException if a die is out of range
     */
    public void showMediumResult(int standard, int special, int steps) {
        showMediumResult(standard, special, steps, null);
    }

    /**
     * Like {@link #showMediumResult(int, int, int)}, then runs {@code afterShown} (if not
     * {@code null}) once the final values are on screen.
     */
    public void showMediumResult(int standard, int special, int steps, Runnable afterShown) {
        if (mode == Mode.EASY) {
            throw new IllegalStateException("An Easy roll is shown with showResult");
        }
        int specialFaces = specialFaces();
        if (standard < 1 || standard > STANDARD_FACES || special < 1 || special > specialFaces) {
            throw new IllegalArgumentException("Dice must be Standard 1–" + STANDARD_FACES + " and "
                    + specialName() + " 1–" + specialFaces);
        }
        animate(standard, special, () -> {
            showMove(standard, special, steps);
            if (afterShown != null) {
                afterShown.run();
            }
        });
    }

    /**
     * Makes Roll available or unavailable (button and Space). When made available, keyboard focus
     * moves to the Roll button. Roll stays unavailable while an effect is being resolved.
     */
    public void setRollEnabled(boolean enabled) {
        rollEnabled = enabled;
        updateRollButton();
    }

    /**
     * While {@code true}, the game is resolving an effect (e.g. a question or a surprise) and Roll
     * is unavailable whatever {@link #setRollEnabled(boolean)} says. When it ends, Roll returns to
     * what {@link #setRollEnabled(boolean)} last set.
     */
    public void setEffectResolving(boolean resolving) {
        effectResolving = resolving;
        updateRollButton();
    }

    /** Cancels pending visual callbacks when the game screen is left. */
    public void stopAnimation() {
        if (rolling != null) {
            rolling.stop();
            rolling = null;
        }
        for (StackPane box : new StackPane[]{resultBox, specialBox}) {
            box.setRotate(0);
            box.getStyleClass().remove("dice-rolling");
        }
        spaceHeld = false;
    }

    private void updateRollButton() {
        boolean available = rollEnabled && !effectResolving;
        rollButton.setDisable(!available);
        if (available) {
            rollButton.requestFocus();
        }
    }

    private void applyMode(Mode newMode) {
        if (rolling != null) {
            throw new IllegalStateException("The dice mode cannot change while a roll is animating");
        }
        mode = newMode;
        boolean medium = newMode != Mode.EASY;
        boolean ultraBoost = newMode == Mode.ULTRABOOST;
        available.setText(medium
                ? "Standard die 1–6, " + specialName() + " die 1–" + specialFaces()
                : "Standard die, 1–6");
        surpriseTag.setVisible(ultraBoost);
        standardName.setVisible(medium);
        specialColumn.setVisible(medium);
        specialName.setText(specialName());
        if (ultraBoost) {
            if (!specialBox.getStyleClass().contains("dice-ultraboost")) {
                specialBox.getStyleClass().add("dice-ultraboost");
            }
        } else {
            specialBox.getStyleClass().remove("dice-ultraboost");
        }

        resultLabel.setText("");
        resultLabel.setAccessibleText("No roll yet");
        resultBox.setAccessibleText((medium ? "Standard die" : "Die") + " result: no roll yet");
        specialLabel.setText("");
        specialLabel.setAccessibleText("No roll yet");
        specialBox.setAccessibleText(specialName() + " die result: no roll yet");
        clearMove();
    }

    private String specialName() {
        return mode == Mode.ULTRABOOST ? "UltraBoost" : "Boost";
    }

    private int specialFaces() {
        return mode == Mode.ULTRABOOST ? STANDARD_FACES : BOOST_FACES;
    }

    /** Rolls the Standard die, and in Medium the second die alongside it; {@code special} is ignored in Easy. */
    private void animate(int standard, int special, Runnable afterShown) {
        if (rolling != null) {
            rolling.stop();
        }
        boolean twoDice = mode != Mode.EASY;
        clearMove();
        markRolling(resultBox);
        if (twoDice) {
            markRolling(specialBox);
        }
        Timeline animation = new Timeline();
        Duration time = Duration.ZERO;
        int face = 0;
        int specialFace = 0;
        for (int frame = 0; frame < FRAME_GAPS_MS.length; frame++) {
            time = time.add(Duration.millis(FRAME_GAPS_MS[frame]));
            face = randomFaceOtherThan(face, STANDARD_FACES);
            specialFace = randomFaceOtherThan(specialFace, specialFaces());
            String faceText = String.valueOf(face);
            String specialText = String.valueOf(specialFace);
            double tilt = (frame % 2 == 0 ? 1 : -1) * MAX_TILT * (1 - (double) frame / FRAME_GAPS_MS.length);
            animation.getKeyFrames().add(new KeyFrame(time, event -> {
                resultLabel.setText(faceText);
                resultBox.setRotate(tilt);
                if (twoDice) {
                    specialLabel.setText(specialText);
                    specialBox.setRotate(-tilt);
                }
            }));
        }
        animation.getKeyFrames().add(new KeyFrame(time.add(Duration.millis(FINAL_GAP_MS)), event -> {
            settle(standard, twoDice ? special : 0);
            if (afterShown != null) {
                afterShown.run();
            }
        }));
        rolling = animation;
        animation.play();
    }

    private static void markRolling(StackPane box) {
        if (!box.getStyleClass().contains("dice-rolling")) {
            box.getStyleClass().add("dice-rolling");
        }
    }

    private void settle(int value, int special) {
        String text = String.valueOf(value);
        resultLabel.setText(text);
        resultLabel.setAccessibleText(text);
        resultBox.setAccessibleText((special == 0 ? "Die" : "Standard die") + " result: " + text);
        resultBox.setRotate(0);
        resultBox.getStyleClass().remove("dice-rolling");
        if (special != 0) {
            String specialText = String.valueOf(special);
            specialLabel.setText(specialText);
            specialLabel.setAccessibleText(specialText);
            specialBox.setAccessibleText(specialName() + " die result: " + specialText);
            specialBox.setRotate(0);
            specialBox.getStyleClass().remove("dice-rolling");
        }
        rolling = null;
    }

    private void showMove(int standard, int special, int steps) {
        moveLabel.setText(standard + " × (" + special + " − 1) = " + steps + (steps == 1 ? " step" : " steps"));
        moveLabel.setVisible(true);
        if (steps == 0) {
            reasonLabel.setText(specialName() + " rolled 1, so this roll gives no move.");
            reasonLabel.setVisible(true);
        }
    }

    private void clearMove() {
        moveLabel.setText("");
        moveLabel.setVisible(false);
        reasonLabel.setText("");
        reasonLabel.setVisible(false);
    }

    /** Hidden nodes take no space, so Easy mode lays out exactly as a single-die area. */
    private static void hideable(Node node) {
        node.managedProperty().bind(node.visibleProperty());
    }

    /**
     * A face for the animation only, never the result: 1 to {@code faces}, different from the
     * previous frame.
     */
    private int randomFaceOtherThan(int previous, int faces) {
        int face;
        do {
            face = animationFaces.nextInt(faces) + 1;
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
