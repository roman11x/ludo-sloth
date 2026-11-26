package View;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiConsumer;

import javafx.beans.binding.Bindings;
import javafx.beans.binding.DoubleBinding;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;

/**
 * Displays the game board screen.
 * <p>
 * The board is drawn from the squares given to {@link #showBoard(int, List)}, so any board the
 * game describes (Easy 15×15, Medium 17×17) is shown, special squares included. Squares are
 * addressed in board grid coordinates: row and column 0 to the grid size - 1, from the top-left
 * corner. Colours are named "Red", "Green", "Yellow" or "Blue" (any case).
 */
public class BoardView {

    /**
     * One square to draw, in board grid coordinates, as plain values.
     *
     * @param kind      "path", "protected", "start", "arrow", "home", "finish", "castle",
     *                  "question" or "surprise"
     * @param colour    the colour a start, arrow, home, finish or castle square belongs to;
     *                  {@code null} for the other kinds
     * @param level     "Easy", "Medium" or "Hard" for a question square; {@code null} otherwise
     * @param direction "up", "down", "left" or "right" for an arrow: the way that colour's pieces
     *                  turn there; {@code null} otherwise
     */
    public record BoardSquare(int row, int column, String kind, String colour, String level, String direction) {
        public BoardSquare {
            if (!KINDS.contains(kind)) {
                throw new IllegalArgumentException("Unknown square kind: " + kind);
            }
            if (COLOURED_KINDS.contains(kind)) {
                colourIndex(colour);
            } else if (colour != null) {
                throw new IllegalArgumentException("A " + kind + " square has no colour");
            }
            if (kind.equals("question") != (level != null) || (level != null && !LEVELS.contains(level))) {
                throw new IllegalArgumentException("A question square, and only one, has a level: Easy, Medium or Hard");
            }
            if (kind.equals("arrow") != (direction != null) || (direction != null && !DIRECTIONS.containsKey(direction))) {
                throw new IllegalArgumentException("An arrow, and only an arrow, has a direction: up, down, left or right");
            }
        }
    }

    private static final List<String> KINDS = List.of(
            "path", "protected", "start", "arrow", "home", "finish", "castle", "question", "surprise");
    private static final List<String> COLOURED_KINDS = List.of("start", "arrow", "home", "finish", "castle");
    // Squares of the playing path: drawn as a plain square first, then marked
    private static final List<String> PATH_KINDS = List.of("path", "protected", "start", "arrow", "question", "surprise");
    private static final List<String> LEVELS = List.of("Easy", "Medium", "Hard");
    // Arrow rotation for each direction; the arrow shape points right
    private static final Map<String, Double> DIRECTIONS = Map.of("right", 0.0, "down", 90.0, "left", 180.0, "up", -90.0);

    private static final double CELL = 40;
    private static final int CASTLE_CELLS = 6;
    private static final double CASTLE_INSET = 30;
    private static final double SIDE_WIDTH = 250;

    // Index order everywhere: Red, Green, Yellow, Blue.
    private static final String[] COLOURS = {"red", "green", "yellow", "blue"};
    private static final String[] COLOUR_NAMES = {"Red", "Green", "Yellow", "Blue"};

    // Pieces: large in the Castle yard (2x2 below the name), smaller on squares, smaller still
    // when several share a square
    private static final double CASTLE_PIECE_RADIUS = 22;
    private static final double[] CASTLE_SLOT_X = {84, 156};
    private static final double[] CASTLE_SLOT_Y = {104, 176};
    private static final double SQUARE_PIECE_RADIUS = 15;
    private static final double CASTLE_RING_GAP = 4;
    // A lone piece's ring, at its thickest (focused), still ends inside the square
    private static final double RING_GAP = 2.5;
    // Pieces sharing a square are drawn smaller, each with its selectable ring on its own edge, so
    // a piece, its outline and its ring stay inside its own part of the square:
    // two to four in a 2x2 grid, five to seven (allowed on protected and Start squares) in rows of
    // three with the last row centred.
    private static final double CROWDED_RING_WIDTH = 2;
    private static final double STACKED_PIECE_RADIUS = 8.5;
    private static final double STACK_OFFSET = 10;
    private static final long STACKED_FONT_SIZE = 8;
    private static final int DENSE_COLUMNS = 3;
    private static final double DENSE_SPACING = CELL / DENSE_COLUMNS;
    private static final double DENSE_RADIUS = 5;
    private static final long DENSE_FONT_SIZE = 8;

    private final StackPane root;
    private final DiceView diceView = new DiceView();
    private final Button mainMenuButton = new Button("Back to Main Menu");
    private final Label turnLine = new Label();
    private final Circle turnCircle = new Circle(9);
    private final Label turnColour = new Label();
    private final Label elapsedTime = new Label(formatElapsed(0));
    private final Label message = new Label();
    private final Pane pieceLayer = new Pane();
    private final StackPane boardHolder = new StackPane();
    // Per colour, from the squares shown: its Castle (null if the board has none) and that
    // Castle's top-left square {row, column}
    private final Castle[] castles = new Castle[COLOURS.length];
    private final int[][] castleOrigins = new int[COLOURS.length][];
    private final String[] playerNames = new String[COLOURS.length];
    private final Map<String, PieceNode> pieces = new LinkedHashMap<>();
    private BiConsumer<String, Integer> onPieceChosen;
    private double boardSize;

    public BoardView() {
        // Whose turn it is on the left, elapsed time on the right
        turnLine.getStyleClass().add("ludo-turn-line");
        turnLine.setMinWidth(Region.USE_PREF_SIZE);
        turnCircle.getStyleClass().add("player-circle");
        turnCircle.setVisible(false);
        turnColour.getStyleClass().add("ludo-summary-text");
        HBox turnColourRow = new HBox(6, turnCircle, turnColour);
        turnColourRow.setAlignment(Pos.CENTER_LEFT);
        HBox turnBox = new HBox(14, turnLine, turnColourRow);
        turnBox.setAlignment(Pos.CENTER_LEFT);

        elapsedTime.getStyleClass().add("ludo-elapsed-time");
        VBox elapsedBox = new VBox(text("Elapsed time", "ludo-hint"), elapsedTime);
        elapsedBox.setAlignment(Pos.CENTER_RIGHT);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox topBar = new HBox(turnBox, spacer, elapsedBox);
        topBar.setAlignment(Pos.CENTER_LEFT);
        BorderPane.setMargin(topBar, new Insets(0, 0, 12, 0));

        // Dice area and the message line beside the board
        message.getStyleClass().add("ludo-message");
        message.setWrapText(true);
        VBox messageBox = new VBox(6, text("Messages", "ludo-section-label"), message);
        messageBox.getStyleClass().add("ludo-panel");
        mainMenuButton.getStyleClass().addAll("ludo-button", "ludo-button-secondary", "ludo-button-compact");
        mainMenuButton.setMaxWidth(Double.MAX_VALUE);
        mainMenuButton.setDisable(true);
        VBox side = new VBox(16, diceView.getRoot(), messageBox, mainMenuButton);
        side.setMinWidth(SIDE_WIDTH);
        side.setPrefWidth(SIDE_WIDTH);
        side.setMaxWidth(SIDE_WIDTH);
        BorderPane.setMargin(side, new Insets(0, 0, 0, 16));

        BorderPane group = new BorderPane();
        group.setTop(topBar);
        boardHolder.setMinSize(0, 0);
        group.setCenter(boardHolder);
        group.setRight(side);

        // The group is centred and no wider than the largest square board plus the side panel,
        // so the dice panel always sits right beside the board and the board still grows with the window.
        root = new StackPane(group);
        root.getStyleClass().addAll("ludo-setup", "ludo-board-screen");
        group.maxWidthProperty().bind(Bindings.createDoubleBinding(
                () -> Math.max(0, root.getHeight() - 32 - topBar.getHeight() - 12) + 16 + SIDE_WIDTH,
                root.heightProperty(), topBar.heightProperty()));
    }

    /**
     * Returns the root node of this screen, ready to be placed in a scene.
     */
    public Parent getRoot() {
        return root;
    }

    /**
     * Returns the dice area shown beside the board, so a controller can attach the roll action.
     */
    public DiceView getDiceView() {
        return diceView;
    }

    public void setOnMainMenu(Runnable action) {
        mainMenuButton.setOnAction(action == null ? null : event -> action.run());
        mainMenuButton.setDisable(action == null);
    }

    /** Avoid leaving while an outstanding roll animation is resolving a move. */
    public void setMainMenuEnabled(boolean enabled) {
        mainMenuButton.setDisable(!enabled || mainMenuButton.getOnAction() == null);
    }

    public boolean confirmLeaveGame() {
        ButtonType leave = new ButtonType("Leave game", ButtonBar.ButtonData.OK_DONE);
        ButtonType keep = new ButtonType("Keep playing", ButtonBar.ButtonData.CANCEL_CLOSE);
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                "This game is still in progress. Returning to the main menu will end it without saving a result.",
                leave, keep);
        alert.setTitle("Leave game");
        alert.setHeaderText("Return to the main menu?");
        if (root.getScene() != null && root.getScene().getWindow() != null) {
            alert.initOwner(root.getScene().getWindow());
        }
        ((Button) alert.getDialogPane().lookupButton(leave)).setDefaultButton(false);
        ((Button) alert.getDialogPane().lookupButton(keep)).setDefaultButton(true);
        return alert.showAndWait().orElse(keep) == leave;
    }

    /**
     * Sets who plays: {@code colours.get(i)} belongs to {@code names.get(i)}. Each name is shown in
     * its colour's Castle; Castles of colours not listed are left empty with no name. Removes all
     * pieces, so call {@link #showPiece} afterwards for every piece.
     */
    public void showPlayers(List<String> colours, List<String> names) {
        if (colours.size() != names.size()) {
            throw new IllegalArgumentException("Each colour needs exactly one name");
        }
        Arrays.fill(playerNames, null);
        for (int i = 0; i < colours.size(); i++) {
            playerNames[colourIndex(colours.get(i))] = names.get(i);
        }
        applyPlayerNames();
        pieces.clear();
        pieceLayer.getChildren().clear();
    }

    /**
     * Draws the board from these squares, replacing any board shown before; player names and
     * pieces already shown stay. Each square is drawn from its kind: plain path squares; a star
     * on a protected square; "#" on a tinted Start; a tinted Arrow with an arrow pointing in its
     * direction; solid Home Path squares; the Finish as one triangle per colour; a Castle for each
     * colour's castle squares; a question mark in the level's colour with the level's letter
     * (E, M or H); a gift on a surprise square.
     *
     * @param gridSize rows and columns of the board grid
     * @throws IllegalArgumentException if a square is outside the grid or listed twice
     */
    public void showBoard(int gridSize, List<BoardSquare> squares) {
        if (gridSize < 1) {
            throw new IllegalArgumentException("The board grid needs at least one square");
        }
        Map<String, BoardSquare> byPosition = new LinkedHashMap<>();
        for (BoardSquare square : squares) {
            if (square.row() < 0 || square.row() >= gridSize || square.column() < 0 || square.column() >= gridSize) {
                throw new IllegalArgumentException("Square outside the " + gridSize + "×" + gridSize + " grid: "
                        + square.row() + "," + square.column());
            }
            if (byPosition.put(square.row() + "," + square.column(), square) != null) {
                throw new IllegalArgumentException("Square listed twice: " + square.row() + "," + square.column());
            }
        }
        boardSize = gridSize * CELL;
        Pane board = buildBoard(List.copyOf(byPosition.values()));
        boardHolder.getChildren().setAll(new Group(board));
        DoubleBinding scale = Bindings.createDoubleBinding(
                () -> Math.max(0.05, Math.min(boardHolder.getWidth(), boardHolder.getHeight()) / boardSize),
                boardHolder.widthProperty(), boardHolder.heightProperty());
        board.scaleXProperty().bind(scale);
        board.scaleYProperty().bind(scale);
        applyPlayerNames();
        layoutPieces();
    }

    private void applyPlayerNames() {
        for (int i = 0; i < castles.length; i++) {
            if (castles[i] != null) {
                castles[i].setName(playerNames[i]);
            }
        }
    }

    /**
     * Shows a piece on the square at {@code row}, {@code column}. A square inside a Castle shows
     * the piece in that Castle's yard, in the matching corner. Several pieces on one square are
     * drawn side by side.
     */
    public void showPiece(String colour, int number, int row, int column) {
        int colourIndex = colourIndex(colour);
        String key = pieceKey(colourIndex, number);
        PieceNode node = pieces.get(key);
        if (node == null) {
            node = new PieceNode(colourIndex, number);
            pieces.put(key, node);
            pieceLayer.getChildren().add(node);
        }
        node.row = row;
        node.column = column;
        layoutPieces();
    }

    /**
     * Shows whose turn it is: "{name}'s turn" with the colour circle and name, a heavy outline and
     * a "TURN" tag on that colour's Castle; the other Castles are faded.
     */
    public void showTurn(String name, String colour) {
        showHeadline(name + "'s turn", colour, "TURN");
    }

    /**
     * Shows that the game is over: "{name} wins!" with the colour, and a "WINNER" tag on the
     * winner's Castle. Rolling is disabled.
     */
    public void showWinner(String name, String colour) {
        showHeadline(name + " wins!", colour, "WINNER");
        clearSelectablePieces();
        diceView.setRollEnabled(false);
    }

    /**
     * Shows the elapsed game time as HH:MM:SS.
     */
    public void showElapsedTime(long seconds) {
        elapsedTime.setText(formatElapsed(seconds));
    }

    /**
     * Shows a message about the latest game event, replacing the previous one.
     */
    public void showMessage(String text) {
        message.setText(text == null ? "" : text);
    }

    /**
     * Highlights these pieces of {@code colour} and lets the player choose one of them by mouse
     * (click) or keyboard (Tab to it, then Enter or Space). All other pieces cannot be chosen.
     * Keyboard focus moves to the first of them.
     */
    public void setSelectablePieces(String colour, List<Integer> numbers) {
        clearSelectablePieces();
        int colourIndex = colourIndex(colour);
        PieceNode first = null;
        for (int number : numbers) {
            PieceNode node = pieces.get(pieceKey(colourIndex, number));
            if (node != null) {
                node.setSelectable(true);
                if (first == null) {
                    first = node;
                }
            }
        }
        if (first != null) {
            first.requestFocus();
        }
    }

    /**
     * Makes every piece unselectable again and removes the highlight.
     */
    public void clearSelectablePieces() {
        for (PieceNode node : pieces.values()) {
            node.setSelectable(false);
        }
    }

    /**
     * Sets what happens when the player chooses one of the selectable pieces: it receives the
     * piece's colour and number. {@code null} means choosing does nothing.
     */
    public void setOnPieceChosen(BiConsumer<String, Integer> action) {
        onPieceChosen = action;
    }

    private void showHeadline(String headline, String colour, String castleTag) {
        int colourIndex = colourIndex(colour);
        turnLine.setText(headline);
        turnCircle.getStyleClass().setAll("player-circle", "player-" + COLOURS[colourIndex]);
        turnCircle.setVisible(true);
        turnColour.setText(COLOUR_NAMES[colourIndex]);
        for (int i = 0; i < castles.length; i++) {
            if (castles[i] != null) {
                castles[i].setHighlighted(i == colourIndex, castleTag);
            }
        }
    }

    private void choose(PieceNode node) {
        if (node.selectable && onPieceChosen != null) {
            onPieceChosen.accept(COLOUR_NAMES[node.colourIndex], node.number);
        }
    }

    /**
     * Places every piece: Castle pieces in their yard corner, other pieces centred on their square,
     * or side by side when they share it.
     */
    private void layoutPieces() {
        Map<String, List<PieceNode>> bySquare = new HashMap<>();
        for (PieceNode node : pieces.values()) {
            if (castleAt(node.row, node.column) < 0) {
                bySquare.computeIfAbsent(node.row + "," + node.column, k -> new ArrayList<>()).add(node);
            }
        }
        for (PieceNode node : pieces.values()) {
            int castle = castleAt(node.row, node.column);
            if (castle >= 0) {
                int originRow = castleOrigins[castle][0];
                int originColumn = castleOrigins[castle][1];
                boolean right = node.column - originColumn >= CASTLE_CELLS / 2;
                boolean bottom = node.row - originRow >= CASTLE_CELLS / 2;
                node.place(originColumn * CELL + CASTLE_SLOT_X[right ? 1 : 0],
                        originRow * CELL + CASTLE_SLOT_Y[bottom ? 1 : 0], CASTLE_PIECE_RADIUS, CASTLE_RING_GAP);
                continue;
            }
            List<PieceNode> together = bySquare.get(node.row + "," + node.column);
            double centreX = node.column * CELL + CELL / 2;
            double centreY = node.row * CELL + CELL / 2;
            int slot = together.indexOf(node);
            if (together.size() == 1) {
                node.place(centreX, centreY, SQUARE_PIECE_RADIUS, RING_GAP);
            } else if (together.size() <= 4) {
                double dx = (slot % 2 == 0 ? -1 : 1) * STACK_OFFSET;
                double dy = (slot < 2 ? -1 : 1) * STACK_OFFSET;
                node.placeCrowded(centreX + dx, centreY + dy, STACKED_PIECE_RADIUS, STACKED_FONT_SIZE);
            } else {
                int rows = (together.size() + DENSE_COLUMNS - 1) / DENSE_COLUMNS;
                int rowIndex = slot / DENSE_COLUMNS;
                int inRow = Math.min(DENSE_COLUMNS, together.size() - rowIndex * DENSE_COLUMNS);
                double dx = (slot % DENSE_COLUMNS - (inRow - 1) / 2.0) * DENSE_SPACING;
                double dy = (rowIndex - (rows - 1) / 2.0) * DENSE_SPACING;
                node.placeCrowded(centreX + dx, centreY + dy, DENSE_RADIUS, DENSE_FONT_SIZE);
            }
        }
    }

    /** Index of the Castle whose 6x6 region contains this square, or -1. */
    private int castleAt(int row, int column) {
        for (int i = 0; i < castleOrigins.length; i++) {
            if (castleOrigins[i] == null) {
                continue;
            }
            int r = row - castleOrigins[i][0];
            int c = column - castleOrigins[i][1];
            if (r >= 0 && r < CASTLE_CELLS && c >= 0 && c < CASTLE_CELLS) {
                return i;
            }
        }
        return -1;
    }

    private static int colourIndex(String colour) {
        for (int i = 0; i < COLOUR_NAMES.length; i++) {
            if (COLOUR_NAMES[i].equalsIgnoreCase(colour)) {
                return i;
            }
        }
        throw new IllegalArgumentException("Unknown colour: " + colour);
    }

    private static String pieceKey(int colourIndex, int number) {
        return colourIndex + "-" + number;
    }

    private static String formatElapsed(long seconds) {
        long safe = Math.max(0, seconds);
        return String.format(Locale.ROOT, "%02d:%02d:%02d", safe / 3600, (safe / 60) % 60, safe % 60);
    }

    /**
     * Builds the board from its squares, in layers: path squares, Arrows and Home Paths, Castles,
     * the Finish, Starts, then the marks of protected, question and surprise squares. The pieces
     * go on top.
     */
    private Pane buildBoard(List<BoardSquare> squares) {
        Pane board = new Pane();
        board.setMinSize(boardSize, boardSize);
        board.setPrefSize(boardSize, boardSize);
        board.setMaxSize(boardSize, boardSize);
        board.setAccessibleText("Ludo board");

        for (BoardSquare square : squares) {
            if (PATH_KINDS.contains(square.kind())) {
                board.getChildren().add(square(square.row(), square.column(), "board-plain", "board-path"));
            }
        }
        for (BoardSquare square : squares) {
            int colour = square.colour() == null ? -1 : colourIndex(square.colour());
            if (square.kind().equals("arrow")) {
                board.getChildren().add(square(square.row(), square.column(),
                        "tint-" + COLOURS[colour], "board-home", "board-arrow-square"));
                board.getChildren().add(arrow(square.row(), square.column(), colour, square.direction()));
            } else if (square.kind().equals("home")) {
                board.getChildren().add(square(square.row(), square.column(), "player-" + COLOURS[colour], "board-home"));
            }
        }
        addCastles(board, squares);
        addFinish(board, squares);
        for (BoardSquare square : squares) {
            if (square.kind().equals("start")) {
                addStartSquare(board, square.row(), square.column(), colourIndex(square.colour()));
            }
        }
        Group marks = new Group();
        marks.setMouseTransparent(true);
        for (BoardSquare square : squares) {
            switch (square.kind()) {
                case "protected" -> marks.getChildren().add(star(square.row(), square.column()));
                case "question" -> marks.getChildren().add(questionMark(square.row(), square.column(), square.level()));
                case "surprise" -> marks.getChildren().add(gift(square.row(), square.column()));
                default -> { }
            }
        }
        board.getChildren().add(marks);

        Rectangle border = new Rectangle(boardSize, boardSize);
        border.getStyleClass().add("board-border");
        border.setMouseTransparent(true);
        pieceLayer.setPickOnBounds(false);
        board.getChildren().addAll(border, pieceLayer);
        return board;
    }

    /** One Castle per colour, covering the 6x6 region whose top-left is that colour's first castle square. */
    private void addCastles(Pane board, List<BoardSquare> squares) {
        Arrays.fill(castles, null);
        for (int colour = 0; colour < COLOURS.length; colour++) {
            castleOrigins[colour] = null;
        }
        for (BoardSquare square : squares) {
            if (square.kind().equals("castle")) {
                int colour = colourIndex(square.colour());
                int[] origin = castleOrigins[colour];
                castleOrigins[colour] = origin == null ? new int[]{square.row(), square.column()}
                        : new int[]{Math.min(origin[0], square.row()), Math.min(origin[1], square.column())};
            }
        }
        for (int colour = 0; colour < COLOURS.length; colour++) {
            if (castleOrigins[colour] != null) {
                castles[colour] = new Castle(colour, castleOrigins[colour][0], castleOrigins[colour][1]);
                board.getChildren().add(castles[colour].node);
            }
        }
    }

    private void addStartSquare(Pane board, int row, int col, int colour) {
        Rectangle tint = square(row, col, "tint-" + COLOURS[colour], "board-start");
        Label mark = text("#", "board-start-mark");
        mark.setAlignment(Pos.CENTER);
        mark.setPrefSize(CELL, CELL);
        mark.relocate(col * CELL, row * CELL);
        board.getChildren().addAll(tint, mark);
    }

    /**
     * The Finish: over the block that holds the finish squares, one triangle per colour, from the
     * side where that colour's finish square lies to the centre.
     */
    private static void addFinish(Pane board, List<BoardSquare> squares) {
        List<BoardSquare> finish = squares.stream().filter(square -> square.kind().equals("finish")).toList();
        if (finish.isEmpty()) {
            return;
        }
        int top = finish.stream().mapToInt(BoardSquare::row).min().orElseThrow();
        int bottom = finish.stream().mapToInt(BoardSquare::row).max().orElseThrow();
        int left = finish.stream().mapToInt(BoardSquare::column).min().orElseThrow();
        int right = finish.stream().mapToInt(BoardSquare::column).max().orElseThrow();
        double x1 = left * CELL;
        double x2 = (right + 1) * CELL;
        double y1 = top * CELL;
        double y2 = (bottom + 1) * CELL;
        double midX = (x1 + x2) / 2;
        double midY = (y1 + y2) / 2;
        for (int colour = 0; colour < COLOURS.length; colour++) {
            for (BoardSquare square : finish) {
                if (colourIndex(square.colour()) != colour) {
                    continue;
                }
                double[] points;
                if (square.row() == bottom) {
                    points = new double[]{x1, y2, x2, y2, midX, midY};
                } else if (square.column() == left) {
                    points = new double[]{x1, y1, x1, y2, midX, midY};
                } else if (square.row() == top) {
                    points = new double[]{x1, y1, x2, y1, midX, midY};
                } else {
                    points = new double[]{x2, y1, x2, y2, midX, midY};
                }
                Polygon triangle = new Polygon(points);
                triangle.getStyleClass().addAll("board-finish", "player-" + COLOURS[colour]);
                board.getChildren().add(triangle);
            }
        }
    }

    private static Rectangle square(int row, int col, String... styleClasses) {
        Rectangle square = new Rectangle(col * CELL, row * CELL, CELL, CELL);
        square.getStyleClass().add("board-square");
        square.getStyleClass().addAll(styleClasses);
        return square;
    }

    /**
     * Arrow centred in the square, in its colour, pointing the way that colour's pieces turn.
     */
    private static Polygon arrow(int row, int col, int colour, String direction) {
        Polygon arrow = new Polygon(6, 13, 20, 13, 20, 6, 34, 20, 20, 34, 20, 27, 6, 27);
        arrow.getStyleClass().addAll("board-arrow", "player-" + COLOURS[colour]);
        arrow.setLayoutX(col * CELL);
        arrow.setLayoutY(row * CELL);
        arrow.setRotate(DIRECTIONS.get(direction));
        return arrow;
    }

    /**
     * A question mark in the level's colour, with the level's letter (E, M or H) in the corner,
     * so the level never depends on colour alone.
     */
    private static StackPane questionMark(int row, int col, String level) {
        Text mark = new Text("?");
        mark.getStyleClass().addAll("board-question", "board-question-" + level.toLowerCase(Locale.ROOT));
        Text letter = new Text(level.substring(0, 1));
        letter.getStyleClass().add("board-question-level");
        StackPane cell = new StackPane(mark, letter);
        StackPane.setAlignment(mark, Pos.CENTER_LEFT);
        StackPane.setMargin(mark, new Insets(0, 0, 0, 7));
        StackPane.setAlignment(letter, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(letter, new Insets(0, 3, 2, 0));
        cell.setPrefSize(CELL, CELL);
        cell.resizeRelocate(col * CELL, row * CELL, CELL, CELL);
        cell.setAccessibleText(level + " question square");
        return cell;
    }

    /** A gift: a box with a ribbon down the middle and a bow on top. */
    private static Group gift(int row, int col) {
        double x = col * CELL;
        double y = row * CELL;
        Rectangle box = new Rectangle(x + 10, y + 18, 20, 14);
        Rectangle lid = new Rectangle(x + 8, y + 13, 24, 6);
        box.getStyleClass().add("board-gift");
        lid.getStyleClass().add("board-gift");
        Rectangle ribbon = new Rectangle(x + 18.5, y + 13, 3, 19);
        ribbon.getStyleClass().add("board-gift-ribbon");
        Circle bowLeft = new Circle(x + 16, y + 9.5, 3.5);
        Circle bowRight = new Circle(x + 24, y + 9.5, 3.5);
        bowLeft.getStyleClass().add("board-gift");
        bowRight.getStyleClass().add("board-gift");
        Group gift = new Group(bowLeft, bowRight, box, lid, ribbon);
        gift.setAccessibleText("Surprise square");
        return gift;
    }

    private static Polygon star(int row, int col) {
        Polygon star = new Polygon();
        double centreX = col * CELL + CELL / 2;
        double centreY = row * CELL + CELL / 2;
        for (int point = 0; point < 10; point++) {
            double radius = point % 2 == 0 ? 15 : 6.5;
            double angle = Math.toRadians(-90 + point * 36);
            star.getPoints().addAll(centreX + radius * Math.cos(angle), centreY + radius * Math.sin(angle));
        }
        star.getStyleClass().add("board-star");
        star.setMouseTransparent(true);
        return star;
    }

    private static Label text(String value, String styleClass) {
        Label label = new Label(value);
        label.getStyleClass().add(styleClass);
        return label;
    }

    /**
     * Castle corner: coloured frame and a white yard with the player's name at the top. The
     * highlighted Castle gets a heavy outline and a tag; the others are faded.
     */
    private static final class Castle {

        private final int colourIndex;
        private final Group node;
        private final Label name = text("", "board-player-name");
        private final Label tag = text("", "board-turn-tag");
        private final Rectangle outline;
        private String playerName;

        private Castle(int colourIndex, int originRow, int originColumn) {
            this.colourIndex = colourIndex;
            double x = originColumn * CELL;
            double y = originRow * CELL;
            double size = CASTLE_CELLS * CELL;
            double yardSize = size - 2 * CASTLE_INSET;

            Rectangle frame = new Rectangle(x, y, size, size);
            frame.getStyleClass().addAll("board-square", "player-" + COLOURS[colourIndex]);
            Rectangle yard = new Rectangle(x + CASTLE_INSET, y + CASTLE_INSET, yardSize, yardSize);
            yard.getStyleClass().add("board-castle-yard");

            name.setMaxWidth(yardSize - 70);
            tag.setVisible(false);
            tag.setManaged(false);
            HBox nameRow = new HBox(6, name, tag);
            nameRow.setAlignment(Pos.CENTER);
            nameRow.setPrefSize(yardSize, 44);
            nameRow.relocate(x + CASTLE_INSET, y + CASTLE_INSET);

            outline = new Rectangle(x, y, size, size);
            outline.getStyleClass().add("board-castle-active");
            outline.setVisible(false);

            node = new Group(frame, yard, nameRow, outline);
            node.getStyleClass().addAll("board-castle", "board-castle-inactive");
            node.setMouseTransparent(true);
            setName(null);
        }

        /** {@code null} means this colour is not playing: no name is shown. */
        private void setName(String playerName) {
            this.playerName = playerName;
            name.setText(playerName == null ? "" : playerName);
            name.setVisible(playerName != null);
            setHighlighted(false, null);
        }

        private void setHighlighted(boolean highlighted, String tagText) {
            outline.setVisible(highlighted);
            tag.setText(highlighted && tagText != null ? tagText : "");
            tag.setVisible(highlighted && tagText != null);
            tag.setManaged(highlighted && tagText != null);
            node.getStyleClass().remove("board-castle-inactive");
            if (!highlighted) {
                node.getStyleClass().add("board-castle-inactive");
            }
            node.setAccessibleText((playerName == null ? "Empty" : playerName + "'s") + " Castle, "
                    + COLOUR_NAMES[colourIndex] + (highlighted && tagText != null ? ", " + tagText.toLowerCase(Locale.ROOT) : ""));
        }
    }

    /**
     * One numbered piece: a solid circle in its colour. When selectable it gets a dashed ring
     * (solid and thicker while focused), a hand cursor, and responds to click, Enter and Space.
     */
    private final class PieceNode extends StackPane {

        private final int colourIndex;
        private final int number;
        private final Circle ring = new Circle();
        private final Circle disc = new Circle();
        private final Text label;
        private int row;
        private int column;
        private boolean selectable;

        private PieceNode(int colourIndex, int number) {
            this.colourIndex = colourIndex;
            this.number = number;
            ring.getStyleClass().add("board-piece-ring");
            ring.setVisible(false);
            ring.setMouseTransparent(true);
            disc.getStyleClass().addAll("player-circle", "player-" + COLOURS[colourIndex]);
            label = new Text(String.valueOf(number));
            label.getStyleClass().addAll("board-piece-number", "board-piece-number-" + COLOURS[colourIndex]);
            getChildren().addAll(ring, disc, label);
            getStyleClass().add("board-piece");
            setFocusTraversable(false);
            // Clicks count only on the piece's circles, not its square bounds, so neighbouring
            // pieces in a shared square never take each other's clicks
            setPickOnBounds(false);
            // Exact positions: pixel snapping would shift pieces by a fraction towards their neighbours
            setSnapToPixel(false);
            // Dashed ring while selectable, solid while focused, so focus is visible without colour
            focusedProperty().addListener((obs, was, focused) -> updateRing());
            updateRing();
            setOnMouseClicked(event -> {
                if (event.getButton() == MouseButton.PRIMARY) {
                    choose(this);
                }
            });
            setOnKeyPressed(event -> {
                if (event.getCode() == KeyCode.ENTER || event.getCode() == KeyCode.SPACE) {
                    choose(this);
                    event.consume();
                }
            });
            updateAccessibleText();
        }

        /** A piece with its selectable ring drawn around it, a small gap outside its edge. */
        private void place(double centreX, double centreY, double radius, double ringGap) {
            getChildren().setAll(ring, disc, label);
            ring.setStyle("");
            layOut(centreX, centreY, radius, radius + ringGap, Math.round(radius * 0.8));
        }

        /**
         * A smaller piece in a shared square: the selectable ring is drawn on the piece's own edge
         * (on top of it) so it never reaches a neighbouring piece.
         */
        private void placeCrowded(double centreX, double centreY, double radius, long fontSize) {
            getChildren().setAll(disc, ring, label);
            ring.setStyle("-fx-stroke-width: " + CROWDED_RING_WIDTH + ";");
            layOut(centreX, centreY, radius, radius, fontSize);
        }

        private void layOut(double centreX, double centreY, double radius, double ringRadius, long fontSize) {
            disc.setRadius(radius);
            ring.setRadius(ringRadius);
            label.setStyle("-fx-font-size: " + fontSize + "px;");
            double size = 2 * (ringRadius + 3);
            setMinSize(size, size);
            setPrefSize(size, size);
            setMaxSize(size, size);
            resizeRelocate(centreX - size / 2, centreY - size / 2, size, size);
        }

        private void setSelectable(boolean selectable) {
            this.selectable = selectable;
            ring.setVisible(selectable);
            setFocusTraversable(selectable);
            getStyleClass().remove("board-piece-selectable");
            if (selectable) {
                getStyleClass().add("board-piece-selectable");
                toFront();
            }
            updateAccessibleText();
        }

        private void updateRing() {
            if (isFocused()) {
                ring.getStrokeDashArray().clear();
            } else {
                ring.getStrokeDashArray().setAll(6.0, 4.0);
            }
        }

        private void updateAccessibleText() {
            setAccessibleText(COLOUR_NAMES[colourIndex] + " piece " + number
                    + (selectable ? ", can move: press Enter to move it" : ""));
        }
    }
}
