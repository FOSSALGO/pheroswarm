package fosalgo.pheroswarm;

/**
 * Built-in JavaFX stylesheet for the simulator application.
 */
public class CSS {
    /**
     * JavaFX stylesheet covering the original interface and collapsible workspace.
     */
    public static final String style =
            "data:text/css,"
                    + ".root {-fx-background-color: #EEEEEE;}"
                    + ".title-bar { -fx-background-color: #27282b; }"
                    + ".title-label { -fx-text-fill: #dfe1e5; -fx-font-size: 14; }"
                    + ".control-btn { -fx-background-color: transparent; -fx-text-fill: white; -fx-font-size: 12px; }"
                    + ".control-btn:hover { -fx-background-color: #555; -fx-text-fill: white; }"
                    + ".info-bar { -fx-background-color: #2b2d30; }"
                    + ".info-label { -fx-text-fill: #8c8a8c; -fx-font-size: 10; }"
                    + ".sidebar { -fx-background-color: #EEEEEE; -fx-pref-width: 10; }"
                    + ".canvas-pane { -fx-background-color: #F7F7F7; }"
                    + ".center-pane { "
                    + "-fx-border-color: #929AAB;"
                    + "-fx-border-width: 1;"
                    + "-fx-border-radius: 2;"
                    + "-fx-background-color: white;"
                    + "-fx-border-insets: 0;"
                    + "-fx-background-insets: 0;"
                    + "}"
                    + ".scrollpane { -fx-background-color: #929AAB; }"
                    + ".scrollpane .scroll-bar { -fx-background-color: #EEEEEE; }"
                    + ".scrollpane .scroll-bar .thumb { -fx-background-color: #555555; -fx-background-radius: 4; }"
                    + ".scrollpane .scroll-bar .thumb:hover { -fx-background-color: #777777; }"
                    + ".scrollpane .increment-button, .scrollpane .decrement-button { -fx-background-color: #EEEEEE; }"
                    + ".scrollpane .increment-arrow, .scrollpane .decrement-arrow { -fx-background-color: transparent; }"
                    + ".scrollpane .corner { -fx-background-color: #929AAB; }"
                    + ".split-pane { -fx-background-color: #EEEEEE; }"
                    + ".split-pane-divider { -fx-background-color: #929AAB; }"
                    + ".status-bar { -fx-background-color: #EEEEEE; }"
                    + ".status-label { -fx-text-fill: #393E46; }"
                    + ".workspace-tabs { -fx-background-color: #F6F8FA; }"
                    + ".workspace-tabs .tab-header-area .tab-header-background { -fx-background-color: #E8EDF1; }"
                    + ".workspace-tabs .tab { -fx-background-color: #E8EDF1; -fx-background-radius: 7 7 0 0; -fx-padding: 7 17 7 17; }"
                    + ".workspace-tabs .tab:selected { -fx-background-color: white; }"
                    + ".workspace-tabs .tab-label { -fx-text-fill: #4D5C66; -fx-font-size: 12px; -fx-font-weight: bold; }"
                    + ".workspace-tabs .tab:selected .tab-label { -fx-text-fill: #007E84; }"
                    + ".algorithm-properties { -fx-background-color: #F6F8FA; }"
                    + ".properties-title { -fx-font-size: 17px; -fx-font-weight: bold; -fx-text-fill: #22313C; }"
                    + ".properties-help { -fx-font-size: 11px; -fx-text-fill: #778691; }"
                    + ".properties-card { -fx-background-color: white; -fx-background-radius: 9; -fx-border-radius: 9; -fx-border-color: #E0E6EB; -fx-padding: 13 17 15 17; }"
                    + ".properties-section-title { -fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #007E84; }"
                    + ".properties-field-label { -fx-font-size: 12px; -fx-text-fill: #354552; }"
                    + ".properties-input, .properties-combo { -fx-background-color: #F7FAFC; -fx-border-color: #CFD9DF; -fx-border-radius: 5; -fx-background-radius: 5; -fx-padding: 5 9 5 9; }"
                    + ".properties-input:focused, .properties-combo:focused { -fx-border-color: #009DAB; }"
                    + ".properties-toggle { -fx-text-fill: #354552; -fx-font-size: 12px; }"
                    + ".properties-secondary-btn { -fx-background-color: #E5EEF0; -fx-text-fill: #17646C; -fx-background-radius: 6; -fx-font-weight: bold; -fx-padding: 8 14; }"
                    + ".properties-secondary-btn:hover { -fx-background-color: #D6E8EB; }"
                    + ".properties-notice { -fx-text-fill: #647782; -fx-font-size: 11px; }"
                    + ".workspace-collapse-handle { -fx-background-color: #E9EEF2; -fx-border-color: #CED8DF; -fx-border-width: 1 0 0 0; -fx-padding: 2 10 2 12; }"
                    + ".workspace-collapse-caption { -fx-text-fill: #74848E; -fx-font-size: 10px; -fx-font-weight: bold; }"
                    + ".workspace-toggle { -fx-background-color: #FFFFFF; -fx-background-radius: 5; -fx-border-radius: 5; -fx-border-color: #C9D5DD; -fx-padding: 2 11 2 11; -fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #286973; -fx-cursor: hand; }"
                    + ".workspace-toggle:hover { -fx-background-color: #DEF0F0; -fx-border-color: #72B3B8; }";
}
