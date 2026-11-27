package Model;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * Keeps the turn state of one game for 2-4 players: whose turn it is, which phase
 * the turn is in, the pending roll and the run of consecutive sixes.
 * <p>
 * The first player is chosen uniformly at random; play then follows the board
 * colors clockwise (green, yellow, blue, red), skipping colors that are not in the
 * game and players who have already finished. Every turn action checks both that
 * it comes from the active player and that the turn is in the right phase.
 * <p>
 * Rolling is delegated to a {@link DiceStrategy}, so the same turn rules work for
 * every difficulty: the strategy decides which dice are rolled and the movement
 * distance, while this class applies the rules that depend on the Standard die
 * alone (FR-016: a six grants another roll, the third consecutive six is cancelled).
 * <p>
 * This class does not move pieces or change board occupancy; {@link Game} does that.
 *
 * @see DiceStrategy
 * @see TurnPhase
 */
public final class TurnManager {
    // Board corners: top-left, top-right, bottom-right, bottom-left.
    private static final List<PlayerColor> CLOCKWISE_COLORS = List.of(
            PlayerColor.GREEN, PlayerColor.YELLOW, PlayerColor.BLUE, PlayerColor.RED);
    private final List<Player> players;
    private final DiceStrategy diceStrategy;
    private final EnumSet<PlayerColor> finishedPlayers = EnumSet.noneOf(PlayerColor.class);
    private int activeIndex;
    private TurnPhase phase = TurnPhase.WAITING_FOR_ROLL;
    private int lastRoll;
    private int consecutiveSixes;

    /**
     * Creates the turn state for a game, rolling with the given dice strategy.
     * The players are reordered into clockwise board order, and the first player
     * is chosen with {@code random}.
     *
     * @param players      the game's participants, 2-4 players with unique colors;
     *                     the list is copied
     * @param diceStrategy the strategy used for every roll in this game
     * @param random       the source used once to choose the first player
     * @throws IllegalArgumentException if there are fewer than 2 or more than 4
     *                                  players, or two players share a color
     * @throws NullPointerException     if any argument is {@code null}
     */
    public TurnManager(List<Player> players, DiceStrategy diceStrategy, Random random) {
        List<Player> participants = List.copyOf(players);
        if (participants.size() < 2 || participants.size() > 4) {
            throw new IllegalArgumentException("Turn order requires 2-4 players");
        }
        if (participants.stream().map(Player::color).distinct().count() != participants.size()) {
            throw new IllegalArgumentException("Players must have unique colors");
        }
        this.players = participants.stream()
                .sorted(Comparator.comparingInt(player -> CLOCKWISE_COLORS.indexOf(player.color())))
                .toList();
        this.diceStrategy = Objects.requireNonNull(diceStrategy, "diceStrategy");
        this.activeIndex = Objects.requireNonNull(random, "random").nextInt(this.players.size());
    }

    /**
     * Creates the turn state for an Easy game: rolls use a real Standard die and
     * the first player is chosen at random.
     *
     * @param players the game's participants, 2-4 players with unique colors
     * @throws IllegalArgumentException if the players are invalid
     * @throws NullPointerException     if {@code players} is {@code null}
     */
    public TurnManager(List<Player> players) {
        this(players, new EasyStrategy(), new Random());
    }

    /**
     * Creates the turn state for an Easy game that rolls the given die. Kept from
     * Iteration 1 so tests can supply a controlled die and first-player source;
     * the die is wrapped in an {@link EasyStrategy}.
     *
     * @param players     the game's participants, 2-4 players with unique colors
     * @param standardDie the die used as the Standard die
     * @param random      the source used once to choose the first player
     * @throws IllegalArgumentException if the players are invalid
     * @throws NullPointerException     if any argument is {@code null}
     */
    public TurnManager(List<Player> players, Die standardDie, Random random) {
        this(players, new EasyStrategy(standardDie), random);
    }

    /**
     * Returns the participants in clockwise turn order.
     *
     * @return an unmodifiable list of the players
     */
    public List<Player> players() {
        return players;
    }

    /**
     * Returns the player whose turn it is.
     *
     * @return the active player
     * @throws IllegalStateException if the game is over, since no player is active then
     */
    public Player currentPlayer() {
        if (phase == TurnPhase.GAME_OVER) {
            throw new IllegalStateException("The game is over; there is no active player");
        }
        return players.get(activeIndex);
    }

    /**
     * Returns the phase of the current turn.
     *
     * @return the current phase
     */
    public TurnPhase phase() {
        return phase;
    }

    /**
     * Returns the Standard die value of the roll that is waiting to be used for a
     * move. Reading it never changes the turn state.
     *
     * @return the pending Standard die value, 1-6
     * @throws IllegalStateException if no roll is waiting for a move
     */
    int pendingRollValue() {
        requirePhase(TurnPhase.WAITING_FOR_MOVE);
        return lastRoll;
    }

    /**
     * Tells whether the player with this color may act now.
     *
     * @param player the color to check
     * @return {@code true} if the game is not over and it is this color's turn
     * @throws NullPointerException if {@code player} is {@code null}
     */
    public boolean isActive(PlayerColor player) {
        Objects.requireNonNull(player, "player");
        return phase != TurnPhase.GAME_OVER && currentPlayer().color() == player;
    }

    /**
     * Checks that the player with this color is the active player.
     *
     * @param player the color of the player trying to act
     * @throws IllegalStateException if it is not this player's turn, or the game is over
     * @throws NullPointerException  if {@code player} is {@code null}
     */
    public void requireActivePlayer(PlayerColor player) {
        if (!isActive(player)) {
            throw new IllegalStateException("Only the active player may perform turn actions");
        }
    }

    /**
     * Rolls once for the active player using this game's {@link DiceStrategy}.
     * <p>
     * The sixes rules look at the Standard die only (FR-016): a six counts toward
     * the run of consecutive sixes, and the third consecutive six is cancelled,
     * which passes the turn to the next player immediately. A cancelled roll must
     * not be used to generate moves. Otherwise the turn moves to
     * {@link TurnPhase#WAITING_FOR_MOVE} and the Standard value is kept until
     * {@link #completeTurn(PlayerColor, boolean)}.
     *
     * @param player the color of the player rolling
     * @return the roll, with all of its dice values, and whether it was cancelled
     * @throws IllegalStateException    if it is not this player's turn, or the turn
     *                                  is not waiting for a roll
     * @throws IllegalArgumentException if the strategy's dice produce an invalid
     *                                  value; the turn state is left unchanged
     */
    public TurnRoll roll(PlayerColor player) {
        requireActivePlayer(player);
        requirePhase(TurnPhase.WAITING_FOR_ROLL);
        DiceRoll dice = diceStrategy.rollDice();
        int value = dice.standardRoll();
        consecutiveSixes = value == 6 ? consecutiveSixes + 1 : 0;
        if (consecutiveSixes == 3) {
            advancePlayer();
            return new TurnRoll(dice, true);
        }
        lastRoll = value;
        phase = TurnPhase.WAITING_FOR_MOVE;
        return new TurnRoll(dice, false);
    }

    /**
     * Checks that this player may move this piece now: it is their turn, a roll is
     * waiting to be used, and the piece is theirs. Whether the move itself is legal
     * on the board is checked by {@link MoveRules}, not here.
     *
     * @param player the color of the player moving
     * @param piece  the piece to move
     * @throws IllegalStateException    if it is not this player's turn, or no roll
     *                                  is waiting for a move
     * @throws IllegalArgumentException if the piece belongs to another player
     * @throws NullPointerException     if {@code piece} is {@code null}
     */
    public void requireCanMove(PlayerColor player, Piece piece) {
        requireActivePlayer(player);
        requirePhase(TurnPhase.WAITING_FOR_MOVE);
        if (!currentPlayer().pieces().contains(Objects.requireNonNull(piece, "piece"))) {
            throw new IllegalArgumentException("The active player may move only their own pieces");
        }
    }

    /**
     * Ends the active player's turn when they have not finished the game. Called
     * by {@link Game} after a move is executed, or when the roll has no legal move.
     * A legal move must not be skipped voluntarily; the caller checks that.
     *
     * @param player the color of the active player
     * @throws IllegalStateException if it is not this player's turn, or no roll
     *                               is waiting for a move
     * @see #completeTurn(PlayerColor, boolean)
     */
    public void completeTurn(PlayerColor player) {
        completeTurn(player, false);
    }

    /**
     * Ends the active player's turn and decides who plays next.
     * <ul>
     *   <li>If the player has just finished (all four pieces on the Finishing
     *       Square, verified by {@link Game}), they get no extra turn, even after a
     *       six. If only one unfinished player remains, the game is over and no
     *       further turn actions are allowed.</li>
     *   <li>Otherwise, if the Standard die showed a six, the same player rolls again.</li>
     *   <li>Otherwise, the turn passes clockwise to the next unfinished player.</li>
     * </ul>
     *
     * @param player         the color of the active player
     * @param playerFinished {@code true} if this turn's move finished the player
     * @throws IllegalStateException if it is not this player's turn, or no roll
     *                               is waiting for a move
     */
    public void completeTurn(PlayerColor player, boolean playerFinished) {
        requireActivePlayer(player);
        requirePhase(TurnPhase.WAITING_FOR_MOVE);
        if (playerFinished) finishedPlayers.add(player);
        if (finishedPlayers.size() >= players.size() - 1) {
            phase = TurnPhase.GAME_OVER;
            lastRoll = 0;
            consecutiveSixes = 0;
        } else if (finishedPlayers.contains(player)) {
            advancePlayer();
        } else if (lastRoll == 6) {
            lastRoll = 0;
            phase = TurnPhase.WAITING_FOR_ROLL;
        } else {
            advancePlayer();
        }
    }

    /** Game has verified this participant's four pieces, possibly during an opponent's effect chain. */
    void recordFinishedPlayer(PlayerColor player) {
        requirePhase(TurnPhase.WAITING_FOR_MOVE);
        if (players.stream().noneMatch(participant -> participant.color() == player)) {
            throw new IllegalArgumentException("Finished player must participate in this game");
        }
        finishedPlayers.add(player);
    }

    /** Suspend the current roll while Game resolves a landing effect. */
    void pauseForEffect() {
        requirePhase(TurnPhase.WAITING_FOR_MOVE);
        phase = TurnPhase.WAITING_FOR_EFFECT;
    }

    /** Resume the same roll so Game can either request another effect or complete the turn. */
    void resumeAfterEffect() {
        requirePhase(TurnPhase.WAITING_FOR_EFFECT);
        phase = TurnPhase.WAITING_FOR_MOVE;
    }

    /** Throws {@link IllegalStateException} unless the turn is in the expected phase. */
    private void requirePhase(TurnPhase expected) {
        if (phase != expected) {
            throw new IllegalStateException("Expected " + expected + " but turn is " + phase);
        }
    }

    /**
     * Passes the turn clockwise to the next player who has not finished, and
     * resets the pending roll and the run of sixes for the new player.
     */
    private void advancePlayer() {
        do {
            activeIndex = (activeIndex + 1) % players.size();
        } while (finishedPlayers.contains(players.get(activeIndex).color()));
        phase = TurnPhase.WAITING_FOR_ROLL;
        lastRoll = 0;
        consecutiveSixes = 0;
    }
}
