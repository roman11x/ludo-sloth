package Model;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link Game#answerQuestion(long, int)}. The board has one Easy question square on
 * Green's path at index 3, the die always rolls 1, and the fake bank always asks {@link #QUESTION}.
 * So {@code playFrom(game, 2)} lands Green on the question square, and an Easy question moves 1 square.
 */
class QuestionAnswerTest {
    // The only question the fake bank returns; the correct answer is index 1 ("B").
    private static final Question QUESTION = new Question("Pick B",
            List.of("A", "B", "C", "D"), 1, QuestionDifficulty.EASY);
    private final AtomicLong nanos = new AtomicLong();
    private final Piece green = new Piece(PlayerColor.GREEN, 1);

    // ---- tests ----

    @Test
    void correctAnswerMovesThePieceForwardAndReturnsTrue() {
        Game game = game();
        playFrom(game, 2);                                   // Green lands on the question square (index 3)

        boolean correct = game.answerQuestion(pending(game).id(), 1);

        assertTrue(correct);
        assertEquals(at(game, 4), location(game, green));    // Easy = 1 square forward
        assertTrue(game.pendingSquareEffect().isEmpty());    // the effect is resolved
    }

    @Test
    void wrongAnswerMovesThePieceBackwardAndReturnsFalse() {
        Game game = game();
        playFrom(game, 2);

        boolean correct = game.answerQuestion(pending(game).id(), 0);

        assertFalse(correct);
        assertEquals(at(game, 2), location(game, green));    // Easy = 1 square back
        assertTrue(game.pendingSquareEffect().isEmpty());
    }

    @Test
    void landingAnnouncesThePickedQuestionWithThePendingEffectId() {
        Game game = game();
        List<Long> ids = new ArrayList<>();                  // filled by the listener below
        List<Question> questions = new ArrayList<>();
        game.addListener(new GameListener() {
            @Override public void questionRequired(long effectId, Player player, Piece piece, Question question) {
                ids.add(effectId);
                questions.add(question);
            }
        });

        playFrom(game, 2);                                   // the announcement is sent when the piece lands

        assertEquals(List.of(QUESTION), questions);
        assertEquals(List.of(pending(game).id()), ids);
    }

    @Test
    void invalidAnswerIndexIsRejectedAndTheQuestionStaysPending() {
        Game game = game();
        playFrom(game, 2);
        long id = pending(game).id();

        assertThrows(IllegalArgumentException.class, () -> game.answerQuestion(id, -1));
        assertThrows(IllegalArgumentException.class, () -> game.answerQuestion(id, 4));

        assertEquals(id, pending(game).id());                // still waiting for an answer
        assertEquals(at(game, 3), location(game, green));    // the piece did not move
    }

    @Test
    void wrongEffectIdIsRejected() {
        Game game = game();
        playFrom(game, 2);

        assertThrows(IllegalStateException.class, () -> game.answerQuestion(pending(game).id() + 1, 1));
        assertTrue(game.pendingSquareEffect().isPresent());  // the real question is still pending
    }

    @Test
    void answeringWhenNoQuestionIsPendingIsRejected() {
        Game game = game();                                  // nothing has landed yet

        assertThrows(IllegalStateException.class, () -> game.answerQuestion(1, 1));
    }

    // ---- helpers (borrowed from ChainedSquareEffectsTest) ----

    /** Easy board with one Easy question square at index 3 of Green's path, and our fake question bank. */
    private Game game() {
        var routes = PlayerRouteFactory.createEasy();
        var greenPath = routes.get(PlayerColor.GREEN).positions();
        Board board = new BoardLayout() {
            private final EasyLayout inner = new EasyLayout();
            @Override protected void createCommonSquares(Square[][] squares) { inner.createCommonSquares(squares); }
            @Override protected void createStartsAndArrows(Square[][] squares) { inner.createStartsAndArrows(squares); }
            @Override protected void createHomesAndCastles(Square[][] squares) { inner.createHomesAndCastles(squares); }
            @Override protected void placeSpecialSquares(Square[][] squares, Random random) {
                Position position = greenPath.get(3);
                defineSquare(squares, position.row(), position.column(), SquareType.QUESTION,
                        SquareColor.NEUTRAL, SquareStatus.ACTIVE, QuestionDifficulty.EASY);
            }
        }.build(new Random(42));
        QuestionPicker picker = new QuestionPicker(() -> List.of(QUESTION));
        return new Game(List.of(new Player("Green", PlayerColor.GREEN), new Player("Red", PlayerColor.RED)),
                Difficulty.EASY, () -> 1, new Random() {
                    @Override public int nextInt(int bound) { return 0; }   // Green goes first
                }, board, routes, Clock.fixed(Instant.parse("2026-11-26T00:00:00Z"), ZoneOffset.UTC),
                nanos::get, picker);
    }

    private Position at(Game game, int index) {
        return game.routes().get(PlayerColor.GREEN).positions().get(index);
    }

    private Position location(Game game, Piece piece) {
        return game.board().occupancy().entrySet().stream().filter(e -> e.getValue().contains(piece))
                .map(Map.Entry::getKey).findFirst().orElseThrow();
    }

    /** Puts Green on path square {@code index}, rolls 1 and moves it onto the next square. */
    private void playFrom(Game game, int index) {
        game.board().movePiece(green, location(game, green), at(game, index));
        game.roll();
        game.play(green);
    }

    private PendingSquareEffect pending(Game game) {
        return game.pendingSquareEffect().orElseThrow();
    }
}
