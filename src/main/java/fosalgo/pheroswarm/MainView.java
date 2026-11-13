package fosalgo.pheroswarm;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

import java.io.File;
import java.util.Locale;

/**
 * JavaFX application view coordinating the existing tools, replay, and collapsible experiment
 * workspace.
 */
public class MainView extends Application {

    /**
     * SVG paths used by the simulation-log context menu.
     */
    private final String CLEAR_LOG_ICON = "M8.5 6.5 H15.5 M10 4.5 H14 M7 6.5 L8 18.5 H16 L17 6.5 M10.5 9.5 V15.5 M13.5 9.5 V15.5";
    private final String COPY_LOG_ICON = "M8.5 8.5 H16.5 A1.5 1.5 0 0 1 18 10 V17 A1.5 1.5 0 0 1 16.5 18.5 H8.5 A1.5 1.5 0 0 1 7 17 V10 A1.5 1.5 0 0 1 8.5 8.5 Z M10 8 V7 A1.5 1.5 0 0 1 11.5 5.5 H18 A1.5 1.5 0 0 1 19.5 7 V13.5";
    private final String SAVE_LOG_ICON = "M4.5 5.5 L6.5 3.5 H17.5 A1.5 1.5 0 0 1 19 5 V19 A1.5 1.5 0 0 1 17.5 20.5 H6 A1.5 1.5 0 0 1 4.5 19 Z M7.5 3.8 V8.5 H15.5 V3.8 M8 20.2 V13.2 H16 V20.2 M14 5.4 V7";

    /**
     * Existing application toolbar and immutable grid configuration.
     */
    private final ToggleGroup toolGroup = new ToggleGroup();
    private final Button btnNew = initializeButton(SVGIcon.newEnv, 1.0, 1.0, "New Environment");
    private final Button btnOpen = initializeButton(SVGIcon.openEnv, 1.0, 1.0, "Open Environment");
    private final Button btnSave = initializeButton(SVGIcon.saveEnv, 1.0, 1.0, "Save Environment");
    private final ToggleButton btnTransform = initializeToggleButton(SVGIcon.transform, 1.0, 1.0, "Transform");
    private final ToggleButton btnAddDrone = initializeToggleButton(SVGIcon.addDrone, 1.0, 1.0, "Add Drone");
    private final ToggleButton btnRemoveDrone = initializeToggleButton(SVGIcon.removeDrone, 1.0, 1.0, "Remove Drone");
    private final ToggleButton btnWall = initializeToggleButton(SVGIcon.wall, 1.0, 1.0, "Wall");
    private final ToggleButton btnDrawLine = initializeToggleButton(SVGIcon.drawLine, 1.0, 1.0, "Draw Line");
    private final ToggleButton btnRemoveLine = initializeToggleButton(SVGIcon.removeLine, 1.0, 1.0, "Remove Line");
    private final Button btnRun = initializeButton(SVGIcon.run, 1.0, 1.0, "Run");
    private final Button btnPlayPause = initializeButton(SVGIcon.playPause, 1.0, 1.0, "Play - Pause");
    private final Button btnReplay = initializeButton(SVGIcon.replay, 1.0, 1.0, "Replay");
    private final int MAX_NUM_ROWS = 200;
    private final int MAX_NUM_COLS = 400;
    private final int cellSize = 10;
    private final int numRows = MAX_NUM_ROWS;
    private final int numCols = MAX_NUM_COLS;
    private final int[][] environment = new int[numRows][numCols];
    private final String workspace = System.getProperty("user.home");
    /**
     * Application status, file dialogs, simulation canvas, and panel references.
     */
    private Label statusLabel;
    private Label statelabel;
    private Stage primaryStage = null;

    private ProgressIndicator progressIndicator;
    private CanvasView canvas = null;
    private TextArea logArea;
    private AlgorithmPropertiesPane algorithmPropertiesPane;
    private String currentMapName = "Untitled (in-memory environment)";

    /**
     * Creates a toolbar button with the existing vector icon and hover behavior.
     *
     * @param svgPathData SVG path commands for the icon
     * @param scaleX      horizontal icon scale
     * @param scaleY      vertical icon scale
     * @param tooltip     text displayed in the control tooltip
     * @return a configured toolbar button
     */
    private Button initializeButton(
            String svgPathData, double scaleX, double scaleY, String tooltip) {
        SVGPath path = new SVGPath();
        path.setContent(svgPathData);
        path.setFill(Color.valueOf("#393E46"));
        path.setStroke(null);
        path.setStrokeLineCap(javafx.scene.shape.StrokeLineCap.ROUND);
        path.setStrokeLineJoin(javafx.scene.shape.StrokeLineJoin.ROUND);
        path.setScaleX(scaleX);
        path.setScaleY(scaleY);
        Button btn = new Button();
        btn.setGraphic(path);
        btn.setTooltip(new Tooltip(tooltip));
        btn.setStyle("-fx-background-color: transparent;");
        btn.setOnMouseEntered(e -> path.setFill(Color.valueOf("#0096FF")));
        btn.setOnMouseExited(e -> path.setFill(Color.valueOf("#393E46")));
        return btn;
    }

    /**
     * Creates a stateful toolbar toggle with the existing vector icon behavior.
     *
     * @param svgPathData SVG path commands for the icon
     * @param scaleX      horizontal icon scale
     * @param scaleY      vertical icon scale
     * @param tooltip     text displayed in the control tooltip
     * @return a configured toolbar toggle button
     */
    private ToggleButton initializeToggleButton(
            String svgPathData, double scaleX, double scaleY, String tooltip) {
        SVGPath path = new SVGPath();
        path.setContent(svgPathData);
        path.setFill(Color.valueOf("#393E46"));
        path.setStroke(null);
        path.setStrokeLineCap(javafx.scene.shape.StrokeLineCap.ROUND);
        path.setStrokeLineJoin(javafx.scene.shape.StrokeLineJoin.ROUND);
        path.setScaleX(scaleX);
        path.setScaleY(scaleY);

        ToggleButton btn = new ToggleButton();
        btn.setGraphic(path);
        btn.setTooltip(new Tooltip(tooltip));
        btn.setStyle("-fx-background-color: transparent;");

        btn.setOnMouseEntered(
                e -> {
                    if (!btn.isSelected()) {
                        path.setFill(Color.valueOf("#393E46"));
                        path.setStroke(null);
                    }
                });

        btn.setOnMouseExited(
                e -> {
                    if (!btn.isSelected()) {
                        path.setFill(Color.valueOf("#393E46"));
                        path.setStroke(null);
                    }
                });

        btn.selectedProperty()
                .addListener(
                        (obs, oldVal, isSelected) -> {
                            if (isSelected) {
                                path.setFill(Color.valueOf("#0096FF"));
                            } else {
                                path.setFill(Color.valueOf("#393E46"));
                            }
                        });

        return btn;
    }

    /**
     * Associates mutually exclusive editor tools with their canvas editing modes.
     */
    private void initializeToggleGroup() {
        btnTransform.setToggleGroup(toolGroup);
        btnAddDrone.setToggleGroup(toolGroup);
        btnRemoveDrone.setToggleGroup(toolGroup);
        btnWall.setToggleGroup(toolGroup);
        btnDrawLine.setToggleGroup(toolGroup);
        btnRemoveLine.setToggleGroup(toolGroup);

        btnTransform.setSelected(true);

        toolGroup
                .selectedToggleProperty()
                .addListener(
                        (obs, oldToggle, newToggle) -> {
                            if (newToggle == null && oldToggle != null) {
                                oldToggle.setSelected(true);
                                return;
                            }
                            if (newToggle == btnTransform) {
                                canvas.setFreeTransform();
                            } else if (newToggle == btnAddDrone) {
                                canvas.setState(State.ADD_DRONE);
                            } else if (newToggle == btnRemoveDrone) {
                                canvas.setState(State.REMOVE_DRONE);
                            } else if (newToggle == btnWall) {
                                canvas.setState(State.OBSTACLE);
                            } else if (newToggle == btnDrawLine) {
                                canvas.setState(State.LINE);
                            } else if (newToggle == btnRemoveLine) {
                                canvas.setState(State.REMOVE_LINE);
                            }
                            checkState();
                        });
    }

    /**
     * Creates a small vector icon for the simulation-log context menu.
     *
     * @param svgPathData SVG path commands for the icon
     * @return a fixed-size JavaFX node containing the outlined icon
     */
    private javafx.scene.Node createMenuIcon(String svgPathData) {
        SVGPath path = new SVGPath();
        path.setContent(svgPathData);
        path.setFill(Color.TRANSPARENT);
        path.setStroke(Color.valueOf("#393E46"));
        path.setStrokeWidth(1.45);
        path.setStrokeLineCap(javafx.scene.shape.StrokeLineCap.ROUND);
        path.setStrokeLineJoin(javafx.scene.shape.StrokeLineJoin.ROUND);
        path.setScaleX(0.90);
        path.setScaleY(0.90);

        StackPane wrapper = new StackPane(path);
        wrapper.setMinSize(18, 18);
        wrapper.setPrefSize(18, 18);
        wrapper.setMaxSize(18, 18);
        return wrapper;
    }

    /**
     * Creates the simulator canvas and attaches its mouse listeners on first access.
     *
     * @return the canvas owned by this application
     */
    private CanvasView getCanvas() {
        if (canvas == null) {
            canvas = new CanvasView(environment, cellSize);
            canvas.setOnMousePressed(this::handleMousePressed);
            canvas.setOnMouseDragged(this::handleMouseDragged);
            canvas.setOnScroll(this::handleMouseScroll);
            canvas.setOnMouseClicked(this::handleMouseClicked);
            canvas.setOnMouseReleased(this::handleMouseReleased);
            canvas.setOnMouseMoved(this::handleMouseMoved);
        }
        return canvas;
    }

    /**
     * Forwards the pointer event to the unchanged canvas input handler.
     *
     * @param mouseEvent JavaFX pointer event
     */
    private void handleMouseMoved(MouseEvent mouseEvent) {
        canvas.handleMouseMoved(mouseEvent);
    }

    /**
     * Forwards the pointer event to the unchanged canvas input handler.
     *
     * @param mouseEvent JavaFX pointer event
     */
    private void handleMouseReleased(MouseEvent mouseEvent) {
        canvas.handleMouseReleased(mouseEvent);
    }

    /**
     * Forwards the pointer event to the unchanged canvas input handler.
     *
     * @param mouseEvent JavaFX pointer event
     */
    private void handleMouseClicked(MouseEvent mouseEvent) {
        canvas.handleMouseClicked(mouseEvent);
    }

    /**
     * Forwards the pointer event to the unchanged canvas input handler.
     *
     * @param scrollEvent JavaFX scroll event
     */
    private void handleMouseScroll(ScrollEvent scrollEvent) {
        canvas.handleMouseScroll(scrollEvent);
    }

    /**
     * Forwards the pointer event to the unchanged canvas input handler.
     *
     * @param mouseEvent JavaFX pointer event
     */
    private void handleMouseDragged(MouseEvent mouseEvent) {
        canvas.handleMouseDragged(mouseEvent);
    }

    /**
     * Forwards the pointer event to the unchanged canvas input handler.
     *
     * @param mouseEvent JavaFX pointer event
     */
    private void handleMousePressed(MouseEvent mouseEvent) {
        canvas.handleMousePressed(mouseEvent);
    }

    /**
     * Prompts for an environment file, loads its contents, and updates UI status.
     *
     * @param actionEvent JavaFX command event
     */
    private void handleButtonOpen(ActionEvent actionEvent) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Open Environment");

        File documentsDir = new File(workspace, "Documents");
        if (documentsDir.exists()) {
            fileChooser.setInitialDirectory(documentsDir);
        }

        fileChooser
                .getExtensionFilters()
                .addAll(
                        new FileChooser.ExtensionFilter("Data", "*.sc"),
                        new FileChooser.ExtensionFilter("All Files", "*.*"));

        File selectedFile = fileChooser.showOpenDialog(primaryStage);
        if (selectedFile != null) {

            try {
                canvas.readFromFile(selectedFile);
                currentMapName = selectedFile.getName();
                clearSimulationLog();
                logArea.appendText("Environment loaded: " + currentMapName + "\n");
                statusLabel.setText("Opened: " + selectedFile.getName());
                checkState();
            } catch (RuntimeException ex) {
                showFileError("Open failed", ex.getMessage());
            }
        }
    }

    /**
     * Prompts for an output file and writes the current environment.
     *
     * @param actionEvent JavaFX command event
     */
    private void handleButtonSave(ActionEvent actionEvent) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Environment");

        File documentsDir = new File(System.getProperty("user.home"), "Documents");
        if (documentsDir.exists()) {
            fileChooser.setInitialDirectory(documentsDir);
        }

        fileChooser
                .getExtensionFilters()
                .addAll(
                        new FileChooser.ExtensionFilter("Data", "*.sc"),
                        new FileChooser.ExtensionFilter("All Files", "*.*"));

        File selectedFile = fileChooser.showSaveDialog(primaryStage);
        if (selectedFile != null) {

            try {
                if (!selectedFile.getName().toLowerCase().endsWith(".sc"))
                    selectedFile =
                            new File(selectedFile.getParentFile(), selectedFile.getName() + ".sc");
                canvas.writeToFile(selectedFile);
                currentMapName = selectedFile.getName();
                statusLabel.setText("Saved: " + selectedFile.getName());
                checkState();
            } catch (RuntimeException ex) {
                showFileError("Save failed", ex.getMessage());
            }
        }
    }

    /**
     * Validates the selected experiment settings and runs the existing simulator.
     *
     * @param actionEvent JavaFX command event
     */
    private void handleButtonRun(ActionEvent actionEvent) {
        canvas.setState(State.DEFAULT);
        checkState();
        clearSimulationLog();
        logArea.appendText("Calculating exploration...\n");
        try {
            ExplorationConfig settings = algorithmPropertiesPane.snapshot();
            canvas.run(settings);
            logArea.setText(buildSimulationReport());
            logArea.positionCaret(0);
            statusLabel.setText("Exploration calculated — replay playing");
        } catch (RuntimeException ex) {
            logArea.appendText("SIMULATION ERROR: " + ex.getMessage() + "\n");
            showFileError("Simulation failed", ex.getMessage());
        }
    }

    /**
     * Toggles playback of the most recent simulation.
     *
     * @param actionEvent JavaFX command event
     */
    private void handleButtonPlayPause(ActionEvent actionEvent) {
        canvas.setState(State.DEFAULT);
        checkState();
        if (!canvas.hasReplay()) {
            statusLabel.setText("Run the simulation first.");
            return;
        }
        canvas.togglePlayPause();
        statusLabel.setText(canvas.isPlaybackPlaying() ? "Playing" : "Paused");
    }

    /**
     * Restarts the current replay from its first frame.
     *
     * @param actionEvent JavaFX command event
     */
    private void handleButtonReplay(ActionEvent actionEvent) {
        canvas.setState(State.DEFAULT);
        checkState();
        if (!canvas.hasReplay()) {
            statusLabel.setText("Run the simulation first.");
            return;
        }
        canvas.replay();
        statusLabel.setText("Replay from timestep 0");
    }

    /**
     * Displays an error dialog and updates the application status.
     *
     * @param title   heading or dialog title
     * @param message error detail displayed to the user
     */
    private void showFileError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(message);
        alert.showAndWait();
        statusLabel.setText(title);
    }

    /**
     * Resets the environment and clears the previous simulation log.
     *
     * @param actionEvent JavaFX command event
     */
    private void handleButtonResetEnvironment(ActionEvent actionEvent) {
        canvas.resetEnvironment();
        currentMapName = "Untitled (in-memory environment)";
        clearSimulationLog();
        logArea.appendText("Environment reset. Ready for a new simulation.\n");
        checkState();
    }

    /**
     * Copies the complete simulation report to the system clipboard.
     */
    private void copySimulationLog() {
        javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
        content.putString(logArea.getText());
        javafx.scene.input.Clipboard.getSystemClipboard().setContent(content);
    }

    /**
     * Writes the simulation report to a user-selected UTF-8 text file.
     */
    private void saveSimulationLog() {
        FileChooser logFileChooser = new FileChooser();
        logFileChooser.setTitle("Save Exploration Log");
        logFileChooser
                .getExtensionFilters()
                .add(new FileChooser.ExtensionFilter("Text Files (*.txt)", "*.txt"));

        String timestamp =
                java.time.LocalDateTime.now()
                        .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        logFileChooser.setInitialFileName("ExplorationLog" + timestamp + ".txt");

        File documentsDir = new File(System.getProperty("user.home"), "Documents");
        if (documentsDir.isDirectory()) {
            logFileChooser.setInitialDirectory(documentsDir);
        }

        File selectedFile = logFileChooser.showSaveDialog(primaryStage);
        if (selectedFile == null) return;

        if (!selectedFile.getName().toLowerCase(Locale.ROOT).endsWith(".txt")) {
            selectedFile = new File(selectedFile.getParentFile(), selectedFile.getName() + ".txt");
        }

        try {
            java.nio.file.Files.writeString(
                    selectedFile.toPath(),
                    logArea.getText(),
                    java.nio.charset.StandardCharsets.UTF_8);
            statusLabel.setText("Log saved: " + selectedFile.getName());
        } catch (java.io.IOException ex) {
            showFileError("Save Log failed", ex.getMessage());
        }
    }

    /**
     * Clears the current simulation report text.
     */
    private void clearSimulationLog() {
        if (logArea != null) logArea.clear();
    }

    /**
     * Formats exploration metrics and replay trajectories without changing simulation state.
     *
     * @return report text for the latest simulation, or a message when none is available
     */
    private String buildSimulationReport() {
        DecentralizedExploration result = canvas.getLastExploration();
        if (result == null) return "No simulation result available.\n";

        StringBuilder sb = new StringBuilder();
        sb.append("DECENTRALIZED SWARM COVERAGE - EXPERIMENT LOG\n");
        sb.append(
                "Algorithm \t: Decentralized Swarm Coverage Using Frontier-Based Exploration and Repulsive Pheromone Stigmergy\n");
        ExplorationConfig used = result.getConfiguration();
        sb.append("Method    \t: ")
                .append(used.mode())
                .append(used.lambda() == 0 ? " (FBE baseline)" : " (FBE + RPS)")
                .append('\n');
        sb.append("Map file  \t\t: ").append(currentMapName).append('\n');
        sb.append(
                String.format(
                        Locale.ROOT,
                        "Grid size \t\t: %d x %d cells%n",
                        canvas.getGridRows(),
                        canvas.getGridCols()));
        sb.append("Drones    \t\t: ").append(canvas.getReplayDrones().size()).append('\n');
        sb.append("Sensing   \t\t: Ray-Casting 360 degrees; range ")
                .append(canvas.getSensorRangeCells())
                .append(" cells\n");
        sb.append("Planner   \t\t: Single-Source Dijkstra (8-connected)\n");
        sb.append("Frontier  \t\t: Local Occupancy Map + Frontier-Based Exploration\n");
        sb.append("Pheromone \t: Global shared; movement-based deposit; evaporation\n");
        sb.append(
                String.format(
                        Locale.ROOT,
                        "Parameters\t: lambda=%.6f, rho=%.6f, deposit=%.6f, recovery=%s, maxSteps=%d, stallLimit=%d%n",
                        used.lambda(),
                        used.rho(),
                        used.deposit(),
                        used.recovery(),
                        used.maxSteps(),
                        used.stallLimit()));
        sb.append("\nSIMULATION RESULT\n");
        sb.append("Termination\t: ").append(result.getTerminationReason()).append('\n');
        boolean complete =
                "ALL_REACHABLE_FREE_CELLS_OBSERVED".equals(result.getTerminationReason());
        sb.append("Status\t\t: ").append(complete ? "COMPLETE" : "INCOMPLETE").append('\n');
        sb.append("\nFOUR PRIMARY EVALUATION METRICS\n");
        sb.append(
                String.format(
                        Locale.ROOT,
                        "1. Observed Coverage \t: %.2f%%%n",
                        result.getObservedRatio()));
        sb.append(
                String.format(
                        Locale.ROOT, "2. Revisit Ratio     \t\t: %.2f%%%n", result.getRevisit()));
        sb.append(
                String.format(
                        Locale.ROOT, "3. Overlap Ratio     \t\t: %.2f%%%n", result.getOverlap()));
        sb.append("4. Completion Steps  \t: ")
                .append(complete ? Integer.toString(result.getSteps()) : "N/A (incomplete run)")
                .append('\n');
        if (!complete)
            sb.append("Simulation Steps     : ")
                    .append(result.getSteps())
                    .append(" (termination before completion)\n");

        boolean[][] mustExplore = result.getMustExploreMask();
        int[][] observedAt = result.getFirstObservedAt();
        int mustExploredCells = 0;
        int exploredCells = 0;
        int minRow = Integer.MAX_VALUE, minCol = Integer.MAX_VALUE;
        int maxRow = -1, maxCol = -1;
        for (int r = 0; r < mustExplore.length; r++) {
            for (int c = 0; c < mustExplore[r].length; c++) {
                if (!mustExplore[r][c]) continue;
                mustExploredCells++;
                if (observedAt[r][c] >= 0) exploredCells++;
                minRow = Math.min(minRow, r);
                minCol = Math.min(minCol, c);
                maxRow = Math.max(maxRow, r);
                maxCol = Math.max(maxCol, c);
            }
        }

        int gridCols = canvas.getGridCols();
        java.util.Map<Integer, Integer> cellVisits = new java.util.HashMap<>();
        java.util.Map<Integer, java.util.Set<Integer>> cellDroneIds = new java.util.HashMap<>();
        java.util.List<Drone> replayDrones = canvas.getReplayDrones();
        for (int i = 0; i < replayDrones.size(); i++) {
            Point previous = null;
            for (Point point : replayDrones.get(i).path) {
                if (point.equals(previous)) continue;
                int key = point.row() * gridCols + point.col();
                cellVisits.merge(key, 1, Integer::sum);
                cellDroneIds.computeIfAbsent(key, ignored -> new java.util.HashSet<>()).add(i);
                previous = point;
            }
        }
        long numRevisitCells = cellVisits.values().stream().filter(count -> count > 1).count();
        long numOverlapCells = cellDroneIds.values().stream().filter(ids -> ids.size() > 1).count();

        sb.append("\nEXPLORATION\n");
        sb.append("1. Must Explored Cells \t: ").append(mustExploredCells).append('\n');
        sb.append("2. Explored Cells      \t: ").append(exploredCells).append('\n');
        sb.append("3. MIN Row             \t\t: ")
                .append(mustExploredCells == 0 ? "N/A" : minRow)
                .append('\n');
        sb.append("4. MIN Col             \t\t: ")
                .append(mustExploredCells == 0 ? "N/A" : minCol)
                .append('\n');
        sb.append("5. MAX Row             \t: ")
                .append(mustExploredCells == 0 ? "N/A" : maxRow)
                .append('\n');
        sb.append("6. MAX Col             \t\t: ")
                .append(mustExploredCells == 0 ? "N/A" : maxCol)
                .append('\n');
        sb.append("7. Observation Area    \t: ")
                .append(Math.abs(maxRow - minRow) + " X " + Math.abs(maxCol - minCol))
                .append('\n');
        sb.append("8. Num Revisit Cells   \t: ").append(numRevisitCells).append('\n');
        sb.append("9. Num Overlap Cells   \t: ").append(numOverlapCells).append('\n');

        sb.append("\nDRONE TRAJECTORIES (row,col; zero-based)\n");
        sb.append(
                "Includes start point and stationary frames. Each point corresponds to one replay frame.\n");
        for (int i = 0; i < replayDrones.size(); i++) {
            Drone d = replayDrones.get(i);
            sb.append("D").append(i + 1).append("\t: ");
            if (d.path == null || d.path.isEmpty()) {
                sb.append("(no trajectory)");
            } else {
                for (int j = 0; j < d.path.size(); j++) {
                    Point point = d.path.get(j);
                    if (j != 0) sb.append('-');
                    sb.append('(').append(point.row()).append(',').append(point.col()).append(')');
                }
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    /**
     * Synchronizes the selected toolbar tool and status text with the canvas mode.
     */
    private void checkState() {
        if (canvas.getState().equals(State.DEFAULT)) {
            statelabel.setText("STATE: " + "FREE TRANSFORM" + "\t-\t");
            btnTransform.setSelected(true);
        } else if (canvas.getState().equals(State.ADD_DRONE)) {
            statelabel.setText("STATE: " + "ADD DRONE" + "\t-\t");
            btnAddDrone.setSelected(true);
        } else if (canvas.getState().equals(State.REMOVE_DRONE)) {
            statelabel.setText("STATE: " + "REMOVE DRONE" + "\t-\t");
            btnRemoveDrone.setSelected(true);
        } else if (canvas.getState().equals(State.OBSTACLE)) {
            statelabel.setText("STATE: " + "DRAW OBSTACLE" + "\t-\t");
            btnWall.setSelected(true);
        } else if (canvas.getState().equals(State.LINE)) {
            statelabel.setText("STATE: " + "DRAW LINE" + "\t-\t");
            btnDrawLine.setSelected(true);
        } else if (canvas.getState().equals(State.REMOVE_LINE)) {
            statelabel.setText("STATE: " + "REMOVE LINE" + "\t-\t");
            btnRemoveLine.setSelected(true);
        }
    }

    /**
     * Builds the JavaFX application window, canvas, resizable tabs, and collapsible workspace.
     *
     * @param stage primary stage provided by the JavaFX runtime
     * @throws Exception if application initialization fails
     */
    @Override
    public void start(Stage stage) throws Exception {
        primaryStage = stage;

        VBox sidebar = new VBox(2);
        sidebar.getStyleClass().add("sidebar");
        sidebar.setPadding(new Insets(4));

        initializeToggleGroup();

        btnOpen.setOnAction(this::handleButtonOpen);
        btnSave.setOnAction(this::handleButtonSave);
        btnRun.setOnAction(this::handleButtonRun);
        btnPlayPause.setOnAction(this::handleButtonPlayPause);
        btnReplay.setOnAction(this::handleButtonReplay);
        btnNew.setOnAction(this::handleButtonResetEnvironment);
        VBox topButtons =
                new VBox(
                        2,
                        btnNew,
                        btnOpen,
                        btnTransform,
                        btnAddDrone,
                        btnRemoveDrone,
                        btnWall,
                        btnDrawLine,
                        btnRemoveLine,
                        btnSave,
                        btnRun,
                        btnPlayPause,
                        btnReplay);
        topButtons.setAlignment(Pos.TOP_CENTER);

        progressIndicator = new ProgressIndicator(0);
        progressIndicator.setVisible(false);

        VBox bottomButtons = new VBox(2, progressIndicator);
        bottomButtons.setAlignment(Pos.BOTTOM_CENTER);
        VBox.setVgrow(topButtons, Priority.ALWAYS);
        sidebar.getChildren().addAll(topButtons, bottomButtons);

        BorderPane centerPane = new BorderPane();
        centerPane.getStyleClass().add("center-pane");
        StackPane canvasPane = new StackPane();

        getCanvas();
        canvas.render();
        canvasPane.getChildren().add(canvas);

        ScrollPane scrollPane = new ScrollPane(canvasPane);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.ALWAYS);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.ALWAYS);
        scrollPane.setPrefHeight(900);

        logArea = new TextArea();
        logArea.setPrefRowCount(3);
        logArea.setWrapText(false);
        logArea.setEditable(false);
        logArea.setBorder(null);
        logArea.setText(
                "Decentralized Swarm Coverage Using Frontier-Based Exploration and Repulsive Pheromone Stigmergy\n");

        MenuItem copyLogItem = new MenuItem("Copy All", createMenuIcon(COPY_LOG_ICON));
        copyLogItem.setOnAction(e -> copySimulationLog());
        MenuItem saveLogItem = new MenuItem("Save Log", createMenuIcon(SAVE_LOG_ICON));
        saveLogItem.setOnAction(e -> saveSimulationLog());
        MenuItem clearLogItem = new MenuItem("Clear Log", createMenuIcon(CLEAR_LOG_ICON));
        clearLogItem.setOnAction(e -> clearSimulationLog());
        logArea.setContextMenu(new ContextMenu(copyLogItem, saveLogItem, clearLogItem));

        statusLabel = new Label("Ready.");
        statusLabel.getStyleClass().add("status-label");
        statelabel = new Label("STATE: " + canvas.getState().toString() + "\t-\t");
        statelabel.getStyleClass().add("status-label");
        HBox statusBar = new HBox(statelabel, statusLabel);
        statusBar.getStyleClass().add("status-bar");
        statusBar.setPadding(new Insets(5));

        ScrollPane logScroll = new ScrollPane(logArea);
        logScroll.setFitToWidth(true);
        logScroll.setFitToHeight(true);
        logScroll.setPannable(false);
        logScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        logScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        logArea.setMinHeight(110);
        logArea.setMinWidth(300);

        algorithmPropertiesPane = new AlgorithmPropertiesPane();
        ScrollPane propertiesScroll = new ScrollPane(algorithmPropertiesPane);
        propertiesScroll.setFitToWidth(true);
        propertiesScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        propertiesScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        propertiesScroll.setPannable(true);
        Tab logTab = new Tab("Simulation Log", logScroll);
        Tab propertiesTab = new Tab("Algorithm Properties", propertiesScroll);
        logTab.setClosable(false);
        propertiesTab.setClosable(false);
        TabPane workspaceTabs = new TabPane(logTab, propertiesTab);
        workspaceTabs.getStyleClass().add("workspace-tabs");
        workspaceTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        workspaceTabs.setMinHeight(130);
        workspaceTabs.setPrefHeight(230);

        SplitPane splitPane = new SplitPane(scrollPane, workspaceTabs);
        splitPane.setOrientation(Orientation.VERTICAL);
        splitPane.setDividerPositions(0.73);

        Button workspaceToggle = new Button("Hide Panel  ▾");
        workspaceToggle.getStyleClass().add("workspace-toggle");
        workspaceToggle.setFocusTraversable(false);
        workspaceToggle.setTooltip(
                new Tooltip("Hide or show Simulation Log and Algorithm Properties"));
        Label workspaceCaption = new Label("SIMULATION WORKSPACE");
        workspaceCaption.getStyleClass().add("workspace-collapse-caption");
        Region workspaceFill = new Region();
        HBox.setHgrow(workspaceFill, Priority.ALWAYS);
        HBox collapseHandle = new HBox(9, workspaceCaption, workspaceFill, workspaceToggle);
        collapseHandle.getStyleClass().add("workspace-collapse-handle");
        collapseHandle.setAlignment(Pos.CENTER_LEFT);
        collapseHandle.setMinHeight(28);
        collapseHandle.setPrefHeight(28);
        collapseHandle.setMaxHeight(28);

        final double[] previousDivider = {0.73};
        final boolean[] workspaceVisible = {true};
        workspaceToggle.setOnAction(
                event -> {
                    if (workspaceVisible[0]) {
                        previousDivider[0] =
                                splitPane.getDividerPositions().length == 0
                                        ? 0.73
                                        : splitPane.getDividerPositions()[0];
                        splitPane.getItems().remove(workspaceTabs);
                        workspaceToggle.setText("Show Panel  ▴");
                        workspaceToggle.setTooltip(
                                new Tooltip("Show Simulation Log and Algorithm Properties"));
                    } else {
                        splitPane.getItems().add(workspaceTabs);
                        splitPane.setDividerPositions(
                                Math.max(0.15, Math.min(0.90, previousDivider[0])));
                        workspaceToggle.setText("Hide Panel  ▾");
                        workspaceToggle.setTooltip(
                                new Tooltip("Hide Simulation Log and Algorithm Properties"));
                    }
                    workspaceVisible[0] = !workspaceVisible[0];
                });

        VBox resizableWorkspace = new VBox(splitPane, collapseHandle);
        VBox.setVgrow(splitPane, Priority.ALWAYS);

        VBox bottomBox = new VBox(statusBar);

        centerPane.setCenter(resizableWorkspace);
        centerPane.setBottom(bottomBox);

        BorderPane root = new BorderPane();

        root.setLeft(sidebar);
        root.setCenter(centerPane);
        Scene scene = new Scene(root, 1000, 600);
        scene.getStylesheets().add(CSS.style);

        AnimationTimer engine =
                new AnimationTimer() {
                    /**
                     * Updates and renders the canvas on each animation pulse.
                     *
                     * @param l animation timestamp supplied by JavaFX, in nanoseconds
                     */
                    @Override
                    public void handle(long l) {

                        canvas.update();

                        canvas.render();
                    }
                };
        engine.start();

        primaryStage.setTitle(
                "Robotics & Mechatronics Laboratory - Mechanical Engineering - Hasanuddin University");
        primaryStage.setMaximized(true);
        primaryStage.setScene(scene);
        primaryStage.show();
        primaryStage.setOnCloseRequest(
                new EventHandler<WindowEvent>() {
                    /**
                     * Shuts down the JavaFX application when the main window closes.
                     *
                     * @param windowEvent primary window close event
                     */
                    @Override
                    public void handle(WindowEvent windowEvent) {
                        Platform.exit();
                        System.exit(0);
                    }
                });
        primaryStage.getIcons().add(new Image(this.getClass().getResourceAsStream("icon.png")));
    }
}
