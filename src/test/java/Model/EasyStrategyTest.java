package Model;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class EasyStrategyTest {

    @Test
    void rollDiceUsesDieRollAsStandardRollAndDistance() {
        Die die = () -> 4; // a Die whose roll() takes nothing and returns 4
        var strategy = new EasyStrategy(die);
        var expectedRoll = new DiceRoll(4, DiceRoll.NO_SPECIAL_DIE, 4);

        assertEquals(expectedRoll, strategy.rollDice());
    }

    @Test
    void rollDiceUsesRandomDieRollAsStandardRollAndDistance() {
        Random random = new Random(42);
        Die die = () -> random.nextInt(6) + 1;
        var strategy = new EasyStrategy(die);

        DiceRoll roll = strategy.rollDice();

        assertTrue(roll.standardRoll() >= 1 && roll.standardRoll() <= 6,
                "Standard roll out of range: " + roll.standardRoll());
        assertEquals(DiceRoll.NO_SPECIAL_DIE, roll.specialRoll());
        assertEquals(roll.standardRoll(), roll.distance());
    }

    @Test
    void defaultStrategyProducesValidEasyRoll() {
        var strategy = new EasyStrategy();

        DiceRoll roll = strategy.rollDice();

        assertTrue(roll.standardRoll() >= 1 && roll.standardRoll() <= 6,
                "Standard roll out of range: " + roll.standardRoll());
        assertEquals(DiceRoll.NO_SPECIAL_DIE, roll.specialRoll());
        assertEquals(roll.standardRoll(), roll.distance());
    }

    @Test
    void rejectsMissingDie() {
        assertThrows(NullPointerException.class, () -> new EasyStrategy(null));
    }
}
