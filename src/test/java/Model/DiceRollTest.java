package Model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DiceRollTest {
    private static final int MIN_STANDARD_ROLL = 1;
    private static final int MAX_STANDARD_ROLL = 6;
    private static final int NO_SPECIAL_DIE = DiceRoll.NO_SPECIAL_DIE;
    private static final int MIN_SPECIAL_ROLL = 1;
    private static final int MAX_SPECIAL_ROLL = 6;
    private static final int VALID_DISTANCE = 4;

    @Test
    void createsDiceRollWithStandardRollSpecialRollAndDistance() {
        var diceRoll = new DiceRoll(3, 2, VALID_DISTANCE);

        assertEquals(3, diceRoll.standardRoll());
        assertEquals(2, diceRoll.specialRoll());
        assertEquals(VALID_DISTANCE, diceRoll.distance());
    }

    @Test
    void createsDiceRollWithoutSpecialDie() {
        var diceRoll = new DiceRoll(3, NO_SPECIAL_DIE, VALID_DISTANCE);

        assertEquals(3, diceRoll.standardRoll());
        assertEquals(NO_SPECIAL_DIE, diceRoll.specialRoll());
        assertEquals(VALID_DISTANCE, diceRoll.distance());
    }

    @Test
    void hasSpecialDieReturnsTrueWhenSpecialRollIsPresent() {
        var diceRoll = new DiceRoll(3, 2, VALID_DISTANCE);

        assertTrue(diceRoll.hasSpecialDie());
    }

    @Test
    void hasSpecialDieReturnsFalseWhenSpecialRollIsMissing() {
        var diceRoll = new DiceRoll(3, NO_SPECIAL_DIE, VALID_DISTANCE);

        assertFalse(diceRoll.hasSpecialDie());
    }

    @Test
    void acceptsMinimumAndMaximumStandardRolls() {
        assertDoesNotThrow(() -> new DiceRoll(MIN_STANDARD_ROLL, NO_SPECIAL_DIE, VALID_DISTANCE));
        assertDoesNotThrow(() -> new DiceRoll(MAX_STANDARD_ROLL, NO_SPECIAL_DIE, VALID_DISTANCE));
    }

    @Test
    void acceptsNoSpecialDieAndMinimumAndMaximumSpecialRolls() {
        assertDoesNotThrow(() -> new DiceRoll(3, NO_SPECIAL_DIE, VALID_DISTANCE));
        assertDoesNotThrow(() -> new DiceRoll(3, MIN_SPECIAL_ROLL, VALID_DISTANCE));
        assertDoesNotThrow(() -> new DiceRoll(3, MAX_SPECIAL_ROLL, VALID_DISTANCE));
    }

    @Test
    void acceptsZeroDistance() {
        var diceRoll = new DiceRoll(3, NO_SPECIAL_DIE, 0);

        assertEquals(0, diceRoll.distance());
    }

    @Test
    void rejectsStandardRollBelowMinimum() {
        assertThrows(IllegalArgumentException.class,
                () -> new DiceRoll(MIN_STANDARD_ROLL - 1, NO_SPECIAL_DIE, VALID_DISTANCE));
    }

    @Test
    void rejectsStandardRollAboveMaximum() {
        assertThrows(IllegalArgumentException.class,
                () -> new DiceRoll(MAX_STANDARD_ROLL + 1, NO_SPECIAL_DIE, VALID_DISTANCE));
    }

    @Test
    void rejectsSpecialRollBelowNoSpecialDieValue() {
        assertThrows(IllegalArgumentException.class,
                () -> new DiceRoll(3, NO_SPECIAL_DIE - 1, VALID_DISTANCE));
    }

    @Test
    void rejectsSpecialRollAboveMaximum() {
        assertThrows(IllegalArgumentException.class,
                () -> new DiceRoll(3, MAX_SPECIAL_ROLL + 1, VALID_DISTANCE));
    }

    @Test
    void rejectsNegativeDistance() {
        assertThrows(IllegalArgumentException.class,
                () -> new DiceRoll(3, NO_SPECIAL_DIE, -1));
    }
}
