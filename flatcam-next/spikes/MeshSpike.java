// Throwaway feasibility spike (not part of the Maven build): wide strokes as a GPU triangle mesh in a JavaFX SubScene
// against the Canvas. Results in PLOT_WIDE_STROKE_DENSITY.md. Compile/run with the JavaFX jars on --module-path:
//   java --module-path <javafx jars> --add-modules javafx.controls,javafx.graphics MeshSpike.java <segments> <widthPx> <mesh|meshnoaa|canvas|empty>
package org.flatcam.fx;

import java.util.Random;
import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.scene.AmbientLight;
import javafx.scene.Group;
import javafx.scene.PerspectiveCamera;
import javafx.scene.ParallelCamera;
import javafx.scene.Scene;
import javafx.scene.SceneAntialiasing;
import javafx.scene.SnapshotParameters;
import javafx.scene.SubScene;
import javafx.scene.canvas.Canvas;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.CullFace;
import javafx.scene.shape.DrawMode;
import javafx.scene.shape.MeshView;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;
import javafx.scene.shape.TriangleMesh;
import javafx.scene.transform.Affine;
import javafx.stage.Stage;

/** Feasibility spike: wide strokes as a GPU triangle mesh in a SubScene vs the Canvas. Usage: segments widthPx */
public class MeshSpike {
    public static void main(String[] args) {
        Platform.startup(() -> {
            try {
                run(Integer.parseInt(args[0]), Double.parseDouble(args[1]), args.length > 2 ? args[2] : "mesh");
            } catch (Throwable t) {
                t.printStackTrace();
                Platform.exit();
            }
        });
    }

    static double[][] walk(int segments, int paths) {
        Random random = new Random(7);
        double[][] result = new double[paths][];
        int per = segments / paths;
        for (int p = 0; p < paths; p++) {
            double[] c = new double[(per + 1) * 2];
            double x = 100 + random.nextDouble() * 1000, y = 100 + random.nextDouble() * 600;
            for (int i = 0; i <= per; i++) {
                c[2 * i] = x;
                c[2 * i + 1] = y;
                x += random.nextGaussian() * 6;
                y += random.nextGaussian() * 6;
            }
            result[p] = c;
        }
        return result;
    }

    static MeshView mesh(double[][] paths, double width, Color ink) {
        double r = width / 2;
        int discSides = 12;
        int vertices = 0, triangles = 0;
        for (double[] c : paths) {
            int n = c.length / 2;
            vertices += (n - 1) * 4 + n * (discSides + 1);
            triangles += (n - 1) * 2 + n * discSides;
        }
        float[] points = new float[vertices * 3];
        int[] faces = new int[triangles * 6];
        int v = 0, f = 0;
        for (double[] c : paths) {
            int n = c.length / 2;
            for (int i = 0; i < n; i++) {
                if (i < n - 1) {
                    double dx = c[2 * i + 2] - c[2 * i], dy = c[2 * i + 3] - c[2 * i + 1];
                    double len = Math.hypot(dx, dy);
                    if (len > 1e-9) {
                        double nx = -dy / len * r, ny = dx / len * r;
                        int b = v;
                        double[][] q = {{c[2 * i] + nx, c[2 * i + 1] + ny}, {c[2 * i] - nx, c[2 * i + 1] - ny},
                                {c[2 * i + 2] - nx, c[2 * i + 3] - ny}, {c[2 * i + 2] + nx, c[2 * i + 3] + ny}};
                        for (double[] pt : q) {
                            points[3 * v] = (float) pt[0]; points[3 * v + 1] = (float) pt[1]; points[3 * v + 2] = 0; v++;
                        }
                        faces[f++] = b; faces[f++] = 0; faces[f++] = b + 1; faces[f++] = 0; faces[f++] = b + 2; faces[f++] = 0;
                        faces[f++] = b; faces[f++] = 0; faces[f++] = b + 2; faces[f++] = 0; faces[f++] = b + 3; faces[f++] = 0;
                    }
                }
                int center = v;
                points[3 * v] = (float) c[2 * i]; points[3 * v + 1] = (float) c[2 * i + 1]; points[3 * v + 2] = 0; v++;
                for (int s = 0; s < discSides; s++) {
                    double a = 2 * Math.PI * s / discSides;
                    points[3 * v] = (float) (c[2 * i] + r * Math.cos(a));
                    points[3 * v + 1] = (float) (c[2 * i + 1] + r * Math.sin(a));
                    points[3 * v + 2] = 0; v++;
                }
                for (int s = 0; s < discSides; s++) {
                    faces[f++] = center; faces[f++] = 0;
                    faces[f++] = center + 1 + s; faces[f++] = 0;
                    faces[f++] = center + 1 + (s + 1) % discSides; faces[f++] = 0;
                }
            }
        }
        TriangleMesh mesh = new TriangleMesh();
        mesh.getPoints().setAll(java.util.Arrays.copyOf(points, v * 3));
        mesh.getTexCoords().setAll(0, 0);
        mesh.getFaces().setAll(java.util.Arrays.copyOf(faces, f));
        MeshView view = new MeshView(mesh);
        view.setCullFace(CullFace.NONE);
        view.setDrawMode(DrawMode.FILL);
        PhongMaterial material = new PhongMaterial(ink);
        view.setMaterial(material);
        return view;
    }

    static void run(int segments, double width, String mode) throws Exception {
        double[][] paths = walk(segments, Math.max(1, segments / 200));
        Color ink = Color.web("#5E6CFF");
        int w = 1280, h = 800;
        System.out.println("SCENE3D=" + Platform.isSupported(javafx.application.ConditionalFeature.SCENE3D)
                + " segments=" + segments + " width=" + width);

        // Canvas reference
        Canvas canvas = new Canvas(w, h);
        var gc = canvas.getGraphicsContext2D();
        gc.setStroke(ink); gc.setLineWidth(width); gc.setLineCap(StrokeLineCap.ROUND); gc.setLineJoin(StrokeLineJoin.ROUND);
        long t0 = System.nanoTime();
        for (double[] c : paths) {
            gc.beginPath(); gc.moveTo(c[0], c[1]);
            for (int i = 1; i < c.length / 2; i++) gc.lineTo(c[2 * i], c[2 * i + 1]);
            gc.stroke();
        }
        SnapshotParameters sp = new SnapshotParameters(); sp.setFill(Color.TRANSPARENT);
        WritableImage ref = canvas.snapshot(sp, null);
        System.out.printf("canvas stroke+snapshot: %.1f ms%n", (System.nanoTime() - t0) / 1e6);

        // Mesh
        t0 = System.nanoTime();
        MeshView view = mesh(paths, width, ink);
        System.out.printf("mesh build: %.1f ms (%d vertices)%n", (System.nanoTime() - t0) / 1e6,
                ((TriangleMesh) view.getMesh()).getPoints().size() / 3);
        Affine transform = new Affine();
        Group root = new Group(view, new AmbientLight(Color.WHITE));
        view.getTransforms().add(transform);
        SubScene sub = new SubScene(root, w, h, false, mode.equals("meshnoaa") ? SceneAntialiasing.DISABLED : SceneAntialiasing.BALANCED);
        sub.setCamera(new ParallelCamera());
        sub.setFill(Color.TRANSPARENT);
        Canvas live = new Canvas(w, h);
        StackPane pane = mode.equals("canvas") ? new StackPane(live) : mode.equals("empty") ? new StackPane() : new StackPane(sub);
        Stage stage = new Stage();
        stage.setScene(new Scene(pane, w, h, Color.web("#1b2433")));
        stage.setX(-3000); stage.setY(50);
        stage.show();

        Platform.runLater(() -> {
            WritableImage snap = mode.startsWith("mesh") ? sub.snapshot(sp, null) : ref;
            double sum = 0; int strokePixels = 0, bad = 0; double colorErr = 0; int colorN = 0;
            for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
                int a = ref.getPixelReader().getArgb(x, y), b = snap.getPixelReader().getArgb(x, y);
                double ra = ((a >>> 24) & 255) / 255.0, ba = ((b >>> 24) & 255) / 255.0;
                sum += Math.abs(ra - ba);
                if (ra > 0 || ba > 0) { strokePixels++; if (Math.abs(ra - ba) > 0.25) bad++; }
                if (ra == 1 && ba == 1) { colorN++; colorErr += Math.abs(((a >> 16) & 255) - ((b >> 16) & 255)) + Math.abs(((a >> 8) & 255) - ((b >> 8) & 255)) + Math.abs((a & 255) - (b & 255)); }
            }
            System.out.printf("mesh vs canvas: meanAlphaDiff=%.5f badFraction=%.4f strokePixels=%d solidColorErr=%.2f/ch-sum%n",
                    sum / (w * h), bad / (double) Math.max(1, strokePixels), strokePixels, colorErr / Math.max(1, colorN));
            // zoom timing
            final int[] step = {0};
            final long[] last = {0};
            final double[] intervals = new double[200];
            AnimationTimer timer = new AnimationTimer() {
                @Override public void handle(long now) {
                    if (last[0] != 0 && step[0] < 200) intervals[step[0]] = (now - last[0]) / 1e6;
                    last[0] = now;
                    double s = 1 + 3 * Math.abs(Math.sin(step[0] / 40.0));
                    transform.setToIdentity();
                    transform.appendTranslation(w / 2.0, h / 2.0);
                    transform.appendScale(s, s);
                    transform.appendTranslation(-w / 2.0, -h / 2.0);
                    if (mode.equals("canvas")) {
                        var g = live.getGraphicsContext2D();
                        g.setTransform(1, 0, 0, 1, 0, 0);
                        g.clearRect(0, 0, w, h);
                        g.setTransform(s, 0, 0, s, w / 2.0 * (1 - s), h / 2.0 * (1 - s));
                        g.setStroke(ink); g.setLineWidth(width); g.setLineCap(StrokeLineCap.ROUND); g.setLineJoin(StrokeLineJoin.ROUND);
                        for (double[] c : paths) {
                            g.beginPath(); g.moveTo(c[0], c[1]);
                            for (int i = 1; i < c.length / 2; i++) g.lineTo(c[2 * i], c[2 * i + 1]);
                            g.stroke();
                        }
                    }
                    if (++step[0] >= 200) {
                        stop();
                        double max = 0, total = 0; int slow = 0;
                        for (int i = 5; i < 200; i++) { max = Math.max(max, intervals[i]); total += intervals[i]; if (intervals[i] > 20) slow++; }
                        System.out.printf(mode + " zoom 200 frames: avg %.1f ms max %.1f ms slow(>20ms)=%d%n", total / 195, max, slow);
                        Platform.exit();
                    }
                }
            };
            timer.start();
        });
    }
}
