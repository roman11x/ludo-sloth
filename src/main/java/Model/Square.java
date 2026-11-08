package Model;

import java.util.Objects;

/**
 * Fixed square properties, independent of changing piece occupancy.
 * An inactive square can still have a type and color, such as a Castle background.
 */
public record Square(
        Position position,
        SquareType type,
        SquareColor associatedColor,
        SquareStatus status
) {
    public Square {
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(associatedColor, "Use NEUTRAL for an unassociated square");
        Objects.requireNonNull(status, "status");
    }
}
