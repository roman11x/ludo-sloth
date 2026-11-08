package Model;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class BoardTest {
    private final Board board = Board.createEasy(new Random(42));

    @Test
    void all361CoordinatesContainMatchingSquaresIncluding265InactiveCells() {
        int inactive = 0;
        for (int row = 0; row < 19; row++) {
            for (int column = 0; column < 19; column++) {
                Position position = new Position(row, column);
                Square square = board.squareAt(position);
                assertNotNull(square);
                assertEquals(position, square.position());
                assertNotNull(square.type());
                assertNotNull(square.status());
                assertNotNull(square.associatedColor());
                if (square.status() == SquareStatus.INACTIVE) {
                    inactive++;
                    assertFalse(board.isActive(position));
                    if (square.type() == SquareType.EMPTY) {
                        assertEquals(SquareColor.NEUTRAL, square.associatedColor());
                    } else {
                        assertEquals(SquareType.CASTLE, square.type());
                        assertNotEquals(SquareColor.NEUTRAL, square.associatedColor());
                    }
                } else {
                    assertTrue(board.isActive(position));
                    assertTrue(row >= 2 && row <= 16);
                    assertTrue(column >= 2 && column <= 16);
                }
            }
        }
        assertEquals(265, inactive);
        assertEquals(44, countSquares(SquareType.NORMAL));
        assertEquals(4, countSquares(SquareType.PROTECTED));
        assertEquals(4, countSquares(SquareType.START));
        assertEquals(4, countSquares(SquareType.ARROW));
        assertEquals(20, countSquares(SquareType.HOME_PATH));
        assertEquals(4, countSquares(SquareType.FINISHING));
        assertEquals(144, countSquares(SquareType.CASTLE));
        assertEquals(137, countSquares(SquareType.EMPTY));
    }

    @Test
    void fixesStartsAndArrowsAtTheAgreedCoordinates() {
        var starts = Map.of(
                PlayerColor.GREEN, new Position(8, 3),
                PlayerColor.YELLOW, new Position(3, 10),
                PlayerColor.BLUE, new Position(10, 15),
                PlayerColor.RED, new Position(15, 8));
        var arrows = Map.of(
                PlayerColor.GREEN, new Position(9, 2),
                PlayerColor.YELLOW, new Position(2, 9),
                PlayerColor.BLUE, new Position(9, 16),
                PlayerColor.RED, new Position(16, 9));
        for (PlayerColor color : PlayerColor.values()) {
            assertEquals(starts.get(color), board.startFor(color));
            assertEquals(arrows.get(color), board.arrowFor(color));
            SquareColor squareColor = SquareColor.valueOf(color.name());
            assertSquare(starts.get(color), SquareType.START, squareColor);
            assertSquare(arrows.get(color), SquareType.ARROW, squareColor);
        }
    }

    @Test
    void definesHomeCellsAndSeparateGoalsForEveryColor() {
        var homes = Map.of(
                SquareColor.GREEN, List.of(new Position(9, 3), new Position(9, 4),
                        new Position(9, 5), new Position(9, 6), new Position(9, 7)),
                SquareColor.YELLOW, List.of(new Position(3, 9), new Position(4, 9),
                        new Position(5, 9), new Position(6, 9), new Position(7, 9)),
                SquareColor.BLUE, List.of(new Position(9, 15), new Position(9, 14),
                        new Position(9, 13), new Position(9, 12), new Position(9, 11)),
                SquareColor.RED, List.of(new Position(15, 9), new Position(14, 9),
                        new Position(13, 9), new Position(12, 9), new Position(11, 9)));
        homes.forEach((color, positions) -> positions.forEach(position ->
                assertSquare(position, SquareType.HOME_PATH, color)));
        assertSquare(new Position(9, 8), SquareType.FINISHING, SquareColor.GREEN);
        assertSquare(new Position(8, 9), SquareType.FINISHING, SquareColor.YELLOW);
        assertSquare(new Position(9, 10), SquareType.FINISHING, SquareColor.BLUE);
        assertSquare(new Position(10, 9), SquareType.FINISHING, SquareColor.RED);
    }

    @Test
    void definesThe16CastleSlotsFromTheUsersTable() {
        var castles = Map.of(
                SquareColor.GREEN, List.of(new Position(3, 3), new Position(3, 6),
                        new Position(6, 3), new Position(6, 6)),
                SquareColor.YELLOW, List.of(new Position(3, 12), new Position(3, 15),
                        new Position(6, 12), new Position(6, 15)),
                SquareColor.RED, List.of(new Position(12, 3), new Position(12, 6),
                        new Position(15, 3), new Position(15, 6)),
                SquareColor.BLUE, List.of(new Position(12, 12), new Position(12, 15),
                        new Position(15, 12), new Position(15, 15)));
        castles.forEach((color, positions) -> positions.forEach(position -> {
            assertSquare(position, SquareType.CASTLE, color);
            assertTrue(board.isActive(position));
        }));
    }

    @Test
    void preservesExactlyThe56SharedCoordinatesAcrossRandomLayouts() {
        for (int seed = 0; seed < 10; seed++) {
            Board layout = Board.createEasy(new Random(seed));
            int shared = 0;
            int protectedCount = 0;
            for (int row = 0; row < 19; row++) {
                for (int column = 0; column < 19; column++) {
                    Square square = layout.squareAt(new Position(row, column));
                    boolean isShared = switch (square.type()) {
                        case NORMAL, START, ARROW, PROTECTED -> true;
                        default -> false;
                    };
                    // Independent geometric predicate for the four arms.
                    boolean expected =
                            ((row == 8 || row == 10)
                                    && ((column >= 2 && column <= 7)
                                        || (column >= 11 && column <= 16)))
                            || ((column == 8 || column == 10)
                                    && ((row >= 2 && row <= 7)
                                        || (row >= 11 && row <= 16)))
                            || ((row == 2 || row == 16) && column == 9)
                            || (row == 9 && (column == 2 || column == 16))
                            || ((row == 8 || row == 10) && (column == 8 || column == 10));
                    assertEquals(expected, isShared, square.position().toString());
                    if (isShared) shared++;
                    if (square.type() == SquareType.PROTECTED) {
                        protectedCount++;
                        assertEquals(SquareColor.NEUTRAL, square.associatedColor());
                        assertNotEquals(SquareType.START,
                                board.squareAt(square.position()).type());
                        assertNotEquals(SquareType.ARROW,
                                board.squareAt(square.position()).type());
                    }
                }
            }
            assertEquals(56, shared);
            assertEquals(4, protectedCount);
        }
    }

    @Test
    void returnsInactiveSquaresButRejectsOutOfBoundsCoordinates() {
        for (Position position : List.of(new Position(0, 0), new Position(2, 2),
                new Position(2, 16), new Position(16, 2), new Position(16, 16),
                new Position(9, 9))) {
            assertTrue(board.isWithinBounds(position));
            assertFalse(board.isActive(position));
            assertEquals(SquareStatus.INACTIVE, board.squareAt(position).status());
            if (position.row() == 9 || position.row() == 8 || position.row() == 10) {
                assertSquare(position, SquareType.EMPTY, SquareColor.NEUTRAL);
            }
        }
        for (Position position : List.of(new Position(-1, 8), new Position(19, 8),
                new Position(8, -1), new Position(8, 19))) {
            assertFalse(board.isWithinBounds(position));
            assertFalse(board.isActive(position));
            assertThrows(IllegalArgumentException.class, () -> board.squareAt(position));
        }
    }

    @Test
    void seededCreationIsReproducibleAndBoardInstancesAreIndependent() {
        Board same = Board.createEasy(new Random(42));
        Board different = Board.createEasy(new Random(43));
        boolean changed = false;
        for (int row = 0; row < 19; row++) {
            for (int column = 0; column < 19; column++) {
                Position position = new Position(row, column);
                Square expected = board.squareAt(position);
                Square actual = same.squareAt(position);
                assertEquals(expected.position(), actual.position());
                assertEquals(expected.type(), actual.type());
                assertEquals(expected.associatedColor(), actual.associatedColor());
                assertEquals(expected.status(), actual.status());
                changed |= expected.type() != different.squareAt(position).type();
            }
        }
        assertTrue(changed);
        assertThrows(NullPointerException.class, () -> Board.createEasy(null));
        assertThrows(NullPointerException.class, () -> board.squareAt(null));
        assertThrows(NullPointerException.class,
                () -> new Square(new Position(8, 4), SquareType.NORMAL, null, SquareStatus.ACTIVE));
        assertEquals(4, countSquares(SquareType.PROTECTED));
    }

    @Test
    void entireCastleRegionsHaveTheirColorButOnlyFourSlotsAreActive() {
        int[][] corners = {{2, 2}, {2, 11}, {11, 11}, {11, 2}};
        SquareColor[] colors = {SquareColor.GREEN, SquareColor.YELLOW,
                SquareColor.BLUE, SquareColor.RED};
        for (int i = 0; i < corners.length; i++) {
            int active = 0;
            int inactive = 0;
            for (int row = corners[i][0]; row < corners[i][0] + 6; row++) {
                for (int column = corners[i][1]; column < corners[i][1] + 6; column++) {
                    Position position = new Position(row, column);
                    assertSquare(position, SquareType.CASTLE, colors[i]);
                    if (board.isActive(position)) active++;
                    else inactive++;
                }
            }
            assertEquals(4, active);
            assertEquals(32, inactive);
        }
    }

    @Test
    void neutralEmptyCellsAndPlayableCellsHaveTheCorrectStatus() {
        for (int row = 0; row < 19; row++) {
            for (int column = 0; column < 19; column++) {
                Square square = board.squareAt(new Position(row, column));
                if (square.type() == SquareType.EMPTY) {
                    assertEquals(SquareStatus.INACTIVE, square.status());
                    assertEquals(SquareColor.NEUTRAL, square.associatedColor());
                } else if (square.type() != SquareType.CASTLE) {
                    assertEquals(SquareStatus.ACTIVE, square.status());
                }
            }
        }
        assertThrows(NullPointerException.class, () -> new Square(
                new Position(8, 4), SquareType.NORMAL, SquareColor.NEUTRAL, null));
    }

    @Test
    void theFourInnerCornersAreNormalNeutralAndActive() {
        for (int seed = 0; seed < 10; seed++) {
            Board layout = Board.createEasy(new Random(seed));
            for (Position position : List.of(new Position(8, 8), new Position(8, 10),
                    new Position(10, 8), new Position(10, 10))) {
                Square square = layout.squareAt(position);
                assertEquals(SquareType.NORMAL, square.type());
                assertEquals(SquareColor.NEUTRAL, square.associatedColor());
                assertEquals(SquareStatus.ACTIVE, square.status());
            }
        }
    }

    private void assertSquare(Position position, SquareType type, SquareColor color) {
        Square square = board.squareAt(position);
        assertEquals(type, square.type(), position.toString());
        assertEquals(color, square.associatedColor(), position.toString());
    }

    private int countSquares(SquareType type) {
        int count = 0;
        for (int row = 0; row < 19; row++) {
            for (int column = 0; column < 19; column++) {
                if (board.squareAt(new Position(row, column)).type() == type) count++;
            }
        }
        return count;
    }
}
