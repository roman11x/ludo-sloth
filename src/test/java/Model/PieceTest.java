package Model;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PieceTest {
    @Test
    void eachColorHasFourDistinctIdentitiesAndMatchingIdentitiesAreEqual() {
        Set<Piece> pieces = new HashSet<>();
        for (PlayerColor color : PlayerColor.values()) {
            for (int number = 1; number <= 4; number++) {
                Piece piece = new Piece(color, number);
                assertEquals(color, piece.color());
                assertEquals(number, piece.number());
                assertTrue(pieces.add(piece));
                assertFalse(pieces.add(new Piece(color, number)));
            }
        }
        assertEquals(16, pieces.size());
    }

    @Test
    void rejectsMissingColorAndOutOfRangeNumbers() {
        assertThrows(NullPointerException.class, () -> new Piece(null, 1));
        for (int number : new int[]{Integer.MIN_VALUE, -1, 0, 5, Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class,
                    () -> new Piece(PlayerColor.GREEN, number));
        }
    }
}
