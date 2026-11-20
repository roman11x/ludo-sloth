package Model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

/**
 * Per-game board with fixed square definitions and changing piece occupancy.
 * Every coordinate has a Square with a separate activity status.
 * Coordinates use the global 19x19 matrix; player routes are maintained separately.
 */
public final class Board {
    public static final int STORAGE_SIZE = 19;

    private final Cell[][] cells = new Cell[STORAGE_SIZE][STORAGE_SIZE];

    /** Only Board can access or mutate a cell's backing occupancy list. */
    private static final class Cell {
        private final List<Piece> pieces = new ArrayList<>();
        private final Square square;

        private Cell(Square definition) {
            square = new Square(definition.position(), definition.type(),
                    definition.associatedColor(), definition.status(),
                    definition.questionDifficulty(), pieces);
        }
    }

    /** Copies layout definitions into fresh cells with board-owned occupancy. */
    Board(Square[][] definitions) {
        for (int row = 0; row < STORAGE_SIZE; row++) {
            for (int column = 0; column < STORAGE_SIZE; column++) {
                cells[row][column] = new Cell(definitions[row][column]);
            }
        }
    }

    /** Creates Easy geometry and randomly places four protected squares. */
    public static Board createEasy() {
        return new EasyLayout().build();
    }

    /** Allows reproducible layouts in tests by supplying a seeded Random. */
    public static Board createEasy(Random random) {
        return new EasyLayout().build(random);
    }

    /** Whether the coordinate is inside the storage matrix, active or not. */
    public boolean isWithinBounds(Position position) {
        Objects.requireNonNull(position, "position");
        return position.row() >= 0 && position.row() < STORAGE_SIZE
                && position.column() >= 0 && position.column() < STORAGE_SIZE;
    }

    /** Returns any cell, including inactive Castle and empty cells. Only out-of-bounds cells are rejected. */
    public Square squareAt(Position position) {
        return cellAt(position).square;
    }

    public boolean isActive(Position position) {
        return isWithinBounds(position)
                && squareAt(position).status() == SquareStatus.ACTIVE;
    }

    public List<Piece> piecesAt(Position position) {
        return squareAt(position).pieces();
    }

    /** Derived snapshot, not a second stored copy of piece locations. */
    public Map<Position, List<Piece>> occupancy() {
        Map<Position, List<Piece>> snapshot = new HashMap<>();
        for (Cell[] row : cells) {
            for (Cell cell : row) {
                Square square = cell.square;
                List<Piece> pieces = square.pieces();
                if (!pieces.isEmpty()) snapshot.put(square.position(), pieces);
            }
        }
        return Map.copyOf(snapshot);
    }

    public boolean hasPieces() {
        for (Cell[] row : cells) {
            for (Cell cell : row) {
                Square square = cell.square;
                if (!square.pieces().isEmpty()) return true;
            }
        }
        return false;
    }

    /**
     * Places an unplaced piece on an active square without duplicating it.
     * Turn, capture, blocking and movement legality are checked separately.
     */
    public void placePiece(Piece piece, Position position) {
        Objects.requireNonNull(piece, "piece");
        Square destination = squareAt(position);
        if (destination.status() != SquareStatus.ACTIVE) {
            throw new IllegalArgumentException("Cannot place a piece on an inactive square");
        }
        for (Cell[] row : cells) {
            for (Cell cell : row) {
                Square square = cell.square;
                if (square.pieces().contains(piece)) {
                    throw new IllegalArgumentException("Piece is already on this board: " + piece);
                }
            }
        }
        addPiece(cellAt(position), piece);
    }

    /** Removes a piece from the stated square; rejects incorrect source locations. */
    public void removePiece(Piece piece, Position position) {
        Objects.requireNonNull(piece, "piece");
        if (!cellAt(position).pieces.remove(piece)) {
            throw new IllegalArgumentException("Piece is not at " + position);
        }
    }

    /**
     * Relocates occupancy after the caller has validated movement rules.
     * Destination validation happens before removing the source piece, so a
     * rejected relocation leaves occupancy unchanged. No capture is performed.
     */
    public void movePiece(Piece piece, Position from, Position to) {
        Objects.requireNonNull(piece, "piece");
        Square source = squareAt(from);
        Square destination = squareAt(to);
        if (!source.pieces().contains(piece)) {
            throw new IllegalArgumentException("Piece is not at " + from);
        }
        if (from.equals(to) || destination.status() != SquareStatus.ACTIVE) {
            throw new IllegalArgumentException("Destination must be a different active square");
        }
        addPiece(cellAt(to), piece);
        cellAt(from).pieces.remove(piece);
    }

    public Position startFor(PlayerColor color) {
        return findPosition(SquareType.START, color);
    }

    public Position arrowFor(PlayerColor color) {
        return findPosition(SquareType.ARROW, color);
    }

    private Position findPosition(SquareType type, PlayerColor color) {
        SquareColor associatedColor = SquareColor.fromPlayerColor(
                Objects.requireNonNull(color, "color"));
        for (Cell[] row : cells) {
            for (Cell cell : row) {
                Square square = cell.square;
                if (square.type() == type && square.associatedColor() == associatedColor) {
                    return square.position();
                }
            }
        }
        throw new IllegalStateException("Missing " + type + " for " + color);
    }

    private Cell cellAt(Position position) {
        if (!isWithinBounds(position)) {
            throw new IllegalArgumentException("Position is outside the board: " + position);
        }
        return cells[position.row()][position.column()];
    }

    private void addPiece(Cell cell, Piece piece) {
        if (cell.pieces.contains(piece)) {
            throw new IllegalArgumentException("Piece is already on this square");
        }
        if (cell.pieces.stream().filter(p -> p.color() == piece.color()).count() >= 4) {
            throw new IllegalArgumentException("At most four pieces of one color per square");
        }
        cell.pieces.add(piece);
    }

}
