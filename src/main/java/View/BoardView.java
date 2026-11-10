package View;

import java.util.ArrayList;
import java.util.List;

import javafx.beans.binding.Bindings;
import javafx.beans.binding.DoubleBinding;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
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
 */
public class BoardView {

    private static final int GRID = 15;
    private static final double CELL = 40;
    private static final double BOARD_SIZE = GRID * CELL;
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

    // Sample game shown until GameController supplies the real one
    private static final String[] SAMPLE_NAMES = {"Maya", "Daniel", "Noa", "Omer"};
    private static final int SAMPLE_ACTIVE_PLAYER = 0;
    private static final String SAMPLE_ELAPSED = "00:12:34";
    private static final int[][] SAMPLE_PROTECTED_SQUARES = {{6, 3}, {2, 8}, {8, 10}, {12, 6}};
    private static final String SAMPLE_MESSAGE = "Roll the die to start your turn.";

    private final StackPane root;
    private final DiceView diceView = new DiceView();

    public BoardView() {
        // Whose turn it is on the left, elapsed time on the right
        Label turnLine = new Label(SAMPLE_NAMES[SAMPLE_ACTIVE_PLAYER] + "'s turn");
        turnLine.getStyleClass().add("ludo-turn-line");
        turnLine.setMinWidth(Region.USE_PREF_SIZE);
        HBox turnColour = new HBox(6, colourCircle(SAMPLE_ACTIVE_PLAYER, 9),
                text(COLOUR_NAMES[SAMPLE_ACTIVE_PLAYER] + ", Player " + (SAMPLE_ACTIVE_PLAYER + 1), "ludo-summary-text"));
        turnColour.setAlignment(Pos.CENTER_LEFT);
        HBox turnBox = new HBox(14, turnLine, turnColour);
        turnBox.setAlignment(Pos.CENTER_LEFT);

        VBox elapsedBox = new VBox(text("Elapsed time", "ludo-hint"), text(SAMPLE_ELAPSED, "ludo-elapsed-time"));
        elapsedBox.setAlignment(Pos.CENTER_RIGHT);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox topBar = new HBox(turnBox, spacer, elapsedBox);
        topBar.setAlignment(Pos.CENTER_LEFT);
        BorderPane.setMargin(topBar, new Insets(0, 0, 12, 0));

        // Dice area and the message line beside the board
        Label message = text(SAMPLE_MESSAGE, "ludo-message");
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
            addCastle(board, colour);
        }
        addFinish(board);
        for (int colour = 0; colour < COLOURS.length; colour++) {
            addStartSquare(board, colour);
        }
        for (int[] square : SAMPLE_PROTECTED_SQUARES) {
            board.getChildren().add(star(square[0], square[1]));
        }

        Rectangle border = new Rectangle(BOARD_SIZE, BOARD_SIZE);
        border.getStyleClass().add("board-border");
        board.getChildren().add(border);
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
     * Castle corner: coloured frame, white yard with the player's name and four numbered pieces.
     * The active player's Castle gets a heavy outline and a "TURN" tag; the others are faded.
     */
    private void addCastle(Pane board, int colour) {
        double x = CASTLE_ORIGINS[colour][1] * CELL;
        double y = CASTLE_ORIGINS[colour][0] * CELL;
        double size = CASTLE_CELLS * CELL;
        double yardSize = size - 2 * CASTLE_INSET;
        boolean active = colour == SAMPLE_ACTIVE_PLAYER;

        Rectangle frame = new Rectangle(x, y, size, size);
        frame.getStyleClass().addAll("board-square", "player-" + COLOURS[colour]);
        Rectangle yard = new Rectangle(x + CASTLE_INSET, y + CASTLE_INSET, yardSize, yardSize);
        yard.getStyleClass().add("board-castle-yard");

        Label name = text(SAMPLE_NAMES[colour], "board-player-name");
        name.setMaxWidth(yardSize - 60);
        HBox nameRow = new HBox(6, name);
        nameRow.setAlignment(Pos.CENTER);
        if (active) {
            nameRow.getChildren().add(text("TURN", "board-turn-tag"));
        }

        GridPane pieces = new GridPane();
        pieces.setHgap(18);
        pieces.setVgap(14);
        pieces.setAlignment(Pos.CENTER);
        for (int number = 1; number <= 4; number++) {
            pieces.add(piece(colour, number), (number - 1) % 2, (number - 1) / 2);
        }

        VBox content = new VBox(10, nameRow, pieces);
        content.setAlignment(Pos.CENTER);
        content.setPrefSize(yardSize, yardSize);
        content.relocate(x + CASTLE_INSET, y + CASTLE_INSET);

        Group castle = new Group(frame, yard, content);
        castle.getStyleClass().add("board-castle");
        castle.setAccessibleText(SAMPLE_NAMES[colour] + "'s Castle, " + COLOUR_NAMES[colour]
                + (active ? ", current turn" : ""));
        if (active) {
            Rectangle outline = new Rectangle(x, y, size, size);
            outline.getStyleClass().add("board-castle-active");
            castle.getChildren().add(outline);
        } else {
            castle.getStyleClass().add("board-castle-inactive");
        }
        board.getChildren().add(castle);
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
        return star;
    }

    private static StackPane piece(int colour, int number) {
        Circle disc = colourCircle(colour, 22);
        Text label = new Text(String.valueOf(number));
        label.getStyleClass().addAll("board-piece-number", "board-piece-number-" + COLOURS[colour]);
        StackPane piece = new StackPane(disc, label);
        piece.getStyleClass().add("board-piece");
        piece.setAccessibleText(COLOUR_NAMES[colour] + " piece " + number);
        return piece;
    }

    private static Circle colourCircle(int colour, double radius) {
        Circle circle = new Circle(radius);
        circle.getStyleClass().addAll("player-circle", "player-" + COLOURS[colour]);
        return circle;
    }

    private static Label text(String value, String styleClass) {
        Label label = new Label(value);
        label.getStyleClass().add(styleClass);
        return label;
    }
}
