package Model;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class StandardDieTest {
    @Test
    void mapsAllSixUniformRandomOutcomesOneToOneAndRequestsANewDrawEachRoll() {
        Random controlled = new Random() {
            private int next = 0;

            @Override
            public int nextInt(int bound) {
                assertEquals(6, bound, "The random draw must have exactly six outcomes");
                return next++ % 6;
            }
        };
        Die die = new StandardDie(controlled);
        for (int roll = 0; roll < 12; roll++) {
            assertEquals(roll % 6 + 1, die.roll());
        }
    }

    @Test
    void seededRollsStayWithinRangeAndCanReachEveryFace() {
        Die die = new StandardDie(new Random(42));
        boolean[] seen = new boolean[7];
        for (int i = 0; i < 10000; i++) {
            int result = die.roll();
            assertTrue(result >= 1 && result <= 6);
            seen[result] = true;
        }
        for (int face = 1; face <= 6; face++) {
            assertTrue(seen[face], "Face was not reached: " + face);
        }
    }

    @Test
    void defaultDieProducesValuesWithinRange() {
        Die die = new StandardDie();
        for (int i = 0; i < 100; i++) {
            int result = die.roll();
            assertTrue(result >= 1 && result <= 6);
        }
    }

    @Test
    void rejectsMissingRandomSource() {
        assertThrows(NullPointerException.class, () -> new StandardDie(null));
    }
}
