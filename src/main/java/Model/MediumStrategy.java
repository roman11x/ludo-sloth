package Model;

import java.util.Objects;

/**
 * Medium difficulty dice strategy that combines a standard die with a boost die.
 *
 * The strategy rolls both dice and calculates distance as the standard roll
 * multiplied by one less than the boost roll. With the default dice, the standard
 * roll is expected to be in the range {@code 1} to {@code 6}, the boost roll is
 * expected to be in the range {@code 1} to {@code 3}, and the resulting distance
 * is non-negative.
 *
 * @see DiceStrategy
 * @see DiceRoll
 * @see StandardDie
 * @see BoostDie
 */
public class MediumStrategy implements DiceStrategy {
    Die standartDie;
    Die boostDie;

    /**
     * Creates a medium difficulty dice strategy using the default dice.
     *
     * The strategy is initialized with a {@link StandardDie} for the standard roll
     * and a {@link BoostDie} for the boost roll.
     *
     * @see StandardDie
     * @see BoostDie
     */
    public MediumStrategy() {
        this(new StandardDie(), new BoostDie());
    }

    /**
     * Creates a medium difficulty dice strategy using the specified dice for testing.
     *
     * @param standartDie the die used for the standard roll
     * @param boostDie the die used for the boost roll
     * @throws NullPointerException if {@code standartDie} or {@code boostDie} is {@code null}
     */
    public MediumStrategy(Die standartDie, Die boostDie) {
        Objects.requireNonNull(standartDie, "standard");
        Objects.requireNonNull(boostDie, "boost");

        this.standartDie = standartDie;
        this.boostDie = boostDie;
    }

    /**
     * Rolls the standard die and boost die, then calculates the distance as the
     * standard roll multiplied by one less than the boost roll.
     *
     * @return a dice roll containing the standard roll, boost roll, and calculated distance
     */
    @Override
    public DiceRoll rollDice() {
        var standardRoll = standartDie.roll();
        var boostRoll = boostDie.roll();
        var distance = standardRoll * (boostRoll - 1);
        return new DiceRoll(standardRoll, boostRoll, distance);
    }
}
