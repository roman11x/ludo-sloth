package Model;

import java.util.Objects;

/**
 * Dice rolling strategy for easy mode.
 *
 * Produces roll results using only a standard six-sided die. The returned result
 * contains a standard die value, no special die value, and a distance determined
 * by a separate standard die roll.
 */
public class EasyStrategy implements DiceStrategy {
    private final Die die;

    /**
     * Creates an easy-mode dice strategy using a new standard six-sided die.
     */
    public EasyStrategy() {
        this(new StandardDie());
    }

    /**
     * Creates an easy-mode dice strategy using the specified standard die for testing.
     *
     * @param die the standard die used to produce roll values for this strategy
     */
    public EasyStrategy(Die die) {
        Objects.requireNonNull(die, "die");
        this.die = die;
    }

    /**
     * Rolls the easy-mode dice result using the standard die for both the standard roll
     * and the distance, with no special die value.
     *
     * @return a dice roll containing a standard die value, no special die value, and a distance from a separate standard die roll
     */
    @Override
    public DiceRoll rollDice() {
        var roll = die.roll();
        return new DiceRoll(roll, DiceRoll.NO_SPECIAL_DIE, roll);
    }
}
