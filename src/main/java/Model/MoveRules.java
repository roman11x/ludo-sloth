package Model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Movement rules: Castle exit, legal moves, Home Path entry, capture and Double Blocks.
 */
public class MoveRules {
    /**
     * BRD-009: protected, Start and Finishing Squares prevent capture,
     * regardless of their associated color. This does not determine move
     * legality, resolve Double Blocks or execute a capture.
     */
    public boolean isProtectedFromCapture(Square square) {
        Objects.requireNonNull(square, "square");
        return switch (square.type()) {
            case PROTECTED, START, FINISHING -> true;
            default -> false;
        };
    }

    /**
     * Filters the COMPLETE list from MoveGenerator for one player's single roll.
     * The generator supplies route order, dice distance, Castle-exit eligibility
     * and exact Finish distance. This method checks current occupancy and square
     * access without moving or capturing pieces. Turn ownership is the caller's
     * responsibility. Do not call separately for each piece: FR-049 requires
     * considering all full moves before allowing a shortened blocked move.
     */
    public List<MoveCandidate> legalMoves(Board board, List<MoveCandidate> candidates) {
        Objects.requireNonNull(board, "board");
        List<MoveCandidate> snapshot = List.copyOf(candidates);
        List<MoveCandidate> fullMoves = new ArrayList<>();
        List<MoveCandidate> shortenedMoves = new ArrayList<>();
        PlayerColor movingColor = snapshot.isEmpty() ? null : snapshot.getFirst().piece().color();

        for (MoveCandidate candidate : snapshot) {
            Piece piece = candidate.piece();
            if (piece.color() != movingColor) {
                throw new IllegalArgumentException("Candidates must belong to one player");
            }
            if (!board.isActive(candidate.source())) continue;
            Square source = board.squareAt(candidate.source());
            if (!canLeaveSquare(source, piece) || source.type() == SquareType.FINISHING) continue;
            if (source.type() == SquareType.CASTLE
                    && (source.associatedColor() != SquareColor.fromPlayerColor(movingColor)
                        || candidate.path().size() != 1
                        || !candidate.destination().equals(board.startFor(movingColor)))) continue;

            boolean invalid = false;
            int blockIndex = -1;
            Square destination = null;
            // Each entered square is checked once, including the final square.
            for (int i = 0; i < candidate.path().size(); i++) {
                Position position = candidate.path().get(i);
                if (!board.isActive(position)) {
                    invalid = true;
                    break;
                }
                Square square = board.squareAt(position);
                if (!allowsPathEntry(square, movingColor)
                        || (square.type() == SquareType.FINISHING
                            && i != candidate.path().size() - 1)) {
                    invalid = true;
                    break;
                }
                if (isBlockedFor(square, movingColor)) {
                    blockIndex = i;
                    break;
                }
                destination = square;
            }
            if (invalid || destination == null) continue;
            // FR-050: limit is per color, including protected mixed-color squares.
            if (destination.pieces().stream().filter(p -> p.color() == movingColor).count() >= 4) continue;
            if (blockIndex < 0) {
                fullMoves.add(candidate);
            } else {
                shortenedMoves.add(new MoveCandidate(piece, candidate.source(),
                        candidate.path().subList(0, blockIndex)));
            }
        }
        return List.copyOf(fullMoves.isEmpty() ? shortenedMoves : fullMoves);
    }

    /** Private Home/Finish cells belong to their color; Castle is not a path cell. */
    private boolean allowsPathEntry(Square square, PlayerColor movingColor) {
        return switch (square.type()) {
            case EMPTY, CASTLE -> false;
            case HOME_PATH, FINISHING ->
                    square.associatedColor() == SquareColor.fromPlayerColor(movingColor);
            default -> true;
        };
    }

    /** FR-047: computed from current occupancy, so leaving can dissolve the block. */
    public boolean isDoubleBlock(Square square, PlayerColor owner) {
        Objects.requireNonNull(square, "square");
        Objects.requireNonNull(owner, "owner");
        return square.pieces().stream().filter(piece -> piece.color() == owner).count() >= 2;
    }

    /** FR-048: protection and square color do not override an opponent's block. */
    public boolean isBlockedFor(Square square, PlayerColor movingColor) {
        Objects.requireNonNull(square, "square");
        Objects.requireNonNull(movingColor, "movingColor");
        for (PlayerColor owner : PlayerColor.values()) {
            if (owner != movingColor && isDoubleBlock(square, owner)) return true;
        }
        return false;
    }

    /** FR-054/057: even an opponent already sharing a blocked square may leave. */
    public boolean canLeaveSquare(Square source, Piece piece) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(piece, "piece");
        return source.status() == SquareStatus.ACTIVE && source.pieces().contains(piece);
    }

    /** Checks blocking and activity only; other movement rules are checked separately. */
    public boolean canPassThrough(Square square, PlayerColor movingColor) {
        return !isBlockedFor(square, movingColor) && square.status() == SquareStatus.ACTIVE;
    }

    /** FR-050/056: up to four of one color; no entry onto an opponent's block. */
    public boolean canLandOn(Square square, Piece piece) {
        Objects.requireNonNull(piece, "piece");
        return canPassThrough(square, piece.color())
                && !square.pieces().contains(piece)
                && square.pieces().stream().filter(p -> p.color() == piece.color()).count() < 4;
    }

    /**
     * Checks a proposed path in traversal order, excluding the source and including
     * the destination. Works for forward and backward movement. Does not validate
     * route membership, dice, capture, Home entry or Finish completion.
     */
    public boolean canMoveWithoutDoubleBlock(Board board, Piece piece,
                                             Position source, List<Position> path) {
        Objects.requireNonNull(board, "board");
        Objects.requireNonNull(piece, "piece");
        Objects.requireNonNull(path, "path");
        if (path.isEmpty() || !canLeaveSquare(board.squareAt(source), piece)) return false;
        for (Position position : path) {
            if (source.equals(position) || !board.isActive(position)
                    || isBlockedFor(board.squareAt(position), piece.color())) return false;
        }
        return canLandOn(board.squareAt(path.getLast()), piece);
    }

    /**
     * FR-049: prefix ending immediately before the first opposing block.
     * The turn's move selector must permit a shortened move ONLY when no piece
     * has a full legal move. This helper does not make that turn-wide decision.
     */
    public List<Position> pathBeforeOpponentBlock(Board board, PlayerColor movingColor,
                                                 List<Position> path) {
        Objects.requireNonNull(board, "board");
        Objects.requireNonNull(movingColor, "movingColor");
        List<Position> snapshot = List.copyOf(path);
        for (int i = 0; i < snapshot.size(); i++) {
            if (isBlockedFor(board.squareAt(snapshot.get(i)), movingColor)) {
                return List.copyOf(snapshot.subList(0, i));
            }
        }
        return snapshot;
    }
}
