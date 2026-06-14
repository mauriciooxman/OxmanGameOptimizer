module cl.oxman.oxmangameoptimizer {
    requires javafx.controls;
    requires javafx.fxml;


    opens cl.oxman.oxmangameoptimizer to javafx.fxml;
    exports cl.oxman.oxmangameoptimizer;
}