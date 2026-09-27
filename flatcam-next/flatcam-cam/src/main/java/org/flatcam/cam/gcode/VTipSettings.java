package org.flatcam.cam.gcode;

/** V-cutter tip geometry; the programmed width determines its cutting depth. */
public record VTipSettings(double tipDiameter, double angleDegrees) {
    public VTipSettings {
        if (!Double.isFinite(tipDiameter) || tipDiameter < 0)
            throw new IllegalArgumentException("V-Tip Dia deve ser nao negativo.");
        if (!Double.isFinite(angleDegrees) || angleDegrees <= 0 || angleDegrees >= 180)
            throw new IllegalArgumentException("V-Tip Angle deve estar entre 0 e 180 graus.");
    }

    public double cutDepth(double toolDiameter) {
        if (!Double.isFinite(toolDiameter) || toolDiameter <= tipDiameter)
            throw new IllegalArgumentException("Tool Dia deve ser maior que V-Tip Dia para produzir corte.");
        double depth = (toolDiameter - tipDiameter) / (2 * Math.tan(Math.toRadians(angleDegrees / 2)));
        if (!Double.isFinite(depth) || depth <= 0)
            throw new IllegalArgumentException("Profundidade V calculada invalida.");
        return depth;
    }
}
