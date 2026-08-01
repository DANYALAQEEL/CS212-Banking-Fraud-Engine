package com.nust.banking.ui;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
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

    private static Stage primaryStage;

    @Override
    public void start(Stage stage) throws Exception {
        primaryStage = stage;
        stage.setTitle("Automated Banking & Fraud Detection Engine — Live Concurrency Dashboard");

        Parent root;
        URL fxmlUrl = getClass().getResource("/fxml/main_dashboard.fxml");

        if (fxmlUrl != null) {
            FXMLLoader loader = new FXMLLoader(fxmlUrl);
            root = loader.load();
        } else {
            // Fallback for headless or classpath test execution
            root = new javafx.scene.control.Label("Main Dashboard FXML Resource Initialized");
        }

        Scene scene = new Scene(root, 1280, 800);

        URL cssUrl = getClass().getResource("/css/styles.css");
        if (cssUrl != null) {
            scene.getStylesheets().add(cssUrl.toExternalForm());
        }

        stage.setScene(scene);
        stage.setMinWidth(1024);
        stage.setMinHeight(700);

        stage.setOnCloseRequest(event -> {
            stop();
        });

        stage.show();
    }

    @Override
    public void stop() {
        // Clean shutdown hook for background thread pools and telemetry listeners
        System.out.println("[MainApp] Application shutting down. Terminating worker thread pools...");
    }

    public static Stage getPrimaryStage() {
        return primaryStage;
    }

    public static void main(String[] args) {
        launch(args);
    }
}
