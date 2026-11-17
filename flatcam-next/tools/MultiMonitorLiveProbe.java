package org.flatcam.fx;

import java.awt.GraphicsEnvironment;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.prefs.*;
import javafx.application.Platform;
import javafx.scene.control.SplitPane;
import javafx.stage.*;
import org.json.*;

/** Runs the real packaged app with file-backed test preferences and read-only display telemetry.
 * No application classes are overridden; the native fixture keeps the production DPI manifest. */
public final class MultiMonitorLiveProbe {
    private static final Path OUTPUT=outputDirectory();
    private static final String SESSION="session-"+System.currentTimeMillis();
    private static volatile boolean stopped;
    private static MainWindow applicationWindow;
    private static String last;
    private static Path outputDirectory(){
        try{return Path.of(MultiMonitorLiveProbe.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParent();}
        catch(Exception e){throw new IllegalStateException(e);}
    }
    public static void main(String[] args)throws Exception {
        System.setProperty("java.util.prefs.PreferencesFactory",FileFactory.class.getName());
        System.setProperty("flatcam.diagnostics.enabled","false");
        Files.createDirectories(OUTPUT);
        var watcher=new Thread(()->{
            while(!stopped){
                try{Platform.runLater(MultiMonitorLiveProbe::capture);Thread.sleep(350);}
                catch(IllegalStateException notStarted){try{Thread.sleep(100);}catch(InterruptedException e){return;}}
                catch(InterruptedException e){return;}
            }
        },"multi-monitor-read-only-observer");
        watcher.setDaemon(true);watcher.start();
        try{javafx.application.Application.launch(LiveApp.class,args);}
        finally{stopped=true;watcher.interrupt();Preferences.userRoot().flush();}
    }
    /** The inherited start/stop and DPI workaround are production code. Only test-data setup is added. */
    public static final class LiveApp extends MainApp {
        @Override public void start(Stage stage){
            super.start(stage);
            try{
                var field=MainApp.class.getDeclaredField("mainWindow");field.setAccessible(true);
                applicationWindow=(MainWindow)field.get(this);
            }catch(Exception e){throw new IllegalStateException(e);}
            var prepare=new Thread(()->{
                try{
                    Path fixture=OUTPUT.resolve("Placa-DPI.fcnproj");
                    if(Files.exists(fixture))applicationWindow.openProject(fixture);
                    else{
                        Path repository=OUTPUT.getParent().getParent();
                        applicationWindow.openGerber(repository.resolve("samples/community/boards/audio-watermark/fabrication/Audio_watermark_generator-F_Cu.gbr"),"Placa DPI");
                        applicationWindow.saveProject(fixture);
                    }
                }catch(Exception e){try{Files.writeString(OUTPUT.resolve("data-setup-failure.txt"),e.toString());}catch(Exception ignored){} }
            },"multi-monitor-public-fixture");prepare.setDaemon(true);prepare.start();
        }
    }
    private static void capture(){
        try{
            var fxScreens=new JSONArray();
            for(var screen:Screen.getScreens()){
                var b=screen.getBounds();var v=screen.getVisualBounds();
                fxScreens.put(new JSONObject().put("primary",screen.equals(Screen.getPrimary()))
                    .put("bounds",List.of(b.getMinX(),b.getMinY(),b.getWidth(),b.getHeight()))
                    .put("visualBounds",List.of(v.getMinX(),v.getMinY(),v.getWidth(),v.getHeight()))
                    .put("scaleX",screen.getOutputScaleX()).put("scaleY",screen.getOutputScaleY()).put("dpi",screen.getDpi()));
            }
            var awtScreens=new JSONArray();
            for(var screen:GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()){
                var config=screen.getDefaultConfiguration();var b=config.getBounds();var t=config.getDefaultTransform();var mode=screen.getDisplayMode();
                awtScreens.put(new JSONObject().put("id",screen.getIDstring()).put("bounds",List.of(b.x,b.y,b.width,b.height))
                    .put("scaleX",t.getScaleX()).put("scaleY",t.getScaleY()).put("physicalMode",List.of(mode.getWidth(),mode.getHeight())));
            }
            var windows=new JSONArray();
            for(var window:List.copyOf(Window.getWindows()))if(window.isShowing()){
                var row=new JSONObject().put("identity",System.identityHashCode(window)).put("kind",window.getClass().getSimpleName())
                    .put("x",window.getX()).put("y",window.getY()).put("width",window.getWidth()).put("height",window.getHeight())
                    .put("outputScaleX",window.getOutputScaleX()).put("outputScaleY",window.getOutputScaleY())
                    .put("renderScaleX",window.getRenderScaleX()).put("renderScaleY",window.getRenderScaleY()).put("focused",window.isFocused());
                if(window instanceof Stage stage)row.put("title",stage.getTitle()).put("maximized",stage.isMaximized()).put("iconified",stage.isIconified());
                if(window.getScene()!=null){
                    var scene=window.getScene();var splitPanes=new JSONArray();
                    row.put("sceneWidth",scene.getWidth()).put("sceneHeight",scene.getHeight()).put("sceneIdentity",System.identityHashCode(scene));
                    for(var node:scene.getRoot().lookupAll(".split-pane"))if(node instanceof SplitPane split){
                        var positions=new JSONArray();for(double value:split.getDividerPositions())positions.put(value);
                        splitPanes.put(new JSONObject().put("orientation",split.getOrientation().name()).put("width",split.getWidth()).put("height",split.getHeight()).put("dividers",positions));
                    }
                    row.put("splits",splitPanes);
                }
                windows.put(row);
            }
            var report=new JSONObject().put("version",AboutInfo.version()).put("source",AboutInfo.build().getProperty("sourceRevision"))
                .put("applicationJar",MainApp.class.getProtectionDomain().getCodeSource().getLocation().toString())
                .put("preferencesFactory",System.getProperty("java.util.prefs.PreferencesFactory"))
                .put("fxScreens",fxScreens).put("awtScreens",awtScreens).put("windows",windows);
            if(applicationWindow!=null)report.put("objects",applicationWindow.objectNames()).put("projectDirty",applicationWindow.hasUnsavedProjectChanges()).put("sidebarScreenId",applicationWindow.currentScreenId());
            String content=report.toString();if(content.equals(last))return;last=content;
            report.put("utc",Instant.now().toString()).put("pid",ProcessHandle.current().pid()).put("session",SESSION);
            Files.writeString(OUTPUT.resolve(SESSION+".ndjson"),report+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);
            Path temporary=OUTPUT.resolve("latest.tmp.json");Files.writeString(temporary,report.toString(2));
            Files.move(temporary,OUTPUT.resolve("latest.json"),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);
        }catch(Exception failure){System.err.println("MULTI-DPI observer: "+failure);}
    }
    public static final class FileFactory implements PreferencesFactory {
        private final Preferences user=new Node(null,"");
        private final Preferences system=new Node(null,"");
        public Preferences userRoot(){return user;}
        public Preferences systemRoot(){return system;}
    }
    private static final class Node extends AbstractPreferences {
        private static final Properties DATA=load();
        private static Properties load(){
            var values=new Properties();Path path=OUTPUT.resolve("test-preferences.properties");
            if(Files.exists(path))try(var reader=Files.newBufferedReader(path)){values.load(reader);}catch(Exception e){throw new IllegalStateException(e);}
            return values;
        }
        Node(AbstractPreferences parent,String name){super(parent,name);}
        private String prefix(){return absolutePath()+"|";}
        protected void putSpi(String key,String value){synchronized(DATA){DATA.setProperty(prefix()+key,value);}}
        protected String getSpi(String key){synchronized(DATA){return DATA.getProperty(prefix()+key);}}
        protected void removeSpi(String key){synchronized(DATA){DATA.remove(prefix()+key);}}
        protected void removeNodeSpi(){synchronized(DATA){DATA.stringPropertyNames().stream().filter(k->k.startsWith(prefix())).toList().forEach(DATA::remove);}}
        protected String[] keysSpi(){synchronized(DATA){return DATA.stringPropertyNames().stream().filter(k->k.startsWith(prefix())).map(k->k.substring(prefix().length())).toArray(String[]::new);}}
        protected String[] childrenNamesSpi(){synchronized(DATA){
            String base=absolutePath().equals("/")?"/":absolutePath()+"/";
            return DATA.stringPropertyNames().stream().filter(k->k.startsWith(base)).map(k->k.substring(base.length()).split("[|/]",2)[0]).filter(k->!k.isBlank()).distinct().toArray(String[]::new);
        }}
        protected AbstractPreferences childSpi(String name){return new Node(this,name);}
        protected void syncSpi(){}
        protected void flushSpi()throws BackingStoreException {synchronized(DATA){
            try{Files.createDirectories(OUTPUT);try(var writer=Files.newBufferedWriter(OUTPUT.resolve("test-preferences.properties"))){DATA.store(writer,"Isolated multi-monitor test preferences; not Windows user preferences");}}
            catch(Exception e){throw new BackingStoreException(e);}
        }}
    }
}
