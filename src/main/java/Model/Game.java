package Model;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.function.LongSupplier;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumMap;
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
    private final Die standardDie;
    private final MoveRules moveRules = new MoveRules();
    private List<MoveCandidate> legalMoves = List.of();
    private final List<Player> ranking = new ArrayList<>();
    private final LocalDateTime startTime;
    private final LongSupplier nanoTime;
    private final long startedAtNanos;
    private Duration finalDuration;
    private final List<GameListener> listeners = new ArrayList<>();
    private PendingSquareEffect pendingSquareEffect;
    private Question pendingQuestion;
    private PendingOpponentMove pendingOpponentMove;
    private long nextEffectId;
    private final Deque<NotificationBatch> notificationQueue = new ArrayDeque<>();
    private boolean notifying;
    private final QuestionPicker questionPicker;

    private record NotificationBatch(List<Consumer<GameListener>> events, List<GameListener> recipients) { }


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
        this(players, difficulty, standardDie, firstPlayerRandom,
                initialBoard(difficulty, boardRandom), PlayerRouteFactory.createEasy(), clock, nanoTime);
    }

    /** Injected layout for effect tests and later integration; normal setup still supports Easy only. */
    Game(List<Player> players, Difficulty difficulty, Die standardDie, Random firstPlayerRandom,
         Board board, Map<PlayerColor, PlayerRoute> routes, Clock clock, LongSupplier nanoTime) {
        this(players, difficulty, standardDie, firstPlayerRandom, board, routes, clock, nanoTime,
                new QuestionPicker(new StubQuestionBank()));
    }

    /** Injected layout plus question picker, for question tests; sets up the game. */
    Game(List<Player> players, Difficulty difficulty, Die standardDie, Random firstPlayerRandom,
         Board board, Map<PlayerColor, PlayerRoute> routes, Clock clock, LongSupplier nanoTime,
         QuestionPicker questionPicker) {
        this.difficulty = Objects.requireNonNull(difficulty, "difficulty");
        Objects.requireNonNull(clock, "clock");
        this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime");
        List<Player> participants = List.copyOf(players);
        this.standardDie = Objects.requireNonNull(standardDie, "standardDie");
        Objects.requireNonNull(firstPlayerRandom, "firstPlayerRandom");
        List<PlayerSetup> setups = participants.stream()
                .map(player -> new PlayerSetup(player.name(), player.color())).toList();
        List<String> errors = SetupValidator.validate(setups, difficulty);
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(String.join("; ", errors));
        }

        this.board = Objects.requireNonNull(board, "board");
        if (board.hasPieces()) throw new IllegalArgumentException("A new game requires an empty board");
        this.routes = Map.copyOf(routes);
        this.moveGenerator = new MoveGenerator(this.routes);
        this.turnManager = new TurnManager(participants, standardDie, firstPlayerRandom);
        this.players = turnManager.players();
        placeInitialPieces();
        // Start only when setup, piece placement and first-player selection are complete.
        this.startTime = LocalDateTime.now(clock);
        this.startedAtNanos = this.nanoTime.getAsLong();
        this.questionPicker = Objects.requireNonNull(questionPicker, "questionPicker");
    }

    private static Board initialBoard(Difficulty difficulty, Random random) {
        Objects.requireNonNull(difficulty, "difficulty");
        Objects.requireNonNull(random, "boardRandom");
        if (difficulty != Difficulty.EASY) {
            throw new IllegalArgumentException("Unsupported game difficulty: " + difficulty
                    + "; currently only EASY is supported");
        }
        return Board.createEasy(random);
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
        continueAfterLanding(piece, events);
        notifyListeners(events);
        return move;
    }

    /** Request one effect at a time; continuing with a new landing imposes no chain-length cap. */
    private void continueAfterLanding(Piece piece, List<Consumer<GameListener>> events) {
        Position position = piecePosition(piece);
        Square destination = board.squareAt(position);
        if (destination.type() == SquareType.QUESTION || destination.type() == SquareType.SURPRISE) {
            pendingSquareEffect = new PendingSquareEffect(++nextEffectId,
                    turnManager.currentPlayer(), piece, destination);
            turnManager.pauseForEffect();
            PendingSquareEffect request = pendingSquareEffect;
            if (destination.type() == SquareType.QUESTION) {
                Question question = questionPicker.pickQuestion(destination.questionDifficulty());
                pendingQuestion = question;
                events.add(listener -> listener.questionRequired(request.id(), request.player(), piece, question));
            } else {
                events.add(listener -> listener.squareEffectRequired(request));
            }
        } else {
            finishTurn(events);
        }
    }

    /** Shared completion path, reached only after all landing effects have resolved. */
    private void finishTurn(List<Consumer<GameListener>> events) {
        Player activePlayer = turnManager.currentPlayer();
        recordPlayerCompletion(activePlayer, events);
        turnManager.completeTurn(activePlayer.color(), hasFinished(activePlayer));
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
    }

    /** A chained effect may finish an opponent's piece while the original player owns the turn. */
    private void recordPlayerCompletion(Player player, List<Consumer<GameListener>> events) {
        if (hasFinished(player) && !ranking.contains(player)) {
            turnManager.recordFinishedPlayer(player.color());
            ranking.add(player);
            int place = ranking.size();
            events.add(listener -> listener.playerFinished(player, place));
        }
    }

    /**
     * Handles an already-selected Surprise: S02 transfers/movement, or S03 opponent selection.
     * Selection belongs to SurpriseEventSelector; skip/UltraBoost have separate handlers.
     * Starting S03 returns empty while waiting for input, not a completed/no-op effect.
     * Direct transfers have a destination-only path (not the intervening route), while back-three
     * uses the existing reverse-route rules, captures and chained-effect continuation.
     */
    public Optional<MoveCandidate> resolveSurpriseEffect(long requestId, SurpriseEvent event) {
        PendingSquareEffect request = requirePendingEffect(requestId);
        requireNoOpponentSelection();
        Objects.requireNonNull(event, "event");
        if (request.square().type() != SquareType.SURPRISE) {
            throw new IllegalArgumentException("A Surprise event requires a pending Surprise square");
        }
        return switch (event) {
            case MOVE_BACK_THREE -> resolveSquareEffect(requestId, -3);
            case MOVE_TO_FINISH -> transferSurprisePiece(request, routes.get(request.piece().color()).finish());
            case RETURN_TO_CASTLE -> transferSurprisePiece(request, surpriseCastleSlot(request.piece()));
            case MOVE_OPPONENT_BACKWARD -> {
                beginOpponentBackward(requestId);
                yield Optional.empty();
            }
            case SKIP_NEXT_TURN, EXTRA_ULTRA_TURN ->
                    throw new IllegalArgumentException("This event requires its own handler: " + event);
        };
    }

    /** Begin S03 without rolling: the triggering player must choose an opponent first. */
    public PendingOpponentMove beginOpponentBackward(long requestId) {
        PendingSquareEffect request = requirePendingEffect(requestId);
        requireNoOpponentSelection();
        if (request.square().type() != SquareType.SURPRISE) {
            throw new IllegalArgumentException("Opponent movement requires a pending Surprise");
        }
        requirePendingLanding(request);
        List<Player> opponents = players.stream()
                .filter(player -> player.color() != request.player().color() && !hasFinished(player)).toList();
        pendingOpponentMove = new PendingOpponentMove(requestId, opponents, null, 0, Map.of());
        PendingOpponentMove choice = pendingOpponentMove;
        notifyListeners(List.of(listener -> listener.opponentMoveRequired(choice)));
        return choice;
    }

    /** Read-only input state; absent after movement or cancellation, including between chained effects. */
    public Optional<PendingOpponentMove> pendingOpponentMove() {
        return Optional.ofNullable(pendingOpponentMove);
    }

    /**
     * Choose an opponent, then roll the Standard die once, outside TurnManager's normal roll path.
     * If this opponent has no move, another can be chosen with the SAME roll. If none has a move,
     * cancel and resume the original turn; an empty return signals that cancellation.
     */
    public Optional<PendingOpponentMove> chooseOpponent(long requestId, PlayerColor color) {
        PendingOpponentMove choice = requireOpponentSelection(requestId);
        Objects.requireNonNull(color, "color");
        Player opponent = choice.opponents().stream().filter(player -> player.color() == color)
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Choose a participating unfinished opponent"));
        if (!choice.moves().isEmpty()) {
            throw new IllegalStateException("Choose a piece of the selected opponent before continuing");
        }
        int roll = choice.roll();
        if (roll == 0) {
            // SPC-004: this draw neither grants a six bonus nor changes the normal six counter.
            roll = standardDie.roll();
            if (roll < 1 || roll > 6) throw new IllegalStateException("Standard die returned a value outside 1-6");
        }
        int steps = (roll + 1) / 2;
        Map<PlayerColor, List<MoveCandidate>> options = new EnumMap<>(PlayerColor.class);
        for (Player player : choice.opponents()) options.put(player.color(), opponentBackwardMoves(player, steps));
        if (options.values().stream().allMatch(List::isEmpty)) {
            PendingSquareEffect request = requirePendingEffect(requestId);
            int result = roll;
            List<Consumer<GameListener>> events = new ArrayList<>();
            events.add(listener -> listener.opponentMoveCancelled(request, result));
            finishResolvedEffect(request.piece(), Optional.empty(), events);
            return Optional.empty();
        }
        pendingOpponentMove = new PendingOpponentMove(requestId, choice.opponents(), opponent, roll, options);
        PendingOpponentMove updated = pendingOpponentMove;
        notifyListeners(List.of(listener -> listener.opponentMoveRequired(updated)));
        return Optional.of(updated);
    }

    /** Execute only a stored eligible choice; a stale source/path is rejected without consuming input. */
    public MoveCandidate playOpponentPiece(long requestId, Piece piece) {
        PendingOpponentMove choice = requireOpponentSelection(requestId);
        Objects.requireNonNull(piece, "piece");
        if (choice.roll() == 0) throw new IllegalStateException("Choose an opponent before choosing a piece");
        MoveCandidate stored = choice.moves().stream().filter(move -> move.piece().equals(piece)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Piece is not an eligible choice for this opponent"));
        MoveCandidate current = opponentBackwardMoves(choice.selectedOpponent(), choice.steps()).stream()
                .filter(stored::equals).findFirst().orElseThrow(() -> new IllegalStateException("The choice no longer matches the board"));
        List<Consumer<GameListener>> events = new ArrayList<>();
        applyMove(choice.selectedOpponent(), current, events);
        finishResolvedEffect(piece, Optional.of(current), events);
        return current;
    }

    private List<MoveCandidate> opponentBackwardMoves(Player opponent, int steps) {
        List<MoveCandidate> moves = new ArrayList<>();
        for (Piece piece : opponent.pieces()) {
            Square square = board.squareAt(piecePosition(piece));
            if (square.type() == SquareType.CASTLE || square.type() == SquareType.HOME_PATH
                    || square.type() == SquareType.FINISHING) continue;
            moveGenerator.generateBackward(board, piece, steps)
                    .flatMap(candidate -> moveRules.legalEventMove(board, candidate)).ifPresent(moves::add);
        }
        return List.copyOf(moves);
    }

    private PendingOpponentMove requireOpponentSelection(long requestId) {
        PendingSquareEffect request = requirePendingEffect(requestId);
        if (pendingOpponentMove == null || pendingOpponentMove.effectId() != requestId) {
            throw new IllegalStateException("No matching opponent selection");
        }
        requirePendingLanding(request);
        return pendingOpponentMove;
    }

    private void requireNoOpponentSelection() {
        if (pendingOpponentMove != null) {
            throw new IllegalStateException("Complete the pending opponent selection first");
        }
    }

    private Position surpriseCastleSlot(Piece piece) {
        Position originalSlot = activeCastleSlots(piece.color()).get(piece.number() - 1);
        // Captures use any free Castle slot, so the original numbered slot may already be occupied.
        return board.piecesAt(originalSlot).isEmpty() ? originalSlot : emptyCastleSlot(piece.color());
    }

    private Optional<MoveCandidate> transferSurprisePiece(PendingSquareEffect request, Position destination) {
        requirePendingLanding(request);
        MoveCandidate transfer = new MoveCandidate(request.piece(), request.square().position(), List.of(destination));
        List<Consumer<GameListener>> events = new ArrayList<>();
        // FR-065: a direct Finish transfer ignores intervening blocks and remaining distance.
        // Both destinations belong to the piece; this is relocation, never a capture en route.
        board.movePiece(transfer.piece(), transfer.source(), transfer.destination());
        Player owner = pieceOwner(transfer.piece());
        events.add(listener -> listener.pieceMoved(owner, transfer));
        return finishResolvedEffect(request.piece(), Optional.of(transfer), events);
    }

    /** The pending request remains stable until accepted; no timeout is imposed. */
    public Optional<PendingSquareEffect> pendingSquareEffect() {
        return Optional.ofNullable(pendingSquareEffect);
    }

    /** Resolve an effect on the landing piece. Positive steps move forward, negative backward. */
    public Optional<MoveCandidate> resolveSquareEffect(long requestId, int steps) {
        PendingSquareEffect request = requirePendingEffect(requestId);
        return resolveSquareEffect(requestId, request.piece(), steps);
    }

    /**
     * Continuation for trusted question/surprise orchestration, not raw UI answers.
     * That logic validates the answer/event and chooses the affected piece/distance.
     * Questions affect their landing piece; a Surprise can move a selected opponent backward.
     * Zero steps or no legal displacement consumes this effect without re-triggering the source.
     * Rejected or stale requests leave the pending request and board unchanged.
     */
    public Optional<MoveCandidate> resolveSquareEffect(long requestId, Piece affectedPiece, int steps) {
        PendingSquareEffect request = requirePendingEffect(requestId);
        requireNoOpponentSelection();
        Objects.requireNonNull(affectedPiece, "affectedPiece");
        if (steps == Integer.MIN_VALUE) throw new IllegalArgumentException("Effect distance is too large");
        if (request.square().type() == SquareType.QUESTION && !affectedPiece.equals(request.piece())) {
            throw new IllegalArgumentException("A Question affects its landing piece only");
        }
        if (steps > 0 && !affectedPiece.equals(request.piece())) {
            throw new IllegalArgumentException("Only the landing piece can move forward for this effect");
        }
        Player owner = pieceOwner(affectedPiece);
        requirePendingLanding(request);
        Optional<MoveCandidate> proposed = steps < 0
                ? moveGenerator.generateBackward(board, affectedPiece, -steps)
                : moveGenerator.generateForwardEffect(board, affectedPiece, steps);
        Optional<MoveCandidate> approved = proposed.flatMap(candidate -> moveRules.legalEventMove(board, candidate));
        List<Consumer<GameListener>> events = new ArrayList<>();
        approved.ifPresent(move -> applyMove(owner, move, events));
        return finishResolvedEffect(affectedPiece, approved, events);
    }

    private Optional<MoveCandidate> finishResolvedEffect(Piece affectedPiece, Optional<MoveCandidate> move,
                                                        List<Consumer<GameListener>> events) {
        pendingSquareEffect = null;
        pendingOpponentMove = null;
        pendingQuestion = null;
        turnManager.resumeAfterEffect();
        recordPlayerCompletion(pieceOwner(affectedPiece), events);
        if (move.isPresent()) continueAfterLanding(affectedPiece, events);
        else finishTurn(events);
        notifyListeners(events);
        return move;
    }

    private void requirePendingLanding(PendingSquareEffect request) {
        if (!board.piecesAt(request.square().position()).contains(request.piece())) {
            throw new IllegalStateException("The pending landing no longer matches the board");
        }
    }

    private PendingSquareEffect requirePendingEffect(long requestId) {
        if (turnManager.phase() != TurnPhase.WAITING_FOR_EFFECT || pendingSquareEffect == null
                || pendingSquareEffect.id() != requestId) {
            throw new IllegalStateException("No matching pending square effect");
        }
        return pendingSquareEffect;
    }

    private Player pieceOwner(Piece piece) {
        return players.stream().filter(player -> player.pieces().contains(piece)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Piece does not belong to a participant"));
    }

    private Position piecePosition(Piece piece) {
        return board.occupancy().entrySet().stream().filter(entry -> entry.getValue().contains(piece))
                .map(Map.Entry::getKey).findFirst()
                .orElseThrow(() -> new IllegalStateException("Piece is not on this board: " + piece));
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
        if (turnManager.phase() == TurnPhase.WAITING_FOR_EFFECT) {
            throw new IllegalStateException("Resolve the pending effect through resolveSquareEffect");
        }
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
        notificationQueue.addLast(new NotificationBatch(List.copyOf(events), List.copyOf(listeners)));
        if (notifying) return;
        notifying = true;
        try {
            // An automatic effect may resume synchronously in a listener. Queue it instead of recursing.
            while (!notificationQueue.isEmpty()) {
                NotificationBatch batch = notificationQueue.removeFirst();
                for (Consumer<GameListener> event : batch.events()) {
                    for (GameListener listener : batch.recipients()) event.accept(listener);
                }
            }
        } finally {
            notifying = false;
            notificationQueue.clear();
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
