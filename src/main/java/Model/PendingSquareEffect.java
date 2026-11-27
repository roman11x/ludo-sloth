package Model;

import java.util.Objects;

/** A single-use request to resolve a landing; player owns the turn, piece may belong to an opponent. */
public record PendingSquareEffect(long id, Player player, Piece piece, Square square) {
    public PendingSquareEffect {
        if (id <= 0) throw new IllegalArgumentException("Effect id must be positive");
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(piece, "piece");
        Objects.requireNonNull(square, "square");
        if (square.type() != SquareType.QUESTION && square.type() != SquareType.SURPRISE) {
            throw new IllegalArgumentException("Only Question and Surprise landings request effects");
        }
    }
}
