package Model;

import java.util.List;

/**
 * Temporary in-memory implementation of {@link QuestionBank} used while the
 * encrypted question bank is not yet available.
 * <p>
 * This class intentionally stores readable questions directly in the source code
 * so development and testing can continue. It must be replaced by an encrypted
 * question bank implementation before release, because SRS requirement SEC-002
 * does not allow readable questions to be packaged in the JAR.
 */
public class StubQuestionBank implements QuestionBank {
    private final List<Question> stubQuestions = List.of(
            new Question(
                    "Which document lists the software requirements?",
                    List.of("SDP", "SRS", "Scrum report", "Class diagram"),
                    1,
                    QuestionDifficulty.EASY
            ),
            new Question(
                    "In Scrum, who is responsible for the product backlog?",
                    List.of("Scrum master", "Development team", "Product owner", "Customer"),
                    2,
                    QuestionDifficulty.EASY
            ),
            new Question(
                    "Which IEEE standard is the recommended practice for SRS documents?",
                    List.of("IEEE 830", "IEEE 1058", "IEEE 829", "IEEE 1012"),
                    0,
                    QuestionDifficulty.MEDIUM
            ),
            new Question(
                    "In MVC, which part holds the application's data and rules?",
                    List.of("View", "Controller", "Router", "Model"),
                    3,
                    QuestionDifficulty.MEDIUM
            ),
            new Question(
                    "Testing a unit's code paths with knowledge of its internal structure is called:",
                    List.of("Black-box testing", "White-box testing", "Acceptance testing", "Usability testing"),
                    1,
                    QuestionDifficulty.HARD
            ),
            new Question(
                    "Which design pattern lets subclasses decide which class to create?",
                    List.of("Observer", "Template Method", "Factory Method", "Singleton"),
                    2,
                    QuestionDifficulty.HARD
            )
    );

    /**
     * Returns the temporary hard-coded questions used by this stub bank.
     * <p>
     * The returned list is non-null and unmodifiable, as required by the
     * {@link QuestionBank} contract.
     *
     * @return an unmodifiable, non-null list containing the stub questions
     */
    @Override
    public List<Question> allQuestions() {
        return stubQuestions;
    }
}
