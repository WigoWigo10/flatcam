package org.flatcam.fx;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.VBox;
import org.flatcam.app.job.JobHandle;
import org.flatcam.cam.gerber.GerberImage;
import org.flatcam.cam.ncc.*;
import org.json.*;
import org.locationtech.jts.geom.*;

/** Audit only: isolated preferences, synthetic geometry; deliberately observes current behavior. */
public final class UxReportsAuditProbe {
    private static final List<JSONObject> findings=new ArrayList<>();
    private static Path output;
    private static <T>T fx(Callable<T> action)throws Exception{return TerminalPanelTest.fx(action);}
    private static Object field(Object owner,String name)throws Exception{var f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}
    private static Object call(Object owner,String name,Class<?>[] types,Object...args)throws Exception{var m=owner.getClass().getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(owner,args);}
    private static Object component(Object value,String name)throws Exception{var m=value.getClass().getDeclaredMethod(name);m.setAccessible(true);return m.invoke(value);}
    private static ScrollPane panel(MainWindow w)throws Exception{return(ScrollPane)((Tab)field(w,"toolTab")).getContent();}
    private static Node ncc(MainCamFlowTest.Session s)throws Exception{call(s.window,"generateNcc",new Class<?>[]{TreeItem.class,GerberImage.class},s.item,s.image);return panel(s.window).getContent();}
    private static List<Node> nodes(Node root){var result=new ArrayList<Node>();result.add(root);if(root instanceof TitledPane p&&p.getContent()!=null)result.addAll(nodes(p.getContent()));else if(root instanceof Parent p)for(var n:p.getChildrenUnmodifiable())result.addAll(nodes(n));return result;}
    private static Node form(String tool,String units,AtomicReference<Object> result){
        var geometry=new GeometryFactory().toGeometry(new Envelope(0,10,0,8));var item=new TreeItem<String>("audit-board");
        if(tool.equals("NCC")){var source=new NccToolPanel.SourceCandidate(item,item.getValue(),units,true,geometry);return NccToolPanel.build(List.of(source),source,List.of(),(s,a,d,c)->false,()->{},List::of,result::set,()->{});}
        var source=new IsolationToolPanel.SourceCandidate(item,GerberImage.of(units,Map.of(),geometry,geometry.getBoundary(),Map.of()));
        return IsolationToolPanel.build(List.of(source),source,List.of(),(s,a,d,c)->false,()->{},List::of,result::set,()->{});
    }
    private static TableView<?> layout(Node form){var root=new VBox(form);new Scene(root,540,1400);root.applyCss();root.layout();return(TableView<?>)form.lookup(".table-view");}
    private static void log(String id,JSONObject data)throws Exception{data.put("id",id);findings.add(data);Files.writeString(output.resolve("observations.json"),new JSONArray(findings).toString(2));System.out.println(data);}
    private static void numericAdd()throws Exception{
        for(var tool:List.of("NCC","Isolation"))for(var units:List.of("MM","IN"))for(var comma:List.of(false,true))fx(()->{
            var result=new AtomicReference<Object>();var form=form(tool,units,result);var table=layout(form);
            var spinner=(Spinner<Double>)nodes(form).stream().filter(n->n instanceof Spinner<?> s&&s.getValueFactory() instanceof SpinnerValueFactory.DoubleSpinnerValueFactory v&&v.getMin()==.0001&&v.getMax()==10000).findFirst().orElseThrow();
            String typedBase=units.equals("MM")?"0.1981234567":"0.00781234567";String typed=comma?typedBase.replace('.',','):typedBase;int before=table.getItems().size();double parsed=spinner.getValueFactory().getConverter().fromString(typed);spinner.getEditor().setText(typed);spinner.commitValue();String after=spinner.getEditor().getText();
            String key=tool.equals("NCC")?"ui.967bcf34a913":"ui.8f1fc115deb7";
            var add=(Button)nodes(form).stream().filter(n->n instanceof Button b&&b.getText().equals(I18n.text(key))).findFirst().orElseThrow();add.fire();
            log("ADD_PRECISION",new JSONObject().put("tool",tool).put("units",units).put("typed",typed).put("afterSpinnerCommit",after).put("factoryParsed",parsed).put("factoryValue",spinner.getValue()).put("locale",Locale.getDefault().toString()).put("rowsBefore",before).put("rowsAfter",table.getItems().size()).put("addedDiameter",table.getItems().size()>before?field(table.getItems().getLast(),"diameter"):JSONObject.NULL));
            return null;
        });
    }
    private static void reopening()throws Exception{
        try(var s=new MainCamFlowTest.Session("MM")){s.release.countDown();fx(()->{
            var first=ncc(s);((Spinner<?>)first.lookup("#ncc-margin")).getEditor().setText("3.14159");((ComboBox<NccMethod>)first.lookup("#ncc-method")).setValue(NccMethod.LINES);
            var previous=panel(s.window);ncc(s);var next=panel(s.window);
            log("REOPEN_RESETS_DRAFT",new JSONObject().put("samePanel",previous==next).put("beforeMargin","3.14159").put("afterMargin",((Spinner<?>)next.getContent().lookup("#ncc-margin")).getEditor().getText()).put("afterMethod",((ComboBox<?>)next.getContent().lookup("#ncc-method")).getValue()));
            return null;
        });}
    }
    private static void restDefaults()throws Exception{fx(()->{
        ToolDefaults.set("ncc.connect",true,"false");ToolDefaults.set("ncc.contour",true,"false");
        try{var form=form("NCC","MM",new AtomicReference<>());var connect=new JSONArray();var contour=new JSONArray();for(var n:nodes(form))if(n instanceof CheckBox c){if(c.getText().equals("Connect"))connect.put(c.isSelected());if(c.getText().equals("Contour"))contour.put(c.isSelected());}
            log("REST_DEFAULTS",new JSONObject().put("configuredConnect",false).put("configuredContour",false).put("mainThenRestConnect",connect).put("mainThenRestContour",contour));
        }finally{ToolDefaults.resetAll();}return null;
    });}
    private static void activeSnapshot()throws Exception{
        try(var s=new MainCamFlowTest.Session("MM")){
            var form=fx(()->ncc(s));
            JobHandle<?> handle=fx(()->{
                ((ComboBox<NccMethod>)form.lookup("#ncc-method")).setValue(NccMethod.SEED);
                ((Spinner<?>)form.lookup("#ncc-margin")).getEditor().setText("1.7");
                ((Button)form.lookup("#ncc-generate")).fire();var job=(JobHandle<?>)field(s.window,"runningJob");
                ((ComboBox<NccMethod>)form.lookup("#ncc-method")).setValue(NccMethod.STANDARD);return job;
            });
            s.release.countDown();handle.completion().get(15,TimeUnit.SECONDS);s.awaitUi();
            fx(()->{
                var generated=s.geometries().entrySet().stream().filter(e->e.getKey()!=s.reference).findFirst().orElseThrow();var geometry=(Geometry)component(generated.getValue(),"geometry");
                var seed=NccGenerator.generate("MM",s.image.solidGeometry(),new NccParameters(1,.4,1.7,NccMethod.SEED,true,true,0),org.flatcam.cam.CancellationToken.none(),org.flatcam.cam.ProgressCallback.none());
                var standard=NccGenerator.generate("MM",s.image.solidGeometry(),new NccParameters(1,.4,1.7,NccMethod.STANDARD,true,true,0),org.flatcam.cam.CancellationToken.none(),org.flatcam.cam.ProgressCallback.none());
                log("EDIT_DURING_GENERATION",new JSONObject().put("visibleMethod",((ComboBox<?>)form.lookup("#ncc-method")).getValue()).put("matchesSeed",geometry.equalsExact(seed.geometry(),1e-8)).put("matchesStandard",geometry.equalsExact(standard.geometry(),1e-8)).put("methodEditorDisabled",form.lookup("#ncc-method").isDisabled()).put("selectedObject",((TreeView<?>)field(s.window,"projectTree")).getSelectionModel().getSelectedItem().toString()));
                ((TabPane)field(s.window,"leftTabs")).getSelectionModel().select((Tab)field(s.window,"propertiesTab"));
                call(s.window,"openSelectedNccTool",new Class<?>[]{});
                log("TOOLBAR_AFTER_GENERATION",new JSONObject().put("activeSidebarTab",((TabPane)field(s.window,"leftTabs")).getSelectionModel().getSelectedItem().getText()).put("existingDraftStillPresent",panel(s.window).getContent()==form));
                return null;
            });
            fx(()->{((TabPane)field(s.window,"leftTabs")).getSelectionModel().select((Tab)field(s.window,"toolTab"));((Button)form.lookup("#ncc-generate")).fire();return null;});
            var second=fx(()->(JobHandle<?>)field(s.window,"runningJob"));second.completion().get(15,TimeUnit.SECONDS);s.awaitUi();
            fx(()->{
                var results=new JSONArray();for(var entry:s.geometries().entrySet())if(entry.getKey()!=s.reference)results.put(new JSONObject().put("name",entry.getKey().getValue()).put("visible",call(s.window,"isObjectVisible",new Class<?>[]{TreeItem.class},entry.getKey())).put("sourceName",component(entry.getValue(),"sourceName")));
                log("GENERATED_VARIANTS",new JSONObject().put("results",results));return null;
            });
        }
    }
    private static void projectDraft()throws Exception{
        try(var s=new MainCamFlowTest.Session("MM")){
            var handle=fx(()->{var form=ncc(s);((Spinner<Double>)form.lookup("#ncc-margin")).getValueFactory().setValue(3.14);((ComboBox<NccMethod>)form.lookup("#ncc-method")).setValue(NccMethod.LINES);((Button)form.lookup("#ncc-generate")).fire();return(JobHandle<?>)field(s.window,"runningJob");});
            s.release.countDown();handle.completion().get(15,TimeUnit.SECONDS);s.awaitUi();
            Path file=output.resolve("synthetic-draft.fcnproj");if(Files.exists(file))throw new FileAlreadyExistsException(file.toString());s.window.saveProject(file);s.window.openProject(file);
            fx(()->{
                var map=(Map<TreeItem<String>,GerberImage>)field(s.window,"gerberByItem");var source=map.entrySet().stream().filter(e->e.getKey().getValue().equals("board")).findFirst().orElseThrow();
                call(s.window,"generateNcc",new Class<?>[]{TreeItem.class,GerberImage.class},source.getKey(),source.getValue());var form=panel(s.window).getContent();
                log("PROJECT_DRAFT",new JSONObject().put("generatedBeforeSave",true).put("restoredGeometryCount",s.geometries().size()).put("beforeMargin","3.14").put("afterMargin",((Spinner<?>)form.lookup("#ncc-margin")).getEditor().getText()).put("afterMethod",((ComboBox<?>)form.lookup("#ncc-method")).getValue()));return null;
            });
        }
    }
    private static void unselectedInvalid()throws Exception{fx(()->{
        var generated=new AtomicReference<Object>();var form=form("NCC","MM",generated);var table=layout(form);table.getSelectionModel().clearAndSelect(0);
        table.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED,"","",KeyCode.F2,false,false,false,false));
        ((TextField)table.lookup("#tool-diameter-editor")).setText("bad");table.edit(-1,null);table.getSelectionModel().clearAndSelect(1);((Button)form.lookup("#ncc-generate")).fire();
        log("UNSELECTED_INVALID",new JSONObject().put("selectedRows",new JSONArray(table.getSelectionModel().getSelectedIndices())).put("requestEmitted",generated.get()!=null));return null;
    });}
    private static void otherNumbers()throws Exception{fx(()->{
        var form=form("NCC","MM",new AtomicReference<>());
        for(var entry:Map.of("#ncc-margin","1.23456789","#ncc-overlap","37.5123456").entrySet()) {
            var spinner=(Spinner<?>)form.lookup(entry.getKey());spinner.getEditor().setText(entry.getValue());spinner.commitValue();
            log("OTHER_NUMERIC_INPUT",new JSONObject().put("field",entry.getKey()).put("typed",entry.getValue()).put("afterCommit",spinner.getEditor().getText()));
        }
        for(var text:List.of("0,198","0.198;0.25")) {
            var row=new JSONObject().put("input",text);
            try{row.put("diameters",new JSONArray(PaintToolPanel.parseDiameters(text)));}catch(IllegalArgumentException invalid){row.put("error",invalid.getMessage());}
            log("PAINT_DECIMAL_LIST",row);
        }
        return null;
    });}
    public static void main(String[]args)throws Exception{
        if(!System.getProperty("java.util.prefs.PreferencesFactory","").equals("org.flatcam.fx.StartupSplashTest$MemoryFactory"))throw new IllegalStateException("This audit requires isolated in-memory preferences");
        output=Path.of(args[0]);Files.createDirectories(output);Platform.startup(()->Platform.setImplicitExit(false));
        int code=0;try{log("BUILD",new JSONObject().put("version",AboutInfo.version()).put("sourceRevision",AboutInfo.build().getProperty("sourceRevision")));numericAdd();otherNumbers();if(args.length<2 || !args[1].equals("numeric-only")){reopening();restDefaults();activeSnapshot();projectDraft();unselectedInvalid();}}
        catch(Throwable error){error.printStackTrace();code=1;}
        finally{Platform.exit();}System.exit(code);
    }
}
