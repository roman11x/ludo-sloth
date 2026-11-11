package Model;

import java.util.Objects;

/** Fixed piece identity. Its location is stored in the board's occupancy. */
public record Piece(PlayerColor color, int number) {
    public Piece {
        Objects.requireNonNull(color, "color");
        if (number < 1 || number > 4) {
            throw new IllegalArgumentException("Piece number must be between 1 and 4");
        }
    }
}
