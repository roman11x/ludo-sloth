package Model;

import java.util.*;

/**
 * Organizes questions by {@link QuestionDifficulty} for randomized selection.
 * <p>
 * The picker keeps two lists for each difficulty: an unmodifiable master list
 * containing every question supplied by the {@link QuestionBank}, and a mutable
 * working list containing the questions that remain available for selection.
 * Keeping a separate working list allows questions to be removed as they are
 * selected without changing the complete collection supplied by the bank. A
 * question is selected at most once per difficulty-specific cycle; after every
 * question of a difficulty has been selected, the working list is replenished
 * from the master list for the next cycle.
 *
 * @see QuestionBank
 * @see Question
 * @see QuestionDifficulty
 */
public final class QuestionPicker {
    /**
     * All questions grouped by difficulty, stored in unmodifiable lists.
     */
    private final Map<QuestionDifficulty, List<Question>> allQuestionsByDifficulty;

    /**
     * Questions still available in the current selection cycle, grouped in
     * mutable lists.
     */
    private final Map<QuestionDifficulty, List<Question>> leftQuestionsByDifficulty;

    /**
     * Source of randomness used when selecting a question.
     */
    private final Random random;

    /**
     * Creates a question picker backed by the specified question bank and a new
     * source of randomness.
     *
     * @param bank the question bank whose questions will be organized by difficulty
     * @throws NullPointerException if {@code bank} is {@code null}
     */
    public QuestionPicker(QuestionBank bank) {
        this(bank, new Random());
    }

    /**
     * Creates a question picker backed by the specified question bank and source
     * of randomness.
     * <p>
     * Accepting the random source as an argument allows callers and tests to use
     * deterministic random behavior when necessary.
     *
     * @param bank   the question bank whose questions will be organized by difficulty
     * @param random the source of randomness used when selecting questions
     * @throws NullPointerException if {@code bank} or {@code random} is {@code null}
     */
    public QuestionPicker(QuestionBank bank, Random random) {
        Objects.requireNonNull(bank, "bank");
        allQuestionsByDifficulty = new EnumMap<>(QuestionDifficulty.class);
        leftQuestionsByDifficulty = new EnumMap<>(QuestionDifficulty.class);
        this.random = Objects.requireNonNull(random, "random");
        initializeQuestionMaps(bank.allQuestions());
    }

    /**
     * Groups the supplied questions by difficulty and initializes both internal
     * maps. Every {@link QuestionDifficulty} is added as a key, even when no
     * questions have that difficulty.
     * <p>
     * Each master list is produced as an unmodifiable list, while each remaining
     * questions list is stored as an independent, mutable copy.
     *
     * @param questions the non-null questions to group by difficulty
     * @throws NullPointerException if {@code questions} is {@code null}
     */
    private void initializeQuestionMaps(List<Question> questions) {
        for (QuestionDifficulty difficulty : QuestionDifficulty.values()) {
            var filteredQuestions = questions.stream()
                    .filter(question -> question.difficulty() == difficulty)
                    .toList();
            allQuestionsByDifficulty.put(difficulty, filteredQuestions);
            leftQuestionsByDifficulty.put(difficulty, new ArrayList<>(filteredQuestions));
        }
    }

    /**
     * Randomly selects a question of the specified difficulty and removes it from
     * the questions remaining in the current selection cycle.
     * <p>
     * Questions are not repeated within a cycle. If every question of the
     * requested difficulty has already been selected, the remaining-questions
     * list is replenished from the master list before a question is selected for
     * the new cycle.
     *
     * @param difficulty the difficulty of the question to select
     * @return a randomly selected question of the requested difficulty
     * @throws NullPointerException if {@code difficulty} is {@code null}
     * @throws IllegalStateException if the question bank contains no questions
     *                               of the requested difficulty
     */
    public Question pickQuestion(QuestionDifficulty difficulty) {
        Objects.requireNonNull(difficulty, "difficulty");

        var allQuestions = allQuestionsByDifficulty.get(difficulty);
        if (allQuestions.isEmpty()) {
            throw new IllegalStateException("No questions available for difficulty: " + difficulty);
        }


        if (leftQuestionsByDifficulty.get(difficulty).isEmpty()) {
            leftQuestionsByDifficulty.put(difficulty, new ArrayList<>(allQuestions));
        }

        var remainingQuestions = leftQuestionsByDifficulty.get(difficulty);
        return remainingQuestions.remove(random.nextInt(remainingQuestions.size()));
    }
}
