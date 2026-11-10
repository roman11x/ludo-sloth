package Model;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class BoardOccupancyAccessTest {
    @Test
    void retainedSquareReflectsBoardChangesWhileReturnedOccupancyRemainsAnImmutableSnapshot() {
        Board board = Board.createEasy(new Random(42));
        Position source = new Position(8, 3);
        Position destination = new Position(8, 4);
        Square square = board.squareAt(source);
        Piece piece = new Piece(PlayerColor.GREEN, 1);
        List<Piece> emptySnapshot = square.pieces();
        board.placePiece(piece, source);
        List<Piece> occupiedSnapshot = square.pieces();
        assertEquals(List.of(piece), occupiedSnapshot);
        assertTrue(emptySnapshot.isEmpty());
        assertThrows(UnsupportedOperationException.class, occupiedSnapshot::clear);
        assertThrows(UnsupportedOperationException.class, () -> square.pieces().add(piece));
        board.movePiece(piece, source, destination);
        assertTrue(square.pieces().isEmpty());
        assertEquals(List.of(piece), occupiedSnapshot);
        assertEquals(List.of(piece), board.piecesAt(destination));
        board.removePiece(piece, destination);
        assertFalse(board.hasPieces());
    }

    @Test
    void failedPlacementAndRelocationPreserveUniquePieceLocation() {
        Board board = Board.createEasy(new Random(42));
        Piece piece = new Piece(PlayerColor.GREEN, 1);
        Position source = new Position(8, 3);
        Position other = new Position(8, 4);
        board.placePiece(piece, source);
        var before = board.occupancy();
        assertThrows(IllegalArgumentException.class, () -> board.placePiece(piece, other));
        assertThrows(IllegalArgumentException.class, () -> board.movePiece(piece, source, new Position(0, 0)));
        assertThrows(IllegalArgumentException.class, () -> board.movePiece(piece, other, source));
        assertEquals(before, board.occupancy());
        assertEquals(1, board.occupancy().values().stream().mapToInt(List::size).sum());
    }
}
