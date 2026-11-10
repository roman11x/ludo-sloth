package Model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Generates forward Easy candidates from fixed routes and board occupancy.
 * Does not move pieces or filter captures, opposing blocks or protected squares.
 * Castle exit is one move to Start on a Standard 6; overshoots and completed
 * pieces have no candidate. Other difficulty/dice configurations are not handled.
 */
public final class MoveGenerator {
    private final Map<PlayerColor, PlayerRoute> routes;

    public MoveGenerator(Map<PlayerColor, PlayerRoute> routes) {
        this.routes = Map.copyOf(routes);
        this.routes.forEach((color, route) -> {
            if (route.color() != color) {
                throw new IllegalArgumentException("Route color does not match its key");
            }
        });
    }

    /**
     * The caller supplies the current player's color and Standard-die result.
     * Only that color's placed pieces are considered, so 2-4 player games use
     * the same method. Candidates are returned in piece-number order.
     */
    public List<MoveCandidate> generate(Board board, PlayerColor currentPlayer, int dieResult) {
        Objects.requireNonNull(board, "board");
        Objects.requireNonNull(currentPlayer, "currentPlayer");
        if (dieResult < 1 || dieResult > 6) {
            throw new IllegalArgumentException("Standard die result must be between 1 and 6");
        }
        PlayerRoute route = routes.get(currentPlayer);
        if (route == null) throw new IllegalArgumentException("Missing route for " + currentPlayer);

        List<MoveCandidate> candidates = new ArrayList<>();
        for (var entry : board.occupancy().entrySet()) {
            Position source = entry.getKey();
            Square square = board.squareAt(source);
            for (Piece piece : entry.getValue()) {
                if (piece.color() != currentPlayer) continue;
                if (square.type() == SquareType.CASTLE) {
                    if (square.associatedColor() != SquareColor.fromPlayerColor(currentPlayer)) {
                        throw new IllegalStateException("Piece is in another color's Castle");
                    }
                    if (dieResult == 6) {
                        candidates.add(new MoveCandidate(piece, source, List.of(route.start())));
                    }
                    continue;
                }
                int sourceIndex = route.positions().indexOf(source);
                if (sourceIndex < 0) {
                    throw new IllegalStateException("Piece is outside its route: " + piece);
                }
                int destinationIndex = sourceIndex + dieResult;
                if (destinationIndex >= route.size()) continue;
                candidates.add(new MoveCandidate(piece, source,
                        route.positions().subList(sourceIndex + 1, destinationIndex + 1)));
            }
        }
        candidates.sort(Comparator.comparingInt(candidate -> candidate.piece().number()));
        return List.copyOf(candidates);
    }
}
