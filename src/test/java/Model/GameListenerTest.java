package Model;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class GameListenerTest {

    /** Records every notification as one line of text. */
    private static class Recorder implements GameListener {
        final List<String> events = new ArrayList<>();
        private final String prefix;

        Recorder() { this(""); }
        Recorder(String prefix) { this.prefix = prefix; }

        @Override public void rolled(Player player, TurnRoll roll) {
            events.add(prefix + "rolled " + player.name() + " " + roll.value() + (roll.cancelled() ? " cancelled" : ""));
        }
        @Override public void pieceMoved(Player player, MoveCandidate move) {
            events.add(prefix + "moved " + player.name() + " " + move.piece().number());
        }
        @Override public void pieceCaptured(Player owner, Piece piece, Position castleSlot) {
            events.add(prefix + "captured " + owner.name() + " " + piece.number());
        }
        @Override public void playerFinished(Player player, int place) {
            events.add(prefix + "finished " + player.name() + " " + place);
        }
        @Override public void turnPassed(Player next) {
            events.add(prefix + "turn " + next.name());
        }
        @Override public void gameOver(List<Player> ranking) {
            events.add(prefix + "over " + ranking.stream().map(Player::name).toList());
        }
        @Override public void questionRequired(long effectId, Player player, Piece piece, Question question) {
            events.add(prefix + "question " + player.name());
        }
    }

    private Random firstAt(int index) {
        return new Random() {
            @Override public int nextInt(int bound) { return index; }
        };
    }

    private Game game(int count, Die die) {
        List<Player> players = new ArrayList<>();
        List<PlayerColor> order = List.of(PlayerColor.GREEN, PlayerColor.YELLOW, PlayerColor.BLUE, PlayerColor.RED);
        for (int i = 0; i < count; i++) players.add(new Player(order.get(i).name(), order.get(i)));
        return new Game(players, die, new Random(42), firstAt(0));
    }

    private Game twoPlayers(Die die) {
        return new Game(List.of(new Player("Green", PlayerColor.GREEN), new Player("Red", PlayerColor.RED)),
                die, new Random(42), firstAt(0));
    }

    private Die rolls(int... values) {
        AtomicInteger index = new AtomicInteger();
        return () -> values[index.getAndIncrement()];
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

    private Recorder listen(Game game) {
        Recorder recorder = new Recorder();
        game.addListener(recorder);
        return recorder;
    }

    @Test
    void rollWithNoLegalMoveNotifiesRollThenTurnPassedAfterTheTurnHasAdvanced() {
        Game game = twoPlayers(() -> 1);
        List<String> seen = new ArrayList<>();
        game.addListener(new GameListener() {
            @Override public void rolled(Player player, TurnRoll roll) {
                seen.add("rolled; now " + game.turnManager().currentPlayer().name());
            }
            @Override public void turnPassed(Player next) {
                seen.add("turn; now " + game.turnManager().currentPlayer().name());
            }
        });
        Recorder recorder = listen(game);
        game.roll();
        assertEquals(List.of("rolled Green 1", "turn Red"), recorder.events);
        assertEquals(List.of("rolled; now Red", "turn; now Red"), seen, "Notified only after the turn advanced");
    }

    @Test
    void rollWithLegalMovesNotifiesOnlyTheRollAndTheMovesAreAlreadyAvailable() {
        Game game = twoPlayers(() -> 6);
        List<Integer> movesSeen = new ArrayList<>();
        game.addListener(new GameListener() {
            @Override public void rolled(Player player, TurnRoll roll) {
                movesSeen.add(game.legalMoves().size());
            }
        });
        Recorder recorder = listen(game);
        game.roll();
        assertEquals(List.of("rolled Green 6"), recorder.events);
        assertEquals(List.of(4), movesSeen);
    }

    @Test
    void cancelledThirdSixIsReportedAndPassesTheTurn() {
        Game game = twoPlayers(() -> 6);
        game.roll();
        game.play(new Piece(PlayerColor.GREEN, 1));
        game.roll();
        game.play(new Piece(PlayerColor.GREEN, 2));
        Recorder recorder = listen(game);
        game.roll();
        assertEquals(List.of("rolled Green 6 cancelled", "turn Red"), recorder.events);
    }

    @Test
    void moveNotifiesMovedThenTurnPassedToNextPlayerOrToTheSamePlayerAfterASix() {
        Game game = twoPlayers(rolls(6, 3));
        Piece green = new Piece(PlayerColor.GREEN, 1);
        Recorder recorder = listen(game);
        List<Position> seenAt = new ArrayList<>();
        game.addListener(new GameListener() {
            @Override public void pieceMoved(Player player, MoveCandidate move) {
                seenAt.add(location(game, move.piece()));
            }
        });
        game.roll();
        MoveCandidate first = game.play(green);
        game.roll();
        MoveCandidate second = game.play(green);
        assertEquals(List.of("rolled Green 6", "moved Green 1", "turn Green",
                "rolled Green 3", "moved Green 1", "turn Red"), recorder.events);
        assertEquals(List.of(first.destination(), second.destination()), seenAt,
                "The piece is already on its destination when notified");
    }

    @Test
    void captureIsNotifiedAfterTheMoveWithOwnerAndTheCastleSlotThePieceIsAlreadyOn() {
        Game game = twoPlayers(() -> 1);
        List<Position> route = game.routes().get(PlayerColor.GREEN).positions();
        int target = 1;
        while (game.board().squareAt(route.get(target)).type() != SquareType.NORMAL) target++;
        Piece green = new Piece(PlayerColor.GREEN, 1);
        Piece red = new Piece(PlayerColor.RED, 1);
        Position freedSlot = location(game, red);
        relocate(game, green, route.get(target - 1));
        relocate(game, red, route.get(target));
        List<Position> reported = new ArrayList<>();
        game.addListener(new GameListener() {
            @Override public void pieceCaptured(Player owner, Piece piece, Position castleSlot) {
                assertEquals(PlayerColor.RED, owner.color());
                assertEquals(red, piece);
                assertEquals(castleSlot, location(game, red));
                reported.add(castleSlot);
            }
        });
        Recorder recorder = listen(game);
        game.roll();
        game.play(green);
        assertEquals(List.of("rolled Green 1", "moved Green 1", "captured Red 1", "turn Red"), recorder.events);
        assertEquals(List.of(freedSlot), reported);
    }

    @Test
    void finishingPlayerIsNotifiedWithPlaceAndTheGameContinues() {
        Game game = game(3, () -> 1);
        readyToFinish(game, game.players().getFirst(), 1);
        Recorder recorder = listen(game);
        game.roll();
        game.play(game.players().getFirst().pieces().getLast());
        assertEquals(List.of("rolled GREEN 1", "moved GREEN 4", "finished GREEN 1", "turn YELLOW"), recorder.events);
    }

    @Test
    void lastFinishNotifiesFinishedThenGameOverWithTheFinalRankingAndNoTurnPassed() {
        Game game = twoPlayers(() -> 6);
        Player green = game.players().getFirst();
        readyToFinish(game, green, 6);
        List<List<Player>> rankings = new ArrayList<>();
        game.addListener(new GameListener() {
            @Override public void gameOver(List<Player> ranking) {
                assertTrue(game.isFinished());
                rankings.add(ranking);
            }
        });
        Recorder recorder = listen(game);
        game.roll();
        game.play(green.pieces().getLast());
        assertEquals(List.of("rolled Green 6", "moved Green 4", "finished Green 1", "over [Green, Red]"),
                recorder.events);
        assertEquals(List.of(game.ranking()), rankings);
    }

    @Test
    void severalListenersAreNotifiedInRegistrationOrderForEachNotification() {
        Game game = twoPlayers(() -> 1);
        List<String> shared = new ArrayList<>();
        Recorder first = new Recorder("A ") {
            @Override public void rolled(Player player, TurnRoll roll) { shared.add("A rolled"); }
            @Override public void turnPassed(Player next) { shared.add("A turn"); }
        };
        Recorder second = new Recorder("B ") {
            @Override public void rolled(Player player, TurnRoll roll) { shared.add("B rolled"); }
            @Override public void turnPassed(Player next) { shared.add("B turn"); }
        };
        game.addListener(first);
        game.addListener(second);
        game.roll();
        assertEquals(List.of("A rolled", "B rolled", "A turn", "B turn"), shared);
    }

    @Test
    void unregisteredListenerHearsNothingAndRegisteringTwiceNotifiesOnce() {
        Game game = twoPlayers(() -> 1);
        Recorder kept = new Recorder();
        Recorder removed = new Recorder();
        game.addListener(kept);
        game.addListener(kept);
        game.addListener(removed);
        game.removeListener(removed);
        game.removeListener(new Recorder()); // Not registered: no effect.
        game.roll();
        assertEquals(List.of("rolled Green 1", "turn Red"), kept.events);
        assertTrue(removed.events.isEmpty());
        assertThrows(NullPointerException.class, () -> game.addListener(null));
    }

    @Test
    void listenerMayUnregisterItselfWhileBeingNotified() {
        Game game = twoPlayers(() -> 1);
        Recorder later = new Recorder();
        game.addListener(new GameListener() {
            @Override public void rolled(Player player, TurnRoll roll) { game.removeListener(this); }
        });
        game.addListener(later);
        game.roll();
        game.roll();
        assertEquals(List.of("rolled Green 1", "turn Red", "rolled Red 1", "turn Green"), later.events);
    }

    @Test
    void rejectedActionsNotifyNothing() {
        Game game = twoPlayers(() -> 6);
        Recorder recorder = listen(game);
        assertThrows(IllegalStateException.class, () -> game.play(new Piece(PlayerColor.GREEN, 1)));
        assertTrue(recorder.events.isEmpty(), "Move before rolling");

        game.roll();
        recorder.events.clear();
        assertThrows(IllegalStateException.class, game::roll);
        assertThrows(IllegalArgumentException.class, () -> game.play(new Piece(PlayerColor.RED, 1)));
        assertThrows(NullPointerException.class, () -> game.play(null));
        relocate(game, new Piece(PlayerColor.GREEN, 1), game.board().startFor(PlayerColor.GREEN));
        assertThrows(IllegalStateException.class, () -> game.play(new Piece(PlayerColor.GREEN, 1)));
        assertTrue(recorder.events.isEmpty(), "Second roll, wrong owner, null and stale move");

        Game over = twoPlayers(() -> 6);
        Player winner = over.players().getFirst();
        readyToFinish(over, winner, 6);
        over.roll();
        over.play(winner.pieces().getLast());
        Recorder afterEnd = listen(over);
        assertThrows(IllegalStateException.class, over::roll);
        assertThrows(IllegalStateException.class, () -> over.play(winner.pieces().getFirst()));
        assertTrue(afterEnd.events.isEmpty(), "Actions after game over");
    }

    @Test
    void fullGameEndsWithGameOverAndNeverAsksAQuestion() {
        Random dice = new Random(7);
        Game game = game(4, () -> dice.nextInt(6) + 1);
        Recorder recorder = listen(game);
        for (int actions = 0; !game.isFinished(); actions++) {
            assertTrue(actions < 100_000, "Game did not finish");
            game.roll();
            if (!game.legalMoves().isEmpty()) game.play(game.legalMoves().getFirst().piece());
        }
        assertEquals("over " + game.ranking().stream().map(Player::name).toList(), recorder.events.getLast());
        assertEquals(1, recorder.events.stream().filter(event -> event.startsWith("over")).count());
        assertEquals(3, recorder.events.stream().filter(event -> event.startsWith("finished")).count());
        assertTrue(recorder.events.stream().noneMatch(event -> event.startsWith("question")));
    }
}
