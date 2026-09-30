package org.flatcam.cam.panel;

import java.util.ArrayList;
import java.util.List;
import org.flatcam.cam.excellon.ExcellonImage;
import org.flatcam.cam.geometry.ToolGeometry;
import org.flatcam.cam.gerber.GerberImage;
import org.flatcam.cam.merge.ExcellonJoin;
import org.flatcam.cam.merge.GeometryJoin;
import org.flatcam.cam.merge.GerberJoin;
import org.flatcam.cam.transform.TransformOp;

/**
 * appTools/ToolPanelize.py: repeats an object in a grid of columns and rows, the copies apart by the size
 * of the reference box plus the spacing. The panel is built by translating the object once per cell and
 * joining the copies with the same rules as Edit > Join Objects.
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
        if (spacingColumns < 0 || spacingRows < 0) {
            throw new IllegalArgumentException("O espacamento nao pode ser negativo");
        }
        double width = box[2] - box[0];
        double height = box[3] - box[1];
        boolean constrained = false;
        if (!Double.isNaN(constrainWidth)) {
            if (width > constrainWidth) {
                throw new IllegalArgumentException("O limite de largura do painel e menor que uma unica copia");
            }
            while (columns > 1 && width * columns + spacingColumns * (columns - 1) > constrainWidth) {
                columns--;
                constrained = true;
            }
        }
        if (!Double.isNaN(constrainHeight)) {
            if (height > constrainHeight) {
                throw new IllegalArgumentException("O limite de altura do painel e menor que uma unica copia");
            }
            while (rows > 1 && height * rows + spacingRows * (rows - 1) > constrainHeight) {
                rows--;
                constrained = true;
            }
        }
        return new Layout(columns, rows, constrained, width + spacingColumns, height + spacingRows);
    }

    public static GerberImage gerber(GerberImage source, Layout layout) {
        List<GerberImage> copies = new ArrayList<>();
        for (double[] cell : layout.offsets()) {
            copies.add(cell[0] == 0 && cell[1] == 0 ? source : source.transformed(new TransformOp.Offset(cell[0], cell[1])));
        }
        return copies.size() == 1 ? source : GerberJoin.join(copies);
    }

    public static ExcellonImage excellon(ExcellonImage source, Layout layout) {
        List<ExcellonImage> copies = new ArrayList<>();
        for (double[] cell : layout.offsets()) {
            copies.add(cell[0] == 0 && cell[1] == 0 ? source : source.transformed(new TransformOp.Offset(cell[0], cell[1])));
        }
        return copies.size() == 1 ? source : ExcellonJoin.join(copies, true);
    }

    public static GeometryJoin.Joined geometry(String units, org.locationtech.jts.geom.Geometry geometry,
                                               boolean strokeOnly, List<ToolGeometry> tools, Layout layout) {
        List<GeometryJoin.Source> copies = new ArrayList<>();
        for (double[] cell : layout.offsets()) {
            TransformOp offset = new TransformOp.Offset(cell[0], cell[1]);
            copies.add(new GeometryJoin.Source(units, offset.apply(geometry), strokeOnly,
                    tools.stream().map(tool -> tool.transformed(offset)).toList()));
        }
        if (copies.size() == 1) {
            return new GeometryJoin.Joined(geometry, strokeOnly, tools);
        }
        return GeometryJoin.join(copies, true);
    }
}
