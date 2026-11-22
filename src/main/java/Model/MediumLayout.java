package Model;

import java.util.Random;

/** Fixed Medium geometry; random special-square placement is added separately in B03. */
public final class MediumLayout extends BoardLayout {
    private static final int MIN = 1;
    private static final int MAX = 17;
    private final EasyLayout innerLayout = new EasyLayout();

    @Override
    protected void createCommonSquares(Square[][] squares) {
        innerLayout.createCommonSquares(squares);
        // The perimeter of a 17x17 square contains 64 distinct cells.
        for (int coordinate = MIN; coordinate <= MAX; coordinate++) {
            defineSquare(squares, MIN, coordinate, SquareType.NORMAL, SquareColor.NEUTRAL, SquareStatus.ACTIVE);
            defineSquare(squares, MAX, coordinate, SquareType.NORMAL, SquareColor.NEUTRAL, SquareStatus.ACTIVE);
            defineSquare(squares, coordinate, MIN, SquareType.NORMAL, SquareColor.NEUTRAL, SquareStatus.ACTIVE);
            defineSquare(squares, coordinate, MAX, SquareType.NORMAL, SquareColor.NEUTRAL, SquareStatus.ACTIVE);
        }
    }

    @Override
    protected void createStartsAndArrows(Square[][] squares) {
        innerLayout.createStartsAndArrows(squares);
        // Medium has four Starts on the outer ring; the former Easy Starts become ordinary cells.
        defineSquare(squares, 8, 3, SquareType.NORMAL, SquareColor.NEUTRAL, SquareStatus.ACTIVE);
        defineSquare(squares, 3, 10, SquareType.NORMAL, SquareColor.NEUTRAL, SquareStatus.ACTIVE);
        defineSquare(squares, 10, 15, SquareType.NORMAL, SquareColor.NEUTRAL, SquareStatus.ACTIVE);
        defineSquare(squares, 15, 8, SquareType.NORMAL, SquareColor.NEUTRAL, SquareStatus.ACTIVE);

        defineSquare(squares, MIN, MIN, SquareType.START, SquareColor.GREEN, SquareStatus.ACTIVE);
        defineSquare(squares, MIN, MAX, SquareType.START, SquareColor.YELLOW, SquareStatus.ACTIVE);
        defineSquare(squares, MAX, MAX, SquareType.START, SquareColor.BLUE, SquareStatus.ACTIVE);
        defineSquare(squares, MAX, MIN, SquareType.START, SquareColor.RED, SquareStatus.ACTIVE);

        defineSquare(squares, 8, MIN, SquareType.ARROW, SquareColor.GREEN, SquareStatus.ACTIVE);
        defineSquare(squares, MIN, 10, SquareType.ARROW, SquareColor.YELLOW, SquareStatus.ACTIVE);
        defineSquare(squares, 10, MAX, SquareType.ARROW, SquareColor.BLUE, SquareStatus.ACTIVE);
        defineSquare(squares, MAX, 8, SquareType.ARROW, SquareColor.RED, SquareStatus.ACTIVE);
    }

    @Override
    protected void createHomesAndCastles(Square[][] squares) {
        innerLayout.createHomesAndCastles(squares);
    }

    @Override
    protected void placeSpecialSquares(Square[][] squares, Random random) {
        // B02 builds fixed geometry only. B03 will place Medium stars, questions and surprises.
    }
}
