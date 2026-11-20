package Model;

import java.util.Objects;
import java.util.Random;

/** Template Method: fixed construction order, with geometry supplied by each layout. */
public abstract class BoardLayout {
    public final Board build() {
        return build(new Random());
    }

    /** Each build uses fresh definitions and produces independent board occupancy. */
    public final Board build(Random random) {
        Objects.requireNonNull(random, "random");
        Square[][] squares = new Square[Board.STORAGE_SIZE][Board.STORAGE_SIZE];
        for (int row = 0; row < Board.STORAGE_SIZE; row++) {
            for (int column = 0; column < Board.STORAGE_SIZE; column++) {
                defineSquare(squares, row, column, SquareType.EMPTY,
                        SquareColor.NEUTRAL, SquareStatus.INACTIVE);
            }
        }
        createCommonSquares(squares);
        createStartsAndArrows(squares);
        createHomesAndCastles(squares);
        placeSpecialSquares(squares, random);
        return new Board(squares);
    }

    protected abstract void createCommonSquares(Square[][] squares);

    protected abstract void createStartsAndArrows(Square[][] squares);

    protected abstract void createHomesAndCastles(Square[][] squares);

    protected abstract void placeSpecialSquares(Square[][] squares, Random random);

    protected final void defineSquare(Square[][] squares, int row, int column,
                                      SquareType type, SquareColor color, SquareStatus status) {
        defineSquare(squares, row, column, type, color, status, null);
    }

    /** Supports future Question squares without changing Board's occupancy management. */
    protected final void defineSquare(Square[][] squares, int row, int column,
                                      SquareType type, SquareColor color, SquareStatus status,
                                      QuestionDifficulty questionDifficulty) {
        squares[row][column] = new Square(new Position(row, column), type, color,
                status, questionDifficulty);
    }
}
