package Model;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class BackwardMoveGeneratorTest {
    @Test
    void reversesEveryMovablePositionForEveryColorOnEasyAndMediumWithoutChangingOccupancy() {
        for (boolean medium : List.of(false, true)) {
            var routes = medium ? PlayerRouteFactory.createMedium() : PlayerRouteFactory.createEasy();
            var generator = new MoveGenerator(routes);
            Board board = medium ? Board.createMedium() : Board.createEasy(new Random(42));
            for (PlayerColor color : PlayerColor.values()) {
                var route = routes.get(color);
                Piece piece = new Piece(color, 1);
                for (int sourceIndex = 1; sourceIndex < route.size() - 1; sourceIndex++) {
                    Position source = route.positions().get(sourceIndex);
                    board.placePiece(piece, source);
                    var before = board.occupancy();
                    for (int distance : new int[]{1, 2, 3, 7, Integer.MAX_VALUE}) {
                        MoveCandidate move = generator.generateBackward(board, piece, distance).orElseThrow();
                        int expectedLength = Math.min(distance, sourceIndex);
                        assertEquals(piece, move.piece());
                        assertEquals(source, move.source());
                        assertEquals(expectedLength, move.path().size());
                        for (int step = 1; step <= expectedLength; step++) {
                            assertEquals(route.positions().get(sourceIndex - step), move.path().get(step - 1));
                        }
                        assertEquals(route.positions().get(sourceIndex - expectedLength), move.destination());
                        assertFalse(move.path().contains(source));
                        assertThrows(UnsupportedOperationException.class, () -> move.path().clear());
                        assertEquals(before, board.occupancy());
                    }
                    board.removePiece(piece, source);
                }
            }
        }
    }

    @Test
    void crossesBothMediumArrowsInReverseAndCanLeaveTheHomePath() {
        Board board = Board.createMedium();
        var routes = PlayerRouteFactory.createMedium();
        var generator = new MoveGenerator(routes);
        for (PlayerColor color : PlayerColor.values()) {
            var route = routes.get(color);
            Piece piece = new Piece(color, 1);
            for (int index : new int[]{58, 110}) {
                board.placePiece(piece, route.positions().get(index));
                var move = generator.generateBackward(board, piece, 1).orElseThrow();
                assertEquals(List.of(route.positions().get(index - 1)), move.path());
                Square arrow = board.squareAt(move.destination());
                assertEquals(SquareType.ARROW, arrow.type());
                assertEquals(SquareColor.fromPlayerColor(color), arrow.associatedColor());
                board.removePiece(piece, route.positions().get(index));
            }
        }
    }

    @Test
    void startCastleFinishedAndZeroDistanceProduceNoMove() {
        Board board = Board.createMedium();
        var routes = PlayerRouteFactory.createMedium();
        var generator = new MoveGenerator(routes);
        Piece piece = new Piece(PlayerColor.GREEN, 1);
        var route = routes.get(piece.color());
        for (Position source : List.of(route.start(), new Position(3, 3), route.finish())) {
            board.placePiece(piece, source);
            assertTrue(generator.generateBackward(board, piece, 3).isEmpty());
            board.removePiece(piece, source);
        }
        board.placePiece(piece, route.positions().get(10));
        assertTrue(generator.generateBackward(board, piece, 0).isEmpty());
    }

    @Test
    void generatesOnlyTheRequestedPieceAndLeavesBlockedPathsForTheRulesStage() {
        Board board = Board.createMedium();
        var routes = PlayerRouteFactory.createMedium();
        var generator = new MoveGenerator(routes);
        var path = routes.get(PlayerColor.GREEN).positions();
        Piece moving = new Piece(PlayerColor.GREEN, 1);
        board.placePiece(moving, path.get(5));
        board.placePiece(new Piece(PlayerColor.GREEN, 2), path.get(8));
        board.placePiece(new Piece(PlayerColor.RED, 1), path.get(4));
        board.placePiece(new Piece(PlayerColor.RED, 2), path.get(4));
        var before = board.occupancy();
        var move = generator.generateBackward(board, moving, 2).orElseThrow();
        assertEquals(moving, move.piece());
        assertEquals(List.of(path.get(4), path.get(3)), move.path());
        assertEquals(before, board.occupancy());
    }

    @Test
    void rejectsInvalidInputAndMisplacedPieces() {
        Board board = Board.createMedium();
        var routes = PlayerRouteFactory.createMedium();
        var generator = new MoveGenerator(routes);
        Piece piece = new Piece(PlayerColor.GREEN, 1);
        assertThrows(NullPointerException.class, () -> generator.generateBackward(null, piece, 1));
        assertThrows(NullPointerException.class, () -> generator.generateBackward(board, null, 1));
        assertThrows(IllegalArgumentException.class, () -> generator.generateBackward(board, piece, -1));
        assertThrows(IllegalArgumentException.class,
                () -> new MoveGenerator(Map.of()).generateBackward(board, piece, 1));
        assertThrows(IllegalStateException.class, () -> generator.generateBackward(board, piece, 1));
        Position wrongFinish = routes.get(PlayerColor.RED).finish();
        board.placePiece(piece, wrongFinish);
        assertThrows(IllegalStateException.class, () -> generator.generateBackward(board, piece, 1));
        board.removePiece(piece, wrongFinish);
        board.placePiece(piece, new Position(12, 3));
        assertThrows(IllegalStateException.class, () -> generator.generateBackward(board, piece, 1));
    }
}
