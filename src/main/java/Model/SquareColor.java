package Model;

/**
 * A square's color association, independent of which pieces occupy it.
 * NEUTRAL means that the square is not associated with a player color.
 */
public enum SquareColor {
    RED,
    GREEN,
    YELLOW,
    BLUE,
    NEUTRAL;

    public static SquareColor fromPlayerColor(PlayerColor color) {
        return switch (color) {
            case RED -> RED;
            case GREEN -> GREEN;
            case YELLOW -> YELLOW;
            case BLUE -> BLUE;
        };
    }
}
