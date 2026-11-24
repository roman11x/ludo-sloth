package Model;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.function.LongSupplier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.function.Consumer;

/**
 * An initialized Easy game: board occupancy, participants, fixed routes and turns.
 * Uses the participants' existing pieces; it does not create another set of pieces.
 * Coordinates rolling, legal move selection, captures and turn transitions.
 * Connecting the UI is a separate implementation step.
 */
public final class Game {
    private final Difficulty difficulty;
    private final Board board;
    private final List<Player> players;
    private final Map<PlayerColor, PlayerRoute> routes;
    private final TurnManager turnManager;
    private final MoveGenerator moveGenerator;
    private final MoveRules moveRules = new MoveRules();
    private List<MoveCandidate> legalMoves = List.of();
    private final List<Player> ranking = new ArrayList<>();
    private final LocalDateTime startTime;
    private final LongSupplier nanoTime;
    private final long startedAtNanos;
    private Duration finalDuration;
    private final List<GameListener> listeners = new ArrayList<>();

    public Game(List<Player> players) {
        this(players, Difficulty.EASY);
    }

    /** Creates a game at the requested difficulty; currently only Easy is supported. */
    public Game(List<Player> players, Difficulty difficulty) {
        this(players, difficulty, new StandardDie(), new Random(), new Random());
    }

    /** Separate random sources allow reproducible layouts and first-player tests. */
    public Game(List<Player> players, Die standardDie,
                Random boardRandom, Random firstPlayerRandom) {
        this(players, Difficulty.EASY, standardDie, boardRandom, firstPlayerRandom);
    }

    /** Explicit difficulty with separate random sources for reproducible tests. */
    public Game(List<Player> players, Difficulty difficulty, Die standardDie,
                Random boardRandom, Random firstPlayerRandom) {
        this(players, difficulty, standardDie, boardRandom, firstPlayerRandom,
                Clock.systemDefaultZone(), System::nanoTime);
    }

    /** Package-visible clock injection for tests; elapsed time must be monotonic. */
    Game(List<Player> players, Die standardDie, Random boardRandom,
         Random firstPlayerRandom, Clock clock, LongSupplier nanoTime) {
        this(players, Difficulty.EASY, standardDie, boardRandom, firstPlayerRandom, clock, nanoTime);
    }

    Game(List<Player> players, Difficulty difficulty, Die standardDie, Random boardRandom,
         Random firstPlayerRandom, Clock clock, LongSupplier nanoTime) {
        this.difficulty = Objects.requireNonNull(difficulty, "difficulty");
        if (difficulty != Difficulty.EASY) {
            throw new IllegalArgumentException("Unsupported game difficulty: " + difficulty
                    + "; currently only EASY is supported");
        }
        Objects.requireNonNull(clock, "clock");
        this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime");
        List<Player> participants = List.copyOf(players);
        Objects.requireNonNull(standardDie, "standardDie");
        Objects.requireNonNull(boardRandom, "boardRandom");
        Objects.requireNonNull(firstPlayerRandom, "firstPlayerRandom");
        List<PlayerSetup> setups = participants.stream()
                .map(player -> new PlayerSetup(player.name(), player.color())).toList();
        List<String> errors = SetupValidator.validate(setups, difficulty);
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(String.join("; ", errors));
        }

        this.board = Board.createEasy(boardRandom);
        this.routes = PlayerRouteFactory.createEasy();
        this.moveGenerator = new MoveGenerator(routes);
        this.turnManager = new TurnManager(participants, standardDie, firstPlayerRandom);
        this.players = turnManager.players();
        placeInitialPieces();
        // Start only when setup, piece placement and first-player selection are complete.
        this.startTime = LocalDateTime.now(clock);
        this.startedAtNanos = this.nanoTime.getAsLong();
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
        Player roller = turnManager.currentPlayer();
        TurnRoll result = turnManager.roll(player);
        legalMoves = List.of();
        if (!result.cancelled()) {
            List<MoveCandidate> candidates = moveGenerator.generate(board, player, result.value());
            legalMoves = moveRules.legalMoves(board, candidates);
            if (legalMoves.isEmpty()) {
                turnManager.completeTurn(player);
            }
        }

        List<Consumer<GameListener>> events = new ArrayList<>();
        events.add(listener -> listener.rolled(roller, result));
        if (turnManager.phase() == TurnPhase.WAITING_FOR_ROLL) {
            Player next = turnManager.currentPlayer();
            events.add(listener -> listener.turnPassed(next));
        }
        notifyListeners(events);
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
        List<Consumer<GameListener>> events = new ArrayList<>();
        applyMove(activePlayer, move, events);
        legalMoves = List.of();
        boolean playerFinished = hasFinished(activePlayer);
        if (playerFinished && !ranking.contains(activePlayer)) {
            ranking.add(activePlayer);
            int place = ranking.size();
            events.add(listener -> listener.playerFinished(activePlayer, place));
        }
        turnManager.completeTurn(player, playerFinished);
        if (isFinished()) {
            finalDuration = elapsedDuration();
            // The remaining participant is last, without moving their pieces
            // or claiming that all four of them reached Finish.
            Player last = players.stream().filter(participant -> !ranking.contains(participant))
                    .findFirst().orElseThrow();
            ranking.add(last);
            List<Player> finalRanking = ranking();
            events.add(listener -> listener.gameOver(finalRanking));
        } else {
            Player next = turnManager.currentPlayer();
            events.add(listener -> listener.turnPassed(next));
        }
        notifyListeners(events);
        return move;
    }

    /**
     * Executes a backward movement requested by a question/surprise effect.
     * The piece may belong to any participant, including an opponent. The caller
     * selects eligible pieces and resolves any resulting square effects separately.
     * Reuses the normal movement/capture implementation and notifies listeners only
     * after the board and pending roll choices have been updated. Does not roll,
     * complete a turn, alter six counts or assign ranks. Returns empty without
     * notifications when no movement is possible.
     */
    public Optional<MoveCandidate> moveBackward(Piece piece, int steps) {
        Objects.requireNonNull(piece, "piece");
        if (isFinished()) throw new IllegalStateException("The game is over");
        Player owner = players.stream().filter(player -> player.pieces().contains(piece))
                .findFirst().orElseThrow(() -> new IllegalArgumentException(
                        "Piece does not belong to a participant"));
        Optional<MoveCandidate> move = moveGenerator.generateBackward(board, piece, steps)
                .flatMap(candidate -> moveRules.legalEventMove(board, candidate));
        if (move.isEmpty()) return Optional.empty();

        List<Consumer<GameListener>> events = new ArrayList<>();
        applyMove(owner, move.get(), events);
        if (turnManager.phase() == TurnPhase.WAITING_FOR_MOVE) {
            // An effect may move a source, remove a block or occupy a destination.
            // Refresh choices for the SAME roll, without resolving or passing the turn.
            legalMoves = moveRules.legalMoves(board, moveGenerator.generate(board,
                    turnManager.currentPlayer().color(), turnManager.pendingRollValue()));
        }
        notifyListeners(events);
        return move;
    }

    /** Registers {@code listener} to be notified after each accepted change; adding it twice has no effect. */
    public void addListener(GameListener listener) {
        Objects.requireNonNull(listener, "listener");
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    /** Stops notifying {@code listener}; does nothing if it is not registered. */
    public void removeListener(GameListener listener) {
        listeners.remove(listener);
    }

    /** Delivers each event to every listener in registration order, against a snapshot of the listeners. */
    private void notifyListeners(List<Consumer<GameListener>> events) {
        List<GameListener> recipients = List.copyOf(listeners);
        for (Consumer<GameListener> event : events) {
            for (GameListener listener : recipients) {
                event.accept(listener);
            }
        }
    }

    /** FR-041/043: capture a lone opponent only on an unprotected destination. */
    private void applyMove(Player mover, MoveCandidate move, List<Consumer<GameListener>> events) {
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
        events.add(listener -> listener.pieceMoved(mover, move));
        if (captured != null) {
            Piece capturedPiece = captured;
            Position capturedSlot = castleSlot;
            Player owner = players.stream().filter(participant -> participant.color() == capturedPiece.color())
                    .findFirst().orElseThrow();
            events.add(listener -> listener.pieceCaptured(owner, capturedPiece, capturedSlot));
        }
    }

    private Position emptyCastleSlot(PlayerColor color) {
        return activeCastleSlots(color).stream()
                .filter(position -> board.piecesAt(position).isEmpty())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No empty Castle slot for " + color));
    }

    /** Wall-clock timestamp for display/history; it is not used to measure elapsed time. */
    public LocalDateTime startTime() {
        return startTime;
    }

    /** Live elapsed duration while playing; a fixed value after game completion. */
    public Duration duration() {
        return finalDuration == null ? elapsedDuration() : finalDuration;
    }

    private Duration elapsedDuration() {
        // Subtract nanoTime readings: their absolute values are not timestamps.
        return Duration.ofNanos(nanoTime.getAsLong() - startedAtNanos);
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

    /** The difficulty chosen when this game was created. */
    public Difficulty difficulty() {
        return difficulty;
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
