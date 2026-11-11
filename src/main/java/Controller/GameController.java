package Controller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import Model.Board;
import Model.Game;
import Model.MoveCandidate;
import Model.Piece;
import Model.Player;
import Model.PlayerColor;
import Model.Position;
import Model.SquareType;
import Model.TurnPhase;
import Model.TurnRoll;
import View.BoardView;
import View.DiceView;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.util.Duration;

/**
 * Handles rolling, piece selection and board updates during play.
 * <p>
 * Plays one Easy game on a board screen. The game is changed only through {@link Game#roll()}
 * and {@link Game#play(Piece)}; everything else is read to update the screen.
 */
public class GameController {

    // The Model's square storage grid holds the board grid in its middle
    private static final int GRID_OFFSET = (Board.STORAGE_SIZE - BoardView.GRID_SIZE) / 2;
    private static final String[] PLACES = {"1st", "2nd", "3rd", "4th"};

    private final Game game;
    private final BoardView boardView;
    private final DiceView diceView;
    private final Timeline clock;
    private Player choosingPlayer;
    private TurnRoll choosingRoll;
    private List<MoveCandidate> choosingMoves = List.of();
    private String choosingText = "";

    /**
     * Starts a new Easy game on the board screen: {@code names.get(i)} plays
     * {@code colours.get(i)} ("Red", "Green", "Yellow" or "Blue", any case).
     *
     * @throws IllegalArgumentException if the players are not a valid Easy setup
     */
    public GameController(BoardView boardView, List<String> names, List<String> colours) {
        this(boardView, new Game(players(names, colours)));
    }

    /**
     * Shows the given game on the board screen and lets the players play it there.
     */
    public GameController(BoardView boardView, Game game) {
        this.boardView = Objects.requireNonNull(boardView, "boardView");
        this.game = Objects.requireNonNull(game, "game");
        this.diceView = boardView.getDiceView();
        diceView.setOnRoll(this::roll);
        boardView.setOnPieceChosen(this::pieceChosen);

        List<String> colours = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (Player player : game.players()) {
            colours.add(colourName(player.color()));
            names.add(player.name());
        }
        boardView.showPlayers(colours, names);
        boardView.showProtectedSquares(protectedSquares());
        showPieces();

        clock = new Timeline(new KeyFrame(Duration.millis(250), event -> showElapsedTime()));
        clock.setCycleCount(Animation.INDEFINITE);
        showElapsedTime();
        if (game.isFinished()) {
            showGameOver("");
        } else {
            showTurn();
            boardView.showMessage(currentPlayer().name() + ", roll the die to start your turn.");
            diceView.setRollEnabled(true);
            clock.play();
        }
    }

    /**
     * Stops the live elapsed-time display, e.g. when the board screen is left.
     */
    public void stop() {
        clock.stop();
    }

    private static List<Player> players(List<String> names, List<String> colours) {
        Objects.requireNonNull(names, "names");
        Objects.requireNonNull(colours, "colours");
        if (names.size() != colours.size()) {
            throw new IllegalArgumentException("Each player needs exactly one colour");
        }
        List<Player> players = new ArrayList<>();
        for (int i = 0; i < names.size(); i++) {
            PlayerColor color = PlayerColor.valueOf(colours.get(i).trim().toUpperCase(Locale.ROOT));
            players.add(new Player(names.get(i), color));
        }
        return players;
    }

    /**
     * One press of Roll: the game rolls for the active player; the outcome is shown once the
     * dice area has finished its animation.
     */
    private void roll() {
        if (game.isFinished() || game.turnManager().phase() != TurnPhase.WAITING_FOR_ROLL) {
            return;
        }
        Player roller = currentPlayer();
        TurnRoll roll = game.roll();
        List<MoveCandidate> moves = game.legalMoves();
        diceView.showResult(roll.value(), () -> afterRoll(roller, roll, moves));
    }

    private void afterRoll(Player roller, TurnRoll roll, List<MoveCandidate> moves) {
        String rolled = roller.name() + " rolled " + roll.value() + ".";
        if (roll.cancelled()) {
            endTurn(rolled + " That is a third 6 in a row, so it is cancelled.", roller, roll);
        } else if (moves.isEmpty()) {
            endTurn(rolled + " No piece can move.", roller, roll);
        } else if (moves.size() == 1) {
            play(roller, roll, moves.getFirst().piece(), rolled);
        } else {
            choosingPlayer = roller;
            choosingRoll = roll;
            choosingMoves = moves;
            choosingText = rolled;
            List<Integer> numbers = moves.stream().map(move -> move.piece().number()).toList();
            diceView.setRollEnabled(false);
            boardView.setSelectablePieces(colourName(roller.color()), numbers);
            boardView.showMessage(rolled + " Choose a piece to move: " + joinNumbers(numbers) + ".");
        }
    }

    private void pieceChosen(String colour, int number) {
        if (choosingPlayer == null || !colourName(choosingPlayer.color()).equalsIgnoreCase(colour)) {
            return;
        }
        MoveCandidate chosen = choosingMoves.stream()
                .filter(move -> move.piece().number() == number)
                .findFirst().orElse(null);
        if (chosen == null) {
            return;
        }
        Player roller = choosingPlayer;
        TurnRoll roll = choosingRoll;
        String rolled = choosingText;
        choosingPlayer = null;
        choosingRoll = null;
        choosingMoves = List.of();
        choosingText = "";
        play(roller, roll, chosen.piece(), rolled);
    }

    private void play(Player roller, TurnRoll roll, Piece piece, String rolled) {
        Map<Piece, Position> before = piecePositions();
        boolean finishedBefore = game.hasFinished(roller);
        game.play(piece);
        Map<Piece, Position> after = piecePositions();
        boardView.clearSelectablePieces();
        showPieces();

        StringBuilder text = new StringBuilder(rolled)
                .append(' ').append(roller.name()).append(" moved piece ").append(piece.number()).append('.');
        for (Player other : game.players()) {
            if (other == roller) {
                continue;
            }
            for (Piece otherPiece : other.pieces()) {
                if (!Objects.equals(before.get(otherPiece), after.get(otherPiece))) {
                    text.append(" It captured ").append(other.name()).append("'s piece ")
                            .append(otherPiece.number()).append(", which goes back to the Castle.");
                }
            }
        }
        if (!finishedBefore && game.hasFinished(roller)) {
            int place = game.ranking().indexOf(roller);
            text.append(' ').append(roller.name()).append(" has all four pieces home: ")
                    .append(PLACES[place]).append(" place!");
        }
        if (game.isFinished()) {
            showGameOver(text.toString());
            return;
        }
        endTurn(text.toString(), roller, roll);
    }

    /**
     * After the game has resolved the turn: say who plays next (the same player after a 6),
     * update the turn display and make Roll available again.
     */
    private void endTurn(String text, Player roller, TurnRoll roll) {
        Player next = currentPlayer();
        String nextText = next == roller && roll.value() == 6
                ? " Rolled a 6: " + roller.name() + " rolls again."
                : " Now it's " + next.name() + "'s turn.";
        showTurn();
        boardView.showMessage(text + nextText);
        diceView.setRollEnabled(true);
    }

    private void showGameOver(String text) {
        clock.stop();
        showElapsedTime();
        List<Player> ranking = game.ranking();
        Player winner = ranking.getFirst();
        boardView.showWinner(winner.name(), colourName(winner.color()));
        StringBuilder order = new StringBuilder();
        for (int i = 0; i < ranking.size(); i++) {
            order.append(i == 0 ? "" : ", ").append(PLACES[i]).append(' ').append(ranking.get(i).name());
        }
        boardView.showMessage((text.isEmpty() ? "" : text + " ") + "Game over: " + winner.name()
                + " wins! Final order: " + order + ".");
    }

    private void showTurn() {
        Player player = currentPlayer();
        boardView.showTurn(player.name(), colourName(player.color()));
    }

    private void showElapsedTime() {
        boardView.showElapsedTime(game.duration().toSeconds());
    }

    private void showPieces() {
        Map<Piece, Position> positions = piecePositions();
        for (Player player : game.players()) {
            for (Piece piece : player.pieces()) {
                Position position = positions.get(piece);
                boardView.showPiece(colourName(piece.color()), piece.number(),
                        position.row() - GRID_OFFSET, position.column() - GRID_OFFSET);
            }
        }
    }

    private Map<Piece, Position> piecePositions() {
        Map<Piece, Position> positions = new HashMap<>();
        for (Map.Entry<Position, List<Piece>> entry : game.board().occupancy().entrySet()) {
            for (Piece piece : entry.getValue()) {
                positions.put(piece, entry.getKey());
            }
        }
        return positions;
    }

    private List<int[]> protectedSquares() {
        List<int[]> squares = new ArrayList<>();
        Board board = game.board();
        for (int row = 0; row < Board.STORAGE_SIZE; row++) {
            for (int column = 0; column < Board.STORAGE_SIZE; column++) {
                if (board.squareAt(new Position(row, column)).type() == SquareType.PROTECTED) {
                    squares.add(new int[]{row - GRID_OFFSET, column - GRID_OFFSET});
                }
            }
        }
        return squares;
    }

    private Player currentPlayer() {
        return game.turnManager().currentPlayer();
    }

    private static String colourName(PlayerColor color) {
        String name = color.name();
        return name.charAt(0) + name.substring(1).toLowerCase(Locale.ROOT);
    }

    private static String joinNumbers(List<Integer> numbers) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < numbers.size(); i++) {
            if (i > 0) {
                text.append(i == numbers.size() - 1 ? " or " : ", ");
            }
            text.append(numbers.get(i));
        }
        return text.toString();
    }
}
