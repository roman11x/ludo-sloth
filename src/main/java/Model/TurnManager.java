package Model;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * Per-game turn state for 2-4 players. Chooses the first player uniformly,
 * then follows board colors clockwise, skipping absent participants. Actions are guarded
 * by both active-player color and turn phase. Does not change board occupancy.
 */
public final class TurnManager {
    // Board corners: top-left, top-right, bottom-right, bottom-left.
    private static final List<PlayerColor> CLOCKWISE_COLORS = List.of(
            PlayerColor.GREEN, PlayerColor.YELLOW, PlayerColor.BLUE, PlayerColor.RED);
    private final List<Player> players;
    private final Die standardDie;
    private final EnumSet<PlayerColor> finishedPlayers = EnumSet.noneOf(PlayerColor.class);
    private int activeIndex;
    private TurnPhase phase = TurnPhase.WAITING_FOR_ROLL;
    private int lastRoll;
    private int consecutiveSixes;

    public TurnManager(List<Player> players) {
        this(players, new StandardDie(), new Random());
    }

    /** Allows controlled dice and first-player selection in tests. */
    public TurnManager(List<Player> players, Die standardDie, Random random) {
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
        this.standardDie = Objects.requireNonNull(standardDie, "standardDie");
        this.activeIndex = Objects.requireNonNull(random, "random").nextInt(this.players.size());
    }

    public List<Player> players() {
        return players;
    }

    /** No player is active once the game is over. */
    public Player currentPlayer() {
        if (phase == TurnPhase.GAME_OVER) {
            throw new IllegalStateException("The game is over; there is no active player");
        }
        return players.get(activeIndex);
    }

    public TurnPhase phase() {
        return phase;
    }

    public boolean isActive(PlayerColor player) {
        Objects.requireNonNull(player, "player");
        return phase != TurnPhase.GAME_OVER && currentPlayer().color() == player;
    }

    public void requireActivePlayer(PlayerColor player) {
        if (!isActive(player)) {
            throw new IllegalStateException("Only the active player may perform turn actions");
        }
    }

    /**
     * Rolls once for the active player. FR-016: the third consecutive six is
     * cancelled and advances immediately; its value must not generate moves.
     */
    public TurnRoll roll(PlayerColor player) {
        requireActivePlayer(player);
        requirePhase(TurnPhase.WAITING_FOR_ROLL);
        int value = standardDie.roll();
        if (value < 1 || value > 6) {
            throw new IllegalStateException("Standard die returned a value outside 1-6");
        }
        consecutiveSixes = value == 6 ? consecutiveSixes + 1 : 0;
        if (consecutiveSixes == 3) {
            advancePlayer();
            return new TurnRoll(value, true);
        }
        lastRoll = value;
        phase = TurnPhase.WAITING_FOR_MOVE;
        return new TurnRoll(value, false);
    }

    /** Checks turn and ownership, not path legality; MoveRules checks the path. */
    public void requireCanMove(PlayerColor player, Piece piece) {
        requireActivePlayer(player);
        requirePhase(TurnPhase.WAITING_FOR_MOVE);
        if (!currentPlayer().pieces().contains(Objects.requireNonNull(piece, "piece"))) {
            throw new IllegalArgumentException("The active player may move only their own pieces");
        }
    }

    /**
     * Called by game orchestration after a move is executed, or after finding
     * no legal move. A pending legal move must not be voluntarily skipped.
     * The caller verifies that condition; this method manages the transition.
     */
    public void completeTurn(PlayerColor player) {
        completeTurn(player, false);
    }

    /**
     * Game reports completion only after verifying all four pieces at Finish.
     * A finishing player never gets an extra turn, even after a six. With only
     * one unfinished participant left, freeze turn actions instead of advancing.
     */
    public void completeTurn(PlayerColor player, boolean playerFinished) {
        requireActivePlayer(player);
        requirePhase(TurnPhase.WAITING_FOR_MOVE);
        if (playerFinished) {
            finishedPlayers.add(player);
            if (finishedPlayers.size() == players.size() - 1) {
                phase = TurnPhase.GAME_OVER;
                lastRoll = 0;
                consecutiveSixes = 0;
            } else {
                advancePlayer();
            }
        } else if (lastRoll == 6) {
            lastRoll = 0;
            phase = TurnPhase.WAITING_FOR_ROLL;
        } else {
            advancePlayer();
        }
    }

    private void requirePhase(TurnPhase expected) {
        if (phase != expected) {
            throw new IllegalStateException("Expected " + expected + " but turn is " + phase);
        }
    }

    private void advancePlayer() {
        do {
            activeIndex = (activeIndex + 1) % players.size();
        } while (finishedPlayers.contains(players.get(activeIndex).color()));
        phase = TurnPhase.WAITING_FOR_ROLL;
        lastRoll = 0;
        consecutiveSixes = 0;
    }
}
