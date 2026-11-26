package Model;

import java.util.Objects;
import java.util.Random;

/** SPC-001: independently selects one event with weights 10/10/10/30/30/10 percent. */
public final class SurpriseEventSelector {
    private final Random random;

    public SurpriseEventSelector() {
        this(new Random());
    }

    /** Allows a seeded or controlled random source for reproducible tests. */
    public SurpriseEventSelector(Random random) {
        this.random = Objects.requireNonNull(random, "random");
    }

    /** Uses one uniform draw in 0-99; eligibility and event execution are handled separately. */
    public SurpriseEvent select() {
        int value = random.nextInt(100);
        if (value < 10) return SurpriseEvent.MOVE_TO_FINISH;
        if (value < 20) return SurpriseEvent.MOVE_OPPONENT_BACKWARD;
        if (value < 30) return SurpriseEvent.RETURN_TO_CASTLE;
        if (value < 60) return SurpriseEvent.MOVE_BACK_THREE;
        if (value < 90) return SurpriseEvent.SKIP_NEXT_TURN;
        return SurpriseEvent.EXTRA_ULTRA_TURN;
    }
}
