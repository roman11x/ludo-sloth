package Model;

import org.junit.jupiter.api.Test;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class GameDurationTest {
    private final AtomicLong nanos = new AtomicLong(10_000_000_000L);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-09T10:00:00Z"), ZoneOffset.UTC);

    private Game game(int count, Die die, Clock wallClock) {
        List<Player> players = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            PlayerColor color = PlayerColor.values()[i];
            players.add(new Player(color.name(), color));
        }
        return new Game(players, die, new Random(42), new Random(7), wallClock, nanos::get);
    }

    private void advance(Duration duration) { nanos.addAndGet(duration.toNanos()); }

    private void relocate(Game game, Piece piece, Position destination) {
        Position source = game.board().occupancy().entrySet().stream()
                .filter(entry -> entry.getValue().contains(piece)).findFirst().orElseThrow().getKey();
        game.board().movePiece(piece, source, destination);
    }

    private void readyToFinish(Game game, Player player) {
        PlayerRoute route = game.routes().get(player.color());
        for (int i = 0; i < 3; i++) relocate(game, player.pieces().get(i), route.finish());
        relocate(game, player.pieces().getLast(), route.positions().get(route.size() - 2));
    }

    private void finishCurrentPlayer(Game game) {
        Player player = game.turnManager().currentPlayer();
        game.roll();
        game.play(player.pieces().getLast());
    }

    @Test
    void startsAtZeroAfterInitializationAndExposesTheStartTimestamp() {
        Game game = game(2, () -> 1, clock);
        assertEquals(Duration.ZERO, game.duration());
        assertEquals(LocalDateTime.of(2026, 11, 9, 10, 0), game.startTime());
        advance(Duration.ofSeconds(5).plusNanos(123));
        assertEquals(Duration.ofSeconds(5).plusNanos(123), game.duration());
        assertEquals(LocalDateTime.of(2026, 11, 9, 10, 0), game.startTime());
    }

    @Test
    void timeContinuesAcrossRollsAndTurnsWithoutResetting() {
        Game game = game(2, () -> 1, clock);
        Player first = game.turnManager().currentPlayer();
        advance(Duration.ofSeconds(10));
        game.roll(); // No Castle exit, so advance to the next player.
        assertNotSame(first, game.turnManager().currentPlayer());
        advance(Duration.ofSeconds(7));
        game.roll();
        assertSame(first, game.turnManager().currentPlayer());
        assertEquals(Duration.ofSeconds(17), game.duration());
    }

    @Test
    void waitingForPieceSelectionAndExtraTurnsCountTowardDuration() {
        Game game = game(2, () -> 6, clock);
        Player active = game.turnManager().currentPlayer();
        advance(Duration.ofSeconds(2));
        game.roll();
        advance(Duration.ofSeconds(30));
        assertEquals(Duration.ofSeconds(32), game.duration());
        game.play(active.pieces().getFirst());
        advance(Duration.ofSeconds(4));
        assertSame(active, game.turnManager().currentPlayer());
        assertEquals(Duration.ofSeconds(36), game.duration());
    }

    @Test
    void stopsOnlyWhenGameCompletesForTwoThreeAndFourPlayers() {
        for (int count = 2; count <= 4; count++) {
            Game game = game(count, () -> 1, clock);
            for (Player player : game.players()) readyToFinish(game, player);
            for (int finished = 1; finished < count; finished++) {
                advance(Duration.ofSeconds(10));
                finishCurrentPlayer(game);
                assertEquals(Duration.ofSeconds(finished * 10), game.duration());
                assertEquals(finished == count - 1, game.isFinished());
            }
            Duration result = Duration.ofSeconds((count - 1) * 10);
            advance(Duration.ofHours(2));
            assertEquals(result, game.duration());
            assertThrows(IllegalStateException.class, game::roll);
            assertEquals(result, game.duration());
        }
    }

    @Test
    void completingAMoveOnSixFreezesTimeWhenItEndsTheGame() {
        Game game = game(2, () -> 6, clock);
        Player player = game.turnManager().currentPlayer();
        PlayerRoute route = game.routes().get(player.color());
        readyToFinish(game, player);
        relocate(game, player.pieces().getLast(), route.positions().get(route.size() - 7));
        advance(Duration.ofSeconds(12));
        game.roll();
        advance(Duration.ofSeconds(3));
        game.play(player.pieces().getLast());
        assertTrue(game.isFinished());
        advance(Duration.ofMinutes(1));
        assertEquals(Duration.ofSeconds(15), game.duration());
    }

    @Test
    void changingWallClockDoesNotChangeElapsedDurationOrRecordedStart() {
        AtomicReference<Instant> wallTime = new AtomicReference<>(clock.instant());
        Clock mutableClock = new Clock() {
            @Override public ZoneId getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(ZoneId zone) { return Clock.fixed(instant(), zone); }
            @Override public Instant instant() { return wallTime.get(); }
        };
        Game game = game(2, () -> 1, mutableClock);
        LocalDateTime start = game.startTime();
        advance(Duration.ofSeconds(8));
        wallTime.set(clock.instant().minus(Duration.ofDays(2)));
        assertEquals(Duration.ofSeconds(8), game.duration());
        advance(Duration.ofSeconds(4));
        wallTime.set(clock.instant().plus(Duration.ofDays(2)));
        assertEquals(Duration.ofSeconds(12), game.duration());
        assertEquals(start, game.startTime());
    }

    @Test
    void gamesCreatedAtDifferentTimesHaveIndependentStartTimesAndFinalDurations() {
        Game first = game(2, () -> 1, clock);
        advance(Duration.ofSeconds(10));
        Game second = game(2, () -> 1, clock);
        assertEquals(Duration.ofSeconds(10), first.duration());
        assertEquals(Duration.ZERO, second.duration());
        readyToFinish(first, first.turnManager().currentPlayer());
        advance(Duration.ofSeconds(5));
        finishCurrentPlayer(first);
        advance(Duration.ofSeconds(3));
        assertEquals(Duration.ofSeconds(15), first.duration());
        assertEquals(Duration.ofSeconds(8), second.duration());
        assertFalse(second.isFinished());
    }

    @Test
    void usesElapsedDifferenceEvenWhenNanoTimeOriginIsNegativeOrWraps() {
        nanos.set(-500);
        Game negativeOrigin = game(2, () -> 1, clock);
        nanos.addAndGet(100);
        assertEquals(Duration.ofNanos(100), negativeOrigin.duration());
        nanos.set(Long.MAX_VALUE - 5);
        Game wrap = game(2, () -> 1, clock);
        nanos.addAndGet(20);
        assertEquals(Duration.ofNanos(20), wrap.duration());
    }
}
