package Model;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class DoubleBlockTest {
    private final Board board = Board.createEasy(new Random(42));
    private final MoveRules rules = new MoveRules();
    private final Position a = new Position(8, 3);
    private final Position b = new Position(8, 4);
    private final Position c = new Position(8, 5);
    private final Position d = new Position(8, 6);

    private Piece place(PlayerColor color, int number, Position position) {
        Piece piece = new Piece(color, number);
        board.placePiece(piece, position);
        return piece;
    }

    @Test
    void secondPieceFormsBlockAndLeavingDissolvesItWithoutChangingSquareProperties() {
        Square square = board.squareAt(b);
        SquareType originalType = square.type();
        Piece first = place(PlayerColor.GREEN, 1, b);
        assertFalse(rules.isDoubleBlock(square, PlayerColor.GREEN));
        place(PlayerColor.GREEN, 2, a);
        Piece second = new Piece(PlayerColor.GREEN, 2);
        assertTrue(rules.canMoveWithoutDoubleBlock(board, second, a, List.of(b)));
        board.movePiece(second, a, b);
        assertTrue(rules.isDoubleBlock(square, PlayerColor.GREEN));
        assertTrue(rules.canMoveWithoutDoubleBlock(board, first, b, List.of(c)));
        board.movePiece(first, b, c);
        assertFalse(rules.isDoubleBlock(square, PlayerColor.GREEN));
        assertEquals(originalType, square.type());
        assertEquals(List.of(second), board.piecesAt(b));
    }

    @Test
    void twoDifferentColorsDoNotFormABlock() {
        place(PlayerColor.GREEN, 1, a);
        place(PlayerColor.RED, 1, a);
        for (PlayerColor color : PlayerColor.values()) {
            assertFalse(rules.isDoubleBlock(board.squareAt(a), color));
            assertFalse(rules.isBlockedFor(board.squareAt(a), color));
        }
    }

    @Test
    void opponentsCannotLandOnOrPassBlockInEitherDirection() {
        place(PlayerColor.RED, 1, b);
        place(PlayerColor.RED, 2, b);
        Piece green = place(PlayerColor.GREEN, 1, a);
        assertFalse(rules.canMoveWithoutDoubleBlock(board, green, a, List.of(b)));
        assertFalse(rules.canMoveWithoutDoubleBlock(board, green, a, List.of(b, c)));
        board.movePiece(green, a, c); // Arrange reverse traversal.
        assertFalse(rules.canMoveWithoutDoubleBlock(board, green, c, List.of(b, a)));
        assertEquals(List.of(green), board.piecesAt(c));
    }

    @Test
    void ownersCanPassJoinAndLeaveTheirBlock() {
        place(PlayerColor.GREEN, 1, b);
        place(PlayerColor.GREEN, 2, b);
        Piece third = place(PlayerColor.GREEN, 3, a);
        assertTrue(rules.canMoveWithoutDoubleBlock(board, third, a, List.of(b, c)));
        assertTrue(rules.canMoveWithoutDoubleBlock(board, third, a, List.of(b)));
        board.movePiece(third, a, b);
        assertTrue(rules.canMoveWithoutDoubleBlock(board, third, b, List.of(c)));
        board.movePiece(third, b, c);
        assertTrue(rules.isDoubleBlock(board.squareAt(b), PlayerColor.GREEN));
    }

    @Test
    void acceptsFourSameColorPiecesAndKeepsBlockUntilFewerThanTwoRemain() {
        for (int i = 1; i <= 4; i++) place(PlayerColor.GREEN, i, b);
        assertEquals(4, board.piecesAt(b).size());
        for (int i = 4; i >= 2; i--) {
            Piece piece = new Piece(PlayerColor.GREEN, i);
            assertTrue(rules.canMoveWithoutDoubleBlock(board, piece, b, List.of(c)));
            board.movePiece(piece, b, c);
            assertEquals(i > 2, rules.isDoubleBlock(board.squareAt(b), PlayerColor.GREEN));
        }
        assertEquals(3, board.piecesAt(c).size());
    }

    @Test
    void blockOverridesProtectionOnStartAndProtectedSquares() {
        Position protectedPosition = null;
        for (int row = 0; row < Board.STORAGE_SIZE; row++) {
            for (int column = 0; column < Board.STORAGE_SIZE; column++) {
                Position position = new Position(row, column);
                if (board.squareAt(position).type() == SquareType.PROTECTED) protectedPosition = position;
            }
        }
        assertNotNull(protectedPosition);
        for (Position position : List.of(a, protectedPosition)) {
            Piece one = place(PlayerColor.RED, 1, position);
            Piece two = place(PlayerColor.RED, 2, position);
            assertTrue(rules.isProtectedFromCapture(board.squareAt(position)));
            assertFalse(rules.canLandOn(board.squareAt(position), new Piece(PlayerColor.GREEN, 1)));
            board.removePiece(one, position);
            board.removePiece(two, position);
        }
    }

    @Test
    void existingOpponentMayLeaveAfterBlockFormsAroundIt() {
        Piece green = place(PlayerColor.GREEN, 1, a);
        place(PlayerColor.RED, 1, a);
        Piece red = place(PlayerColor.RED, 2, b);
        assertTrue(rules.canMoveWithoutDoubleBlock(board, red, b, List.of(a)));
        board.movePiece(red, b, a);
        assertTrue(rules.isBlockedFor(board.squareAt(a), PlayerColor.GREEN));
        assertTrue(rules.canMoveWithoutDoubleBlock(board, green, a, List.of(b)));
        board.movePiece(green, a, b);
        assertTrue(rules.isDoubleBlock(board.squareAt(a), PlayerColor.RED));
    }

    @Test
    void limitIsFourPerColorEvenWithAnExistingOpponent() {
        place(PlayerColor.GREEN, 1, a);
        for (int i = 1; i <= 4; i++) {
            Piece red = place(PlayerColor.RED, i, b);
            assertTrue(rules.canMoveWithoutDoubleBlock(board, red, b, List.of(a)));
            board.movePiece(red, b, a);
        }
        assertEquals(5, board.piecesAt(a).size());
        assertTrue(rules.canLeaveSquare(board.squareAt(a), new Piece(PlayerColor.GREEN, 1)));
    }

    @Test
    void returnsPrefixBeforeBlockIncludingWhenAdjacentOrAbsent() {
        place(PlayerColor.RED, 1, c);
        place(PlayerColor.RED, 2, c);
        assertEquals(List.of(b), rules.pathBeforeOpponentBlock(board, PlayerColor.GREEN, List.of(b, c, d)));
        assertEquals(List.of(), rules.pathBeforeOpponentBlock(board, PlayerColor.GREEN, List.of(c, b)));
        assertEquals(List.of(d), rules.pathBeforeOpponentBlock(board, PlayerColor.GREEN, List.of(d, c, b)));
        assertEquals(List.of(b, c, d), rules.pathBeforeOpponentBlock(board, PlayerColor.RED, List.of(b, c, d)));
        assertEquals(List.of(), rules.pathBeforeOpponentBlock(board, PlayerColor.GREEN, List.of()));
    }

    @Test
    void inactiveCellsAndIncorrectSourcesAreRejectedWithoutChangingOccupancy() {
        Piece green = place(PlayerColor.GREEN, 1, a);
        assertFalse(rules.canMoveWithoutDoubleBlock(board, green, b, List.of(c)));
        assertFalse(rules.canMoveWithoutDoubleBlock(board, green, a, List.of()));
        assertFalse(rules.canMoveWithoutDoubleBlock(board, green, a, List.of(new Position(0, 0))));
        assertThrows(IllegalArgumentException.class, () -> board.movePiece(green, a, new Position(0, 0)));
        assertThrows(IllegalArgumentException.class, () -> board.movePiece(green, b, c));
        assertThrows(IllegalArgumentException.class, () -> board.movePiece(green, a, a));
        assertEquals(List.of(green), board.piecesAt(a));
    }

    @Test
    void blocksAreBasedOnPieceOwnerForEverySquareTypeAndColor() {
        for (SquareType type : SquareType.values()) {
            if (type == SquareType.EMPTY) continue;
            for (PlayerColor owner : PlayerColor.values()) {
                Square square = new Square(b, type, SquareColor.NEUTRAL, SquareStatus.ACTIVE);
                square.addPiece(new Piece(owner, 1));
                square.addPiece(new Piece(owner, 2));
                assertTrue(rules.isDoubleBlock(square, owner));
                for (PlayerColor movingColor : PlayerColor.values()) {
                    assertEquals(movingColor != owner, rules.isBlockedFor(square, movingColor));
                }
            }
        }
    }

    @Test
    void leavingABlockStillRequiresAnUnblockedDestinationAndPath() {
        Piece green = place(PlayerColor.GREEN, 1, a);
        place(PlayerColor.GREEN, 2, a);
        place(PlayerColor.RED, 1, c);
        place(PlayerColor.RED, 2, c);
        assertTrue(rules.canMoveWithoutDoubleBlock(board, green, a, List.of(b)));
        assertFalse(rules.canMoveWithoutDoubleBlock(board, green, a, List.of(b, c)));
        assertFalse(rules.canMoveWithoutDoubleBlock(board, green, a, List.of(b, c, d)));
        assertEquals(2, board.piecesAt(a).size());
    }

    @Test
    void identityPreventsDuplicatePiecesAndRejectsInvalidNumbers() {
        Piece green = place(PlayerColor.GREEN, 1, a);
        assertThrows(IllegalArgumentException.class, () -> board.placePiece(new Piece(PlayerColor.GREEN, 1), b));
        assertThrows(IllegalArgumentException.class, () -> board.squareAt(a).addPiece(green));
        assertThrows(IllegalArgumentException.class, () -> new Piece(PlayerColor.GREEN, 0));
        assertThrows(IllegalArgumentException.class, () -> new Piece(PlayerColor.GREEN, 5));
        assertThrows(NullPointerException.class, () -> new Piece(null, 1));
        assertEquals(1, board.piecesAt(a).size());
    }
}
