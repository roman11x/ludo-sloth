package Model;

import java.util.List;
import java.util.Locale;

public record Question(String text,
                       List<String> answers,
                       int correctIndex,
                       QuestionDifficulty difficulty) {

    private static final int ANSWER_COUNT = 4;
    private static final int MIN_INDEX = 0; // Minimum valid index for correct answer

    /**
     * Creates a question with the specified text, answer options, correct answer index, and difficulty.
     * Validates that the text is not null or blank, the difficulty is not null, the answer options are
     * valid and distinct, and the correct answer index is within the allowed range. The answer options
     * are defensively copied into an unmodifiable list.
     *
     * @param text the question text
     * @param answers the answer options
     * @param correctIndex the index of the correct answer
     * @param difficulty the question difficulty
     * @throws IllegalArgumentException if the text is null or blank, the difficulty is null, the answer
     *                                  options are null, invalid, not distinct, or not the required
     *                                  count, or the correct answer index is outside the allowed range
     */
    public Question {
        validateQuestionText(text);
        validateQuestionDifficulty(difficulty);
        answers = List.copyOf(validateAnswers(answers));
        validateCorrectIndex(correctIndex);
    }


    /**
     * Validates that the question text is not null or empty.
     *
     * @param text the question text to validate
     * @throws IllegalArgumentException if the question text is null or empty
     */
    private static void validateQuestionText(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Question text cannot be null or empty");
        }
    }

    /**
     * Validates that the question difficulty is not null.
     *
     * @param difficulty the question difficulty to validate
     * @throws IllegalArgumentException if the question difficulty is null
     */
    private static void validateQuestionDifficulty(QuestionDifficulty difficulty) {
        if (difficulty == null) {
            throw new IllegalArgumentException("Question difficulty cannot be null");
        }
    }

    /**
     * Validates that the provided answer options are not null, contain exactly the required number of
     * options, contain no null or blank values, and are distinct after normalization.
     *
     * @param answers the answer options to validate
     * @return the validated answer options
     * @throws IllegalArgumentException if the answer options are null, do not contain exactly the
     *                                  required number of options, contain null or blank values, or
     *                                  contain duplicate values
     */
    private static List<String> validateAnswers(List<String> answers) {
        if (answers == null) {
            throw new IllegalArgumentException("Answers cannot be null");
        }
        if (answers.size() != ANSWER_COUNT) {
            throw new IllegalArgumentException("Answers must contain exactly " + ANSWER_COUNT + " options" +
                    ", " + "but " + answers.size() + " were provided");
        }

        for (String answer : answers) {
            if (answer == null || answer.isBlank()) {
                throw new IllegalArgumentException("Answers cannot contain null or blank options");
            }
        }

        if (!hasDistinctAnswers(answers)) {
            throw new IllegalArgumentException("Answers must be distinct");
        }

        return answers;

    }

    /**
     * Validates that the correct answer index is within the allowed answer index range.
     *
     * @param correctIndex the correct answer index to validate
     * @throws IllegalArgumentException if the correct answer index is less than the minimum index or
     *                                  greater than or equal to the required answer count
     */
    private static void validateCorrectIndex(int correctIndex) {
        if (correctIndex < MIN_INDEX || correctIndex >= ANSWER_COUNT) {
            throw new IllegalArgumentException("Correct index must be between "
                    + MIN_INDEX + " and " + (ANSWER_COUNT - 1)
            + " but got " + correctIndex);
        }
    }

    /**
     * Determines whether all provided answers are distinct after stripping surrounding whitespace
     * and comparing them case-insensitively.
     *
     * @param answers the answer options to check
     * @return true if all normalized answers are distinct; false otherwise
     */
    private static boolean hasDistinctAnswers(List<String> answers) {
        return answers.stream()
                .map(answer -> answer.toLowerCase(Locale.ROOT))
                .map(String::strip)
                .distinct()
                .count() == answers.size();
    }
}
