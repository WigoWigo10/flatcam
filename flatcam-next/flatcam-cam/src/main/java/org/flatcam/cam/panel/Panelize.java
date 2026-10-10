package org.flatcam.cam.panel;

import java.util.ArrayList;
import java.util.List;
import org.flatcam.cam.CancellationToken;
import org.flatcam.cam.ProgressCallback;
import org.flatcam.cam.excellon.ExcellonImage;
import org.flatcam.cam.geometry.ToolGeometry;
import org.flatcam.cam.gerber.GerberImage;
import org.flatcam.cam.geometry.ParallelGeometry;
import org.flatcam.cam.merge.GeometryJoin;
import org.flatcam.cam.merge.GerberJoin;
import org.flatcam.cam.transform.TransformOp;

/**
 * appTools/ToolPanelize.py: repeats an object in a grid of columns and rows, the copies apart by the size
 * of the reference box plus the spacing. The panel is built by translating the object once per cell and
 * assembling the copies without recentering. Gerber apertures are joined; Excellon tool IDs and
 * Geometry tool indexes are kept intact so their machining defaults remain associated correctly.
 */
public final class Panelize {

    /**
     * The grid actually used.
     *
     * @param columns  copies across (after any size constraint)
     * @param rows     copies up
     * @param constrained true when the requested grid did not fit the size limit and was reduced
     * @param stepX    distance between the same point of two neighbouring columns
     * @param stepY    distance between the same point of two neighbouring rows
     */
    public record Layout(int columns, int rows, boolean constrained, double stepX, double stepY) {
        public Layout {
            int maximum = Math.max(1, Integer.getInteger("flatcam.panelize.maxCopies", 10_000));
            if (columns < 1 || rows < 1 || (long) columns * rows > maximum)
                throw new IllegalArgumentException("Grade invalida ou excede o limite de " + maximum + " copias.");
            if (!Double.isFinite(stepX) || !Double.isFinite(stepY) || stepX < 0 || stepY < 0
                    || !Double.isFinite((columns - 1.0) * stepX) || !Double.isFinite((rows - 1.0) * stepY))
                throw new IllegalArgumentException("Passo da grade deve ser finito e nao negativo.");
        }
        /** {dx, dy} of every cell, row by row, the first cell being the original at (0, 0). */
        public List<double[]> offsets() {
            List<double[]> cells = new ArrayList<>();
            for (int row = 0; row < rows; row++) {
                for (int column = 0; column < columns; column++) {
                    cells.add(new double[]{column * stepX, row * stepY});
                }
            }
            return cells;
        }

        /** Width and height of the whole panel for a reference box of {@code boxWidth} x {@code boxHeight}. */
        public double[] size(double boxWidth, double boxHeight, double spacingColumns, double spacingRows) {
            return new double[]{boxWidth * columns + spacingColumns * (columns - 1),
                    boxHeight * rows + spacingRows * (rows - 1)};
        }
    }

    private Panelize() {
    }

    /**
     * @param box             {xmin, ymin, xmax, ymax} of the reference (the object itself or another one)
     * @param constrainWidth  largest panel width allowed, or NaN for none
     * @param constrainHeight largest panel height allowed, or NaN for none
     * @throws IllegalArgumentException for a non-positive grid, a negative spacing, or a limit smaller than one copy
     */
    public static Layout layout(double[] box, int columns, int rows, double spacingColumns, double spacingRows,
                                double constrainWidth, double constrainHeight) {
        if (columns < 1 || rows < 1) {
            throw new IllegalArgumentException("Colunas e linhas devem ser inteiros positivos");
        }
        if (box == null || box.length != 4 || java.util.Arrays.stream(box).anyMatch(v -> !Double.isFinite(v))
                || box[2] < box[0] || box[3] < box[1])
            throw new IllegalArgumentException("Caixa de referencia invalida.");
        if (!Double.isFinite(spacingColumns) || !Double.isFinite(spacingRows) || spacingColumns < 0 || spacingRows < 0) {
            throw new IllegalArgumentException("O espacamento nao pode ser negativo");
        }
        double width = box[2] - box[0];
        double height = box[3] - box[1];
        boolean constrained = false;
        if (!Double.isNaN(constrainWidth)) {
            if (!Double.isFinite(constrainWidth) || constrainWidth <= 0)
                throw new IllegalArgumentException("Limite de largura deve ser finito e positivo.");
            if (width > constrainWidth) {
                throw new IllegalArgumentException("O limite de largura do painel e menor que uma unica copia");
            }
            int fitted = width + spacingColumns == 0 ? columns : Math.max(1,
                    (int) Math.floor((constrainWidth + spacingColumns) / (width + spacingColumns)));
            if (fitted < columns) { columns = fitted; constrained = true; }
        }
        if (!Double.isNaN(constrainHeight)) {
            if (!Double.isFinite(constrainHeight) || constrainHeight <= 0)
                throw new IllegalArgumentException("Limite de altura deve ser finito e positivo.");
            if (height > constrainHeight) {
                throw new IllegalArgumentException("O limite de altura do painel e menor que uma unica copia");
            }
            int fitted = height + spacingRows == 0 ? rows : Math.max(1,
                    (int) Math.floor((constrainHeight + spacingRows) / (height + spacingRows)));
            if (fitted < rows) { rows = fitted; constrained = true; }
        }
        return new Layout(columns, rows, constrained, width + spacingColumns, height + spacingRows);
    }

    public static GerberImage gerber(GerberImage source, Layout layout) {
        return gerber(source, layout, CancellationToken.none(), ProgressCallback.none());
    }

    public static GerberImage gerber(GerberImage source, Layout layout, CancellationToken cancellation, ProgressCallback progress) {
        List<GerberImage> copies = new ArrayList<>();
        for (double[] cell : layout.offsets()) {
            cancellation.throwIfCancellationRequested();
            copies.add(cell[0] == 0 && cell[1] == 0 ? source : source.transformed(new TransformOp.Offset(cell[0], cell[1])));
            progress.report(.9 * copies.size() / ((double) layout.columns() * layout.rows()));
        }
        cancellation.throwIfCancellationRequested();
        GerberImage result = copies.size() == 1 ? source : GerberJoin.panelCopies(copies);
        cancellation.throwIfCancellationRequested(); progress.report(1);
        cancellation.throwIfCancellationRequested(); return result;
    }

    public static ExcellonImage excellon(ExcellonImage source, Layout layout) {
        return excellon(source, layout, CancellationToken.none(), ProgressCallback.none());
    }

    public static ExcellonImage excellon(ExcellonImage source, Layout layout, CancellationToken cancellation, ProgressCallback progress) {
        List<ExcellonImage.Drill> drills = new ArrayList<>();
        List<ExcellonImage.Slot> slots = new ArrayList<>();
        List<org.locationtech.jts.geom.Geometry> solids = new ArrayList<>();
        int done = 0;
        for (double[] cell : layout.offsets()) {
            cancellation.throwIfCancellationRequested();
            var copy = cell[0] == 0 && cell[1] == 0 ? source : source.transformed(new TransformOp.Offset(cell[0], cell[1]));
            drills.addAll(copy.drills()); slots.addAll(copy.slots());
            if (copy.solidGeometry() != null && !copy.solidGeometry().isEmpty()) solids.add(copy.solidGeometry());
            progress.report(.9 * ++done / ((double) layout.columns() * layout.rows()));
        }
        cancellation.throwIfCancellationRequested();
        var solid = solids.isEmpty() ? source.solidGeometry().getFactory().createGeometryCollection()
                : solids.size() == 1 ? solids.getFirst() : ParallelGeometry.unionGrouped(solids);
        // Copies of one object must preserve its exact diameters and IDs, not fuse rounded diameters.
        var result = done == 1 ? source : ExcellonImage.of(source.units(), source.toolDiameters(), drills, slots, solid);
        cancellation.throwIfCancellationRequested(); progress.report(1);
        cancellation.throwIfCancellationRequested(); return result;
    }

    public static GeometryJoin.Joined geometry(String units, org.locationtech.jts.geom.Geometry geometry,
                                               boolean strokeOnly, List<ToolGeometry> tools, Layout layout) {
        return geometry(units, geometry, strokeOnly, tools, layout, CancellationToken.none(), ProgressCallback.none());
    }

    public static GeometryJoin.Joined geometry(String units, org.locationtech.jts.geom.Geometry geometry,
            boolean strokeOnly, List<ToolGeometry> tools, Layout layout, CancellationToken cancellation, ProgressCallback progress) {
        List<org.locationtech.jts.geom.Geometry> copies = new ArrayList<>();
        List<List<org.locationtech.jts.geom.Geometry>> byTool = new ArrayList<>();
        for (int i = 0; i < tools.size(); i++) byTool.add(new ArrayList<>());
        for (double[] cell : layout.offsets()) {
            cancellation.throwIfCancellationRequested();
            TransformOp offset = new TransformOp.Offset(cell[0], cell[1]);
            copies.add(offset.apply(geometry));
            for (int i = 0; i < tools.size(); i++) {
                cancellation.throwIfCancellationRequested();
                byTool.get(i).add(offset.apply(tools.get(i).geometry()));
            }
            progress.report(.9 * copies.size() / ((double) layout.columns() * layout.rows()));
        }
        cancellation.throwIfCancellationRequested();
        List<ToolGeometry> copiedTools = new ArrayList<>();
        for (int i = 0; i < tools.size(); i++) {
            var tool = tools.get(i);
            copiedTools.add(new ToolGeometry(tool.toolDiameter(), geometry.getFactory().buildGeometry(byTool.get(i)), tool.toolProfile()));
        }
        var result = copies.size() == 1 ? new GeometryJoin.Joined(geometry, strokeOnly, tools)
                : new GeometryJoin.Joined(geometry.getFactory().buildGeometry(copies), strokeOnly, copiedTools);
        cancellation.throwIfCancellationRequested(); progress.report(1);
        cancellation.throwIfCancellationRequested(); return result;
    }
}
