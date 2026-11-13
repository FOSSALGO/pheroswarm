package fosalgo.pheroswarm;

/**
 * User-interface modes for environment editing and free navigation.
 */
public enum State {
    /**
     * Navigates the grid and repositions drones.
     */
    DEFAULT,

    /**
     * Toggles an obstacle cell.
     */
    OBSTACLE,

    /**
     * Draws a line of obstacle cells.
     */
    LINE,

    /**
     * Clears obstacle cells along a line.
     */
    REMOVE_LINE,

    /**
     * Places a drone on the grid.
     */
    ADD_DRONE,

    /**
     * Removes a drone from the grid.
     */
    REMOVE_DRONE
}
