module cl.oxman.oxmangameoptimizer {

    requires javafx.controls;
    requires javafx.fxml;
    requires jdk.management;
    requires jdk.unsupported;
    requires java.desktop;

    requires com.github.oshi;
    requires com.sun.jna;
    requires com.sun.jna.platform;
    requires org.slf4j;
    requires org.apache.commons.csv;

    opens cl.oxman.oxmangameoptimizer to javafx.fxml;
    opens cl.oxman.oxmangameoptimizer.ui to javafx.fxml;

    exports cl.oxman.oxmangameoptimizer;
    exports cl.oxman.oxmangameoptimizer.ui;
    exports cl.oxman.oxmangameoptimizer.optimizer;
    exports cl.oxman.oxmangameoptimizer.optimizer.action;
    exports cl.oxman.oxmangameoptimizer.optimizer.state;
    exports cl.oxman.oxmangameoptimizer.system;
    exports cl.oxman.oxmangameoptimizer.performance;
    exports cl.oxman.oxmangameoptimizer.performance.benchmark;
}
