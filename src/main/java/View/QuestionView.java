package View;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.AccessibleRole;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;

/**
 * The question dialog: the player who landed on a question square picks one of four answers
 * (mouse, or keys 1–4), then sees the feedback the caller gives and presses Continue.
 * <p>
 * It opens as a modal window over the game screen, so nothing on the game screen (Roll, Space,
 * pieces, Main Menu) can be used while it is open, and it cannot be closed without answering.
 * The dialog never decides whether an answer is right: it reports the chosen answer number and
 * shows whatever feedback it is given.
 */
public class QuestionView {

    private static final int ANSWER_COUNT = 4;
    private static final double WIDTH = 560;
    private static final double ANSWER_TEXT_WIDTH = 300;

    private final VBox content = new VBox(12);
    private final Label playerLine = new Label();
    private final Label levelTag = new Label();
    private final Label questionText = new Label();
    private final List<HBox> answerRows = new ArrayList<>();
    private final List<Label> answerTexts = new ArrayList<>();
    private final List<Label> answerMarks = new ArrayList<>();
    private final VBox feedbackBox = new VBox(6);
    private final Label resultLine = new Label();
    private final Label correctLine = new Label();
    private final Label nextLine = new Label();
    private final Button continueButton = new Button("Continue");
    private IntConsumer onAnswerChosen;
    private Runnable onContinue;
    private Stage stage;
    private int chosen; // 1–4 once an answer is picked, 0 before
    private boolean feedbackShown;

    public QuestionView() {
        playerLine.getStyleClass().add("question-player");
        playerLine.setWrapText(true);
        playerLine.setMaxWidth(Double.MAX_VALUE);
        levelTag.getStyleClass().add("results-tag-outline");
        levelTag.setMinWidth(Region.USE_PREF_SIZE);
        HBox header = new HBox(12, playerLine, levelTag);
        header.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(playerLine, Priority.ALWAYS);

        questionText.getStyleClass().add("question-text");
        questionText.setWrapText(true);

        Label hint = new Label("Choose one answer: click it or press 1, 2, 3 or 4.");
        hint.getStyleClass().add("ludo-hint");
        hint.setWrapText(true);

        VBox answers = new VBox(8);
        for (int number = 1; number <= ANSWER_COUNT; number++) {
            answers.getChildren().add(buildAnswer(number));
        }

        resultLine.getStyleClass().add("question-result");
        correctLine.getStyleClass().add("question-feedback-text");
        correctLine.setWrapText(true);
        correctLine.managedProperty().bind(correctLine.visibleProperty());
        nextLine.getStyleClass().add("question-next");
        nextLine.setWrapText(true);
        continueButton.getStyleClass().addAll("ludo-button", "ludo-button-primary", "ludo-button-compact");
        continueButton.setDefaultButton(true);
        continueButton.setOnAction(event -> finish());
        HBox continueRow = new HBox(continueButton);
        continueRow.setAlignment(Pos.CENTER_RIGHT);
        feedbackBox.getChildren().addAll(resultLine, correctLine, nextLine, continueRow);
        feedbackBox.getStyleClass().add("question-feedback");
        feedbackBox.managedProperty().bind(feedbackBox.visibleProperty());

        content.getChildren().addAll(header, questionText, hint, answers, feedbackBox);
        content.getStyleClass().add("question-dialog");
        content.setMinWidth(WIDTH);
        content.setPrefWidth(WIDTH);
        content.setMaxWidth(WIDTH);
    }

    /**
     * Sets what happens when the player picks an answer: it receives the answer number, 1–4.
     * Called once per question; the answers are locked from then on.
     */
    public void setOnAnswerChosen(IntConsumer action) {
        onAnswerChosen = action;
    }

    /**
     * Sets what happens when Continue is pressed after the feedback; the dialog has already closed.
     */
    public void setOnContinue(Runnable action) {
        onContinue = action;
    }

    /**
     * Opens the dialog over the game screen with a new question; nothing is picked yet.
     *
     * @param gameScreen any node on the game screen, e.g. the board screen's root; its window
     *                   must be showing
     * @param playerName the player who must answer
     * @param level      the question level as a word: "Easy", "Medium" or "Hard"
     * @param question   the question text
     * @param answers    exactly four answers, shown as 1–4 in this order
     * @throws IllegalStateException    if the dialog is already open or the game screen is not showing
     * @throws IllegalArgumentException if a value is missing or there are not exactly four answers
     */
    public void show(Parent gameScreen, String playerName, String level, String question, List<String> answers) {
        if (stage != null) {
            throw new IllegalStateException("The question dialog is already open");
        }
        Window owner = gameScreen == null || gameScreen.getScene() == null ? null : gameScreen.getScene().getWindow();
        if (owner == null || !owner.isShowing()) {
            throw new IllegalStateException("The game screen must be showing");
        }
        requireText(playerName, "player name");
        requireText(level, "level");
        requireText(question, "question");
        if (answers == null || answers.size() != ANSWER_COUNT) {
            throw new IllegalArgumentException("A question needs exactly " + ANSWER_COUNT + " answers");
        }
        answers.forEach(answer -> requireText(answer, "answer"));

        playerLine.setText(playerName + ", answer this question");
        levelTag.setText("Level: " + level);
        questionText.setText(question);
        chosen = 0;
        feedbackShown = false;
        for (int i = 0; i < ANSWER_COUNT; i++) {
            answerTexts.get(i).setText(answers.get(i));
            answerRows.get(i).setAccessibleText("Answer " + (i + 1) + ": " + answers.get(i));
            answerRows.get(i).getStyleClass().removeAll("question-answer-chosen", "question-answer-locked");
            answerMarks.get(i).setText("");
            answerMarks.get(i).setVisible(false);
        }
        feedbackBox.setVisible(false);
        content.setAccessibleText(playerName + ", " + level + " question: " + question);

        stage = new Stage(StageStyle.UNDECORATED);
        stage.initOwner(owner);
        stage.initModality(Modality.WINDOW_MODAL);
        stage.setTitle(level + " question");
        // Closing is refused until the player has answered and pressed Continue
        stage.setOnCloseRequest(event -> event.consume());
        Scene scene = new Scene(content);
        scene.getStylesheets().addAll(owner.getScene().getStylesheets());
        scene.addEventFilter(KeyEvent.ANY, this::onKey);
        stage.setScene(scene);
        stage.setOnShown(event -> placeOver(owner));
        stage.show();
    }

    /**
     * Shows the outcome of the picked answer and offers Continue. The answers stay locked.
     *
     * @param correct             whether the picked answer was right
     * @param correctAnswerNumber the right answer, 1–4; shown in words when the pick was wrong
     * @param nextStep            what happens next, e.g. "Move forward 2 squares"
     * @throws IllegalStateException    if no answer has been picked yet, or feedback is already shown
     * @throws IllegalArgumentException if {@code correctAnswerNumber} is not 1–4, differs from the
     *                                  pick when {@code correct}, equals the pick when not
     *                                  {@code correct}, or {@code nextStep} is missing
     */
    public void showFeedback(boolean correct, int correctAnswerNumber, String nextStep) {
        if (stage == null || chosen == 0 || feedbackShown) {
            throw new IllegalStateException("Feedback is shown once, after an answer is picked");
        }
        if (correctAnswerNumber < 1 || correctAnswerNumber > ANSWER_COUNT || correct != (correctAnswerNumber == chosen)) {
            throw new IllegalArgumentException(
                    "The correct answer must be 1–4: the picked one if it was right, another one if it was wrong");
        }
        requireText(nextStep, "next step");
        feedbackShown = true;

        Label chosenMark = answerMarks.get(chosen - 1);
        if (correct) {
            resultLine.setText("✔ Correct!");
            chosenMark.setText("✔ Your answer: correct");
            correctLine.setVisible(false);
        } else {
            resultLine.setText("✘ Wrong answer");
            chosenMark.setText("✘ Your answer: wrong");
            Label correctMark = answerMarks.get(correctAnswerNumber - 1);
            correctMark.setText("✔ Correct answer");
            correctMark.setVisible(true);
            correctLine.setText("The correct answer is " + correctAnswerNumber + ": "
                    + answerTexts.get(correctAnswerNumber - 1).getText() + ".");
            correctLine.setVisible(true);
        }
        chosenMark.setVisible(true);
        nextLine.setText(nextStep);
        feedbackBox.setVisible(true);
        feedbackBox.setAccessibleText(resultLine.getText() + ". "
                + (correct ? "" : correctLine.getText() + " ") + nextStep);
        resize();
        continueButton.requestFocus();
    }

    /** Whether the dialog is open. */
    public boolean isShowing() {
        return stage != null;
    }

    /**
     * Closes the dialog without calling the Continue action, e.g. when the game screen is left.
     */
    public void close() {
        if (stage != null) {
            Stage closing = stage;
            stage = null;
            closing.hide();
            // Frees the content for the next question's window
            closing.getScene().setRoot(new Region());
        }
    }

    /** One answer: its number, its text and, once picked, a mark; the whole row is clickable. */
    private HBox buildAnswer(int number) {
        Label numberLabel = new Label(String.valueOf(number));
        numberLabel.getStyleClass().add("question-answer-number");
        numberLabel.setMinWidth(Region.USE_PREF_SIZE);
        Label text = new Label();
        text.getStyleClass().add("question-answer-text");
        text.setWrapText(true);
        // A definite width, so a wrapping answer's height is computed for the width it really gets
        text.setPrefWidth(ANSWER_TEXT_WIDTH);
        text.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(text, Priority.ALWAYS);
        Label mark = new Label();
        mark.getStyleClass().add("question-answer-mark");
        mark.setMinWidth(Region.USE_PREF_SIZE);
        mark.managedProperty().bind(mark.visibleProperty());
        HBox row = new HBox(12, numberLabel, text, mark);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().addAll("ludo-button", "question-answer");
        row.setAccessibleRole(AccessibleRole.BUTTON);
        row.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY) {
                choose(number);
            }
        });
        answerRows.add(row);
        answerTexts.add(text);
        answerMarks.add(mark);
        return row;
    }

    private void onKey(KeyEvent event) {
        KeyCode code = event.getCode();
        if (event.getEventType() == KeyEvent.KEY_PRESSED) {
            int number = switch (code) {
                case DIGIT1, NUMPAD1 -> 1;
                case DIGIT2, NUMPAD2 -> 2;
                case DIGIT3, NUMPAD3 -> 3;
                case DIGIT4, NUMPAD4 -> 4;
                default -> 0;
            };
            if (number != 0) {
                choose(number);
                event.consume();
                return;
            }
        }
        if (code == KeyCode.ESCAPE) {
            // There is nothing to cancel: the question must be answered
            event.consume();
        } else if (!feedbackShown && (code == KeyCode.SPACE || code == KeyCode.ENTER)) {
            // A Space press meant for Roll must not act here; until the feedback, only 1–4 answer
            event.consume();
        }
    }

    /** The first pick counts; later clicks and keys do nothing. */
    private void choose(int number) {
        if (stage == null || chosen != 0) {
            return;
        }
        chosen = number;
        for (int i = 0; i < ANSWER_COUNT; i++) {
            HBox row = answerRows.get(i);
            row.getStyleClass().add("question-answer-locked");
            if (i == number - 1) {
                row.getStyleClass().add("question-answer-chosen");
            }
        }
        Label mark = answerMarks.get(number - 1);
        mark.setText("▶ Your answer");
        mark.setVisible(true);
        resize();
        if (onAnswerChosen != null) {
            onAnswerChosen.accept(number);
        }
    }

    private void finish() {
        if (!feedbackShown) {
            return;
        }
        close();
        if (onContinue != null) {
            onContinue.run();
        }
    }

    private void resize() {
        stage.sizeToScene();
        placeOver(stage.getOwner());
    }

    /** Centred over the game window, and kept inside the screen that window is on. */
    private void placeOver(Window owner) {
        Rectangle2D area = Screen.getScreensForRectangle(owner.getX(), owner.getY(), owner.getWidth(), owner.getHeight())
                .stream().findFirst().orElse(Screen.getPrimary()).getVisualBounds();
        double x = owner.getX() + (owner.getWidth() - stage.getWidth()) / 2;
        double y = owner.getY() + (owner.getHeight() - stage.getHeight()) / 2;
        stage.setX(Math.max(area.getMinX(), Math.min(x, area.getMaxX() - stage.getWidth())));
        stage.setY(Math.max(area.getMinY(), Math.min(y, area.getMaxY() - stage.getHeight())));
    }

    private static void requireText(String value, String what) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("The " + what + " must not be empty");
        }
    }
}
