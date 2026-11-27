package Model;

/**
 * Defines a strategy for producing dice roll outcomes.
 *
 * Implementations encapsulate the rules used to roll dice and convert those rolls
 * into a {@link DiceRoll}. Different implementations represent different game
 * difficulty levels
 *
 * Returned roll results are expected to satisfy the validation rules of
 * {@link DiceRoll}: a valid standard roll, an optional valid special roll, and a
 * non-negative distance.
 */
public interface DiceStrategy {
    /**
     * Rolls the dice according to this strategy and produces the resulting roll outcome.
     *
     * @return the dice roll result, including the standard roll, optional special roll, and distance
     */
    DiceRoll rollDice();
}
