package org.flatcam.cam.gcode;

/** Optional job-level positions. Null preserves the existing automatic behaviour. */
public record GeometryJobOptions(Double startZ, Double endZ, Double endX, Double endY,
                                 Double toolChangeZ, Double toolChangeX, Double toolChangeY) {
    public static final GeometryJobOptions AUTOMATIC = new GeometryJobOptions(null, null, null, null, null, null, null);
    public GeometryJobOptions {
        nonNegative(startZ, "Start Z"); nonNegative(endZ, "End Z"); positive(toolChangeZ, "Tool change Z");
        xy(endX, endY, "End X,Y"); xy(toolChangeX, toolChangeY, "Tool change X,Y");
    }
    private static void nonNegative(Double value, String label) {
        if (value != null && (!Double.isFinite(value) || value < 0))
            throw new IllegalArgumentException(label + " deve ser nao negativo e finito.");
    }
    private static void positive(Double value, String label) {
        if (value != null && (!Double.isFinite(value) || value <= 0))
            throw new IllegalArgumentException(label + " deve ser positivo e finito.");
    }
    private static void xy(Double x, Double y, String label) {
        if ((x == null) != (y == null) || x != null && (!Double.isFinite(x) || !Double.isFinite(y)))
            throw new IllegalArgumentException(label + " exige duas coordenadas finitas.");
    }
    public boolean isAutomatic() { return equals(AUTOMATIC); }
    public void validate(GCodePreprocessor profile, double clearance, boolean toolChange) {
        if (!isAutomatic() && (profile.isLaser() || profile.isPlotter() || profile.isRoland() || profile.requiresProbe()))
            throw new IllegalArgumentException("Posicoes comuns avancadas exigem perfil de fresagem sem sondagem. A sonda possui seu proprio painel de troca.");
        if (toolChange && toolChangeZ != null && toolChangeZ < clearance)
            throw new IllegalArgumentException("Tool change Z deve ser maior ou igual ao maior Travel Z das ferramentas.");
    }
}
