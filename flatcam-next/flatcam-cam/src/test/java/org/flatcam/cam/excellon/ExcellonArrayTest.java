package org.flatcam.cam.excellon;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import org.flatcam.cam.*;
import org.junit.jupiter.api.Test;

class ExcellonArrayTest {
    static ExcellonArray.Parameters parameters(ExcellonArray.Kind kind,boolean slots,double u,boolean cw,boolean rotate) {
        return new ExcellonArray.Parameters(kind,4,.8*u,slots,3*u,0,rotate,10*u,20*u,2*u,90,5*u,180,90,cw);
    }
    static ExcellonArray.Prepared generate(ExcellonArray.Parameters p) { return ExcellonArray.generate(p,CancellationToken.none(),ProgressCallback.none()); }
    @Test void linearArrayUsesTotalCountAndObjectUnits() {
        for(double u:new double[]{1,1/25.4}) for(boolean slots:List.of(false,true)) {
            var result=generate(parameters(ExcellonArray.Kind.LINEAR,slots,u,true,true));
            assertEquals(4,result.elements().size());
            for(int i=0;i<4;i++) {
                var e=result.elements().get(i); double cx=(e.x1()+e.x2())/2,cy=(e.y1()+e.y2())/2;
                assertEquals(10*u,cx,1e-12); assertEquals((20+2*i)*u,cy,1e-12);
                assertEquals(slots ? 2.2*u : 0,e.x2()-e.x1(),1e-12);
            }
        }
    }
    @Test void circularArraysHandleAllQuadrantsClockwiseAndCounterclockwiseAndSlotOrientation() {
        for(double u:new double[]{1,1/25.4}) for(boolean cw:List.of(false,true)) for(boolean rotate:List.of(false,true)) {
            var result=generate(parameters(ExcellonArray.Kind.CIRCULAR,true,u,cw,rotate));
            double[][] expected=cw ? new double[][]{{5,20},{10,25},{15,20},{10,15}}
                    : new double[][]{{5,20},{10,15},{15,20},{10,25}};
            for(int i=0;i<4;i++) {
                var e=result.elements().get(i);
                assertEquals(expected[i][0]*u,(e.x1()+e.x2())/2,1e-12);
                assertEquals(expected[i][1]*u,(e.y1()+e.y2())/2,1e-12);
                double angle=rotate ? Math.toRadians(180+(cw ? -90 : 90)*i) : 0;
                assertEquals(2.2*u*Math.cos(angle),e.x2()-e.x1(),1e-12);
                assertEquals(2.2*u*Math.sin(angle),e.y2()-e.y1(),1e-12);
            }
        }
    }
    @Test void confirmationIsOneTransactionIncludingNewToolAndSurvivesDrillFileRoundtrip() {
        var session=new ExcellonEditSession(ExcellonImage.of("MM",Map.of(7,1.2),List.of(new ExcellonImage.Drill(7,-1,-2)),List.of(),null));
        var p=generate(parameters(ExcellonArray.Kind.CIRCULAR,true,1,true,true));
        var initial=session.geometry(); assertFalse(session.isDirty()); assertEquals(1,session.toolDiameters().size());
        assertEquals(4,session.addArray(p,session.revision()));
        assertEquals(.8,session.toolDiameters().get(8)); assertEquals(4,session.selectedCount());
        var rows=session.rows(); var combined=session.geometry(); assertSame(combined,session.geometry());
        var file=new ExcellonExporter().export(session.resultImage()); var restored=new ExcellonParser().parse(file.lines().toList());
        assertEquals(1,restored.drills().size()); assertEquals(4,restored.slots().size());
        for(int i=0;i<4;i++) assertEquals(p.elements().get(i).x2(),restored.slots().get(i).x2(),1e-6);
        assertTrue(session.undo()); assertFalse(session.isDirty()); assertEquals(Map.of(7,1.2),session.toolDiameters()); assertSame(initial,session.geometry());
        assertTrue(session.redo()); assertEquals(rows,session.rows()); assertSame(combined,session.geometry());
    }
    @Test void existingDiameterIsReusedAndOldResultsCannotApplyAfterUndoOrEdits() {
        var session=new ExcellonEditSession(ExcellonImage.of("IN",Map.of(3,.8),List.of(),List.of(),null));
        var p=generate(parameters(ExcellonArray.Kind.LINEAR,false,1,true,false)); long revision=session.revision();
        session.addArray(p,revision); assertEquals(Map.of(3,.8),session.toolDiameters());
        assertThrows(IllegalArgumentException.class,()->session.addArray(p,revision));
        session.undo(); assertThrows(IllegalArgumentException.class,()->session.addArray(p,revision));
        assertFalse(session.isDirty());
    }
    @Test void exportPublicOracleCoordinates() throws Exception {
        var rows=new ArrayList<String>(); rows.add("kind,slots,units,clockwise,angle,index,x1,y1,x2,y2");
        for(var kind:ExcellonArray.Kind.values()) for(boolean slots:List.of(false,true)) for(String units:List.of("MM","IN"))
            for(boolean cw:List.of(false,true)) for(double angle:new double[]{0,45,135,225}) {
                double u=units.equals("MM") ? 1 : 1/25.4;
                var p=new ExcellonArray.Parameters(kind,4,.8*u,slots,3*u,0,true,10*u,20*u,2*u,angle,5*u,angle,90,cw);
                var result=generate(p);
                for(int i=0;i<4;i++) { var e=result.elements().get(i);
                    rows.add(kind+","+slots+","+units+","+cw+","+angle+","+i+","+e.x1()+","+e.y1()+","+e.x2()+","+e.y2());
                }
            }
        java.nio.file.Files.createDirectories(java.nio.file.Path.of("target"));
        java.nio.file.Files.write(java.nio.file.Path.of("target/excellon-array-comparison.csv"),rows);
    }
    @Test void invalidPatternsAndCancellationNeverPublishPartialResults() {
        var p=parameters(ExcellonArray.Kind.LINEAR,false,1,true,false);
        for(int count:new int[]{0,10001}) assertThrows(IllegalArgumentException.class,()->new ExcellonArray.Parameters(p.kind(),count,p.diameter(),false,0,0,false,0,0,1,0,0,0,0,true));
        assertThrows(IllegalArgumentException.class,()->new ExcellonArray.Parameters(ExcellonArray.Kind.CIRCULAR,5,1,false,0,0,false,0,0,0,0,5,0,90,true));
        assertThrows(IllegalArgumentException.class,()->new ExcellonArray.Parameters(p.kind(),3,1,true,.5,0,false,0,0,1,0,0,0,0,true));
        var calls=new AtomicInteger(); assertThrows(CancellationException.class,()->ExcellonArray.generate(p,()->calls.incrementAndGet()>2,ProgressCallback.none()));
        var overflow=new ExcellonArray.Parameters(p.kind(),4,1,false,0,0,false,Double.MAX_VALUE,0,Double.MAX_VALUE,0,0,0,0,true);
        assertThrows(IllegalArgumentException.class,()->generate(overflow));
    }
}
