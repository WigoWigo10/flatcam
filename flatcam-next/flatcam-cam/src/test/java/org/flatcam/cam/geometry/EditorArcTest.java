package org.flatcam.cam.geometry;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.*;

class EditorArcTest {
    private static final GeometryFactory F = new GeometryFactory();
    private static Coordinate p(double x, double y) { return new Coordinate(x,y); }

    @Test void agreesWithFortyOriginalPythonMathControlsInMmAndInches() throws Exception {
        try (var stream = getClass().getResourceAsStream("python-editor-arcs.json")) {
            assertNotNull(stream);
            var root = new JSONObject(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
            assertEquals(40, root.getJSONArray("cases").length());
            for (Object value : root.getJSONArray("cases")) {
                JSONObject c = (JSONObject) value;
                var input = c.getJSONArray("points");
                var points = java.util.stream.IntStream.range(0,3).mapToObj(i ->
                        p(input.getJSONArray(i).getDouble(0),input.getJSONArray(i).getDouble(1))).toList();
                var result = EditorArc.build(F, EditorArc.Mode.valueOf(c.getString("mode")), c.getBoolean("clockwise"), points);
                var expected = c.getJSONArray("expected");
                String label = c.getString("mode") + " " + c.getString("units") + " " + points;
                assertEquals(expected.length(), result.path().getNumPoints(), label);
                for (int i = 0; i < expected.length(); i++) {
                    assertEquals(expected.getJSONArray(i).getDouble(0), result.path().getCoordinateN(i).x, 1e-11, label);
                    assertEquals(expected.getJSONArray(i).getDouble(1), result.path().getCoordinateN(i).y, 1e-11, label);
                }
            }
            var difference = root.getJSONArray("knownPythonDifferences").getJSONObject(0);
            var legacy = difference.getJSONArray("expectedPython");
            Coordinate[] legacyPoints = new Coordinate[legacy.length()];
            for (int i=0;i<legacy.length();i++) legacyPoints[i]=p(legacy.getJSONArray(i).getDouble(0),legacy.getJSONArray(i).getDouble(1));
            var through=F.createPoint(p(-5,0));
            assertTrue(F.createLineString(legacyPoints).distance(through)>7,
                    "Original Python must reproduce the documented complementary-arc error");
            var corrected=EditorArc.build(F,EditorArc.Mode.START_END_THROUGH,false,List.of(p(5,0),p(0,5),p(-5,0)));
            assertTrue(corrected.clockwise()); assertEquals(0,corrected.path().distance(through),1e-12);
        }
    }

    @Test void centerModeProjectsTheStopClickOntoTheStartRadiusAndCanMakeAFullCircle() {
        var r = EditorArc.build(F, EditorArc.Mode.CENTER_START_END, false, List.of(p(0,0),p(5,0),p(0,12)));
        assertEquals(5,r.radius()); assertTrue(r.path().getCoordinateN(r.path().getNumPoints()-1).distance(p(0,5))<1e-12);
        var circle = EditorArc.build(F, EditorArc.Mode.CENTER_START_END, true, List.of(p(0,0),p(5,0),p(5,0)));
        assertTrue(circle.path().isClosed()); assertEquals(65,circle.path().getNumPoints());
    }

    @Test void hintCenterUsesDistanceToMidpointAndNotTheClickedPosition() {
        var r = EditorArc.build(F, EditorArc.Mode.START_END_CENTER, true, List.of(p(-5,0),p(5,0),p(2,3)));
        assertEquals(0,r.center().x); assertEquals(Math.sqrt(13),r.center().y,1e-12);
    }

    @Test void invalidPointsDoNotAddHistoryAndTranslatedTinyArcsRemainUsable() {
        var session = new GeometryEditSession(F.createGeometryCollection(),List.of());
        assertThrows(IllegalArgumentException.class,()->session.addArc(EditorArc.Mode.START_END_THROUGH,true,
                List.of(p(0,0),p(2,0),p(1,0)),-1));
        assertFalse(session.canUndo());
        assertThrows(IllegalArgumentException.class,()->EditorArc.build(F,EditorArc.Mode.CENTER_START_END,true,
                List.of(p(0,0),p(0,0),p(1,0))));
        assertThrows(IllegalArgumentException.class,()->EditorArc.build(F,EditorArc.Mode.CENTER_START_END,true,
                List.of(p(0,0),p(1,0),p(Double.NaN,1))));
        var r = EditorArc.build(F,EditorArc.Mode.START_END_THROUGH,false,
                List.of(p(10000.001,10000),p(9999.999,10000),p(10000,10000.001)));
        assertEquals(.001,r.radius(),1e-10);
    }

    @Test void insertionRetainsToolAndMillingDirectionAndUndoIsOneTransaction() {
        var original = F.createLineString(new Coordinate[]{p(20,20),p(21,20)});
        for (boolean climb : new boolean[]{true,false}) {
            var session = new GeometryEditSession(original,List.of(new ToolGeometry(.8,original)));
            session.setClimbMilling(climb);
            var points = List.of(p(0,0),p(5,0),p(0,5));
            var expected = EditorArc.build(F,EditorArc.Mode.CENTER_START_END,true,points).path();
            session.addArc(EditorArc.Mode.CENTER_START_END,true,points,0);
            assertEquals(2,session.shapeCount());
            Geometry added = session.resultGeometry().getGeometryN(1);
            assertEquals(climb ? expected.reverse() : expected, added);
            assertEquals(.8,session.resultTools().getFirst().toolDiameter());
            assertTrue(session.undo()); assertEquals(1,session.shapeCount());
            assertTrue(session.redo()); assertEquals(2,session.shapeCount());
        }
    }

    @Test void customResolutionsAgreeWithEighteenOriginalPythonControls() throws Exception {
        try(var stream=getClass().getResourceAsStream("python-editor-arcs.json")) {
            assertNotNull(stream);
            var cases=new JSONObject(new String(stream.readAllBytes(),StandardCharsets.UTF_8)).getJSONArray("resolutionCases");
            assertEquals(18,cases.length());
            for(Object value:cases) {
                JSONObject c=(JSONObject)value; var input=c.getJSONArray("points");
                var points=java.util.stream.IntStream.range(0,3).mapToObj(i->p(input.getJSONArray(i).getDouble(0),input.getJSONArray(i).getDouble(1))).toList();
                var result=EditorArc.build(F,EditorArc.Mode.valueOf(c.getString("mode")),c.getBoolean("clockwise"),points,c.getInt("steps"));
                var expected=c.getJSONArray("expected"); String label=c.getString("mode")+" "+c.getString("units")+" steps="+c.getInt("steps");
                assertEquals(expected.length(),result.path().getNumPoints(),label);
                for(int i=0;i<expected.length();i++) {
                    assertEquals(expected.getJSONArray(i).getDouble(0),result.path().getCoordinateN(i).x,1e-11,label);
                    assertEquals(expected.getJSONArray(i).getDouble(1),result.path().getCoordinateN(i).y,1e-11,label);
                }
            }
        }
    }

    @Test void customResolutionControlsArcsAndCirclesAndInvalidValuesDoNotChangeTheDraft() {
        for (int steps : new int[]{4,65,128,4096}) {
            var full=EditorArc.build(F,EditorArc.Mode.CENTER_START_END,true,List.of(p(0,0),p(5,0),p(5,0)),steps);
            assertEquals(steps+1,full.path().getNumPoints()); assertTrue(full.path().isClosed());
            var session=new GeometryEditSession(F.createGeometryCollection(),List.of());
            session.addCircle(0,0,5,0,-1,steps); assertEquals(4*(steps/4)+1,session.resultGeometry().getGeometryN(0).getNumPoints());
            assertTrue(session.resultGeometry().isValid()); assertTrue(session.undo()); assertFalse(session.isDirty());
            for (int bad : new int[]{0,3,4097,Integer.MAX_VALUE})
                assertThrows(IllegalArgumentException.class,()->session.addCircle(0,0,5,0,-1,bad));
            assertFalse(session.isDirty());
        }
    }
}
