package Model;

import java.util.List;
import java.util.Objects;

/** A proposed move, not yet approved by movement rules. */
public record MoveCandidate(Piece piece, Position source, List<Position> path) {
    public MoveCandidate {
        Objects.requireNonNull(piece, "piece");
        Objects.requireNonNull(source, "source");
        path = List.copyOf(path);
        if (path.isEmpty() || path.contains(source)) {
            throw new IllegalArgumentException("Path must be nonempty and exclude the source");
        }
    }

    /** The path includes its destination; no second destination value is stored. */
    public Position destination() {
        return path.getLast();
    }
}
