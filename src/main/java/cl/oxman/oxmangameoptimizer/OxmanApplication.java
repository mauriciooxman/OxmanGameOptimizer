package cl.oxman.oxmangameoptimizer;

import cl.oxman.oxmangameoptimizer.game.GamingSessionManager;
import cl.oxman.oxmangameoptimizer.ui.TrayManager;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.io.IOException;

public class OxmanApplication extends Application {

    @Override
    public void start(Stage stage) throws IOException {

        FXMLLoader fxmlLoader =
                new FXMLLoader(OxmanApplication.class.getResource("main-view.fxml"));

        Scene scene = new Scene(fxmlLoader.load(), 720, 850);

        // Cargar CSS
        scene.getStylesheets().add(
                OxmanApplication.class
                        .getResource("styles.css")
                        .toExternalForm()
        );

        stage.setTitle("Oxman Game Optimizer");
        stage.getIcons().add(new Image(
                OxmanApplication.class.getResourceAsStream("company-logo-gold.png")
        ));

        stage.setScene(scene);
        stage.setMinWidth(660);
        stage.setMinHeight(720);
        Platform.setImplicitExit(false);
        TrayManager.initialize(stage, GamingSessionManager::finishBeforeExit);
        stage.setOnCloseRequest(event -> {
            event.consume();
            TrayManager.exitApplication();
        });

        stage.show();
        stage.centerOnScreen();

    }

    public static void main(String[] args) {
        launch();
    }
}
