package Model;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Immutable S03 choice snapshot, tied to one PendingSquareEffect ID.
 * A roll of zero means an opponent has not been chosen and no die has been rolled.
 * After the roll, movesByOpponent contains each opponent's legal reverse-path choices,
 * including empty lists; the same roll is retained when choosing another opponent.
 */
public record PendingOpponentMove(long effectId, List<Player> opponents, Player selectedOpponent,
                                  int roll, Map<PlayerColor, List<MoveCandidate>> movesByOpponent) {
    public PendingOpponentMove {
        if (effectId <= 0 || roll < 0 || roll > 6) {
            throw new IllegalArgumentException("Invalid effect ID or Standard die result");
        }
        opponents = List.copyOf(opponents);
        movesByOpponent = Objects.requireNonNull(movesByOpponent, "movesByOpponent").entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, e -> List.copyOf(e.getValue())));
        if ((roll == 0 && (selectedOpponent != null || !movesByOpponent.isEmpty()))
                || (roll != 0 && (selectedOpponent == null || !opponents.contains(selectedOpponent)))) {
            throw new IllegalArgumentException("Opponent selection must precede the roll");
        }
    }

    /** Half the Standard result, rounded up; zero before the event roll. */
    public int steps() { return (roll + 1) / 2; }

    /** The selected opponent's choices, in piece-number order; empty before selection or if blocked. */
    public List<MoveCandidate> moves() {
        return selectedOpponent == null ? List.of() : movesByOpponent.getOrDefault(selectedOpponent.color(), List.of());
    }
}
