package Model;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class LegalMovesTest {
    private final Board board = Board.createEasy(new Random(42));
    private final Map<PlayerColor, PlayerRoute> routes = PlayerRouteFactory.createEasy();
    private final PlayerRoute route = routes.get(PlayerColor.GREEN);
    private final MoveGenerator generator = new MoveGenerator(routes);
    private final MoveRules rules = new MoveRules();

    private Position at(int index) { return route.positions().get(index); }

    private Piece place(PlayerColor color, int number, Position position) {
        Piece piece = new Piece(color, number);
        board.placePiece(piece, position);
        return piece;
    }

    private void block(int index) {
        place(PlayerColor.RED, 1, at(index));
        place(PlayerColor.RED, 2, at(index));
    }

    private List<MoveCandidate> legal(int roll) {
        return rules.legalMoves(board, generator.generate(board, PlayerColor.GREEN, roll));
    }

    @Test
    void connectsGeneratorToRulesWithoutMutatingOccupancy() {
        place(PlayerColor.GREEN, 1, at(0));
        var before = board.occupancy();
        List<MoveCandidate> candidates = generator.generate(board, PlayerColor.GREEN, 3);
        List<MoveCandidate> result = rules.legalMoves(board, candidates);
        assertEquals(candidates, result);
        assertEquals(List.of(at(1), at(2), at(3)), result.getFirst().path());
        assertEquals(before, board.occupancy());
        assertThrows(UnsupportedOperationException.class, result::clear);
    }

    @Test
    void fullMoveOfAnyPieceSuppressesAllShortenedMovesRegardlessOfOrder() {
        place(PlayerColor.GREEN, 1, at(0));
        Piece full = place(PlayerColor.GREEN, 2, at(10));
        block(2);
        List<MoveCandidate> candidates = generator.generate(board, PlayerColor.GREEN, 3);
        for (List<MoveCandidate> ordered : List.of(candidates, candidates.reversed())) {
            List<MoveCandidate> result = rules.legalMoves(board, ordered);
            assertEquals(1, result.size());
            assertEquals(full, result.getFirst().piece());
            assertEquals(3, result.getFirst().path().size());
        }
    }

    @Test
    void returnsEachPrefixBeforeBlockOnlyWhenNoFullMoveExists() {
        place(PlayerColor.GREEN, 1, at(0));
        place(PlayerColor.GREEN, 2, at(1));
        block(3);
        List<MoveCandidate> result = legal(3);
        assertEquals(2, result.size());
        assertEquals(List.of(at(1), at(2)), result.get(0).path());
        assertEquals(List.of(at(2)), result.get(1).path());
        assertEquals(at(2), result.get(0).destination());
        assertEquals(at(2), result.get(1).destination());
    }

    @Test
    void adjacentBlockProducesNoZeroStepMoveAndEmptyInputProducesNoMoves() {
        assertTrue(legal(1).isEmpty());
        place(PlayerColor.GREEN, 1, at(0));
        block(1);
        assertTrue(legal(4).isEmpty());
    }

    @Test
    void blockOnTargetShortensMoveAndOwnerCanPassItsOwnBlock() {
        place(PlayerColor.GREEN, 1, at(0));
        block(3);
        assertEquals(List.of(at(1), at(2)), legal(3).getFirst().path());
        board.removePiece(new Piece(PlayerColor.RED, 1), at(3));
        board.removePiece(new Piece(PlayerColor.RED, 2), at(3));
        place(PlayerColor.GREEN, 2, at(3));
        place(PlayerColor.GREEN, 3, at(3));
        MoveCandidate move = legal(4).stream().filter(c -> c.piece().number() == 1).findFirst().orElseThrow();
        assertEquals(at(4), move.destination());
        assertEquals(4, move.path().size());
    }

    @Test
    void blockedStartPreventsCastleExitButSingleOpponentDoesNot() {
        Piece green = place(PlayerColor.GREEN, 1, new Position(3, 3));
        Piece red = place(PlayerColor.RED, 1, route.start());
        assertEquals(List.of(route.start()), legal(6).getFirst().path());
        place(PlayerColor.RED, 2, route.start());
        assertTrue(legal(6).isEmpty());
        assertEquals(List.of(green), board.piecesAt(new Position(3, 3)));
        assertTrue(board.piecesAt(route.start()).contains(red));
    }

    @Test
    void existingOpponentCanLeaveProtectedSquareAfterBlockFormsAroundIt() {
        Piece green = place(PlayerColor.GREEN, 1, at(0));
        block(0);
        List<MoveCandidate> result = legal(2);
        assertEquals(1, result.size());
        assertEquals(green, result.getFirst().piece());
        assertEquals(at(2), result.getFirst().destination());
    }

    @Test
    void allowsLandingOnSingleOpponentWithoutPerformingCapture() {
        int normalIndex = 1;
        while (board.squareAt(at(normalIndex)).type() != SquareType.NORMAL) normalIndex++;
        place(PlayerColor.GREEN, 1, at(normalIndex - 1));
        Piece red = place(PlayerColor.RED, 1, at(normalIndex));
        assertEquals(at(normalIndex), legal(1).getFirst().destination());
        assertEquals(List.of(red), board.piecesAt(at(normalIndex)));
    }

    @Test
    void exactFinishRemainsLegalAndFinishedPiecesHaveNoMoves() {
        place(PlayerColor.GREEN, 1, at(route.size() - 3));
        assertEquals(route.finish(), legal(2).getFirst().destination());
        assertTrue(legal(3).isEmpty());
        Piece finished = place(PlayerColor.GREEN, 2, route.finish());
        MoveCandidate invalid = new MoveCandidate(finished, route.finish(), List.of(at(55)));
        assertTrue(rules.legalMoves(board, List.of(invalid)).isEmpty());
    }

    @Test
    void rejectsInactiveForeignHomeAndWrongSourceCandidates() {
        Piece green = place(PlayerColor.GREEN, 1, at(0));
        for (Position destination : List.of(new Position(0, 0), new Position(-1, 0),
                new Position(3, 9), routes.get(PlayerColor.RED).finish(), new Position(3, 3))) {
            MoveCandidate candidate = new MoveCandidate(green, at(0), List.of(destination));
            assertTrue(rules.legalMoves(board, List.of(candidate)).isEmpty());
        }
        assertTrue(rules.legalMoves(board, List.of(new MoveCandidate(green, at(1), List.of(at(2))))).isEmpty());
        assertTrue(rules.legalMoves(board, List.of(new MoveCandidate(green, new Position(-1, 0), List.of(at(2))))).isEmpty());
    }

    @Test
    void castleExitOnSixIsPreferredOverAnotherPiecesShortenedMove() {
        Piece entering = place(PlayerColor.GREEN, 1, new Position(3, 3));
        place(PlayerColor.GREEN, 2, route.start());
        block(2);
        List<MoveCandidate> candidates = generator.generate(board, PlayerColor.GREEN, 6);
        for (List<MoveCandidate> ordered : List.of(candidates, candidates.reversed())) {
            List<MoveCandidate> moves = rules.legalMoves(board, ordered);
            assertEquals(1, moves.size());
            assertEquals(entering, moves.getFirst().piece());
            assertEquals(List.of(route.start()), moves.getFirst().path());
        }
    }

    @Test
    void opposingBlockOnOwnArrowPreventsEntryToHomeAndStopsImmediatelyBeforeArrow() {
        int arrow = route.positions().indexOf(board.arrowFor(PlayerColor.GREEN));
        Piece green = place(PlayerColor.GREEN, 1, at(arrow - 2));
        block(arrow);
        MoveCandidate full = generator.generate(board, PlayerColor.GREEN, 3).getFirst();
        assertFalse(rules.isLegalMove(board, green, full.source(), full.path()));
        MoveCandidate shortened = legal(3).getFirst();
        assertEquals(List.of(at(arrow - 1)), shortened.path());
        assertTrue(rules.isLegalMove(board, green, shortened.source(), shortened.path()));
    }

    @Test
    void opponentPassesAnotherColorsArrowAsAnOrdinarySharedSquare() {
        PlayerRoute red = routes.get(PlayerColor.RED);
        Position greenArrow = board.arrowFor(PlayerColor.GREEN);
        int index = red.positions().indexOf(greenArrow);
        Piece piece = place(PlayerColor.RED, 1, red.positions().get(index - 1));
        List<MoveCandidate> moves = rules.legalMoves(board, generator.generate(board, PlayerColor.RED, 2));
        assertEquals(1, moves.size());
        assertEquals(List.of(greenArrow, red.positions().get(index + 1)), moves.getFirst().path());
        assertTrue(rules.isLegalMove(board, piece, moves.getFirst().source(), moves.getFirst().path()));
        assertNotEquals(SquareType.HOME_PATH, board.squareAt(moves.getFirst().destination()).type());
    }

    @Test
    void filteringAndExecutionRevalidationBothRejectForeignHomeEntry() {
        Position source = new Position(10, 2);
        Piece red = place(PlayerColor.RED, 1, source);
        List<Position> path = List.of(new Position(9, 2), new Position(9, 3));
        MoveCandidate candidate = new MoveCandidate(red, source, path);
        assertTrue(rules.legalMoves(board, List.of(candidate)).isEmpty());
        assertFalse(rules.isLegalMove(board, red, source, path));
    }

    @Test
    void filteringAndRevalidationRejectInvalidCastleExitAndPassingThroughFinish() {
        Piece green = place(PlayerColor.GREEN, 1, new Position(3, 3));
        List<Position> wrongExit = List.of(at(1));
        assertTrue(rules.legalMoves(board, List.of(new MoveCandidate(green, new Position(3, 3), wrongExit))).isEmpty());
        assertFalse(rules.isLegalMove(board, green, new Position(3, 3), wrongExit));
        Position beforeFinish = at(route.size() - 2);
        board.movePiece(green, new Position(3, 3), beforeFinish);
        List<Position> pastFinish = List.of(route.finish(), at(0));
        assertTrue(rules.legalMoves(board, List.of(new MoveCandidate(green, beforeFinish, pastFinish))).isEmpty());
        assertFalse(rules.isLegalMove(board, green, beforeFinish, pastFinish));
    }

    @Test
    void rejectsMixedPlayerListsBecauseFallbackIsDecidedForOnePlayer() {
        Piece green = place(PlayerColor.GREEN, 1, at(0));
        PlayerRoute redRoute = routes.get(PlayerColor.RED);
        Piece red = place(PlayerColor.RED, 1, redRoute.start());
        List<MoveCandidate> candidates = List.of(
                new MoveCandidate(green, at(0), List.of(at(1))),
                new MoveCandidate(red, redRoute.start(), List.of(redRoute.positions().get(1))));
        assertThrows(IllegalArgumentException.class, () -> rules.legalMoves(board, candidates));
    }
}
