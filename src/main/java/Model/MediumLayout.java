package Model;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Medium geometry with per-game protected, question and surprise square placement. */
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
        List<Position> candidates = new ArrayList<>();
        for (Square[] row : squares) {
            for (Square square : row) {
                if (square.status() == SquareStatus.ACTIVE && square.type() == SquareType.NORMAL) {
                    candidates.add(square.position());
                }
            }
        }
        // Remove each selection from the same pool: no two special squares can overlap.
        for (int count = 0; count < 8; count++) {
            placeRandomSquare(squares, candidates, random, SquareType.PROTECTED, null);
        }
        for (QuestionDifficulty difficulty : QuestionDifficulty.values()) {
            for (int count = 0; count < 2; count++) {
                placeRandomSquare(squares, candidates, random, SquareType.QUESTION, difficulty);
            }
        }
        for (int count = 0; count < 2; count++) {
            placeRandomSquare(squares, candidates, random, SquareType.SURPRISE, null);
        }
    }

    private void placeRandomSquare(Square[][] squares, List<Position> candidates, Random random,
                                   SquareType type, QuestionDifficulty questionDifficulty) {
        Position position = candidates.remove(random.nextInt(candidates.size()));
        defineSquare(squares, position.row(), position.column(), type,
                SquareColor.NEUTRAL, SquareStatus.ACTIVE, questionDifficulty);
    }
}
