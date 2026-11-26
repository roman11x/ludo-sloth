package Model;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class ChainedSquareEffectsTest {
    private final AtomicLong nanos = new AtomicLong();
    private final Piece green = new Piece(PlayerColor.GREEN, 1);
    private final Piece red = new Piece(PlayerColor.RED, 1);

    private Game game(int roll, Map<Integer, SquareType> specials) {
        var routes = PlayerRouteFactory.createEasy();
        var greenPath = routes.get(PlayerColor.GREEN).positions();
        Board board = new BoardLayout() {
            private final EasyLayout inner = new EasyLayout();
            @Override protected void createCommonSquares(Square[][] squares) { inner.createCommonSquares(squares); }
            @Override protected void createStartsAndArrows(Square[][] squares) { inner.createStartsAndArrows(squares); }
            @Override protected void createHomesAndCastles(Square[][] squares) { inner.createHomesAndCastles(squares); }
            @Override protected void placeSpecialSquares(Square[][] squares, Random random) {
                specials.forEach((index, type) -> {
                    Position position = greenPath.get(index);
                    defineSquare(squares, position.row(), position.column(), type, SquareColor.NEUTRAL,
                            SquareStatus.ACTIVE, type == SquareType.QUESTION ? QuestionDifficulty.EASY : null);
                });
            }
        }.build(new Random(42));
        return new Game(List.of(new Player("Green", PlayerColor.GREEN), new Player("Red", PlayerColor.RED)),
                Difficulty.EASY, () -> roll, new Random() {
                    @Override public int nextInt(int bound) { return 0; }
                }, board, routes, Clock.fixed(Instant.parse("2026-11-26T00:00:00Z"), ZoneOffset.UTC), nanos::get);
    }

    private Position at(Game game, int index) { return game.routes().get(PlayerColor.GREEN).positions().get(index); }
    private Position location(Game game, Piece piece) {
        return game.board().occupancy().entrySet().stream().filter(e -> e.getValue().contains(piece))
                .map(Map.Entry::getKey).findFirst().orElseThrow();
    }
    private void relocate(Game game, Piece piece, Position destination) {
        game.board().movePiece(piece, location(game, piece), destination);
    }
    private PendingSquareEffect pending(Game game) { return game.pendingSquareEffect().orElseThrow(); }
    private void playFrom(Game game, int index) {
        relocate(game, green, at(game, index));
        game.roll();
        game.play(green);
    }

    @Test
    void capturesBeforeRequestingInputAndBlocksNormalActionsWhileWaiting() {
        Game game = game(1, Map.of(3, SquareType.QUESTION));
        Position castle = location(game, red);
        relocate(game, red, at(game, 3));
        List<String> events = new ArrayList<>();
        game.addListener(new GameListener() {
            @Override public void pieceMoved(Player player, MoveCandidate move) { events.add("moved"); }
            @Override public void pieceCaptured(Player player, Piece piece, Position slot) { events.add("captured"); }
            @Override public void squareEffectRequired(PendingSquareEffect effect) {
                assertEquals(castle, location(game, red));
                assertEquals(TurnPhase.WAITING_FOR_EFFECT, game.turnManager().phase());
                assertEquals(QuestionDifficulty.EASY, effect.square().questionDifficulty());
                events.add("input");
            }
            @Override public void turnPassed(Player player) { events.add("turn"); }
        });
        playFrom(game, 2);
        assertEquals(List.of("moved", "captured", "input"), events);
        var request = pending(game);
        var before = game.board().occupancy();
        assertTrue(game.legalMoves().isEmpty());
        assertThrows(IllegalStateException.class, game::roll);
        assertThrows(IllegalStateException.class, () -> game.play(green));
        assertThrows(IllegalStateException.class, () -> game.moveBackward(green, 1));
        assertThrows(IllegalStateException.class, () -> game.turnManager().completeTurn(PlayerColor.GREEN));
        assertEquals(before, game.board().occupancy());
        assertEquals(request, pending(game));
        game.resolveSquareEffect(request.id(), -1);
        assertEquals(castle, location(game, red), "The earlier capture must not be undone");
        assertEquals(at(game, 2), location(game, green));
        assertEquals(List.of("moved", "captured", "input", "moved", "turn"), events);
        assertEquals(PlayerColor.RED, game.turnManager().currentPlayer().color());
    }

    @Test
    void questionSurpriseQuestionChainKeepsTheSameTurnUntilResolved() {
        Game game = game(3, Map.of(3, SquareType.QUESTION, 5, SquareType.SURPRISE, 4, SquareType.QUESTION));
        playFrom(game, 0);
        long first = pending(game).id();
        assertEquals(SquareType.QUESTION, pending(game).square().type());
        game.resolveSquareEffect(first, 2);
        long second = pending(game).id();
        assertTrue(second > first);
        assertEquals(SquareType.SURPRISE, pending(game).square().type());
        assertEquals(PlayerColor.GREEN, game.turnManager().currentPlayer().color());
        game.resolveSquareEffect(second, -1);
        long third = pending(game).id();
        assertTrue(third > second);
        assertEquals(at(game, 4), pending(game).square().position());
        assertEquals(TurnPhase.WAITING_FOR_EFFECT, game.turnManager().phase());
        game.resolveSquareEffect(third, 0);
        assertTrue(game.pendingSquareEffect().isEmpty());
        assertEquals(TurnPhase.WAITING_FOR_ROLL, game.turnManager().phase());
        assertEquals(PlayerColor.RED, game.turnManager().currentPlayer().color());
    }

    @Test
    void rejectsStaleOrInvalidRepliesWithoutConsumingThePendingEffect() {
        Game game = game(1, Map.of(3, SquareType.QUESTION, 4, SquareType.SURPRISE));
        playFrom(game, 2);
        var first = pending(game);
        var before = game.board().occupancy();
        assertThrows(IllegalStateException.class, () -> game.resolveSquareEffect(first.id() + 1, 1));
        assertThrows(IllegalArgumentException.class, () -> game.resolveSquareEffect(first.id(), red, -1));
        assertThrows(NullPointerException.class, () -> game.resolveSquareEffect(first.id(), null, 1));
        assertThrows(IllegalArgumentException.class, () -> game.resolveSquareEffect(first.id(), Integer.MIN_VALUE));
        assertEquals(first, pending(game));
        assertEquals(before, game.board().occupancy());
        game.resolveSquareEffect(first.id(), 1);
        var second = pending(game);
        assertThrows(IllegalStateException.class, () -> game.resolveSquareEffect(first.id(), 0));
        assertThrows(IllegalArgumentException.class,
                () -> game.resolveSquareEffect(second.id(), new Piece(PlayerColor.BLUE, 1), -1));
        assertEquals(second, pending(game));
        game.resolveSquareEffect(second.id(), 0);
        assertThrows(IllegalStateException.class, () -> game.resolveSquareEffect(second.id(), 0));
    }

    @Test
    void zeroStepsDoNotTriggerTheSameSquareAgainAndBlockedMovementCanEndTheChain() {
        Game game = game(1, Map.of(6, SquareType.QUESTION));
        AtomicInteger requests = new AtomicInteger();
        game.addListener(new GameListener() {
            @Override public void squareEffectRequired(PendingSquareEffect effect) { requests.incrementAndGet(); }
        });
        relocate(game, red, at(game, 4));
        relocate(game, new Piece(PlayerColor.RED, 2), at(game, 4));
        playFrom(game, 5);
        var executed = game.resolveSquareEffect(pending(game).id(), -3).orElseThrow();
        assertEquals(List.of(at(game, 5)), executed.path());
        assertEquals(1, requests.get());
        assertTrue(game.pendingSquareEffect().isEmpty());

        Game zero = game(1, Map.of(3, SquareType.SURPRISE));
        playFrom(zero, 2);
        long id = pending(zero).id();
        assertTrue(zero.resolveSquareEffect(id, 0).isEmpty());
        assertEquals(at(zero, 3), location(zero, green));
        assertTrue(zero.pendingSquareEffect().isEmpty());
        assertEquals(PlayerColor.RED, zero.turnManager().currentPlayer().color());
    }

    @Test
    void backwardResolutionStopsAtStartAndSurpriseRemainsProtected() {
        Game game = game(1, Map.of(1, SquareType.SURPRISE));
        relocate(game, red, at(game, 1));
        playFrom(game, 0);
        assertEquals(at(game, 1), location(game, red));
        var move = game.resolveSquareEffect(pending(game).id(), -3).orElseThrow();
        assertEquals(List.of(at(game, 0)), move.path());
        assertEquals(at(game, 1), location(game, red));
    }

    @Test
    void aSixRetainsTheTurnOnlyAfterTheEffectAndTheThirdSixStillCancels() {
        Game game = game(6, Map.of(6, SquareType.QUESTION, 12, SquareType.SURPRISE));
        playFrom(game, 0);
        assertEquals(TurnPhase.WAITING_FOR_EFFECT, game.turnManager().phase());
        game.resolveSquareEffect(pending(game).id(), 0);
        assertEquals(PlayerColor.GREEN, game.turnManager().currentPlayer().color());
        game.roll();
        game.play(green);
        assertEquals(TurnPhase.WAITING_FOR_EFFECT, game.turnManager().phase());
        game.resolveSquareEffect(pending(game).id(), 0);
        assertTrue(game.roll().cancelled());
        assertTrue(game.pendingSquareEffect().isEmpty());
        assertEquals(PlayerColor.RED, game.turnManager().currentPlayer().color());
    }

    @Test
    void forwardEffectCanFinishThePlayerAndGameAndFreezeDuration() {
        Game game = game(1, Map.of(49, SquareType.QUESTION));
        Player player = game.players().getFirst();
        Position finish = game.routes().get(PlayerColor.GREEN).finish();
        for (Piece piece : player.pieces().subList(1, 4)) relocate(game, piece, finish);
        List<String> events = new ArrayList<>();
        game.addListener(new GameListener() {
            @Override public void playerFinished(Player p, int place) { events.add("finished " + place); }
            @Override public void gameOver(List<Player> ranking) { events.add("over"); }
            @Override public void turnPassed(Player p) { events.add("turn"); }
        });
        playFrom(game, 48);
        nanos.set(100);
        assertEquals(TurnPhase.WAITING_FOR_EFFECT, game.turnManager().phase());
        assertTrue(events.isEmpty());
        game.resolveSquareEffect(pending(game).id(), 7).orElseThrow();
        assertTrue(game.isFinished());
        assertEquals(finish, location(game, green));
        assertEquals(List.of("finished 1", "over"), events);
        assertEquals(game.players(), game.ranking());
        var duration = game.duration();
        nanos.set(1000);
        assertEquals(duration, game.duration());
    }

    @Test
    void overshootingFinishProducesNoMoveAndDoesNotCompleteThePiece() {
        Game game = game(1, Map.of(49, SquareType.QUESTION));
        playFrom(game, 48);
        assertTrue(game.resolveSquareEffect(pending(game).id(), 8).isEmpty());
        assertEquals(at(game, 49), location(game, green));
        assertTrue(game.ranking().isEmpty());
        assertTrue(game.pendingSquareEffect().isEmpty());
    }

    @Test
    void chosenOpponentCanTriggerANewQuestionWhileTheOriginalPlayerKeepsTheTurn() {
        Game game = game(1, Map.of(3, SquareType.SURPRISE, 41, SquareType.QUESTION));
        var redPath = game.routes().get(PlayerColor.RED).positions();
        assertEquals(at(game, 41), redPath.get(2));
        relocate(game, red, redPath.get(4));
        playFrom(game, 2);
        game.resolveSquareEffect(pending(game).id(), red, -2).orElseThrow();
        assertEquals(red, pending(game).piece());
        assertEquals(PlayerColor.GREEN, pending(game).player().color());
        assertEquals(SquareType.QUESTION, pending(game).square().type());
        game.resolveSquareEffect(pending(game).id(), -1).orElseThrow();
        assertEquals(redPath.get(1), location(game, red));
        assertEquals(at(game, 3), location(game, green));
        assertEquals(PlayerColor.RED, game.turnManager().currentPlayer().color());
    }

    @Test
    void synchronousAutomaticRepliesUseAnIterativeQueueWithoutAChainCapOrStackGrowth() {
        Game game = game(1, Map.of(3, SquareType.QUESTION, 4, SquareType.SURPRISE));
        AtomicInteger requests = new AtomicInteger();
        game.addListener(new GameListener() {
            @Override public void squareEffectRequired(PendingSquareEffect effect) {
                int number = requests.incrementAndGet();
                int distance = number > 3000 ? 0 : effect.square().position().equals(at(game, 3)) ? 1 : -1;
                game.resolveSquareEffect(effect.id(), distance);
            }
        });
        playFrom(game, 2);
        assertEquals(3001, requests.get());
        assertTrue(game.pendingSquareEffect().isEmpty());
        assertEquals(PlayerColor.RED, game.turnManager().currentPlayer().color());
    }
}
