package Controller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import Model.Board;
import Model.Difficulty;
import Model.Game;
import Model.GameListener;
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
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.util.Duration;

/**
 * Handles rolling, piece selection and board updates during play.
 * <p>
 * Plays one Easy game on a board screen. The game is changed only through {@link Game#roll()}
 * and {@link Game#play(Piece)}; the screen is updated from the game's {@link GameListener}
 * notifications.
 */
public class GameController implements GameListener {

    // The Model's square storage grid holds the board grid in its middle
    private static final int GRID_OFFSET = (Board.STORAGE_SIZE - BoardView.GRID_SIZE) / 2;
    private static final String[] PLACES = {"1st", "2nd", "3rd", "4th"};

    private final Game game;
    private final BoardView boardView;
    private final DiceView diceView;
    private final Timeline clock;
    // How long the board shows its final message before the game end is reported
    private final PauseTransition resultsDelay = new PauseTransition(Duration.seconds(3));
    private Consumer<Game> onGameOver;
    private boolean gameOverReported;
    private boolean stopped;
    private Player choosingPlayer;
    private List<MoveCandidate> choosingMoves = List.of();
    // The turn being shown: who rolled what, and the message built from its notifications
    private Player roller;
    private TurnRoll roll;
    private List<MoveCandidate> rolledMoves = List.of();
    private final StringBuilder turnText = new StringBuilder();
    // Set while the die animates: the turn already passed to this player
    private Player nextAfterRoll;
    private boolean diceRolling;

    /**
     * Starts a new Easy game on the board screen: {@code names.get(i)} plays
     * {@code colours.get(i)} ("Red", "Green", "Yellow" or "Blue", any case).
     *
     * @throws IllegalArgumentException if the players are not a valid Easy setup
     */
    public GameController(BoardView boardView, List<String> names, List<String> colours) {
        this(boardView, names, colours, Difficulty.EASY);
    }

    /** Starts a game with an explicit difficulty; currently only Easy is supported. */
    public GameController(BoardView boardView, List<String> names, List<String> colours,
                          Difficulty difficulty) {
        this(boardView, new Game(players(names, colours), difficulty));
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
            game.addListener(this);
        }
    }

    /**
     * Stops the live elapsed-time display, e.g. when the board screen is left.
     */
    public void stop() {
        if (stopped) return;
        stopped = true;
        game.removeListener(this);
        clock.stop();
        resultsDelay.stop();
        diceView.stopAnimation();
        diceView.setRollEnabled(false);
        diceView.setOnRoll(null);
        boardView.clearSelectablePieces();
        boardView.setOnPieceChosen(null);
        boardView.setOnMainMenu(null);
        choosingPlayer = null;
        choosingMoves = List.of();
    }

    /** Leaving a running game abandons it; only completed games reach ResultsController. */
    public void setOnMainMenu(Runnable action) {
        Objects.requireNonNull(action, "action");
        if (stopped || game.isFinished()) return;
        boardView.setOnMainMenu(() -> {
            if (stopped || game.isFinished() || !boardView.confirmLeaveGame()) return;
            stop();
            action.run();
        });
    }

    /**
     * Sets what happens once the game has ended: it receives the finished game, after the board
     * has shown its final message for a moment. Runs once per game.
     */
    public void setOnGameOver(Consumer<Game> action) {
        onGameOver = action;
        if (game.isFinished() && !gameOverReported) {
            reportGameOver();
        }
    }

    private void reportGameOver() {
        if (stopped || onGameOver == null || gameOverReported) {
            return;
        }
        gameOverReported = true;
        resultsDelay.setOnFinished(event -> onGameOver.accept(game));
        resultsDelay.playFromStart();
    }

    static List<Player> players(List<String> names, List<String> colours) {
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
     * One press of Roll: the game rolls for the active player. Its notifications start the dice
     * animation; the outcome is shown once the animation has finished.
     */
    private void roll() {
        if (stopped || game.isFinished() || game.turnManager().phase() != TurnPhase.WAITING_FOR_ROLL) {
            return;
        }
        boardView.setMainMenuEnabled(false);
        game.roll();
    }

    @Override
    public void rolled(Player player, TurnRoll roll) {
        if (stopped) return;
        roller = player;
        this.roll = roll;
        rolledMoves = game.legalMoves();
        nextAfterRoll = null;
        turnText.setLength(0);
        turnText.append(player.name()).append(" rolled ").append(roll.value()).append('.');
        diceRolling = true;
        diceView.showResult(roll.value(), this::afterRoll);
    }

    private void afterRoll() {
        diceRolling = false;
        if (stopped) return;
        boardView.setMainMenuEnabled(!game.isFinished());
        if (roll.cancelled()) {
            turnText.append(" That is a third 6 in a row, so it is cancelled.");
            showNextTurn(nextAfterRoll);
        } else if (rolledMoves.isEmpty()) {
            turnText.append(" No piece can move.");
            showNextTurn(nextAfterRoll);
        } else if (rolledMoves.size() == 1) {
            game.play(rolledMoves.getFirst().piece());
        } else {
            choosingPlayer = roller;
            choosingMoves = rolledMoves;
            List<Integer> numbers = rolledMoves.stream().map(move -> move.piece().number()).toList();
            diceView.setRollEnabled(false);
            boardView.setSelectablePieces(colourName(roller.color()), numbers);
            boardView.showMessage(turnText + " Choose a piece to move: " + joinNumbers(numbers) + ".");
        }
    }

    private void pieceChosen(String colour, int number) {
        if (stopped || choosingPlayer == null || !colourName(choosingPlayer.color()).equalsIgnoreCase(colour)) {
            return;
        }
        MoveCandidate chosen = choosingMoves.stream()
                .filter(move -> move.piece().number() == number)
                .findFirst().orElse(null);
        if (chosen == null) {
            return;
        }
        choosingPlayer = null;
        choosingMoves = List.of();
        game.play(chosen.piece());
    }

    @Override
    public void pieceMoved(Player player, MoveCandidate move) {
        if (stopped) return;
        boardView.clearSelectablePieces();
        showPiece(move.piece(), move.destination());
        turnText.append(' ').append(player.name()).append(" moved piece ").append(move.piece().number()).append('.');
    }

    @Override
    public void pieceCaptured(Player owner, Piece piece, Position castleSlot) {
        if (stopped) return;
        showPiece(piece, castleSlot);
        turnText.append(" It captured ").append(owner.name()).append("'s piece ")
                .append(piece.number()).append(", which goes back to the Castle.");
    }

    @Override
    public void playerFinished(Player player, int place) {
        if (stopped) return;
        turnText.append(' ').append(player.name()).append(" has all four pieces home: ")
                .append(PLACES[place - 1]).append(" place!");
    }

    @Override
    public void turnPassed(Player next) {
        if (stopped) return;
        if (diceRolling) {
            // Shown once the die animation has finished
            nextAfterRoll = next;
        } else {
            showNextTurn(next);
        }
    }

    @Override
    public void gameOver(List<Player> ranking) {
        if (stopped) return;
        showGameOver(turnText.toString());
    }

    /**
     * After the game has resolved the turn: say who plays next (the same player after a 6),
     * update the turn display and make Roll available again.
     */
    private void showNextTurn(Player next) {
        String nextText = next == roller && roll.value() == 6
                ? " Rolled a 6: " + roller.name() + " rolls again."
                : " Now it's " + next.name() + "'s turn.";
        boardView.showTurn(next.name(), colourName(next.color()));
        boardView.showMessage(turnText + nextText);
        diceView.setRollEnabled(true);
    }

    private void showGameOver(String text) {
        boardView.setMainMenuEnabled(false);
        diceView.setRollEnabled(false);
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
                + " wins! Final order: " + order + "."
                + (onGameOver != null ? " The results open in a moment." : ""));
        reportGameOver();
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
                showPiece(piece, positions.get(piece));
            }
        }
    }

    private void showPiece(Piece piece, Position position) {
        boardView.showPiece(colourName(piece.color()), piece.number(),
                position.row() - GRID_OFFSET, position.column() - GRID_OFFSET);
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

    static String colourName(PlayerColor color) {
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
