package Controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;

import Model.Difficulty;
import Model.Game;
import Model.Player;
import Model.PlayerColor;
import Model.PlayerSetup;
import Model.SetupValidator;
import View.BoardView;
import View.SetupView;
import javafx.scene.Parent;

/** Validates setup input and opens a new Easy game with its results flow. */
public class SetupController {
    private final Consumer<Parent> showScreen;
    private final Runnable onBack;
    private final SetupView view = new SetupView();
    private boolean gameStarted;

    /**
     * @param showScreen puts a screen's root node into the application window
     * @param onBack what "Back to Main Menu" does
     */
    public SetupController(Consumer<Parent> showScreen, Runnable onBack) {
        this.showScreen = Objects.requireNonNull(showScreen, "showScreen");
        this.onBack = Objects.requireNonNull(onBack, "onBack");
        view.setOnBack(onBack);
        view.setOnStartGame(this::startGame);
    }

    public void show() {
        showScreen.accept(view.getRoot());
    }

    private void startGame() {
        if (gameStarted) return;
        Validation input = validateSetup(view.getPlayerNames(), view.getPlayerColours());
        if (!input.errors().isEmpty()) {
            view.showValidationMessages(input.errors());
            return;
        }
        view.clearValidationMessages();
        Game game = new Game(input.players().stream().map(Player::new).toList());
        BoardView board = new BoardView();
        GameController controller = new GameController(board, game);
        Runnable mainMenu = () -> {
            controller.stop();
            onBack.run();
        };
        Runnable newGame = () -> {
            controller.stop();
            new SetupController(showScreen, onBack).show();
        };
        controller.setOnMainMenu(mainMenu);
        ResultsController results = new ResultsController(showScreen, mainMenu, newGame);
        results.showWhenOver(controller);
        gameStarted = true;
        showScreen.accept(board.getRoot());
    }

    /** Validate raw form values without creating JavaFX controls or changing the input. */
    static Validation validateSetup(List<String> names, List<String> colours) {
        Objects.requireNonNull(names, "names");
        Objects.requireNonNull(colours, "colours");
        if (names.size() != colours.size()) {
            return new Validation(List.of(), List.of("Each player needs exactly one color"));
        }
        if (names.size() < 2 || names.size() > 4) {
            return new Validation(List.of(), List.of("Please add between 2 and 4 players"));
        }
        List<PlayerSetup> players = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        for (int i = 0; i < names.size(); i++) {
            try {
                String colour = colours.get(i);
                if (colour == null) throw new IllegalArgumentException("Please select a color");
                PlayerColor color;
                try {
                    color = PlayerColor.valueOf(colour.trim().toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException invalidColor) {
                    throw new IllegalArgumentException("Please select Red, Green, Yellow or Blue");
                }
                players.add(new PlayerSetup(names.get(i), color));
            } catch (IllegalArgumentException invalidInput) {
                errors.add("Player " + (i + 1) + ": " + invalidInput.getMessage());
            }
        }
        if (errors.isEmpty()) errors.addAll(SetupValidator.validate(players, Difficulty.EASY));
        return new Validation(errors.isEmpty() ? players : List.of(), errors);
    }

    /** Only a fully valid input can supply participants for a new game. */
    record Validation(List<PlayerSetup> players, List<String> errors) {
        Validation {
            players = List.copyOf(players);
            errors = List.copyOf(errors);
        }
    }
}
