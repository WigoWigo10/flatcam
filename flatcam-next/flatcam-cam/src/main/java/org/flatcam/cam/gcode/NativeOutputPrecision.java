package org.flatcam.cam.gcode;

/** Text quantization used by the native encoders; never a mechanical accuracy estimate. */
public final class NativeOutputPrecision {
    public static final double HPGL_STEP_MM = .025;
    public static final double ROLAND_TEXT_STEP_MM = .0025;
    public static final double ROLAND_FEED_STEP_MM_MIN = 6;

    private NativeOutputPrecision() { }

    public static double coordinate(GCodePreprocessor profile, String units, double requested) {
        if (!Double.isFinite(requested)) throw new IllegalArgumentException("Coordenada deve ser finita.");
        if (profile.isPlotter()) return HpglProgramCodec.coordinate(requested,units)/hpglScale(units);
        if (!profile.isRoland()) throw new IllegalArgumentException("Perfil nativo obrigatorio.");
        if (!"MM".equalsIgnoreCase(units)) throw new IllegalArgumentException("Roland exige MM.");
        // Generation first formats ordinary MM coordinates/Z with four decimals, then encodes RML.
        return RolandProgramCodec.coordinate(Double.parseDouble(GCodeGenerator.fmt(requested)))/40;
    }

    public static double rolandFeed(double requested, boolean rapid) {
        double input=rapid ? requested==0 ? 900 : requested : Double.parseDouble(GCodeGenerator.fmt(requested));
        return RolandProgramCodec.velocity(input)*60;
    }

    public static double hpglMinimum(String units) { return -32767/hpglScale(units); }
    public static double hpglMaximum(String units) { return 32768/hpglScale(units); }

    private static double hpglScale(String units) {
        if ("MM".equalsIgnoreCase(units)) return 40;
        if ("IN".equalsIgnoreCase(units)) return 1016;
        throw new IllegalArgumentException("HPGL exige MM ou IN.");
    }
}
