package Model;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class GameStateTest {
    private final Board board = Board.createEasy(new Random(42));

    @Test
    void initializesEveryTwoThreeAndFourPlayerColorCombinationInTheCorrectSlots() {
        PlayerColor[] colors = PlayerColor.values();
        int tested = 0;
        for (int mask = 0; mask < 16; mask++) {
            int count = Integer.bitCount(mask);
            if (count < 2) continue;
            var players = new ArrayList<PlayerSetup>();
            for (int i = 0; i < colors.length; i++) {
                if ((mask & (1 << i)) != 0) {
                    players.add(new PlayerSetup(colors[i].name(), colors[i]));
                }
            }

            Board board = Board.createEasy(new Random(42));
            GameState state = GameState.createInitial(board, players);
            Set<Piece> expected = new HashSet<>();
            for (PlayerSetup player : players) {
                for (int number = 1; number <= 4; number++) {
                    expected.add(new Piece(player.color(), number));
                }
            }
            Set<Piece> actual = new HashSet<>();
            int occupiedCount = 0;
            for (int row = 0; row < 19; row++) {
                for (int column = 0; column < 19; column++) {
                    Position position = new Position(row, column);
                    Square square = board.squareAt(position);
                    List<Piece> occupants = state.piecesAt(position);
                    boolean participating = players.stream().anyMatch(player ->
                            SquareColor.fromPlayerColor(player.color()) == square.associatedColor());
                    boolean shouldBeOccupied = participating
                            && square.type() == SquareType.CASTLE
                            && square.status() == SquareStatus.ACTIVE;
                    assertEquals(shouldBeOccupied ? 1 : 0, occupants.size(), position.toString());
                    if (shouldBeOccupied) {
                        Piece piece = occupants.getFirst();
                        assertEquals(square.associatedColor(), SquareColor.fromPlayerColor(piece.color()));
                        assertTrue(actual.add(piece));
                        occupiedCount++;
                    }
                }
            }
            assertEquals(expected, actual);
            assertEquals(count * 4, occupiedCount);
            assertEquals(count * 4, state.occupancy().size());
            assertEquals(players, state.players());
            tested++;
        }
        assertEquals(11, tested);
    }

    @Test
    void assignsNumbersByTheAgreedCastleSlotOrder() {
        GameState state = GameState.createInitial(board, List.of(
                new PlayerSetup("Green", PlayerColor.GREEN),
                new PlayerSetup("Blue", PlayerColor.BLUE)));
        assertEquals(List.of(new Piece(PlayerColor.GREEN, 1)), state.piecesAt(new Position(3, 3)));
        assertEquals(List.of(new Piece(PlayerColor.GREEN, 2)), state.piecesAt(new Position(3, 6)));
        assertEquals(List.of(new Piece(PlayerColor.GREEN, 3)), state.piecesAt(new Position(6, 3)));
        assertEquals(List.of(new Piece(PlayerColor.GREEN, 4)), state.piecesAt(new Position(6, 6)));
        assertEquals(List.of(new Piece(PlayerColor.BLUE, 1)), state.piecesAt(new Position(12, 12)));
        assertEquals(List.of(new Piece(PlayerColor.BLUE, 4)), state.piecesAt(new Position(15, 15)));
    }

    @Test
    void freshGamesHaveIndependentSquaresAndPieceLists() {
        var players = List.of(new PlayerSetup("Green", PlayerColor.GREEN),
                new PlayerSetup("Blue", PlayerColor.BLUE));
        Board secondBoard = Board.createEasy(new Random(42));
        GameState first = GameState.createInitial(board, players);
        GameState second = GameState.createInitial(secondBoard, players);
        Position slot = new Position(3, 3);
        assertNotSame(first.board().squareAt(slot), second.board().squareAt(slot));
        board.removePiece(new Piece(PlayerColor.GREEN, 1), slot);
        assertTrue(first.piecesAt(slot).isEmpty());
        assertEquals(List.of(new Piece(PlayerColor.GREEN, 1)), second.piecesAt(slot));
    }

    @Test
    void doesNotReinitializeAnOccupiedBoard() {
        var players = List.of(new PlayerSetup("Green", PlayerColor.GREEN),
                new PlayerSetup("Blue", PlayerColor.BLUE));
        GameState.createInitial(board, players);
        var before = board.occupancy();
        assertThrows(IllegalArgumentException.class, () -> GameState.createInitial(board, players));
        assertEquals(before, board.occupancy());
    }

    @Test
    void rejectsInvalidPlayerCountsDuplicateColorsAndDuplicateNames() {
        PlayerSetup green = new PlayerSetup("Green", PlayerColor.GREEN);
        PlayerSetup blue = new PlayerSetup("Blue", PlayerColor.BLUE);
        for (List<PlayerSetup> players : List.of(
                List.<PlayerSetup>of(), List.of(green), List.of(green, blue, green, blue, green),
                List.of(green, new PlayerSetup("Other", PlayerColor.GREEN)),
                List.of(green, new PlayerSetup(" green ", PlayerColor.BLUE)))) {
            assertThrows(IllegalArgumentException.class,
                    () -> GameState.createInitial(board, players));
        }
        assertThrows(NullPointerException.class, () -> GameState.createInitial(board, null));
        assertThrows(NullPointerException.class, () -> GameState.createInitial(null, List.of(green, blue)));
        var withNull = new ArrayList<PlayerSetup>();
        withNull.add(green);
        withNull.add(null);
        assertThrows(NullPointerException.class, () -> GameState.createInitial(board, withNull));
    }

    @Test
    void protectsReadAccessAndKeepsSquareDefinitionsWhilePopulatingTheBoard() {
        var before = new ArrayList<List<Object>>();
        for (int row = 0; row < 19; row++) {
            for (int column = 0; column < 19; column++) {
                Square square = board.squareAt(new Position(row, column));
                before.add(List.of(square.position(), square.type(),
                        square.associatedColor(), square.status()));
            }
        }
        var players = new ArrayList<>(List.of(new PlayerSetup("Green", PlayerColor.GREEN),
                new PlayerSetup("Red", PlayerColor.RED)));
        GameState state = GameState.createInitial(board, players);
        players.clear();
        Position slot = new Position(3, 3);
        assertEquals(2, state.players().size());
        assertSame(board, state.board());
        assertThrows(UnsupportedOperationException.class, () -> state.players().clear());
        assertThrows(UnsupportedOperationException.class, () -> state.occupancy().clear());
        assertThrows(UnsupportedOperationException.class, () -> state.occupancy().get(slot).clear());
        assertThrows(UnsupportedOperationException.class, () -> state.piecesAt(slot).clear());
        assertTrue(state.piecesAt(new Position(0, 0)).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> state.piecesAt(new Position(19, 0)));
        assertThrows(NullPointerException.class, () -> state.piecesAt(null));
        assertEquals(state.piecesAt(slot), board.squareAt(slot).pieces());
        assertEquals(state.occupancy(), board.occupancy());
        for (List<Object> definition : before) {
            Square square = board.squareAt((Position) definition.get(0));
            assertEquals(definition, List.of(square.position(), square.type(),
                    square.associatedColor(), square.status()));
        }
    }
}
