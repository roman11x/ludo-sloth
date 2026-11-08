package Model;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class BoardOccupancyTest {
    private final Board board = Board.createEasy(new Random(42));

    @Test
    void squareHoldsSeveralPiecesAndReadOnlySnapshotsDoNotChangeAfterRemoval() {
        Position position = new Position(8, 4);
        Square square = board.squareAt(position);
        var definition = List.of(square.position(), square.type(),
                square.associatedColor(), square.status());
        Piece green = new Piece(PlayerColor.GREEN, 1);
        Piece blue = new Piece(PlayerColor.BLUE, 1);
        assertFalse(board.hasPieces());
        board.placePiece(green, position);
        board.placePiece(blue, position);
        var snapshot = board.occupancy();
        var pieces = square.pieces();
        assertEquals(List.of(green, blue), pieces);
        assertEquals(pieces, board.piecesAt(position));
        assertThrows(UnsupportedOperationException.class, pieces::clear);
        assertThrows(UnsupportedOperationException.class, snapshot::clear);
        assertThrows(UnsupportedOperationException.class, () -> snapshot.get(position).clear());
        board.removePiece(green, position);
        assertEquals(List.of(blue), square.pieces());
        assertEquals(List.of(green, blue), snapshot.get(position));
        assertEquals(definition, List.of(square.position(), square.type(),
                square.associatedColor(), square.status()));
        board.removePiece(blue, position);
        assertTrue(board.occupancy().isEmpty());
        assertFalse(board.hasPieces());
    }

    @Test
    void rejectsDuplicateAndInactivePlacementsAndIncorrectRemovalWithoutLosingPieces() {
        Piece piece = new Piece(PlayerColor.GREEN, 1);
        Position source = new Position(3, 3);
        Position target = new Position(8, 3);
        board.placePiece(piece, source);
        assertThrows(IllegalArgumentException.class, () -> board.placePiece(piece, target));
        assertThrows(IllegalArgumentException.class, () -> board.removePiece(piece, target));
        assertThrows(IllegalArgumentException.class,
                () -> board.placePiece(new Piece(PlayerColor.BLUE, 1), new Position(2, 2)));
        assertThrows(IllegalArgumentException.class,
                () -> board.placePiece(new Piece(PlayerColor.BLUE, 1), new Position(19, 0)));
        assertEquals(List.of(piece), board.piecesAt(source));
        assertTrue(board.piecesAt(target).isEmpty());
        assertEquals(1, board.occupancy().size());
    }
}
