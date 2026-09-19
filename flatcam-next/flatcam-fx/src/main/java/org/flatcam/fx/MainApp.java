package org.flatcam.fx;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import javafx.application.Application;
import javafx.stage.Stage;
import org.flatcam.app.job.JobExecutor;

/**
 * Entry point for the FlatCAM Next JavaFX shell (Fase 1 skeleton -
 * CONTEXTO_FLATCAM_FX.md). Deliberately thin: all layout lives in
 * {@link MainWindow}, all background work goes through {@link JobExecutor}
 * so nothing heavy ever runs on this class's thread.
 */
public class MainApp extends Application {

    private static final Logger LOG = System.getLogger(MainApp.class.getName());

    private JobExecutor jobExecutor;

    @Override
    public void start(Stage primaryStage) {
        jobExecutor = new JobExecutor();

        MainWindow mainWindow = new MainWindow(jobExecutor);
        primaryStage.setTitle("FlatCAM Next (skeleton)");
        primaryStage.setScene(mainWindow.createScene());
        primaryStage.setWidth(AppPreferences.loadWindowWidth(1200));
        primaryStage.setHeight(AppPreferences.loadWindowHeight(800));
        primaryStage.setOnCloseRequest(e -> {
            AppPreferences.saveWindowSize(primaryStage.getWidth(), primaryStage.getHeight());
            mainWindow.saveSplitPositions();
        });
        // Matches the legacy app, which always opens maximized regardless of its last
        // saved window size - setWidth/Height above still matter as the size restored
        // if the user un-maximizes later. Set before show() so it takes effect without
        // a visible flash of the un-maximized size first.
        primaryStage.setMaximized(true);
        primaryStage.show();

        LOG.log(Level.INFO, "MainApp started");
    }

    @Override
    public void stop() {
        jobExecutor.shutdown();
        LOG.log(Level.INFO, "MainApp stopped");
    }

    public static void main(String[] args) {
        launch(args);
    }
}
