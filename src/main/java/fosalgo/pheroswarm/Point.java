package fosalgo.pheroswarm;

/**
 * Represents an immutable cell in the simulation grid.
 *
 * @param row zero-based grid row
 * @param col zero-based grid column
 */
public record Point(int row, int col) {

    /**
     * Checks equality of immutable row and column coordinates.
     *
     * @param obj object to compare with this point
     * @return {@code true} if the object is a point with the same row and column
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Point(int row1, int col1))) return false;
        return row == row1 && col == col1;
    }
}
