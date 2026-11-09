package Model;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** Prints initial Castle placement for games with two, three and four players. */
class GameStatePrintTest {
    private static final List<PlayerColor> COLOR_ORDER = List.of(
            PlayerColor.GREEN, PlayerColor.YELLOW, PlayerColor.RED, PlayerColor.BLUE);

    private static final Map<PlayerColor, List<Position>> CASTLE_SLOTS = Map.of(
            PlayerColor.GREEN, List.of(new Position(3, 3), new Position(3, 6),
                    new Position(6, 3), new Position(6, 6)),
            PlayerColor.YELLOW, List.of(new Position(3, 12), new Position(3, 15),
                    new Position(6, 12), new Position(6, 15)),
            PlayerColor.RED, List.of(new Position(12, 3), new Position(12, 6),
                    new Position(15, 3), new Position(15, 6)),
            PlayerColor.BLUE, List.of(new Position(12, 12), new Position(12, 15),
                    new Position(15, 12), new Position(15, 15)));

    @Test
    void printsInitialPlacementForTwoPlayers() {
        printAndCheck(List.of(PlayerColor.GREEN, PlayerColor.BLUE));
    }

    @Test
    void printsInitialPlacementForThreePlayers() {
        printAndCheck(List.of(PlayerColor.GREEN, PlayerColor.YELLOW, PlayerColor.RED));
    }

    @Test
    void printsInitialPlacementForFourPlayers() {
        printAndCheck(COLOR_ORDER);
    }

    private void printAndCheck(List<PlayerColor> participants) {
        // Every scenario creates its own board and piece lists.
        Board board = Board.createEasy(new Random(42));
        List<PlayerSetup> players = participants.stream()
                .map(color -> new PlayerSetup(color.name(), color))
                .toList();
        GameState state = GameState.createInitial(board, players);

        StringBuilder output = new StringBuilder();
        output.append("\n=== INITIAL EASY STATE: ").append(players.size())
                .append(" PLAYERS ===\n");
        output.append("Participants: ").append(participants).append('\n');
        output.append("Coordinates: (row, column)\n");
        output.append(String.format("%-8s | %-5s | %-11s | %s%n",
                "Castle", "Slot", "Position", "Pieces"));
        output.append("---------+-------+-------------+--------------------\n");

        int total = 0;
        for (PlayerColor color : COLOR_ORDER) {
            List<Position> slots = CASTLE_SLOTS.get(color);
            for (int i = 0; i < slots.size(); i++) {
                Position position = slots.get(i);
                Square square = board.squareAt(position);
                List<Piece> occupants = state.piecesAt(position);
                assertEquals(SquareType.CASTLE, square.type());
                assertEquals(SquareStatus.ACTIVE, square.status());
                assertEquals(SquareColor.fromPlayerColor(color), square.associatedColor());

                List<Piece> expected = participants.contains(color)
                        ? List.of(new Piece(color, i + 1)) : List.of();
                assertEquals(expected, occupants, position.toString());
                assertEquals(occupants, square.pieces());
                total += occupants.size();

                String pieces = occupants.isEmpty() ? "EMPTY (not participating)"
                        : occupants.stream()
                                .map(piece -> piece.color() + " #" + String.format("%02d", piece.number()))
                                .collect(Collectors.joining(", "));
                output.append(String.format("%-8s | %-5d | %-11s | %s%n",
                        color, i + 1, "(" + position.row() + "," + position.column() + ")", pieces));
            }
        }

        // Ensure no additional pieces were placed outside the expected Castle slots.
        int boardTotal = 0;
        for (int row = 0; row < Board.STORAGE_SIZE; row++) {
            for (int column = 0; column < Board.STORAGE_SIZE; column++) {
                Square square = board.squareAt(new Position(row, column));
                boardTotal += square.pieces().size();
                if (square.type() != SquareType.CASTLE || square.status() != SquareStatus.ACTIVE) {
                    assertTrue(square.pieces().isEmpty(), square.position().toString());
                }
            }
        }
        assertEquals(players.size() * 4, total);
        assertEquals(total, boardTotal);
        output.append("Total pieces: ").append(total).append(" (expected ")
                .append(players.size() * 4).append(")\n");
        System.out.print(output);
    }
}
