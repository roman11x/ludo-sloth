package Model;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class MediumBoardTest {
    private final Board board = Board.createMedium(new Random(42));

    @Test
    void definesThe64OuterSquaresAndKeepsTheHardBorderInactive() {
        int outerCount = 0;
        int activeCount = 0;
        for (int row = 0; row < Board.STORAGE_SIZE; row++) {
            for (int column = 0; column < Board.STORAGE_SIZE; column++) {
                Position position = new Position(row, column);
                Square square = board.squareAt(position);
                assertEquals(position, square.position());
                boolean onOuterRing = row >= 1 && row <= 17 && column >= 1 && column <= 17
                        && (row == 1 || row == 17 || column == 1 || column == 17);
                if (onOuterRing) {
                    outerCount++;
                    assertTrue(board.isActive(position));
                    assertTrue(List.of(SquareType.NORMAL, SquareType.START, SquareType.ARROW,
                            SquareType.PROTECTED, SquareType.QUESTION, SquareType.SURPRISE).contains(square.type()));
                }
                if (row == 0 || row == 18 || column == 0 || column == 18) {
                    assertEquals(SquareType.EMPTY, square.type());
                    assertEquals(SquareStatus.INACTIVE, square.status());
                }
                if (board.isActive(position)) activeCount++;
            }
        }
        assertEquals(64, outerCount);
        assertEquals(156, activeCount); // 52 inner + 64 outer + 24 Home/Finish + 16 Castle slots.
    }

    @Test
    void hasExactlyFourOuterStartsAndTwoOrderedArrowsPerColor() {
        var starts = Map.of(PlayerColor.GREEN, new Position(1, 1),
                PlayerColor.YELLOW, new Position(1, 17), PlayerColor.BLUE, new Position(17, 17),
                PlayerColor.RED, new Position(17, 1));
        var arrows = Map.of(PlayerColor.GREEN, List.of(new Position(8, 1), new Position(9, 2)),
                PlayerColor.YELLOW, List.of(new Position(1, 10), new Position(2, 9)),
                PlayerColor.BLUE, List.of(new Position(10, 17), new Position(9, 16)),
                PlayerColor.RED, List.of(new Position(17, 8), new Position(16, 9)));
        for (PlayerColor color : PlayerColor.values()) {
            assertEquals(starts.get(color), board.startFor(color));
            assertEquals(SquareColor.fromPlayerColor(color), board.squareAt(starts.get(color)).associatedColor());
            assertEquals(arrows.get(color), board.arrowsFor(color));
            assertEquals(arrows.get(color).getLast(), board.arrowFor(color));
            assertThrows(UnsupportedOperationException.class, () -> board.arrowsFor(color).clear());
        }
        long startCount = 0;
        long arrowCount = 0;
        for (int row = 0; row < Board.STORAGE_SIZE; row++) {
            for (int column = 0; column < Board.STORAGE_SIZE; column++) {
                SquareType type = board.squareAt(new Position(row, column)).type();
                if (type == SquareType.START) startCount++;
                if (type == SquareType.ARROW) arrowCount++;
            }
        }
        assertEquals(4, startCount);
        assertEquals(8, arrowCount);
    }

    @Test
    void preservesInnerGeometryCastlesAndHomeAreasButRemovesTheOldStarts() {
        Board easy = Board.createEasy(new Random(42));
        for (int row = 2; row <= 16; row++) {
            for (int column = 2; column <= 16; column++) {
                Position position = new Position(row, column);
                Square oldSquare = easy.squareAt(position);
                Square actual = board.squareAt(position);
                boolean ordinaryShared = List.of(SquareType.NORMAL, SquareType.START,
                        SquareType.PROTECTED).contains(oldSquare.type());
                if (ordinaryShared) {
                    assertTrue(List.of(SquareType.NORMAL, SquareType.PROTECTED,
                            SquareType.QUESTION, SquareType.SURPRISE).contains(actual.type()));
                } else {
                    assertEquals(oldSquare.type(), actual.type());
                }
                assertEquals(ordinaryShared ? SquareColor.NEUTRAL : oldSquare.associatedColor(),
                        actual.associatedColor());
                assertEquals(oldSquare.status(), actual.status());
                assertTrue(actual.pieces().isEmpty());
            }
        }
    }
}
