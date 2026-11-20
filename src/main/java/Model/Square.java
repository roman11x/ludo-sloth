package Model;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Fixed square properties and the pieces currently occupying this square. */
public final class Square {
    private final Position position;
    private final SquareType type;
    private final SquareColor associatedColor;
    private final SquareStatus status;
    private final QuestionDifficulty questionDifficulty;
    private final List<Piece> pieces;

    public Square(Position position, SquareType type,
                  SquareColor associatedColor, SquareStatus status) {
        this(position, type, associatedColor, status, null, List.of());
    }

    /** Question squares require a difficulty; all other square types require null. */
    public Square(Position position, SquareType type, SquareColor associatedColor,
                  SquareStatus status, QuestionDifficulty questionDifficulty) {
        this(position, type, associatedColor, status, questionDifficulty, List.of());
    }

    /** Read-only view of the backing list privately owned by a Board cell. */
    Square(Position position, SquareType type, SquareColor associatedColor,
           SquareStatus status, List<Piece> pieces) {
        this(position, type, associatedColor, status, null, pieces);
    }

    Square(Position position, SquareType type, SquareColor associatedColor,
           SquareStatus status, QuestionDifficulty questionDifficulty, List<Piece> pieces) {
        this.position = Objects.requireNonNull(position, "position");
        this.type = Objects.requireNonNull(type, "type");
        this.associatedColor = Objects.requireNonNull(associatedColor,
                "Use NEUTRAL for an unassociated square");
        this.status = Objects.requireNonNull(status, "status");
        if ((type == SquareType.QUESTION) != (questionDifficulty != null)) {
            throw new IllegalArgumentException(
                    "Question difficulty is required exactly for QUESTION squares");
        }
        this.questionDifficulty = questionDifficulty;
        this.pieces = Collections.unmodifiableList(Objects.requireNonNull(pieces, "pieces"));
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

    /** Returns null for squares that are not QUESTION squares. */
    public QuestionDifficulty questionDifficulty() {
        return questionDifficulty;
    }

    /** Read-only snapshot; changing occupancy is coordinated by Board. */
    public List<Piece> pieces() {
        return List.copyOf(pieces);
    }

}
