package Model;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class BoardLayoutTest {
    @Test
    void reusingALayoutCreatesIndependentBoardsAndDoesNotResetAnExistingGame() {
        BoardLayout layout = new EasyLayout();
        Board first = layout.build(new Random(42));
        Position start = first.startFor(PlayerColor.GREEN);
        Piece piece = new Piece(PlayerColor.GREEN, 1);
        first.placePiece(piece, start);

        Board second = layout.build(new Random(42));
        assertNotSame(first, second);
        assertEquals(List.of(piece), first.piecesAt(start));
        assertFalse(second.hasPieces());
        for (int row = 0; row < Board.STORAGE_SIZE; row++) {
            for (int column = 0; column < Board.STORAGE_SIZE; column++) {
                Position position = new Position(row, column);
                Square original = first.squareAt(position);
                Square rebuilt = second.squareAt(position);
                assertNotSame(original, rebuilt);
                assertEquals(original.position(), rebuilt.position());
                assertEquals(original.type(), rebuilt.type());
                assertEquals(original.associatedColor(), rebuilt.associatedColor());
                assertEquals(original.status(), rebuilt.status());
                assertEquals(original.questionDifficulty(), rebuilt.questionDifficulty());
            }
        }
        second.placePiece(piece, start);
        first.removePiece(piece, start);
        assertEquals(List.of(piece), second.piecesAt(start));
    }
}
