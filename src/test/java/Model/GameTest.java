package Model;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class GameTest {
    private static final Map<PlayerColor, List<Position>> CASTLES = Map.of(
            PlayerColor.GREEN, List.of(new Position(3, 3), new Position(3, 6),
                    new Position(6, 3), new Position(6, 6)),
            PlayerColor.YELLOW, List.of(new Position(3, 12), new Position(3, 15),
                    new Position(6, 12), new Position(6, 15)),
            PlayerColor.BLUE, List.of(new Position(12, 12), new Position(12, 15),
                    new Position(15, 12), new Position(15, 15)),
            PlayerColor.RED, List.of(new Position(12, 3), new Position(12, 6),
                    new Position(15, 3), new Position(15, 6)));

    private Game create(List<Player> players) {
        return new Game(players, () -> 2, new Random(42), new Random(7));
    }

    @Test
    void everyTwoThreeAndFourColorCombinationStartsWithExactlyThePlayersPiecesInCastle() {
        for (int mask = 0; mask < 16; mask++) {
            if (Integer.bitCount(mask) < 2) continue;
            List<Player> participants = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                if ((mask & (1 << i)) != 0) {
                    PlayerColor color = PlayerColor.values()[i];
                    participants.add(new Player(color.name(), color));
                }
            }
            Game game = create(participants);
            assertEquals(participants.size() * 4, game.board().occupancy().size());
            for (Player player : participants) {
                for (int i = 0; i < 4; i++) {
                    List<Piece> occupants = game.board().piecesAt(CASTLES.get(player.color()).get(i));
                    assertEquals(1, occupants.size());
                    assertSame(player.pieces().get(i), occupants.getFirst());
                }
            }
            for (int row = 0; row < Board.STORAGE_SIZE; row++) {
                for (int col = 0; col < Board.STORAGE_SIZE; col++) {
                    Square square = game.board().squareAt(new Position(row, col));
                    if (square.type() != SquareType.CASTLE || square.status() != SquareStatus.ACTIVE) {
                        assertTrue(square.pieces().isEmpty());
                    }
                }
            }
            for (PlayerColor color : PlayerColor.values()) {
                if (participants.stream().noneMatch(player -> player.color() == color)) {
                    for (Position slot : CASTLES.get(color)) assertTrue(game.board().piecesAt(slot).isEmpty());
                }
            }
        }
    }

    @Test
    void gameAndTurnManagerShareClockwiseParticipantsAndStartWaitingForRoll() {
        Player red = new Player("Red", PlayerColor.RED);
        Player blue = new Player("Blue", PlayerColor.BLUE);
        Player green = new Player("Green", PlayerColor.GREEN);
        Game game = create(List.of(red, blue, green));
        assertEquals(List.of(green, blue, red), game.players());
        assertSame(game.players(), game.turnManager().players());
        assertSame(game.players().get(new Random(7).nextInt(3)), game.turnManager().currentPlayer());
        assertEquals(TurnPhase.WAITING_FOR_ROLL, game.turnManager().phase());
    }

    @Test
    void createsAllFourFixedRoutesAndPreservesImmutableCollections() {
        List<Player> input = new ArrayList<>(List.of(
                new Player("Green", PlayerColor.GREEN), new Player("Red", PlayerColor.RED)));
        Game game = create(input);
        input.clear();
        assertEquals(2, game.players().size());
        assertEquals(4, game.routes().size());
        for (PlayerColor color : PlayerColor.values()) {
            PlayerRoute route = game.routes().get(color);
            assertEquals(color, route.color());
            assertEquals(57, route.size());
            assertEquals(game.board().startFor(color), route.start());
            assertEquals(SquareType.FINISHING, game.board().squareAt(route.finish()).type());
        }
        assertThrows(UnsupportedOperationException.class, () -> game.players().clear());
        assertThrows(UnsupportedOperationException.class, () -> game.routes().clear());
    }

    @Test
    void eachGameHasItsOwnBoardOccupancyAndTurnState() {
        List<Player> participants = List.of(new Player("Green", PlayerColor.GREEN), new Player("Red", PlayerColor.RED));
        Game first = create(participants);
        Game second = create(participants);
        assertNotSame(first.board(), second.board());
        assertNotSame(first.turnManager(), second.turnManager());
        Piece piece = participants.getFirst().pieces().getFirst();
        Position slot = CASTLES.get(PlayerColor.GREEN).getFirst();
        first.board().movePiece(piece, slot, first.board().startFor(PlayerColor.GREEN));
        first.turnManager().roll(first.turnManager().currentPlayer().color());
        assertTrue(first.board().piecesAt(slot).isEmpty());
        assertEquals(List.of(piece), second.board().piecesAt(slot));
        assertEquals(TurnPhase.WAITING_FOR_ROLL, second.turnManager().phase());
    }

    @Test
    void rejectsInvalidParticipantsBeforeCreatingAGame() {
        Player green = new Player("Green", PlayerColor.GREEN);
        assertThrows(IllegalArgumentException.class, () -> create(List.of()));
        assertThrows(IllegalArgumentException.class, () -> create(List.of(green)));
        assertThrows(IllegalArgumentException.class, () -> create(List.of(green, new Player("Other", PlayerColor.GREEN))));
        assertThrows(IllegalArgumentException.class, () -> create(List.of(green, new Player("GREEN", PlayerColor.RED))));
        assertThrows(IllegalArgumentException.class, () -> create(List.of(green,
                new Player("Red", PlayerColor.RED), new Player("Yellow", PlayerColor.YELLOW),
                new Player("Blue", PlayerColor.BLUE), new Player("Extra", PlayerColor.RED))));
        assertThrows(NullPointerException.class, () -> create(null));
    }

    @Test
    void defaultConstructionCreatesAReadyEasyGame() {
        Game game = new Game(List.of(new Player("Green", PlayerColor.GREEN), new Player("Red", PlayerColor.RED)));
        assertEquals(8, game.board().occupancy().size());
        assertTrue(game.players().contains(game.turnManager().currentPlayer()));
        assertEquals(TurnPhase.WAITING_FOR_ROLL, game.turnManager().phase());
    }
}
