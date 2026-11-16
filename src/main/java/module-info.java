module fosalgo.pheroswarm {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.xml;
    opens fosalgo.pheroswarm to javafx.fxml;
    exports fosalgo.pheroswarm;
}