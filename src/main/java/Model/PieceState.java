package Model;

/**
 * Legacy vocabulary for a piece's journey, retained for compatibility with
 * other team branches. No PieceState value is stored on Piece: the current
 * state is derived from its square's type and color in Board.
 */
public enum PieceState {
    IN_CASTLE,
    ON_PATH,
    IN_HOME_PATH,
    FINISHED
}
