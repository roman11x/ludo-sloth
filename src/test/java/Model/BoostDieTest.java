package Model;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class BoostDieTest {
    private static final int MAX_VALUE = 3;
    private static final int TEST_OUTCOME_ROLLS = 6;
    private static final int SEE_ALL_FACES_ROLLS = 100;
    private static final int RANGE_CHECK_ROLLS = 10_000;
    private static final long SEE_ALL_FACES_SEED = 42L;

    @Test
    void mapsAllThreeUniformRandomOutcomesOneToOneAndRequestsANewDrawEachRoll() {
        Random controlled = new Random() { // anonymous subclass of Random
            private int next = 0; // a counter that lives inside the fake Random

            @Override
            public int nextInt(int bound) {
                assertEquals(MAX_VALUE, bound, "The random face must have exactly: " + MAX_VALUE + " faces");
                return next++ % MAX_VALUE;
            }
        };
        var die = new BoostDie(controlled);
        for (int i = 0; i < TEST_OUTCOME_ROLLS; i++) {
            assertEquals(i % MAX_VALUE + 1, die.roll());
        }
    }

    @Test
    void seededRollsStayWithinRangeAndEventuallyProduceEveryFace() {
        var die = new BoostDie(new Random(SEE_ALL_FACES_SEED));
        boolean[] seen = new boolean[MAX_VALUE + 1];
        for (int i = 0; i < SEE_ALL_FACES_ROLLS; i++) {
            int roll = die.roll();
            assertTrue(roll >= 1 && roll <= MAX_VALUE);
            seen[roll] = true;
        }
        for (int i = 1; i <= MAX_VALUE; i++) {
            assertTrue(seen[i], "Face was not reached: " + i);
        }
    }

    @Test
    void defaultDieAlwaysProducesValuesWithinRange() {
        var die = new BoostDie();
        for (int i = 0; i < RANGE_CHECK_ROLLS; i++) {
            int roll = die.roll();
            assertTrue(roll >= 1 && roll <= MAX_VALUE);
        }
    }

    @Test
    void rejectsMissingRandomSource() {
        assertThrows(NullPointerException.class, () -> new BoostDie(null));
    }

}
