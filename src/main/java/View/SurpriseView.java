package View;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.ObjIntConsumer;

import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
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
 * names, colours, eligible opponents and pieces, the roll and the actual distances as plain values.
 * The screen is never taller than the screen it is on: its body scrolls, and the outcome line and
 * Continue stay visible below it.
 * <p>
 * Colours are the words "Red", "Green", "Yellow" or "Blue" (any case) and are always shown as a
 * word next to their circle. Choices can also be made by key: an opponent with the number shown
 * in front of it, a piece with its own piece number. Holding a key makes one choice; it counts
 * again only after it is released.
 */
public class SurpriseView {

    private static final double WIDTH = 520;
    // Width inside the screen's frame and padding (see .surprise-dialog)
    private static final double INNER_WIDTH = WIDTH - 2 * 24 - 2 * 4;
    private static final double MIN_SCROLL_HEIGHT = 120;
    private static final double SCREEN_MARGIN = 12;
    private static final Set<String> COLOURS = Set.of("red", "green", "yellow", "blue");
    private static final int MAX_OPPONENTS = 3;
    private static final int MAX_PIECE = 4;
    private static final int BACK_THREE = 3;

    private final VBox content = new VBox(14);
    private final ScrollPane scroll = new ScrollPane();
    private final Label playerLine = new Label();
    private final Label title = new Label();
    private final VBox body = new VBox(10);
    private final Label status = new Label();
    private final Button continueButton = new Button("Continue");
    private final Map<Integer, Button> numberedOptions = new HashMap<>(); // number key -> choice
    private final Set<KeyCode> heldKeys = new HashSet<>();
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

        // Event, choices and roll scroll when they do not fit the screen
        VBox scrolled = new VBox(14, header, title, body);
        scroll.setContent(scrolled);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.setMinHeight(MIN_SCROLL_HEIGHT);
        // Plain white, without a frame of its own (the Setup screen's scroll style)
        scroll.getStyleClass().add("ludo-setup-scroll");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        // The outcome and Continue stay below the scrolled part, always visible
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

        content.getChildren().addAll(scroll, status, continueRow);
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
     * Event 4, Back 3 squares: names the piece and how far it really moves back. A backward move
     * stops at the piece's own Start Square, so this can be fewer than 3.
     *
     * @param squaresBack the squares the piece actually moves back, as the game decided it: 0–3
     * @param onContinue  called after Continue has closed the screen
     * @throws IllegalArgumentException if {@code squaresBack} is not 0–3, or another value is invalid
     */
    public void showBackThreeSquares(Parent gameScreen, String playerName, String pieceColour, int pieceNumber,
                                     int squaresBack, Runnable onContinue) {
        String piece = pieceName(pieceColour, pieceNumber);
        if (squaresBack < 0 || squaresBack > BACK_THREE) {
            throw new IllegalArgumentException("Squares back must be 0–" + BACK_THREE);
        }
        String sentence;
        if (squaresBack == BACK_THREE) {
            sentence = playerName + "'s " + piece + " moves back 3 squares.";
        } else if (squaresBack == 0) {
            sentence = playerName + "'s " + piece + " is on its own Start Square, so it does not move back.";
        } else {
            sentence = playerName + "'s " + piece + " moves back " + squares(squaresBack)
                    + ": a piece never moves back past its own Start Square.";
        }
        announce(gameScreen, playerName, "Back 3 squares", pieceLine(pieceColour, sentence), onContinue);
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
     * Event 2, Opponent moves back. The player picks an eligible opponent, then sees the Standard
     * die roll, then picks one of that opponent's eligible pieces, which moves back the distance
     * the caller gives for it. If no opponent is eligible, the screen says the event is cancelled.
     * <p>
     * A piece is eligible when it can move back at least one square. An opponent is eligible
     * exactly when it has at least one eligible piece; input where {@code opponentEligible} and
     * {@code eligiblePieces} disagree is rejected. Opponents that are not eligible are shown with
     * "no piece can move back" and cannot be picked.
     *
     * @param opponentNames    the opponents, in the order shown
     * @param opponentColours  each opponent's colour word, same order
     * @param opponentEligible whether each opponent is eligible, same order
     * @param eligiblePieces   for each opponent, its eligible pieces: piece number (1–4) to the
     *                         squares that piece actually moves back (at least 1), as the game
     *                         decided it; empty for an opponent that is not eligible
     * @param roll             the Standard die roll, 1–6
     * @param onPieceChosen    after Continue: the picked opponent's name and piece number
     * @param onCancelled      after Continue, if the event was cancelled
     * @throws IllegalArgumentException if the lists differ in size, there are not 1–3 opponents,
     *                                  eligibility and eligible pieces disagree, or a value is
     *                                  missing or out of range
     */
    public void showOpponentMovesBack(Parent gameScreen, String playerName, List<String> opponentNames,
                                      List<String> opponentColours, List<Boolean> opponentEligible,
                                      List<Map<Integer, Integer>> eligiblePieces, int roll,
                                      ObjIntConsumer<String> onPieceChosen, Runnable onCancelled) {
        requireText(playerName, "player name");
        int count = opponentNames == null ? 0 : opponentNames.size();
        if (count < 1 || count > MAX_OPPONENTS || opponentColours == null || opponentColours.size() != count
                || opponentEligible == null || opponentEligible.size() != count
                || eligiblePieces == null || eligiblePieces.size() != count) {
            throw new IllegalArgumentException("Need 1–" + MAX_OPPONENTS + " opponents, each with a colour, "
                    + "an eligibility flag and its eligible pieces");
        }
        List<Map<Integer, Integer>> pieces = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            requireText(opponentNames.get(i), "opponent name");
            requireColour(opponentColours.get(i));
            Boolean eligible = opponentEligible.get(i);
            Map<Integer, Integer> opponentPieces = eligiblePieces.get(i);
            if (eligible == null || opponentPieces == null) {
                throw new IllegalArgumentException("Opponent " + (i + 1) + " needs an eligibility flag and its pieces");
            }
            if (eligible == opponentPieces.isEmpty()) {
                throw new IllegalArgumentException("Opponent " + (i + 1) + " is eligible exactly when it has at "
                        + "least one eligible piece");
            }
            for (Map.Entry<Integer, Integer> piece : opponentPieces.entrySet()) {
                if (piece.getKey() == null || piece.getKey() < 1 || piece.getKey() > MAX_PIECE) {
                    throw new IllegalArgumentException("Piece numbers must be 1–" + MAX_PIECE);
                }
                if (piece.getValue() == null || piece.getValue() < 1) {
                    throw new IllegalArgumentException("An eligible piece moves back at least one square");
                }
            }
            // Pieces are listed by number
            pieces.add(new TreeMap<>(opponentPieces));
        }
        if (roll < 1 || roll > 6) {
            throw new IllegalArgumentException("The Standard die roll must be 1–6");
        }

        new OpponentChoice(playerName, List.copyOf(opponentNames), List.copyOf(opponentColours),
                List.copyOf(opponentEligible), pieces, roll, onPieceChosen, onCancelled).start(gameScreen);
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
        private final List<Map<Integer, Integer>> pieces;
        private final int roll;
        private final ObjIntConsumer<String> onPieceChosen;
        private final Runnable onCancelled;
        private final List<Button> opponentButtons = new ArrayList<>();
        private final VBox rollBox = new VBox();
        private final VBox pieceBox = new VBox(8);

        OpponentChoice(String playerName, List<String> names, List<String> colours, List<Boolean> eligible,
                       List<Map<Integer, Integer>> pieces, int roll,
                       ObjIntConsumer<String> onPieceChosen, Runnable onCancelled) {
            this.playerName = playerName;
            this.names = names;
            this.colours = colours;
            this.eligible = eligible;
            this.pieces = pieces;
            this.roll = roll;
            this.onPieceChosen = onPieceChosen;
            this.onCancelled = onCancelled;
        }

        void start(Parent gameScreen) {
            Label intro = text(playerName + ", choose an opponent. A Standard die roll then decides how far "
                    + "one of their pieces moves back.");
            VBox opponents = new VBox(8);
            Map<Integer, Button> keys = new HashMap<>();
            for (int i = 0; i < names.size(); i++) {
                int index = i;
                Button button = optionButton(i + 1, colours.get(i), names.get(i) + " · " + colourWord(colours.get(i)));
                button.setOnAction(event -> chooseOpponent(index));
                if (!eligible.get(i)) {
                    button.setText(button.getText() + " (no piece can move back)");
                    button.setDisable(true);
                }
                opponentButtons.add(button);
                opponents.getChildren().add(button);
                keys.put(i + 1, button);
            }
            rollBox.managedProperty().bind(rollBox.visibleProperty());
            rollBox.setVisible(false);
            pieceBox.managedProperty().bind(pieceBox.visibleProperty());
            pieceBox.setVisible(false);
            open(gameScreen, playerName, "Opponent moves back", new VBox(10, intro, opponents, rollBox, pieceBox));
            setNumberedOptions(keys);
            if (eligible.stream().noneMatch(Boolean::booleanValue)) {
                cancel("No opponent has a piece that can move back, so this surprise is cancelled.");
            }
        }

        private void chooseOpponent(int index) {
            if (!eligible.get(index) || stage == null || rollBox.isVisible()) {
                return;
            }
            opponentButtons.forEach(button -> button.setDisable(true));
            opponentButtons.get(index).setText(opponentButtons.get(index).getText() + " (chosen)");
            opponentButtons.get(index).getStyleClass().add("surprise-option-chosen");
            showRoll();

            // Pieces are picked by their own number (key 3 picks piece 3), so no extra key number is shown
            String name = names.get(index);
            String colour = colours.get(index);
            List<Button> pieceButtons = new ArrayList<>();
            Map<Integer, Button> keys = new HashMap<>();
            for (Map.Entry<Integer, Integer> entry : pieces.get(index).entrySet()) {
                int piece = entry.getKey();
                int back = entry.getValue();
                Button button = optionButton(0, colour, pieceName(colour, piece) + " · moves back " + squares(back));
                button.setOnAction(event -> choosePiece(index, piece, back, button, pieceButtons));
                pieceButtons.add(button);
                keys.put(piece, button);
            }
            pieceBox.getChildren().setAll(text("Choose one of " + name + "'s pieces (click it or press its number):"));
            pieceBox.getChildren().addAll(pieceButtons);
            pieceBox.setVisible(true);
            setNumberedOptions(keys);
            resize();
            scrollToEnd();
        }

        private void choosePiece(int opponent, int piece, int back, Button chosen, List<Button> pieceButtons) {
            if (stage == null || continueButton.isVisible()) {
                return;
            }
            pieceButtons.forEach(button -> button.setDisable(true));
            chosen.setText(chosen.getText() + " (chosen)");
            chosen.getStyleClass().add("surprise-option-chosen");
            String name = names.get(opponent);
            showStatus(name + "'s " + pieceName(colours.get(opponent), piece) + " moves back " + squares(back) + ".");
            setNumberedOptions(Map.of());
            offerContinue(() -> {
                if (onPieceChosen != null) {
                    onPieceChosen.accept(name, piece);
                }
            });
            // The pieces are at the end of the body; keep the chosen one in view
            scrollToEnd();
        }

        private void showRoll() {
            Label value = new Label(String.valueOf(roll));
            value.getStyleClass().add("dice-result-value");
            StackPane die = new StackPane(value);
            die.getStyleClass().add("dice-result");
            die.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
            die.setAccessibleText("Standard die: " + roll);
            Label rolled = new Label("Standard die roll: " + roll);
            rolled.getStyleClass().add("surprise-roll");
            Label move = text("A piece moves back half the roll, rounded up, but never past its own Start "
                    + "Square. Each piece below shows how far it moves.");
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
        heldKeys.clear();
        scroll.setVvalue(0);
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
        scene.addEventFilter(KeyEvent.ANY, this::onKey);
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
        KeyCode code = event.getCode();
        if (event.getEventType() == KeyEvent.KEY_RELEASED) {
            heldKeys.remove(code);
            return;
        }
        if (event.getEventType() != KeyEvent.KEY_PRESSED) {
            return;
        }
        // Auto-repeat while a key is held must not make further choices
        boolean repeat = !heldKeys.add(code);
        int number = switch (code) {
            case DIGIT1, NUMPAD1 -> 1;
            case DIGIT2, NUMPAD2 -> 2;
            case DIGIT3, NUMPAD3 -> 3;
            case DIGIT4, NUMPAD4 -> 4;
            default -> 0;
        };
        if (number != 0) {
            Button option = numberedOptions.get(number);
            if (!repeat && option != null && !option.isDisable()) {
                option.fire();
            }
            event.consume();
        } else if (code == KeyCode.ESCAPE || (repeat && (code == KeyCode.ENTER || code == KeyCode.SPACE))) {
            // Escape: nothing to cancel. A held Enter or Space must not press Continue on repeat.
            event.consume();
        }
    }

    /** Fits the content, but never taller than the screen, so Continue is always on screen. */
    private void resize() {
        if (stage == null || !stage.isShowing()) {
            return;
        }
        Node scrolled = scroll.getContent();
        // Newly added choices must have their styles before their height is measured
        content.applyCss();
        scroll.setPrefViewportHeight(scrolled.prefHeight(INNER_WIDTH));
        stage.sizeToScene();
        Window owner = stage.getOwner();
        Rectangle2D area = Screen.getScreensForRectangle(owner.getX(), owner.getY(), owner.getWidth(), owner.getHeight())
                .stream().findFirst().orElse(Screen.getPrimary()).getVisualBounds();
        double maxHeight = area.getHeight() - 2 * SCREEN_MARGIN;
        if (stage.getHeight() > maxHeight) {
            stage.setHeight(maxHeight);
        }
        double x = owner.getX() + (owner.getWidth() - stage.getWidth()) / 2;
        double y = owner.getY() + (owner.getHeight() - stage.getHeight()) / 2;
        stage.setX(Math.max(area.getMinX(), Math.min(x, area.getMaxX() - stage.getWidth())));
        stage.setY(Math.max(area.getMinY() + SCREEN_MARGIN, Math.min(y, area.getMaxY() - SCREEN_MARGIN - stage.getHeight())));
    }

    /** Brings newly offered choices at the bottom of the body into view. */
    private void scrollToEnd() {
        scroll.layout();
        scroll.setVvalue(scroll.getVmax());
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
        // A definite width, so a wrapping line's height is computed for the width it really gets
        label.setPrefWidth(INNER_WIDTH - 40);
        label.setMaxWidth(Double.MAX_VALUE);
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
