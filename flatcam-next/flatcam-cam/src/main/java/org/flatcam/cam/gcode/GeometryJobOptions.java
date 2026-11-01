package org.flatcam.cam.gcode;

/** Optional job-level positions. Null preserves the existing automatic behaviour. */
public record GeometryJobOptions(Double startZ, Double endZ, Double endX, Double endY,
                                 Double toolChangeZ, Double toolChangeX, Double toolChangeY,
                                 boolean exclusionsEnabled, java.util.List<CncExclusionArea> exclusions, CncRouting routing,
                                 GeometryTravelClearance travelClearance, MachiningZPolicy zPolicy) {
    public GeometryJobOptions(Double startZ,Double endZ,Double endX,Double endY,Double toolChangeZ,Double toolChangeX,Double toolChangeY,
                              boolean exclusionsEnabled,java.util.List<CncExclusionArea> exclusions,CncRouting routing,GeometryTravelClearance travelClearance) {
        this(startZ,endZ,endX,endY,toolChangeZ,toolChangeX,toolChangeY,exclusionsEnabled,exclusions,routing,travelClearance,MachiningZPolicy.STANDARD);
    }
    public GeometryJobOptions(Double startZ,Double endZ,Double endX,Double endY,Double toolChangeZ,Double toolChangeX,Double toolChangeY,
                              boolean exclusionsEnabled,java.util.List<CncExclusionArea> exclusions,CncRouting routing) {
        this(startZ,endZ,endX,endY,toolChangeZ,toolChangeX,toolChangeY,exclusionsEnabled,exclusions,routing,GeometryTravelClearance.JOB_MAXIMUM);
    }
    public GeometryJobOptions(Double startZ,Double endZ,Double endX,Double endY,Double toolChangeZ,Double toolChangeX,Double toolChangeY,
                              boolean exclusionsEnabled,java.util.List<CncExclusionArea> exclusions) {
        this(startZ,endZ,endX,endY,toolChangeZ,toolChangeX,toolChangeY,exclusionsEnabled,exclusions,CncRouting.LEGACY);
    }
    public GeometryJobOptions(Double startZ,Double endZ,Double endX,Double endY,Double toolChangeZ,Double toolChangeX,Double toolChangeY) {
        this(startZ,endZ,endX,endY,toolChangeZ,toolChangeX,toolChangeY,false,java.util.List.of());
    }
    public static final GeometryJobOptions AUTOMATIC = new GeometryJobOptions(null, null, null, null, null, null, null);
    public GeometryJobOptions {
        java.util.Objects.requireNonNull(routing);
        java.util.Objects.requireNonNull(travelClearance);
        java.util.Objects.requireNonNull(zPolicy);
        exclusions=java.util.List.copyOf(exclusions);
        if(exclusions.size()>100) throw new IllegalArgumentException("Maximo de 100 exclusoes por trabalho.");
        if(zPolicy==MachiningZPolicy.STANDARD) {
            nonNegative(startZ, "Start Z"); nonNegative(endZ, "End Z"); positive(toolChangeZ, "Tool change Z");
        } else {
            if(startZ!=null) MachiningZPolicy.finite(startZ,"Start Z");
            if(endZ!=null) MachiningZPolicy.finite(endZ,"End Z");
            if(toolChangeZ!=null) MachiningZPolicy.finite(toolChangeZ,"Tool change Z");
        }
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
    public GeometryJobOptions withExclusions(boolean enabled,java.util.List<CncExclusionArea> areas) {
        return new GeometryJobOptions(startZ,endZ,endX,endY,toolChangeZ,toolChangeX,toolChangeY,enabled,areas,routing,travelClearance,zPolicy);
    }
    public GeometryJobOptions withRouting(CncRouting value) {
        return new GeometryJobOptions(startZ,endZ,endX,endY,toolChangeZ,toolChangeX,toolChangeY,exclusionsEnabled,exclusions,value,travelClearance,zPolicy);
    }
    public GeometryJobOptions withTravelClearance(GeometryTravelClearance value) {
        return new GeometryJobOptions(startZ,endZ,endX,endY,toolChangeZ,toolChangeX,toolChangeY,exclusionsEnabled,exclusions,routing,value,zPolicy);
    }
    public GeometryJobOptions withZPolicy(MachiningZPolicy policy) {
        return new GeometryJobOptions(startZ,endZ,endX,endY,toolChangeZ,toolChangeX,toolChangeY,exclusionsEnabled,exclusions,routing,travelClearance,policy);
    }
    public boolean isAutomatic() {
        return startZ==null && endZ==null && endX==null && toolChangeZ==null && toolChangeX==null && !exclusionsEnabled
                && routing.equals(CncRouting.LEGACY) && travelClearance==GeometryTravelClearance.JOB_MAXIMUM;
    }
    public void validate(GCodePreprocessor profile, double clearance, boolean toolChange) {
        if(exclusionsEnabled && exclusions.isEmpty()) throw new IllegalArgumentException("Exclusoes ativadas: desenhe ao menos uma area ou desmarque a opcao.");
        if(profile.isLaser()) {
            if(startZ!=null || toolChangeZ!=null || toolChangeX!=null || exclusionsEnabled
                    || !routing.equals(CncRouting.LEGACY) || travelClearance!=GeometryTravelClearance.JOB_MAXIMUM)
                throw new IllegalArgumentException("Laser permite End Z/XY; Start Z, troca mecanica, exclusoes e roteamento avancado ainda nao sao suportados.");
            return;
        }
        if (!isAutomatic() && (profile.isLaser() || profile.isPlotter() || profile.isRoland() || profile.requiresProbe()))
            throw new IllegalArgumentException("Posicoes comuns avancadas exigem perfil de fresagem sem sondagem. A sonda possui seu proprio painel de troca.");
        if (zPolicy==MachiningZPolicy.STANDARD && toolChange && toolChangeZ != null && toolChangeZ < clearance)
            throw new IllegalArgumentException("Tool change Z deve ser maior ou igual ao maior Travel Z das ferramentas.");
    }
}
