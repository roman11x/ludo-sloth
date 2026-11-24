package Model;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class MediumSpecialSquaresTest {
    private boolean isSpecial(Square square) {
        return List.of(SquareType.PROTECTED, SquareType.QUESTION, SquareType.SURPRISE).contains(square.type());
    }

    private List<Square> squares(Board board) {
        List<Square> squares = new ArrayList<>();
        for (int row = 0; row < Board.STORAGE_SIZE; row++) {
            for (int column = 0; column < Board.STORAGE_SIZE; column++) {
                squares.add(board.squareAt(new Position(row, column)));
            }
        }
        return squares;
    }

    private boolean isCommonPath(Position p) {
        int row = p.row();
        int column = p.column();
        boolean outer = row >= 1 && row <= 17 && column >= 1 && column <= 17
                && (row == 1 || row == 17 || column == 1 || column == 17);
        boolean inner = ((row == 8 || row == 10) && ((column >= 2 && column <= 7)
                || (column >= 11 && column <= 16)))
                || ((column == 8 || column == 10) && ((row >= 2 && row <= 7)
                || (row >= 11 && row <= 16)))
                || List.of(new Position(2, 9), new Position(9, 16),
                new Position(16, 9), new Position(9, 2)).contains(p);
        return outer || inner;
    }

    @Test
    void exactlySixteenDistinctSpecialSquaresHaveCorrectCountsAndLegalLocationsAcrossSeeds() {
        for (int seed = 0; seed < 100; seed++) {
            Board board = Board.createMedium(new Random(seed));
            var all = squares(board);
            var specials = all.stream().filter(this::isSpecial).toList();
            assertEquals(16, specials.size());
            assertEquals(16, new HashSet<>(specials.stream().map(Square::position).toList()).size());
            assertEquals(8, specials.stream().filter(s -> s.type() == SquareType.PROTECTED).count());
            assertEquals(6, specials.stream().filter(s -> s.type() == SquareType.QUESTION).count());
            assertEquals(2, specials.stream().filter(s -> s.type() == SquareType.SURPRISE).count());
            for (QuestionDifficulty difficulty : QuestionDifficulty.values()) {
                assertEquals(2, specials.stream().filter(s -> s.questionDifficulty() == difficulty).count());
            }
            assertEquals(88, all.stream().filter(s -> s.type() == SquareType.NORMAL).count());
            for (Square square : specials) {
                assertTrue(isCommonPath(square.position()), "Special square outside common paths");
                assertEquals(SquareStatus.ACTIVE, square.status());
                assertEquals(SquareColor.NEUTRAL, square.associatedColor());
                assertTrue(square.pieces().isEmpty());
                assertEquals(square.type() == SquareType.QUESTION, square.questionDifficulty() != null);
            }
            for (PlayerColor color : PlayerColor.values()) {
                assertEquals(SquareType.START, board.squareAt(board.startFor(color)).type());
                for (Position arrow : board.arrowsFor(color)) {
                    assertEquals(SquareType.ARROW, board.squareAt(arrow).type());
                }
            }
        }
    }

    private Map<Position, String> specialDefinitions(Board board) {
        Map<Position, String> result = new HashMap<>();
        for (Square square : squares(board)) {
            if (isSpecial(square)) result.put(square.position(), square.type() + ":" + square.questionDifficulty());
        }
        return result;
    }

    @Test
    void sameSeedReproducesPositionsTypesAndQuestionDifficultiesAndDifferentSeedsVaryTheLayout() {
        var first = specialDefinitions(Board.createMedium(new Random(42)));
        assertEquals(first, specialDefinitions(Board.createMedium(new Random(42))));
        assertNotEquals(first, specialDefinitions(Board.createMedium(new Random(43))));
    }

    @Test
    void adjacentSpecialsAndUnbalancedRegionsAreAllowedWithoutDuplicateSelections() {
        Board board = Board.createMedium(new Random() {
            @Override public int nextInt(int bound) { return 0; }
        });
        assertTrue(isSpecial(board.squareAt(new Position(1, 2))));
        assertTrue(isSpecial(board.squareAt(new Position(1, 3))));
        // Fourteen available top-edge cells may all be selected; no regional quota is imposed.
        assertEquals(14, squares(board).stream().filter(this::isSpecial)
                .filter(s -> s.position().row() == 1).count());
        assertEquals(16, specialDefinitions(board).size());
    }

    @Test
    void protectionMatchesTheNewTypesAndDoesNotDependOnQuestionDifficulty() {
        Board board = Board.createMedium(new Random(42));
        MoveRules rules = new MoveRules();
        for (Square square : squares(board)) {
            boolean expected = List.of(SquareType.PROTECTED, SquareType.START,
                    SquareType.FINISHING, SquareType.SURPRISE).contains(square.type());
            assertEquals(expected, rules.isProtectedFromCapture(square));
        }
        assertEquals(18, squares(board).stream().filter(rules::isProtectedFromCapture).count());
    }

    @Test
    void specialLayoutIsIndependentOfTheTwoThreeOrFourActiveParticipants() {
        List<PlayerColor> colors = List.of(PlayerColor.GREEN, PlayerColor.YELLOW, PlayerColor.BLUE, PlayerColor.RED);
        var expected = specialDefinitions(Board.createMedium(new Random(42)));
        for (int mask = 0; mask < 16; mask++) {
            if (Integer.bitCount(mask) < 2) continue;
            Board board = Board.createMedium(new Random(42));
            for (int index = 0; index < colors.size(); index++) {
                if ((mask & (1 << index)) == 0) continue;
                Player player = new Player(colors.get(index).name(), colors.get(index));
                var slots = squares(board).stream().filter(s -> s.type() == SquareType.CASTLE
                        && s.status() == SquareStatus.ACTIVE
                        && s.associatedColor() == SquareColor.fromPlayerColor(player.color())).toList();
                assertEquals(4, slots.size());
                for (int number = 0; number < 4; number++) {
                    board.placePiece(player.pieces().get(number), slots.get(number).position());
                }
                assertEquals(SquareType.START, board.squareAt(board.startFor(player.color())).type());
            }
            assertEquals(Integer.bitCount(mask) * 4, board.occupancy().values().stream().mapToInt(List::size).sum());
            assertEquals(expected, specialDefinitions(board));
        }
    }

    @Test
    void rebuildingTheSameLayoutDoesNotChangeAnExistingBoard() {
        BoardLayout layout = new MediumLayout();
        Board first = layout.build(new Random(42));
        var original = specialDefinitions(first);
        Piece piece = new Piece(PlayerColor.GREEN, 1);
        Position start = first.startFor(PlayerColor.GREEN);
        first.placePiece(piece, start);
        Board second = layout.build(new Random(43));
        assertEquals(original, specialDefinitions(first));
        assertEquals(List.of(piece), first.piecesAt(start));
        assertTrue(second.piecesAt(start).isEmpty());
        assertThrows(NullPointerException.class, () -> Board.createMedium(null));
    }
}
