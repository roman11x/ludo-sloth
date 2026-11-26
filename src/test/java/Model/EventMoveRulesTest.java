package Model;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class EventMoveRulesTest {
    private final MoveRules rules = new MoveRules();

    @Test
    void acceptsGeneratedBackwardMovesAcrossBothMediumArrowsForEveryColor() {
        Board board = Board.createMedium();
        var routes = PlayerRouteFactory.createMedium();
        var generator = new MoveGenerator(routes);
        for (PlayerColor color : PlayerColor.values()) {
            Piece piece = new Piece(color, 1);
            var path = routes.get(color).positions();
            for (int index : new int[]{58, 110}) {
                board.placePiece(piece, path.get(index));
                var candidate = generator.generateBackward(board, piece, 2).orElseThrow();
                var before = board.occupancy();
                assertEquals(candidate, rules.legalEventMove(board, candidate).orElseThrow());
                assertEquals(before, board.occupancy());
                board.removePiece(piece, path.get(index));
            }
        }
    }

    @Test
    void eventCanStopBeforeABlockEvenWhenAnotherPieceHasAFullMove() {
        Board board = Board.createMedium();
        var routes = PlayerRouteFactory.createMedium();
        var path = routes.get(PlayerColor.GREEN).positions();
        var generator = new MoveGenerator(routes);
        Piece affected = new Piece(PlayerColor.GREEN, 1);
        Piece other = new Piece(PlayerColor.GREEN, 2);
        board.placePiece(affected, path.get(5));
        board.placePiece(other, path.get(12));
        board.placePiece(new Piece(PlayerColor.RED, 1), path.get(3));
        board.placePiece(new Piece(PlayerColor.RED, 2), path.get(3));
        var event = generator.generateBackward(board, affected, 3).orElseThrow();
        var full = generator.generateBackward(board, other, 3).orElseThrow();
        var before = board.occupancy();
        var result = rules.legalEventMove(board, event).orElseThrow();
        assertEquals(List.of(path.get(4)), result.path());
        assertEquals(affected, result.piece());
        assertEquals(List.of(full), rules.legalMoves(board, List.of(event, full)));
        assertTrue(rules.isLegalMove(board, affected, result.source(), result.path()));
        assertEquals(before, board.occupancy());
    }

    @Test
    void adjacentOpponentBlockPreventsMovementEvenOnAProtectedStart() {
        Board board = Board.createMedium();
        var routes = PlayerRouteFactory.createMedium();
        var generator = new MoveGenerator(routes);
        var path = routes.get(PlayerColor.GREEN).positions();
        Position start = board.startFor(PlayerColor.YELLOW);
        int index = path.indexOf(start);
        Piece affected = new Piece(PlayerColor.GREEN, 1);
        board.placePiece(affected, path.get(index + 1));
        board.placePiece(new Piece(PlayerColor.RED, 1), start);
        board.placePiece(new Piece(PlayerColor.RED, 2), start);
        var before = board.occupancy();
        var candidate = generator.generateBackward(board, affected, 3).orElseThrow();
        assertTrue(rules.legalEventMove(board, candidate).isEmpty());
        assertEquals(before, board.occupancy());
    }

    @Test
    void ownBlockDoesNotPreventPassingOrLandingAndSourceBlocksDoNotTrapAnOccupant() {
        Board board = Board.createMedium();
        var routes = PlayerRouteFactory.createMedium();
        var generator = new MoveGenerator(routes);
        var path = routes.get(PlayerColor.GREEN).positions();
        Piece moving = new Piece(PlayerColor.GREEN, 1);
        board.placePiece(moving, path.get(10));
        board.placePiece(new Piece(PlayerColor.GREEN, 2), path.get(8));
        board.placePiece(new Piece(PlayerColor.GREEN, 3), path.get(8));
        for (int distance : new int[]{2, 3}) {
            var candidate = generator.generateBackward(board, moving, distance).orElseThrow();
            assertEquals(candidate, rules.legalEventMove(board, candidate).orElseThrow());
        }
        board.removePiece(moving, path.get(10));
        Position protectedSource = board.startFor(PlayerColor.YELLOW);
        board.placePiece(moving, protectedSource);
        board.placePiece(new Piece(PlayerColor.RED, 1), protectedSource);
        board.placePiece(new Piece(PlayerColor.RED, 2), protectedSource);
        var leaving = generator.generateBackward(board, moving, 1).orElseThrow();
        assertEquals(leaving, rules.legalEventMove(board, leaving).orElseThrow());
    }

    @Test
    void loneOpponentDoesNotPreventLandingAndEvaluationNeverCapturesOrMovesPieces() {
        Board board = Board.createMedium();
        var routes = PlayerRouteFactory.createMedium();
        var generator = new MoveGenerator(routes);
        var path = routes.get(PlayerColor.GREEN).positions();
        Piece moving = new Piece(PlayerColor.GREEN, 1);
        Piece opponent = new Piece(PlayerColor.RED, 1);
        for (Position target : List.of(path.get(3), board.startFor(PlayerColor.YELLOW))) {
            int index = path.indexOf(target);
            Position source = path.get(index + 1);
            board.placePiece(moving, source);
            board.placePiece(opponent, target);
            var candidate = generator.generateBackward(board, moving, 1).orElseThrow();
            var before = board.occupancy();
            assertEquals(candidate, rules.legalEventMove(board, candidate).orElseThrow());
            assertEquals(before, board.occupancy());
            board.removePiece(moving, source);
            board.removePiece(opponent, target);
        }
    }

    @Test
    void reusesTerrainColorAndCompletedPieceRestrictionsForEvents() {
        Board board = Board.createEasy(new Random(42));
        var routes = PlayerRouteFactory.createEasy();
        var path = routes.get(PlayerColor.GREEN).positions();
        Piece moving = new Piece(PlayerColor.GREEN, 1);
        Position source = path.get(3);
        var candidate = new MoveCandidate(moving, source, List.of(path.get(2)));
        assertTrue(rules.legalEventMove(board, candidate).isEmpty()); // Stale or unplaced source.
        board.placePiece(moving, source);
        for (Position illegal : List.of(new Position(0, 0), new Position(3, 3),
                new Position(3, 9), routes.get(PlayerColor.RED).finish())) {
            assertTrue(rules.legalEventMove(board, new MoveCandidate(moving, source, List.of(illegal))).isEmpty());
        }
        board.removePiece(moving, source);
        Position finish = routes.get(PlayerColor.GREEN).finish();
        board.placePiece(moving, finish);
        assertTrue(rules.legalEventMove(board,
                new MoveCandidate(moving, finish, List.of(path.get(path.size() - 2)))).isEmpty());
    }

    @Test
    void rejectsNullInputs() {
        Board board = Board.createMedium();
        var route = PlayerRouteFactory.createMedium().get(PlayerColor.GREEN);
        var candidate = new MoveCandidate(new Piece(PlayerColor.GREEN, 1),
                route.positions().get(1), List.of(route.start()));
        assertThrows(NullPointerException.class, () -> rules.legalEventMove(null, candidate));
        assertThrows(NullPointerException.class, () -> rules.legalEventMove(board, null));
    }
}
