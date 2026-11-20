package Model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SquareTest {
    private final Position position = new Position(8, 3);

    @Test
    void questionDifficultyIsStoredIndependentlyOfSquareColor() {
        for (QuestionDifficulty difficulty : QuestionDifficulty.values()) {
            for (SquareColor color : SquareColor.values()) {
                Square square = new Square(position, SquareType.QUESTION, color,
                        SquareStatus.ACTIVE, difficulty);
                assertEquals(SquareType.QUESTION, square.type());
                assertEquals(color, square.associatedColor());
                assertEquals(difficulty, square.questionDifficulty());
            }
        }
    }

    @Test
    void questionSquaresCannotBeCreatedWithoutAQuestionDifficulty() {
        assertThrows(IllegalArgumentException.class, () -> new Square(position,
                SquareType.QUESTION, SquareColor.NEUTRAL, SquareStatus.ACTIVE));
        assertThrows(IllegalArgumentException.class, () -> new Square(position,
                SquareType.QUESTION, SquareColor.NEUTRAL, SquareStatus.ACTIVE,
                (QuestionDifficulty) null));
    }

    @Test
    void otherSquareTypesHaveNoQuestionDifficultyAndRejectOne() {
        for (SquareType type : SquareType.values()) {
            if (type == SquareType.QUESTION) continue;
            Square square = new Square(position, type, SquareColor.NEUTRAL, SquareStatus.ACTIVE);
            assertNull(square.questionDifficulty());
            for (QuestionDifficulty difficulty : QuestionDifficulty.values()) {
                assertThrows(IllegalArgumentException.class, () -> new Square(position,
                        type, SquareColor.NEUTRAL, SquareStatus.ACTIVE, difficulty));
            }
        }
    }
}
