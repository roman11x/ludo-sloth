package Model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Fixed square properties and the pieces currently occupying this square. */
public final class Square {
    private final Position position;
    private final SquareType type;
    private final SquareColor associatedColor;
    private final SquareStatus status;
    private final List<Piece> pieces = new ArrayList<>();

    public Square(Position position, SquareType type,
                  SquareColor associatedColor, SquareStatus status) {
        this.position = Objects.requireNonNull(position, "position");
        this.type = Objects.requireNonNull(type, "type");
        this.associatedColor = Objects.requireNonNull(associatedColor,
                "Use NEUTRAL for an unassociated square");
        this.status = Objects.requireNonNull(status, "status");
    }

    public Position position() {
        return position;
    }

    public SquareType type() {
        return type;
    }

    public SquareColor associatedColor() {
        return associatedColor;
    }

    public SquareStatus status() {
        return status;
    }

    /** Read-only snapshot; changing occupancy is coordinated by Board. */
    public List<Piece> pieces() {
        return List.copyOf(pieces);
    }

    // Package-private operations used by Board after checking the placement.
    void addPiece(Piece piece) {
        pieces.add(Objects.requireNonNull(piece, "piece"));
    }

    boolean removePiece(Piece piece) {
        return pieces.remove(piece);
    }
}
