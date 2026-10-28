package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.*;
import org.flatcam.app.job.*;
import org.flatcam.cam.excellon.*;
import org.flatcam.cam.gcode.*;
import org.flatcam.cam.geometry.*;
import org.locationtech.jts.geom.*;

public final class UiNumbersProbe {
    public static void main(String[] args) {
        int exit=0;
        try {
            Path output=Path.of(args[1]); boolean custom=args[0].equals("custom");
            var expected=custom ? new UiNumbers.Settings("IN",2,8) : UiNumbers.Settings.DEFAULT;
            assertTrue(System.getProperty("java.util.prefs.PreferencesFactory","").contains("MemoryFactory"));
            assertEquals(UiNumbers.Settings.DEFAULT,AppPreferences.loadNumericSettings());
            var prefs=java.util.prefs.Preferences.userNodeForPackage(AppPreferences.class);
            prefs.put("initialUnits","cm"); prefs.putInt("displayDecimalsMetric",17); prefs.put("displayDecimalsInch","not-a-number");
            assertEquals(UiNumbers.Settings.DEFAULT,AppPreferences.loadNumericSettings());
            AppPreferences.saveNumericSettings(expected); assertEquals(expected,AppPreferences.loadNumericSettings());
            UiNumbers.initialize(AppPreferences.loadNumericSettings()); UiNumbers.initialize(new UiNumbers.Settings("MM",3,3));
            assertEquals(expected,UiNumbers.settings());
            AppPreferences.saveNumericSettings(new UiNumbers.Settings("MM",6,6));
            assertEquals(expected,UiNumbers.settings(),"saved changes wait for another startup");
            Locale original=Locale.getDefault();
            var jobs=new JobExecutor(1);
            var window=TerminalPanelTest.fx(()->{var w=new MainWindow(jobs);w.createScene();return w;});
            try {
                TerminalPanelTest.fx(()-> {
                    assertEquals(!custom,window.displayUnitsMetric());
                    var empty=window.newEmptyExcellonObject();
                    @SuppressWarnings("unchecked") Map<TreeItem<String>,ExcellonImage> images=(Map<TreeItem<String>,ExcellonImage>)field(window,"excellonByItem");
                    assertEquals(expected.initialUnits(),images.get(empty).units());
                    var emptyGeo=window.newEmptyGeometryObject();
                    @SuppressWarnings("unchecked") Map<TreeItem<String>,?> geometries=(Map<TreeItem<String>,?>)field(window,"geometryByItem");
                    var entry=geometries.get(emptyGeo);var unit=entry.getClass().getDeclaredMethod("units");unit.setAccessible(true);
                    assertEquals(expected.initialUnits(),unit.invoke(entry));
                    String mm=custom ? "1.23" : "1.2346", in=custom ? "1.23456789" : "1.2346";
                    assertTrue(PlotAreaView.formatHud(1.23456789,0,0,0,"MM").startsWith("Dx: "+mm+" [mm]"));
                    assertTrue(PlotAreaView.formatHud(1.23456789,0,0,0,"IN").startsWith("Dx: "+in+" [in]"));
                    assertEquals("X: "+in+"   Y: "+in,UiNumbers.coordinates(1.23456789,1.23456789,"IN"));
                    for(String units:List.of("MM","IN")) {
                        var panel=new MeasurementToolPanel(false,units,()->{},xy->{},()->{});
                        var measured=new MeasurementToolPanel.Measurement(0,0,1.23456789,0); panel.showResult(measured);
                        assertEquals(units.equals("MM") ? mm : in,((TextField)panel.lookup("#measure-distance")).getText());
                        assertEquals(1.23456789,measured.distance());
                        var image=new ExcellonParser().parse(List.of("M48",units.equals("MM") ? "METRIC" : "INCH","T1C1.23456789","%","T1","X1.23456789Y2.34567891","M30"));
                        TableView<?> table=(TableView<?>)ExcellonObjectToolsTable.build(image);
                        assertEquals(units.equals("MM") ? mm : in,table.getColumns().get(1).getCellData(0));
                        assertEquals(1.23456789,image.drills().getFirst().x());
                        var aperture=org.flatcam.cam.gerber.Aperture.rectangle(1.23456789,2.34567891);
                        var gerber=org.flatcam.cam.gerber.GerberImage.of(units,Map.of("10",aperture),
                                new GeometryFactory().createGeometryCollection(),new GeometryFactory().createGeometryCollection(),Map.of());
                        var apertures=(javafx.scene.layout.VBox)GerberAperturesTable.build(gerber,(code,marked)->{});
                        var apertureTable=(TableView<?>)apertures.getChildren().get(1);
                        assertEquals((units.equals("MM") ? mm+" x "+(custom ? "2.35" : "2.3457") : in+" x "+(custom ? "2.34567891" : "2.3457")),
                                apertureTable.getColumns().get(4).getCellData(0));
                        assertEquals(1.23456789,aperture.width);
                    }
                    return null;
                });
                Path source=output.resolve("input.drl");
                Files.writeString(source,"M48\nMETRIC\nT1C1.23456789\n%\nT1\nX1.23456789Y2.34567891\nM30\n");
                window.openExcellon(source,"imported-mm");
                TerminalPanelTest.fx(()-> {
                    assertTrue(window.displayUnitsMetric(),"imported units override the startup preference");
                    var add=MainWindow.class.getDeclaredMethod("addGeometryToProject",String.class,String.class,String.class,Geometry.class,boolean.class);
                    add.setAccessible(true); var factory=new GeometryFactory();
                    @SuppressWarnings("unchecked") var first=(TreeItem<String>)add.invoke(window,"measure-a","","MM",factory.createPoint(new Coordinate(0,0)),false);
                    @SuppressWarnings("unchecked") var second=(TreeItem<String>)add.invoke(window,"measure-b","","MM",factory.createPoint(new Coordinate(1.23456789,0)),false);
                    @SuppressWarnings("unchecked") var tree=(TreeView<String>)field(window,"projectTree");
                    tree.getSelectionModel().clearSelection(); tree.getSelectionModel().select(first);tree.getSelectionModel().select(second);
                    assertEquals(1.23456789,window.measureMinimumDistance().distance());
                    assertEquals(I18n.text("ui.3da10b96c423")+(custom ? "1.23" : "1.2346"),((Label)field(window,"statusLabel")).getText(),
                            "measurement without an open panel must use current units, not startup units");
                    var method=MainWindow.class.getDeclaredMethod("clearProject",boolean.class);method.setAccessible(true);method.invoke(window,false);
                    assertEquals(!custom,window.displayUnitsMetric(),"empty projects return to active startup units");
                    return null;
                });
            } finally { TerminalPanelTest.fx(()->{window.disposeViewport();return null;});jobs.shutdown(); }
            var line=new GeometryFactory().createLineString(new Coordinate[]{new Coordinate(0,0),new Coordinate(10.123456789,2.34567891)});
            var generated=GeometryCncGeneration.generate("MM",List.of(new ToolGeometry(.512345678,line,ToolProfile.C2)),
                    new GeometryGCodeParameters(2,.23456789,true,.1,100.123456,10000,false),Map.of(),Map.of(),GCodePreprocessor.GRBL_11_NO_M6,
                    output.resolve("job.nc"),new JobContext(){public boolean isCancelled(){return false;}public void reportProgress(double n,String s){}},()->{});
            Files.writeString(output.resolve("job.csv"),CncStepCsv.toCsv(generated.preview().stats().steps(),"MM"));
            // A real drill section supplies the per-tool table. Geometry programs expose different statistics.
            var drill=GCodeToolpathParser.parse("G21\nG90\nT1\n(FCFX TOOL T1 D0.5123)\nG0 Z2\nG0 X0 Y0\nG1 Z-0.2346 F100\nG0 Z2\nM30\n",
                    org.flatcam.cam.CancellationToken.none(),org.flatcam.cam.ProgressCallback.none());
            TerminalPanelTest.fx(()-> {
                var table=(TableView<?>)CncJobToolsTable.build(drill.stats(),"MM",new HashSet<>(),()->{});
                assertFalse(table.getItems().isEmpty());
                assertEquals(custom ? "-0.23" : "-0.2346",table.getColumns().get(4).getCellData(0));
                var inches=(TableView<?>)CncJobToolsTable.build(drill.stats(),"IN",new HashSet<>(),()->{});
                assertEquals(custom ? "-0.23460000" : "-0.2346",inches.getColumns().get(4).getCellData(0));
                return null;
            });
            assertEquals(original,Locale.getDefault());
            System.out.println("PASS "+args[0]+": startup units, separate precision, unchanged geometry/imports, restart and machine output");
        } catch(Throwable error){error.printStackTrace();exit=1;}
        finally {Platform.exit();}
        System.exit(exit);
    }
    private static Object field(Object owner,String name) throws Exception {
        var field=owner.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(owner);
    }
}
