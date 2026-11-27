package Model;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class OpponentBackwardSurpriseTest {
    private static final List<PlayerColor> COLORS = List.of(
            PlayerColor.GREEN, PlayerColor.YELLOW, PlayerColor.BLUE, PlayerColor.RED);
    private static final Piece GREEN = new Piece(PlayerColor.GREEN, 1);
    private static final Piece RED = new Piece(PlayerColor.RED, 1);

    private record Fixture(Game game, AtomicInteger draws, int firstRoll) {
        Position at(PlayerColor color, int index) { return game.routes().get(color).positions().get(index); }
        Position location(Piece piece) {
            return game.board().occupancy().entrySet().stream().filter(e -> e.getValue().contains(piece))
                    .map(Map.Entry::getKey).findFirst().orElseThrow();
        }
        void move(Piece piece, Position to) { game.board().movePiece(piece, location(piece), to); }
        long land() {
            move(GREEN, at(PlayerColor.GREEN, 6 - firstRoll));
            game.roll();
            game.play(GREEN);
            return id();
        }
        long id() { return game.pendingSquareEffect().orElseThrow().id(); }
        PendingOpponentMove choice() { return game.pendingOpponentMove().orElseThrow(); }
        PendingOpponentMove choose(long id, PlayerColor color) { return game.chooseOpponent(id, color).orElseThrow(); }
        void begin(long id) { game.beginOpponentBackward(id); }
    }

    /** Real Medium geometry/routes; controlled special placement and scripted Standard results. */
    private Fixture fixture(List<PlayerColor> colors, Map<Position, SquareType> specials, int... rolls) {
        var routes = PlayerRouteFactory.createMedium();
        Position surprise = routes.get(PlayerColor.GREEN).positions().get(6);
        Board board = new BoardLayout() {
            private final MediumLayout medium = new MediumLayout();
            @Override protected void createCommonSquares(Square[][] s) { medium.createCommonSquares(s); }
            @Override protected void createStartsAndArrows(Square[][] s) { medium.createStartsAndArrows(s); }
            @Override protected void createHomesAndCastles(Square[][] s) { medium.createHomesAndCastles(s); }
            @Override protected void placeSpecialSquares(Square[][] s, Random random) {
                defineSquare(s, surprise.row(), surprise.column(), SquareType.SURPRISE,
                        SquareColor.NEUTRAL, SquareStatus.ACTIVE);
                specials.forEach((p, type) -> defineSquare(s, p.row(), p.column(), type, SquareColor.NEUTRAL,
                        SquareStatus.ACTIVE, type == SquareType.QUESTION ? QuestionDifficulty.MEDIUM : null));
            }
        }.build(new Random(42));
        AtomicInteger draws = new AtomicInteger();
        Game game = new Game(COLORS.stream().filter(colors::contains).map(c -> new Player(c.name(), c)).toList(),
                Difficulty.MEDIUM, () -> {
                    int index = draws.getAndIncrement();
                    assertTrue(index < rolls.length, "Unexpected additional die draw");
                    return rolls[index];
                }, new Random() { @Override public int nextInt(int bound) { return 0; } },
                board, routes, Clock.systemUTC(), () -> 0L);
        return new Fixture(game, draws, rolls[0]);
    }

    private Fixture fixture(int... rolls) {
        return fixture(List.of(PlayerColor.GREEN, PlayerColor.RED), Map.of(), rolls);
    }

    @Test
    void selectsOpponentBeforeDrawingAndRoundsEachStandardResultUpForTwoToFourPlayers() {
        for (int count = 2; count <= 4; count++) {
            for (int roll = 1; roll <= 6; roll++) {
                Fixture f = fixture(COLORS.subList(0, count), Map.of(), 1, roll);
                PlayerColor opponent = COLORS.get(count - 1);
                Piece piece = new Piece(opponent, 1);
                f.move(piece, f.at(opponent, 7));
                long id = f.land();
                assertTrue(f.game().resolveSurpriseEffect(id, SurpriseEvent.MOVE_OPPONENT_BACKWARD).isEmpty());
                PendingOpponentMove initial = f.choice();
                assertEquals(count - 1, initial.opponents().size());
                assertNull(initial.selectedOpponent());
                assertEquals(0, initial.roll());
                assertEquals(0, initial.steps());
                assertEquals(1, f.draws().get(), "Beginning the event must not roll");
                PendingOpponentMove choice = f.choose(id, opponent);
                int steps = (roll + 1) / 2;
                assertEquals(roll, choice.roll());
                assertEquals(steps, choice.steps());
                assertEquals(opponent, choice.selectedOpponent().color());
                assertEquals(List.of(piece), choice.moves().stream().map(MoveCandidate::piece).toList());
                assertEquals(steps, choice.moves().getFirst().path().size());
                assertEquals(0, initial.roll(), "Previously returned snapshots stay unchanged");
                assertEquals(2, f.draws().get());
                MoveCandidate executed = f.game().playOpponentPiece(id, piece);
                assertEquals(choice.moves().getFirst(), executed);
                assertEquals(f.at(opponent, 7 - steps), f.location(piece));
                assertEquals(f.at(PlayerColor.GREEN, 6), f.location(GREEN));
                assertTrue(f.game().pendingOpponentMove().isEmpty());
                assertTrue(f.game().pendingSquareEffect().isEmpty());
                assertEquals(COLORS.get(1), f.game().turnManager().currentPlayer().color());
                assertEquals(2, f.draws().get());
            }
        }
    }

    @Test
    void retargetsAnIneligibleOpponentUsingExactlyTheSameRoll() {
        Fixture f = fixture(List.of(PlayerColor.GREEN, PlayerColor.YELLOW, PlayerColor.RED), Map.of(), 1, 5);
        f.move(RED, f.at(PlayerColor.RED, 7));
        long id = f.land();
        f.begin(id);
        PendingOpponentMove noYellowPiece = f.choose(id, PlayerColor.YELLOW);
        assertTrue(noYellowPiece.moves().isEmpty());
        assertFalse(noYellowPiece.movesByOpponent().get(PlayerColor.RED).isEmpty());
        assertEquals(TurnPhase.WAITING_FOR_EFFECT, f.game().turnManager().phase());
        PendingOpponentMove redChoice = f.choose(id, PlayerColor.RED);
        assertEquals(5, redChoice.roll());
        assertEquals(3, redChoice.steps());
        assertEquals(2, f.draws().get());
        assertTrue(noYellowPiece.moves().isEmpty(), "Retargeting cannot mutate an earlier snapshot");
        assertThrows(IllegalStateException.class, () -> f.game().chooseOpponent(id, PlayerColor.YELLOW));
        f.game().playOpponentPiece(id, RED);
        assertEquals(f.at(PlayerColor.RED, 4), f.location(RED));
    }

    @Test
    void cancelsWhenNoOpponentCanMoveWithoutMovingPiecesOrRetriggeringTheSurprise() {
        Fixture f = fixture(1, 6);
        List<String> events = new ArrayList<>();
        f.game().addListener(new GameListener() {
            @Override public void rolled(Player p, TurnRoll r) { events.add("normal roll"); }
            @Override public void squareEffectRequired(PendingSquareEffect effect) { events.add("square"); }
            @Override public void opponentMoveRequired(PendingOpponentMove choice) { events.add("opponent"); }
            @Override public void opponentMoveCancelled(PendingSquareEffect effect, int roll) {
                assertEquals(6, roll);
                assertTrue(f.game().pendingOpponentMove().isEmpty());
                events.add("cancel");
            }
            @Override public void turnPassed(Player p) { events.add("turn"); }
        });
        long id = f.land();
        var before = f.game().board().occupancy();
        f.begin(id);
        assertTrue(f.game().chooseOpponent(id, PlayerColor.RED).isEmpty());
        assertEquals(before, f.game().board().occupancy());
        assertEquals(List.of("normal roll", "square", "opponent", "cancel", "turn"), events);
        assertEquals(PlayerColor.RED, f.game().turnManager().currentPlayer().color(), "Event six grants no bonus");
        assertTrue(f.game().pendingSquareEffect().isEmpty());
        assertEquals(2, f.draws().get());
    }

    @Test
    void excludesCastleHomeFinishAndStartPiecesButAllowsOtherPlacedPieces() {
        for (String location : List.of("castle", "home", "finish", "start")) {
            Fixture f = fixture(1, 6);
            switch (location) {
                case "home" -> f.move(RED, f.at(PlayerColor.RED, 110));
                case "finish" -> f.move(RED, f.game().routes().get(PlayerColor.RED).finish());
                case "start" -> f.move(RED, f.at(PlayerColor.RED, 0));
                case "castle" -> { }
            }
            Piece allowed = new Piece(PlayerColor.RED, 2);
            f.move(allowed, f.at(PlayerColor.RED, 7));
            long id = f.land();
            f.begin(id);
            PendingOpponentMove choice = f.choose(id, PlayerColor.RED);
            assertEquals(List.of(allowed), choice.moves().stream().map(MoveCandidate::piece).toList(), location);
            var before = f.game().board().occupancy();
            assertThrows(IllegalArgumentException.class, () -> f.game().playOpponentPiece(id, RED));
            assertEquals(before, f.game().board().occupancy());
        }
    }

    @Test
    void excludesFullyBlockedPiecesAndKeepsPartialMovesEvenIfAnotherPieceHasAFullMove() {
        Fixture f = fixture(1, 6);
        Piece red2 = new Piece(PlayerColor.RED, 2), red3 = new Piece(PlayerColor.RED, 3);
        f.move(RED, f.at(PlayerColor.RED, 7));
        f.move(red2, f.at(PlayerColor.RED, 6));
        f.move(red3, f.at(PlayerColor.RED, 12));
        f.move(new Piece(PlayerColor.GREEN, 2), f.at(PlayerColor.RED, 5));
        f.move(new Piece(PlayerColor.GREEN, 3), f.at(PlayerColor.RED, 5));
        long id = f.land();
        f.begin(id);
        PendingOpponentMove choice = f.choose(id, PlayerColor.RED);
        assertEquals(List.of(RED, red3), choice.moves().stream().map(MoveCandidate::piece).toList());
        assertEquals(List.of(f.at(PlayerColor.RED, 6)), choice.moves().getFirst().path());
        assertEquals(3, choice.moves().getLast().path().size());
        f.game().playOpponentPiece(id, RED);
        assertEquals(f.at(PlayerColor.RED, 6), f.location(RED));
        assertEquals(f.at(PlayerColor.RED, 6), f.location(red2));
    }

    @Test
    void cancelsIfTheOnlyOtherwiseEligiblePieceIsBlockedOnItsFirstStep() {
        Fixture f = fixture(1, 3);
        f.move(RED, f.at(PlayerColor.RED, 7));
        f.move(new Piece(PlayerColor.GREEN, 2), f.at(PlayerColor.RED, 6));
        f.move(new Piece(PlayerColor.GREEN, 3), f.at(PlayerColor.RED, 6));
        long id = f.land();
        f.begin(id);
        assertTrue(f.game().chooseOpponent(id, PlayerColor.RED).isEmpty());
        assertEquals(f.at(PlayerColor.RED, 7), f.location(RED));
        assertEquals(TurnPhase.WAITING_FOR_ROLL, f.game().turnManager().phase());
    }

    @Test
    void reverseMovementClampsAtTheOpponentsStart() {
        Fixture f = fixture(1, 6);
        f.move(RED, f.at(PlayerColor.RED, 1));
        long id = f.land();
        f.begin(id);
        var choice = f.choose(id, PlayerColor.RED);
        assertEquals(3, choice.steps());
        assertEquals(List.of(f.at(PlayerColor.RED, 0)), choice.moves().getFirst().path());
        f.game().playOpponentPiece(id, RED);
        assertEquals(SquareType.START, f.game().board().squareAt(f.location(RED)).type());
    }

    @Test
    void reusesCaptureAndReportsTheMovingOpponentAsThePieceOwner() {
        Fixture f = fixture(1, 5);
        Piece captured = new Piece(PlayerColor.GREEN, 2);
        // Capture uses the first free own slot; Green #1 vacates this slot before the event.
        Position castle = f.location(GREEN);
        f.move(captured, f.at(PlayerColor.RED, 4));
        f.move(RED, f.at(PlayerColor.RED, 7));
        long id = f.land();
        f.begin(id);
        f.choose(id, PlayerColor.RED);
        List<String> events = new ArrayList<>();
        f.game().addListener(new GameListener() {
            @Override public void pieceMoved(Player p, MoveCandidate move) {
                assertEquals(PlayerColor.RED, p.color());
                assertEquals(RED, move.piece());
                events.add("move");
            }
            @Override public void pieceCaptured(Player p, Piece piece, Position slot) {
                assertEquals(PlayerColor.GREEN, p.color());
                assertEquals(captured, piece);
                assertEquals(castle, slot);
                events.add("capture");
            }
            @Override public void turnPassed(Player p) { events.add("turn"); }
        });
        f.game().playOpponentPiece(id, RED);
        assertEquals(List.of("move", "capture", "turn"), events);
        assertEquals(castle, f.location(captured));
    }

    @Test
    void protectedDestinationIsSharedAndTheMovingPieceCanLeaveItsOwnDoubleBlock() {
        Position protectedSquare = PlayerRouteFactory.createMedium().get(PlayerColor.RED).positions().get(4);
        Fixture f = fixture(List.of(PlayerColor.GREEN, PlayerColor.RED),
                Map.of(protectedSquare, SquareType.PROTECTED), 1, 6);
        Piece red2 = new Piece(PlayerColor.RED, 2), green2 = new Piece(PlayerColor.GREEN, 2);
        f.move(RED, f.at(PlayerColor.RED, 7));
        f.move(red2, f.at(PlayerColor.RED, 7));
        f.move(green2, protectedSquare);
        long id = f.land();
        f.begin(id);
        f.choose(id, PlayerColor.RED);
        f.game().playOpponentPiece(id, RED);
        assertEquals(f.at(PlayerColor.RED, 7), f.location(red2));
        assertTrue(f.game().board().piecesAt(protectedSquare).containsAll(List.of(RED, green2)));
    }

    @Test
    void newQuestionOrSurpriseContinuesTheChainForTheOriginalPlayerWithoutKeepingOldSelection() {
        Position destination = PlayerRouteFactory.createMedium().get(PlayerColor.RED).positions().get(4);
        for (SquareType type : List.of(SquareType.QUESTION, SquareType.SURPRISE)) {
            Fixture f = fixture(List.of(PlayerColor.GREEN, PlayerColor.RED), Map.of(destination, type), 1, 6);
            f.move(RED, f.at(PlayerColor.RED, 7));
            long id = f.land();
            f.begin(id);
            f.choose(id, PlayerColor.RED);
            f.game().playOpponentPiece(id, RED);
            PendingSquareEffect next = f.game().pendingSquareEffect().orElseThrow();
            assertTrue(next.id() > id);
            assertEquals(RED, next.piece());
            assertEquals(PlayerColor.GREEN, next.player().color());
            assertEquals(type, next.square().type());
            assertTrue(f.game().pendingOpponentMove().isEmpty());
            assertEquals(TurnPhase.WAITING_FOR_EFFECT, f.game().turnManager().phase());
            assertThrows(IllegalStateException.class, () -> f.game().playOpponentPiece(id, RED));
            if (type == SquareType.SURPRISE) {
                f.game().resolveSurpriseEffect(next.id(), SurpriseEvent.RETURN_TO_CASTLE);
            } else {
                f.game().resolveSquareEffect(next.id(), 0);
            }
            assertTrue(f.game().pendingSquareEffect().isEmpty());
            assertEquals(PlayerColor.RED, f.game().turnManager().currentPlayer().color());
            assertEquals(2, f.draws().get());
        }
    }

    @Test
    void eventRollNeitherCountsNorResetsConsecutiveNormalSixes() {
        for (int eventRoll : List.of(1, 6)) {
            Fixture f = fixture(6, eventRoll, 6, eventRoll, 6);
            f.move(RED, f.at(PlayerColor.RED, 12));
            long first = f.land();
            f.begin(first);
            f.choose(first, PlayerColor.RED);
            f.game().playOpponentPiece(first, RED);
            assertEquals(PlayerColor.GREEN, f.game().turnManager().currentPlayer().color());
            f.move(GREEN, f.at(PlayerColor.GREEN, 0));
            assertFalse(f.game().roll().cancelled());
            f.game().play(GREEN);
            long second = f.id();
            f.begin(second);
            f.choose(second, PlayerColor.RED);
            f.game().playOpponentPiece(second, RED);
            assertEquals(PlayerColor.GREEN, f.game().turnManager().currentPlayer().color());
            assertTrue(f.game().roll().cancelled(), "Only the third normal six cancels");
            assertEquals(PlayerColor.RED, f.game().turnManager().currentPlayer().color());
            assertEquals(5, f.draws().get());
        }
    }

    @Test
    void rejectsWrongPhaseOpponentPieceAndBypassAttemptsWithoutChangingStateOrRerolling() {
        Fixture f = fixture(1, 4);
        f.move(RED, f.at(PlayerColor.RED, 7));
        assertThrows(IllegalStateException.class, () -> f.game().chooseOpponent(1, PlayerColor.RED));
        long id = f.land();
        f.begin(id);
        var before = f.game().board().occupancy();
        assertThrows(IllegalStateException.class, () -> f.game().playOpponentPiece(id, RED));
        assertThrows(IllegalArgumentException.class, () -> f.game().chooseOpponent(id, PlayerColor.GREEN));
        assertThrows(IllegalArgumentException.class, () -> f.game().chooseOpponent(id, PlayerColor.BLUE));
        assertThrows(NullPointerException.class, () -> f.game().chooseOpponent(id, null));
        assertThrows(IllegalStateException.class, () -> f.game().chooseOpponent(id + 1, PlayerColor.RED));
        assertThrows(IllegalStateException.class, () -> f.game().beginOpponentBackward(id));
        assertThrows(IllegalStateException.class, () -> f.game().resolveSquareEffect(id, 0));
        assertThrows(IllegalStateException.class,
                () -> f.game().resolveSurpriseEffect(id, SurpriseEvent.RETURN_TO_CASTLE));
        assertThrows(IllegalStateException.class, f.game()::roll);
        assertThrows(IllegalStateException.class, () -> f.game().play(GREEN));
        assertEquals(1, f.draws().get());
        f.choose(id, PlayerColor.RED);
        assertThrows(IllegalArgumentException.class, () -> f.game().playOpponentPiece(id, GREEN));
        assertThrows(IllegalArgumentException.class,
                () -> f.game().playOpponentPiece(id, new Piece(PlayerColor.RED, 2)));
        assertThrows(NullPointerException.class, () -> f.game().playOpponentPiece(id, null));
        assertEquals(before, f.game().board().occupancy());
        f.game().playOpponentPiece(id, RED);
        var after = f.game().board().occupancy();
        assertThrows(IllegalStateException.class, () -> f.game().playOpponentPiece(id, RED));
        assertThrows(IllegalStateException.class, () -> f.game().chooseOpponent(id, PlayerColor.RED));
        assertEquals(after, f.game().board().occupancy());
        assertEquals(2, f.draws().get());
    }

    @Test
    void revalidatesTheChosenSourceAndPathAndPreservesPendingInputOnStaleChoices() {
        for (boolean changeSource : List.of(false, true)) {
            Fixture f = fixture(1, 6);
            f.move(RED, f.at(PlayerColor.RED, 7));
            long id = f.land();
            f.begin(id);
            PendingOpponentMove choice = f.choose(id, PlayerColor.RED);
            if (changeSource) f.move(RED, f.at(PlayerColor.RED, 8));
            else {
                f.move(new Piece(PlayerColor.GREEN, 2), f.at(PlayerColor.RED, 6));
                f.move(new Piece(PlayerColor.GREEN, 3), f.at(PlayerColor.RED, 6));
            }
            var before = f.game().board().occupancy();
            assertThrows(IllegalStateException.class, () -> f.game().playOpponentPiece(id, RED));
            assertEquals(before, f.game().board().occupancy());
            assertEquals(choice, f.choice());
            assertEquals(TurnPhase.WAITING_FOR_EFFECT, f.game().turnManager().phase());
            assertEquals(2, f.draws().get());
        }
    }

    @Test
    void rejectsInvalidEventDieValuesWithoutAcceptingAChoiceOrCompletingTheTurn() {
        for (int invalid : List.of(0, 7)) {
            Fixture f = fixture(1, invalid);
            f.move(RED, f.at(PlayerColor.RED, 7));
            long id = f.land();
            f.begin(id);
            var original = f.choice();
            var before = f.game().board().occupancy();
            assertThrows(IllegalStateException.class, () -> f.game().chooseOpponent(id, PlayerColor.RED));
            assertEquals(original, f.choice());
            assertEquals(before, f.game().board().occupancy());
            assertEquals(0, f.choice().roll());
            assertEquals(TurnPhase.WAITING_FOR_EFFECT, f.game().turnManager().phase());
        }
    }

    @Test
    void choiceCollectionsAreDeeplyReadOnlyAndNotificationSnapshotsFollowAcceptedActionsOnly() {
        Fixture f = fixture(1, 6);
        f.move(RED, f.at(PlayerColor.RED, 7));
        List<PendingOpponentMove> notifications = new ArrayList<>();
        f.game().addListener(new GameListener() {
            @Override public void opponentMoveRequired(PendingOpponentMove choice) {
                assertEquals(choice, f.choice());
                assertEquals(TurnPhase.WAITING_FOR_EFFECT, f.game().turnManager().phase());
                notifications.add(choice);
            }
        });
        long id = f.land();
        f.begin(id);
        f.choose(id, PlayerColor.RED);
        var choice = f.choice();
        assertThrows(UnsupportedOperationException.class, () -> choice.opponents().clear());
        assertThrows(UnsupportedOperationException.class, () -> choice.movesByOpponent().clear());
        assertThrows(UnsupportedOperationException.class,
                () -> choice.movesByOpponent().get(PlayerColor.RED).clear());
        assertThrows(UnsupportedOperationException.class, () -> choice.moves().getFirst().path().clear());
        assertThrows(IllegalArgumentException.class, () -> f.game().chooseOpponent(id, PlayerColor.GREEN));
        assertEquals(2, notifications.size());
        assertEquals(0, notifications.getFirst().roll());
        assertEquals(6, notifications.getLast().roll());
    }

    @Test
    void cannotBeginOpponentSelectionOnAQuestionOrChooseAFinishedOpponent() {
        Position question = PlayerRouteFactory.createMedium().get(PlayerColor.GREEN).positions().get(6);
        Fixture q = fixture(List.of(PlayerColor.GREEN, PlayerColor.RED), Map.of(question, SquareType.QUESTION), 1);
        long id = q.land();
        assertThrows(IllegalArgumentException.class, () -> q.game().beginOpponentBackward(id));
        assertTrue(q.game().pendingOpponentMove().isEmpty());
        assertEquals(1, q.draws().get());

        Fixture f = fixture(List.of(PlayerColor.GREEN, PlayerColor.YELLOW, PlayerColor.RED), Map.of(), 1);
        Player red = f.game().players().getLast();
        for (Piece piece : red.pieces()) f.move(piece, f.game().routes().get(PlayerColor.RED).finish());
        long request = f.land();
        f.begin(request);
        assertEquals(List.of(PlayerColor.YELLOW), f.choice().opponents().stream().map(Player::color).toList());
        assertThrows(IllegalArgumentException.class, () -> f.game().chooseOpponent(request, PlayerColor.RED));
        assertEquals(1, f.draws().get());
    }
}
