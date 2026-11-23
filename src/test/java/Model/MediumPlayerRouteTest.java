package Model;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MediumPlayerRouteTest {
    private final Board board = Board.createMedium();
    private final Map<PlayerColor, PlayerRoute> routes = PlayerRouteFactory.createMedium();

    @Test
    void everyColorHasACompleteUniqueActiveRouteWithItsOwnHomeAndFinish() {
        assertEquals(4, routes.size());
        for (PlayerColor color : PlayerColor.values()) {
            PlayerRoute route = routes.get(color);
            assertEquals(color, route.color());
            assertEquals(board.startFor(color), route.start());
            assertEquals(116, route.size());
            assertEquals(route.size(), new HashSet<>(route.positions()).size());
            for (int i = 0; i < route.size(); i++) {
                Position position = route.positions().get(i);
                Square square = board.squareAt(position);
                assertTrue(board.isActive(position));
                assertNotEquals(SquareType.CASTLE, square.type());
                assertNotEquals(SquareType.EMPTY, square.type());
                if (i > 0) {
                    Position previous = route.positions().get(i - 1);
                    assertEquals(1, Math.max(Math.abs(position.row() - previous.row()),
                            Math.abs(position.column() - previous.column())), "Unexpected jump for " + color);
                }
                if (i >= route.size() - 6) {
                    assertEquals(SquareColor.fromPlayerColor(color), square.associatedColor());
                    assertEquals(i == route.size() - 1 ? SquareType.FINISHING : SquareType.HOME_PATH, square.type());
                } else {
                    assertNotEquals(SquareType.HOME_PATH, square.type());
                    assertNotEquals(SquareType.FINISHING, square.type());
                }
            }
        }
    }

    @Test
    void eachOwnOuterArrowEntersTheInnerRingAndEachInnerArrowEntersHome() {
        var entries = Map.of(PlayerColor.GREEN, new Position(8, 2),
                PlayerColor.YELLOW, new Position(2, 10), PlayerColor.BLUE, new Position(10, 16),
                PlayerColor.RED, new Position(16, 8));
        var firstHome = Map.of(PlayerColor.GREEN, new Position(9, 3),
                PlayerColor.YELLOW, new Position(3, 9), PlayerColor.BLUE, new Position(9, 15),
                PlayerColor.RED, new Position(15, 9));
        for (PlayerColor color : PlayerColor.values()) {
            List<Position> path = routes.get(color).positions();
            assertEquals(board.arrowsFor(color).getFirst(), path.get(57));
            assertEquals(entries.get(color), path.get(58));
            assertEquals(board.arrowFor(color), path.get(109));
            assertEquals(firstHome.get(color), path.get(110));
            long outerSquares = path.stream().filter(p -> p.row() == 1 || p.row() == 17
                    || p.column() == 1 || p.column() == 17).count();
            assertEquals(58, outerSquares);
            for (int i = 58; i < 110; i++) {
                Position position = path.get(i);
                assertTrue(position.row() >= 2 && position.row() <= 16
                        && position.column() >= 2 && position.column() <= 16);
            }
        }
    }

    @Test
    void opponentsArrowsArePassedClockwiseWithoutEnteringTheirInnerPath() {
        var green = routes.get(PlayerColor.GREEN).positions();
        assertSuccessor(green, new Position(1, 10), new Position(1, 11));
        assertSuccessor(green, new Position(10, 17), new Position(11, 17));
        assertSuccessor(green, new Position(17, 8), new Position(17, 7));
        assertSuccessor(green, new Position(2, 9), new Position(2, 10));
        assertSuccessor(green, new Position(9, 16), new Position(10, 16));
        assertSuccessor(green, new Position(16, 9), new Position(16, 8));
    }

    @Test
    void forwardCandidatesUseMediumRoutesAcrossBothArrowTransitions() {
        var generator = new MoveGenerator(routes);
        for (PlayerColor color : PlayerColor.values()) {
            Board current = Board.createMedium();
            var path = routes.get(color).positions();
            Piece piece = new Piece(color, 1);
            for (int index : new int[]{56, 108}) {
                current.placePiece(piece, path.get(index));
                var move = generator.generate(current, color, 3).getFirst();
                assertEquals(path.subList(index + 1, index + 4), move.path());
                assertTrue(new MoveRules().isLegalMove(current, piece, path.get(index), move.path()));
                current.removePiece(piece, path.get(index));
            }
        }
    }

    private void assertSuccessor(List<Position> path, Position source, Position expected) {
        int index = path.indexOf(source);
        assertTrue(index >= 0);
        assertEquals(expected, path.get(index + 1));
    }
}
