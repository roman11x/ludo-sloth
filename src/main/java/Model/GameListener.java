package Model;

import java.util.List;

/**
 * Observer of a {@link Game}. Register with {@link Game#addListener(GameListener)}.
 * <p>
 * The game calls these methods only after an accepted action has fully updated its state, so a
 * listener may read the game from inside any of them. A rejected action notifies nothing.
 * Every method does nothing by default; a listener overrides only what it needs.
 */
public interface GameListener {

    /** {@code player} rolled; a cancelled roll is a third 6 in a row and ends the turn. */
    default void rolled(Player player, TurnRoll roll) {
    }

    /** {@code player} moved {@code move.piece()} along {@code move.path()} to {@code move.destination()}. */
    default void pieceMoved(Player player, MoveCandidate move) {
    }

    /** {@code owner}'s {@code piece} was captured and now stands on {@code castleSlot}. */
    default void pieceCaptured(Player owner, Piece piece, Position castleSlot) {
    }

    /** All four of {@code player}'s pieces reached Finish; {@code place} is 1 for first place. */
    default void playerFinished(Player player, int place) {
    }

    /**
     * The turn is over and the game waits for {@code next} to roll. {@code next} is the same player
     * when a 6 earned another roll. Not called when the turn ends the game.
     */
    default void turnPassed(Player next) {
    }

    /** The game is over; {@code ranking} is the final order, winner first. */
    default void gameOver(List<Player> ranking) {
    }

    /** {@code player} must answer {@code question} before {@code piece}'s move is resolved. */
    default void questionRequired(Player player, Piece piece, Question question) {
    }

    /** Resolve this request through Game.resolveSquareEffect after any required player input. */
    default void squareEffectRequired(PendingSquareEffect effect) {
    }

}
