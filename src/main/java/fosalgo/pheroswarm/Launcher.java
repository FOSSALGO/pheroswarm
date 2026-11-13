package fosalgo.pheroswarm;

import javafx.application.Application;

/**
 * Entry point for the JavaFX simulator.
 */
public class Launcher {
    /**
     * Starts the JavaFX application using the existing main view.
     *
     * @param args command-line arguments forwarded to JavaFX
     */
    public static void main(String[] args) {
        Application.launch(MainView.class, args);
    }
}
