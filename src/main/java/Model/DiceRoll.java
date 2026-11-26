package Model;

/**
 * Represents the result of a dice roll, including a standard die value, an optional special die value, and an associated distance.
 * The standard die value must be between {@code 1} and {@code 6}. The special die value must be {@code 0} when no special die is present, or between {@code 1} and {@code 6} when
 *  present. The distance must not be negative.
 *
 * @param standardRoll the standard die roll value
 * @param specialRoll the special die roll value, or {@code 0} when no special die was rolled
 * @param distance the non-negative distance associated with the roll
 */
public record DiceRoll(int standardRoll, int specialRoll, int distance) {

    public static final int NO_SPECIAL_DIE = 0; // No special die means no bonus value
    private static final int MIN_ROLL = 1;
    private static final int MAX_ROLL = 6;

    /**
     * Creates a dice roll with a standard die result, an optional special die result, and a distance value.
     *
     * @param standardRoll the standard die roll value
     * @param specialRoll the special die roll value, or the no-special-die value
     * @param distance the distance associated with the roll
     * @throws IllegalArgumentException if {@code standardRoll} is outside the allowed roll range, {@code specialRoll} is outside the allowed special roll range, or {@code distance
     * } is negative
     */
    public DiceRoll {
        validateStandardRoll(standardRoll);
        validateSpecialRoll(specialRoll);
        validateDistance(distance);
    }

    /**
     * Indicates whether this dice roll includes a special die result.
     * So that calling code can easily see if a special die was rolled
     * @return {@code true} if a special die was rolled; {@code false} otherwise
     */
    public boolean hasSpecialDie() {
        return specialRoll != NO_SPECIAL_DIE;
    }

    /**
     * Validates that the standard die roll is within the allowed range.
     *
     * @param standardRoll the standard die roll value to validate
     * @throws IllegalArgumentException if {@code standardRoll} is less than the minimum roll value or greater than the maximum roll value
     */
    private static void validateStandardRoll(int standardRoll) {
        if (standardRoll < MIN_ROLL || standardRoll > MAX_ROLL)
            throw new IllegalArgumentException("Got: " + standardRoll + ". Standard roll must be between " + MIN_ROLL + " and " + MAX_ROLL);
    }

    /**
     * Validates that the special die roll is within the allowed range.
     *
     * @param specialRoll the special die roll value to validate, or the no-special-die value
     * @throws IllegalArgumentException if {@code specialRoll} is less than the no-special-die value or greater than the maximum roll value
     */
    private static void validateSpecialRoll(int specialRoll) {
        if (specialRoll < NO_SPECIAL_DIE || specialRoll > MAX_ROLL)
            throw new IllegalArgumentException("Got: " + specialRoll + ". Special roll value must be between "
                    + NO_SPECIAL_DIE + " and " + MAX_ROLL);
    }

    /**
     * Validates that the distance is not negative.
     *
     * @param distance the distance value to validate
     * @throws IllegalArgumentException if {@code distance} is negative
     */
    private static void validateDistance(int distance) {
        if (distance < 0) {
            throw new IllegalArgumentException("Got " + distance + ". Distance must be non-negative");
        }
    }
}
