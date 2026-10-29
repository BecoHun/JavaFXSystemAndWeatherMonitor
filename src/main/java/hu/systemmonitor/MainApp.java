package hu.systemmonitor;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.util.Objects;

public class MainApp extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        FXMLLoader loader = new FXMLLoader(Objects.requireNonNull(getClass().getResource("/hu/systemmonitor/views/dashboard.fxml")));
        Scene scene = new Scene(loader.load(), 700, 650);

        primaryStage.setTitle("Rendszer- és Időjárásfigyelő v1.0");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    @Override
    public void stop() {
        // Alkalmazás bezárásakor leállítjuk a szálakat, ha szükséges
    }

    public static void main(String[] args) {
        launch(args);
    }
}