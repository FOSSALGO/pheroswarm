package fosalgo.pheroswarm;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.Locale;

/**
 * JavaFX form for validating baseline and repulsive-pheromone exploration settings.
 */
public final class AlgorithmPropertiesPane extends VBox {
    /**
     * Editable experimental parameters and associated form feedback.
     */
    private final ComboBox<String> method = new ComboBox<>();

    private final TextField maxSteps = field("2500");
    private final TextField stallLimit = field("30");
    private final TextField lambda = field("1.0");
    private final TextField rho = field("0.05");
    private final TextField deposit = field("1.0");
    private final CheckBox recovery = new CheckBox("Enable collision/stall recovery");
    private final Label notice = new Label("Changes take effect on the next Run.");

    /**
     * Creates the experiment editor and loads initial values from JVM properties or the built-in
     * defaults.
     */
    public AlgorithmPropertiesPane() {
        setSpacing(14);
        setPadding(new Insets(18, 22, 22, 22));
        getStyleClass().add("algorithm-properties");
        setFillWidth(true);

        ExplorationConfig initial;
        try {
            initial = ExplorationConfig.fromSystemProperties();
        } catch (RuntimeException ex) {
            initial = ExplorationConfig.defaults();
        }
        maxSteps.setText(Integer.toString(initial.maxSteps()));
        stallLimit.setText(Integer.toString(initial.stallLimit()));
        lambda.setText(Double.toString(initial.lambda() == 0.0 ? 1.0 : initial.lambda()));
        rho.setText(Double.toString(initial.rho()));
        deposit.setText(Double.toString(initial.deposit()));
        recovery.setSelected(initial.recovery());

        Label header = new Label("Algorithm Properties");
        header.getStyleClass().add("properties-title");
        Label subtitle = new Label("Configure an experiment before running the simulation.");
        subtitle.getStyleClass().add("properties-help");
        getChildren().addAll(header, subtitle);

        method.getItems()
                .addAll("M0 — Frontier-Based Exploration", "M1 — FBE + Repulsive Pheromone");
        method.getSelectionModel().select(initial.lambda() == 0 ? 0 : 1);
        method.setMaxWidth(Double.MAX_VALUE);
        method.getStyleClass().add("properties-combo");
        method.valueProperty().addListener((obs, oldValue, newValue) -> updateMethodState());

        VBox modelSection =
                section(
                        "EXPLORATION METHOD",
                        "Choose the baseline (M0) or pheromone-aware method (M1).",
                        row(
                                "Algorithm variant",
                                method,
                                "M0 fixes lambda to zero; M1 uses the configured lambda value."));
        VBox runtimeSection =
                section(
                        "SIMULATION CONTROL",
                        "Stopping criteria shared by both algorithms.",
                        row("Maximum steps", maxSteps, "Maximum number of simulation timesteps."),
                        row(
                                "Stall limit",
                                stallLimit,
                                "Consecutive timesteps without movement before termination."));
        VBox pheromoneSection =
                section(
                        "REPULSIVE PHEROMONE",
                        "For paired M0/M1 experiments, keep rho and deposit identical.",
                        row(
                                "Lambda (λ)",
                                lambda,
                                "Strength of pheromone penalty in frontier selection."),
                        row(
                                "Evaporation (ρ)",
                                rho,
                                "Fraction of pheromone evaporated per timestep (0–1)."),
                        row(
                                "Deposit",
                                deposit,
                                "Pheromone added at a destination after a successful move."));
        recovery.getStyleClass().add("properties-toggle");
        VBox behaviorSection =
                section(
                        "RECOVERY",
                        "Keep this option unchanged when comparing M0 and M1.",
                        recovery);
        getChildren().addAll(modelSection, runtimeSection, pheromoneSection, behaviorSection);

        Button reset = new Button("Reset Defaults");
        reset.getStyleClass().add("properties-secondary-btn");
        reset.setOnAction(e -> resetDefaults());
        notice.getStyleClass().add("properties-notice");
        notice.setWrapText(true);
        HBox footer = new HBox(14, reset, notice);
        footer.setAlignment(Pos.CENTER_LEFT);
        getChildren().add(footer);
        updateMethodState();
    }

    /**
     * Builds a styled text field populated with a default value.
     *
     * @param value initial text for the field
     * @return a styled text field initialized with the supplied text
     */
    private static TextField field(String value) {
        TextField result = new TextField(value);
        result.setPrefColumnCount(10);
        result.getStyleClass().add("properties-input");
        return result;
    }

    /**
     * Creates a grouped algorithm-property section with a title and explanatory hint.
     *
     * @param title    heading or dialog title
     * @param hint     help text shown below the heading
     * @param controls controls displayed in the section
     * @return a vertical container for the heading, hint, and controls
     */
    private static VBox section(String title, String hint, javafx.scene.Node... controls) {
        Label heading = new Label(title);
        heading.getStyleClass().add("properties-section-title");
        Label description = new Label(hint);
        description.getStyleClass().add("properties-help");
        description.setWrapText(true);
        VBox box = new VBox(9, heading, description);
        box.getChildren().addAll(controls);
        box.getStyleClass().add("properties-card");
        box.setFillWidth(true);
        return box;
    }

    /**
     * Lays out a property label and its input control with a shared tooltip.
     *
     * @param label   label displayed for the input or used in error messages
     * @param editor  input control for the property
     * @param tooltip text displayed in the control tooltip
     * @return a horizontal container holding the label and its input
     */
    private static HBox row(String label, Control editor, String tooltip) {
        Label name = new Label(label);
        name.getStyleClass().add("properties-field-label");
        name.setMinWidth(150);
        name.setPrefWidth(180);
        name.setTooltip(new Tooltip(tooltip));
        editor.setTooltip(new Tooltip(tooltip));
        HBox line = new HBox(14, name, editor);
        line.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(editor, Priority.ALWAYS);
        editor.setMaxWidth(Double.MAX_VALUE);
        return line;
    }

    /**
     * Disables lambda editing for M0 and updates the method-specific explanatory notice.
     */
    private void updateMethodState() {
        boolean m0 = method.getSelectionModel().getSelectedIndex() == 0;
        lambda.setDisable(m0);
        lambda.setPromptText(m0 ? "Fixed at 0.0 for M0" : "e.g. 1.0");
        notice.setText(
                m0
                        ? "M0 uses λ = 0.0. Other parameters remain configurable."
                        : "M1 uses λ > 0. Settings apply on the next Run.");
    }

    /**
     * Restores the research baseline parameter values and selects M1.
     */
    private void resetDefaults() {
        method.getSelectionModel().select(1);
        maxSteps.setText("2500");
        stallLimit.setText("30");
        lambda.setText("1.0");
        rho.setText("0.05");
        deposit.setText("1.0");
        recovery.setSelected(true);
        updateMethodState();
    }

    /**
     * Parses an integer from a property field. Range checks occur during configuration validation.
     *
     * @param editor input control for the property
     * @param label  label displayed for the input or used in error messages
     * @return the parsed integer, before range validation
     * @throws IllegalArgumentException if the field is not a valid integer
     */
    private int positiveInteger(TextField editor, String label) {
        try {
            return Integer.parseInt(editor.getText().trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(label + " must be an integer", e);
        }
    }

    /**
     * Parses a decimal value while tolerating comma-based decimal input.
     *
     * @param editor input control for the property
     * @param label  label displayed for the input or used in error messages
     * @return the parsed decimal value, before range validation
     * @throws IllegalArgumentException if the field is not a valid decimal
     */
    private double decimal(TextField editor, String label) {
        try {
            return Double.parseDouble(editor.getText().trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(label + " must be a number", e);
        }
    }

    /**
     * Validates the current form and returns an immutable experiment configuration.
     *
     * @return the immutable configuration used for the next run
     * @throws IllegalArgumentException if any active property is invalid
     */
    public ExplorationConfig snapshot() {
        boolean m0 = method.getSelectionModel().getSelectedIndex() == 0;
        double actualLambda = m0 ? 0.0 : decimal(lambda, "Lambda");
        if (!m0 && actualLambda <= 0)
            throw new IllegalArgumentException("M1 requires lambda > 0. Select M0 for lambda = 0.");

        ExplorationConfig settings =
                new ExplorationConfig(
                        positiveInteger(maxSteps, "Maximum steps"),
                        positiveInteger(stallLimit, "Stall limit"),
                        actualLambda,
                        decimal(rho, "Evaporation"),
                        decimal(deposit, "Deposit"),
                        recovery.isSelected(),
                        System.getProperty("swarm.trace", ""),
                        Integer.getInteger("swarm.traceLast", 0));
        notice.setText(
                String.format(
                        Locale.ROOT,
                        "%s ready • λ=%s • ρ=%s • deposit=%s",
                        settings.mode(),
                        settings.lambda(),
                        settings.rho(),
                        settings.deposit()));
        return settings;
    }
}
