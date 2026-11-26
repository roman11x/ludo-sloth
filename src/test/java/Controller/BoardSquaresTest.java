package Controller;

import Model.Board;
import Model.PlayerRouteFactory;
import Model.Position;
import Model.QuestionDifficulty;
import Model.SquareColor;
import Model.SquareType;
import View.BoardView.BoardSquare;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.*;

class BoardSquaresTest {

    private static Map<String, Long> countByKind(BoardSquares board) {
        return board.squares().stream().collect(Collectors.groupingBy(BoardSquare::kind, Collectors.counting()));
    }

    private static BoardSquare at(BoardSquares board, int row, int column) {
        return board.squares().stream().filter(s -> s.row() == row && s.column() == column).findFirst().orElse(null);
    }

    private static List<BoardSquare> ofKind(BoardSquares board, String kind) {
        return board.squares().stream().filter(s -> s.kind().equals(kind)).toList();
    }

    @Test
    void easyBoardIsTheFifteenByFifteenGridWithEveryKindOfSquare() {
        BoardSquares easy = BoardSquares.of(Board.createEasy(new Random(1)), PlayerRouteFactory.createEasy());
        assertEquals(15, easy.gridSize());
        assertEquals(Map.of("path", 40L, "protected", 4L, "start", 4L, "arrow", 4L,
                "home", 20L, "finish", 4L, "castle", 144L), countByKind(easy));
        for (BoardSquare square : easy.squares()) {
            assertTrue(square.row() >= 0 && square.row() < 15 && square.column() >= 0 && square.column() < 15);
        }
    }

    @Test
    void easyStartsAndArrowsSitWhereTheBoardScreenAlwaysDrewThem() {
        BoardSquares easy = BoardSquares.of(Board.createEasy(new Random(1)), PlayerRouteFactory.createEasy());
        assertEquals(new BoardSquare(6, 1, "start", "Green", null, null), at(easy, 6, 1));
        assertEquals(new BoardSquare(1, 8, "start", "Yellow", null, null), at(easy, 1, 8));
        assertEquals(new BoardSquare(8, 13, "start", "Blue", null, null), at(easy, 8, 13));
        assertEquals(new BoardSquare(13, 6, "start", "Red", null, null), at(easy, 13, 6));
        assertEquals(new BoardSquare(7, 0, "arrow", "Green", null, "right"), at(easy, 7, 0));
        assertEquals(new BoardSquare(0, 7, "arrow", "Yellow", null, "down"), at(easy, 0, 7));
        assertEquals(new BoardSquare(7, 14, "arrow", "Blue", null, "left"), at(easy, 7, 14));
        assertEquals(new BoardSquare(14, 7, "arrow", "Red", null, "up"), at(easy, 14, 7));
    }

    @Test
    void easyProtectedSquaresComeFromTheBoard() {
        Board board = Board.createEasy(new Random(7));
        BoardSquares easy = BoardSquares.of(board, PlayerRouteFactory.createEasy());
        for (BoardSquare star : ofKind(easy, "protected")) {
            Position position = new Position(star.row() + 2, star.column() + 2);
            assertEquals(SquareType.PROTECTED, board.squareAt(position).type());
        }
    }

    @Test
    void mediumBoardIsSeventeenBySeventeenWithTheOuterPathStartsAtTheCornersAndTwoArrowsPerColour() {
        BoardSquares medium = BoardSquares.medium();
        assertEquals(17, medium.gridSize());
        assertEquals(Map.of("path", 88L, "protected", 8L, "question", 6L, "surprise", 2L, "start", 4L,
                "arrow", 8L, "home", 20L, "finish", 4L, "castle", 144L), countByKind(medium));
        assertEquals(Map.of("Easy", 2L, "Medium", 2L, "Hard", 2L), ofKind(medium, "question").stream()
                .collect(Collectors.groupingBy(BoardSquare::level, Collectors.counting())));
        assertEquals(new BoardSquare(0, 0, "start", "Green", null, null), at(medium, 0, 0));
        assertEquals(new BoardSquare(0, 16, "start", "Yellow", null, null), at(medium, 0, 16));
        assertEquals(new BoardSquare(16, 16, "start", "Blue", null, null), at(medium, 16, 16));
        assertEquals(new BoardSquare(16, 0, "start", "Red", null, null), at(medium, 16, 0));
        // Outer arrows turn onto the inner ring, inner arrows into the Home Path
        assertEquals(new BoardSquare(7, 0, "arrow", "Green", null, "right"), at(medium, 7, 0));
        assertEquals(new BoardSquare(8, 1, "arrow", "Green", null, "right"), at(medium, 8, 1));
        assertEquals(new BoardSquare(0, 9, "arrow", "Yellow", null, "down"), at(medium, 0, 9));
        assertEquals(new BoardSquare(1, 8, "arrow", "Yellow", null, "down"), at(medium, 1, 8));
        assertEquals(new BoardSquare(9, 16, "arrow", "Blue", null, "left"), at(medium, 9, 16));
        assertEquals(new BoardSquare(8, 15, "arrow", "Blue", null, "left"), at(medium, 8, 15));
        assertEquals(new BoardSquare(16, 7, "arrow", "Red", null, "up"), at(medium, 16, 7));
        assertEquals(new BoardSquare(15, 8, "arrow", "Red", null, "up"), at(medium, 15, 8));
    }

    @Test
    void formerEasyStartsAreNoLongerStartsOnTheMediumBoard() {
        BoardSquares medium = BoardSquares.medium();
        // A former Easy Start may now hold a random special square, so only its kind "start" is ruled out
        for (int[] former : new int[][]{{7, 2}, {2, 9}, {9, 14}, {14, 7}}) {
            assertNotEquals("start", at(medium, former[0], former[1]).kind());
        }
    }

    @Test
    void piecePositionsAreShiftedByTheSameOffsetAsTheSquares() {
        BoardSquares easy = BoardSquares.of(Board.createEasy(new Random(1)), PlayerRouteFactory.createEasy());
        BoardSquares medium = BoardSquares.medium();
        assertEquals(7, easy.gridRow(new Position(9, 2)));
        assertEquals(0, easy.gridColumn(new Position(9, 2)));
        assertEquals(7, medium.gridRow(new Position(8, 1)));
        assertEquals(0, medium.gridColumn(new Position(8, 1)));
    }

    @Test
    void kindsColoursAndLevelsBecomeTheWordsTheBoardScreenUses() {
        assertEquals("question", BoardSquares.kindWord(SquareType.QUESTION));
        assertEquals("surprise", BoardSquares.kindWord(SquareType.SURPRISE));
        assertEquals("home", BoardSquares.kindWord(SquareType.HOME_PATH));
        assertEquals("finish", BoardSquares.kindWord(SquareType.FINISHING));
        assertThrows(IllegalArgumentException.class, () -> BoardSquares.kindWord(SquareType.EMPTY));
        assertEquals("Easy", BoardSquares.levelWord(QuestionDifficulty.EASY));
        assertEquals("Medium", BoardSquares.levelWord(QuestionDifficulty.MEDIUM));
        assertEquals("Hard", BoardSquares.levelWord(QuestionDifficulty.HARD));
        assertNull(BoardSquares.levelWord(null));
        assertEquals("Yellow", BoardSquares.colourWord(SquareColor.YELLOW));
        assertNull(BoardSquares.colourWord(SquareColor.NEUTRAL));
    }

    @Test
    void theListOfSquaresCannotBeChanged() {
        BoardSquares medium = BoardSquares.medium();
        assertThrows(UnsupportedOperationException.class, () -> medium.squares().clear());
    }
}
