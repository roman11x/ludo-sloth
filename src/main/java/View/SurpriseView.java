package View;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Set;
import java.util.function.ObjIntConsumer;

import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.stage.Modality;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import javafx.stage.WindowEvent;

/**
 * The surprise screen: after a piece lands on a surprise square, the player sees which of the six
 * events happened and, for "Opponent moves back", picks an opponent and one of their pieces.
 * <p>
 * It opens as a modal window over the game screen, so nothing on the game screen can be used
 * while it is open, and it cannot be dismissed back to the game without finishing; closing it
 * closes the game window, under that window's usual rules. Every event ends with Continue, which
 * closes the screen and then tells the caller. The screen decides nothing: the caller passes the
 * names, colours, eligible opponents and pieces, the roll and the resulting move as plain values.
 * <p>
 * Colours are the words "Red", "Green", "Yellow" or "Blue" (any case) and are always shown as a
 * word next to their circle. Choices can also be made by key: an opponent with the number shown
 * in front of it, a piece with its own piece number.
 */
public class SurpriseView {

    private static final double WIDTH = 520;
    private static final double SCREEN_MARGIN = 12;
    private static final Set<String> COLOURS = Set.of("red", "green", "yellow", "blue");
    private static final int MAX_OPPONENTS = 3;
    private static final int MAX_PIECE = 4;

    private final VBox content = new VBox(14);
    private final Label playerLine = new Label();
    private final Label title = new Label();
    private final VBox body = new VBox(10);
    private final Label status = new Label();
    private final Button continueButton = new Button("Continue");
    private final Map<Integer, Button> numberedOptions = new HashMap<>(); // number key -> choice
    private Stage stage;
    private Runnable continueAction;

    public SurpriseView() {
        Label tag = new Label("SURPRISE");
        tag.getStyleClass().add("ludo-tag");
        tag.setMinWidth(Region.USE_PREF_SIZE);
        playerLine.getStyleClass().add("surprise-player");
        playerLine.setWrapText(true);
        HBox header = new HBox(12, tag, playerLine);
        header.setAlignment(Pos.CENTER_LEFT);

        title.getStyleClass().add("surprise-title");
        title.setWrapText(true);
        status.getStyleClass().add("surprise-status");
        status.setWrapText(true);
        status.setMaxWidth(Double.MAX_VALUE);
        status.managedProperty().bind(status.visibleProperty());

        continueButton.getStyleClass().addAll("ludo-button", "ludo-button-primary", "ludo-button-compact");
        continueButton.setDefaultButton(true);
        continueButton.managedProperty().bind(continueButton.visibleProperty());
        continueButton.setOnAction(event -> finish());
        HBox continueRow = new HBox(continueButton);
        continueRow.setAlignment(Pos.CENTER_RIGHT);

        content.getChildren().addAll(header, title, body, status, continueRow);
        content.getStyleClass().add("surprise-dialog");
        content.setMinWidth(WIDTH);
        content.setPrefWidth(WIDTH);
        content.setMaxWidth(WIDTH);
    }

    /**
     * Event 1, Straight to Finish: names the piece that goes to Finish.
     *
     * @param onContinue called after Continue has closed the screen
     */
    public void showStraightToFinish(Parent gameScreen, String playerName, String pieceColour, int pieceNumber,
                                     Runnable onContinue) {
        String piece = pieceName(pieceColour, pieceNumber);
        announce(gameScreen, playerName, "Straight to Finish",
                pieceLine(pieceColour, playerName + "'s " + piece + " goes straight to Finish."), onContinue);
    }

    /**
     * Event 3, Back to Castle: names the piece that goes back to its Castle.
     *
     * @param onContinue called after Continue has closed the screen
     */
    public void showBackToCastle(Parent gameScreen, String playerName, String pieceColour, int pieceNumber,
                                 Runnable onContinue) {
        String piece = pieceName(pieceColour, pieceNumber);
        announce(gameScreen, playerName, "Back to Castle",
                pieceLine(pieceColour, playerName + "'s " + piece + " goes back to the Castle."), onContinue);
    }

    /**
     * Event 4, Back 3 squares: names the piece that moves back three squares.
     *
     * @param onContinue called after Continue has closed the screen
     */
    public void showBackThreeSquares(Parent gameScreen, String playerName, String pieceColour, int pieceNumber,
                                     Runnable onContinue) {
        String piece = pieceName(pieceColour, pieceNumber);
        announce(gameScreen, playerName, "Back 3 squares",
                pieceLine(pieceColour, playerName + "'s " + piece + " moves back 3 squares."), onContinue);
    }

    /**
     * Event 5, Lose next turn: names the player whose next turn is skipped.
     *
     * @param onContinue called after Continue has closed the screen
     */
    public void showLoseNextTurn(Parent gameScreen, String playerName, Runnable onContinue) {
        requireText(playerName, "player name");
        announce(gameScreen, playerName, "Lose next turn",
                text(playerName + " loses their next turn: it will be skipped."), onContinue);
    }

    /**
     * Event 6, Extra turn with UltraBoost: the player rolls again now, with the Standard die and
     * the UltraBoost die.
     *
     * @param onContinue called after Continue has closed the screen
     */
    public void showExtraTurnWithUltraBoost(Parent gameScreen, String playerName, Runnable onContinue) {
        requireText(playerName, "player name");
        Label dice = new Label("Standard die 1–6 + UltraBoost die 1–6");
        dice.getStyleClass().add("results-tag-outline");
        announce(gameScreen, playerName, "Extra turn with UltraBoost",
                new VBox(8, text(playerName + " rolls again now, with the Standard die and the UltraBoost die."), dice),
                onContinue);
    }

    /**
     * Event 2, Opponent moves back. The player picks an opponent, then sees the Standard die roll,
     * then picks one of that opponent's eligible pieces, which moves back {@code stepsBack} squares.
     * Opponents not marked eligible cannot be picked. If the picked opponent has no eligible piece,
     * the player may pick another opponent; the roll stays the same. If no opponent is eligible, or
     * every eligible opponent turns out to have no eligible piece, the event is cancelled.
     *
     * @param opponentNames     the opponents, in the order shown
     * @param opponentColours   each opponent's colour word, same order
     * @param opponentEligible  whether each opponent may be picked, same order
     * @param eligiblePieces    for each opponent, the numbers (1–4) of the pieces that may move back
     *                          with this roll; may be empty
     * @param roll              the Standard die roll, 1–6
     * @param stepsBack         how far the picked piece moves back, as the game decided it
     * @param onPieceChosen     after Continue: the picked opponent's name and piece number
     * @param onCancelled       after Continue, if the event was cancelled
     * @throws IllegalArgumentException if the lists differ in size, there are not 1–3 opponents,
     *                                  or a value is missing or out of range
     */
    public void showOpponentMovesBack(Parent gameScreen, String playerName, List<String> opponentNames,
                                      List<String> opponentColours, List<Boolean> opponentEligible,
                                      List<List<Integer>> eligiblePieces, int roll, int stepsBack,
                                      ObjIntConsumer<String> onPieceChosen, Runnable onCancelled) {
        requireText(playerName, "player name");
        int count = opponentNames == null ? 0 : opponentNames.size();
        if (count < 1 || count > MAX_OPPONENTS || opponentColours == null || opponentColours.size() != count
                || opponentEligible == null || opponentEligible.size() != count
                || eligiblePieces == null || eligiblePieces.size() != count) {
            throw new IllegalArgumentException("Need 1–" + MAX_OPPONENTS + " opponents, each with a colour, "
                    + "an eligibility flag and a list of eligible pieces");
        }
        for (int i = 0; i < count; i++) {
            requireText(opponentNames.get(i), "opponent name");
            requireColour(opponentColours.get(i));
            if (opponentEligible.get(i) == null || eligiblePieces.get(i) == null) {
                throw new IllegalArgumentException("Opponent " + (i + 1) + " needs an eligibility flag and a piece list");
            }
            for (Integer piece : eligiblePieces.get(i)) {
                if (piece == null || piece < 1 || piece > MAX_PIECE) {
                    throw new IllegalArgumentException("Piece numbers must be 1–" + MAX_PIECE);
                }
            }
        }
        if (roll < 1 || roll > 6) {
            throw new IllegalArgumentException("The Standard die roll must be 1–6");
        }
        if (stepsBack < 0) {
            throw new IllegalArgumentException("Steps back must not be negative");
        }

        new OpponentChoice(playerName, List.copyOf(opponentNames), List.copyOf(opponentColours),
                List.copyOf(opponentEligible), eligiblePieces.stream().map(List::copyOf).toList(),
                roll, stepsBack, onPieceChosen, onCancelled).start(gameScreen);
    }

    /** Whether the screen is open. */
    public boolean isShowing() {
        return stage != null;
    }

    /** Closes the screen without calling the Continue action, e.g. when the game screen is left. */
    public void close() {
        if (stage != null) {
            Stage closing = stage;
            stage = null;
            closing.hide();
            // Frees the content for the next surprise's window
            closing.getScene().setRoot(new Region());
        }
    }

    /** The flow of event 2; one instance per surprise. */
    private final class OpponentChoice {
        private final String playerName;
        private final List<String> names;
        private final List<String> colours;
        private final List<Boolean> eligible;
        private final List<List<Integer>> pieces;
        private final int roll;
        private final int stepsBack;
        private final ObjIntConsumer<String> onPieceChosen;
        private final Runnable onCancelled;
        private final List<Button> opponentButtons = new ArrayList<>();
        private final boolean[] tried;
        private final VBox rollBox = new VBox();
        private final VBox pieceBox = new VBox(8);

        OpponentChoice(String playerName, List<String> names, List<String> colours, List<Boolean> eligible,
                       List<List<Integer>> pieces, int roll, int stepsBack,
                       ObjIntConsumer<String> onPieceChosen, Runnable onCancelled) {
            this.playerName = playerName;
            this.names = names;
            this.colours = colours;
            this.eligible = eligible;
            this.pieces = pieces;
            this.roll = roll;
            this.stepsBack = stepsBack;
            this.onPieceChosen = onPieceChosen;
            this.onCancelled = onCancelled;
            this.tried = new boolean[names.size()];
        }

        void start(Parent gameScreen) {
            Label intro = text(playerName + ", choose an opponent. A Standard die roll then decides how far "
                    + "one of their pieces moves back.");
            VBox opponents = new VBox(8);
            for (int i = 0; i < names.size(); i++) {
                int index = i;
                Button button = optionButton(i + 1, colours.get(i), names.get(i) + " · " + colourWord(colours.get(i)));
                button.setOnAction(event -> chooseOpponent(index));
                if (!eligible.get(i)) {
                    button.setText(button.getText() + " (cannot be chosen)");
                    button.setDisable(true);
                }
                opponentButtons.add(button);
                opponents.getChildren().add(button);
            }
            rollBox.managedProperty().bind(rollBox.visibleProperty());
            rollBox.setVisible(false);
            pieceBox.managedProperty().bind(pieceBox.visibleProperty());
            pieceBox.setVisible(false);
            open(gameScreen, playerName, "Opponent moves back", new VBox(10, intro, opponents, rollBox, pieceBox));
            Map<Integer, Button> keys = new HashMap<>();
            for (int i = 0; i < opponentButtons.size(); i++) {
                keys.put(i + 1, opponentButtons.get(i));
            }
            setNumberedOptions(keys);
            if (eligible.stream().noneMatch(Boolean::booleanValue)) {
                cancel("No opponent can be chosen, so this surprise is cancelled.");
            }
        }

        private void chooseOpponent(int index) {
            if (!eligible.get(index) || tried[index] || stage == null) {
                return;
            }
            tried[index] = true;
            showRoll();
            String name = names.get(index);
            if (pieces.get(index).isEmpty()) {
                Button button = opponentButtons.get(index);
                button.setText(button.getText() + " (no piece can move back " + squares(stepsBack) + ")");
                button.setDisable(true);
                boolean anotherLeft = false;
                for (int i = 0; i < names.size(); i++) {
                    anotherLeft |= eligible.get(i) && !tried[i];
                }
                if (anotherLeft) {
                    showStatus(name + " has no piece that can move back " + squares(stepsBack)
                            + ". Choose another opponent; the roll stays " + roll + ".");
                } else {
                    cancel(name + " has no piece that can move back " + squares(stepsBack)
                            + ", and no other opponent can be chosen, so this surprise is cancelled.");
                }
                resize();
                return;
            }

            // The opponent is settled: lock the list and offer their eligible pieces
            for (int i = 0; i < opponentButtons.size(); i++) {
                opponentButtons.get(i).setDisable(true);
            }
            opponentButtons.get(index).setText(opponentButtons.get(index).getText() + " (chosen)");
            opponentButtons.get(index).getStyleClass().add("surprise-option-chosen");
            status.setVisible(false);
            String colour = colours.get(index);
            // Pieces are picked by their own number (key 3 picks piece 3), so no extra key number is shown
            List<Button> pieceButtons = new ArrayList<>();
            Map<Integer, Button> keys = new HashMap<>();
            for (int piece : pieces.get(index)) {
                Button button = optionButton(0, colour, pieceName(colour, piece));
                button.setOnAction(event -> choosePiece(index, piece, button, pieceButtons));
                pieceButtons.add(button);
                keys.put(piece, button);
            }
            pieceBox.getChildren().setAll(text("Choose one of " + name + "'s pieces to move back "
                    + squares(stepsBack) + " (click it or press its number):"));
            pieceBox.getChildren().addAll(pieceButtons);
            pieceBox.setVisible(true);
            setNumberedOptions(keys);
            resize();
        }

        private void choosePiece(int opponent, int piece, Button chosen, List<Button> pieceButtons) {
            if (stage == null || continueButton.isVisible()) {
                return;
            }
            pieceButtons.forEach(button -> button.setDisable(true));
            chosen.setText(chosen.getText() + " (chosen)");
            chosen.getStyleClass().add("surprise-option-chosen");
            String name = names.get(opponent);
            showStatus(name + "'s " + pieceName(colours.get(opponent), piece) + " moves back " + squares(stepsBack) + ".");
            setNumberedOptions(Map.of());
            offerContinue(() -> {
                if (onPieceChosen != null) {
                    onPieceChosen.accept(name, piece);
                }
            });
        }

        private void showRoll() {
            if (rollBox.isVisible()) {
                return;
            }
            Label value = new Label(String.valueOf(roll));
            value.getStyleClass().add("dice-result-value");
            StackPane die = new StackPane(value);
            die.getStyleClass().add("dice-result");
            die.setAccessibleText("Standard die: " + roll);
            Label rolled = new Label("Standard die roll: " + roll);
            rolled.getStyleClass().add("surprise-roll");
            Label move = text("One piece moves back " + squares(stepsBack) + " (half the roll, rounded up).");
            VBox words = new VBox(4, rolled, move);
            HBox.setHgrow(words, Priority.ALWAYS);
            HBox row = new HBox(14, die, words);
            row.setAlignment(Pos.CENTER_LEFT);
            rollBox.getChildren().setAll(row);
            rollBox.setVisible(true);
        }

        private void cancel(String reason) {
            opponentButtons.forEach(button -> button.setDisable(true));
            setNumberedOptions(Map.of());
            showStatus(reason);
            offerContinue(onCancelled);
        }
    }

    private void announce(Parent gameScreen, String playerName, String eventTitle, Node message, Runnable onContinue) {
        open(gameScreen, playerName, eventTitle, message);
        offerContinue(onContinue);
    }

    /** Opens the window with an event's title and body; Continue stays hidden until the event is finished. */
    private void open(Parent gameScreen, String playerName, String eventTitle, Node eventBody) {
        if (stage != null) {
            throw new IllegalStateException("The surprise screen is already open");
        }
        Window owner = gameScreen == null || gameScreen.getScene() == null ? null : gameScreen.getScene().getWindow();
        if (owner == null || !owner.isShowing()) {
            throw new IllegalStateException("The game screen must be showing");
        }
        requireText(playerName, "player name");
        playerLine.setText(playerName + " landed on a surprise square");
        title.setText(eventTitle);
        body.getChildren().setAll(eventBody);
        status.setText("");
        status.setVisible(false);
        continueButton.setVisible(false);
        continueButton.setDisable(true);
        continueAction = null;
        setNumberedOptions(Map.of());
        content.setAccessibleText("Surprise: " + eventTitle);

        stage = new Stage(StageStyle.UNDECORATED);
        stage.initOwner(owner);
        stage.initModality(Modality.WINDOW_MODAL);
        stage.setTitle("Surprise: " + eventTitle);
        // The screen itself is never dismissed without finishing. Closing it (Alt+F4) means
        // quitting the game, so the request goes to the game window and follows its rules;
        // the game window's own close button is disabled while this modal screen is open.
        stage.setOnCloseRequest(event -> {
            event.consume();
            owner.fireEvent(new WindowEvent(owner, WindowEvent.WINDOW_CLOSE_REQUEST));
        });
        Scene scene = new Scene(content);
        scene.getStylesheets().addAll(owner.getScene().getStylesheets());
        scene.addEventFilter(KeyEvent.KEY_PRESSED, this::onKey);
        stage.setScene(scene);
        stage.setOnShown(event -> {
            resize();
            // Nothing that acts is focused at first, so a stray Space or Enter does nothing
            content.requestFocus();
        });
        stage.show();
    }

    private void offerContinue(Runnable action) {
        continueAction = action;
        continueButton.setVisible(true);
        continueButton.setDisable(false);
        resize();
    }

    private void finish() {
        if (stage == null || continueButton.isDisable()) {
            return;
        }
        Runnable action = continueAction;
        close();
        if (action != null) {
            action.run();
        }
    }

    private void showStatus(String text) {
        status.setText(text);
        status.setVisible(true);
    }

    private void setNumberedOptions(Map<Integer, Button> options) {
        numberedOptions.clear();
        numberedOptions.putAll(options);
    }

    private void onKey(KeyEvent event) {
        int number = switch (event.getCode()) {
            case DIGIT1, NUMPAD1 -> 1;
            case DIGIT2, NUMPAD2 -> 2;
            case DIGIT3, NUMPAD3 -> 3;
            case DIGIT4, NUMPAD4 -> 4;
            default -> 0;
        };
        if (number != 0) {
            Button option = numberedOptions.get(number);
            if (option != null && !option.isDisable()) {
                option.fire();
            }
            event.consume();
        } else if (event.getCode() == KeyCode.ESCAPE) {
            // There is nothing to cancel: the surprise must be finished
            event.consume();
        }
    }

    private void resize() {
        if (stage == null || !stage.isShowing()) {
            return;
        }
        stage.sizeToScene();
        Window owner = stage.getOwner();
        Rectangle2D area = Screen.getScreensForRectangle(owner.getX(), owner.getY(), owner.getWidth(), owner.getHeight())
                .stream().findFirst().orElse(Screen.getPrimary()).getVisualBounds();
        double x = owner.getX() + (owner.getWidth() - stage.getWidth()) / 2;
        double y = owner.getY() + (owner.getHeight() - stage.getHeight()) / 2;
        stage.setX(Math.max(area.getMinX(), Math.min(x, area.getMaxX() - stage.getWidth())));
        stage.setY(Math.max(area.getMinY() + SCREEN_MARGIN, Math.min(y, area.getMaxY() - stage.getHeight())));
    }

    /**
     * A choice with the colour circle; the text always names the colour too. {@code keyNumber},
     * if not 0, is shown in front as the number key that picks it.
     */
    private static Button optionButton(int keyNumber, String colour, String label) {
        Button button = new Button((keyNumber == 0 ? "" : keyNumber + "   ") + label, colourCircle(colour));
        button.setContentDisplay(ContentDisplay.LEFT);
        button.setGraphicTextGap(10);
        button.getStyleClass().addAll("ludo-button", "surprise-option");
        button.setMaxWidth(Double.MAX_VALUE);
        button.setAccessibleText(label);
        return button;
    }

    private static Node pieceLine(String colour, String sentence) {
        HBox line = new HBox(10, colourCircle(colour), text(sentence));
        line.setAlignment(Pos.CENTER_LEFT);
        return line;
    }

    private static Label text(String sentence) {
        Label label = new Label(sentence);
        label.getStyleClass().add("surprise-text");
        label.setWrapText(true);
        return label;
    }

    private static Circle colourCircle(String colour) {
        Circle circle = new Circle(9);
        circle.getStyleClass().addAll("player-circle", "player-" + requireColour(colour));
        return circle;
    }

    private static String pieceName(String colour, int number) {
        requireColour(colour);
        if (number < 1 || number > MAX_PIECE) {
            throw new IllegalArgumentException("Piece numbers must be 1–" + MAX_PIECE);
        }
        return colourWord(colour) + " piece " + number;
    }

    private static String colourWord(String colour) {
        String lower = requireColour(colour);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private static String squares(int count) {
        return count + (count == 1 ? " square" : " squares");
    }

    /** Returns the colour in lower case, the form the circle styles use. */
    private static String requireColour(String colour) {
        String lower = colour == null ? "" : colour.strip().toLowerCase(Locale.ROOT);
        if (!COLOURS.contains(lower)) {
            throw new IllegalArgumentException("Colour must be Red, Green, Yellow or Blue, not " + colour);
        }
        return lower;
    }

    private static void requireText(String value, String what) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("The " + what + " must not be empty");
        }
    }
}
