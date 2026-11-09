package Controller;

import java.util.Objects;

import Model.Die;
import Model.StandardDie;
import View.BoardView;
import View.DiceView;

/**
 * Handles rolling, piece selection and board updates during play.
 */
public class GameController {

    private final DiceView diceView;
    private final Die die;

    /**
     * Drives the given board screen with the Easy level's standard die (1–6, equal probability).
     */
    public GameController(BoardView boardView) {
        this(boardView, new StandardDie());
    }

    /**
     * Drives the given board screen with the given die, e.g. a seeded one for repeatable tests.
     */
    public GameController(BoardView boardView, Die die) {
        this.diceView = Objects.requireNonNull(boardView, "boardView").getDiceView();
        this.die = Objects.requireNonNull(die, "die");
        diceView.setOnRoll(this::roll);
    }

    /**
     * One roll per press of Roll (button or Space): a new value from the die, shown in the dice area.
     */
    private void roll() {
        diceView.showResult(die.roll());
    }
}
