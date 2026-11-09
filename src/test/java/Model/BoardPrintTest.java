package Model;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Prints a complete Easy board as a matrix for manual inspection. */
class BoardPrintTest {
    private static final int CELL_WIDTH = 10;

    @Test
    void printsEverySquareOfAnEasyBoard() {
        // Fixed seed keeps the protected squares reproducible between runs.
        Board board = Board.createEasy(new Random(42));
        StringBuilder output = new StringBuilder();

        output.append("Easy board - each cell: TYPE / COLOR / STATUS\n");
        output.append("Coordinates: (row, column), starting at 0.\n\n");

        // Column coordinates, aligned with the cells below.
        output.append("     |");
        for (int column = 0; column < Board.STORAGE_SIZE; column++) {
            appendCell(output, Integer.toString(column));
        }
        output.append('\n');
        appendBorder(output);

        int printed = 0;
        for (int row = 0; row < Board.STORAGE_SIZE; row++) {
            // Each logical board row occupies three console lines.
            for (int line = 0; line < 3; line++) {
                if (line == 1) {
                    output.append(String.format("%4d |", row));
                } else {
                    output.append("     |");
                }

                for (int column = 0; column < Board.STORAGE_SIZE; column++) {
                    Position position = new Position(row, column);
                    Square square = board.squareAt(position);
                    assertNotNull(square);
                    assertEquals(position, square.position());

                    String value = switch (line) {
                        case 0 -> square.type().name();
                        case 1 -> square.associatedColor().name();
                        default -> square.status().name();
                    };
                    appendCell(output, value);
                    if (line == 0) printed++;
                }
                output.append('\n');
            }
            appendBorder(output);
        }

        assertEquals(Board.STORAGE_SIZE * Board.STORAGE_SIZE, printed);
        output.append("Total squares: ").append(printed).append('\n');
        System.out.print(output);
    }

    private static void appendCell(StringBuilder output, String value) {
        output.append(String.format(" %-" + CELL_WIDTH + "s |", value));
    }

    private static void appendBorder(StringBuilder output) {
        output.append("     +");
        output.append(("-".repeat(CELL_WIDTH + 2) + "+").repeat(Board.STORAGE_SIZE));
        output.append('\n');
    }
}
