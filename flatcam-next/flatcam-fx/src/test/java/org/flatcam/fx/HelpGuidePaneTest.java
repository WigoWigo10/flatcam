package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import javafx.geometry.Orientation;
import javafx.scene.Scene;
import javafx.scene.control.*;
import org.flatcam.app.job.JobExecutor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

@EnabledOnOs(OS.WINDOWS)
class HelpGuidePaneTest {
    @Test void searchEmptyStateAndRelatedLinksNavigateWithoutLosingTheArticle() throws Exception {
        TerminalPanelTest.fx(()-> {
            var guide=new HelpGuidePane(icon->Icons.fromResource(icon,16));new Scene(guide,1050,780);
            guide.applyCss();guide.layout();
            TextField search=(TextField)guide.lookup("#guide-search");
            var list=(ListView<?>)guide.lookup("#guide-topics");
            assertEquals("workflow",guide.selectedTopic());
            search.setText("dessincronizar");assertEquals(1,list.getItems().size());assertEquals("panelize",guide.selectedTopic());
            search.setText("no-topic-zzzz");assertTrue(list.getItems().isEmpty());assertNotNull(guide.lookup("#guide-article"));
            assertNull(guide.lookup("#guide-page-panelize"));
            ((Button)guide.lookup("#guide-clear")).fire();assertEquals(HelpGuideCatalog.TOPICS.size(),list.getItems().size());
            assertEquals("panelize",guide.selectedTopic());
            search.setText("panelizar");((Button)guide.lookup("#guide-link-ncc")).fire();
            assertEquals("",search.getText());assertEquals("ncc",guide.selectedTopic());assertEquals(HelpGuideCatalog.TOPICS.size(),list.getItems().size());
            assertNotNull(guide.lookup("#guide-page-ncc"));
            assertThrows(IllegalArgumentException.class,()->guide.selectTopic("unknown"));
            return null;
        });
    }

    @Test void menuOpensOneClosableGuideTabAndReopeningPreservesTheSelectedArticle() throws Exception {
        var jobs=new JobExecutor(1);
        try {
            TerminalPanelTest.fx(()-> {
                var window=new MainWindow(jobs);
                try {
                    var scene=window.createScene();scene.getRoot().applyCss();scene.getRoot().layout();
                    MenuItem menu=scene.getRoot().lookupAll(".menu-bar").stream().map(MenuBar.class::cast)
                            .flatMap(bar->bar.getMenus().stream()).flatMap(m->m.getItems().stream())
                            .filter(item->"help-guide-menu".equals(item.getId())).findFirst().orElseThrow();
                    assertFalse(menu.isDisable());assertEquals(new javafx.scene.input.KeyCodeCombination(javafx.scene.input.KeyCode.F1),menu.getAccelerator());
                    menu.fire();var guide=(HelpGuidePane)scene.lookup("#help-guide");assertNotNull(guide);guide.selectTopic("panelize");
                    menu.fire();
                    var field=MainWindow.class.getDeclaredField("centerTabs");field.setAccessible(true);var tabs=(TabPane)field.get(window);
                    assertEquals(1,tabs.getTabs().stream().filter(t->"help-guide-tab".equals(t.getId())).count());
                    Tab tab=tabs.getSelectionModel().getSelectedItem();assertTrue(tab.isClosable());assertEquals("panelize",guide.selectedTopic());
                    tabs.getTabs().remove(tab);assertEquals("Plot Area",tabs.getTabs().getFirst().getText());
                    menu.fire();assertEquals("workflow",((HelpGuidePane)scene.lookup("#help-guide")).selectedTopic());
                } finally {window.disposeViewport();}
                return null;
            });
        } finally {jobs.shutdown();}
    }

    @Test void narrowLayoutStacksNavigationAndRichTextUsesTheActivePalette() throws Exception {
        TerminalPanelTest.fx(()-> {
            var guide=new HelpGuidePane(icon->Icons.fromResource(icon,16));var scene=new Scene(guide,1100,800);
            var split=(SplitPane)guide.lookup("#guide-split");
            for(var theme:ThemeOption.values()) {
                theme.applyTo(scene);guide.resize(1100,800);guide.applyCss();guide.layout();assertEquals(Orientation.HORIZONTAL,split.getOrientation());
                for(String id:List.of("panelize","ncc","cnc")) {
                    guide.selectTopic(id);guide.applyCss();guide.layout();
                    assertNotNull(guide.lookup("#guide-page-"+id));
                    guide.snapshot(null,null); // Finish CSS/layout for newly attached ScrollPane content.
                    var text=(javafx.scene.text.Text)guide.lookup(".guide-prose");
                    var title=(Label)guide.lookup(".guide-title");assertEquals(title.getTextFill(),text.getFill());
                    assertTrue(guide.lookupAll(".guide-emphasis").size()>0);
                }
                guide.resize(460,680);guide.applyCss();guide.layout();assertEquals(Orientation.VERTICAL,split.getOrientation());
                var scroll=(ScrollPane)guide.lookup("#guide-article");assertTrue(scroll.isFitToWidth());
                assertTrue(scroll.getViewportBounds().getWidth()>250);assertTrue(scroll.getViewportBounds().getHeight()>150);
            }
            return null;
        });
    }
}
