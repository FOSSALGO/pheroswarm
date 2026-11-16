package fosalgo.pheroswarm;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

import java.util.ArrayList;

/**
 * Visual drone representation, local sensor radius, and replay trajectory container.
 */
public class Drone {

    /**
     * Stable color palette shared by drone trajectory renderers.
     */
    private static final String[] PATH_COLORS = {
            "#FF204E", "#00C2FF", "#FFB000", "#7D3CFF", "#00C878",
            "#FF5E00", "#EC00B9", "#0075FF", "#A4D600", "#FF4466",
            "#00A9A5", "#FF8C00", "#9D00FF", "#20BF55", "#FF006E",
            "#00B4D8", "#D95F02", "#8F00FF", "#5BA000", "#E63946",
            "#4361EE", "#F77F00", "#7209B7", "#00B894", "#FB5607",
            "#3A86FF", "#E500A4", "#6A994E", "#F94144", "#00A6FB"
    };

    /**
     * Renderer state, drone initial location, sensor footprint, and replay trajectory.
     */
    protected final int[][] environment;

    /**
     * Starting cell coordinates, indexed from zero.
     */
    protected int initialRowPosition, initialColPosition;

    /**
     * Constructor-initialized position used before replay coordinates are selected.
     */
    protected int rowPosition, colPosition;

    /**
     * Sensing radius in cells and display size of one cell in pixels.
     */
    protected int sensorFoV, cellSize;

    /**
     * Display label for this drone.
     */
    protected String name = "";

    /**
     * Replay path containing the start followed by one position per step.
     */
    protected ArrayList<Point> path = new ArrayList<>();

    /**
     * Current index into the replay path, or a negative value before replay.
     */
    protected int frameIndex = -1;

    private String pathColor = PATH_COLORS[0];

    /**
     * Creates a visual drone at its starting cell and initializes its replay path.
     *
     * @param environment occupancy grid indexed by row and column, with -1 marking obstacles
     * @param row         zero-based grid row
     * @param col         zero-based grid column
     * @param sensorFoV   simulated sensor radius in grid cells
     * @param cellSize    display size of one grid cell in world pixels
     */
    public Drone(int[][] environment, int row, int col, int sensorFoV, int cellSize) {
        this.environment = environment;
        this.initialRowPosition = row;
        this.initialColPosition = col;
        this.rowPosition = row;
        this.colPosition = col;
        this.sensorFoV = sensorFoV;
        this.cellSize = cellSize;
        path.add(new Point(row, col));
    }

    /**
     * Returns the number of available drone-path palette colors.
     *
     * @return number of colors in the path palette
     */
    static int pathPaletteSize() {
        return PATH_COLORS.length;
    }

    /**
     * Assigns the display name of the drone.
     *
     * @param name label displayed next to the drone
     */
    protected void setName(String name) {
        this.name = name;
    }

    /**
     * Assigns a stable path color from the predefined palette.
     *
     * @param index palette index, wrapped modulo the palette size
     */
    void setPathColorByIndex(int index) {
        pathColor = PATH_COLORS[Math.floorMod(index, PATH_COLORS.length)];
    }

    /**
     * Returns the drone's assigned path color.
     *
     * @return the assigned CSS color string
     */
    String getPathColor() {
        return pathColor;
    }

    /**
     * Renders the drone sprite, sensing footprint, and position label for the selected replay
     * frame.
     *
     * @param gc graphics context in the current world-space transform
     */
    protected void draw(GraphicsContext gc) {
        draw(gc, sensorFoV);
    }

    /**
     * Renders the same sprite with an optional preview radius, without changing simulation state.
     *
     * @param gc               graphics context in world space
     * @param sensorRangeCells sensing radius to draw in grid cells
     */
    void draw(GraphicsContext gc, int sensorRangeCells) {
        int renderRow = rowPosition, renderCol = colPosition;
        if (frameIndex < 0 || path == null || path.isEmpty() || frameIndex >= path.size()) {
            renderRow = initialRowPosition;
            renderCol = initialColPosition;
        } else {
            renderRow = path.get(frameIndex).row();
            renderCol = path.get(frameIndex).col();
        }

        double halfCellSize = 0.5 * cellSize;
        double droneX = (double) renderCol * cellSize + halfCellSize;
        double droneY = (double) renderRow * cellSize + halfCellSize;
        if (droneX >= 0 && droneY >= 0) {
            gc.setFill(Color.valueOf("#FF7D29"));
            if (sensorRangeCells == sensorFoV) {
                drawSensoreVisibility(gc, droneX, droneY);
            } else {
                drawSensoreVisibility(gc, droneX, droneY, sensorRangeCells);
            }

            gc.setFill(Color.BLACK);
            gc.fillRect(droneX - 1.5, droneY - 1.5, 3, 3);
            gc.setLineWidth(0.4);
            gc.setStroke(Color.BLACK);
            double rx1 = droneX - 4;
            double ry1 = droneY - 4;
            double rx2 = droneX + 4;
            double ry2 = droneY + 4;
            double rx3 = droneX - 4;
            double ry3 = droneY + 4;
            double rx4 = droneX + 4;
            double ry4 = droneY - 4;
            gc.strokeLine(rx1, ry1, rx2, ry2);
            gc.strokeLine(rx3, ry3, rx4, ry4);

            double rPropeller = 3;
            gc.strokeOval(rx1 - rPropeller, ry1 - rPropeller, 2 * rPropeller, 2 * rPropeller);
            gc.strokeOval(rx2 - rPropeller, ry2 - rPropeller, 2 * rPropeller, 2 * rPropeller);
            gc.strokeOval(rx3 - rPropeller, ry3 - rPropeller, 2 * rPropeller, 2 * rPropeller);
            gc.strokeOval(rx4 - rPropeller, ry4 - rPropeller, 2 * rPropeller, 2 * rPropeller);

            gc.setGlobalAlpha(1.0);
            gc.setFont(Font.font("Arial", 8));

            gc.setFill(Color.valueOf("#FF0087"));
            gc.fillText(
                    name + " (" + (renderRow + 1) + "," + (renderCol + 1) + ")",
                    droneX + cellSize,
                    droneY - cellSize);
        }
    }

    /**
     * Draws 360-degree ray-sampled sensor visibility clipped by environment obstacles.
     *
     * @param gc graphics context in the current world-space transform
     * @param sx world-space horizontal origin in pixels
     * @param sy world-space vertical origin in pixels
     */
    protected void drawSensoreVisibility(GraphicsContext gc, double sx, double sy) {
        drawSensoreVisibility(gc, sx, sy, sensorFoV);
    }

    /**
     * Draws the occluded 360-degree footprint for the given radius.
     *
     * @param gc               graphics context in world space
     * @param sx               horizontal sensor origin in pixels
     * @param sy               vertical sensor origin in pixels
     * @param sensorRangeCells sensing radius to draw in grid cells
     */
    private void drawSensoreVisibility(GraphicsContext gc, double sx, double sy, int sensorRangeCells) {
        int numRows = environment.length;
        int numCols = environment[0].length;
        int rays = 360;
        double[] xPoints = new double[rays];
        double[] yPoints = new double[rays];
        double cSize = cellSize;
        double hcSize = 0.5 * cSize;

        for (int i = 0; i < rays; i++) {
            double angle = Math.toRadians(i);
            double dx = Math.cos(angle);
            double dy = Math.sin(angle);

            double x = sx;
            double y = sy;
            double step = cSize * 0.20;
            double checked = 0;
            double MAX_RADIUS = (double) sensorRangeCells * cSize + hcSize;
            while (checked < MAX_RADIUS) {
                x += dx * step;
                y += dy * step;
                checked += step;
                int col = (int) (x / cSize);
                int row = (int) (y / cSize);
                if (row < 0
                        || row >= numRows
                        || col < 0
                        || col >= numCols
                        || environment[row][col] == -1) {
                    break;
                }
            }
            xPoints[i] = x;
            yPoints[i] = y;
        }
        gc.setGlobalAlpha(0.3);
        gc.fillPolygon(xPoints, yPoints, rays);
        gc.setGlobalAlpha(1.0);
    }
}
