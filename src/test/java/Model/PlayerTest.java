package Model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PlayerTest {
    @Test
    void createsExactlyFourDistinctNumberedPiecesOfThePlayersColor() {
        for (PlayerColor color : PlayerColor.values()) {
            Player player = new Player("Razi", color);
            assertEquals("Razi", player.name());
            assertEquals(color, player.color());
            assertEquals(4, player.pieces().size());
            assertEquals(4L, player.pieces().stream().distinct().count());
            assertEquals(List.of(1, 2, 3, 4), player.pieces().stream().map(Piece::number).toList());
            assertTrue(player.pieces().stream().allMatch(piece -> piece.color() == color));
        }
    }

    @Test
    void buildsFromSetupAndBothConstructorsUseTheValidatedName() {
        PlayerSetup setup = new PlayerSetup("  Razi  ", PlayerColor.GREEN);
        Player fromSetup = new Player(setup);
        Player fromFields = new Player("  Razi  ", PlayerColor.GREEN);
        assertEquals("Razi", fromSetup.name());
        assertEquals(setup.color(), fromSetup.color());
        assertEquals(fromSetup.name(), fromFields.name());
        assertEquals(fromSetup.color(), fromFields.color());
        assertEquals(fromSetup.pieces(), fromFields.pieces());
    }

    @Test
    void piecesCannotBeAddedRemovedOrReplaced() {
        Player player = new Player("Razi", PlayerColor.GREEN);
        assertThrows(UnsupportedOperationException.class,
                () -> player.pieces().add(new Piece(PlayerColor.RED, 1)));
        assertThrows(UnsupportedOperationException.class, () -> player.pieces().remove(0));
        assertThrows(UnsupportedOperationException.class,
                () -> player.pieces().set(0, new Piece(PlayerColor.RED, 1)));
        assertEquals(4, player.pieces().size());
    }

    @Test
    void rejectsMissingSetupInvalidNamesAndMissingColor() {
        assertThrows(NullPointerException.class, () -> new Player((PlayerSetup) null));
        assertThrows(IllegalArgumentException.class, () -> new Player(null, PlayerColor.GREEN));
        assertThrows(IllegalArgumentException.class, () -> new Player("   ", PlayerColor.GREEN));
        assertThrows(IllegalArgumentException.class, () -> new Player("Ra,zi", PlayerColor.GREEN));
        assertThrows(IllegalArgumentException.class, () -> new Player("Razi", null));
    }
}
