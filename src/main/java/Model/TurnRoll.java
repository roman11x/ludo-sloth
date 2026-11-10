package Model;

/** A Standard-die result; cancelled means the third consecutive six. */
public record TurnRoll(int value, boolean cancelled) {
    public TurnRoll {
        if (value < 1 || value > 6 || (cancelled && value != 6)) {
            throw new IllegalArgumentException("Invalid Standard-die turn result");
        }
    }
}
