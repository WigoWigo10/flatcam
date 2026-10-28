package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Control;
import javafx.scene.control.Labeled;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.Pane;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

/** Inventory of real panel controls (never skin nodes), reproducible without opening a user window. */
class TooltipAuditTest {
    private record Panel(Class<?> type, String context) { }
    private static final List<Panel> PANELS = List.of(
        new Panel(AlignObjectsToolPanel.class,"Align Objects Tool"), new Panel(CalibrationToolPanel.class,"Calibration Tool"),
        new Panel(CopperThievingToolPanel.class,"Copper Thieving Tool"), new Panel(CornerMarkersToolPanel.class,"Corner Markers Tool"),
        new Panel(DoubleSidedToolPanel.class,"2-Sided Tool"), new Panel(EtchCompensationToolPanel.class,"Etch Compensation Tool"),
        new Panel(ExtractDrillsToolPanel.class,"Extract Drills Tool"), new Panel(FiducialsToolPanel.class,"Fiducials Tool"),
        new Panel(FilmToolPanel.class,"Film Tool"), new Panel(InvertGerberToolPanel.class,"Invert Gerber Tool"),
        new Panel(OptimalToolPanel.class,"Optimal Tool"), new Panel(PanelizeToolPanel.class,"Panelize Tool"),
        new Panel(PaintToolPanel.class,"Paint Tool"), new Panel(PunchGerberToolPanel.class,"Punch Gerber Tool"),
        new Panel(RulesCheckToolPanel.class,"Rules Check Tool"), new Panel(SolderPasteToolPanel.class,"SolderPaste Tool"),
        new Panel(QrCodeToolPanel.class,"QRCode Tool"), new Panel(SubtractToolPanel.class,"Subtract Tool"));

    @Test @EnabledOnOs(OS.WINDOWS)
    void realPanelInventory() throws Exception {
        try { Platform.startup(() -> {}); } catch (IllegalStateException started) { }
        FutureTask<Void> task = new FutureTask<>(() -> {
            List<String> report = new ArrayList<>();
            Map<String,Node> roots = new java.util.LinkedHashMap<>();
            for (Panel panel : PANELS) {
                Class<?> host = Class.forName(panel.type.getName()+"$Host");
                Object stub = Proxy.newProxyInstance(host.getClassLoader(),new Class<?>[]{host},(proxy,method,args) -> {
                    if (method.getReturnType() == List.class) return List.of();
                    if (method.getReturnType() == Map.class) return Map.of();
                    if (method.getReturnType() == boolean.class) return false;
                    if (method.getReturnType() == String.class) return "MM";
                    return null;
                });
                Node root = (Node)panel.type.getDeclaredMethod("build",host,Runnable.class).invoke(null,stub,(Runnable)() -> {});
                roots.put(panel.context,root);
            }
            var geometry = new org.locationtech.jts.geom.GeometryFactory().createPolygon(new org.locationtech.jts.geom.Coordinate[]{
                new org.locationtech.jts.geom.Coordinate(0,0),new org.locationtech.jts.geom.Coordinate(10,0),
                new org.locationtech.jts.geom.Coordinate(10,10),new org.locationtech.jts.geom.Coordinate(0,10),new org.locationtech.jts.geom.Coordinate(0,0)});
            var gerber = org.flatcam.cam.gerber.GerberImage.of("MM",Map.of(),geometry,geometry,Map.of());
            var iso = new IsolationToolPanel.SourceCandidate(new javafx.scene.control.TreeItem<>("Copper"),gerber);
            roots.put("Isolation Tool",IsolationToolPanel.build(List.of(iso),iso,List.of(),(a,b,c,d)->false,()->{},List::of,r->{},()->{}));
            var ncc = new NccToolPanel.SourceCandidate(new javafx.scene.control.TreeItem<>("Copper"),"Copper","MM",true,geometry);
            roots.put("NCC Tool",NccToolPanel.build(List.of(ncc),ncc,List.of(),(a,b,c,d)->false,()->{},List::of,r->{},()->{}));
            roots.put("Cutout Tool",CutoutToolPanel.build("MM",(a,b,c)->false,()->{},r->{},()->{}));
            roots.put("Geometry CNC Job",GeometryCncToolPanel.build("MM",geometry,List.of(),r->{},()->{}));
            var excellon = new org.flatcam.cam.excellon.ExcellonParser().parse(List.of("M48","METRIC","T1C1.0","%","T1","X1.0Y1.0","M30"));
            var drill = new DrillGCodeToolPanel.SourceCandidate(new javafx.scene.control.TreeItem<>("Drills"),excellon,Map.of());
            roots.put("Drilling Tool",DrillGCodeToolPanel.build(List.of(drill),drill,List::of,r->{},()->{}));
            var mill = new ExcellonMillingToolPanel.SourceCandidate(new javafx.scene.control.TreeItem<>("Drills"),excellon);
            roots.put("Milling Tool",ExcellonMillingToolPanel.build(List.of(mill),mill,r->{},()->{}));
            roots.put("Calculators",CalculatorsPanel.build());
            roots.put("Transform Tool",TransformToolPanel.build(s->new org.locationtech.jts.geom.Coordinate(0,0),r->{},()->{}));
            var geoHost = captureHost(GeometryEditorController.Host.class,roots);
            var geoEditor = new GeometryEditorController(new PlotAreaView(),geoHost,new AsyncFontCatalog(()->List.of("Dialog"),Runnable::run));
            geoEditor.start(new javafx.scene.control.TreeItem<>("Shapes"),geometry,List.of(),false);
            var excEditor = new ExcellonEditorController(new PlotAreaView(),captureHost(ExcellonEditorController.Host.class,roots));
            excEditor.start(new javafx.scene.control.TreeItem<>("Holes"),excellon);
            var constructor=GerberEditToolPanel.class.getDeclaredConstructors()[0];
            Object[] args=new Object[constructor.getParameterCount()]; Class<?>[] types=constructor.getParameterTypes();
            for(int i=0;i<types.length;i++) args[i]= types[i]==String.class ? (i==0 ? "Copper" : "MM")
                : types[i]==boolean.class ? false : types[i]==Map.class ? Map.of()
                : Proxy.newProxyInstance(types[i].getClassLoader(),new Class<?>[]{types[i]},(proxy,method,arguments)->null);
            var gerberEditor=(GerberEditToolPanel)constructor.newInstance(args);
            roots.put("Editor Gerber",gerberEditor.node());
            var jobs = new org.flatcam.app.job.JobExecutor(1);
            var window = new MainWindow(jobs);
            var about = new AboutDialog(null,ThemeOption.CLASSIC_LIGHT,url -> {},text -> {});
            var cncReview = new CncReviewDialog(null, ThemeOption.CLASSIC_LIGHT,
                    CncPreflight.geometry("Public fixture", "MM", CncPreflightTest.geometry()), true);
            var drillReview = new CncReviewDialog(null, ThemeOption.CLASSIC_LIGHT,
                    CncPreflight.drilling(CncPreflightTest.drilling()));
            var code = new CodeEditor("G21\nG90\n",CodeSyntax.Language.MACHINE,true);
            List<javafx.scene.control.Dialog<?>> dialogs = new ArrayList<>();
            var script = new ScriptEditorPanel("# fixture", null, new ScriptEditorPanel.Host() {
                public boolean run(String text, String label) { return false; }
                public Path chooseSaveTarget(Path suggested) { return null; }
                public void log(String text) { }
            });
            try {
                var preferences = MainWindow.class.getDeclaredMethod("buildPreferencesPanel"); preferences.setAccessible(true);
                var preferencesPane = (PreferencesPane) preferences.invoke(window);
                roots.put("Preferências", preferencesPane);
                var preferencesSplit = (javafx.scene.control.SplitPane) preferencesPane.getCenter();
                @SuppressWarnings("unchecked") var navigation = (javafx.scene.control.TreeView<PreferencesPane.Section>) preferencesSplit.getItems().getFirst();
                for (var section : preferencesPane.sections()) {
                    navigation.getSelectionModel().select(findSection(navigation.getRoot(), section.id()));
                    var split = (javafx.scene.control.SplitPane) preferencesPane.getCenter();
                    roots.put("Preferências / " + section.label(), ((javafx.scene.layout.BorderPane) split.getItems().get(1)).getCenter());
                }
                roots.put("Medir Distância", new MeasurementToolPanel(false, "MM", () -> {}, xy -> {}, () -> {}));
                roots.put("Distância Mínima", new MeasurementToolPanel(true, "IN", () -> {}, xy -> {}, () -> {}));
                roots.put("Progresso compacto", new CompactJobProgress(new javafx.beans.property.SimpleDoubleProperty(.25),
                        new javafx.beans.property.SimpleBooleanProperty(false), () -> {}));
                roots.put("Editor de script", script);
                addDialog(roots, dialogs, "Exportar Gerber", CamExportDialog.gerberDialog(null, "MM"));
                addDialog(roots, dialogs, "Exportar Excellon", CamExportDialog.excellonDialog(null, "IN", false));
                addDialog(roots, dialogs, "Exportar Excellon curvo", CamExportDialog.excellonDialog(null, "MM", true));
                addDialog(roots, dialogs, "Cor do objeto", LayerColorDialog.colorDialog(javafx.scene.paint.Color.YELLOW));
                addDialog(roots, dialogs, "Opacidade do objeto", LayerColorDialog.opacityDialog(.5));
                addDialog(roots, dialogs, "Imprimir como PDF", new PrintPdfDialog(null, 2));
                addDialog(roots, dialogs, "Coordenadas do arco", new ArcCoordinateDialog(null, new org.locationtech.jts.geom.Coordinate(2, 3), "MM"));
                addDialog(roots, dialogs, "Coordenadas Jump", new ArcCoordinateDialog(null, new org.locationtech.jts.geom.Coordinate(2, 3), "IN", false, true));
                addDialog(roots, dialogs, "Ir para localização", window.jumpLocationDialog());
                addDialog(roots, dialogs, "Localizar no objeto", window.locateObjectDialog());
                for (var entry : List.of(new Object[]{"Gerber Object","buildGerberPropertiesPanel",org.flatcam.cam.gerber.GerberImage.class,gerber},
                        new Object[]{"Excellon Object","buildExcellonPropertiesPanel",org.flatcam.cam.excellon.ExcellonImage.class,excellon})) {
                    var method = MainWindow.class.getDeclaredMethod((String)entry[1],javafx.scene.control.TreeItem.class,(Class<?>)entry[2]);
                    method.setAccessible(true);
                    roots.put((String)entry[0],(Node)method.invoke(window,new javafx.scene.control.TreeItem<>("Public fixture"),entry[3]));
                }
                roots.put("Tools Database",new ToolsDatabasePanel(jobs,()->null,path -> {},file -> Icons.fromResource(file,16)));
                roots.put("Sobre",about.getDialogPane().getContent());
                roots.put("Revisão CNC Geometry", cncReview.getDialogPane().getContent());
                roots.put("Revisão CNC Drilling", drillReview.getDialogPane().getContent());
                roots.put("Mapa de cobertura", new CncCoveragePanel("Public fixture", "MM", CncPreflightTest.geometry().tools(), value -> {}, () -> {}, () -> {}));
                roots.put("Revisão CNC mapa", cncReview.getDialogPane().lookupButton(CncReviewDialog.COVERAGE));
                for (String kind : List.of("Geometry", "Drilling")) {
                    var dialog = kind.equals("Geometry") ? cncReview : drillReview;
                    roots.put("Revisão CNC " + kind + " continuar", dialog.getDialogPane().lookupButton(CncReviewDialog.CONTINUE));
                    roots.put("Revisão CNC " + kind + " voltar", dialog.getDialogPane().lookupButton(dialog.getDialogPane().getButtonTypes().getLast()));
                }
                roots.put("Editor de código",code);
            for(var entry:roots.entrySet()) {
                Node root=entry.getValue(); String context=entry.getKey().replace(" — barra", "");
                PanelTooltips.install(root,context);
                List<Node> controls = new ArrayList<>(); collect(root,controls);
                for (Node node : controls) {
                    if (!(node instanceof Control) || node instanceof javafx.scene.control.Label || node instanceof TitledPane
                            || node instanceof javafx.scene.control.Separator || node instanceof javafx.scene.control.ToolBar
                            || node instanceof javafx.scene.control.Accordion || node instanceof javafx.scene.control.TabPane
                            || node instanceof javafx.scene.control.SplitPane || node instanceof javafx.scene.control.ProgressIndicator
                            || node instanceof javafx.scene.control.TextArea area && !area.isEditable() || node instanceof ScrollPane) continue;
                    String label = node instanceof Labeled l ? l.getText() : node.getId();
                    boolean covered = hasHelp(node);
                    report.add(context + " | " + node.getClass().getSimpleName() + " | " + label + " | " + (covered ? "HELP" : "MISSING"));
                }
            }
            Path output = Path.of("target/tooltip-audit.txt"); Files.createDirectories(output.getParent()); Files.write(output,report);
            geoEditor.cancel(); excEditor.cancel();
            assertTrue(report.size() > 200);
            assertEquals(List.of(), report.stream().filter(line -> line.endsWith("MISSING")).toList(),
                    "Actual panel controls without help; see target/tooltip-audit.txt");
            } finally { dialogs.forEach(javafx.scene.control.Dialog::close); script.close(); code.close(); about.close(); cncReview.close(); drillReview.close(); window.disposeViewport(); jobs.shutdown(); }
            return null;
        });
        Platform.runLater(task); task.get(30,TimeUnit.SECONDS);
    }
    private static <T> T captureHost(Class<T> type,Map<String,Node> roots) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},(proxy,method,args)->{
            if(method.getName().equals("openToolPanel")) roots.put((String)args[0],(Node)args[1]);
            if(method.getName().equals("showToolbar") || method.getName().equals("showEditorToolbar"))
                roots.put(type==GeometryEditorController.Host.class ? "Editor Geometry — barra" : "Editor Excellon — barra",(Node)args[0]);
            if(method.getReturnType()==boolean.class)return false;
            return null;
        }));
    }
    private static javafx.scene.control.TreeItem<PreferencesPane.Section> findSection(
            javafx.scene.control.TreeItem<PreferencesPane.Section> node, String id) {
        if (node.getValue() != null && node.getValue().id().equals(id)) return node;
        for (var child : node.getChildren()) {
            var match = findSection(child, id);
            if (match != null) return match;
        }
        return null;
    }
    private static void addDialog(Map<String, Node> roots, List<javafx.scene.control.Dialog<?>> dialogs,
                                  String context, javafx.scene.control.Dialog<?> dialog) {
        dialogs.add(dialog);
        roots.put(context, dialog.getDialogPane().getContent());
        for (var button : dialog.getDialogPane().getButtonTypes())
            roots.put(context + " / " + button.getText(), dialog.getDialogPane().lookupButton(button));
    }
    private static boolean hasHelp(Node node) {
        // A generic description on a container cannot stand in for its individual inputs/actions.
        if (node.getProperties().get(FluidTooltips.TEXT_KEY) instanceof String text && !text.isBlank()) return true;
        return node instanceof Control c && c.getTooltip() != null && !c.getTooltip().getText().isBlank();
    }
    private static void collect(Node root,List<Node> nodes) {
        if(root==null)return; nodes.add(root);
        if(root instanceof TitledPane p)collect(p.getContent(),nodes);
        else if(root instanceof javafx.scene.control.Accordion p)List.copyOf(p.getPanes()).forEach(n -> collect(n,nodes));
        else if(root instanceof javafx.scene.control.TabPane p)List.copyOf(p.getTabs()).forEach(tab -> collect(tab.getContent(),nodes));
        else if(root instanceof javafx.scene.control.SplitPane p)List.copyOf(p.getItems()).forEach(n -> collect(n,nodes));
        else if(root instanceof ScrollPane p)collect(p.getContent(),nodes);
        else if(root instanceof javafx.scene.control.ToolBar p)List.copyOf(p.getItems()).forEach(n->collect(n,nodes));
        else if(root instanceof Pane p)List.copyOf(p.getChildren()).forEach(n->collect(n,nodes));
    }
}
