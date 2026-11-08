package Model;

import java.util.ArrayList;
import java.util.Objects;
import java.util.Random;

/**
 * Fixed square definitions in the global 19x19 coordinate system.
 * Every coordinate has a Square with a separate activity status.
 * Player routes and changing piece occupancy are maintained separately.
 */
public final class Board {
    public static final int STORAGE_SIZE = 19;

    private final Square[][] squares = new Square[STORAGE_SIZE][STORAGE_SIZE];

    private Board() {
        for (int row = 0; row < STORAGE_SIZE; row++) {
            for (int column = 0; column < STORAGE_SIZE; column++) {
                defineSquare(row, column, SquareType.EMPTY, SquareColor.NEUTRAL,
                        SquareStatus.INACTIVE);
            }
        }
    }

    /** Creates Easy geometry and randomly places four protected squares. */
    public static Board createEasy() {
        return createEasy(new Random());
    }

    /** Allows reproducible layouts in tests by supplying a seeded Random. */
    public static Board createEasy(Random random) {
        Objects.requireNonNull(random, "random");
        Board board = new Board();
        board.defineEasyCommonSquares();
        board.defineEasyStartsAndArrows();
        board.defineEasyHomesAndCastles();
        board.placeEasyProtectedSquares(random);

        // Activate the four inner corners without changing the existing random layout.
        board.defineSquare(8, 8, SquareType.NORMAL, SquareColor.NEUTRAL, SquareStatus.ACTIVE);
        board.defineSquare(8, 10, SquareType.NORMAL, SquareColor.NEUTRAL, SquareStatus.ACTIVE);
        board.defineSquare(10, 8, SquareType.NORMAL, SquareColor.NEUTRAL, SquareStatus.ACTIVE);
        board.defineSquare(10, 10, SquareType.NORMAL, SquareColor.NEUTRAL, SquareStatus.ACTIVE);
        return board;
    }

    private void defineEasyCommonSquares() {
        // Define the edges of the four arms by global coordinates.
        // Iteration order describes construction only, not a walking route.
        for (int coordinate = 2; coordinate <= 16; coordinate++) {
            if (coordinate < 8 || coordinate > 10) {
                defineSquare(coordinate, 8, SquareType.NORMAL, SquareColor.NEUTRAL, SquareStatus.ACTIVE);
                defineSquare(coordinate, 10, SquareType.NORMAL, SquareColor.NEUTRAL, SquareStatus.ACTIVE);
                defineSquare(8, coordinate, SquareType.NORMAL, SquareColor.NEUTRAL, SquareStatus.ACTIVE);
                defineSquare(10, coordinate, SquareType.NORMAL, SquareColor.NEUTRAL, SquareStatus.ACTIVE);
            }
        }
        // The four endpoints between the edges complete the 52 shared squares.
        defineSquare(2, 9, SquareType.NORMAL, SquareColor.NEUTRAL, SquareStatus.ACTIVE);
        defineSquare(9, 16, SquareType.NORMAL, SquareColor.NEUTRAL, SquareStatus.ACTIVE);
        defineSquare(16, 9, SquareType.NORMAL, SquareColor.NEUTRAL, SquareStatus.ACTIVE);
        defineSquare(9, 2, SquareType.NORMAL, SquareColor.NEUTRAL, SquareStatus.ACTIVE);
    }

    private void defineEasyStartsAndArrows() {
        defineSquare(8, 3, SquareType.START, SquareColor.GREEN, SquareStatus.ACTIVE);
        defineSquare(3, 10, SquareType.START, SquareColor.YELLOW, SquareStatus.ACTIVE);
        defineSquare(10, 15, SquareType.START, SquareColor.BLUE, SquareStatus.ACTIVE);
        defineSquare(15, 8, SquareType.START, SquareColor.RED, SquareStatus.ACTIVE);

        defineSquare(9, 2, SquareType.ARROW, SquareColor.GREEN, SquareStatus.ACTIVE);
        defineSquare(2, 9, SquareType.ARROW, SquareColor.YELLOW, SquareStatus.ACTIVE);
        defineSquare(9, 16, SquareType.ARROW, SquareColor.BLUE, SquareStatus.ACTIVE);
        defineSquare(16, 9, SquareType.ARROW, SquareColor.RED, SquareStatus.ACTIVE);
    }

    private void defineEasyHomesAndCastles() {
        // Five Home cells followed by a separate sixth cell: Finish.
        for (int step = 0; step < 5; step++) {
            defineSquare(9, 3 + step, SquareType.HOME_PATH, SquareColor.GREEN, SquareStatus.ACTIVE);
            defineSquare(3 + step, 9, SquareType.HOME_PATH, SquareColor.YELLOW, SquareStatus.ACTIVE);
            defineSquare(9, 15 - step, SquareType.HOME_PATH, SquareColor.BLUE, SquareStatus.ACTIVE);
            defineSquare(15 - step, 9, SquareType.HOME_PATH, SquareColor.RED, SquareStatus.ACTIVE);
        }
        defineSquare(9, 8, SquareType.FINISHING, SquareColor.GREEN, SquareStatus.ACTIVE);
        defineSquare(8, 9, SquareType.FINISHING, SquareColor.YELLOW, SquareStatus.ACTIVE);
        defineSquare(9, 10, SquareType.FINISHING, SquareColor.BLUE, SquareStatus.ACTIVE);
        defineSquare(10, 9, SquareType.FINISHING, SquareColor.RED, SquareStatus.ACTIVE);

        defineCastle(2, 2, SquareColor.GREEN);
        defineCastle(2, 11, SquareColor.YELLOW);
        defineCastle(11, 11, SquareColor.BLUE);
        defineCastle(11, 2, SquareColor.RED);
    }

    private void defineCastle(int topRow, int leftColumn, SquareColor color) {
        // The full 6x6 region belongs to the Castle for drawing purposes.
        for (int row = topRow; row < topRow + 6; row++) {
            for (int column = leftColumn; column < leftColumn + 6; column++) {
                defineSquare(row, column, SquareType.CASTLE, color, SquareStatus.INACTIVE);
            }
        }

        // Only the four slots from the diagram are usable piece locations.
        defineSquare(topRow + 1, leftColumn + 1, SquareType.CASTLE, color, SquareStatus.ACTIVE);
        defineSquare(topRow + 1, leftColumn + 4, SquareType.CASTLE, color, SquareStatus.ACTIVE);
        defineSquare(topRow + 4, leftColumn + 1, SquareType.CASTLE, color, SquareStatus.ACTIVE);
        defineSquare(topRow + 4, leftColumn + 4, SquareType.CASTLE, color, SquareStatus.ACTIVE);
    }

    private void placeEasyProtectedSquares(Random random) {
        // A temporary set of candidates for random selection, not a route.
        var candidates = new ArrayList<Position>();
        for (Square[] row : squares) {
            for (Square square : row) {
                if (square.type() == SquareType.NORMAL && square.status() == SquareStatus.ACTIVE) {
                    candidates.add(square.position());
                }
            }
        }
        for (int count = 0; count < 4; count++) {
            Position position = candidates.remove(random.nextInt(candidates.size()));
            defineSquare(position.row(), position.column(),
                    SquareType.PROTECTED, SquareColor.NEUTRAL, SquareStatus.ACTIVE);
        }
    }

    /** Whether the coordinate is inside the storage matrix, active or not. */
    public boolean isWithinBounds(Position position) {
        Objects.requireNonNull(position, "position");
        return position.row() >= 0 && position.row() < STORAGE_SIZE
                && position.column() >= 0 && position.column() < STORAGE_SIZE;
    }

    /** Returns any cell, including inactive Castle and empty cells. Only out-of-bounds cells are rejected. */
    public Square squareAt(Position position) {
        if (!isWithinBounds(position)) {
            throw new IllegalArgumentException("Position is outside the board: " + position);
        }
        return squares[position.row()][position.column()];
    }

    public boolean isActive(Position position) {
        return isWithinBounds(position)
                && squareAt(position).status() == SquareStatus.ACTIVE;
    }

    public Position startFor(PlayerColor color) {
        return findPosition(SquareType.START, color);
    }

    public Position arrowFor(PlayerColor color) {
        return findPosition(SquareType.ARROW, color);
    }

    private Position findPosition(SquareType type, PlayerColor color) {
        SquareColor associatedColor = SquareColor.fromPlayerColor(
                Objects.requireNonNull(color, "color"));
        for (Square[] row : squares) {
            for (Square square : row) {
                if (square.type() == type && square.associatedColor() == associatedColor) {
                    return square.position();
                }
            }
        }
        throw new IllegalStateException("Missing " + type + " for " + color);
    }

    private void defineSquare(int row, int column, SquareType type,
                              SquareColor color, SquareStatus status) {
        squares[row][column] = new Square(new Position(row, column), type, color, status);
    }
}
