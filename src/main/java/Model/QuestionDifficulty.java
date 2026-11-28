package Model;

/**
 * Difficulty of a question, independent of the game difficulty and square color.
 * Each difficulty has an associated number of steps: easy questions have one,
 * medium questions have two, and hard questions have three.
 */
public enum QuestionDifficulty {
    EASY(1),
    MEDIUM(2),
    HARD(3);

    private final int steps;

    QuestionDifficulty(int steps) {
        this.steps = steps;
    }

    /**
     * Returns the number of squares moved for this question difficulty.
     *
     * @return the number of squares moved
     */
    public int steps() {
        return steps;
    }
}
