package View;

import javafx.application.Application;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * The JavaFX application: creates the primary window and shows the first scene.
 */
public class LudoApp extends Application {

    @Override
    public void start(Stage stage) {
        // Set the window title
        stage.setTitle("Ludo – Team Sloth");
        // Development only: --dev-setup opens the Setup screen directly instead of the Main Menu
        boolean devSetup = getParameters().getRaw().contains("--dev-setup");
        // Build the first screen: the Main Menu, or the Setup screen in development
        Parent firstScreen = devSetup ? new SetupView().getRoot() : new MainMenuView().getRoot();
        // Create an 800x600 scene showing the first screen
        Scene scene = new Scene(firstScreen, 800, 600);
        // Apply the shared look (white background, chunky square buttons)
        scene.getStylesheets().add(getClass().getResource("/View/ludo.css").toExternalForm());
        // Attach the scene to the window
        stage.setScene(scene);
        // Never let the window shrink below the size the menu needs, so nothing is cut off
        stage.setMinWidth(500);
        stage.setMinHeight(480);
        // Show the window
        stage.show();
    }

    public static void main(String[] args) {
        // Launch the JavaFX runtime, which calls start()
        launch(args);
    }
}
