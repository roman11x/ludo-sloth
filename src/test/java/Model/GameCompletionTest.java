package Model;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class GameCompletionTest {
    private Random firstAt(int index) {
        return new Random() {
            @Override public int nextInt(int bound) { return index; }
        };
    }

    private Game game(int count, Die die, int first) {
        List<Player> players = new ArrayList<>();
        List<PlayerColor> order = List.of(PlayerColor.GREEN, PlayerColor.YELLOW, PlayerColor.BLUE, PlayerColor.RED);
        for (int i = 0; i < count; i++) players.add(new Player(order.get(i).name(), order.get(i)));
        return new Game(players, die, new Random(42), firstAt(first));
    }

    private Position location(Game game, Piece piece) {
        return game.board().occupancy().entrySet().stream()
                .filter(entry -> entry.getValue().contains(piece)).findFirst().orElseThrow().getKey();
    }

    private void relocate(Game game, Piece piece, Position destination) {
        game.board().movePiece(piece, location(game, piece), destination);
    }

    private void readyToFinish(Game game, Player player, int distance) {
        PlayerRoute route = game.routes().get(player.color());
        for (int i = 0; i < 3; i++) relocate(game, player.pieces().get(i), route.finish());
        relocate(game, player.pieces().get(3), route.positions().get(route.size() - 1 - distance));
    }

    private void finishCurrentPlayer(Game game) {
        Player active = game.turnManager().currentPlayer();
        game.roll();
        game.play(active.pieces().getLast());
    }

    @Test
    void detectsPlayerCompletionOnlyWhenAllFourPiecesReachTheirFinish() {
        Game game = game(3, () -> 1, 0);
        Player player = game.players().getFirst();
        assertFalse(game.hasFinished(player));
        Position finish = game.routes().get(player.color()).finish();
        for (int i = 0; i < 4; i++) {
            relocate(game, player.pieces().get(i), finish);
            assertEquals(i == 3, game.hasFinished(player));
        }
        assertThrows(IllegalArgumentException.class,
                () -> game.hasFinished(new Player("Not in game", PlayerColor.RED)));
        assertThrows(NullPointerException.class, () -> game.hasFinished(null));
    }

    @Test
    void recordsActualFinishOrderAndEndsAfterAllButOneForTwoThreeAndFourPlayers() {
        for (int count = 2; count <= 4; count++) {
            Game game = game(count, () -> 1, count - 1);
            for (Player player : game.players()) readyToFinish(game, player, 1);
            List<Player> expected = new ArrayList<>();
            for (int i = 0; i < count; i++) expected.add(game.players().get((count - 1 + i) % count));
            for (int i = 0; i < count - 1; i++) {
                Player finisher = expected.get(i);
                assertSame(finisher, game.turnManager().currentPlayer());
                List<Player> oldRanking = game.ranking();
                finishCurrentPlayer(game);
                assertTrue(game.hasFinished(finisher));
                assertEquals(i, oldRanking.size(), "Previously returned rankings must remain snapshots");
                assertTrue(game.legalMoves().isEmpty());
                if (i < count - 2) {
                    assertFalse(game.isFinished());
                    assertEquals(expected.subList(0, i + 1), game.ranking());
                    assertSame(expected.get(i + 1), game.turnManager().currentPlayer());
                }
            }
            assertTrue(game.isFinished());
            assertEquals(TurnPhase.GAME_OVER, game.turnManager().phase());
            assertEquals(expected, game.ranking());
            assertEquals(count, game.ranking().stream().distinct().count());
            assertFalse(game.hasFinished(expected.getLast()), "Last place has not actually finished its pieces");
            assertEquals(3, game.board().piecesAt(game.routes().get(expected.getLast().color()).finish()).size());
        }
    }

    @Test
    void finishingOnSixSkipsExtraTurnAndNextPlayersSixCounterStartsFresh() {
        Game game = game(3, () -> 6, 0);
        Player green = game.players().getFirst();
        Player yellow = game.players().get(1);
        readyToFinish(game, green, 6);
        finishCurrentPlayer(game);
        assertEquals(List.of(green), game.ranking());
        assertFalse(game.isFinished());
        assertSame(yellow, game.turnManager().currentPlayer());
        assertEquals(TurnPhase.WAITING_FOR_ROLL, game.turnManager().phase());
        assertEquals(new TurnRoll(6, false), game.roll());
        game.play(yellow.pieces().getFirst());
        assertEquals(new TurnRoll(6, false), game.roll());
        game.play(yellow.pieces().get(1));
        assertEquals(new TurnRoll(6, true), game.roll());
        assertEquals(PlayerColor.BLUE, game.turnManager().currentPlayer().color());
        assertEquals(List.of(green), game.ranking());
    }

    @Test
    void finishedPlayerIsSkippedOnLaterClockwiseCyclesWithoutDuplicateRanking() {
        Game game = game(4, () -> 1, 0);
        Player green = game.players().getFirst();
        readyToFinish(game, green, 1);
        finishCurrentPlayer(game);
        for (int cycle = 0; cycle < 2; cycle++) {
            for (PlayerColor expected : List.of(PlayerColor.YELLOW, PlayerColor.BLUE, PlayerColor.RED)) {
                assertEquals(expected, game.turnManager().currentPlayer().color());
                game.roll(); // All remaining participants are in Castle and cannot move on 1.
            }
        }
        assertEquals(List.of(green), game.ranking());
        assertEquals(PlayerColor.YELLOW, game.turnManager().currentPlayer().color());
        assertFalse(game.isFinished());
    }

    @Test
    void finalFinishOnSixEndsGameAndRejectsAllFurtherActionsWithoutChanges() {
        AtomicInteger dieCalls = new AtomicInteger();
        Game game = game(2, () -> { dieCalls.incrementAndGet(); return 6; }, 0);
        Player winner = game.players().getFirst();
        readyToFinish(game, winner, 6);
        finishCurrentPlayer(game);
        var before = game.board().occupancy();
        List<Player> ranking = game.ranking();
        assertTrue(game.isFinished());
        assertEquals(1, dieCalls.get());
        assertThrows(IllegalStateException.class, game::roll);
        assertThrows(IllegalStateException.class, () -> game.play(winner.pieces().getFirst()));
        assertThrows(IllegalStateException.class, () -> game.turnManager().completeTurn(winner.color(), true));
        assertThrows(IllegalStateException.class, () -> game.turnManager().roll(winner.color()));
        assertThrows(IllegalStateException.class, () -> game.turnManager().currentPlayer());
        for (Player player : game.players()) assertFalse(game.turnManager().isActive(player.color()));
        assertEquals(1, dieCalls.get());
        assertEquals(before, game.board().occupancy());
        assertEquals(ranking, game.ranking());
        assertTrue(game.legalMoves().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> game.ranking().clear());
    }

    @Test
    void ordinaryMovesDoNotAddRankingOrEndGame() {
        Game game = game(2, () -> 6, 0);
        Player active = game.turnManager().currentPlayer();
        game.roll();
        game.play(active.pieces().getFirst());
        assertFalse(game.hasFinished(active));
        assertFalse(game.isFinished());
        assertTrue(game.ranking().isEmpty());
        assertSame(active, game.turnManager().currentPlayer());
    }
}
