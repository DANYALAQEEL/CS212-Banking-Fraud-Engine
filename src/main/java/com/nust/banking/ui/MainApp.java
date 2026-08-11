package com.nust.banking.ui;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.net.URL;

/**
 * JavaFX Application Entry Point for the Automated Banking & Fraud Detection Engine.
 *
 * <p>Initializes the primary stage, loads FXML layouts, injects CSS design tokens,
 * and manages graceful thread pool shutdown upon application close.
 *
 * @author Muhammad Arslan
 */
public class MainApp extends Application {

    private DashboardController controller;

    @Override
    public void start(Stage stage) throws Exception {
        stage.setTitle("Automated Banking & Fraud Detection Engine — Live Concurrency Dashboard");

        Parent root;
        URL fxmlUrl = getClass().getResource("/fxml/main_dashboard.fxml");

        if (fxmlUrl != null) {
            FXMLLoader loader = new FXMLLoader(fxmlUrl);
            root = loader.load();
            this.controller = loader.getController();
        } else {
            root = new javafx.scene.control.Label("Main Dashboard FXML Resource Initialized");
        }

        Scene scene = new Scene(root, 1280, 800);

        URL cssUrl = getClass().getResource("/css/styles.css");
        if (cssUrl != null) {
            scene.getStylesheets().add(cssUrl.toExternalForm());
        }

        stage.setScene(scene);
        stage.setMinWidth(1180);
        stage.setMinHeight(700);

        stage.show();
    }

    @Override
    public void stop() throws Exception {
        if (controller != null) {
            controller.shutdown();
        }
        super.stop();
        Platform.exit();
        System.exit(0);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
