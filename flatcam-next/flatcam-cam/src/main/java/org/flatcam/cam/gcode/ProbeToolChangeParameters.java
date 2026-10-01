package org.flatcam.cam.gcode;

/** Mach3's two-pass Z probing cycle. All lengths/feeds use the source object's MM or IN units. */
public record ProbeToolChangeParameters(double toolChangeZ, double probeDepth, double feedRate,
                                         double contactZ, Double toolChangeX, Double toolChangeY) {
    public ProbeToolChangeParameters {
        if (!Double.isFinite(toolChangeZ) || toolChangeZ <= 0 || rounded(toolChangeZ) <= 0)
            throw new IllegalArgumentException("Tool change Z da sonda deve ser positivo.");
        if (!Double.isFinite(probeDepth) || probeDepth >= 0 || rounded(probeDepth) >= 0)
            throw new IllegalArgumentException("Probe depth deve ser negativo e representavel com 4 casas decimais.");
        if (!Double.isFinite(feedRate) || feedRate <= 0 || rounded(feedRate / 2) <= 0)
            throw new IllegalArgumentException("Probe feed e metade do avanco devem ser positivos e representaveis.");
        if (!Double.isFinite(contactZ) || contactZ < 0)
            throw new IllegalArgumentException("Contact Z (espessura da placa) deve ser nao negativo.");
        if ((toolChangeX == null) != (toolChangeY == null)
                || toolChangeX != null && (!Double.isFinite(toolChangeX) || !Double.isFinite(toolChangeY)))
            throw new IllegalArgumentException("Probe X,Y devem ser ambos finitos ou ambos vazios.");
    }

    public void validateTravelZ(double travelZ) {
        if (!Double.isFinite(travelZ) || rounded(travelZ) <= rounded(contactZ)
                || rounded(toolChangeZ) < rounded(travelZ)
                || rounded(retractZ(travelZ)) <= rounded(contactZ))
            throw new IllegalArgumentException("Travel Z deve superar Contact Z; Tool change Z deve ser pelo menos Travel Z. "
                    + "A retracao intermediaria deve ser representavel acima do contato.");
    }

    public double retractZ(double travelZ) {
        return contactZ / 2 + travelZ / 2;
    }

    private static double rounded(double value) {
        return Double.parseDouble(GCodeGenerator.fmt(value));
    }

    /** Caller has already emitted Tn. M0 confirmations guard each G92 against an unverified contact. */
    public String cycle(int tool, double diameter, String units, double travelZ) {
        validateTravelZ(travelZ);
        GCodePreprocessor.TOOLCHANGE_PROBE_MACH3.unitsCode(units);
        StringBuilder code = new StringBuilder("M05\nG00 Z").append(GCodeGenerator.fmt(toolChangeZ))
                .append("\nM6\nM05\nG90\n")
                .append("MM".equalsIgnoreCase(units) ? "G21\n" : "G20\n")
                .append("G17\nG94\nG00 Z").append(GCodeGenerator.fmt(toolChangeZ)).append('\n');
        if (toolChangeX != null) code.append("G00 X").append(GCodeGenerator.fmt(toolChangeX))
                .append(" Y").append(GCodeGenerator.fmt(toolChangeY)).append('\n');
        code.append("(MSG, Troque para T").append(tool).append(" D").append(GCodeGenerator.fmt(diameter))
                .append("; conecte e teste a sonda; confira Z aproximado e G92/G52 existentes)\nM0\n");
        for (int pass = 0; pass < 2; pass++) {
            code.append("G31 Z").append(GCodeGenerator.fmt(probeDepth)).append(" F")
                    .append(GCodeGenerator.fmt(pass == 0 ? feedRate : feedRate / 2)).append('\n')
                    .append("(MSG, Confirme contato real da sonda; se nao houve contato ABORTE sem continuar)\nM0\n")
                    .append("G92 Z").append(GCodeGenerator.fmt(contactZ)).append('\n')
                    .append("G00 Z").append(GCodeGenerator.fmt(pass == 0 ? retractZ(travelZ) : travelZ)).append('\n');
        }
        return code.append("(MSG, Remova a placa e todos os clips da sonda antes de continuar)\nM0\n").toString();
    }
}
