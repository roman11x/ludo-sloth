package Model;

import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class SurpriseEventSelectorTest {
    private SurpriseEvent eventFor(int value) {
        return new SurpriseEventSelector(new Random() {
            @Override public int nextInt(int bound) {
                assertEquals(100, bound);
                return value;
            }
        }).select();
    }

    @Test
    void everyIntervalIncludesItsLowerAndUpperBoundary() {
        var boundaries = Map.ofEntries(
                Map.entry(0, SurpriseEvent.MOVE_TO_FINISH),
                Map.entry(9, SurpriseEvent.MOVE_TO_FINISH),
                Map.entry(10, SurpriseEvent.MOVE_OPPONENT_BACKWARD),
                Map.entry(19, SurpriseEvent.MOVE_OPPONENT_BACKWARD),
                Map.entry(20, SurpriseEvent.RETURN_TO_CASTLE),
                Map.entry(29, SurpriseEvent.RETURN_TO_CASTLE),
                Map.entry(30, SurpriseEvent.MOVE_BACK_THREE),
                Map.entry(59, SurpriseEvent.MOVE_BACK_THREE),
                Map.entry(60, SurpriseEvent.SKIP_NEXT_TURN),
                Map.entry(89, SurpriseEvent.SKIP_NEXT_TURN),
                Map.entry(90, SurpriseEvent.EXTRA_ULTRA_TURN),
                Map.entry(99, SurpriseEvent.EXTRA_ULTRA_TURN));
        boundaries.forEach((value, expected) -> assertEquals(expected, eventFor(value), "Draw " + value));
    }

    @Test
    void allOneHundredEquallyLikelyInputsProduceTheExactRequiredProbabilityWeights() {
        AtomicInteger draws = new AtomicInteger();
        var selector = new SurpriseEventSelector(new Random() {
            @Override public int nextInt(int bound) {
                assertEquals(100, bound);
                return draws.getAndIncrement();
            }
        });
        Map<SurpriseEvent, Integer> counts = new EnumMap<>(SurpriseEvent.class);
        for (int input = 0; input < 100; input++) counts.merge(selector.select(), 1, Integer::sum);
        assertEquals(Map.of(
                SurpriseEvent.MOVE_TO_FINISH, 10,
                SurpriseEvent.MOVE_OPPONENT_BACKWARD, 10,
                SurpriseEvent.RETURN_TO_CASTLE, 10,
                SurpriseEvent.MOVE_BACK_THREE, 30,
                SurpriseEvent.SKIP_NEXT_TURN, 30,
                SurpriseEvent.EXTRA_ULTRA_TURN, 10), counts);
        assertEquals(100, draws.get(), "Each selection must use exactly one draw");
    }

    @Test
    void equalSeedsReproduceASequenceWithoutResettingTheRandomSourceEachSelection() {
        var first = new SurpriseEventSelector(new Random(42));
        var second = new SurpriseEventSelector(new Random(42));
        var seen = java.util.EnumSet.noneOf(SurpriseEvent.class);
        for (int draw = 0; draw < 1000; draw++) {
            SurpriseEvent event = first.select();
            assertEquals(event, second.select());
            seen.add(event);
        }
        assertEquals(java.util.EnumSet.allOf(SurpriseEvent.class), seen);
    }

    @Test
    void defaultConstructionProducesAnEventWithoutNeedingGameState() {
        assertNotNull(new SurpriseEventSelector().select());
    }

    @Test
    void rejectsAMissingRandomSource() {
        assertThrows(NullPointerException.class, () -> new SurpriseEventSelector(null));
    }
}
