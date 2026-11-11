package Model;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class GamePlayTest {
    private Random firstPlayer() {
        return new Random() {
            @Override public int nextInt(int bound) { return 0; }
        };
    }

    private Game game(Die die) {
        return new Game(List.of(new Player("Green", PlayerColor.GREEN), new Player("Red", PlayerColor.RED)),
                die, new Random(42), firstPlayer());
    }

    private Die rolls(int... values) {
        AtomicInteger index = new AtomicInteger();
        return () -> values[index.getAndIncrement()];
    }

    private Piece piece(PlayerColor color, int number) { return new Piece(color, number); }

    private Position location(Game game, Piece piece) {
        return game.board().occupancy().entrySet().stream()
                .filter(entry -> entry.getValue().contains(piece)).findFirst().orElseThrow().getKey();
    }

    private void relocate(Game game, Piece piece, Position destination) {
        game.board().movePiece(piece, location(game, piece), destination);
    }

    private int indexOfType(Game game, SquareType type) {
        List<Position> route = game.routes().get(PlayerColor.GREEN).positions();
        for (int i = 1; i < 51; i++) {
            if (game.board().squareAt(route.get(i)).type() == type) return i;
        }
        throw new AssertionError("No shared target of type " + type);
    }

    @Test
    void chosenCastlePieceEntersStartAndSixAllowsAnotherRoll() {
        Game game = game(() -> 6);
        Piece green = piece(PlayerColor.GREEN, 2);
        Position source = location(game, green);
        game.roll();
        MoveCandidate expected = game.legalMoves().get(1);
        assertEquals(expected, game.play(green));
        assertTrue(game.board().piecesAt(source).isEmpty());
        assertEquals(List.of(green), game.board().piecesAt(game.board().startFor(PlayerColor.GREEN)));
        assertTrue(game.legalMoves().isEmpty());
        assertEquals(PlayerColor.GREEN, game.turnManager().currentPlayer().color());
        assertEquals(TurnPhase.WAITING_FOR_ROLL, game.turnManager().phase());
    }

    @Test
    void movesAlongStoredPathAndPassesTurnAfterNonSix() {
        Game game = game(() -> 3);
        Piece green = piece(PlayerColor.GREEN, 1);
        PlayerRoute route = game.routes().get(PlayerColor.GREEN);
        relocate(game, green, route.start());
        game.roll();
        MoveCandidate executed = game.play(green);
        assertEquals(route.positions().subList(1, 4), executed.path());
        assertEquals(route.positions().get(3), location(game, green));
        assertEquals(PlayerColor.RED, game.turnManager().currentPlayer().color());
        assertEquals(TurnPhase.WAITING_FOR_ROLL, game.turnManager().phase());
        assertTrue(game.legalMoves().isEmpty());
    }

    @Test
    void capturesLoneOpponentOnNormalSquareAndReturnsItToAnEmptyOwnCastleSlot() {
        Game game = game(() -> 1);
        int targetIndex = indexOfType(game, SquareType.NORMAL);
        List<Position> route = game.routes().get(PlayerColor.GREEN).positions();
        Piece green = piece(PlayerColor.GREEN, 1);
        Piece red = piece(PlayerColor.RED, 1);
        Position freedCastleSlot = location(game, red);
        relocate(game, green, route.get(targetIndex - 1));
        relocate(game, red, route.get(targetIndex));
        game.roll();
        game.play(green);
        assertEquals(List.of(green), game.board().piecesAt(route.get(targetIndex)));
        assertEquals(List.of(red), game.board().piecesAt(freedCastleSlot));
        assertEquals(SquareColor.RED, game.board().squareAt(location(game, red)).associatedColor());
        assertEquals(8, game.board().occupancy().values().stream().mapToInt(List::size).sum());
    }

    @Test
    void protectedAndStartSquaresAreSharedWithoutCapture() {
        for (SquareType type : List.of(SquareType.PROTECTED, SquareType.START)) {
            Game game = game(() -> 1);
            int targetIndex = indexOfType(game, type);
            List<Position> route = game.routes().get(PlayerColor.GREEN).positions();
            Piece green = piece(PlayerColor.GREEN, 1);
            Piece red = piece(PlayerColor.RED, 1);
            relocate(game, green, route.get(targetIndex - 1));
            relocate(game, red, route.get(targetIndex));
            game.roll();
            game.play(green);
            assertEquals(2, game.board().piecesAt(route.get(targetIndex)).size());
            assertTrue(game.board().piecesAt(route.get(targetIndex)).containsAll(List.of(green, red)));
            assertEquals(route.get(targetIndex), location(game, red));
        }
    }

    @Test
    void arrowIsNotProtectedAndLandingCaptures() {
        Game game = game(() -> 1);
        int targetIndex = indexOfType(game, SquareType.ARROW);
        List<Position> route = game.routes().get(PlayerColor.GREEN).positions();
        Piece green = piece(PlayerColor.GREEN, 1);
        Piece red = piece(PlayerColor.RED, 1);
        relocate(game, green, route.get(targetIndex - 1));
        relocate(game, red, route.get(targetIndex));
        game.roll();
        game.play(green);
        assertEquals(List.of(green), game.board().piecesAt(route.get(targetIndex)));
        assertEquals(SquareType.CASTLE, game.board().squareAt(location(game, red)).type());
    }

    @Test
    void joiningAndLeavingOwnBlockUpdatesOccupancyAndBlockDetection() {
        Game game = game(rolls(1, 2, 1));
        List<Position> route = game.routes().get(PlayerColor.GREEN).positions();
        Piece first = piece(PlayerColor.GREEN, 1);
        Piece second = piece(PlayerColor.GREEN, 2);
        relocate(game, first, route.get(0));
        relocate(game, second, route.get(1));
        game.roll();
        game.play(first);
        assertTrue(new MoveRules().isDoubleBlock(game.board().squareAt(route.get(1)), PlayerColor.GREEN));
        game.roll(); // Red has only Castle pieces and cannot move on 2.
        game.roll();
        game.play(first);
        assertFalse(new MoveRules().isDoubleBlock(game.board().squareAt(route.get(1)), PlayerColor.GREEN));
        assertEquals(List.of(second), game.board().piecesAt(route.get(1)));
        assertEquals(route.get(2), location(game, first));
    }

    @Test
    void executesShortenedMoveBeforeBlockWhenNoFullMoveExists() {
        Game game = game(() -> 3);
        List<Position> route = game.routes().get(PlayerColor.GREEN).positions();
        Piece green = piece(PlayerColor.GREEN, 1);
        relocate(game, green, route.get(0));
        relocate(game, piece(PlayerColor.RED, 1), route.get(2));
        relocate(game, piece(PlayerColor.RED, 2), route.get(2));
        game.roll();
        assertEquals(List.of(route.get(1)), game.play(green).path());
        assertEquals(route.get(1), location(game, green));
        assertEquals(2, game.board().piecesAt(route.get(2)).size());
    }

    @Test
    void rejectsMoveBeforeRollWrongOwnerAndPieceWithoutCandidateWithoutStateChanges() {
        Game game = game(() -> 1);
        Piece green = piece(PlayerColor.GREEN, 1);
        Position start = game.routes().get(PlayerColor.GREEN).start();
        relocate(game, green, start);
        var before = game.board().occupancy();
        assertThrows(IllegalStateException.class, () -> game.play(green));
        game.roll();
        List<MoveCandidate> choices = game.legalMoves();
        assertThrows(IllegalArgumentException.class, () -> game.play(piece(PlayerColor.RED, 1)));
        assertThrows(IllegalArgumentException.class, () -> game.play(piece(PlayerColor.GREEN, 2)));
        assertEquals(before, game.board().occupancy());
        assertEquals(choices, game.legalMoves());
        assertEquals(TurnPhase.WAITING_FOR_MOVE, game.turnManager().phase());
    }

    @Test
    void cannotExecuteSameRollTwiceOrUseOldChoiceDuringExtraTurn() {
        Game game = game(() -> 6);
        Piece green = piece(PlayerColor.GREEN, 1);
        game.roll();
        game.play(green);
        var after = game.board().occupancy();
        assertThrows(IllegalStateException.class, () -> game.play(piece(PlayerColor.GREEN, 2)));
        assertEquals(after, game.board().occupancy());
        assertTrue(game.legalMoves().isEmpty());
    }

    @Test
    void thirdSixDoesNotMovePiecesAfterFirstTwoMovesHaveExecuted() {
        Game game = game(() -> 6);
        game.roll();
        game.play(piece(PlayerColor.GREEN, 1));
        game.roll();
        game.play(piece(PlayerColor.GREEN, 2));
        var beforeThird = game.board().occupancy();
        assertEquals(new TurnRoll(6, true), game.roll());
        assertEquals(beforeThird, game.board().occupancy());
        assertEquals(PlayerColor.RED, game.turnManager().currentPlayer().color());
        assertThrows(IllegalStateException.class, () -> game.play(piece(PlayerColor.GREEN, 1)));
    }

    @Test
    void rejectsStoredMoveWhenBoardSourceOrPathChangesAfterRolling() {
        Game game = game(() -> 6);
        Piece green = piece(PlayerColor.GREEN, 1);
        game.roll();
        relocate(game, green, game.board().startFor(PlayerColor.GREEN));
        var changed = game.board().occupancy();
        assertThrows(IllegalStateException.class, () -> game.play(green));
        assertEquals(changed, game.board().occupancy());

        Game blocked = game(() -> 6);
        blocked.roll();
        Position start = blocked.board().startFor(PlayerColor.GREEN);
        relocate(blocked, piece(PlayerColor.RED, 1), start);
        relocate(blocked, piece(PlayerColor.RED, 2), start);
        var blockedBefore = blocked.board().occupancy();
        assertThrows(IllegalStateException.class, () -> blocked.play(green));
        assertEquals(blockedBefore, blocked.board().occupancy());
    }

    @Test
    void exactFinishRetiresPieceFromFutureMoveGeneration() {
        Game game = game(rolls(1, 2, 1));
        Piece green = piece(PlayerColor.GREEN, 1);
        PlayerRoute route = game.routes().get(PlayerColor.GREEN);
        relocate(game, green, route.positions().get(route.size() - 2));
        game.roll();
        game.play(green);
        assertEquals(route.finish(), location(game, green));
        game.roll(); // Red cannot leave Castle on 2.
        game.roll(); // Green's finished piece and Castle pieces cannot move on 1.
        assertTrue(game.legalMoves().isEmpty());
        assertEquals(route.finish(), location(game, green));
    }
}
