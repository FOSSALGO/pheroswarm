package fosalgo.pheroswarm;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontSmoothingType;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;

/**
 * Grid-world renderer and editor with mouse navigation and synchronized drone replay.
 */
public class CanvasView extends Canvas {

    /**
     * Camera zoom limits, zoom increment, and pointer-gesture discrimination settings.
     */
    private static final double MIN_SCALE = 0.1;
    private static final double MAX_SCALE = 20.0;
    private static final double ZOOM_FACTOR = 1.05;
    private static final double DRAG_THRESHOLD_PX = 4.0;
    private static final long PLAYBACK_INTERVAL_NS = 95_000_000L;

    /**
     * Environment serialization format, sensor radius, and maximum grid dimensions.
     */
    private final String delimiter = ":";

    /** Original sensing radius, in grid cells. */
    static final int DEFAULT_SENSOR_RANGE_CELLS = 4;

    /** Upper limit matching the maximum grid width. */
    static final int MAX_SENSOR_RANGE_CELLS = 400;

    private int sensorFoV = DEFAULT_SENSOR_RANGE_CELLS;

    /** Pending sensing radius shown when playback is idle or paused. */
    private int previewSensorRangeCells = DEFAULT_SENSOR_RANGE_CELLS;
    private final int MAX_NUM_ROWS = 200;
    private final int MAX_NUM_COLS = 400;

    /**
     * Camera transform and mouse-interaction state.
     */
    protected double translateX = 0.0;

    /**
     * Vertical camera offset in pixels.
     */
    protected double translateY = 0.0;

    /**
     * Current world-to-canvas scale.
     */
    protected double scale = 1.0;

    private double mouseAnchorX = 0.0;
    private double mouseAnchorY = 0.0;

    private double mousePressX = 0.0;
    private double mousePressY = 0.0;
    private boolean mousePressActive = false;
    private boolean dragGestureDetected = false;
    private boolean zoomGestureDetected = false;
    private boolean suppressNextClick = false;
    private boolean panningCamera = false;
    private MouseButton pressedMouseButton = MouseButton.NONE;

    /**
     * Drone collection, drag targets, line-editing state, and current grid geometry.
     */
    private ArrayList<Drone> drones = new ArrayList<>();

    private int draggingDrone = -1;
    private int moveToI = -1;
    private int moveToJ = -1;
    private int lineToI = -1;
    private int lineToJ = -1;
    private int positionI = -1;
    private int positionJ = -1;
    private int cellSize;
    private double halfCellSize;
    private int numRows;

    /**
     * Occupancy, obstacle display cache, and active editor mode.
     */
    private int numCols;

    private int[][] environment = null;
    private String[][] wallType = new String[0][0];
    private State state = State.DEFAULT;

    /**
     * Replay results, synchronized frame index, and playback timer.
     */
    private DecentralizedExploration lastExploration;

    private int playbackFrame = 0;
    private boolean playbackPlaying = false;
    private long lastPlaybackTick = 0;

    /**
     * Creates the grid canvas and initializes its obstacle-edge cache.
     *
     * @param environment occupancy grid indexed by row and column, with -1 marking obstacles
     * @param cellSize    display size of one grid cell in world pixels
     */
    protected CanvasView(int[][] environment, int cellSize) {
        super();
        if (environment != null
                && environment.length > 0
                && environment[0] != null
                && environment[0].length > 0
                && cellSize > 0) {
            this.environment = environment;
            numRows = environment.length;
            numCols = environment[0].length;
            this.cellSize = cellSize;
            this.halfCellSize = 0.5 * cellSize;
            wallType = new String[numRows][numCols];
            setWidth(numCols * cellSize);
            setHeight(numRows * cellSize);
            checkWallType();
        }
    }

    /**
     * Parses and validates a named integer from an environment-file header.
     *
     * @param lines lines read from the environment file
     * @param index zero-based header line index
     * @param key   expected header label before the separator
     * @return integer parsed from the header value
     * @throws IOException if the header is missing, mislabeled, or not an integer
     */
    private static int readHeaderInteger(java.util.List<String> lines, int index, String key)
            throws IOException {
        if (index >= lines.size()) throw new IOException("Missing " + key);
        String[] tokens = lines.get(index).trim().split(":");
        if (tokens.length < 2 || !tokens[0].trim().equals(key))
            throw new IOException("Expected " + key + " at line " + (index + 1));
        try {
            return Integer.parseInt(tokens[1].trim());
        } catch (NumberFormatException e) {
            throw new IOException("Invalid " + key, e);
        }
    }

    /**
     * Selects the first unused drone-path palette color, reusing colors only when necessary.
     *
     * @return index of an unused palette color, or a reusable index when all colors are in use
     */
    private int nextAvailablePathColorIndex() {
        java.util.Set<String> used = new java.util.HashSet<>();
        for (Drone drone : drones) used.add(drone.getPathColor());
        for (int i = 0; i < Drone.pathPaletteSize(); i++) {
            Drone probe = new Drone(environment, 0, 0, sensorFoV, cellSize);
            probe.setPathColorByIndex(i);
            if (!used.contains(probe.getPathColor())) return i;
        }
        return drones.size() % Drone.pathPaletteSize();
    }

    /**
     * Returns the most recent stored simulation result.
     *
     * @return the stored simulation, or {@code null} if none is available
     */
    protected DecentralizedExploration getLastExploration() {
        return lastExploration;
    }

    /**
     * Returns a read-only list view of drones used by replay.
     *
     * @return an unmodifiable view of the current drone list
     */
    protected java.util.List<Drone> getReplayDrones() {
        return java.util.Collections.unmodifiableList(drones);
    }

    /**
     * Returns the current height of the grid in cells.
     *
     * @return current number of grid rows
     */
    protected int getGridRows() {
        return numRows;
    }

    /**
     * Returns the current width of the grid in cells.
     *
     * @return current number of grid columns
     */
    protected int getGridCols() {
        return numCols;
    }

    /**
     * Returns the currently applied sensing radius.
     *
     * @return the applied sensing radius in grid cells
     */
    protected int getSensorRangeCells() {
        return sensorFoV;
    }

    /**
     * Updates the idle sensor footprint without modifying the previous simulation.
     *
     * @param cells validated sensing radius to preview
     */
    void previewSensorRangeCells(int cells) {
        previewSensorRangeCells = validateSensorRangeCells(cells);
        if (!playbackPlaying) render();
    }

    /**
     * Tests whether a simulation is available for replay.
     *
     * @return {@code true} if a simulation result is stored
     */
    protected boolean hasReplay() {
        return lastExploration != null;
    }

    /**
     * Returns the index of the last replay frame.
     *
     * @return final replay frame index, or zero if no simulation is stored
     */
    protected int totalPlaybackFrames() {
        return lastExploration == null ? 0 : lastExploration.getSteps();
    }

    /**
     * Tests whether replay is advancing.
     *
     * @return {@code true} while playback is active
     */
    protected boolean isPlaybackPlaying() {
        return playbackPlaying;
    }

    /**
     * Synchronizes the replay frame across all drone trajectories.
     *
     * @param frame requested zero-based replay frame
     */
    private void setPlaybackFrame(int frame) {
        if (lastExploration == null) return;
        playbackFrame = Math.max(0, Math.min(frame, lastExploration.getSteps()));
        for (Drone d : drones) {
            d.frameIndex = Math.min(playbackFrame, d.path.size() - 1);
        }
    }

    /**
     * Toggles playback, rewinding first if it has reached the final frame.
     */
    protected void togglePlayPause() {
        if (!hasReplay()) return;
        if (playbackFrame >= totalPlaybackFrames()) setPlaybackFrame(0);
        playbackPlaying = !playbackPlaying;
        lastPlaybackTick = System.nanoTime();
        render();
    }

    /**
     * Starts playback at the initial frame when a simulation is available.
     */
    protected void replay() {
        if (!hasReplay()) return;
        setPlaybackFrame(0);
        playbackPlaying = true;
        lastPlaybackTick = System.nanoTime();
        render();
    }

    /**
     * Discards the previous replay and resets playback timing.
     */
    private void clearReplay() {
        lastExploration = null;
        playbackPlaying = false;
        playbackFrame = 0;
        lastPlaybackTick = 0;
    }

    /**
     * Converts a screen-space horizontal coordinate into world space.
     *
     * @param screenX horizontal canvas coordinate in pixels
     * @return horizontal position before the camera transform
     */
    private double screenToWorldX(double screenX) {
        return (screenX - translateX) / scale;
    }

    /**
     * Converts a screen-space vertical coordinate into world space.
     *
     * @param screenY vertical canvas coordinate in pixels
     * @return vertical position before the camera transform
     */
    private double screenToWorldY(double screenY) {
        return (screenY - translateY) / scale;
    }

    /**
     * Computes a world-grid column from the mouse position.
     *
     * @param mouseX horizontal pointer coordinate in canvas pixels
     * @return zero-based grid column, which may be outside the map
     */
    private int getColumnFromMouse(double mouseX) {
        double worldX = screenToWorldX(mouseX);
        return (int) Math.floor(worldX / cellSize);
    }

    /**
     * Computes a world-grid row from the mouse position.
     *
     * @param mouseY vertical pointer coordinate in canvas pixels
     * @return zero-based grid row, which may be outside the map
     */
    private int getRowFromMouse(double mouseY) {
        double worldY = screenToWorldY(mouseY);
        return (int) Math.floor(worldY / cellSize);
    }

    /**
     * Tests whether screen coordinates map inside the environment bounds.
     *
     * @param mouseX horizontal pointer coordinate in canvas pixels
     * @param mouseY vertical pointer coordinate in canvas pixels
     * @return {@code true} if the point maps within the grid footprint
     */
    private boolean isMouseInsideEnvironment(double mouseX, double mouseY) {
        double worldX = screenToWorldX(mouseX), worldY = screenToWorldY(mouseY);
        return worldX >= 0
                && worldX < numCols * cellSize
                && worldY >= 0
                && worldY < numRows * cellSize;
    }

    /**
     * Tests row and column indices against the current grid dimensions.
     *
     * @param row zero-based grid row
     * @param col zero-based grid column
     * @return {@code true} if both zero-based indices are within bounds
     */
    private boolean isValidGridPosition(int row, int col) {
        return row >= 0 && row < numRows && col >= 0 && col < numCols;
    }

    /**
     * Serializes the current occupancy grid and initial drone positions to an environment file.
     *
     * @param file source or destination file
     * @throws java.io.UncheckedIOException if the environment cannot be written
     */
    protected void writeToFile(File file) {
        if (environment != null)
            try {
                StringBuffer sb =
                        new StringBuffer()
                                .append("ENVIRONMENT----------------------------------\n")
                                .append("NUM ROWS" + delimiter + environment.length + "\n")
                                .append("NUM COLS" + delimiter + environment[0].length + "\n");
                for (int i = 0; i < environment.length; i++) {
                    for (int j = 0; j < environment[i].length; j++)
                        sb.append(environment[i][j] + delimiter);
                    sb.append("\n");
                }
                if (drones != null && !drones.isEmpty()) {
                    sb.append("DRONES---------------------------------------\n")
                            .append("NUM DRONES" + delimiter + drones.size() + "\n");
                    for (int d = 0; d < drones.size(); d++)
                        sb.append(
                                drones.get(d).initialRowPosition
                                        + delimiter
                                        + drones.get(d).initialColPosition
                                        + "\n");
                }
                String data = sb.toString();
                Path filePath = file.toPath();
                Files.write(
                        filePath,
                        data.getBytes(),
                        StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING);
                state = State.DEFAULT;
            } catch (IOException e) {
                throw new java.io.UncheckedIOException("Could not save environment", e);
            }
    }

    /**
     * Validates an environment file and replaces the current grid and drone starting positions
     * atomically.
     *
     * @param file source or destination file
     * @throws IllegalArgumentException if the file cannot be read or its environment data is
     *                                  invalid
     */
    protected void readFromFile(File file) {
        try {
            java.util.List<String> lines = Files.readAllLines(file.toPath());
            int index = 0;
            if (lines.isEmpty()) throw new IOException("Empty environment file");
            if (lines.get(index).startsWith("ENVIRONMENT")) index++;
            int r = readHeaderInteger(lines, index++, "NUM ROWS");
            int c = readHeaderInteger(lines, index++, "NUM COLS");
            if (r <= 0 || c <= 0 || r > MAX_NUM_ROWS || c > MAX_NUM_COLS)
                throw new IOException("Invalid grid size: " + r + " x " + c);
            int[][] loaded = new int[r][c];
            for (int row = 0; row < r; row++) {
                if (index >= lines.size()) throw new IOException("Missing grid row " + row);
                String[] data = lines.get(index++).trim().split(":");
                if (data.length != c) throw new IOException("Incorrect column count at row " + row);
                for (int col = 0; col < c; col++) {
                    int value = Integer.parseInt(data[col].trim());
                    if (value != 0 && value != -1)
                        throw new IOException("Invalid occupancy value at " + row + "," + col);
                    loaded[row][col] = value;
                }
            }
            ArrayList<Point> starts = new ArrayList<>();
            if (index < lines.size() && lines.get(index).startsWith("DRONES")) index++;
            if (index < lines.size() && lines.get(index).startsWith("NUM DRONES")) {
                int n = readHeaderInteger(lines, index++, "NUM DRONES");
                if (n < 0 || n > r * c) throw new IOException("Invalid drone count");
                java.util.HashSet<Integer> unique = new java.util.HashSet<>();
                for (int i = 0; i < n; i++) {
                    if (index >= lines.size()) throw new IOException("Missing drone entry " + i);
                    String[] pos = lines.get(index++).trim().split(":");
                    if (pos.length < 2) throw new IOException("Invalid drone coordinate");
                    int rr = Integer.parseInt(pos[0].trim()), cc = Integer.parseInt(pos[1].trim());
                    if (rr < 0 || rr >= r || cc < 0 || cc >= c || loaded[rr][cc] == -1)
                        throw new IOException("Drone cannot start at (" + rr + "," + cc + ")");
                    if (!unique.add(rr * c + cc))
                        throw new IOException("Duplicate starting position");
                    starts.add(new Point(rr, cc));
                }
            }

            clearReplay();
            environment = loaded;
            numRows = r;
            numCols = c;
            translateX = 0;
            translateY = 0;
            scale = 1;
            setWidth(numCols * cellSize);
            setHeight(numRows * cellSize);
            drones = new ArrayList<>();
            for (Point pos : starts) {
                Drone drone = new Drone(environment, pos.row(), pos.col(), sensorFoV, cellSize);
                drone.setPathColorByIndex(nextAvailablePathColorIndex());
                drones.add(drone);
            }
            checkWallType();
            setFreeTransform();
            render();
        } catch (Exception ex) {
            throw new IllegalArgumentException(
                    "Failed to open environment: " + ex.getMessage(), ex);
        }
    }

    /**
     * Encodes the exposed sides of one obstacle cell.
     *
     * @param i zero-based grid row
     * @param j zero-based grid column
     * @return four edge flags ordered east, south, west, north, or {@code null} for a non-obstacle
     * cell
     */
    private String checkWallType(int i, int j) {
        String type = null;
        if (environment != null
                && i >= 0
                && i < environment.length
                && j >= 0
                && j < environment[0].length
                && environment[i][j] == -1) {
            type = "";

            String value = "0";
            if (j == environment[i].length - 1
                    || (j < environment[i].length - 1 && environment[i][j + 1] == 0)) value = "1";
            type += value;

            value = "0";
            if (i == environment.length - 1
                    || (i < environment.length - 1 && environment[i + 1][j] == 0)) value = "1";

            type += value;

            value = "0";
            if (j == 0 || (j > 0 && environment[i][j - 1] == 0)) value = "1";
            type += value;

            value = "0";
            if (i == 0 || (i > 0 && environment[i - 1][j] == 0)) value = "1";
            type += value;
        }

        return type;
    }

    /**
     * Rebuilds the obstacle-edge cache used by the renderer.
     */
    private void checkWallType() {
        if (environment != null) {
            wallType = new String[environment.length][];
            for (int i = 0; i < environment.length; i++) {
                wallType[i] = new String[environment[i].length];
                for (int j = 0; j < environment[i].length; j++)
                    wallType[i][j] = checkWallType(i, j);
            }
        }
    }

    /**
     * Returns the active editor interaction mode.
     *
     * @return the active editor mode
     */
    protected State getState() {
        return state;
    }

    /**
     * Sets the active editor interaction mode.
     *
     * @param state editor interaction mode
     */
    protected void setState(State state) {
        this.state = state;
    }

    /**
     * Returns the editor to free-transform mode and resets gesture state.
     */
    protected void setFreeTransform() {
        state = State.DEFAULT;
        moveToI = -1;
        moveToJ = -1;
        lineToI = -1;
        lineToJ = -1;
        positionI = -1;
        positionJ = -1;
        draggingDrone = -1;
    }

    /**
     * Restores the default grid, camera transform, and empty drone collection.
     */
    protected void resetEnvironment() {
        clearReplay();
        numRows = MAX_NUM_ROWS;
        numCols = MAX_NUM_COLS;
        translateX = 0.0;
        translateY = 0.0;
        scale = 1.0;
        setWidth(numCols * cellSize);
        setHeight(numRows * cellSize);
        environment = new int[numRows][numCols];
        wallType = new String[numRows][numCols];
        drones = new ArrayList<>();
        state = State.DEFAULT;
        moveToI = -1;
        moveToJ = -1;
        lineToI = -1;
        lineToJ = -1;
        positionI = -1;
        positionJ = -1;
        draggingDrone = -1;
        render();
    }

    /**
     * Initializes pointer coordinates and click, drag, zoom, and pan flags.
     *
     * @param mouseEvent JavaFX pointer event
     */
    private void beginPointerGesture(MouseEvent mouseEvent) {
        mousePressActive = true;
        pressedMouseButton = mouseEvent.getButton();

        mousePressX = mouseEvent.getX();
        mousePressY = mouseEvent.getY();

        mouseAnchorX = mousePressX;
        mouseAnchorY = mousePressY;

        dragGestureDetected = false;
        zoomGestureDetected = false;
        suppressNextClick = false;
        panningCamera = false;
    }

    /**
     * Marks the current gesture as a drag after its displacement exceeds the threshold.
     *
     * @param mouseX horizontal pointer coordinate in canvas pixels
     * @param mouseY vertical pointer coordinate in canvas pixels
     */
    private void updateDragGesture(double mouseX, double mouseY) {
        if (!mousePressActive || dragGestureDetected) return;

        double deltaX = mouseX - mousePressX;
        double deltaY = mouseY - mousePressY;

        if (Math.hypot(deltaX, deltaY) >= DRAG_THRESHOLD_PX) {
            dragGestureDetected = true;
            suppressNextClick = true;
        }
    }

    /**
     * Clears transient gesture-suppression flags after click processing.
     */
    private void clearCompletedClickGesture() {
        dragGestureDetected = false;
        zoomGestureDetected = false;
        suppressNextClick = false;
    }

    /**
     * Processes primary-button grid editing while rejecting drag and zoom artifacts.
     *
     * @param mouseEvent JavaFX pointer event
     */
    protected void handleMouseClicked(MouseEvent mouseEvent) {

        if (mouseEvent.getButton() != MouseButton.PRIMARY || mouseEvent.getClickCount() != 1) {
            mouseEvent.consume();
            return;
        }

        if (suppressNextClick || dragGestureDetected || zoomGestureDetected) {
            clearCompletedClickGesture();
            mouseEvent.consume();
            return;
        }

        double mouseX = mouseEvent.getX();
        double mouseY = mouseEvent.getY();

        if (!isMouseInsideEnvironment(mouseX, mouseY)) {
            clearCompletedClickGesture();
            mouseEvent.consume();
            return;
        }

        int i = getRowFromMouse(mouseY);
        int j = getColumnFromMouse(mouseX);

        if (!isValidGridPosition(i, j)) {
            clearCompletedClickGesture();
            mouseEvent.consume();
            return;
        }

        if (state.equals(State.OBSTACLE)) {
            clearReplay();
            if (environment[i][j] == 0) environment[i][j] = -1;
            else environment[i][j] = 0;
        } else if (state.equals(State.ADD_DRONE)) {
            clearReplay();
            Drone drone = new Drone(environment, i, j, sensorFoV, cellSize);
            drone.setPathColorByIndex(nextAvailablePathColorIndex());
            drones.add(drone);
        } else if (state.equals(State.REMOVE_DRONE) && !drones.isEmpty()) {
            clearReplay();
            for (int k = drones.size() - 1; k >= 0; k--) {
                Drone drone = drones.get(k);
                if (drone.initialRowPosition == i && drone.initialColPosition == j) {
                    drones.remove(k);
                    break;
                }
            }
        }

        checkWallType();
        render();
        clearCompletedClickGesture();
        mouseEvent.consume();
    }

    /**
     * Applies bounded mouse-centered zoom without changing the underlying environment.
     *
     * @param scrollEvent JavaFX scroll event
     */
    protected void handleMouseScroll(ScrollEvent scrollEvent) {

        if (mousePressActive) {
            zoomGestureDetected = true;
            suppressNextClick = true;
        }

        final double mouseX = scrollEvent.getX();
        final double mouseY = scrollEvent.getY();

        final double oldScale = scale;

        double newScale;
        if (scrollEvent.getDeltaY() > 0) {
            newScale = oldScale * ZOOM_FACTOR;
        } else if (scrollEvent.getDeltaY() < 0) {
            newScale = oldScale / ZOOM_FACTOR;
        } else {
            return;
        }

        newScale = Math.max(MIN_SCALE, Math.min(newScale, MAX_SCALE));

        if (Math.abs(newScale - oldScale) < 0.0000001) {
            scrollEvent.consume();
            return;
        }

        final double worldX = screenToWorldX(mouseX);
        final double worldY = screenToWorldY(mouseY);

        scale = newScale;

        translateX = mouseX - worldX * scale;
        translateY = mouseY - worldY * scale;

        render();
        scrollEvent.consume();
    }

    /**
     * Begins line drawing, drone dragging, or camera panning according to the selected tool.
     *
     * @param mouseEvent JavaFX pointer event
     */
    protected void handleMousePressed(MouseEvent mouseEvent) {
        beginPointerGesture(mouseEvent);

        double mouseX = mouseEvent.getX();
        double mouseY = mouseEvent.getY();
        int i = getRowFromMouse(mouseY);
        int j = getColumnFromMouse(mouseX);

        if (pressedMouseButton == MouseButton.MIDDLE) {
            panningCamera = true;
            draggingDrone = -1;
            mouseEvent.consume();
            return;
        }

        if (pressedMouseButton == MouseButton.PRIMARY
                && (state.equals(State.LINE) || state.equals(State.REMOVE_LINE))) {

            if (moveToI == -1 && moveToJ == -1 && isMouseInsideEnvironment(mouseX, mouseY)) {
                moveToI = i;
                moveToJ = j;
                lineToI = -1;
                lineToJ = -1;
            }

            mouseEvent.consume();
            return;
        }

        draggingDrone = -1;

        if (pressedMouseButton == MouseButton.PRIMARY
                && state.equals(State.DEFAULT)
                && isValidGridPosition(i, j)) {

            for (int k = 0; k < drones.size(); k++) {
                Drone drone = drones.get(k);
                if (i == drone.initialRowPosition && j == drone.initialColPosition) {
                    draggingDrone = k;
                    break;
                }
            }
        }

        mouseEvent.consume();
    }

    /**
     * Updates hover coordinates for obstacle and line-editing previews.
     *
     * @param mouseEvent JavaFX pointer event
     */
    protected void handleMouseMoved(MouseEvent mouseEvent) {
        if (state.equals(State.OBSTACLE)
                || state.equals(State.LINE)
                || state.equals(State.REMOVE_LINE)) {
            double mouseX = mouseEvent.getX();
            double mouseY = mouseEvent.getY();

            if (isMouseInsideEnvironment(mouseX, mouseY)) {
                int i = getRowFromMouse(mouseY);
                int j = getColumnFromMouse(mouseX);

                if (isValidGridPosition(i, j)) {
                    positionI = i;
                    positionJ = j;
                    render();
                }
            }
        }
    }

    /**
     * Handles camera panning, obstacle-line previews, and eligible drone repositioning.
     *
     * @param mouseEvent JavaFX pointer event
     */
    protected void handleMouseDragged(MouseEvent mouseEvent) {
        double mouseX = mouseEvent.getX();
        double mouseY = mouseEvent.getY();

        updateDragGesture(mouseX, mouseY);

        if (panningCamera && pressedMouseButton == MouseButton.MIDDLE) {
            double deltaX = mouseX - mouseAnchorX;
            double deltaY = mouseY - mouseAnchorY;

            translateX += deltaX;
            translateY += deltaY;

            mouseAnchorX = mouseX;
            mouseAnchorY = mouseY;

            render();
            mouseEvent.consume();
            return;
        }

        int i = getRowFromMouse(mouseY);
        int j = getColumnFromMouse(mouseX);

        if (pressedMouseButton == MouseButton.PRIMARY
                && (state.equals(State.OBSTACLE)
                || state.equals(State.LINE)
                || state.equals(State.REMOVE_LINE))) {

            if (moveToI != -1 && moveToJ != -1 && isMouseInsideEnvironment(mouseX, mouseY)) {
                positionI = i;
                positionJ = j;

                if (state.equals(State.LINE) || state.equals(State.REMOVE_LINE)) {
                    lineToI = i;
                    lineToJ = j;
                }
            }

            render();
            mouseEvent.consume();
            return;
        }

        if (pressedMouseButton == MouseButton.PRIMARY
                && state.equals(State.DEFAULT)
                && dragGestureDetected
                && draggingDrone >= 0
                && draggingDrone < drones.size()
                && isValidGridPosition(i, j)) {

            clearReplay();
            drones.get(draggingDrone).initialRowPosition = i;
            drones.get(draggingDrone).initialColPosition = j;
            render();
        }

        mouseEvent.consume();
    }

    /**
     * Commits line edits and resets the current pointer gesture.
     *
     * @param mouseEvent JavaFX pointer event
     */
    protected void handleMouseReleased(MouseEvent mouseEvent) {

        if (pressedMouseButton == MouseButton.PRIMARY
                && moveToI != -1
                && moveToJ != -1
                && lineToI != -1
                && lineToJ != -1) {

            clearReplay();
            drawLineOnGrid(moveToI, moveToJ, lineToI, lineToJ, state.equals(State.LINE));
            checkWallType();
        }

        moveToI = -1;
        moveToJ = -1;
        lineToI = -1;
        lineToJ = -1;
        draggingDrone = -1;

        if (dragGestureDetected || zoomGestureDetected || panningCamera) {
            suppressNextClick = true;
        }

        mousePressActive = false;
        panningCamera = false;
        pressedMouseButton = MouseButton.NONE;

        render();
        mouseEvent.consume();
    }

    /**
     * Rasterizes a grid-aligned line of obstacle additions or removals.
     *
     * @param row0        starting grid row
     * @param col0        starting grid column
     * @param row1        ending grid row
     * @param col1        ending grid column
     * @param addObstacle true to mark cells as obstacles, false to clear them
     */
    private void drawLineOnGrid(int row0, int col0, int row1, int col1, boolean addObstacle) {
        int x0 = col0,
                y0 = row0,
                x1 = col1,
                y1 = row1,
                dx = Math.abs(x1 - x0),
                dy = Math.abs(y1 - y0),
                sx = x0 < x1 ? 1 : -1,
                sy = y0 < y1 ? 1 : -1,
                err = dx - dy;
        while (true) {
            if (isValidGridPosition(y0, x0))
                if (addObstacle) environment[y0][x0] = -1;
                else environment[y0][x0] = 0;
            if (x0 == x1 && y0 == y1) break;
            int e2 = 2 * err;
            if (e2 > -dy) {
                err -= dy;
                x0 += sx;
            }
            if (e2 < dx) {
                err += dx;
                y0 += sy;
            }
        }
    }

    /**
     * Advances replay timing when playback is enabled.
     */
    protected void update() {
        if (!playbackPlaying || lastExploration == null) return;
        long now = System.nanoTime();
        if (now - lastPlaybackTick < PLAYBACK_INTERVAL_NS) return;
        lastPlaybackTick = now;
        if (playbackFrame < totalPlaybackFrames()) setPlaybackFrame(playbackFrame + 1);
        if (playbackFrame >= totalPlaybackFrames()) playbackPlaying = false;
    }

    /**
     * Draws the grid, obstacles, observed coverage, drone paths, and drone markers using camera
     * transforms.
     */
    protected void render() {
        if (environment == null) return;
        halfCellSize = 0.5 * cellSize;
        GraphicsContext gc = this.getGraphicsContext2D();
        gc.setImageSmoothing(true);
        gc.setFontSmoothingType(FontSmoothingType.LCD);
        gc.clearRect(0, 0, getWidth(), getHeight());
        gc.save();

        gc.translate(translateX, translateY);
        gc.scale(scale, scale);

        gc.setFill(Color.valueOf("#F7F7F7"));

        gc.fillRect(0, 0, numCols * cellSize, numRows * cellSize);

        for (int i = 0; i <= numRows; i++) {
            gc.setLineWidth(0.2);
            gc.setStroke(Color.valueOf("#929AAB"));
            if (i % 10 == 0) {
                gc.setLineWidth(0.6);
                gc.setStroke(Color.valueOf("#929AAB"));
            }
            gc.strokeLine(0, i * cellSize, numCols * cellSize, i * cellSize);
        }

        for (int i = 0; i <= numCols; i++) {
            gc.setLineWidth(0.2);
            gc.setStroke(Color.valueOf("#929AAB"));
            if (i % 10 == 0) {
                gc.setLineWidth(0.6);
                gc.setStroke(Color.valueOf("#929AAB"));
            }
            gc.strokeLine(i * cellSize, 0, i * cellSize, numRows * cellSize);
        }

        gc.setGlobalAlpha(0.15);
        for (int i = 0; i < environment.length; i++) {
            for (int j = 0; j < environment[i].length; j++) {
                if (environment[i][j] == -1) {
                    double xo = j * cellSize, yo = i * cellSize;
                    gc.setFill(Color.valueOf("#505050"));
                    gc.fillRect(xo + 3, yo + 3, cellSize, cellSize);
                }
            }
        }
        gc.setGlobalAlpha(1.0);

        for (int i = 0; i < environment.length; i++) {
            for (int j = 0; j < environment[i].length; j++) {
                if (environment[i][j] == -1) {
                    double xo = j * cellSize, yo = i * cellSize;
                    gc.setFill(Color.valueOf("#03A6A1"));
                    gc.fillRect(xo, yo, cellSize, cellSize);
                }
            }
        }

        for (int i = 0; i < environment.length; i++) {
            for (int j = 0; j < environment[i].length; j++) {
                if (environment[i][j] == -1) {
                    double xo = j * cellSize, yo = i * cellSize;
                    gc.setStroke(Color.WHITE);
                    gc.setLineWidth(0.3);
                    gc.strokeLine(xo, yo, xo + cellSize, yo + cellSize);
                    gc.strokeLine(
                            xo + halfCellSize, yo, xo + cellSize, yo + cellSize - halfCellSize);
                    gc.strokeLine(
                            xo, yo + halfCellSize, xo + cellSize - halfCellSize, yo + cellSize);

                    if (wallType != null
                            && i < wallType.length
                            && wallType[i] != null
                            && j < wallType[i].length
                            && wallType[i][j] != null
                            && wallType[i][j].length() >= 4) {
                        gc.setLineWidth(0.6);
                        String type = wallType[i][j];

                        char value = type.charAt(0);
                        if (value == '1')
                            gc.strokeLine(xo + cellSize, yo, xo + cellSize, yo + cellSize);

                        value = type.charAt(1);
                        if (value == '1')
                            gc.strokeLine(xo, yo + cellSize, xo + cellSize, yo + cellSize);

                        value = type.charAt(2);
                        if (value == '1') gc.strokeLine(xo, yo, xo, yo + cellSize);

                        value = type.charAt(3);
                        if (value == '1') gc.strokeLine(xo, yo, xo + cellSize, yo);
                    }
                }
            }
        }

        gc.setGlobalAlpha(1.0);
        for (int i = 0; i <= numRows; i++) {
            for (int j = 0; j <= numCols; j++) {
                double xo = j * cellSize, yo = i * cellSize;
                gc.setFill(Color.valueOf("#929AAB"));
                gc.fillOval(xo - 0.4, yo - 0.4, 0.8, 0.8);
            }
        }
        gc.setGlobalAlpha(1.0);

        if (moveToI != -1 && moveToJ != -1 && lineToI != -1 && lineToJ != -1) {
            double x0 = moveToJ * cellSize + halfCellSize,
                    y0 = moveToI * cellSize + halfCellSize,
                    x1 = lineToJ * cellSize + halfCellSize,
                    y1 = lineToI * cellSize + halfCellSize;
            gc.setStroke(Color.valueOf("#03A6A1"));
            if (state.equals(State.REMOVE_LINE)) gc.setStroke(Color.RED);
            gc.setLineWidth(0.8);
            gc.strokeLine(x0, y0, x1, y1);
        }

        if (lastExploration != null) {
            int[][] knownAt = lastExploration.getFirstObservedAt();
            boolean[][] exploreArea = lastExploration.getMustExploreMask();
            gc.setFill(Color.valueOf("#263241"));
            gc.setGlobalAlpha(0.76);
            for (int r = 0; r < numRows; r++)
                for (int c = 0; c < numCols; c++) {
                    if (exploreArea[r][c]
                            && environment[r][c] != -1
                            && (knownAt[r][c] == -1 || knownAt[r][c] > playbackFrame))
                        gc.fillRect(c * cellSize, r * cellSize, cellSize, cellSize);
                }
            gc.setGlobalAlpha(1.0);
        }

        gc.setFill(Color.valueOf("#79CDB2"));
        gc.setGlobalAlpha(0.20);
        for (Drone drone : drones) {
            if (drone.path == null) continue;
            int last = Math.min(Math.max(drone.frameIndex, 0), drone.path.size() - 1);
            for (int k = 0; k <= last; k++) {
                Point visited = drone.path.get(k);
                gc.fillRect(visited.col() * cellSize, visited.row() * cellSize, cellSize, cellSize);
            }
        }
        gc.setGlobalAlpha(1.0);

        if (drones != null && !drones.isEmpty()) {
            for (int i = 0; i < drones.size(); i++) {
                ArrayList<Point> path = drones.get(i).path;
                if (path != null && !path.isEmpty() && path.size() > 1) {
                    for (int k = 1; k <= Math.min(drones.get(i).frameIndex, path.size() - 1); k++) {
                        Point point0 = path.get(k - 1), point1 = path.get(k);
                        double x0 = (double) point0.col() * cellSize + halfCellSize,
                                y0 = (double) point0.row() * cellSize + halfCellSize,
                                x1 = (double) point1.col() * cellSize + halfCellSize,
                                y1 = (double) point1.row() * cellSize + halfCellSize;
                        gc.setStroke(Color.valueOf(drones.get(i).getPathColor()));
                        gc.setLineWidth(0.8);
                        gc.strokeLine(x0, y0, x1, y1);
                    }
                }
            }
        }

        if (drones != null && !drones.isEmpty())
            for (Drone drone : drones) {
                if (playbackPlaying || previewSensorRangeCells == drone.sensorFoV) {
                    drone.draw(gc);
                } else {
                    drone.draw(gc, previewSensorRangeCells);
                }
            }

        if ((state.equals(State.OBSTACLE)
                || state.equals(State.LINE)
                || state.equals(State.REMOVE_LINE))
                && isValidGridPosition(positionI, positionJ)) {
            double px = positionJ * cellSize + halfCellSize,
                    py = positionI * cellSize + halfCellSize;
            String textPosition = "(" + (positionI + 1) + "," + (positionJ + 1) + ")";
            gc.setFont(Font.font("Arial", 8));
            gc.setFill(Color.BLACK);
            gc.fillText(textPosition, px, py + 2 + 3 * cellSize);
        }

        gc.restore();
    }

    /**
     * Runs exploration using settings read from JVM properties.
     */
    protected void run() {
        run(ExplorationConfig.fromSystemProperties());
    }

    /**
     * Runs exploration with the supplied settings and starts replay when drones are present.
     *
     * @param config validated settings for this run
     */
    protected void run(ExplorationConfig config) {
        run(config, sensorFoV);
    }

    /**
     * Applies one sensor radius to all drones before starting a new simulation.
     *
     * @param config validated settings for this run
     * @param sensorRangeCells sensing radius in grid cells
     * @throws IllegalArgumentException if the sensing radius is outside the supported range
     */
    protected void run(ExplorationConfig config, int sensorRangeCells) {
        sensorFoV = validateSensorRangeCells(sensorRangeCells);
        previewSensorRangeCells = sensorFoV;
        for (Drone drone : drones) {
            drone.sensorFoV = sensorFoV;
        }

        for (int i = 0; i < drones.size(); i++) drones.get(i).setName("D" + (i + 1));
        clearReplay();
        if (!drones.isEmpty()) {
            lastExploration = new DecentralizedExploration(environment, drones, config);
            setPlaybackFrame(0);
            playbackPlaying = true;
            lastPlaybackTick = System.nanoTime();
        }
        render();
    }

    /**
     * Validates the sensing radius shared by all drones in a run.
     *
     * @param cells proposed sensing radius in grid cells
     * @return the validated radius
     * @throws IllegalArgumentException if the radius is outside the supported range
     */
    static int validateSensorRangeCells(int cells) {
        if (cells < 1 || cells > MAX_SENSOR_RANGE_CELLS) {
            throw new IllegalArgumentException(
                    "Sensor range must be between 1 and " + MAX_SENSOR_RANGE_CELLS + " cells.");
        }
        return cells;
    }
}
