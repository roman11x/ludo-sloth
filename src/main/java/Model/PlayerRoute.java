package Model;

import java.util.List;
import java.util.Objects;

/**
 * One color's fixed coordinate sequence from Start through Home Path to Finish.
 * Castle cells, piece locations and movement rules are not part of the route.
 */
public final class PlayerRoute {
    private final PlayerColor color;
    private final List<Position> positions;

    public PlayerRoute(PlayerColor color, List<Position> positions) {
        this.color = Objects.requireNonNull(color, "color");

        Objects.requireNonNull(positions, "positions");
        if (positions.isEmpty()) {
            throw new IllegalArgumentException("Route cannot be empty");
        }

        this.positions = List.copyOf(positions);
    }

    public PlayerColor color() {
        return color;
    }

    public List<Position> positions() {
        return positions;
    }

    public Position start() {
        return positions.getFirst();
    }

    public Position finish() {
        return positions.getLast();
    }

    public int size() {
        return positions.size();
    }
}
