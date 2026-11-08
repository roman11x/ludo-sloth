package Model;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Participating players and a reference to their per-game mutable board.
 * Only initial placement and read access are implemented here; move execution follows later.
 */
public final class GameState {
    private final Board board;
    private final List<PlayerSetup> players;

    private GameState(Board board, List<PlayerSetup> players) {
        this.board = Objects.requireNonNull(board, "board");
        this.players = validatePlayers(players);
        if (board.hasPieces()) {
            throw new IllegalArgumentException("Initial state requires a board without pieces");
        }
        placeInitialPieces();
    }

    public static GameState createInitial(Board board, List<PlayerSetup> players) {
        return new GameState(board, players);
    }

    public Board board() {
        return board;
    }

    public List<PlayerSetup> players() {
        return players;
    }

    /** Returns the occupants, or an empty list for an unoccupied in-bounds cell. */
    public List<Piece> piecesAt(Position position) {
        return board.piecesAt(position);
    }

    /** Returns a read-only snapshot of occupied cells, with read-only piece lists. */
    public Map<Position, List<Piece>> occupancy() {
        return board.occupancy();
    }

    private static List<PlayerSetup> validatePlayers(List<PlayerSetup> players) {
        Objects.requireNonNull(players, "players");
        if (players.size() < 2 || players.size() > 4) {
            throw new IllegalArgumentException("A game requires between 2 and 4 players");
        }

        List<PlayerSetup> copy = List.copyOf(players);
        var colors = EnumSet.noneOf(PlayerColor.class);
        var names = new HashSet<String>();
        for (PlayerSetup player : copy) {
            if (!colors.add(player.color())) {
                throw new IllegalArgumentException("Player colors must be unique");
            }
            if (!names.add(player.name().toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("Player names must be unique");
            }
        }
        return copy;
    }

    private void placeInitialPieces() {
        for (PlayerSetup player : players) {
            SquareColor color = SquareColor.fromPlayerColor(player.color());
            var slots = new ArrayList<Position>();
            for (int row = 0; row < Board.STORAGE_SIZE; row++) {
                for (int column = 0; column < Board.STORAGE_SIZE; column++) {
                    Square square = board.squareAt(new Position(row, column));
                    if (square.type() == SquareType.CASTLE
                            && square.status() == SquareStatus.ACTIVE
                            && square.associatedColor() == color) {
                        slots.add(square.position());
                    }
                }
            }
            if (slots.size() != 4) {
                throw new IllegalStateException(
                        "Expected four active Castle slots for " + player.color());
            }

            // Number pieces in row-major order: top-left, top-right, bottom-left, bottom-right.
            for (int index = 0; index < slots.size(); index++) {
                Piece piece = new Piece(player.color(), index + 1);
                board.placePiece(piece, slots.get(index));
            }
        }
    }
}
