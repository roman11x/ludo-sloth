package Model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Movement validation; fixed routes and dice distance come from MoveGenerator. */
public class MoveRules {
    /** BRD-009: protection is independent of square color. */
    public boolean isProtectedFromCapture(Square square) {
        Objects.requireNonNull(square, "square");
        return switch (square.type()) {
            case PROTECTED, START, FINISHING -> true;
            case EMPTY, NORMAL, ARROW, CASTLE, HOME_PATH -> false;
        };
    }

    /**
     * Filters the complete candidate list for one player's roll. Full moves
     * take precedence over all shortened moves (FR-049). Does not change the board.
     */
    public List<MoveCandidate> legalMoves(Board board, List<MoveCandidate> candidates) {
        Objects.requireNonNull(board, "board");
        List<MoveCandidate> snapshot = List.copyOf(candidates);
        requireOnePlayer(snapshot);
        List<MoveCandidate> fullMoves = new ArrayList<>();
        List<MoveCandidate> shortenedMoves = new ArrayList<>();
        for (MoveCandidate candidate : snapshot) {
            Optional<MoveCandidate> evaluated = evaluateMove(board, candidate);
            if (evaluated.isEmpty()) continue;
            MoveCandidate move = evaluated.get();
            if (move.path().size() == candidate.path().size()) fullMoves.add(move);
            else shortenedMoves.add(move);
        }
        return List.copyOf(fullMoves.isEmpty() ? shortenedMoves : fullMoves);
    }

    /**
     * Revalidates an already selected path with the SAME rules as legalMoves.
     * No shortening is accepted here. The caller must select from the complete
     * legalMoves result to enforce turn-wide preference for full moves.
     * Route order and dice eligibility remain MoveGenerator's responsibility.
     */
    public boolean isLegalMove(Board board, Piece piece, Position source, List<Position> path) {
        Objects.requireNonNull(board, "board");
        Objects.requireNonNull(piece, "piece");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(path, "path");
        if (path.isEmpty() || path.contains(source)) return false;
        MoveCandidate candidate = new MoveCandidate(piece, source, path);
        return evaluateMove(board, candidate).filter(candidate::equals).isPresent();
    }

    private void requireOnePlayer(List<MoveCandidate> candidates) {
        if (candidates.isEmpty()) return;
        PlayerColor color = candidates.getFirst().piece().color();
        if (candidates.stream().anyMatch(candidate -> candidate.piece().color() != color)) {
            throw new IllegalArgumentException("Candidates must belong to one player");
        }
    }

    /** One evaluation path for generation filtering and execution revalidation. */
    private Optional<MoveCandidate> evaluateMove(Board board, MoveCandidate candidate) {
        if (!validSource(board, candidate)) return Optional.empty();
        Optional<List<Position>> scanned = pathBeforeFirstBlock(board, candidate);
        if (scanned.isEmpty() || scanned.get().isEmpty()) return Optional.empty();
        List<Position> path = scanned.get();
        if (!hasRoomFor(board.squareAt(path.getLast()), candidate.piece())) return Optional.empty();
        return Optional.of(new MoveCandidate(candidate.piece(), candidate.source(), path));
    }

    private boolean validSource(Board board, MoveCandidate candidate) {
        if (!board.isActive(candidate.source())) return false;
        Square source = board.squareAt(candidate.source());
        if (!canLeaveSquare(source, candidate.piece()) || source.type() == SquareType.FINISHING) return false;
        if (source.type() == SquareType.CASTLE) return validCastleExit(board, source, candidate);
        return allowsPathEntry(source, candidate.piece().color());
    }

    /** The generator has already checked the Standard-die six. */
    private boolean validCastleExit(Board board, Square source, MoveCandidate candidate) {
        PlayerColor color = candidate.piece().color();
        return source.associatedColor() == SquareColor.fromPlayerColor(color)
                && candidate.path().size() == 1
                && candidate.destination().equals(board.startFor(color));
    }

    /**
     * Empty Optional means invalid terrain/access. A present list is the ordered
     * prefix before a block (possibly empty), or the full unobstructed path.
     * Source is excluded; a Finish square can only be the destination.
     */
    private Optional<List<Position>> pathBeforeFirstBlock(Board board, MoveCandidate candidate) {
        List<Position> prefix = new ArrayList<>();
        List<Position> path = candidate.path();
        PlayerColor color = candidate.piece().color();
        for (int i = 0; i < path.size(); i++) {
            Position position = path.get(i);
            if (!board.isActive(position)) return Optional.empty();
            Square square = board.squareAt(position);
            if (!allowsPathEntry(square, color)) return Optional.empty();
            if (square.type() == SquareType.FINISHING && i != path.size() - 1) return Optional.empty();
            if (isBlockedFor(square, color)) return Optional.of(List.copyOf(prefix));
            prefix.add(position);
        }
        return Optional.of(List.copyOf(prefix));
    }

    private boolean allowsPathEntry(Square square, PlayerColor color) {
        return switch (square.type()) {
            case EMPTY, CASTLE -> false;
            case HOME_PATH, FINISHING -> square.associatedColor() == SquareColor.fromPlayerColor(color);
            case NORMAL, START, ARROW, PROTECTED -> true;
        };
    }

    private boolean hasRoomFor(Square square, Piece piece) {
        return !square.pieces().contains(piece)
                && square.pieces().stream().filter(p -> p.color() == piece.color()).count() < 4;
    }

    /** FR-047: derived from occupancy, so the block dissolves when fewer than two remain. */
    public boolean isDoubleBlock(Square square, PlayerColor owner) {
        Objects.requireNonNull(square, "square");
        Objects.requireNonNull(owner, "owner");
        return square.pieces().stream().filter(piece -> piece.color() == owner).count() >= 2;
    }

    public boolean isBlockedFor(Square square, PlayerColor movingColor) {
        Objects.requireNonNull(square, "square");
        Objects.requireNonNull(movingColor, "movingColor");
        for (PlayerColor owner : PlayerColor.values()) {
            if (owner != movingColor && isDoubleBlock(square, owner)) return true;
        }
        return false;
    }

    /** FR-054/057: blocks on the source square never trap an existing occupant. */
    public boolean canLeaveSquare(Square source, Piece piece) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(piece, "piece");
        return source.status() == SquareStatus.ACTIVE && source.pieces().contains(piece);
    }
}
