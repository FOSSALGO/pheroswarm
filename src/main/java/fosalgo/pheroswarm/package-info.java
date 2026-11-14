/**
 * Provides PheroSwarm, a JavaFX simulator for decentralized drone swarm coverage on 2D grids.
 *
 * <p>{@link fosalgo.pheroswarm.Launcher} starts the application. The editor and replay are managed by
 * {@link fosalgo.pheroswarm.MainView} and {@link fosalgo.pheroswarm.CanvasView}. The simulation engine,
 * {@link fosalgo.pheroswarm.DecentralizedExploration}, selects frontiers using local occupancy maps
 * and, in M1, a penalty derived from a shared digital pheromone field.
 *
 * <p>{@link fosalgo.pheroswarm.ExplorationConfig} defines the settings for each run. M0 sets the
 * pheromone weight to zero; M1 uses a positive weight. Coordinates are zero-based and indexed by
 * row and column. The grid is a discrete experimental model, not a flight dynamics simulator.
 */
package fosalgo.pheroswarm;
