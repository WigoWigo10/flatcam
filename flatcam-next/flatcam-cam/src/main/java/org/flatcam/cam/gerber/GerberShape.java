package org.flatcam.cam.gerber;

import org.locationtech.jts.geom.Geometry;

/**
 * One individual flash, stroke or region exactly as the file drew it, before
 * any union - ParseGerber.py's per-aperture {@code apertures[code]['geometry']}
 * element ({@code {'solid': ...}} for dark polarity, {@code {'clear': ...}}
 * for clear). The Gerber Editor selects and edits these, not the unioned
 * {@link GerberImage#solidGeometry()}.
 *
 * @param apertureCode the D-code that drew it, or {@link #REGION_APERTURE} for a G36/G37 region
 * @param clear        true for LPC (clear polarity) shapes, which the editor never selects
 */
public record GerberShape(String apertureCode, Geometry geometry, boolean clear) {

    /** ParseGerber.py files region fills under aperture '0' (type 'REG'); same key here. */
    public static final String REGION_APERTURE = "0";
}
