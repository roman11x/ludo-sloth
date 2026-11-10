package Model;

import java.util.List;
import java.util.Objects;

/**
 * A game participant with a fixed name, color and four pieces.
 * Piece locations belong to Board; active-player selection belongs to TurnManager.
 */
public final class Player {
    private final String name;
    private final PlayerColor color;
    private final List<Piece> pieces;

    /** Reuses the setup validation rather than maintaining a second set of rules. */
    public Player(String name, PlayerColor color) {
        this(new PlayerSetup(name, color));
    }

    /** Creates the four pieces; placing them in Castle is a separate setup step. */
    public Player(PlayerSetup setup) {
        Objects.requireNonNull(setup, "setup");
        this.name = setup.name();
        this.color = setup.color();
        this.pieces = List.of(new Piece(color, 1), new Piece(color, 2),
                new Piece(color, 3), new Piece(color, 4));
    }

    public String name() {
        return name;
    }

    public PlayerColor color() {
        return color;
    }

    /** Immutable list of immutable pieces, in number order. */
    public List<Piece> pieces() {
        return pieces;
    }
}
