package Model;

import org.junit.jupiter.api.Test;

import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class MoveRulesTest {
    private final MoveRules rules = new MoveRules();

    @Test
    void captureProtectionDependsOnSquareTypeNotItsAssociatedColor() {
        Set<SquareType> protectedTypes = Set.of(
                SquareType.PROTECTED, SquareType.START, SquareType.FINISHING);
        for (SquareType type : SquareType.values()) {
            for (SquareColor color : SquareColor.values()) {
                Square square = new Square(new Position(8, 3), type, color,
                        type == SquareType.EMPTY ? SquareStatus.INACTIVE : SquareStatus.ACTIVE);
                assertEquals(protectedTypes.contains(type), rules.isProtectedFromCapture(square),
                        type + " / " + color);
            }
        }
    }

    @Test
    void easyBoardHasTwelveProtectedLocationsAndItsArrowsAreNotProtected() {
        Board board = Board.createEasy(new Random(42));
        int protectedCount = 0;
        for (int row = 0; row < Board.STORAGE_SIZE; row++) {
            for (int column = 0; column < Board.STORAGE_SIZE; column++) {
                Square square = board.squareAt(new Position(row, column));
                if (rules.isProtectedFromCapture(square)) {
                    protectedCount++;
                    assertEquals(SquareStatus.ACTIVE, square.status());
                }
            }
        }
        assertEquals(12, protectedCount); // 4 stars + 4 Starts + 4 Finishes.
        for (PlayerColor color : PlayerColor.values()) {
            assertTrue(rules.isProtectedFromCapture(board.squareAt(board.startFor(color))));
            assertFalse(rules.isProtectedFromCapture(board.squareAt(board.arrowFor(color))));
        }
    }

    @Test
    void rejectsAMissingSquare() {
        assertThrows(NullPointerException.class, () -> rules.isProtectedFromCapture(null));
    }
}
