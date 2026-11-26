package Model;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class GameBackwardMoveTest {
    private Game game(int count, int roll, AtomicInteger rolls) {
        List<Player> players = List.of(new Player("Green", PlayerColor.GREEN),
                new Player("Yellow", PlayerColor.YELLOW), new Player("Blue", PlayerColor.BLUE),
                new Player("Red", PlayerColor.RED));
        return new Game(players.subList(0, count), () -> { rolls.incrementAndGet(); return roll; },
                new Random(42), new Random() {
                    @Override public int nextInt(int bound) { return 0; }
                });
    }

    private Position location(Game game, Piece piece) {
        return game.board().occupancy().entrySet().stream()
                .filter(entry -> entry.getValue().contains(piece)).map(java.util.Map.Entry::getKey)
                .findFirst().orElseThrow();
    }

    private void relocate(Game game, Piece piece, Position destination) {
        game.board().movePiece(piece, location(game, piece), destination);
    }

    @Test
    void executesBackwardMovesForAnyParticipantWithoutRollingOrPassingTurn() {
        for (int count = 2; count <= 4; count++) {
            AtomicInteger rolls = new AtomicInteger();
            Game game = game(count, 2, rolls);
            Player active = game.turnManager().currentPlayer();
            List<Player> reportedOwners = new ArrayList<>();
            game.addListener(new GameListener() {
                @Override public void pieceMoved(Player owner, MoveCandidate move) {
                    assertEquals(move.destination(), location(game, move.piece()));
                    reportedOwners.add(owner);
                }
                @Override public void turnPassed(Player next) { fail("Effect must not pass the turn"); }
                @Override public void rolled(Player player, TurnRoll roll) { fail("Effect must not roll"); }
            });
            for (Player player : game.players()) {
                Piece piece = player.pieces().getFirst();
                var path = game.routes().get(player.color()).positions();
                relocate(game, piece, path.get(5));
                var move = game.moveBackward(piece, 2).orElseThrow();
                assertEquals(List.of(path.get(4), path.get(3)), move.path());
                assertEquals(path.get(3), location(game, piece));
                assertSame(active, game.turnManager().currentPlayer());
                assertEquals(TurnPhase.WAITING_FOR_ROLL, game.turnManager().phase());
                assertTrue(game.ranking().isEmpty());
                assertTrue(game.legalMoves().isEmpty());
            }
            assertEquals(game.players(), reportedOwners);
            assertEquals(0, rolls.get());
        }
    }

    @Test
    void capturesOnUnprotectedLandingUsingTheNormalCastleReturnAndListenerEvents() {
        Game game = game(4, 2, new AtomicInteger());
        var path = game.routes().get(PlayerColor.GREEN).positions();
        int target = 1;
        while (game.board().squareAt(path.get(target)).type() != SquareType.NORMAL) target++;
        Player green = game.players().getFirst();
        Player red = game.players().getLast();
        Piece moving = green.pieces().getFirst();
        Piece victim = red.pieces().getFirst();
        Position castle = location(game, victim);
        relocate(game, moving, path.get(target + 2));
        relocate(game, victim, path.get(target));
        List<String> events = new ArrayList<>();
        game.addListener(new GameListener() {
            @Override public void pieceMoved(Player owner, MoveCandidate move) {
                assertEquals(green, owner);
                assertEquals(move.destination(), location(game, moving));
                assertEquals(castle, location(game, victim));
                events.add("moved");
            }
            @Override public void pieceCaptured(Player owner, Piece piece, Position slot) {
                assertEquals(red, owner);
                assertEquals(victim, piece);
                assertEquals(castle, slot);
                events.add("captured");
            }
        });
        game.moveBackward(moving, 2).orElseThrow();
        assertEquals(List.of(moving), game.board().piecesAt(path.get(target)));
        assertEquals(castle, location(game, victim));
        assertEquals(List.of("moved", "captured"), events);
        assertEquals(16, game.board().occupancy().values().stream().mapToInt(List::size).sum());
    }

    @Test
    void protectedLandingsAreSharedAndBlocksShortenTheActualExecutedMove() {
        Game game = game(4, 2, new AtomicInteger());
        var path = game.routes().get(PlayerColor.GREEN).positions();
        Piece moving = new Piece(PlayerColor.GREEN, 1);
        Piece opponent = new Piece(PlayerColor.RED, 1);
        int protectedIndex = 1;
        while (game.board().squareAt(path.get(protectedIndex)).type() != SquareType.PROTECTED) protectedIndex++;
        relocate(game, moving, path.get(protectedIndex + 1));
        relocate(game, opponent, path.get(protectedIndex));
        game.moveBackward(moving, 1).orElseThrow();
        assertTrue(game.board().piecesAt(path.get(protectedIndex)).containsAll(List.of(moving, opponent)));

        relocate(game, moving, path.get(10));
        relocate(game, opponent, path.get(8));
        relocate(game, new Piece(PlayerColor.RED, 2), path.get(8));
        var move = game.moveBackward(moving, 3).orElseThrow();
        assertEquals(List.of(path.get(9)), move.path());
        assertEquals(path.get(9), location(game, moving));
        var before = game.board().occupancy();
        assertTrue(game.moveBackward(moving, 3).isEmpty());
        assertEquals(before, game.board().occupancy());
    }

    @Test
    void clampsAtStartAndNoMovementDoesNotNotifyListeners() {
        Game game = game(2, 2, new AtomicInteger());
        Piece moving = new Piece(PlayerColor.GREEN, 1);
        var path = game.routes().get(PlayerColor.GREEN).positions();
        AtomicInteger events = new AtomicInteger();
        game.addListener(new GameListener() {
            @Override public void pieceMoved(Player player, MoveCandidate move) { events.incrementAndGet(); }
        });
        assertTrue(game.moveBackward(moving, 3).isEmpty()); // Castle.
        relocate(game, moving, path.get(1));
        assertTrue(game.moveBackward(moving, 0).isEmpty());
        assertEquals(path.getFirst(), game.moveBackward(moving, Integer.MAX_VALUE).orElseThrow().destination());
        assertTrue(game.moveBackward(moving, 3).isEmpty()); // Start.
        relocate(game, moving, path.getLast());
        assertTrue(game.moveBackward(moving, 3).isEmpty()); // Finished piece.
        assertEquals(1, events.get());
    }

    @Test
    void refreshesPendingChoicesForTheSameRollAndPreservesConsecutiveSixes() {
        AtomicInteger rolls = new AtomicInteger();
        Game game = game(2, 6, rolls);
        Piece moving = new Piece(PlayerColor.GREEN, 1);
        var path = game.routes().get(PlayerColor.GREEN).positions();
        relocate(game, moving, path.get(10));
        game.roll();
        game.addListener(new GameListener() {
            @Override public void pieceMoved(Player player, MoveCandidate move) {
                if (!move.destination().equals(path.get(8))) return;
                var refreshed = game.legalMoves().stream().filter(c -> c.piece().equals(moving)).findFirst().orElseThrow();
                assertEquals(path.get(8), refreshed.source());
                assertEquals(path.get(14), refreshed.destination());
            }
        });
        game.moveBackward(moving, 2).orElseThrow();
        assertEquals(TurnPhase.WAITING_FOR_MOVE, game.turnManager().phase());
        assertEquals(PlayerColor.GREEN, game.turnManager().currentPlayer().color());
        assertEquals(1, rolls.get());
        game.play(moving);
        assertEquals(path.get(14), location(game, moving));
        assertFalse(game.roll().cancelled());
        game.play(moving);
        assertTrue(game.roll().cancelled());
        assertEquals(3, rolls.get());
        assertEquals(PlayerColor.YELLOW, game.turnManager().currentPlayer().color());
    }

    @Test
    void doesNotCompletePendingTurnEvenIfTheEffectLeavesNoLegalDiceMove() {
        Game game = game(2, 1, new AtomicInteger());
        Piece moving = new Piece(PlayerColor.GREEN, 1);
        var path = game.routes().get(PlayerColor.GREEN).positions();
        Position sharedStart = game.board().startFor(PlayerColor.YELLOW);
        relocate(game, moving, sharedStart);
        relocate(game, new Piece(PlayerColor.YELLOW, 1), sharedStart);
        relocate(game, new Piece(PlayerColor.YELLOW, 2), sharedStart);
        game.roll(); // The opponent block on a protected source does not trap an existing occupant.
        assertFalse(game.legalMoves().isEmpty());
        game.moveBackward(moving, 1).orElseThrow();
        assertTrue(game.legalMoves().isEmpty());
        assertEquals(TurnPhase.WAITING_FOR_MOVE, game.turnManager().phase());
        assertEquals(PlayerColor.GREEN, game.turnManager().currentPlayer().color());
    }

    @Test
    void rejectsInvalidRequestsAndCompletedGamesWithoutChangingState() {
        Game game = game(2, 1, new AtomicInteger());
        Piece moving = new Piece(PlayerColor.GREEN, 1);
        var before = game.board().occupancy();
        assertThrows(NullPointerException.class, () -> game.moveBackward(null, 1));
        assertThrows(IllegalArgumentException.class, () -> game.moveBackward(moving, -1));
        assertThrows(IllegalArgumentException.class, () -> game.moveBackward(new Piece(PlayerColor.RED, 1), 1));
        assertEquals(before, game.board().occupancy());
        Player green = game.players().getFirst();
        var path = game.routes().get(PlayerColor.GREEN).positions();
        for (Piece piece : green.pieces()) relocate(game, piece, path.getLast());
        relocate(game, green.pieces().getLast(), path.get(path.size() - 2));
        game.roll();
        game.play(green.pieces().getLast());
        assertTrue(game.isFinished());
        var finished = game.board().occupancy();
        assertThrows(IllegalStateException.class, () -> game.moveBackward(new Piece(PlayerColor.YELLOW, 1), 1));
        assertEquals(finished, game.board().occupancy());
    }
}
