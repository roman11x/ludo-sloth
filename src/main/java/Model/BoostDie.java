package Model;

import java.util.Objects;
import java.util.Random;

/**
 * A die that produces a random boost value from 1 to 3 inclusive.
 * The die uses a supplied random source, or a new default random source when
 * created with the no-argument constructor.
 * Used in Medium difficulty.
 * @see Die
 */
public final class BoostDie implements Die {
    private final Random random;// the source of randomness this die uses

    public BoostDie() { // normal constructor, used by the real game
        this(new Random());
    }

    public BoostDie(Random random) { // constructor for tests: pass in a controlled Random
        this.random = Objects.requireNonNull(random, "random");
    }


    @Override
    public int roll() {
        return random.nextInt(3) + 1;
    }
}
