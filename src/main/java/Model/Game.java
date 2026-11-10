package Model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

/**
 * An initialized Easy game: board occupancy, participants, fixed routes and turns.
 * Uses the participants' existing pieces; it does not create another set of pieces.
 * Coordinates rolling, legal move selection, captures and turn transitions.
 * Connecting the UI is a separate implementation step.
 */
public final class Game {
    private final Board board;
    private final List<Player> players;
    private final Map<PlayerColor, PlayerRoute> routes;
    private final TurnManager turnManager;
    private final MoveGenerator moveGenerator;
    private final MoveRules moveRules = new MoveRules();
    private List<MoveCandidate> legalMoves = List.of();
    private final List<Player> ranking = new ArrayList<>();

    public Game(List<Player> players) {
        this(players, new StandardDie(), new Random(), new Random());
    }

    /** Separate random sources allow reproducible layouts and first-player tests. */
    public Game(List<Player> players, Die standardDie,
                Random boardRandom, Random firstPlayerRandom) {
        List<Player> participants = List.copyOf(players);
        Objects.requireNonNull(standardDie, "standardDie");
        Objects.requireNonNull(boardRandom, "boardRandom");
        Objects.requireNonNull(firstPlayerRandom, "firstPlayerRandom");
        List<PlayerSetup> setups = participants.stream()
                .map(player -> new PlayerSetup(player.name(), player.color())).toList();
        List<String> errors = SetupValidator.validate(setups, Difficulty.EASY);
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(String.join("; ", errors));
        }

        this.board = Board.createEasy(boardRandom);
        this.routes = PlayerRouteFactory.createEasy();
        this.moveGenerator = new MoveGenerator(routes);
        this.turnManager = new TurnManager(participants, standardDie, firstPlayerRandom);
        this.players = turnManager.players();
        placeInitialPieces();
    }

    private void placeInitialPieces() {
        for (Player player : players) {
            List<Position> slots = activeCastleSlots(player.color());
            if (slots.size() != player.pieces().size()) {
                throw new IllegalStateException("Castle must have one active slot per piece");
            }
            for (int i = 0; i < player.pieces().size(); i++) {
                board.placePiece(player.pieces().get(i), slots.get(i));
            }
        }
    }

    /** Derives starting locations from square definitions, in row/column order. */
    private List<Position> activeCastleSlots(PlayerColor color) {
        List<Position> slots = new ArrayList<>();
        SquareColor castleColor = SquareColor.fromPlayerColor(color);
        for (int row = 0; row < Board.STORAGE_SIZE; row++) {
            for (int column = 0; column < Board.STORAGE_SIZE; column++) {
                Square square = board.squareAt(new Position(row, column));
                if (square.type() == SquareType.CASTLE
                        && square.status() == SquareStatus.ACTIVE
                        && square.associatedColor() == castleColor) {
                    slots.add(square.position());
                }
            }
        }
        return slots;
    }

    /**
     * Rolls for the current player on the shared computer, then computes and
     * stores legal moves for that roll. No piece is moved here. A cancelled
     * third six must not generate moves. If no legal move exists, the roll is
     * resolved immediately using the normal turn/extra-turn rules.
     */
    public TurnRoll roll() {
        PlayerColor player = turnManager.currentPlayer().color();
        // Validate and roll before changing pending moves: a rejected second
        // roll must leave the original choices intact.
        TurnRoll result = turnManager.roll(player);
        legalMoves = List.of();
        if (result.cancelled()) return result;

        List<MoveCandidate> candidates = moveGenerator.generate(board, player, result.value());
        legalMoves = moveRules.legalMoves(board, candidates);
        if (legalMoves.isEmpty()) {
            turnManager.completeTurn(player);
        }
        return result;
    }

    /**
     * Executes this piece's stored legal move for the current roll. The caller
     * chooses a piece, never an arbitrary destination or path. Invalid choices
     * leave occupancy, pending moves and the turn unchanged.
     */
    public MoveCandidate play(Piece piece) {
        Objects.requireNonNull(piece, "piece");
        Player activePlayer = turnManager.currentPlayer();
        PlayerColor player = activePlayer.color();
        turnManager.requireCanMove(player, piece);
        MoveCandidate move = legalMoves.stream()
                .filter(candidate -> candidate.piece().equals(piece))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Piece has no legal move for this roll"));
        // Board is currently exposed for setup/inspection; reject a stale source
        // or a newly blocked path before changing any occupancy.
        if (!moveRules.isLegalMove(board, piece, move.source(), move.path())) {
            throw new IllegalStateException("The stored move no longer matches the board");
        }
        applyMove(move);
        legalMoves = List.of();
        boolean playerFinished = hasFinished(activePlayer);
        if (playerFinished && !ranking.contains(activePlayer)) {
            ranking.add(activePlayer);
        }
        turnManager.completeTurn(player, playerFinished);
        if (isFinished()) {
            // The remaining participant is last, without moving their pieces
            // or claiming that all four of them reached Finish.
            Player last = players.stream().filter(participant -> !ranking.contains(participant))
                    .findFirst().orElseThrow();
            ranking.add(last);
        }
        return move;
    }

    /** FR-041/043: capture a lone opponent only on an unprotected destination. */
    private void applyMove(MoveCandidate move) {
        Square destination = board.squareAt(move.destination());
        List<Piece> opponents = destination.pieces().stream()
                .filter(piece -> piece.color() != move.piece().color()).toList();
        Piece captured = null;
        Position castleSlot = null;
        if (!moveRules.isProtectedFromCapture(destination) && !opponents.isEmpty()) {
            if (opponents.size() != 1) {
                throw new IllegalStateException("Unprotected capture requires exactly one opponent");
            }
            captured = opponents.getFirst();
            castleSlot = emptyCastleSlot(captured.color());
            board.movePiece(captured, move.destination(), castleSlot);
        }
        try {
            board.movePiece(move.piece(), move.source(), move.destination());
        } catch (RuntimeException failure) {
            if (captured != null) board.movePiece(captured, castleSlot, move.destination());
            throw failure;
        }
    }

    private Position emptyCastleSlot(PlayerColor color) {
        return activeCastleSlots(color).stream()
                .filter(position -> board.piecesAt(position).isEmpty())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No empty Castle slot for " + color));
    }

    /** Derives completion from board occupancy rather than a second piece-state flag. */
    public boolean hasFinished(Player player) {
        Objects.requireNonNull(player, "player");
        if (!players.contains(player)) {
            throw new IllegalArgumentException("Player does not participate in this game");
        }
        Position finish = routes.get(player.color()).finish();
        return board.piecesAt(finish).containsAll(player.pieces());
    }

    public boolean isFinished() {
        return turnManager.phase() == TurnPhase.GAME_OVER;
    }

    /** Finish order so far; once over, also includes the last unfinished participant. */
    public List<Player> ranking() {
        return List.copyOf(ranking);
    }

    /** Read-only choices from the most recent roll, empty initially or with no move. */
    public List<MoveCandidate> legalMoves() {
        return legalMoves;
    }

    public Board board() {
        return board;
    }

    /** The same participants and clockwise order used by the turn manager. */
    public List<Player> players() {
        return players;
    }

    /** Fixed routes for all four colors, including colors not participating. */
    public Map<PlayerColor, PlayerRoute> routes() {
        return routes;
    }

    public TurnManager turnManager() {
        return turnManager;
    }
}
