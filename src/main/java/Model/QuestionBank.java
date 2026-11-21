package Model;

import java.util.List;

/**
 * Represents a source of questions for the game.
 * <p>
 * Implementations provide access to the full collection of questions available
 * in a question bank.
 */
public interface QuestionBank {

    /**
     * Returns all questions available in this question bank.
     * <p>
     * The returned list must not be {@code null} and must be unmodifiable.
     *
     * @return an unmodifiable, non-null list containing all questions in this question bank
     */
    List<Question> allQuestions();
}
