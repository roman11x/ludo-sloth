package Model;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PlayerRouteTest {

    @Test
    void preservesTheSuppliedOrderAndColorWithoutSharingAMutableList() {
        var coordinates = new ArrayList<>(List.of(
                new Position(9, 6), new Position(9, 7), new Position(9, 8)));
        var expected = List.copyOf(coordinates);
        var route = new PlayerRoute(PlayerColor.GREEN, coordinates);

        coordinates.clear();

        assertEquals(PlayerColor.GREEN, route.color());
        assertEquals(expected, route.positions());
        assertEquals(new Position(9, 6), route.start());
        assertEquals(new Position(9, 8), route.finish());
        assertEquals(3, route.size());
        assertThrows(UnsupportedOperationException.class,
                () -> route.positions().set(0, new Position(0, 0)));
    }

    @Test
    void rejectsMissingColorMissingCoordinatesAndAnEmptyRoute() {
        assertThrows(NullPointerException.class,
                () -> new PlayerRoute(null, List.of(new Position(8, 3))));
        assertThrows(NullPointerException.class,
                () -> new PlayerRoute(PlayerColor.GREEN, null));
        assertThrows(IllegalArgumentException.class,
                () -> new PlayerRoute(PlayerColor.GREEN, List.of()));

        var coordinates = new ArrayList<Position>();
        coordinates.add(null);
        assertThrows(NullPointerException.class,
                () -> new PlayerRoute(PlayerColor.GREEN, coordinates));
    }
}
