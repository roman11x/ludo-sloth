package Model;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class QuestionPickerTest {
    private static final List<Question> questions = List.of(new Question(
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
            ));


    @Test
    void returnsNoRepeatsUntilEmpty() {
        QuestionBank bank = () -> questions;
        var picker = new QuestionPicker(bank);
        Set<Question> pickedQuestions = new HashSet<>();
        var easyQuestions = questions.stream()
                .filter(question -> question.difficulty() == QuestionDifficulty.EASY)
                .toList();

        for (int i = 0; i < easyQuestions.size(); i++) {
            var question = picker.pickQuestion(QuestionDifficulty.EASY);
            assertFalse(pickedQuestions.contains(question));
            pickedQuestions.add(question);
        }

        assertEquals(new HashSet<>(easyQuestions), pickedQuestions);
    }

    @Test
    void pickingAfterEmptyIsAccepted() {
        QuestionBank bank = () -> questions;
        var picker = new QuestionPicker(bank);
        var mediumQuestions = questions.stream()
                .filter(question -> question.difficulty() == QuestionDifficulty.MEDIUM)
                .toList();
        Set<Question> pickedQuestions = new HashSet<>();

        for (int i = 0; i < mediumQuestions.size(); i++) {
            var question = picker.pickQuestion(QuestionDifficulty.MEDIUM);
            pickedQuestions.add(question);
        }

        var question = picker.pickQuestion(QuestionDifficulty.MEDIUM);


        assertTrue(pickedQuestions.contains(question));
    }

    @Test
    void emptyDifficultyListIsRejected() {
        QuestionBank bank = () -> questions;
        var picker = new QuestionPicker(bank);
        assertThrows(IllegalStateException.class, () -> picker.pickQuestion(QuestionDifficulty.HARD));
    }
}
