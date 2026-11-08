package Model;

import java.util.Objects;

/**
 * A piece's fixed identity: its player color and number within that color (1-4).
 * Position is stored separately in the game state.
 */
public record Piece(PlayerColor color, int number) {
    public Piece {
        Objects.requireNonNull(color, "color");
        if (number < 1 || number > 4) {
            throw new IllegalArgumentException("Piece number must be between 1 and 4");
        }
    }
}
