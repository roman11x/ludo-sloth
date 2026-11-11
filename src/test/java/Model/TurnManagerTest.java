package Model;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class TurnManagerTest {
    private List<Player> players(int count) {
        List<Player> result = new ArrayList<>();
        List<PlayerColor> clockwise = List.of(PlayerColor.GREEN, PlayerColor.YELLOW,
                PlayerColor.BLUE, PlayerColor.RED);
        for (int i = 0; i < count; i++) {
            result.add(new Player("Player " + (i + 1), clockwise.get(i)));
        }
        return result;
    }

    private Random firstAt(int index) {
        return new Random() {
            @Override public int nextInt(int bound) {
                assertTrue(index >= 0 && index < bound);
                return index;
            }
        };
    }

    private Die rolls(int... values) {
        AtomicInteger index = new AtomicInteger();
        return () -> values[index.getAndIncrement()];
    }

    @Test
    void anyParticipantCanBeSelectedFirstForTwoThreeAndFourPlayers() {
        for (int count = 2; count <= 4; count++) {
            List<Player> participants = players(count);
            for (int first = 0; first < count; first++) {
                TurnManager turns = new TurnManager(participants, () -> 1, firstAt(first));
                assertSame(participants.get(first), turns.currentPlayer());
                assertEquals(participants, turns.players());
                assertEquals(TurnPhase.WAITING_FOR_ROLL, turns.phase());
                assertTrue(turns.isActive(participants.get(first).color()));
            }
        }
    }

    @Test
    void followsClockwiseOrderAndWrapsAroundFromSelectedStartingPlayer() {
        for (int count = 2; count <= 4; count++) {
            List<Player> participants = players(count);
            TurnManager turns = new TurnManager(participants, () -> 2, firstAt(count - 1));
            for (int step = 0; step < count * 2; step++) {
                Player expected = participants.get((count - 1 + step) % count);
                assertSame(expected, turns.currentPlayer());
                assertEquals(new TurnRoll(2, false), turns.roll(expected.color()));
                assertEquals(TurnPhase.WAITING_FOR_MOVE, turns.phase());
                turns.requireCanMove(expected.color(), expected.pieces().getFirst());
                turns.completeTurn(expected.color());
                assertEquals(TurnPhase.WAITING_FOR_ROLL, turns.phase());
            }
        }
    }

    @Test
    void allInputOrdersAndPlayerSubsetsProduceClockwiseTurnsFromEveryStartingPlayer() {
        List<PlayerColor> clockwise = List.of(PlayerColor.GREEN, PlayerColor.YELLOW,
                PlayerColor.BLUE, PlayerColor.RED);
        for (int mask = 0; mask < 16; mask++) {
            if (Integer.bitCount(mask) < 2) continue;
            List<Player> expected = new ArrayList<>();
            for (int i = 0; i < clockwise.size(); i++) {
                if ((mask & (1 << i)) != 0) {
                    PlayerColor color = clockwise.get(i);
                    expected.add(new Player(color.name(), color));
                }
            }
            for (List<Player> input : permutations(expected)) {
                List<Player> originalOrder = List.copyOf(input);
                for (int first = 0; first < expected.size(); first++) {
                    TurnManager turns = new TurnManager(input, () -> 2, firstAt(first));
                    assertEquals(expected, turns.players());
                    for (int step = 0; step <= expected.size(); step++) {
                        Player player = expected.get((first + step) % expected.size());
                        assertSame(player, turns.currentPlayer());
                        turns.roll(player.color());
                        turns.completeTurn(player.color());
                    }
                }
                assertEquals(originalOrder, input, "Sorting must not mutate the caller's list");
            }
        }
    }

    private List<List<Player>> permutations(List<Player> players) {
        if (players.isEmpty()) return List.of(List.of());
        List<List<Player>> result = new ArrayList<>();
        for (int i = 0; i < players.size(); i++) {
            List<Player> remaining = new ArrayList<>(players);
            Player first = remaining.remove(i);
            for (List<Player> suffix : permutations(remaining)) {
                List<Player> order = new ArrayList<>();
                order.add(first);
                order.addAll(suffix);
                result.add(order);
            }
        }
        return result;
    }

    @Test
    void inactivePlayerCannotRollSelectPieceOrCompleteTurn() {
        List<Player> participants = players(2);
        AtomicInteger dieCalls = new AtomicInteger();
        TurnManager turns = new TurnManager(participants, () -> { dieCalls.incrementAndGet(); return 3; }, firstAt(0));
        Player active = participants.get(0);
        Player other = participants.get(1);
        assertThrows(IllegalStateException.class, () -> turns.roll(other.color()));
        assertEquals(0, dieCalls.get());
        turns.roll(active.color());
        assertThrows(IllegalStateException.class, () -> turns.requireCanMove(other.color(), other.pieces().getFirst()));
        assertThrows(IllegalStateException.class, () -> turns.completeTurn(other.color()));
        assertSame(active, turns.currentPlayer());
        assertEquals(TurnPhase.WAITING_FOR_MOVE, turns.phase());
    }

    @Test
    void cannotMoveBeforeRollingOrRollTwiceBeforeResolvingMove() {
        List<Player> participants = players(2);
        Player active = participants.getFirst();
        TurnManager turns = new TurnManager(participants, () -> 2, firstAt(0));
        assertThrows(IllegalStateException.class, () -> turns.requireCanMove(active.color(), active.pieces().getFirst()));
        assertThrows(IllegalStateException.class, () -> turns.completeTurn(active.color()));
        turns.roll(active.color());
        assertThrows(IllegalStateException.class, () -> turns.roll(active.color()));
        turns.completeTurn(active.color());
        assertThrows(IllegalStateException.class, () -> turns.completeTurn(active.color()));
    }

    @Test
    void activePlayerCannotChooseAnotherPlayersPiece() {
        List<Player> participants = players(2);
        TurnManager turns = new TurnManager(participants, () -> 2, firstAt(0));
        turns.roll(participants.getFirst().color());
        assertThrows(IllegalArgumentException.class, () -> turns.requireCanMove(
                participants.getFirst().color(), participants.getLast().pieces().getFirst()));
        assertEquals(TurnPhase.WAITING_FOR_MOVE, turns.phase());
    }

    @Test
    void sixGrantsAnotherTurnOnlyAfterCurrentMoveIsResolved() {
        List<Player> participants = players(2);
        Player active = participants.getFirst();
        TurnManager turns = new TurnManager(participants, rolls(6, 2), firstAt(0));
        assertEquals(new TurnRoll(6, false), turns.roll(active.color()));
        assertThrows(IllegalStateException.class, () -> turns.roll(active.color()));
        turns.completeTurn(active.color());
        assertSame(active, turns.currentPlayer());
        assertEquals(TurnPhase.WAITING_FOR_ROLL, turns.phase());
        turns.roll(active.color());
        turns.completeTurn(active.color());
        assertSame(participants.getLast(), turns.currentPlayer());
    }

    @Test
    void thirdConsecutiveSixCancelsMovementAndPassesTurnImmediately() {
        List<Player> participants = players(2);
        Player active = participants.getFirst();
        TurnManager turns = new TurnManager(participants, rolls(6, 6, 6, 6), firstAt(0));
        for (int i = 0; i < 2; i++) {
            assertFalse(turns.roll(active.color()).cancelled());
            turns.completeTurn(active.color());
        }
        assertEquals(new TurnRoll(6, true), turns.roll(active.color()));
        assertSame(participants.getLast(), turns.currentPlayer());
        assertEquals(TurnPhase.WAITING_FOR_ROLL, turns.phase());
        assertThrows(IllegalStateException.class, () -> turns.requireCanMove(active.color(), active.pieces().getFirst()));
        // The following player's first six is not cancelled.
        assertFalse(turns.roll(participants.getLast().color()).cancelled());
    }

    @Test
    void sixCounterResetsWhenPlayerChangesAndOnANonSix() {
        List<Player> participants = players(2);
        TurnManager turns = new TurnManager(participants, rolls(6, 2, 6, 2, 6), firstAt(0));
        for (int i = 0; i < 5; i++) {
            PlayerColor color = turns.currentPlayer().color();
            assertFalse(turns.roll(color).cancelled());
            turns.completeTurn(color);
        }
    }

    @Test
    void noLegalMoveCanResolveRollUsingTheSameTurnTransition() {
        List<Player> participants = players(2);
        TurnManager turns = new TurnManager(participants, rolls(2, 6), firstAt(0));
        turns.roll(participants.getFirst().color());
        // Caller found no legal moves; resolve without selecting a piece.
        turns.completeTurn(participants.getFirst().color());
        assertSame(participants.getLast(), turns.currentPlayer());
        turns.roll(participants.getLast().color());
        turns.completeTurn(participants.getLast().color());
        assertSame(participants.getLast(), turns.currentPlayer());
    }

    @Test
    void participantOrderCannotBeChangedThroughOriginalOrReturnedList() {
        List<Player> original = players(3);
        TurnManager turns = new TurnManager(original, () -> 1, firstAt(0));
        original.clear();
        assertEquals(3, turns.players().size());
        assertThrows(UnsupportedOperationException.class, () -> turns.players().clear());
    }

    @Test
    void invalidDiceResultLeavesTurnUnchanged() {
        for (int value : new int[]{0, 7}) {
            List<Player> participants = players(2);
            TurnManager turns = new TurnManager(participants, () -> value, firstAt(0));
            assertThrows(IllegalStateException.class, () -> turns.roll(participants.getFirst().color()));
            assertSame(participants.getFirst(), turns.currentPlayer());
            assertEquals(TurnPhase.WAITING_FOR_ROLL, turns.phase());
        }
    }

    @Test
    void rejectsInvalidParticipantsAndMissingDependencies() {
        assertThrows(IllegalArgumentException.class, () -> new TurnManager(players(1)));
        List<Player> tooMany = players(4);
        tooMany.add(new Player("Extra", PlayerColor.RED));
        assertThrows(IllegalArgumentException.class, () -> new TurnManager(tooMany));
        assertThrows(IllegalArgumentException.class, () -> new TurnManager(List.of(
                new Player("One", PlayerColor.RED), new Player("Two", PlayerColor.RED))));
        assertThrows(NullPointerException.class, () -> new TurnManager(null));
        assertThrows(NullPointerException.class, () -> new TurnManager(players(2), null, new Random()));
        assertThrows(NullPointerException.class, () -> new TurnManager(players(2), () -> 1, null));
    }
}
