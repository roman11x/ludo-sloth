package Model;

import java.util.Objects;
import java.util.Random;

/**
 * Standard die producing integers 1-6 with equal probability.
 * Easy uses only this die; turn rules and UI are managed separately.
 */
public final class StandardDie implements Die {
    private final Random random;

    public StandardDie() {
        this(new Random());
    }

    /** Allows a seeded or controlled random source for repeatable tests. */
    public StandardDie(Random random) {
        this.random = Objects.requireNonNull(random, "random");
    }

    @Override
    public int roll() {
        // nextInt(6) is uniform over 0-5; adding 1 preserves equal probabilities.
        return random.nextInt(6) + 1;
    }
}
