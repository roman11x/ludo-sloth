package Model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Builds fixed player routes, separately from the board's square definitions. */
public final class PlayerRouteFactory {
    private PlayerRouteFactory() {
    }

    /** Includes Start, the shared squares, the Home Path and Finish; excludes Castle. */
    public static Map<PlayerColor, PlayerRoute> createEasy() {
        return routesFromGreen(greenEasyCoordinates());
    }

    /** Outer Start to outer Arrow, one full inner circuit, then Home Path and Finish. */
    public static Map<PlayerColor, PlayerRoute> createMedium() {
        List<Position> green = new ArrayList<>();
        // Clockwise from the green corner (1,1), stopping at its outer arrow (8,1).
        for (int column = 1; column <= 17; column++) green.add(new Position(1, column));
        for (int row = 2; row <= 17; row++) green.add(new Position(row, 17));
        for (int column = 16; column >= 1; column--) green.add(new Position(17, column));
        for (int row = 16; row >= 8; row--) green.add(new Position(row, 1));
        // The arrow points right onto the inner ring, before the former Easy Start.
        green.add(new Position(8, 2));
        green.addAll(greenEasyCoordinates());
        return routesFromGreen(green);
    }

    private static Map<PlayerColor, PlayerRoute> routesFromGreen(List<Position> green) {
        List<Position> yellow = rotateClockwise(green);
        List<Position> blue = rotateClockwise(yellow);
        List<Position> red = rotateClockwise(blue);

        return Map.of(
                PlayerColor.GREEN, new PlayerRoute(PlayerColor.GREEN, green),
                PlayerColor.YELLOW, new PlayerRoute(PlayerColor.YELLOW, yellow),
                PlayerColor.BLUE, new PlayerRoute(PlayerColor.BLUE, blue),
                PlayerColor.RED, new PlayerRoute(PlayerColor.RED, red));
    }

    private static List<Position> rotateClockwise(List<Position> positions) {
        // Rotate coordinates 90 degrees around the center of the 19x19 matrix.
        // (row, column) becomes (column, 18 - row), preserving sequence order.
        return positions.stream()
                .map(position -> new Position(position.column(),
                        Board.STORAGE_SIZE - 1 - position.row()))
                .toList();
    }

    private static List<Position> greenEasyCoordinates() {
        // The 57-coordinate route: inner corners connect directly in one diagonal step.
        return List.of(
                new Position(8, 3),
                new Position(8, 4),
                new Position(8, 5),
                new Position(8, 6),
                new Position(8, 7),
                new Position(7, 8),
                new Position(6, 8),
                new Position(5, 8),
                new Position(4, 8),
                new Position(3, 8),
                new Position(2, 8),
                new Position(2, 9),
                new Position(2, 10),
                new Position(3, 10),
                new Position(4, 10),
                new Position(5, 10),
                new Position(6, 10),
                new Position(7, 10),
                new Position(8, 11),
                new Position(8, 12),
                new Position(8, 13),
                new Position(8, 14),
                new Position(8, 15),
                new Position(8, 16),
                new Position(9, 16),
                new Position(10, 16),
                new Position(10, 15),
                new Position(10, 14),
                new Position(10, 13),
                new Position(10, 12),
                new Position(10, 11),
                new Position(11, 10),
                new Position(12, 10),
                new Position(13, 10),
                new Position(14, 10),
                new Position(15, 10),
                new Position(16, 10),
                new Position(16, 9),
                new Position(16, 8),
                new Position(15, 8),
                new Position(14, 8),
                new Position(13, 8),
                new Position(12, 8),
                new Position(11, 8),
                new Position(10, 7),
                new Position(10, 6),
                new Position(10, 5),
                new Position(10, 4),
                new Position(10, 3),
                new Position(10, 2),
                new Position(9, 2),
                new Position(9, 3),
                new Position(9, 4),
                new Position(9, 5),
                new Position(9, 6),
                new Position(9, 7),
                new Position(9, 8));
    }
}
