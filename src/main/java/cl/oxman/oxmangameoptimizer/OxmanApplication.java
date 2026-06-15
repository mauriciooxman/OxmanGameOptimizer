package cl.oxman.oxmangameoptimizer;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class OxmanApplication extends Application {

    @Override
    public void start(Stage stage) throws IOException {

        FXMLLoader fxmlLoader =
                new FXMLLoader(OxmanApplication.class.getResource("main-view.fxml"));

        Scene scene = new Scene(fxmlLoader.load(),700,700);

        // Cargar CSS
        scene.getStylesheets().add(
                OxmanApplication.class
                        .getResource("styles.css")
                        .toExternalForm()
        );

        stage.setTitle("Oxman Game Optimizer");

        stage.setScene(scene);

        stage.show();

    }

    public static void main(String[] args) {
        launch();
    }
}