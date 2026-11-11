package Model;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class MoveGeneratorTest {
    private final Board board = Board.createEasy(new Random(42));
    private final Map<PlayerColor, PlayerRoute> routes = PlayerRouteFactory.createEasy();
    private final MoveGenerator generator = new MoveGenerator(routes);

    @Test
    void producesOrderedPathsForEveryColorAndEveryDieResultWithoutChangingBoard() {
        for (PlayerColor color : PlayerColor.values()) {
            Board layout = Board.createEasy(new Random(42));
            PlayerRoute route = routes.get(color);
            Piece piece = new Piece(color, 1);
            layout.placePiece(piece, route.start());
            var before = layout.occupancy();
            for (int roll = 1; roll <= 6; roll++) {
                MoveCandidate candidate = generator.generate(layout, color, roll).getFirst();
                assertEquals(piece, candidate.piece());
                assertEquals(route.start(), candidate.source());
                assertEquals(route.positions().subList(1, roll + 1), candidate.path());
                assertEquals(route.positions().get(roll), candidate.destination());
                assertFalse(candidate.path().contains(candidate.source()));
            }
            assertEquals(before, layout.occupancy());
        }
    }

    @Test
    void candidatesAreSortedByPieceNumberAndOnlyIncludeCurrentPlayer() {
        PlayerRoute route = routes.get(PlayerColor.GREEN);
        board.placePiece(new Piece(PlayerColor.GREEN, 4), route.positions().get(3));
        board.placePiece(new Piece(PlayerColor.GREEN, 1), route.start());
        board.placePiece(new Piece(PlayerColor.RED, 1), routes.get(PlayerColor.RED).start());
        List<MoveCandidate> candidates = generator.generate(board, PlayerColor.GREEN, 2);
        assertEquals(List.of(1, 4), candidates.stream().map(c -> c.piece().number()).toList());
        assertEquals(route.positions().subList(4, 6), candidates.getLast().path());
    }

    @Test
    void supportsTwoThreeAndFourPlayersAndCastleExitOnlyOnSix() {
        for (int count = 2; count <= 4; count++) {
            Board layout = Board.createEasy(new Random(42));
            for (int player = 0; player < count; player++) {
                PlayerColor color = PlayerColor.values()[player];
                List<Position> slots = new ArrayList<>();
                for (int row = 0; row < Board.STORAGE_SIZE; row++) {
                    for (int col = 0; col < Board.STORAGE_SIZE; col++) {
                        Square square = layout.squareAt(new Position(row, col));
                        if (square.type() == SquareType.CASTLE && square.status() == SquareStatus.ACTIVE
                                && square.associatedColor() == SquareColor.fromPlayerColor(color)) {
                            slots.add(square.position());
                        }
                    }
                }
                for (int i = 0; i < 4; i++) layout.placePiece(new Piece(color, i + 1), slots.get(i));
            }
            for (int player = 0; player < count; player++) {
                PlayerColor color = PlayerColor.values()[player];
                for (int roll = 1; roll < 6; roll++) assertTrue(generator.generate(layout, color, roll).isEmpty());
                List<MoveCandidate> candidates = generator.generate(layout, color, 6);
                assertEquals(4, candidates.size());
                for (MoveCandidate candidate : candidates) {
                    assertEquals(List.of(routes.get(color).start()), candidate.path());
                    assertEquals(SquareType.CASTLE, layout.squareAt(candidate.source()).type());
                }
            }
        }
    }

    @Test
    void exactFinishIsIncludedWhileOvershootsAndFinishedPiecesAreExcluded() {
        PlayerRoute route = routes.get(PlayerColor.GREEN);
        Position source = route.positions().get(route.size() - 3);
        board.placePiece(new Piece(PlayerColor.GREEN, 1), source);
        board.placePiece(new Piece(PlayerColor.GREEN, 2), route.finish());
        assertEquals(1, generator.generate(board, PlayerColor.GREEN, 1).size());
        MoveCandidate finish = generator.generate(board, PlayerColor.GREEN, 2).getFirst();
        assertEquals(route.finish(), finish.destination());
        assertEquals(2, finish.path().size());
        assertTrue(generator.generate(board, PlayerColor.GREEN, 3).isEmpty());
    }

    @Test
    void generationKeepsBlockedOptionsForTheSeparateRulesStage() {
        PlayerRoute route = routes.get(PlayerColor.GREEN);
        board.placePiece(new Piece(PlayerColor.GREEN, 1), route.start());
        Position blocked = route.positions().get(1);
        board.placePiece(new Piece(PlayerColor.RED, 1), blocked);
        board.placePiece(new Piece(PlayerColor.RED, 2), blocked);
        MoveCandidate candidate = generator.generate(board, PlayerColor.GREEN, 2).getFirst();
        assertEquals(route.positions().subList(1, 3), candidate.path());
        assertFalse(new MoveRules().isLegalMove(board, candidate.piece(),
                candidate.source(), candidate.path()));
    }

    @Test
    void candidateOwnsAnImmutablePathAndDerivesItsDestination() {
        List<Position> path = new ArrayList<>(List.of(new Position(8, 4), new Position(8, 5)));
        MoveCandidate candidate = new MoveCandidate(new Piece(PlayerColor.GREEN, 1), new Position(8, 3), path);
        path.clear();
        assertEquals(new Position(8, 5), candidate.destination());
        assertThrows(UnsupportedOperationException.class, () -> candidate.path().clear());
        assertThrows(IllegalArgumentException.class, () -> new MoveCandidate(candidate.piece(), candidate.source(), List.of()));
        assertThrows(IllegalArgumentException.class, () -> new MoveCandidate(candidate.piece(), candidate.source(), List.of(candidate.source())));
    }

    @Test
    void rejectsInvalidRollsMissingRoutesAndPositionsOutsideThePlayersRoute() {
        assertThrows(IllegalArgumentException.class, () -> generator.generate(board, PlayerColor.GREEN, 0));
        assertThrows(IllegalArgumentException.class, () -> generator.generate(board, PlayerColor.GREEN, 7));
        assertThrows(IllegalArgumentException.class, () -> new MoveGenerator(Map.of()).generate(board, PlayerColor.GREEN, 1));
        assertThrows(IllegalArgumentException.class, () -> new MoveGenerator(Map.of(PlayerColor.GREEN, routes.get(PlayerColor.RED))));
        board.placePiece(new Piece(PlayerColor.GREEN, 1), routes.get(PlayerColor.RED).finish());
        assertThrows(IllegalStateException.class, () -> generator.generate(board, PlayerColor.GREEN, 1));
    }
}
