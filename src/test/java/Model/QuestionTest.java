package Model;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class QuestionTest {
    private static final int ANSWER_COUNT = 4;
    private static final int MIN_INDEX = 0;
    private static final int MAX_INDEX = ANSWER_COUNT - 1;

    @Test
    void acceptsValidQuestion() {
        var answers = List.of("answer1", "answer2", "answer3", "answer4");
        var question = new Question("Question text", answers, 1, QuestionDifficulty.EASY);

        assertEquals("Question text", question.text());
        assertEquals(answers, question.answers());
        assertEquals(1, question.correctIndex());
        assertEquals(QuestionDifficulty.EASY, question.difficulty());
    }

    @Test
    void acceptsMinimumCorrectIndex() {
        var answers = List.of("answer1", "answer2", "answer3", "answer4");
        var question = new Question("Question text", answers, MIN_INDEX, QuestionDifficulty.EASY);

        assertEquals("Question text", question.text());
        assertEquals(answers, question.answers());
        assertEquals(MIN_INDEX, question.correctIndex());
        assertEquals(QuestionDifficulty.EASY, question.difficulty());
    }

    @Test
    void acceptsMaximumCorrectIndex() {
        var answers = List.of("answer1", "answer2", "answer3", "answer4");
        var question = new Question("Question text", answers, MAX_INDEX, QuestionDifficulty.EASY);

        assertEquals("Question text", question.text());
        assertEquals(answers, question.answers());
        assertEquals(MAX_INDEX, question.correctIndex());
        assertEquals(QuestionDifficulty.EASY, question.difficulty());
    }

    @Test
    void rejectsNullQuestionText() {
        var answers = List.of("answer1", "answer2", "answer3", "answer4");
        var e = assertThrows(IllegalArgumentException.class,
                () -> new Question(null, answers, MAX_INDEX, QuestionDifficulty.EASY));

        assertEquals("Question text cannot be null or blank", e.getMessage());
    }

    @Test
    void rejectsBlankQuestionText() {
        var answers = List.of("answer1", "answer2", "answer3", "answer4");
        var e = assertThrows(IllegalArgumentException.class,
                () -> new Question("    ", answers, MAX_INDEX, QuestionDifficulty.EASY));

        assertEquals("Question text cannot be null or blank", e.getMessage());
    }

    @Test
    void rejectsNullQuestionDifficulty() {
        var answers = List.of("answer1", "answer2", "answer3", "answer4");
        var e = assertThrows(IllegalArgumentException.class,
                () -> new Question("Question text", answers, MAX_INDEX, null));

        assertEquals("Question difficulty cannot be null", e.getMessage());
    }

    @Test
    void rejectsNullAnswerList() {
        var e = assertThrows(IllegalArgumentException.class,
                () -> new Question("Question text", null, MAX_INDEX, QuestionDifficulty.EASY));

        assertEquals("Answers cannot be null", e.getMessage());
    }

    @Test
    void rejectsNullAnswer() {
        var answers = Arrays.asList("answer1", null, "answer3", "answer4");
        var e = assertThrows(IllegalArgumentException.class,
                () -> new Question("Question text", answers, MAX_INDEX, QuestionDifficulty.EASY));

        assertEquals("Answers cannot contain null or blank options", e.getMessage());
    }

    @Test
    void rejectsBlankAnswer() {
        var answers = Arrays.asList("answer1", "answer2", "answer3", "  ");
        var e = assertThrows(IllegalArgumentException.class,
                () -> new Question("Question text", answers, MAX_INDEX, QuestionDifficulty.EASY));

        assertEquals("Answers cannot contain null or blank options", e.getMessage());
    }

    @Test
    void rejectsTooFewAnswers() {
        var answers = Arrays.asList("answer1", "answer2");
        var e = assertThrows(IllegalArgumentException.class,
                () -> new Question("Question text", answers, MAX_INDEX, QuestionDifficulty.EASY));

        assertEquals("Answers must contain exactly " + ANSWER_COUNT + " options" +
                ", " + "but " + answers.size() + " were provided", e.getMessage());
    }

    @Test
    void rejectsTooManyAnswers() {
        var answers = Arrays.asList("answer1", "answer2", "answer3", "answer4", "answer5");
        var e = assertThrows(IllegalArgumentException.class,
                () -> new Question("Question text", answers, MAX_INDEX, QuestionDifficulty.EASY));

        assertEquals("Answers must contain exactly " + ANSWER_COUNT + " options" +
                ", " + "but " + answers.size() + " were provided", e.getMessage());
    }

    @Test
    void rejectsDuplicateAnswersIgnoringCaseAndWhitespace() {
        var answers = List.of("answer1", " Answer1 ", "answer2", "answer3");
        var e = assertThrows(IllegalArgumentException.class,
                () -> new Question("Question text", answers, MAX_INDEX, QuestionDifficulty.EASY));

        assertEquals("Answers must be distinct", e.getMessage());
    }

    @Test
    void rejectsNegativeCorrectIndex() {
        var index = -1;

        var e = assertThrows(IllegalArgumentException.class,
                () -> new Question("Question text",
                        List.of("answer1", "answer2", "answer3", "answer4"), index, QuestionDifficulty.EASY));

        assertEquals("Correct index must be between "
                + MIN_INDEX + "," + " and " + (ANSWER_COUNT - 1)
                + " but got " + index, e.getMessage());
    }

    @Test
    void rejectsCorrectIndexAboveMaximum() {
        var index = ANSWER_COUNT;
        var e = assertThrows(IllegalArgumentException.class,
                () -> new Question("Question text",
                        List.of("answer1", "answer2", "answer3", "answer4"), index, QuestionDifficulty.EASY));

        assertEquals("Correct index must be between "
                + MIN_INDEX + "," + " and " + (ANSWER_COUNT - 1)
                + " but got " + index, e.getMessage());
    }

    @Test
    void defensivelyCopiesAnswers() {
        var answers = new ArrayList<>(List.of("answer1", "answer2", "answer3", "answer4"));
        var question = new Question("Question text", answers, MAX_INDEX, QuestionDifficulty.EASY);

        answers.set(0, "answer5");
        answers.add("answer8");

        assertEquals(List.of("answer1", "answer2", "answer3", "answer4"), question.answers());
    }

    @Test
    void answersListIsUnmodifiable() {
        var answers = new ArrayList<>(List.of("answer1", "answer2", "answer3", "answer4"));
        var question = new Question("Question text", answers, MAX_INDEX, QuestionDifficulty.EASY);

        assertThrows(UnsupportedOperationException.class, () -> question.answers().set(0, "answer5"));
        assertThrows(UnsupportedOperationException.class, () -> question.answers().add("answer8"));
    }
}
