package org.flatcam.fx;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.prefs.*;
import javafx.application.Platform;
import javafx.scene.control.TreeItem;
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
            String iso=window.isolate("Copper_panelized","Isolation",.4,1,.1,IsolationType.BOTH);
            String ncc=window.nccClear("Copper_panelized","NCC",List.of(.8),.4,1,NccMethod.STANDARD,true,true,false,new NccBoundary.Itself());
            String cutout=window.cutoutRectangular("Edge_Cuts_area","Cutout",.8,.1,1,GapPattern.FOUR);
            var programs=new LinkedHashMap<String,String>();
            for(String geometry:List.of(iso,ncc,cutout)) {
                String cnc=window.cncjob(geometry,geometry+"_cnc",geometry.equals(iso) ? .4 : .8,-.1,2,100,60,500);
                String code=window.exportGcode(cnc,"","");check(code.contains("G1") || code.contains("G01"),"Empty cutting program");
                window.writeGcode(cnc,output.resolve(cnc+".nc"),"","");programs.put(cnc,code);
            }
            var request=new TclDrillRequest(List.of(),null,1.6,false,.5,2,50,500,10000,false,5,null,null,2,null,false,0,GCodePreprocessor.GRBL_11_NO_M6,false);
            String drillCnc=window.drillcncjob("Drills_panelized","Drilling_cnc",request);
            programs.put(drillCnc,window.exportGcode(drillCnc,"",""));window.writeGcode(drillCnc,output.resolve("Drilling.nc"),"","");
            var names=window.objectNames();
            Path nativeProject=output.resolve("Painel-testado.fcnproj"),pythonProject=output.resolve("Painel-testado.FlatPrj");
            window.saveProject(nativeProject);window.saveProject(pythonProject);
            for(Path saved:List.of(nativeProject,pythonProject)) {
                window.openProject(saved,true);check(new HashSet<>(names).equals(new HashSet<>(window.objectNames())),"Object inventory lost after reopening "+saved.getFileName());
                for(var code:programs.entrySet())check(code.getValue().equals(window.exportGcode(code.getKey(),"","")),"G-code lost after reopening");
            }
            check(Files.readString(copperFile).equals(copper) && Files.readString(edgeFile).equals(outline) && Files.readString(drillFile).equals(drills),"Original inputs were changed");
            Files.writeString(output.resolve("smoke.json"),new JSONObject().put("status","PASS").put("version",AboutInfo.version()).put("revision",args[2])
                    .put("appJar",location.toString()).put("objects",names.size()).put("panelDrills",8).put("panelSlots",4).put("programs",programs.size())
                    .put("checks",List.of("invisible main window","offline Outline guide","Gerber/Excellon import","Outline to Area","shared 2x2 panel",
                            "Isolation","NCC","Cutout","CNC and Drilling export","native and Python project reopen","source files unchanged")).toString(2));
            System.out.println("PASS beta smoke "+AboutInfo.version()+" "+args[2]);
        } catch(Throwable error) {error.printStackTrace();exit=1;}
        finally {
            try {if(window!=null)fx(()->{window.disposeViewport();stage.close();return null;});} catch(Exception error){error.printStackTrace();exit=1;}
            if(jobs!=null)jobs.shutdown();Platform.exit();
        }
        System.exit(exit);
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
