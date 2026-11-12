package View;

import java.util.ArrayList;
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
 * Squares are addressed in board grid coordinates: row and column 0 to {@link #GRID_SIZE} - 1,
 * from the top-left corner. Colours are named "Red", "Green", "Yellow" or "Blue" (any case).
 */
public class BoardView {

    /** Number of rows and columns of the board grid. */
    public static final int GRID_SIZE = 15;

    private static final double CELL = 40;
    private static final double BOARD_SIZE = GRID_SIZE * CELL;
    private static final int CASTLE_CELLS = 6;
    private static final double CASTLE_INSET = 30;
    private static final double SIDE_WIDTH = 250;
    private static final int HOME_PATH_LENGTH = 6;

    // Index order everywhere: Red, Green, Yellow, Blue.
    // Corners as in the Model and the board diagram: Green top-left, Yellow top-right,
    // Blue bottom-right, Red bottom-left.
    private static final String[] COLOURS = {"red", "green", "yellow", "blue"};
    private static final String[] COLOUR_NAMES = {"Red", "Green", "Yellow", "Blue"};
    private static final int[][] CASTLE_ORIGINS = {{9, 0}, {0, 0}, {0, 9}, {9, 9}};
    // Outermost centre-line square of each colour's Finish arm; first square of its Home Path
    private static final int[][] ARROW_SQUARES = {{14, 7}, {7, 0}, {0, 7}, {7, 14}};
    // Step from each Arrow Square towards the Finish, and the matching arrow rotation
    private static final int[][] HOME_DIRECTIONS = {{-1, 0}, {0, 1}, {1, 0}, {0, -1}};
    private static final double[] ARROW_ROTATIONS = {-90, 0, 90, 180};
    // Two clockwise steps after each Arrow Square
    private static final int[][] START_SQUARES = {{13, 6}, {6, 1}, {1, 8}, {8, 13}};

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
    private final Label turnLine = new Label();
    private final Circle turnCircle = new Circle(9);
    private final Label turnColour = new Label();
    private final Label elapsedTime = new Label(formatElapsed(0));
    private final Label message = new Label();
    private final Group starLayer = new Group();
    private final Pane pieceLayer = new Pane();
    private final Castle[] castles = new Castle[COLOURS.length];
    private final Map<String, PieceNode> pieces = new LinkedHashMap<>();
    private BiConsumer<String, Integer> onPieceChosen;

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
        VBox side = new VBox(16, diceView.getRoot(), messageBox);
        side.setMinWidth(SIDE_WIDTH);
        side.setPrefWidth(SIDE_WIDTH);
        side.setMaxWidth(SIDE_WIDTH);
        BorderPane.setMargin(side, new Insets(0, 0, 0, 16));

        BorderPane group = new BorderPane();
        group.setTop(topBar);
        group.setCenter(scaledToFit(buildBoard()));
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

    /**
     * Sets who plays: {@code colours.get(i)} belongs to {@code names.get(i)}. Each name is shown in
     * its colour's Castle; Castles of colours not listed are left empty with no name. Removes all
     * pieces, so call {@link #showPiece} afterwards for every piece.
     */
    public void showPlayers(List<String> colours, List<String> names) {
        if (colours.size() != names.size()) {
            throw new IllegalArgumentException("Each colour needs exactly one name");
        }
        for (Castle castle : castles) {
            castle.setName(null);
        }
        for (int i = 0; i < colours.size(); i++) {
            castles[colourIndex(colours.get(i))].setName(names.get(i));
        }
        pieces.clear();
        pieceLayer.getChildren().clear();
    }

    /**
     * Marks these squares as protected with a star; each entry is {row, column}. Replaces any
     * previously shown stars.
     */
    public void showProtectedSquares(List<int[]> squares) {
        starLayer.getChildren().clear();
        for (int[] square : squares) {
            starLayer.getChildren().add(star(square[0], square[1]));
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
            castles[i].setHighlighted(i == colourIndex, castleTag);
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
                int originRow = CASTLE_ORIGINS[castle][0];
                int originColumn = CASTLE_ORIGINS[castle][1];
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
    private static int castleAt(int row, int column) {
        for (int i = 0; i < CASTLE_ORIGINS.length; i++) {
            int r = row - CASTLE_ORIGINS[i][0];
            int c = column - CASTLE_ORIGINS[i][1];
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
     * Wraps the fixed-size board so it is scaled uniformly to the largest square that fits,
     * keeping it square and fully visible at any window size.
     */
    private static StackPane scaledToFit(Pane board) {
        StackPane holder = new StackPane(new Group(board));
        holder.setMinSize(0, 0);
        DoubleBinding scale = Bindings.createDoubleBinding(
                () -> Math.max(0.05, Math.min(holder.getWidth(), holder.getHeight()) / BOARD_SIZE),
                holder.widthProperty(), holder.heightProperty());
        board.scaleXProperty().bind(scale);
        board.scaleYProperty().bind(scale);
        return holder;
    }

    private Pane buildBoard() {
        Pane board = new Pane();
        board.setMinSize(BOARD_SIZE, BOARD_SIZE);
        board.setPrefSize(BOARD_SIZE, BOARD_SIZE);
        board.setMaxSize(BOARD_SIZE, BOARD_SIZE);
        board.setAccessibleText("Ludo board");

        for (int[] square : commonPath()) {
            board.getChildren().add(square(square[0], square[1], "board-plain", "board-path"));
        }
        for (int colour = 0; colour < COLOURS.length; colour++) {
            addHomePath(board, colour);
            castles[colour] = new Castle(colour);
            board.getChildren().add(castles[colour].node);
        }
        addFinish(board);
        for (int colour = 0; colour < COLOURS.length; colour++) {
            addStartSquare(board, colour);
        }
        board.getChildren().add(starLayer);

        Rectangle border = new Rectangle(BOARD_SIZE, BOARD_SIZE);
        border.getStyleClass().add("board-border");
        border.setMouseTransparent(true);
        pieceLayer.setPickOnBounds(false);
        board.getChildren().addAll(border, pieceLayer);
        return board;
    }

    /**
     * The 52 common-path squares in clockwise order, starting at the outer end of the top row of
     * the left arm.
     */
    private static List<int[]> commonPath() {
        List<int[]> path = new ArrayList<>();
        for (int col = 0; col <= 5; col++) {
            path.add(new int[]{6, col});
        }
        for (int row = 5; row >= 0; row--) {
            path.add(new int[]{row, 6});
        }
        path.add(new int[]{0, 7});
        for (int row = 0; row <= 5; row++) {
            path.add(new int[]{row, 8});
        }
        for (int col = 9; col <= 14; col++) {
            path.add(new int[]{6, col});
        }
        path.add(new int[]{7, 14});
        for (int col = 14; col >= 9; col--) {
            path.add(new int[]{8, col});
        }
        for (int row = 9; row <= 14; row++) {
            path.add(new int[]{row, 8});
        }
        path.add(new int[]{14, 7});
        for (int row = 14; row >= 9; row--) {
            path.add(new int[]{row, 6});
        }
        for (int col = 5; col >= 0; col--) {
            path.add(new int[]{8, col});
        }
        path.add(new int[]{7, 0});
        return path;
    }

    /**
     * Home Path: the tinted Arrow Square with its arrow, then five solid squares towards the Finish.
     */
    private void addHomePath(Pane board, int colour) {
        int row = ARROW_SQUARES[colour][0];
        int col = ARROW_SQUARES[colour][1];
        board.getChildren().add(square(row, col, "tint-" + COLOURS[colour], "board-home", "board-arrow-square"));
        board.getChildren().add(arrow(row, col, colour));
        for (int step = 1; step < HOME_PATH_LENGTH; step++) {
            int r = row + step * HOME_DIRECTIONS[colour][0];
            int c = col + step * HOME_DIRECTIONS[colour][1];
            board.getChildren().add(square(r, c, "player-" + COLOURS[colour], "board-home"));
        }
    }

    private void addStartSquare(Pane board, int colour) {
        int row = START_SQUARES[colour][0];
        int col = START_SQUARES[colour][1];
        Rectangle tint = square(row, col, "tint-" + COLOURS[colour], "board-start");
        Label mark = text("#", "board-start-mark");
        mark.setAlignment(Pos.CENTER);
        mark.setPrefSize(CELL, CELL);
        mark.relocate(col * CELL, row * CELL);
        board.getChildren().addAll(tint, mark);
    }

    /**
     * Central Finish: four triangles, each in the colour of the Home Path that ends there.
     */
    private void addFinish(Pane board) {
        double low = 6 * CELL;
        double high = 9 * CELL;
        double mid = BOARD_SIZE / 2;
        double[][] triangles = {
                {low, high, high, high, mid, mid},
                {low, low, low, high, mid, mid},
                {low, low, high, low, mid, mid},
                {high, low, high, high, mid, mid}};
        for (int colour = 0; colour < COLOURS.length; colour++) {
            Polygon triangle = new Polygon(triangles[colour]);
            triangle.getStyleClass().addAll("board-finish", "player-" + COLOURS[colour]);
            board.getChildren().add(triangle);
        }
    }

    private static Rectangle square(int row, int col, String... styleClasses) {
        Rectangle square = new Rectangle(col * CELL, row * CELL, CELL, CELL);
        square.getStyleClass().add("board-square");
        square.getStyleClass().addAll(styleClasses);
        return square;
    }

    /**
     * Arrow centred in the square, pointing along the Home Path towards the Finish.
     */
    private static Polygon arrow(int row, int col, int colour) {
        Polygon arrow = new Polygon(6, 13, 20, 13, 20, 6, 34, 20, 20, 34, 20, 27, 6, 27);
        arrow.getStyleClass().addAll("board-arrow", "player-" + COLOURS[colour]);
        arrow.setLayoutX(col * CELL);
        arrow.setLayoutY(row * CELL);
        arrow.setRotate(ARROW_ROTATIONS[colour]);
        return arrow;
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

        private Castle(int colourIndex) {
            this.colourIndex = colourIndex;
            double x = CASTLE_ORIGINS[colourIndex][1] * CELL;
            double y = CASTLE_ORIGINS[colourIndex][0] * CELL;
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
