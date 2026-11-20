package Model;

import java.util.ArrayList;
import java.util.Random;

/** Easy board geometry and placement of four protected squares. */
public final class EasyLayout extends BoardLayout {
    @Override
    protected void createCommonSquares(Square[][] squares) {
        // Define the edges of the four arms by global coordinates.
        // Iteration order describes construction only, not a walking route.
        for (int coordinate = 2; coordinate <= 16; coordinate++) {
            if (coordinate < 8 || coordinate > 10) {
                defineSquare(squares, coordinate, 8, SquareType.NORMAL, SquareColor.NEUTRAL, SquareStatus.ACTIVE);
                defineSquare(squares, coordinate, 10, SquareType.NORMAL, SquareColor.NEUTRAL, SquareStatus.ACTIVE);
                defineSquare(squares, 8, coordinate, SquareType.NORMAL, SquareColor.NEUTRAL, SquareStatus.ACTIVE);
                defineSquare(squares, 10, coordinate, SquareType.NORMAL, SquareColor.NEUTRAL, SquareStatus.ACTIVE);
            }
        }
        // The four endpoints between the edges complete the 52 shared squares.
        defineSquare(squares, 2, 9, SquareType.NORMAL, SquareColor.NEUTRAL, SquareStatus.ACTIVE);
        defineSquare(squares, 9, 16, SquareType.NORMAL, SquareColor.NEUTRAL, SquareStatus.ACTIVE);
        defineSquare(squares, 16, 9, SquareType.NORMAL, SquareColor.NEUTRAL, SquareStatus.ACTIVE);
        defineSquare(squares, 9, 2, SquareType.NORMAL, SquareColor.NEUTRAL, SquareStatus.ACTIVE);
    }

    @Override
    protected void createStartsAndArrows(Square[][] squares) {
        defineSquare(squares, 8, 3, SquareType.START, SquareColor.GREEN, SquareStatus.ACTIVE);
        defineSquare(squares, 3, 10, SquareType.START, SquareColor.YELLOW, SquareStatus.ACTIVE);
        defineSquare(squares, 10, 15, SquareType.START, SquareColor.BLUE, SquareStatus.ACTIVE);
        defineSquare(squares, 15, 8, SquareType.START, SquareColor.RED, SquareStatus.ACTIVE);

        defineSquare(squares, 9, 2, SquareType.ARROW, SquareColor.GREEN, SquareStatus.ACTIVE);
        defineSquare(squares, 2, 9, SquareType.ARROW, SquareColor.YELLOW, SquareStatus.ACTIVE);
        defineSquare(squares, 9, 16, SquareType.ARROW, SquareColor.BLUE, SquareStatus.ACTIVE);
        defineSquare(squares, 16, 9, SquareType.ARROW, SquareColor.RED, SquareStatus.ACTIVE);
    }

    @Override
    protected void createHomesAndCastles(Square[][] squares) {
        // Five Home cells followed by a separate sixth cell: Finish.
        for (int step = 0; step < 5; step++) {
            defineSquare(squares, 9, 3 + step, SquareType.HOME_PATH, SquareColor.GREEN, SquareStatus.ACTIVE);
            defineSquare(squares, 3 + step, 9, SquareType.HOME_PATH, SquareColor.YELLOW, SquareStatus.ACTIVE);
            defineSquare(squares, 9, 15 - step, SquareType.HOME_PATH, SquareColor.BLUE, SquareStatus.ACTIVE);
            defineSquare(squares, 15 - step, 9, SquareType.HOME_PATH, SquareColor.RED, SquareStatus.ACTIVE);
        }
        defineSquare(squares, 9, 8, SquareType.FINISHING, SquareColor.GREEN, SquareStatus.ACTIVE);
        defineSquare(squares, 8, 9, SquareType.FINISHING, SquareColor.YELLOW, SquareStatus.ACTIVE);
        defineSquare(squares, 9, 10, SquareType.FINISHING, SquareColor.BLUE, SquareStatus.ACTIVE);
        defineSquare(squares, 10, 9, SquareType.FINISHING, SquareColor.RED, SquareStatus.ACTIVE);

        defineCastle(squares, 2, 2, SquareColor.GREEN);
        defineCastle(squares, 2, 11, SquareColor.YELLOW);
        defineCastle(squares, 11, 11, SquareColor.BLUE);
        defineCastle(squares, 11, 2, SquareColor.RED);
    }

    private void defineCastle(Square[][] squares, int topRow, int leftColumn, SquareColor color) {
        // The full 6x6 region belongs to the Castle for drawing purposes.
        for (int row = topRow; row < topRow + 6; row++) {
            for (int column = leftColumn; column < leftColumn + 6; column++) {
                defineSquare(squares, row, column, SquareType.CASTLE, color, SquareStatus.INACTIVE);
            }
        }

        // Only the four slots from the diagram are usable piece locations.
        defineSquare(squares, topRow + 1, leftColumn + 1, SquareType.CASTLE, color, SquareStatus.ACTIVE);
        defineSquare(squares, topRow + 1, leftColumn + 4, SquareType.CASTLE, color, SquareStatus.ACTIVE);
        defineSquare(squares, topRow + 4, leftColumn + 1, SquareType.CASTLE, color, SquareStatus.ACTIVE);
        defineSquare(squares, topRow + 4, leftColumn + 4, SquareType.CASTLE, color, SquareStatus.ACTIVE);
    }

    @Override
    protected void placeSpecialSquares(Square[][] squares, Random random) {
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
            defineSquare(squares, position.row(), position.column(),
                    SquareType.PROTECTED, SquareColor.NEUTRAL, SquareStatus.ACTIVE);
        }
    }

}
