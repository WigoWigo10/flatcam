package org.flatcam.fx;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.*;
import java.util.prefs.*;
import javafx.application.Platform;
import javafx.scene.*;
import javafx.scene.control.*;
import org.flatcam.app.job.*;
import org.flatcam.cam.*;
import org.flatcam.cam.excellon.ExcellonParser;
import org.flatcam.cam.gerber.GerberImage;
import org.flatcam.cam.ncc.*;
import org.json.*;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.geom.util.AffineTransformation;

/** Read-only audit of packaged CAM UI routes; synthetic data and memory preferences only.
 * FAIL means the asserted user-visible contract was violated, not an oracle exception.
 * Does not patch production classes or write any user project. */
public final class CamRelatedAuditProbe {
    private static final GeometryFactory F=new GeometryFactory();
    private static final JSONArray RESULTS=new JSONArray();
    private static final List<String> SKIPPED=new ArrayList<>();
    private static Path output;
    private static int repeats;
    interface Checked { JSONObject run() throws Exception; }
    public static void main(String[] args) throws Exception {
        output=Path.of(args[0]);Files.createDirectories(output);repeats=Integer.parseInt(args[1]);
        if(!System.getProperty("java.util.prefs.PreferencesFactory","").contains("CamRelatedAuditProbe"))throw new IllegalStateException("Preferences must be isolated");
        Platform.startup(()->Platform.setImplicitExit(false));
        fx(()->{ToolDefaults.useStore(ToolDefaults.memoryStore());UiFonts.initialize(AppPreferences.loadFontSettings());UiNumbers.initialize(AppPreferences.loadNumericSettings());return null;});
        try {
            for(String kind:List.of("Geometry","Excellon","GCode"))check("editor-context-to-Gerber-"+kind,()->editorContext(kind));
            check("paint-removed-picked-source",CamRelatedAuditProbe::removedPickedSource);
            for(String tool:List.of("Paint","NCC"))check("late-cancel-"+tool,()->lateCancel(tool));
            for(String tool:List.of("Paint","NCC"))check("source-removed-during-"+tool,()->removedDuringJob(tool));
            check("paint-inches-without-initial-selection",CamRelatedAuditProbe::inchDefaults);
            check("paint-source-unit-switch",CamRelatedAuditProbe::unitSwitch);
            check("paint-single-member-collection-selection",CamRelatedAuditProbe::singleCollection);
            check("paint-settings-survive-switch-and-apply-all",CamRelatedAuditProbe::paintSettings);
            var report=new JSONObject().put("schema",1).put("version",AboutInfo.version())
                    .put("binarySource",AboutInfo.build().getProperty("sourceRevision"))
                    .put("appJar",MainWindow.class.getProtectionDomain().getCodeSource().getLocation().toString())
                    .put("classOverrides",System.getProperty("flatcam.audit.overrides",""))
                    .put("repeatCount",repeats).put("cases",RESULTS).put("excludedCases",SKIPPED)
                    .put("scope","Synthetic packaged JavaFX controls; no visible Stage, registry preferences, private project or physical CNC");
            long failures=RESULTS.toList().stream().filter(x->((Map<?,?>)x).get("status").equals("FAIL")).count();
            long errors=RESULTS.toList().stream().filter(x->((Map<?,?>)x).get("status").equals("ORACLE_ERROR")).count();
            report.put("failedCases",failures).put("oracleErrors",errors);
            Files.writeString(output.resolve("ui-audit.json"),report.toString(2));
            System.out.println("UI audit: "+RESULTS.length()+" cases; "+failures+" failed contracts; "+errors+" oracle errors");
            System.exit(failures>0 || errors>0 ? 1 : 0);
        } finally {Platform.exit();}
    }
    private static void check(String id,Checked action)throws Exception {
        if(!id.matches(System.getProperty("flatcam.audit.cases",".*"))){SKIPPED.add(id);return;}
        var runs=new JSONArray();int fail=0,error=0;
        for(int i=0;i<repeats;i++) {
            try {var value=action.run();boolean ok=value.getBoolean("ok");if(!ok)fail++;runs.put(value.put("run",i));}
            catch(Throwable problem){error++;problem.printStackTrace();runs.put(new JSONObject().put("run",i).put("oracleError",problem.toString()));}
        }
        var result=new JSONObject().put("id",id).put("status",error>0 ? "ORACLE_ERROR" : fail>0 ? "FAIL" : "PASS")
                .put("failedRuns",fail).put("oracleErrors",error).put("runs",runs);
        RESULTS.put(result);Files.writeString(output.resolve(id+".json"),result.toString(2));
        System.out.println(id+": "+result.getString("status")+" ("+fail+"/"+repeats+" failed; "+error+" errors)");
    }
    private static final class Session implements AutoCloseable {
        final JobExecutor jobs=new JobExecutor(1);final MainWindow window;
        final TreeItem<String> gerber,area;final GerberImage image;
        Session(String units)throws Exception {
            double scale=units.equals("IN") ? 1/25.4 : 1;
            Geometry box=F.toGeometry(new Envelope(0,10*scale,0,8*scale));image=GerberImage.of(units,Map.of(),box,box.getBoundary(),Map.of());
            window=fx(()->{var w=new MainWindow(jobs);w.createScene();return w;});
            gerber=fx(()->(TreeItem<String>)call(window,"addGerberToProject",new Class<?>[]{String.class,Path.class,GerberImage.class},"copper",null,image));
            area=fx(()->(TreeItem<String>)call(window,"addGeometryToProject",new Class<?>[]{String.class,String.class,String.class,Geometry.class,boolean.class},"area","",units,box,false));
        }
        public void close()throws Exception {
            fx(()->{for(String name:List.of("gerberEditor","geometryEditor","excellonEditor","gcodeEditor")){
                var controller=field(window,name);var cancel=controller.getClass().getDeclaredMethod("cancel");cancel.setAccessible(true);cancel.invoke(controller);
            }window.disposeViewport();return null;});jobs.shutdown();
        }
    }
    private static JSONObject editorContext(String kind)throws Exception {
        try(var s=new Session("MM")) {return fx(()-> {
            TreeItem<String> target=s.area;
            if(kind.equals("Excellon")) {
                var image=new ExcellonParser().parse(List.of("M48","METRIC","T1C0.8","%","T1","X1.0Y1.0","M30"));
                target=(TreeItem<String>)call(s.window,"addExcellonToProject",new Class<?>[]{String.class,Path.class,image.getClass()},"holes",null,image);
            } else if(kind.equals("GCode"))target=(TreeItem<String>)call(s.window,"addCncJobToProject",new Class<?>[]{String.class,String.class,Path.class,String.class,Geometry.class,Geometry.class},"code","",null,"G21\nG90\nG0 X1 Y1\nM5\n",null,null);
            call(s.window,"selectProjectItem",new Class<?>[]{TreeItem.class},target);call(s.window,"editSelected"+kind,new Class<?>[]{});
            var tab=(Tab)field(s.window,"toolTab");var panel=tab.getContent();
            call(s.window,"selectProjectItem",new Class<?>[]{TreeItem.class},s.gerber);
            var menu=(List<MenuItem>)call(s.window,"gerberContextMenuItems",new Class<?>[]{TreeItem.class,GerberImage.class},s.gerber,s.image);
            var edit=menu.stream().filter(item->I18n.text("ui.2eba946b2e1e").equals(item.getText())).findFirst().orElseThrow();
            if(edit.isDisable())throw new IllegalStateException("Context edit unavailable");edit.fire();
            int active=0;for(String name:List.of("gerberEditor","geometryEditor","excellonEditor","gcodeEditor"))if(active(field(s.window,name)))active++;
            boolean preserved=tab.getContent()==panel;
            ((GerberEditorController)field(s.window,"gerberEditor")).cancel();
            boolean reachable=tab.getContent()==panel;
            return new JSONObject().put("ok",active==1 && preserved).put("activeEditors",active).put("originalPanelPreserved",preserved)
                    .put("originalPanelReachableAfterGerberCancel",reachable);
        });}
    }
    private static JSONObject removedPickedSource()throws Exception {
        try(var s=new Session("MM")) {
                var handle=fx(()-> {
                    call(s.window,"selectProjectItem",new Class<?>[]{TreeItem.class},s.gerber);call(s.window,"openPaintTool",new Class<?>[]{});
                    var form=form(s.window);var radio=nodes(form).stream().filter(n->n instanceof RadioButton r && r.getText().equals(I18n.text("ui.ba89e6f82dfb"))).map(RadioButton.class::cast).findFirst().orElseThrow();radio.setSelected(true);
                    button(form,I18n.text("ui.6a57618c2593")).fire();
                    var callback=(Consumer<Coordinate>)field(s.window,"pendingPointPick");
                    if(callback==null)throw new IllegalStateException("Picker not armed");callback.accept(new Coordinate(3,3));
                    removeGerber(s);
                    ((Button)form.lookup("#paint-generate")).fire();return job(s.window);
                });
                if(handle!=null)finish(s.window,handle);
                int published=fx(()->map(s.window,"geometryByItem").size()-1);
                return new JSONObject().put("ok",handle==null && published==0).put("sourceStillPresent",fx(()->map(s.window,"gerberByItem").containsKey(s.gerber)))
                        .put("jobSubmittedAfterSourceRemoved",handle!=null).put("published",published);
        }
    }
    private static JSONObject removedDuringJob(String tool)throws Exception {
        try(var s=new Session("MM")) {
            var gate=new CountDownLatch(1);var ready=new CountDownLatch(1);
            s.jobs.submit(context->{ready.countDown();if(!gate.await(20,TimeUnit.SECONDS))throw new IllegalStateException("Gate timed out");return null;},null);
            if(!ready.await(5,TimeUnit.SECONDS))throw new IllegalStateException("Worker gate not ready");
            try {
                var handle=fx(()->{
                    if(tool.equals("Paint"))call(s.window,"runPaintGeneration",new Class<?>[]{TreeItem.class,String.class,Geometry.class,PaintParameters.class},s.gerber,"MM",s.image.solidGeometry(),new PaintParameters(List.of(.8),.4,0,NccMethod.LINES,false,true,NccOrder.NONE,false));
                    else {
                        var source=new NccToolPanel.SourceCandidate(s.gerber,"copper","MM",true,s.image.solidGeometry());
                        var p=new NccParameters(List.of(.8),.4,1,NccMethod.LINES,false,true,0,false,NccOrder.NONE,new NccBoundary.Itself());
                        call(s.window,"runNccGeneration",new Class<?>[]{TreeItem.class,String.class,Geometry.class,boolean.class,NccToolPanel.Result.class},s.gerber,"MM",s.image.solidGeometry(),true,new NccToolPanel.Result(source,p,false,Map.of()));
                    }
                    var job=job(s.window);if(job==null)throw new IllegalStateException("No computation submitted");
                    removeGerber(s);return job;
                });
                gate.countDown();finish(s.window,handle);int published=fx(()->map(s.window,"geometryByItem").size()-1);
                return new JSONObject().put("ok",published==0).put("sourceStillPresent",fx(()->map(s.window,"gerberByItem").containsKey(s.gerber))).put("published",published);
            }finally{gate.countDown();}
        }
    }
    private static JSONObject lateCancel(String tool)throws Exception {
        try(var s=new Session("MM")) {
            var handle=fx(()-> {
                if(tool.equals("Paint"))call(s.window,"runPaintGeneration",new Class<?>[]{TreeItem.class,String.class,Geometry.class,PaintParameters.class},s.gerber,"MM",s.image.solidGeometry(),new PaintParameters(List.of(.8),.4,0,NccMethod.LINES,false,true,NccOrder.NONE,false));
                else {
                    var source=new NccToolPanel.SourceCandidate(s.gerber,"copper","MM",true,s.image.solidGeometry());
                    var p=new NccParameters(List.of(.8),.4,1,NccMethod.LINES,false,true,0,false,NccOrder.NONE,new NccBoundary.Itself());
                    var result=new NccToolPanel.Result(source,p,false,Map.of());
                    call(s.window,"runNccGeneration",new Class<?>[]{TreeItem.class,String.class,Geometry.class,boolean.class,NccToolPanel.Result.class},s.gerber,"MM",s.image.solidGeometry(),true,result);
                }
                var running=job(s.window);if(running==null)throw new IllegalStateException("No running computation");
                running.completion().get(10,TimeUnit.SECONDS);call(s.window,"cancelDemoJob",new Class<?>[]{});return running;
            });
            awaitIdle(s.window);int published=fx(()->map(s.window,"geometryByItem").size()-1);
            return new JSONObject().put("ok",published==0).put("cancelledBeforeUiPublication",handle.isCancelled()).put("published",published);
        }
    }
    private static JSONObject inchDefaults()throws Exception {
        try(var s=new Session("IN")) {return fx(()->{
            ((TreeView<?>)field(s.window,"projectTree")).getSelectionModel().clearSelection();call(s.window,"openPaintTool",new Class<?>[]{});
            var form=form(s.window);double actual=PaintToolPanel.parseDiameters(((TextField)form.lookup("#paint-diameters")).getText()).getFirst();
            double expected=PaintToolPanel.parseDiameters(ToolDefaults.text("paint.tooldia",false)).getFirst();
            return new JSONObject().put("ok",actual==expected).put("sourceUnits","IN").put("expectedInches",expected).put("actualInches",actual).put("actualMillimeters",actual*25.4);
        });}
    }
    private static JSONObject unitSwitch()throws Exception {
        try(var s=new Session("MM")) {
            var evidence=new JSONObject();var handle=fx(()->{
            var image=GerberImage.of("IN",Map.of(),AffineTransformation.scaleInstance(1/25.4,1/25.4).transform(s.image.solidGeometry()),F.createGeometryCollection(),Map.of());
            var inch=(TreeItem<String>)call(s.window,"addGerberToProject",new Class<?>[]{String.class,Path.class,GerberImage.class},"inch-copper",null,image);
            call(s.window,"selectProjectItem",new Class<?>[]{TreeItem.class},s.gerber);call(s.window,"openPaintTool",new Class<?>[]{});
            var form=form(s.window);var picker=(ComboBox<TreeItem<String>>)nodes(form).stream().filter(n->n instanceof ComboBox<?> c && c.getItems().contains(s.gerber) && !c.visibleProperty().isBound()).findFirst().orElseThrow();
            double before=PaintToolPanel.parseDiameters(((TextField)form.lookup("#paint-diameters")).getText()).getFirst();
            if(!picker.getItems().contains(inch)) {
                // Both conversion and an explicit fixed-unit picker satisfy the original contract.
                // Also test that a foreign value cannot bypass the policy programmatically.
                picker.setValue(inch);((Button)form.lookup("#paint-generate")).fire();
                var running=job(s.window);
                evidence.put("ok",running==null).put("policy","fixed-unit-picker").put("beforeMillimeters",before)
                        .put("crossUnitSourceAllowed",false).put("foreignValueRejected",running==null);
                return running;
            }
            picker.setValue(inch);double after=PaintToolPanel.parseDiameters(((TextField)form.lookup("#paint-diameters")).getText()).getFirst();
            evidence.put("ok",Math.abs(after*25.4-before)<1e-9).put("beforeMillimeters",before).put("afterInches",after).put("afterMillimeters",after*25.4).put("crossUnitSourceAllowed",picker.getItems().contains(inch));
            ((Button)form.lookup("#paint-generate")).fire();return job(s.window);
            });
            if(handle!=null)finish(s.window,handle);
            return fx(()-> {
                var entry=map(s.window,"geometryByItem").entrySet().stream().filter(e->e.getKey()!=s.area).map(Map.Entry::getValue).findFirst().orElse(null);
                if(entry!=null){
                    var units=entry.getClass().getDeclaredMethod("units");units.setAccessible(true);
                    var tools=entry.getClass().getDeclaredMethod("tools");tools.setAccessible(true);
                    var generated=(List<org.flatcam.cam.geometry.ToolGeometry>)tools.invoke(entry);
                    evidence.put("generatedUnits",units.invoke(entry)).put("generatedToolDiameter",generated.getFirst().toolDiameter());
                }
                if(!evidence.optBoolean("crossUnitSourceAllowed",true) && entry!=null)evidence.put("ok",false);
                return evidence.put("geometryPublished",entry!=null);
            });
        }
    }
    private static JSONObject singleCollection()throws Exception {return fx(()->{
        var polygon=F.toGeometry(new Envelope(0,10,0,8));var collection=F.createGeometryCollection(new Geometry[]{F.createGeometryCollection(new Geometry[]{polygon})});
        int counted=PaintToolPanel.polygonsOf(collection).size();var generated=NccGenerator.paint("MM",collection,new PaintParameters(List.of(.8),.4,0,NccMethod.LINES,false,true,NccOrder.NONE,false),CancellationToken.NONE,ProgressCallback.none());
        int direct=PaintToolPanel.polygonsOf(polygon).size(),oneWrapper=PaintToolPanel.polygonsOf(F.createGeometryCollection(new Geometry[]{polygon})).size();
        return new JSONObject().put("ok",counted==1 && direct==1 && oneWrapper==1).put("uiSelectablePolygons",counted)
                .put("directPolygonCount",direct).put("singleWrapperCount",oneWrapper).put("kernelAcceptedSameGeometry",!generated.isEmpty());
    });}
    private static JSONObject paintSettings()throws Exception {return fx(()->{
        var output=new AtomicReference<PaintParameters>();var shape=F.toGeometry(new Envelope(0,10,0,8));var source=new TreeItem<String>("fixture");
        var form=PaintToolPanel.build(new PaintToolPanel.Host(){
            public List<TreeItem<String>> sources(){return List.of(source);}public TreeItem<String> initialSource(){return source;}
            public String units(TreeItem<String> item){return "MM";}public Geometry polygons(TreeItem<String> item){return shape;}
            public void pickPoint(Consumer<Coordinate> callback){}public void cancelPick(){}public void selectArea(Geometry g,boolean polygon,Consumer<Geometry> done,Runnable cancel){}
            public void cancelAreaSelection(){}public void preview(Geometry g){}public void paint(TreeItem<String> item,String units,Geometry g,PaintParameters p){output.set(p);}
        },()->{});
        var root=new javafx.scene.layout.VBox(form);new Scene(root,540,1300);root.applyCss();root.layout();
        ((TextField)form.lookup("#paint-diameters")).setText("0.3;0.5");((Button)form.lookup("#paint-update-tools")).fire();
        var table=(TableView<?>)form.lookup("#paint-tools");table.getSelectionModel().clearAndSelect(0);
        ((ComboBox<NccMethod>)form.lookup("#paint-method")).setValue(NccMethod.LINES);((CheckBox)form.lookup("#paint-angle-auto")).setSelected(false);((TextField)form.lookup("#paint-angle-degrees")).setText("37,5123456");
        table.getSelectionModel().clearAndSelect(1);table.getSelectionModel().clearAndSelect(0);((Button)form.lookup("#paint-apply-all")).fire();((Button)form.lookup("#paint-generate")).fire();
        var p=output.get();if(p==null)throw new IllegalStateException("Paint settings rejected");
        var angles=new ArrayList<Double>();boolean ok=true;
        for(double diameter:p.toolDiameters()) {
            var settings=p.settingsFor(diameter);
            // Optional Beta 6 API: permits compiling older-build controls while explicitly excluding this case.
            Double value=(Double)settings.getClass().getMethod("lineAngleDegrees").invoke(settings);angles.add(value);
            ok &= Double.valueOf(37.5123456).equals(value) && settings.method()==NccMethod.LINES;
        }
        return new JSONObject().put("ok",ok).put("toolAngles",angles);
    });}
    private static Node form(MainWindow window)throws Exception {var root=((Scene)field(window,"scene")).getRoot();root.applyCss();root.layout();return ((ScrollPane)((Tab)field(window,"toolTab")).getContent()).getContent();}
    private static void removeGerber(Session s)throws Exception {
        var menu=(List<MenuItem>)call(s.window,"gerberContextMenuItems",new Class<?>[]{TreeItem.class,GerberImage.class},s.gerber,s.image);
        var remove=menu.stream().filter(item->I18n.text("ui.a3037dc71a43").equals(item.getText())).findFirst().orElseThrow();
        if(remove.isDisable())throw new IllegalStateException("Context removal unavailable");remove.fire();
    }
    private static Button button(Node root,String text){return nodes(root).stream().filter(n->n instanceof Button b && b.getText().equals(text)).map(Button.class::cast).findFirst().orElseThrow();}
    private static List<Node> nodes(Node root){var result=new ArrayList<Node>();result.add(root);if(root instanceof TitledPane p && p.getContent()!=null)result.addAll(nodes(p.getContent()));if(root instanceof Parent p)for(var child:p.getChildrenUnmodifiable())result.addAll(nodes(child));return result;}
    private static boolean active(Object controller)throws Exception{var m=controller.getClass().getDeclaredMethod("isActive");m.setAccessible(true);return (boolean)m.invoke(controller);}
    private static Object field(Object target,String name)throws Exception{var f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}
    private static Object call(MainWindow window,String name,Class<?>[] types,Object... args)throws Exception{var m=MainWindow.class.getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(window,args);}
    private static Map<?,?> map(MainWindow window,String name)throws Exception{return (Map<?,?>)field(window,name);}
    private static JobHandle<?> job(MainWindow window)throws Exception{return (JobHandle<?>)field(window,"runningJob");}
    private static void finish(MainWindow window,JobHandle<?> job)throws Exception{job.completion().get(10,TimeUnit.SECONDS);awaitIdle(window);}
    private static void awaitIdle(MainWindow window)throws Exception{long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);while(fx(()->job(window)!=null)){if(System.nanoTime()>end)throw new TimeoutException("UI completion did not settle");Thread.sleep(5);}}
    private static <T>T fx(Callable<T> action)throws Exception{var task=new FutureTask<T>(action);Platform.runLater(task);return task.get(25,TimeUnit.SECONDS);}
    public static final class MemoryFactory implements PreferencesFactory {
        private final Preferences user=new MemoryNode(null,""),system=new MemoryNode(null,"");public Preferences userRoot(){return user;}public Preferences systemRoot(){return system;}
    }
    private static final class MemoryNode extends AbstractPreferences {
        private final Map<String,String> values=new HashMap<>();private final Map<String,MemoryNode> children=new HashMap<>();
        MemoryNode(AbstractPreferences parent,String name){super(parent,name);}protected void putSpi(String k,String v){values.put(k,v);}protected String getSpi(String k){return values.get(k);}
        protected void removeSpi(String k){values.remove(k);}protected void removeNodeSpi(){values.clear();children.clear();}protected String[] keysSpi(){return values.keySet().toArray(String[]::new);}
        protected String[] childrenNamesSpi(){return children.keySet().toArray(String[]::new);}protected AbstractPreferences childSpi(String name){return children.computeIfAbsent(name,n->new MemoryNode(this,n));}
        protected void syncSpi(){}protected void flushSpi(){}
    }
}
