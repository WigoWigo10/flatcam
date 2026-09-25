package org.flatcam.fx;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.List;
import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.flatcam.app.job.JobExecutor;

/**
 * Entry point for the FlatCAM FX JavaFX shell (Fase 1 skeleton -
 * CONTEXTO_FLATCAM_FX.md). Deliberately thin: all layout lives in
 * {@link MainWindow}, all background work goes through {@link JobExecutor}
 * so nothing heavy ever runs on this class's thread.
 */
public class MainApp extends Application {

    private static final Logger LOG = System.getLogger(MainApp.class.getName());
    private static final Duration MAXIMIZE_POLL_INTERVAL = Duration.millis(400);
    /** Consecutive polls isMaximized() must read true (after having read false) before acting - see installDpiRescaleWorkaround. */
    private static final int STABILITY_THRESHOLD = 3;
    /** Grace period after showing a Stage before watching its isMaximized() - see installDpiRescaleWorkaround. */
    private static final Duration ARM_DELAY = Duration.millis(1000);

    private JobExecutor jobExecutor;
    private Stage currentStage;
    private boolean recreatingStage;
    private boolean armed;
    private boolean sawUnmaximizedSinceLastFix;
    private int maximizedStreak;
    private Timeline maximizePoll;
    private String capturedScreenId;
    private double[] capturedScreenTopLeft;

    private record StartupTarget(Screen screen, String screenId) {
    }

    @Override
    public void start(Stage primaryStage) {
        jobExecutor = new JobExecutor();
        // The DPI-rescale workaround below hides the old Stage and shows a fresh one
        // right after - with the JavaFX default (true), that momentary zero-showing-
        // windows instant is enough for the toolkit to auto-exit before fresh.show()
        // runs (confirmed: the app quit on its own during a recreate before this was
        // added). Exit is now only ever triggered explicitly, from wireCloseHandler's
        // onCloseRequest.
        Platform.setImplicitExit(false);

        MainWindow mainWindow = new MainWindow(jobExecutor);
        StartupTarget startup = startupTarget();
        Rectangle2D startupBounds = startup.screen().getBounds();
        primaryStage.setTitle("FlatCAM FX");
        primaryStage.setScene(mainWindow.createScene());
        primaryStage.setWidth(Math.min(AppPreferences.loadWindowWidth(1200),
                Math.max(1, startupBounds.getWidth() - 20)));
        primaryStage.setHeight(Math.min(AppPreferences.loadWindowHeight(800),
                Math.max(1, startupBounds.getHeight() - 20)));
        // Place the Stage before maximizing: the OS chooses the monitor to
        // maximize from its initial bounds, not from a later setX/setY call.
        primaryStage.setX(startupBounds.getMinX() + 10);
        primaryStage.setY(startupBounds.getMinY() + 10);
        wireCloseHandler(primaryStage, mainWindow);
        // Matches the legacy app, which always opens maximized regardless of its last
        // saved window size - setWidth/Height above still matter as the size restored
        // if the user un-maximizes later. Set before show() so it takes effect without
        // a visible flash of the un-maximized size first.
        primaryStage.setMaximized(true);
        primaryStage.show();

        currentStage = primaryStage;
        mainWindow.setCurrentScreenId(startup.screenId());
        installDpiRescaleWorkaround(mainWindow);

        LOG.log(Level.INFO, "MainApp started");
    }

    private static StartupTarget startupTarget() {
        AppPreferences.SavedWindowScreen saved = AppPreferences.loadWindowScreen();
        if (saved != null && isDeviceConnected(saved.deviceId())) {
            List<Screen> screens = Screen.getScreens();
            List<WindowScreenMatcher.Bounds> bounds = screens.stream()
                    .map(screen -> screenBounds(screen.getBounds())).toList();
            int index = WindowScreenMatcher.bestMatch(bounds, saved.bounds());
            if (index >= 0) {
                return new StartupTarget(screens.get(index), saved.deviceId());
            }
        }
        String primaryId = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getDefaultScreenDevice().getIDstring();
        return new StartupTarget(Screen.getPrimary(), primaryId);
    }

    private static boolean isDeviceConnected(String deviceId) {
        for (java.awt.GraphicsDevice device : java.awt.GraphicsEnvironment
                .getLocalGraphicsEnvironment().getScreenDevices()) {
            if (device.getIDstring().equals(deviceId)) {
                return true;
            }
        }
        return false;
    }

    private static WindowScreenMatcher.Bounds screenBounds(Rectangle2D bounds) {
        return new WindowScreenMatcher.Bounds(bounds.getMinX(), bounds.getMinY(),
                bounds.getWidth(), bounds.getHeight());
    }

    private static Screen screenContainingMostOf(Stage stage) {
        Screen best = Screen.getPrimary();
        double bestArea = -1;
        for (Screen screen : Screen.getScreens()) {
            Rectangle2D bounds = screen.getBounds();
            double width = Math.max(0, Math.min(stage.getX() + stage.getWidth(), bounds.getMaxX())
                    - Math.max(stage.getX(), bounds.getMinX()));
            double height = Math.max(0, Math.min(stage.getY() + stage.getHeight(), bounds.getMaxY())
                    - Math.max(stage.getY(), bounds.getMinY()));
            double area = width * height;
            if (area > bestArea) {
                bestArea = area;
                best = screen;
            }
        }
        return best;
    }

    /**
     * Works around a real Glass/JavaFX limitation on Windows: dragging (not
     * Win+Arrow snapping - confirmed that goes through a different,
     * unaffected code path) a maximized window onto a monitor with a
     * different DPI leaves the whole UI rendered at the WRONG scale once it
     * re-maximizes there - confirmed via a live two-monitor test (200% and
     * 100%) with a diagnostic poll logging the Stage's own bounds and
     * Glass's reported render scale throughout the drag.
     *
     * <p>Two earlier versions of this workaround tried to detect the
     * mismatch directly and both failed for reasons specific to this bug:
     * <ul>
     *   <li>{@code Stage.xProperty()}/{@code yProperty()}/{@code widthProperty()}/
     *       {@code heightProperty()} change listeners never fired during a
     *       real drag, despite those same properties' getters returning
     *       fresh values on demand.</li>
     *   <li>Re-deriving the correct scale independently via
     *       {@code Screen.getScreensForRectangle(stage.getX(), getY(),
     *       getWidth(), getHeight())} - reading the Stage's own bounds, not
     *       its (stuck) {@code outputScaleX} - seemed promising, but logging
     *       it throughout a real drag showed {@code getWidth()}/{@code
     *       getHeight()} themselves inconsistently switch between physical
     *       and logical pixel units while this bug is in effect. That
     *       corrupts the very inputs this check depends on, so it can
     *       under- or over-report a "match" against the last known scale
     *       even while the visible bug is present.</li>
     * </ul>
     *
     * <p>Since none of the Stage's own geometry can be trusted while this
     * bug is active, this instead watches {@link Stage#isMaximized()} for a
     * false-then-true transition: dragging a maximized window always
     * restores it first and re-maximizes on drop (confirmed the same way),
     * so that transition reliably brackets every drag-across-monitors,
     * maximized or not. Once seen (held stable for
     * {@link #STABILITY_THRESHOLD} polls, to ignore the brief un-maximized
     * blip mid-drag), the Stage is unconditionally replaced with a brand new
     * one on the same Scene (JavaFX allows reassigning a Scene to a
     * different Window) - a new Stage, created fresh while already
     * positioned on the current screen, computes its scale from scratch and
     * gets it right every time (confirmed), the same way a full app restart
     * on that monitor would. This never needs to know whether the scale
     * was actually wrong; recreating when it wasn't is a harmless no-op.
     *
     * <p>The trade-off: a Stage moved (or dragged) between monitors while
     * NOT maximized is not covered - this app is always launched maximized
     * (see {@link #start}) and the reported bug and this port's own testing
     * only concern the maximized case.
     */
    private void installDpiRescaleWorkaround(MainWindow mainWindow) {
        // Each recreate calls this again for the fresh Stage - without stopping the
        // PREVIOUS Timeline first, every past recreate left its own poll running
        // forever in the background, all sharing (and fighting over) the same
        // sawUnmaximizedSinceLastFix/maximizedStreak instance fields. Confirmed this
        // caused real failures during testing: after a few recreates, several orphaned
        // polls firing at slightly different offsets kept resetting each other's
        // streak count, and a later real drag that should have triggered a recreate
        // never did.
        if (maximizePoll != null) {
            maximizePoll.stop();
        }
        // A freshly shown Stage's isMaximized() doesn't necessarily read true on the
        // very first poll after show() - setMaximized(true) was called before show(),
        // but the OS-level maximize can still be settling for a beat after that.
        // Without this grace period, that transient false reading looked exactly like
        // a real drag starting, and the resulting recreate (itself going through this
        // same startup sequence) chained into MORE spurious recreates - confirmed:
        // seen cascading 3-4 times in a row with zero user interaction. "armed" stays
        // false (isMaximized() reads are ignored entirely) until ARM_DELAY after this
        // Stage was shown.
        armed = false;
        sawUnmaximizedSinceLastFix = false;
        maximizedStreak = 0;
        PauseTransition arm = new PauseTransition(ARM_DELAY);
        arm.setOnFinished(e -> armed = true);
        arm.play();
        maximizePoll = new Timeline(new KeyFrame(MAXIMIZE_POLL_INTERVAL, e -> {
            if (recreatingStage || !armed) {
                return;
            }
            if (!currentStage.isMaximized()) {
                sawUnmaximizedSinceLastFix = true;
                maximizedStreak = 0;
                return;
            }
            if (!sawUnmaximizedSinceLastFix) {
                return;
            }
            maximizedStreak++;
            if (maximizedStreak == 1) {
                // Capture NOW, on the very first poll where isMaximized() reads true again -
                // not STABILITY_THRESHOLD polls (1.2s+) from now, when this actually acts on
                // it. Confirmed this matters: capturing at the delayed, "act on it" moment
                // instead put the fresh Stage back on the ORIGINAL monitor whenever the user's
                // mouse had already drifted off the target monitor by then (e.g. straight to a
                // menu after dropping the window) - a mistake identical in spirit to trusting
                // old.getX()/getY() at that same delayed moment (see recreateStageOnCurrentScreen).
                capturedScreenId = currentScreenId();
                capturedScreenTopLeft = currentScreenTopLeft();
            }
            if (maximizedStreak < STABILITY_THRESHOLD) {
                return;
            }
            sawUnmaximizedSinceLastFix = false;
            maximizedStreak = 0;
            recreatingStage = true;
            try {
                recreateStageOnCurrentScreen(mainWindow, capturedScreenId, capturedScreenTopLeft);
            } finally {
                recreatingStage = false;
            }
        }));
        maximizePoll.setCycleCount(Timeline.INDEFINITE);
        maximizePoll.play();
    }

    private void recreateStageOnCurrentScreen(MainWindow mainWindow, String screenId, double[] screenTopLeft) {
        Stage old = currentStage;
        Scene scene = old.getScene();

        Stage fresh = new Stage();
        fresh.setTitle(old.getTitle());
        wireCloseHandler(fresh, mainWindow);
        old.setOnCloseRequest(null);
        old.hide();

        fresh.setScene(scene);
        // NOT old.getX()/getY(): confirmed those can themselves be wrong while this
        // bug is active (same root cause as the width/height mixed-units problem in
        // this method's own doc) - using them here to place the fresh Stage sent it
        // right back to the ORIGINAL monitor every time, even while dragging onto the
        // other one. Using the screen captured back when the maximize was first
        // detected (see installDpiRescaleWorkaround), not recomputed now - by now the
        // mouse may have drifted off the target monitor already.
        if (screenTopLeft != null) {
            fresh.setX(screenTopLeft[0]);
            fresh.setY(screenTopLeft[1]);
        }
        fresh.setMaximized(true);
        fresh.show();

        currentStage = fresh;
        mainWindow.setCurrentScreenId(screenId != null ? screenId : currentScreenId());
        installDpiRescaleWorkaround(mainWindow);
        LOG.log(Level.INFO, "Recreated Stage after a maximize transition (DPI rescale workaround)");
    }

    /**
     * A stable identifier for whichever monitor currently has the mouse cursor
     * (Windows' own device name, e.g. "\\.\DISPLAY1") - used to remember the
     * sidebar width separately per monitor (see MainWindow.setCurrentScreenId).
     * java.awt.GraphicsDevice is the only API here with a persistent-across-
     * sessions per-monitor id at all; javafx.stage.Screen has none. Used only
     * to identify WHICH device, never for its bounds/coordinates (see
     * currentScreenTopLeft's doc for why that specific mix caused a real bug).
     */
    private static String currentScreenId() {
        java.awt.Point mouse = java.awt.MouseInfo.getPointerInfo().getLocation();
        for (java.awt.GraphicsDevice device : java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
            if (device.getDefaultConfiguration().getBounds().contains(mouse)) {
                return device.getIDstring();
            }
        }
        return "default";
    }

    /**
     * The top-left corner (plus a small inset) of whichever monitor currently has
     * the mouse cursor, or null if undetermined. Only the raw mouse position comes
     * from AWT (java.awt.MouseInfo - JavaFX has no global cursor-position query);
     * the screen bounds it's tested against are {@link Screen#getScreens()}'s own,
     * not AWT's GraphicsDevice - deliberately, since AWT and Glass do not
     * necessarily agree on monitor bounds/coordinate space on a mixed-DPI setup
     * (confirmed: using GraphicsDevice bounds here positioned the fresh Stage
     * wrong specifically on the 100%-scaled monitor). Stage.setX/setY need
     * coordinates in Glass's own space, so the bounds must come from Glass's own
     * Screen API too.
     */
    private static double[] currentScreenTopLeft() {
        java.awt.Point mouse = java.awt.MouseInfo.getPointerInfo().getLocation();
        for (Screen screen : Screen.getScreens()) {
            Rectangle2D bounds = screen.getBounds();
            if (bounds.contains(mouse.x, mouse.y)) {
                return new double[]{bounds.getMinX() + 10, bounds.getMinY() + 10};
            }
        }
        return null;
    }

    private void wireCloseHandler(Stage stage, MainWindow mainWindow) {
        stage.setOnCloseRequest(e -> {
            AppPreferences.saveWindowSize(stage.getWidth(), stage.getHeight());
            AppPreferences.saveWindowScreen(mainWindow.currentScreenId(),
                    screenBounds(screenContainingMostOf(stage).getBounds()));
            mainWindow.saveSplitPositions();
            Platform.exit();
        });
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
