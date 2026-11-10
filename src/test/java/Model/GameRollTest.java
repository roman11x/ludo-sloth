package Model;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class GameRollTest {
    private Random firstAt(int index) {
        return new Random() {
            @Override public int nextInt(int bound) { return index; }
        };
    }

    private Game game(Die die) {
        return new Game(List.of(new Player("Green", PlayerColor.GREEN),
                new Player("Red", PlayerColor.RED)), die, new Random(42), firstAt(0));
    }

    private void relocate(Game game, PlayerColor color, int number, Position destination) {
        Piece piece = new Piece(color, number);
        Position source = game.board().occupancy().entrySet().stream()
                .filter(entry -> entry.getValue().contains(piece)).findFirst().orElseThrow().getKey();
        game.board().movePiece(piece, source, destination);
    }

    @Test
    void rollsOneToFiveWithAllPiecesInCastlePassTurnWithoutMovingPieces() {
        for (int roll = 1; roll <= 5; roll++) {
            int value = roll;
            Game game = game(() -> value);
            var before = game.board().occupancy();
            assertEquals(new TurnRoll(value, false), game.roll());
            assertTrue(game.legalMoves().isEmpty());
            assertEquals(PlayerColor.RED, game.turnManager().currentPlayer().color());
            assertEquals(TurnPhase.WAITING_FOR_ROLL, game.turnManager().phase());
            assertEquals(before, game.board().occupancy());
        }
    }

    @Test
    void sixCreatesFourImmutableCastleExitChoicesWithoutMovingAnyPiece() {
        Game game = game(() -> 6);
        assertTrue(game.legalMoves().isEmpty());
        var before = game.board().occupancy();
        assertEquals(new TurnRoll(6, false), game.roll());
        assertEquals(4, game.legalMoves().size());
        assertEquals(List.of(1, 2, 3, 4), game.legalMoves().stream().map(c -> c.piece().number()).toList());
        for (MoveCandidate candidate : game.legalMoves()) {
            assertEquals(PlayerColor.GREEN, candidate.piece().color());
            assertEquals(List.of(game.board().startFor(PlayerColor.GREEN)), candidate.path());
            assertEquals(SquareType.CASTLE, game.board().squareAt(candidate.source()).type());
        }
        assertEquals(PlayerColor.GREEN, game.turnManager().currentPlayer().color());
        assertEquals(TurnPhase.WAITING_FOR_MOVE, game.turnManager().phase());
        assertThrows(UnsupportedOperationException.class, () -> game.legalMoves().clear());
        assertEquals(before, game.board().occupancy());
    }

    @Test
    void secondRollIsRejectedAndPreservesOriginalChoicesWithoutRollingDieAgain() {
        AtomicInteger calls = new AtomicInteger();
        Game game = game(() -> { calls.incrementAndGet(); return 6; });
        game.roll();
        List<MoveCandidate> original = game.legalMoves();
        assertThrows(IllegalStateException.class, game::roll);
        assertEquals(1, calls.get());
        assertEquals(original, game.legalMoves());
        assertEquals(TurnPhase.WAITING_FOR_MOVE, game.turnManager().phase());
    }

    @Test
    void blockedPathIsShortenedByRulesWhenNoFullMoveExists() {
        Game game = game(() -> 3);
        List<Position> route = game.routes().get(PlayerColor.GREEN).positions();
        relocate(game, PlayerColor.GREEN, 1, route.get(0));
        relocate(game, PlayerColor.RED, 1, route.get(2));
        relocate(game, PlayerColor.RED, 2, route.get(2));
        var before = game.board().occupancy();
        game.roll();
        assertEquals(1, game.legalMoves().size());
        assertEquals(List.of(route.get(1)), game.legalMoves().getFirst().path());
        assertEquals(TurnPhase.WAITING_FOR_MOVE, game.turnManager().phase());
        assertEquals(before, game.board().occupancy());
    }

    @Test
    void fullMoveOfAnotherPieceSuppressesShortenedMove() {
        Game game = game(() -> 3);
        List<Position> route = game.routes().get(PlayerColor.GREEN).positions();
        relocate(game, PlayerColor.GREEN, 1, route.get(0));
        relocate(game, PlayerColor.GREEN, 2, route.get(10));
        relocate(game, PlayerColor.RED, 1, route.get(2));
        relocate(game, PlayerColor.RED, 2, route.get(2));
        game.roll();
        assertEquals(1, game.legalMoves().size());
        assertEquals(new Piece(PlayerColor.GREEN, 2), game.legalMoves().getFirst().piece());
        assertEquals(route.subList(11, 14), game.legalMoves().getFirst().path());
    }

    @Test
    void sixWithNoLegalMoveGrantsExtraRollAndThirdSixCancelsAndPassesTurn() {
        Game game = game(() -> 6);
        Position blockedStart = game.board().startFor(PlayerColor.GREEN);
        relocate(game, PlayerColor.RED, 1, blockedStart);
        relocate(game, PlayerColor.RED, 2, blockedStart);
        var before = game.board().occupancy();
        for (int i = 0; i < 2; i++) {
            assertEquals(new TurnRoll(6, false), game.roll());
            assertTrue(game.legalMoves().isEmpty());
            assertEquals(PlayerColor.GREEN, game.turnManager().currentPlayer().color());
            assertEquals(TurnPhase.WAITING_FOR_ROLL, game.turnManager().phase());
        }
        assertEquals(new TurnRoll(6, true), game.roll());
        assertTrue(game.legalMoves().isEmpty());
        assertEquals(PlayerColor.RED, game.turnManager().currentPlayer().color());
        assertEquals(TurnPhase.WAITING_FOR_ROLL, game.turnManager().phase());
        assertEquals(before, game.board().occupancy());
    }

    @Test
    void generatesChoicesOnlyForRandomlyChosenActivePlayerInTwoThreeAndFourPlayerGames() {
        for (int count = 2; count <= 4; count++) {
            List<Player> players = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                PlayerColor color = PlayerColor.values()[i];
                players.add(new Player(color.name(), color));
            }
            for (int first = 0; first < count; first++) {
                Game game = new Game(players, () -> 6, new Random(42), firstAt(first));
                PlayerColor active = game.turnManager().currentPlayer().color();
                game.roll();
                assertEquals(4, game.legalMoves().size());
                assertTrue(game.legalMoves().stream().allMatch(candidate -> candidate.piece().color() == active));
            }
        }
    }
}
