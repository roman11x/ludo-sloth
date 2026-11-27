package Model;

import java.util.Objects;

/**
 * Represents the dice outcome of a turn and whether that outcome was cancelled.
 * The contained {@link DiceRoll} provides the standard-die value, any special-die
 * value, and the resulting movement distance. A cancelled outcome represents the
 * third consecutive Standard die roll of six; it ends the current player's turn
 * and must not be used to generate a move.
 * For compatibility with the iteration-one API, {@link #value()} returns the
 * Standard die's value rather than the movement distance.
 *
 * @param dice      the dice-roll outcome produced for the turn
 * @param cancelled {@code true} when the outcome is a cancelled third consecutive
 *                  Standard die roll of six; {@code false} otherwise
 */
public record TurnRoll(DiceRoll dice, boolean cancelled) {
    /**
     * Creates a turn-roll result and validates the cancellation invariant.
     *
     * @param dice      the dice-roll outcome produced for the turn
     * @param cancelled {@code true} when the outcome is a cancelled third
     *                  consecutive Standard die roll of six; {@code false} otherwise
     * @throws NullPointerException     if {@code dice} is {@code null}
     * @throws IllegalArgumentException if {@code cancelled} is {@code true} and
     *                                  the Standard die's value is not six
     */
    public TurnRoll {
        Objects.requireNonNull(dice, "dice");
        if (cancelled && dice.standardRoll() != 6) {
            throw new IllegalArgumentException("Invalid Standard-die turn result");
        }
    }

    /**
     * Creates an Easy-difficulty turn result for compatibility with the
     * iteration-one code, where a turn used only the standard die.
     * The supplied value is used as both the Standard die's value and the movement
     * distance, and the resulting {@link DiceRoll} has no special-die value.
     *
     * @param value     the Standard die's value and movement distance
     * @param cancelled whether the roll was cancelled because it was the third
     *                  consecutive six
     * @throws IllegalArgumentException if {@code value} is not a valid Standard
     *                                  die value, or if a cancelled roll is not six
     */
    public TurnRoll(int value, boolean cancelled) {
        this(new DiceRoll(value, DiceRoll.NO_SPECIAL_DIE, value), cancelled);
    }

    /**
     * Returns the Standard die's value for this turn.
     * This value is distinct from {@link DiceRoll#distance()} for difficulties
     * whose movement distance is affected by a special die.
     *
     * @return the Standard die's value, from one through six
     */
    public int value() {
        return dice.standardRoll();
    }
}
