package Controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import Model.Board;
import Model.PlayerColor;
import Model.PlayerRoute;
import Model.PlayerRouteFactory;
import Model.Position;
import Model.QuestionDifficulty;
import Model.Square;
import Model.SquareColor;
import Model.SquareType;
import View.BoardView;

/**
 * Turns a game board into the plain square values the board screen draws, square by square, so
 * the screen shows exactly what the board contains. The board grid is the smallest square of the
 * Model's storage grid that holds every non-empty square: 15×15 for Easy, 17×17 for Medium.
 */
public final class BoardSquares {

    private final int rowOffset;
    private final int columnOffset;
    private final int gridSize;
    private final List<BoardView.BoardSquare> squares;

    private BoardSquares(int rowOffset, int columnOffset, int gridSize, List<BoardView.BoardSquare> squares) {
        this.rowOffset = rowOffset;
        this.columnOffset = columnOffset;
        this.gridSize = gridSize;
        this.squares = List.copyOf(squares);
    }

    /**
     * The board's squares as plain values. Each Arrow points the way its colour's pieces turn
     * there: towards the next square on that colour's route.
     *
     * @throws IllegalArgumentException if the board has no squares, or an Arrow is not on its
     *                                  colour's route
     */
    public static BoardSquares of(Board board, Map<PlayerColor, PlayerRoute> routes) {
        Objects.requireNonNull(board, "board");
        Objects.requireNonNull(routes, "routes");
        int minRow = Integer.MAX_VALUE, minColumn = Integer.MAX_VALUE, maxRow = -1, maxColumn = -1;
        List<Square> drawn = new ArrayList<>();
        for (int row = 0; row < Board.STORAGE_SIZE; row++) {
            for (int column = 0; column < Board.STORAGE_SIZE; column++) {
                Square square = board.squareAt(new Position(row, column));
                if (square.type() == SquareType.EMPTY) {
                    continue;
                }
                drawn.add(square);
                minRow = Math.min(minRow, row);
                minColumn = Math.min(minColumn, column);
                maxRow = Math.max(maxRow, row);
                maxColumn = Math.max(maxColumn, column);
            }
        }
        if (drawn.isEmpty()) {
            throw new IllegalArgumentException("The board has no squares to draw");
        }
        int size = Math.max(maxRow - minRow, maxColumn - minColumn) + 1;

        List<BoardView.BoardSquare> values = new ArrayList<>();
        for (Square square : drawn) {
            Position position = square.position();
            values.add(new BoardView.BoardSquare(position.row() - minRow, position.column() - minColumn,
                    kindWord(square.type()), colourWord(square.associatedColor()),
                    levelWord(square.questionDifficulty()),
                    square.type() == SquareType.ARROW ? arrowDirection(square, routes) : null));
        }
        return new BoardSquares(minRow, minColumn, size, values);
    }

    /**
     * A new Medium board and its routes from the Model, with the Model's randomly placed special
     * squares: 8 protected, 6 question (2 per level) and 2 surprise.
     */
    public static BoardSquares medium() {
        return of(Board.createMedium(), PlayerRouteFactory.createMedium());
    }

    /** Rows and columns of the board grid. */
    public int gridSize() {
        return gridSize;
    }

    /** Every square to draw, in board grid coordinates; the list cannot be modified. */
    public List<BoardView.BoardSquare> squares() {
        return squares;
    }

    /** Draws these squares on the board screen. */
    public void showOn(BoardView view) {
        view.showBoard(gridSize, squares);
    }

    int gridRow(Position position) {
        return position.row() - rowOffset;
    }

    int gridColumn(Position position) {
        return position.column() - columnOffset;
    }

    static String kindWord(SquareType type) {
        return switch (type) {
            case NORMAL -> "path";
            case PROTECTED -> "protected";
            case START -> "start";
            case ARROW -> "arrow";
            case QUESTION -> "question";
            case SURPRISE -> "surprise";
            case CASTLE -> "castle";
            case HOME_PATH -> "home";
            case FINISHING -> "finish";
            case EMPTY -> throw new IllegalArgumentException("Empty squares are not drawn");
        };
    }

    /** "Red", "Green", "Yellow" or "Blue"; {@code null} for a square of no colour. */
    static String colourWord(SquareColor colour) {
        return colour == SquareColor.NEUTRAL ? null : capitalised(colour.name());
    }

    /** "Easy", "Medium" or "Hard"; {@code null} for a square that is not a question. */
    static String levelWord(QuestionDifficulty level) {
        return level == null ? null : capitalised(level.name());
    }

    private static String arrowDirection(Square arrow, Map<PlayerColor, PlayerRoute> routes) {
        PlayerColor colour = PlayerColor.valueOf(arrow.associatedColor().name());
        List<Position> route = routes.get(colour).positions();
        int index = route.indexOf(arrow.position());
        if (index < 0 || index == route.size() - 1) {
            throw new IllegalArgumentException("Arrow " + arrow.position() + " is not on the " + colour + " route");
        }
        Position next = route.get(index + 1);
        int rowStep = Integer.signum(next.row() - arrow.position().row());
        int columnStep = Integer.signum(next.column() - arrow.position().column());
        if (rowStep != 0 && columnStep != 0) {
            throw new IllegalArgumentException("Arrow " + arrow.position() + " turns diagonally");
        }
        return rowStep < 0 ? "up" : rowStep > 0 ? "down" : columnStep < 0 ? "left" : "right";
    }

    private static String capitalised(String name) {
        return name.charAt(0) + name.substring(1).toLowerCase(Locale.ROOT);
    }
}
