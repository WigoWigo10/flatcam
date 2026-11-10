import org.locationtech.jts.io.WKTReader;
import org.flatcam.cam.CancellationToken;
/** Standalone guard for the auditor: point samples alone can miss gaps between crossings. */
class TestRegionCncCoverage {
    public static void main(String[] args)throws Exception {
        var r=new WKTReader();var required=r.read("LINESTRING (0 0,10 0)");
        var gapped=r.read("MULTILINESTRING ((0 0,1 0),(4 0,6 0),(9 0,10 0))");
        double missing=CompareRegionCncProbe.uncoveredLength(required,gapped,1e-8,CancellationToken.NONE);
        if(Math.abs(missing-6)>1e-6)throw new AssertionError("Interior gaps must be detected: "+missing);
        var split=r.read("MULTILINESTRING ((10 0,6 0),(0 0,4 0),(4 0,6 0))");
        if(CompareRegionCncProbe.uncoveredLength(required,split,1e-8,CancellationToken.NONE)!=0)throw new AssertionError("Noding/reversal may preserve every interval");
        if(CompareRegionCncProbe.uncoveredLength(required,r.read("LINESTRING (0 0.0001,10 0.0001)"),1e-8,CancellationToken.NONE)<9.99)throw new AssertionError("Displaced cuts must fail");
        if(CompareRegionCncProbe.uncoveredLength(required,r.read("LINESTRING EMPTY"),1e-8,CancellationToken.NONE)!=10)throw new AssertionError("Empty output must fail");
        System.out.println("Region CNC coverage guards: 4 passed");
    }
}
