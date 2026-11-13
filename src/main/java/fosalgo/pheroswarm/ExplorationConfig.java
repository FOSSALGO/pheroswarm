package fosalgo.pheroswarm;

/**
 * Stores validated parameters for one simulation run.
 *
 * <p>Use {@link #defaults()} for the built-in settings or {@link #fromSystemProperties()} for JVM
 * overrides.
 *
 * @param maxSteps   maximum simulation steps, from 1 to 1,000,000
 * @param stallLimit maximum consecutive steps without movement, from 1 to maxSteps
 * @param lambda     nonnegative weight of the digital pheromone penalty; zero selects M0
 * @param rho        pheromone fraction evaporated per step, between 0 and 1
 * @param deposit    nonnegative amount of pheromone deposited at a successful move destination
 * @param recovery   whether frontier exhaustion and alternate-move recovery are enabled
 * @param traceFile  destination for the diagnostic CSV, or an empty string to disable export
 * @param traceLast  maximum number of trailing steps to export; zero writes the full trace
 */
public record ExplorationConfig(
        int maxSteps,
        int stallLimit,
        double lambda,
        double rho,
        double deposit,
        boolean recovery,
        String traceFile,
        int traceLast) {
    /**
     * Creates a configuration and validates the parameter ranges.
     *
     * @param maxSteps   maximum simulation steps, from 1 to 1,000,000
     * @param stallLimit maximum consecutive steps without movement, from 1 to maxSteps
     * @param lambda     nonnegative weight of the digital pheromone penalty; zero selects M0
     * @param rho        pheromone fraction evaporated per step, between 0 and 1
     * @param deposit    nonnegative amount of pheromone deposited at a successful move destination
     * @param recovery   whether frontier exhaustion and alternate-move recovery are enabled
     * @param traceFile  destination for the diagnostic CSV, or an empty string to disable export
     * @param traceLast  maximum number of trailing steps to export; zero writes the full trace
     * @throws IllegalArgumentException if a numeric parameter is outside its permitted range
     */
    public ExplorationConfig {
        if (maxSteps < 1 || maxSteps > 1_000_000)
            throw new IllegalArgumentException("Max steps must be 1..1,000,000");
        if (stallLimit < 1 || stallLimit > maxSteps)
            throw new IllegalArgumentException("Stall limit must be between 1 and max steps");
        if (!Double.isFinite(lambda) || lambda < 0 || lambda > 1_000_000)
            throw new IllegalArgumentException(
                    "Lambda must be finite and >= 0 (maximum 1,000,000)");
        if (!Double.isFinite(rho) || rho < 0 || rho > 1)
            throw new IllegalArgumentException("Rho must be between 0 and 1");
        if (!Double.isFinite(deposit) || deposit < 0 || deposit > 1_000_000)
            throw new IllegalArgumentException(
                    "Deposit must be finite and >= 0 (maximum 1,000,000)");
        if (traceLast < 0) throw new IllegalArgumentException("Trace last cannot be negative");
        if (traceFile == null) traceFile = "";
    }

    /**
     * Creates the built-in configuration for M1.
     *
     * @return the built-in M1 configuration with tracing disabled
     */
    public static ExplorationConfig defaults() {
        return new ExplorationConfig(2500, 30, 1.0, 0.05, 1.0, true, "", 0);
    }

    /**
     * Reads legacy JVM swarm properties for backward-compatible headless execution.
     *
     * @return validated settings resolved from JVM properties and defaults
     * @throws IllegalArgumentException if a supplied decimal property cannot be parsed or the
     *                                  configuration fails validation
     */
    public static ExplorationConfig fromSystemProperties() {
        return new ExplorationConfig(
                Integer.getInteger("swarm.maxSteps", 2500),
                Integer.getInteger("swarm.stallLimit", 30),
                Double.parseDouble(System.getProperty("swarm.lambda", "1.0")),
                Double.parseDouble(System.getProperty("swarm.rho", "0.05")),
                Double.parseDouble(System.getProperty("swarm.deposit", "1.0")),
                Boolean.parseBoolean(System.getProperty("swarm.recovery", "true")),
                System.getProperty("swarm.trace", ""),
                Integer.getInteger("swarm.traceLast", 0));
    }

    /**
     * Classifies a zero-lambda configuration as M0 and a positive-lambda configuration as M1.
     *
     * @return {@code "M0"} for zero lambda, otherwise {@code "M1"}
     */
    public String mode() {
        return lambda == 0.0 ? "M0" : "M1";
    }
}
