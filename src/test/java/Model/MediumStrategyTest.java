package Model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MediumStrategyTest {

    @Test
    void rollDiceUsesBothDieRollsAsStandardRollAndDistance() {
        Die standard = () -> 4; // a Die whose roll() takes nothing and returns 4
        Die boost = () -> 3;
        var strategy = new MediumStrategy(standard, boost);
        var expectedRoll = new DiceRoll(4, 3, 8);

        assertEquals(expectedRoll, strategy.rollDice());
    }

    @Test
    void distanceIsZeroForBoostOfOne() {
        Die standard = () -> 5;
        Die boost = () -> 1;
        var strategy = new MediumStrategy(standard, boost);
        var expectedRoll = new DiceRoll(5, 1, 0);

        assertEquals(expectedRoll, strategy.rollDice());
    }

    @Test
    void defaultStrategyProducesValidMediumRoll() {
        var strategy = new MediumStrategy();

        DiceRoll roll = strategy.rollDice();

        assertTrue(roll.standardRoll() >= 1 && roll.standardRoll() <= 6,
                "Standard roll out of range: " + roll.standardRoll());
        assertTrue(roll.specialRoll() >= 1 && roll.specialRoll() <= 3,
                "Boost roll out of range: " + roll.specialRoll());
        assertEquals(roll.standardRoll() * (roll.specialRoll() - 1), roll.distance());
    }

    @Test
    void rejectsMissingDice() {
        Die standard = () -> 4;
        Die boost = () -> 3;
        assertThrows(NullPointerException.class, () -> new MediumStrategy(standard, null));
        assertThrows(NullPointerException.class, () -> new MediumStrategy(null, boost));
        assertThrows(NullPointerException.class, () -> new MediumStrategy(null, null));
    }
}
