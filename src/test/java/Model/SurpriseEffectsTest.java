package Model;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class SurpriseEffectsTest {
    private static final List<PlayerColor> COLORS = List.of(
            PlayerColor.GREEN, PlayerColor.YELLOW, PlayerColor.BLUE, PlayerColor.RED);

    private record Fixture(Game game, Piece piece, AtomicInteger dieCalls, AtomicLong nanos) {
        Position at(int index) { return game.routes().get(piece.color()).positions().get(index); }
        Position location(Piece p) {
            return game.board().occupancy().entrySet().stream().filter(e -> e.getValue().contains(p))
                    .map(Map.Entry::getKey).findFirst().orElseThrow();
        }
        void relocate(Piece p, Position to) { game.board().movePiece(p, location(p), to); }
        PendingSquareEffect pending() { return game.pendingSquareEffect().orElseThrow(); }
        void landFrom(int index) {
            relocate(piece, at(index));
            game.roll();
            game.play(piece);
            assertEquals(TurnPhase.WAITING_FOR_EFFECT, game.turnManager().phase());
        }
        Piece opponent(int number) {
            return game.players().stream().filter(p -> p.color() != piece.color()).findFirst().orElseThrow()
                    .pieces().get(number - 1);
        }
    }

    /** Actual Medium geometry/routes, with controlled special locations instead of random placement. */
    private Fixture fixture(List<PlayerColor> colors, PlayerColor active, int roll, Map<Integer, SquareType> specials) {
        var routes = PlayerRouteFactory.createMedium();
        var path = routes.get(active).positions();
        Board board = new BoardLayout() {
            private final MediumLayout medium = new MediumLayout();
            @Override protected void createCommonSquares(Square[][] s) { medium.createCommonSquares(s); }
            @Override protected void createStartsAndArrows(Square[][] s) { medium.createStartsAndArrows(s); }
            @Override protected void createHomesAndCastles(Square[][] s) { medium.createHomesAndCastles(s); }
            @Override protected void placeSpecialSquares(Square[][] s, Random random) {
                specials.forEach((index, type) -> {
                    Position position = path.get(index);
                    assertEquals(SquareType.NORMAL, s[position.row()][position.column()].type());
                    defineSquare(s, position.row(), position.column(), type, SquareColor.NEUTRAL,
                            SquareStatus.ACTIVE, type == SquareType.QUESTION ? QuestionDifficulty.MEDIUM : null);
                });
            }
        }.build(new Random(42));
        List<Player> players = COLORS.stream().filter(colors::contains).map(c -> new Player(c.name(), c)).toList();
        AtomicInteger calls = new AtomicInteger();
        AtomicLong nanos = new AtomicLong();
        Game game = new Game(players, Difficulty.MEDIUM, () -> { calls.incrementAndGet(); return roll; },
                new Random() { @Override public int nextInt(int bound) { return colorsInOrder().indexOf(active); }
                    private List<PlayerColor> colorsInOrder() { return players.stream().map(Player::color).toList(); }
                }, board, routes, Clock.fixed(Instant.parse("2026-11-26T00:00:00Z"), ZoneOffset.UTC), nanos::get);
        return new Fixture(game, new Piece(active, 1), calls, nanos);
    }

    private Fixture fixture(int roll, Map<Integer, SquareType> specials) {
        return fixture(List.of(PlayerColor.GREEN, PlayerColor.RED), PlayerColor.GREEN, roll, specials);
    }

    @Test
    void directTransfersUseThePiecesOwnFinishOrCastleForEveryColorAndKeepOtherPiecesUntouched() {
        for (PlayerColor color : COLORS) {
            for (SurpriseEvent event : List.of(SurpriseEvent.MOVE_TO_FINISH, SurpriseEvent.RETURN_TO_CASTLE)) {
                Fixture f = fixture(COLORS, color, 1, Map.of(6, SquareType.SURPRISE));
                Position castle = f.location(f.piece());
                f.landFrom(5);
                var before = f.game().board().occupancy();
                var request = f.pending();
                MoveCandidate move = f.game().resolveSurpriseEffect(request.id(), event).orElseThrow();
                Position destination = event == SurpriseEvent.MOVE_TO_FINISH
                        ? f.game().routes().get(color).finish() : castle;
                assertEquals(destination, f.location(f.piece()));
                assertEquals(f.at(6), move.source());
                assertEquals(List.of(destination), move.path(), "A transfer has no traversed route squares");
                before.forEach((position, pieces) -> pieces.stream().filter(p -> !p.equals(f.piece()))
                        .forEach(p -> assertEquals(position, f.location(p))));
                assertEquals(1, f.game().board().occupancy().values().stream()
                        .filter(pieces -> pieces.contains(f.piece())).count());
                assertTrue(f.game().pendingSquareEffect().isEmpty());
                assertEquals(1, f.dieCalls().get());
            }
        }
    }

    @Test
    void finishTransferIgnoresDistanceAndInterveningBlocksAndDoesNotCaptureOnSharedSurprise() {
        Fixture f = fixture(1, Map.of(6, SquareType.SURPRISE));
        Piece shared = f.opponent(1), block1 = f.opponent(2), block2 = f.opponent(3);
        f.relocate(shared, f.at(6));
        f.relocate(block1, f.at(8));
        f.relocate(block2, f.at(8));
        List<MoveCandidate> moves = new ArrayList<>();
        AtomicInteger captures = new AtomicInteger();
        f.game().addListener(new GameListener() {
            @Override public void pieceMoved(Player p, MoveCandidate move) { moves.add(move); }
            @Override public void pieceCaptured(Player p, Piece piece, Position slot) { captures.incrementAndGet(); }
        });
        f.landFrom(5);
        f.game().resolveSurpriseEffect(f.pending().id(), SurpriseEvent.MOVE_TO_FINISH);
        assertEquals(f.game().routes().get(PlayerColor.GREEN).finish(), f.location(f.piece()));
        assertEquals(f.at(6), f.location(shared));
        assertEquals(List.of(block1, block2), f.game().board().piecesAt(f.at(8)));
        assertEquals(0, captures.get());
        assertEquals(2, moves.size(), "One normal move and one transfer notification");
        assertEquals(PlayerColor.RED, f.game().turnManager().currentPlayer().color());
    }

    @Test
    void castleReturnUsesAFreeOwnSlotWhenACapturePreviouslyOccupiedTheOriginalSlot() {
        Fixture f = fixture(1, Map.of(6, SquareType.SURPRISE));
        Position original = f.location(f.piece());
        f.landFrom(5);
        Piece own2 = new Piece(PlayerColor.GREEN, 2);
        Position vacated = f.location(own2);
        f.relocate(own2, original);
        f.game().resolveSurpriseEffect(f.pending().id(), SurpriseEvent.RETURN_TO_CASTLE);
        assertEquals(vacated, f.location(f.piece()));
        assertEquals(original, f.location(own2));
        assertEquals(List.of(f.piece()), f.game().board().piecesAt(vacated));
        assertEquals(SquareType.CASTLE, f.game().board().squareAt(vacated).type());
    }

    @Test
    void backThreeUsesReverseRouteAndCapturesALoneUnprotectedOpponent() {
        Fixture f = fixture(1, Map.of(6, SquareType.SURPRISE));
        Piece opponent = f.opponent(1);
        Position castle = f.location(opponent);
        f.relocate(opponent, f.at(3));
        List<String> events = new ArrayList<>();
        f.landFrom(5);
        f.game().addListener(new GameListener() {
            @Override public void pieceMoved(Player p, MoveCandidate move) { events.add("move"); }
            @Override public void pieceCaptured(Player p, Piece piece, Position slot) { events.add("capture"); }
            @Override public void turnPassed(Player p) { events.add("turn"); }
        });
        MoveCandidate move = f.game().resolveSurpriseEffect(f.pending().id(), SurpriseEvent.MOVE_BACK_THREE).orElseThrow();
        assertEquals(List.of(f.at(5), f.at(4), f.at(3)), move.path());
        assertEquals(f.at(3), f.location(f.piece()));
        assertEquals(castle, f.location(opponent));
        assertEquals(List.of("move", "capture", "turn"), events);
        assertEquals(1, f.dieCalls().get());
    }

    @Test
    void backThreeSharesProtectedDestinationsWithoutCapture() {
        Fixture f = fixture(1, Map.of(6, SquareType.SURPRISE, 3, SquareType.PROTECTED));
        Piece opponent = f.opponent(1);
        f.relocate(opponent, f.at(3));
        f.landFrom(5);
        f.game().resolveSurpriseEffect(f.pending().id(), SurpriseEvent.MOVE_BACK_THREE);
        assertTrue(f.game().board().piecesAt(f.at(3)).containsAll(List.of(f.piece(), opponent)));
    }

    @Test
    void backThreeStopsBeforeDoubleBlockOrConsumesWithoutRetriggeringWhenFullyBlocked() {
        for (int blockIndex : List.of(4, 5)) {
            Fixture f = fixture(1, Map.of(6, SquareType.SURPRISE));
            AtomicInteger requests = new AtomicInteger();
            f.game().addListener(new GameListener() {
                @Override public void squareEffectRequired(PendingSquareEffect effect) { requests.incrementAndGet(); }
            });
            f.landFrom(5);
            // Isolate the event's reverse-path checks from the preceding dice move.
            f.relocate(f.opponent(1), f.at(blockIndex));
            f.relocate(f.opponent(2), f.at(blockIndex));
            var move = f.game().resolveSurpriseEffect(f.pending().id(), SurpriseEvent.MOVE_BACK_THREE);
            assertEquals(blockIndex == 4, move.isPresent());
            assertEquals(f.at(blockIndex + 1), f.location(f.piece()));
            assertEquals(1, requests.get());
            assertTrue(f.game().pendingSquareEffect().isEmpty());
            assertEquals(PlayerColor.RED, f.game().turnManager().currentPlayer().color());
        }
    }

    @Test
    void backThreeClampsAtStartWithoutReturningToCastle() {
        Fixture f = fixture(1, Map.of(1, SquareType.SURPRISE));
        f.landFrom(0);
        MoveCandidate move = f.game().resolveSurpriseEffect(f.pending().id(), SurpriseEvent.MOVE_BACK_THREE).orElseThrow();
        assertEquals(List.of(f.at(0)), move.path());
        assertEquals(SquareType.START, f.game().board().squareAt(f.location(f.piece())).type());
    }

    @Test
    void backwardLandingOnQuestionOrSurpriseRequestsTheNextEffectBeforeCompletingTheTurn() {
        for (SquareType destinationType : List.of(SquareType.QUESTION, SquareType.SURPRISE)) {
            Fixture f = fixture(1, Map.of(6, SquareType.SURPRISE, 3, destinationType));
            f.landFrom(5);
            long first = f.pending().id();
            f.game().resolveSurpriseEffect(first, SurpriseEvent.MOVE_BACK_THREE);
            assertEquals(destinationType, f.pending().square().type());
            assertTrue(f.pending().id() > first);
            assertEquals(f.piece(), f.pending().piece());
            assertEquals(PlayerColor.GREEN, f.game().turnManager().currentPlayer().color());
            assertThrows(IllegalStateException.class,
                    () -> f.game().resolveSurpriseEffect(first, SurpriseEvent.RETURN_TO_CASTLE));
            if (destinationType == SquareType.SURPRISE) {
                f.game().resolveSurpriseEffect(f.pending().id(), SurpriseEvent.RETURN_TO_CASTLE);
            } else {
                f.game().resolveSquareEffect(f.pending().id(), 0);
            }
            assertTrue(f.game().pendingSquareEffect().isEmpty());
            assertEquals(PlayerColor.RED, f.game().turnManager().currentPlayer().color());
        }
    }

    @Test
    void fourthPieceFinishesTheGameOnlyAfterTheEffectAndFreezesDurationEvenOnSix() {
        Fixture f = fixture(6, Map.of(6, SquareType.SURPRISE));
        Player green = f.game().players().getFirst();
        Position finish = f.game().routes().get(PlayerColor.GREEN).finish();
        for (Piece piece : green.pieces().subList(1, 4)) f.relocate(piece, finish);
        List<String> events = new ArrayList<>();
        f.game().addListener(new GameListener() {
            @Override public void pieceMoved(Player p, MoveCandidate move) { events.add("move"); }
            @Override public void playerFinished(Player p, int place) { events.add("rank " + place); }
            @Override public void gameOver(List<Player> ranking) { events.add("over"); }
            @Override public void turnPassed(Player p) { events.add("turn"); }
        });
        f.landFrom(0);
        assertTrue(f.game().ranking().isEmpty());
        assertFalse(f.game().isFinished());
        f.nanos().set(200);
        f.game().resolveSurpriseEffect(f.pending().id(), SurpriseEvent.MOVE_TO_FINISH);
        assertEquals(List.of("move", "move", "rank 1", "over"), events);
        assertEquals(f.game().players(), f.game().ranking());
        assertTrue(f.game().isFinished());
        assertEquals(Duration.ofNanos(200), f.game().duration());
        f.nanos().set(1000);
        assertEquals(Duration.ofNanos(200), f.game().duration());
        assertThrows(IllegalStateException.class, f.game()::roll);
    }

    @Test
    void finishingWithThreePlayersSkipsTheFinisherAndCancelsTheirSixExtraTurn() {
        Fixture f = fixture(COLORS.subList(0, 3), PlayerColor.GREEN, 6, Map.of(6, SquareType.SURPRISE));
        Player green = f.game().players().getFirst();
        for (Piece p : green.pieces().subList(1, 4)) f.relocate(p, f.game().routes().get(p.color()).finish());
        f.landFrom(0);
        f.game().resolveSurpriseEffect(f.pending().id(), SurpriseEvent.MOVE_TO_FINISH);
        assertFalse(f.game().isFinished());
        assertEquals(List.of(green), f.game().ranking());
        assertEquals(PlayerColor.YELLOW, f.game().turnManager().currentPlayer().color());
    }

    @Test
    void ordinarySixStillGrantsItsExtraTurnAfterReturnToCastleWithoutAnExtraRollForTheEvent() {
        Fixture f = fixture(6, Map.of(6, SquareType.SURPRISE, 12, SquareType.SURPRISE));
        f.landFrom(0);
        f.game().resolveSurpriseEffect(f.pending().id(), SurpriseEvent.RETURN_TO_CASTLE);
        assertEquals(PlayerColor.GREEN, f.game().turnManager().currentPlayer().color());
        f.relocate(f.piece(), f.at(6));
        f.game().roll();
        f.game().play(f.piece());
        f.game().resolveSurpriseEffect(f.pending().id(), SurpriseEvent.RETURN_TO_CASTLE);
        assertTrue(f.game().roll().cancelled());
        assertEquals(3, f.dieCalls().get());
        assertEquals(PlayerColor.RED, f.game().turnManager().currentPlayer().color());
    }

    @Test
    void invalidOrRepeatedEventsDoNotConsumeOrChangeAPendingRequest() {
        Fixture f = fixture(1, Map.of(6, SquareType.SURPRISE));
        assertThrows(IllegalStateException.class,
                () -> f.game().resolveSurpriseEffect(1, SurpriseEvent.MOVE_TO_FINISH));
        f.landFrom(5);
        long id = f.pending().id();
        var snapshot = f.game().board().occupancy();
        assertThrows(NullPointerException.class, () -> f.game().resolveSurpriseEffect(id, null));
        for (SurpriseEvent event : List.of(SurpriseEvent.MOVE_OPPONENT_BACKWARD,
                SurpriseEvent.SKIP_NEXT_TURN, SurpriseEvent.EXTRA_ULTRA_TURN)) {
            assertThrows(IllegalArgumentException.class, () -> f.game().resolveSurpriseEffect(id, event));
        }
        assertThrows(IllegalStateException.class,
                () -> f.game().resolveSurpriseEffect(id + 1, SurpriseEvent.MOVE_TO_FINISH));
        assertEquals(snapshot, f.game().board().occupancy());
        assertEquals(id, f.pending().id());
        f.game().resolveSurpriseEffect(id, SurpriseEvent.RETURN_TO_CASTLE);
        var after = f.game().board().occupancy();
        assertThrows(IllegalStateException.class,
                () -> f.game().resolveSurpriseEffect(id, SurpriseEvent.MOVE_TO_FINISH));
        assertEquals(after, f.game().board().occupancy());

        Fixture question = fixture(1, Map.of(6, SquareType.QUESTION));
        question.landFrom(5);
        var original = question.game().board().occupancy();
        assertThrows(IllegalArgumentException.class, () -> question.game().resolveSurpriseEffect(
                question.pending().id(), SurpriseEvent.MOVE_TO_FINISH));
        assertEquals(original, question.game().board().occupancy());
        assertTrue(question.game().pendingSquareEffect().isPresent());
    }

    @Test
    void backThreeCrossesTheOuterArrowInReverseForAllColors() {
        for (PlayerColor color : COLORS) {
            Fixture f = fixture(COLORS, color, 1, Map.of(59, SquareType.SURPRISE));
            f.landFrom(58);
            MoveCandidate move = f.game().resolveSurpriseEffect(f.pending().id(), SurpriseEvent.MOVE_BACK_THREE)
                    .orElseThrow();
            assertEquals(List.of(f.at(58), f.at(57), f.at(56)), move.path());
            assertEquals(SquareType.ARROW, f.game().board().squareAt(f.at(57)).type());
        }
    }

    @Test
    void anOpponentFinishingDuringAChainGetsTheirOwnRankAndIsSkippedOrEndsTheGame() {
        for (int count : List.of(2, 3)) {
            List<PlayerColor> colors = count == 2 ? List.of(PlayerColor.GREEN, PlayerColor.RED)
                    : List.of(PlayerColor.GREEN, PlayerColor.YELLOW, PlayerColor.RED);
            Fixture f = fixture(colors, PlayerColor.GREEN, 1,
                    Map.of(6, SquareType.SURPRISE, 52, SquareType.SURPRISE));
            Player red = f.game().players().getLast();
            var redRoute = f.game().routes().get(PlayerColor.RED);
            assertEquals(f.at(52), redRoute.positions().get(4));
            for (Piece p : red.pieces().subList(1, 4)) f.relocate(p, redRoute.finish());
            f.relocate(red.pieces().getFirst(), redRoute.positions().get(7));
            f.landFrom(5);
            f.game().resolveSquareEffect(f.pending().id(), red.pieces().getFirst(), -3);
            assertEquals(PlayerColor.GREEN, f.pending().player().color());
            assertEquals(red.pieces().getFirst(), f.pending().piece());
            f.game().resolveSurpriseEffect(f.pending().id(), SurpriseEvent.MOVE_TO_FINISH);
            assertTrue(f.game().hasFinished(red));
            assertSame(red, f.game().ranking().getFirst());
            if (count == 2) {
                assertTrue(f.game().isFinished());
                assertEquals(List.of(red, f.game().players().getFirst()), f.game().ranking());
            } else {
                assertFalse(f.game().isFinished());
                assertEquals(PlayerColor.YELLOW, f.game().turnManager().currentPlayer().color());
                f.game().roll(); // Yellow has no move on 1; Red must be skipped.
                assertEquals(PlayerColor.GREEN, f.game().turnManager().currentPlayer().color());
                assertEquals(List.of(red), f.game().ranking());
            }
        }
    }

    @Test
    void aStaleLandingRejectsDirectTransfersWithoutClearingTheRequest() {
        for (SurpriseEvent event : List.of(SurpriseEvent.MOVE_TO_FINISH, SurpriseEvent.RETURN_TO_CASTLE)) {
            Fixture f = fixture(1, Map.of(6, SquareType.SURPRISE));
            f.landFrom(5);
            var request = f.pending();
            f.relocate(f.piece(), f.at(7));
            var snapshot = f.game().board().occupancy();
            assertThrows(IllegalStateException.class, () -> f.game().resolveSurpriseEffect(request.id(), event));
            assertEquals(snapshot, f.game().board().occupancy());
            assertEquals(request, f.pending());
        }
    }
}
