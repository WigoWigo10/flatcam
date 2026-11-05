package org.flatcam.fx;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.prefs.*;
import javafx.application.Platform;
import javafx.scene.control.TreeItem;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.stage.*;
import org.flatcam.app.job.JobExecutor;
import org.flatcam.cam.cutout.GapPattern;
import org.flatcam.cam.excellon.ExcellonImage;
import org.flatcam.cam.gcode.GCodePreprocessor;
import org.flatcam.cam.gerber.GerberImage;
import org.flatcam.cam.isolation.IsolationType;
import org.flatcam.cam.ncc.*;
import org.flatcam.cam.panel.Panelize;
import org.json.JSONObject;

/** Compiled only by test-beta.ps1, against the extracted/installed app jars. Never shipped as app code. */
public final class BetaSmokeProbe {
    private static MainWindow window;
    private static Stage stage;
    private static JobExecutor jobs;
    public static void main(String[] args) throws Exception {
        int exit=0;Path output=Path.of(args[0]);Files.createDirectories(output);
        try {
            check(System.getProperty("java.util.prefs.PreferencesFactory","").contains("BetaSmokeProbe"),"Preferences must be isolated");
            check(AboutInfo.version().equals(args[1]),"Wrong packaged application version");
            check(AboutInfo.build().getProperty("sourceRevision").equals(args[2]),"Wrong source revision");
            var location=Path.of(MainWindow.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            check(location.getFileName().toString().equals("flatcam-fx-"+args[1]+".jar"),"Application must load from packaged JAR");
            Platform.startup(()->Platform.setImplicitExit(false));
            fx(()-> {
                UiFonts.initialize(AppPreferences.loadFontSettings());UiNumbers.initialize(AppPreferences.loadNumericSettings());
                jobs=new JobExecutor(2);window=new MainWindow(jobs);stage=new Stage(StageStyle.UTILITY);
                stage.setOpacity(0);stage.setX(-20000);stage.setY(-20000);stage.setScene(window.createScene());stage.setWidth(1200);stage.setHeight(850);stage.show();
                window.openHelpGuide();var guide=(HelpGuidePane)stage.getScene().lookup("#help-guide");
                if(guide==null){stage.getScene().getRoot().applyCss();stage.getScene().getRoot().layout();guide=(HelpGuidePane)stage.getScene().lookup("#help-guide");}
                check(guide!=null,"Guide must open from packaged resources");guide.selectTopic("outline");return null;
            });
            String copper="%FSLAX24Y24*%\n%MOMM*%\n%ADD10C,0.1*%\nD10*\nG36*\nX010000Y010000D02*\nX090000Y010000D01*\nX090000Y070000D01*\nX010000Y070000D01*\nX010000Y010000D01*\nG37*\nM02*\n";
            String outline="%FSLAX24Y24*%\n%MOMM*%\n%ADD10C,0.05*%\nD10*\nX000000Y000000D02*\nX100000Y000000D01*\nX100000Y080000D01*\nX000000Y080000D01*\nX000000Y000000D01*\nM02*\n";
            String drills="M48\nMETRIC\nT1C0.8\n%\nT1\nX2.0Y2.0\nX7.0Y5.0\nX3.0Y3.0G85X4.0Y3.0\nM30\n";
            Path copperFile=output.resolve("Cobre-á.gbr"),edgeFile=output.resolve("Contorno.gbr"),drillFile=output.resolve("Furação.drl");
            Files.writeString(copperFile,copper);Files.writeString(edgeFile,outline);Files.writeString(drillFile,drills);
            window.openGerber(copperFile,"Copper");window.openGerber(edgeFile,"Edge_Cuts");window.openExcellon(drillFile,"Drills");
            checkExcellonDiameter();
            fx(()-> {call("selectProjectItem",new Class<?>[]{TreeItem.class},item("gerberByItem","Edge_Cuts"));call("convertOutlineToArea",new Class<?>[]{});return null;});
            awaitJob();check(window.objectNames().contains("Edge_Cuts_area"),"Outline conversion failed");
            fx(()-> {
                var reference=item("geometryByItem","Edge_Cuts_area");double[] box={0,0,10,8};
                var layout=Panelize.layout(box,2,2,3,3,Double.NaN,Double.NaN);
                var request=new PanelizeToolPanel.Request(List.of(item("gerberByItem","Copper"),item("gerberByItem","Edge_Cuts"),item("excellonByItem","Drills"),reference),reference,box,layout,false);
                call("runPanelize",new Class<?>[]{PanelizeToolPanel.Request.class},request);return null;
            });
            awaitJob();
            var panel=fx(()->(ExcellonImage)map("excellonByItem").get(item("excellonByItem","Drills_panelized")));
            check(panel.drills().size()==8 && panel.slots().size()==4,"Panelized drill/slot counts differ");
            for(double dx:new double[]{0,13})for(double dy:new double[]{0,11})
                check(panel.drills().stream().anyMatch(d->Math.abs(d.x()-(2+dx))<1e-9 && Math.abs(d.y()-(2+dy))<1e-9),"Shared panel pitch lost");
            checkCamDraftAndDiameter("NCC");
            checkCamDraftAndDiameter("Isolation");
            String internal=checkInternalCutouts();
            String iso=window.isolate("Copper_panelized","Isolation",.4,1,.1,IsolationType.BOTH);
            String ncc=window.nccClear("Copper_panelized","NCC",List.of(.8),.4,1,NccMethod.STANDARD,true,true,false,new NccBoundary.Itself());
            String cutout=window.cutoutRectangular("Edge_Cuts_area","Cutout",.8,.1,1,GapPattern.FOUR);
            var programs=new LinkedHashMap<String,String>();
            for(String geometry:List.of(iso,ncc,cutout,internal)) {
                String cnc=window.cncjob(geometry,geometry+"_cnc",geometry.equals(iso) ? .4 : .8,-.1,2,100,60,500);
                String code=window.exportGcode(cnc,"","");check(code.contains("G1") || code.contains("G01"),"Empty cutting program");
                window.writeGcode(cnc,output.resolve(cnc+".nc"),"","");programs.put(cnc,code);
            }
            var request=new TclDrillRequest(List.of(),null,1.6,false,.5,2,50,500,10000,false,5,null,null,2,null,false,0,GCodePreprocessor.GRBL_11_NO_M6,false);
            String drillCnc=window.drillcncjob("Drills_panelized","Drilling_cnc",request);
            programs.put(drillCnc,window.exportGcode(drillCnc,"",""));window.writeGcode(drillCnc,output.resolve("Drilling.nc"),"","");
            checkZPolicies();
            checkEditorFileSaves(output);
            var names=window.objectNames();
            Path nativeProject=output.resolve("Painel-testado.fcnproj"),pythonProject=output.resolve("Painel-testado.FlatPrj");
            window.saveProject(nativeProject);window.saveProject(pythonProject);
            for(Path saved:List.of(nativeProject,pythonProject)) {
                window.openProject(saved,true);check(new HashSet<>(names).equals(new HashSet<>(window.objectNames())),"Object inventory lost after reopening "+saved.getFileName());
                for(var code:programs.entrySet())check(code.getValue().equals(window.exportGcode(code.getKey(),"","")),"G-code lost after reopening");
                check(((ExcellonImage)fx(()->map("excellonByItem").get(item("excellonByItem","Drills")))).toolDiameters().get(1)==.1981234567,
                        "Edited Excellon tool diameter lost after project reopen");
                var internalProject=saved.equals(nativeProject) ? org.flatcam.app.project.ProjectFileIO.load(saved)
                        : org.flatcam.app.project.PythonProjectIO.load(saved);
                var internalSaved=internalProject.geometries().stream()
                        .filter(g->g.name().equals(internal)).findFirst().orElseThrow();
                check(internalSaved.tools().getFirst().toolDiameter()==.1981234567,"Internal cutout tool precision lost");
                check(internalSaved.geometry().getNumGeometries()==1 && ((org.locationtech.jts.geom.LineString)internalSaved.geometry()).isClosed(),
                        "Internal cutout contour lost after reopening");
                if(saved.equals(nativeProject))checkSavedNccDraft();
            }
            check(Files.readString(copperFile).equals(copper) && Files.readString(edgeFile).equals(outline) && Files.readString(drillFile).equals(drills),"Original inputs were changed");
            Files.writeString(output.resolve("smoke.json"),new JSONObject().put("status","PASS").put("version",AboutInfo.version()).put("revision",args[2])
                    .put("appJar",location.toString()).put("objects",names.size()).put("panelDrills",8).put("panelSlots",4).put("programs",programs.size())
                    .put("checks",List.of("invisible main window","offline Outline guide","Gerber/Excellon import","Outline to Area","shared 2x2 panel",
                            "Isolation","NCC","Cutout","CNC and Drilling export","native and Python project reopen","source files unchanged",
                            "NCC Seed to Standard with preserved draft","NCC and Isolation edited diameter reaches generated geometry",
                            "exact Add spinner confirmation","Delete row versus text","global NCC draft resume","native NCC draft persistence",
                            "Standard and Machinist Z in Geometry Drilling Cutout MM IN",
                            "G-code original save and copy save","Cancel help and saved files preserved","CAM table undo and redo",
                            "Excellon properties diameter edit Plot undo redo and project persistence",
                            "internal cutout panel preview footprint validation CNC and project reopen",
                            "four language catalogs and SVG language flags")).toString(2));
            System.out.println("PASS beta smoke "+AboutInfo.version()+" "+args[2]);
        } catch(Throwable error) {error.printStackTrace();exit=1;}
        finally {
            try {if(window!=null)fx(()->{window.disposeViewport();stage.close();return null;});} catch(Exception error){error.printStackTrace();exit=1;}
            if(jobs!=null)jobs.shutdown();Platform.exit();
        }
        System.exit(exit);
    }
    private static String checkInternalCutouts() throws Exception {
        var factory=new org.locationtech.jts.geom.GeometryFactory();
        var board=factory.toGeometry(new org.locationtech.jts.geom.Envelope(0,40,0,30))
                .difference(factory.toGeometry(new org.locationtech.jts.geom.Envelope(5,15,5,13)));
        String original=board.toText();
        var source=fx(()->(TreeItem<String>)call("addGeometryToProject",new Class<?>[]{String.class,String.class,String.class,
                org.locationtech.jts.geom.Geometry.class,boolean.class},"Internal-source","","MM",board,false));
        var form=fx(()-> {
            for(UiLanguage language:UiLanguage.values()) {
                check(I18n.catalog(language).size()==I18n.catalog(UiLanguage.PT_BR).size(),"Incomplete packaged language catalog");
                check(I18n.catalog(language).containsKey("cutout.internal.legend_help"),"Internal cutout help missing");
                check(LanguageFlagIcons.icon(language,22)!=null,"Packaged SVG flag unavailable");
            }
            var entry=map("geometryByItem").get(source);
            call("generateCutout",new Class<?>[]{TreeItem.class,entry.getClass()},source,entry);
            var external=((ScrollPane)((Tab)field("toolTab")).getContent()).getContent();
            ((Button)external.lookup("#cutout-open-internal")).fire();
            return ((ScrollPane)((Tab)field("toolTab")).getContent()).getContent();
        });
        awaitCondition(()->((TableView<?>)form.lookup("#cutout-internal-openings")).getItems().size()==1);
        fx(()-> {
            ((TextField)form.lookup("#cutout-internal-diameter")).setText("50");
            ((TextField)form.lookup("#cutout-internal-margin")).setText("0");
            ((TextField)form.lookup("#cutout-internal-cut-z")).setText("-1.6");
            ((TableView<?>)form.lookup("#cutout-internal-openings")).getSelectionModel().select(0);return null;
        });
        awaitCondition(()->!((Label)form.lookup("#cutout-internal-error")).getText().isBlank());
        fx(()-> {
            check(form.lookup("#cutout-internal-generate").isDisabled(),"Invalid internal cutout can generate");
            ((TextField)form.lookup("#cutout-internal-diameter")).setText(".1981234567");return null;
        });
        awaitCondition(()->!form.lookup("#cutout-internal-generate").isDisabled());
        var before=fx(()->Set.copyOf(map("geometryByItem").keySet()));
        fx(()-> {
            check(form.lookup("#cutout-internal-legend")!=null && form.lookup("#cutout-internal-summary")!=null,"Preview legend/summary missing");
            check(form.lookup("#cutout-internal-frame").getAccessibleHelp()!=null,"Frame selection help missing");
            var plot=(PlotAreaView)field("plotAreaView");var f=PlotAreaView.class.getDeclaredField("internalCutoutPreview");f.setAccessible(true);
            var preview=(InternalCutoutPreview)f.get(plot);
            check(preview!=null && preview.swept()!=null && preview.paths()!=null,"Cutter footprint preview missing");
            check(!preview.swept().covers(factory.createPoint(new org.locationtech.jts.geom.Coordinate(10,9))),"Preview incorrectly shows a cleared pocket");
            ((Button)form.lookup("#cutout-internal-generate")).fire();return null;
        });awaitJob();
        return fx(()-> {
            var added=map("geometryByItem").keySet().stream().filter(i->!before.contains(i)).toList();
            check(added.size()==1 && added.getFirst().getValue().endsWith("_internal_cutout"),"Internal generation produced incorrect objects");
            check(original.equals(board.toText()),"Internal cutout changed the source");
            check(((ScrollPane)((Tab)field("toolTab")).getContent()).getContent()==form,"Internal cutout draft closed after generation");
            return added.getFirst().getValue();
        });
    }
    private static void awaitCondition(Callable<Boolean> condition) throws Exception {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
        while(!fx(condition)){if(System.nanoTime()>deadline)throw new TimeoutException("Internal cutout preview timed out");Thread.sleep(15);}
    }
    private static void checkExcellonDiameter() throws Exception {
        var source=fx(()->item("excellonByItem","Drills"));
        var before=fx(()->(ExcellonImage)map("excellonByItem").get(source));
        var form=fx(()-> {
            call("selectProjectItem",new Class<?>[]{TreeItem.class},source);
            call("showProperties",new Class<?>[]{TreeItem.class},source);
            var tabs=(TabPane)field("leftTabs");tabs.getSelectionModel().select((Tab)field("propertiesTab"));
            var root=(javafx.scene.Parent)stage.getScene().getRoot();root.applyCss();root.layout();
            return ((ScrollPane)((javafx.scene.layout.StackPane)field("propertiesContainer")).getChildren().getFirst()).getContent();
        });
        fx(()-> {
            var table=(TableView<?>)form.lookup("#excellon-object-tools");check(table!=null,"Excellon properties table missing");
            table.getSelectionModel().clearAndSelect(0);
            table.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED,"","",KeyCode.F2,false,false,false,false));
            stage.getScene().getRoot().applyCss();stage.getScene().getRoot().layout();
            var editor=(TextField)table.lookup("#tool-diameter-editor");check(editor!=null,"Excellon diameter F2 failed");
            editor.setText("0,1981234567");editor.fireEvent(new javafx.event.ActionEvent());
            check(field("runningJob")!=null,"Excellon diameter did not use a worker");return null;
        });awaitJob();
        fx(()-> {
            var image=(ExcellonImage)map("excellonByItem").get(source);
            check(image.toolDiameters().get(1)==.1981234567,"Excellon diameter was rounded");
            check(image.drills().equals(before.drills()) && image.slots().equals(before.slots()),"Diameter moved/reassigned hits");
            var plot=(PlotAreaView)field("plotAreaView");
            check(plot.visibleSelectableLayers().stream().anyMatch(layer->layer.key()==source && layer.geometry()==image.solidGeometry()),"Diameter did not update Plot");
            form.lookup("#excellon-object-tools").fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED,"","",KeyCode.Z,false,true,false,false));return null;
        });awaitJob();
        fx(()-> {
            check(((ExcellonImage)map("excellonByItem").get(source)).toolDiameters().get(1)==.8,"Excellon diameter Undo failed");
            form.lookup("#excellon-object-tools").fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED,"","",KeyCode.Y,false,true,false,false));return null;
        });awaitJob();
        fx(()-> {check(((ExcellonImage)map("excellonByItem").get(source)).toolDiameters().get(1)==.1981234567,"Excellon diameter Redo failed");return null;});
    }
    private static void checkZPolicies() throws Exception {
        fx(()-> {
            var path=new org.locationtech.jts.geom.GeometryFactory().createLineString(new org.locationtech.jts.geom.Coordinate[]{
                    new org.locationtech.jts.geom.Coordinate(1,0),new org.locationtech.jts.geom.Coordinate(2,0)});
            try {
                for(String units:List.of("MM","IN")) for(boolean machinist:List.of(false,true)) for(double z:new double[]{-.1,0,.1}) {
                    ToolDefaults.set("cnc.machinist",true,Boolean.toString(machinist));
                    for(String tool:List.of("Geometry","Drilling","Cutout")) {
                        var result=new java.util.concurrent.atomic.AtomicReference<Object>();
                        javafx.scene.Node form;String cutId,generateId;
                        if(tool.equals("Geometry")) {
                            form=GeometryCncToolPanel.build(units,path,List.of(),result::set,()->{});
                            cutId="cnc-cut-depth";generateId="cnc-generate";
                        } else if(tool.equals("Drilling")) {
                            var image=new org.flatcam.cam.excellon.ExcellonParser().parse(List.of("M48",units.equals("MM") ? "METRIC" : "INCH","T1C1.0","%","T1","X1.0Y0.0","M30"));
                            var source=new DrillGCodeToolPanel.SourceCandidate(new TreeItem<>("Z-probe"),image,Map.of());
                            form=DrillGCodeToolPanel.build(List.of(source),source,List::of,result::set,()->{});
                            cutId="drill-cut-z";generateId="drill-generate";
                        } else {
                            form=CutoutToolPanel.build(units,(polygon,done,cancel)->false,()->{},result::set,()->{});
                            cutId="cutout-cut-z";generateId="cutout-generate-freeform";
                        }
                        ((TextField)form.lookup("#"+cutId)).setText(Double.toString(z));
                        ((Button)form.lookup("#"+generateId)).fire();
                        String context=tool+" "+units+" machinist="+machinist+" CutZ="+z;
                        if(!machinist && z==0) {check(result.get()==null,"Standard zero was accepted: "+context);continue;}
                        check(result.get()!=null,"Signed Z generation refused: "+context);
                        double target=machinist ? z : -Math.abs(z);
                        double actual=result.get() instanceof GeometryCncToolPanel.Result r ? r.parameters().cutZ()
                                : result.get() instanceof DrillGCodeToolPanel.Result r ? r.settingsByTool().get(1).cutZ()
                                : ((CutoutToolPanel.Result)result.get()).machining().cutZ();
                        check(actual==target,"Wrong Z target: "+context);
                        if(result.get() instanceof GeometryCncToolPanel.Result r) {
                            String code=org.flatcam.cam.gcode.GCodeGenerator.generateGeometryCncJob(units,path,r.parameters(),1,
                                    org.flatcam.cam.CancellationToken.none()).gcode();
                            check(code.contains("G1 Z"+String.format(Locale.ROOT,"%.4f",target==0 ? 0 : target)),"Wrong emitted Z: "+context);
                        }
                    }
                }
            } finally {ToolDefaults.set("cnc.machinist",true,"false");}
            return null;
        });
    }
    private static void checkEditorFileSaves(Path output) throws Exception {
        Path original=output.resolve("Editor-original.nc"),copy=output.resolve("Editor-copy.nc");
        String initial="G21\nG90\nM5\n",saved="G21\nG90\nG0 Z3\nM5\n",copied="G21\nG90\nG0 Z4\nM5\n";
        Files.writeString(original,initial);
        fx(()-> {
            var item=(TreeItem<String>)call("addCncJobToProject",new Class<?>[]{String.class,String.class,Path.class,String.class,
                    org.locationtech.jts.geom.Geometry.class,org.locationtech.jts.geom.Geometry.class},
                    "Editor-smoke.nc","Editor smoke",original,initial,null,null);
            call("selectProjectItem",new Class<?>[]{TreeItem.class},item);call("editSelectedGCode",new Class<?>[]{});
            var decision=MainWindow.class.getDeclaredField("cncExportDecision");decision.setAccessible(true);
            decision.set(window,(java.util.function.Predicate<CncPreflight.Report>)report->true);
            var chooser=MainWindow.class.getDeclaredField("cncExportDestination");chooser.setAccessible(true);
            chooser.set(window,(java.util.function.BiFunction<String,String,java.io.File>)(name,text)->copy.toFile());
            var tabs=(TabPane)field("centerTabs"); var editor=(CodeEditor)tabs.getSelectionModel().getSelectedItem().getContent();
            editor.area().replaceText(saved);
            var form=((ScrollPane)((Tab)field("toolTab")).getContent()).getContent();
            check(form.lookup("#gcode-cancel").getAccessibleHelp().contains("Não desfaz arquivos já gravados"),"Cancel tooltip must explain saved files");
            ((Button)form.lookup("#gcode-save")).fire();return null;
        });
        awaitJob();check(Files.readString(original).equals(saved),"Save did not update the original");
        check(!Files.exists(copy),"Save unexpectedly used Save as");
        fx(()-> {
            var tabs=(TabPane)field("centerTabs"); var editor=(CodeEditor)tabs.getSelectionModel().getSelectedItem().getContent();
            editor.area().replaceText(copied);
            var form=((ScrollPane)((Tab)field("toolTab")).getContent()).getContent();
            ((Button)form.lookup("#gcode-save-as")).fire();return null;
        });
        awaitJob();check(Files.readString(copy).equals(copied),"Save as did not write the copy");
        check(Files.readString(original).equals(saved),"Save as changed the original");
        fx(()-> {
            var form=((ScrollPane)((Tab)field("toolTab")).getContent()).getContent();
            ((Button)form.lookup("#gcode-cancel")).fire();
            check(!((GCodeEditorController)field("gcodeEditor")).isActive(),"Cancel did not close the editor");return null;
        });
        check(Files.readString(original).equals(saved) && Files.readString(copy).equals(copied),"Cancel undid saved files");
    }
    private static void checkCamDraftAndDiameter(String tool) throws Exception {
        double diameter=.1981234567;
        var source=fx(()->item("gerberByItem","Copper"));
        ScrollPane content=fx(()-> {
            call(tool.equals("NCC") ? "generateNcc" : "generateIsolation",new Class<?>[]{TreeItem.class,GerberImage.class},source,(GerberImage)map("gerberByItem").get(source));
            var scroll=(ScrollPane)((Tab)field("toolTab")).getContent();
            stage.getScene().getRoot().applyCss();stage.getScene().getRoot().layout();
            return scroll;
        });
        var form=content.getContent();
        fx(()-> {
            var inventory=Set.copyOf(window.objectNames());
            var table=(TableView<?>)form.lookup(tool.equals("NCC") ? "#ncc-tools" : "#isolation-tools");
            int count=table.getItems().size();
            var spinner=(Spinner<Double>)form.lookupAll(".spinner").stream().filter(node -> node instanceof Spinner<?> input
                    && input.getValueFactory() instanceof SpinnerValueFactory.DoubleSpinnerValueFactory values
                    && values.getMin()==.0001 && values.getMax()==10000).findFirst().orElseThrow();
            spinner.getEditor().setText("0,3171234567");spinner.commitValue();
            var add=(Button)form.lookupAll(".button").stream().filter(node -> node instanceof Button button
                    && button.getText().equals(I18n.text(tool.equals("NCC") ? "ui.967bcf34a913" : "ui.8f1fc115deb7"))).findFirst().orElseThrow();
            add.fire();check(table.getItems().size()==count+1,"Add did not append a cutter");
            check(((Number)table.getColumns().get(1).getCellData(count)).doubleValue()==.3171234567,"Spinner confirmation rounded the new cutter");
            table.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED,"","",KeyCode.DELETE,false,false,false,false));
            check(table.getItems().size()==count,"Delete did not remove exactly the selected cutter");
            table.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED,"","",KeyCode.Z,false,true,false,false));
            check(table.getItems().size()==count+1,"CAM table Undo did not restore the removed cutter");
            table.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED,"","",KeyCode.Y,false,true,false,false));
            check(table.getItems().size()==count,"CAM table Redo did not remove the cutter again");
            check(inventory.equals(Set.copyOf(window.objectNames())),"Table Delete removed a project object");
            return null;
        });
        for(int run=0;run<(tool.equals("NCC") ? 2 : 1);run++) {
            int current=run;
            var before=fx(()->Set.copyOf(map("geometryByItem").keySet()));
            fx(()-> {
                var table=(TableView<?>)form.lookup(tool.equals("NCC") ? "#ncc-tools" : "#isolation-tools");
                check(table!=null,"Missing editable tools table");table.getSelectionModel().clearAndSelect(0);
                if(current==0) {
                    table.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED,"","",KeyCode.F2,false,false,false,false));
                    stage.getScene().getRoot().applyCss();stage.getScene().getRoot().layout();
                    var editor=(TextField)table.lookup("#tool-diameter-editor");check(editor!=null,"F2 did not open the diameter editor");
                    int count=table.getItems().size();editor.setText("0.198");editor.positionCaret(2);
                    editor.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED,"","",KeyCode.DELETE,false,false,false,false));
                    check(editor.getText().equals("0.98") && table.getItems().size()==count,"Delete in the editor must only delete text");
                    editor.setText("0,1981234567");
                }
                if(tool.equals("NCC")) {
                    ((ComboBox<NccMethod>)form.lookup("#ncc-method")).setValue(current==0 ? NccMethod.SEED : NccMethod.STANDARD);
                    if(current==0) {
                        ((Spinner<?>)form.lookup("#ncc-margin")).getEditor().setText("1.7");
                        ((Spinner<?>)form.lookup("#ncc-overlap")).getEditor().setText("37.5");
                    }
                }
                ((Button)form.lookup(tool.equals("NCC") ? "#ncc-generate" : "#isolation-generate")).fire();
                check(field("runningJob")!=null,"Generate did not submit the edited CAM request");return null;
            });
            awaitJob();
            fx(()-> {
                var tab=(Tab)field("toolTab");check(tab.getContent()==content,"CAM completion discarded the draft");
                var tabs=(TabPane)field("leftTabs");tabs.getSelectionModel().select((Tab)field("propertiesTab"));tabs.getSelectionModel().select(tab);
                var created=map("geometryByItem").entrySet().stream().filter(e->!before.contains(e.getKey())).toList();
                check(created.size()==1,"CAM request did not publish exactly one geometry");
                var method=created.getFirst().getValue().getClass().getDeclaredMethod("tools");method.setAccessible(true);
                var tools=(List<org.flatcam.cam.geometry.ToolGeometry>)method.invoke(created.getFirst().getValue());
                check(tools.size()==1 && tools.getFirst().toolDiameter()==diameter,"Generated geometry lost the user diameter");
                if(tool.equals("NCC")) {
                    check(((ComboBox<?>)form.lookup("#ncc-method")).getValue()==(current==0 ? NccMethod.SEED : NccMethod.STANDARD),"NCC method reset");
                    check(((Spinner<?>)form.lookup("#ncc-margin")).getEditor().getText().equals("1.7"),"NCC margin reset");
                    check(((Spinner<?>)form.lookup("#ncc-overlap")).getEditor().getText().equals("37.5"),"NCC overlap reset");
                }
                return null;
            });
        }
        if(tool.equals("NCC"))fx(()-> {
            ((TabPane)field("leftTabs")).getSelectionModel().select((Tab)field("propertiesTab"));
            call("openSelectedNccTool",new Class<?>[]{});
            check(((Tab)field("toolTab")).getContent()==content,"Global NCC button did not resume the existing draft");
            return null;
        });
    }
    private static void checkSavedNccDraft() throws Exception {
        fx(()-> {
            var source=item("gerberByItem","Copper");
            call("generateNcc",new Class<?>[]{TreeItem.class,GerberImage.class},source,(GerberImage)map("gerberByItem").get(source));
            var form=((ScrollPane)((Tab)field("toolTab")).getContent()).getContent();
            check(((ComboBox<?>)form.lookup("#ncc-method")).getValue()==NccMethod.STANDARD,"Native project lost NCC method");
            check(((Spinner<?>)form.lookup("#ncc-margin")).getEditor().getText().equals("1.7"),"Native project lost NCC margin");
            var table=(TableView<?>)form.lookup("#ncc-tools");
            check(((Number)table.getColumns().get(1).getCellData(0)).doubleValue()==.1981234567,"Native project lost NCC tool precision");
            return null;
        });
    }
    private static void check(boolean value,String message) {if(!value)throw new IllegalStateException(message);}
    private static <T> T fx(Callable<T> action) throws Exception {var task=new FutureTask<T>(action);Platform.runLater(task);return task.get(20,TimeUnit.SECONDS);}
    private static Object field(String name) throws Exception {var f=MainWindow.class.getDeclaredField(name);f.setAccessible(true);return f.get(window);}
    @SuppressWarnings("unchecked") private static Map<TreeItem<String>,Object> map(String name) throws Exception {return (Map<TreeItem<String>,Object>)field(name);}
    private static TreeItem<String> item(String map,String name) throws Exception {return map(map).keySet().stream().filter(i->name.equals(i.getValue())).findFirst().orElseThrow();}
    private static Object call(String name,Class<?>[] types,Object... args) throws Exception {var method=MainWindow.class.getDeclaredMethod(name,types);method.setAccessible(true);return method.invoke(window,args);}
    private static void awaitJob() throws Exception {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(45);
        while(fx(()->field("runningJob")!=null)) {if(System.nanoTime()>deadline)throw new TimeoutException("CAM job timed out");Thread.sleep(15);}
    }
    public static final class MemoryFactory implements PreferencesFactory {
        private final Preferences user=new MemoryNode(null,""),system=new MemoryNode(null,"");
        public Preferences userRoot(){return user;}public Preferences systemRoot(){return system;}
    }
    private static final class MemoryNode extends AbstractPreferences {
        private final Map<String,String> values=new HashMap<>();private final Map<String,MemoryNode> children=new HashMap<>();
        MemoryNode(AbstractPreferences parent,String name){super(parent,name);}
        protected void putSpi(String k,String v){values.put(k,v);}protected String getSpi(String k){return values.get(k);}
        protected void removeSpi(String k){values.remove(k);}protected void removeNodeSpi(){values.clear();children.clear();}
        protected String[] keysSpi(){return values.keySet().toArray(String[]::new);}protected String[] childrenNamesSpi(){return children.keySet().toArray(String[]::new);}
        protected AbstractPreferences childSpi(String name){return children.computeIfAbsent(name,n->new MemoryNode(this,n));}
        protected void syncSpi(){}protected void flushSpi(){}
    }
}
