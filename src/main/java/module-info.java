module cl.oxman.oxmangameoptimizer {

    requires javafx.controls;
    requires javafx.fxml;
    requires jdk.management;

    requires com.github.oshi;
    requires org.slf4j;

    opens cl.oxman.oxmangameoptimizer to javafx.fxml;
    opens cl.oxman.oxmangameoptimizer.ui to javafx.fxml;

    exports cl.oxman.oxmangameoptimizer;
    exports cl.oxman.oxmangameoptimizer.ui;
}