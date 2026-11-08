package Model;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/** Prints each complete Easy route and checks its agreement with the board. */
class PlayerRoutePrintTest {

    @Test
    void printsAllEasyRoutesFromStartToFinish() {
        Map<PlayerColor, PlayerRoute> routes = PlayerRouteFactory.createEasy();
        Board board = Board.createEasy(new Random(42));
        var finishes = Map.of(
                PlayerColor.GREEN, new Position(9, 8),
                PlayerColor.YELLOW, new Position(8, 9),
                PlayerColor.BLUE, new Position(9, 10),
                PlayerColor.RED, new Position(10, 9));
        assertEquals(4, routes.size());

        for (PlayerColor color : List.of(PlayerColor.GREEN, PlayerColor.YELLOW,
                PlayerColor.BLUE, PlayerColor.RED)) {
            PlayerRoute route = routes.get(color);
            assertEquals(color, route.color());
            assertEquals(61, route.size());
            assertEquals(61, new HashSet<>(route.positions()).size());
            assertEquals(board.startFor(color), route.start());
            assertEquals(finishes.get(color), route.finish());
            assertEquals(board.arrowFor(color), route.positions().get(54));

            StringBuilder output = new StringBuilder();
            output.append("\n=== ").append(color).append(" ROUTE ===\n");
            output.append("Coordinates: (row, column); Start -> Finish; 61 positions\n");

            for (int i = 0; i < route.size(); i++) {
                Position position = route.positions().get(i);
                assertTrue(board.isActive(position), position.toString());
                Square square = board.squareAt(position);

                if (i < 55) {
                    assertTrue(switch (square.type()) {
                        case NORMAL, START, ARROW, PROTECTED -> true;
                        default -> false;
                    });
                } else {
                    assertEquals(SquareColor.fromPlayerColor(color), square.associatedColor());
                    assertEquals(i == 60 ? SquareType.FINISHING : SquareType.HOME_PATH,
                            square.type());
                }

                if (i > 0) {
                    Position previous = route.positions().get(i - 1);
                    assertEquals(1, Math.abs(position.row() - previous.row())
                            + Math.abs(position.column() - previous.column()),
                            "Non-adjacent step: " + previous + " -> " + position);
                    output.append(" -> ");
                    if (i % 8 == 0) output.append('\n');
                }
                output.append('(').append(position.row()).append(',')
                        .append(position.column()).append(')');
            }
            output.append('\n');
            System.out.print(output);
        }
        assertThrows(UnsupportedOperationException.class, () -> routes.clear());
    }
}
