package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javafx.event.Event;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@EnabledOnOs(OS.WINDOWS)
class CodeEditorTest {
    @ParameterizedTest @EnumSource(ThemeOption.class)
    void allThemesHaveLineNumbersCaretStatusReadableSyntaxAndLiveThemeChanges(ThemeOption theme) throws Exception {
        CodeEditor editor = TerminalPanelTest.fx(() -> {
            var code = new CodeEditor("(PCB header)\nG21\nG90\nG0 X2.5 Y-3\nG1 Z-0.1 F100\nM5\n", CodeSyntax.Language.MACHINE, true);
            var scene = new Scene(code, 720, 420); theme.applyTo(scene); code.applyCss(); code.resize(720,420); code.layout();
            code.area().moveTo(3, 5); return code;
        });
        try {
            // Wait outside FX; the debounce/worker must be able to run while FX remains free.
            await(() -> TerminalPanelTest.fx(() -> !editor.area().getStyleOfChar(0).isEmpty()));
            TerminalPanelTest.fx(() -> {
                editor.applyCss(); editor.layout();
                assertFalse(editor.area().isWrapText()); assertTrue(editor.area().isEditable());
                assertTrue(((Label)editor.lookup("#code-position")).getText().startsWith("Ln 4, Col 6"));
                assertNotNull(editor.lookup(".lineno"));
                assertNotNull(editor.lookup(".code-comment")); assertNotNull(editor.lookup(".code-command"));
                Color fill = (Color) ((Text)editor.lookup(".code-command")).getFill();
                assertEquals(theme.isDark(), fill.getBrightness() > 0.7);
                assertTrue(MainWindow.isTextInputTarget(editor.area()));
                assertTrue(MainWindow.isTextInputTarget(editor.lookup(".code-command")));
                if (Boolean.getBoolean("flatcam.tests.snapshots")) snapshot(editor, "code-editor-"+theme+".png");
                ThemeOption opposite = theme.isDark() ? ThemeOption.CLASSIC_LIGHT : ThemeOption.CLASSIC_DARK;
                opposite.applyTo(editor.getScene()); editor.applyCss(); editor.layout();
                Color changed = (Color)((Text)editor.lookup(".code-command")).getFill(); assertNotEquals(fill, changed);
                return null;
            });
        } finally { TerminalPanelTest.fx(() -> { editor.close(); return null; }); }
    }
    @Test void programmaticLoadIsNotAnEditAndUndoRedoAffectOnlyText() throws Exception {
        TerminalPanelTest.fx(() -> {
            CodeEditor editor = new CodeEditor("G21\r\nG90\r\n", CodeSyntax.Language.MACHINE, true);
            try {
                var edits = new java.util.concurrent.atomic.AtomicInteger(); editor.onEdit(edits::incrementAndGet);
                assertEquals("G21\nG90\n", editor.getText());
                editor.setText("G0 X0\n"); assertEquals(0, edits.get());
                editor.area().appendText("M5\n"); assertEquals(1, edits.get());
                editor.area().undo(); assertEquals("G0 X0\n", editor.getText());
                editor.area().redo(); assertEquals("G0 X0\nM5\n", editor.getText());
                editor.setEditable(false); assertFalse(editor.area().isEditable());
            } finally { editor.close(); } return null;
        });
    }
    @Test void largeDocumentsPrepareInBackgroundAndStaleLoadsCannotOverwriteNewContent() throws Exception {
        String large = "G01 X1.5 Y2.5 F100\n".repeat(40000);
        CodeEditor editor = TerminalPanelTest.fx(() -> {
            var code = new CodeEditor(large, CodeSyntax.Language.MACHINE, true);
            assertTrue(code.isLoading()); assertFalse(code.area().isEditable()); return code;
        });
        try {
            await(() -> TerminalPanelTest.fx(() -> !editor.isLoading()));
            TerminalPanelTest.fx(() -> {
                assertEquals(40001, editor.area().getParagraphs().size()); assertEquals(large, editor.getText());
                assertTrue(editor.area().isEditable());
                editor.setText(large); editor.setText("M5\n"); return null;
            });
            // Barrier queued after the old worker finishes; ensure stale result is ignored.
            Thread.sleep(250);
            TerminalPanelTest.fx(() -> { assertEquals("M5\n", editor.getText()); editor.close(); return null; });
        } finally { TerminalPanelTest.fx(() -> { editor.close(); return null; }); }
    }
    @Test void missingFileReportsFailureWithoutBlockingOrEnablingEditing() throws Exception {
        CountDownLatch failed = new CountDownLatch(1);
        CodeEditor editor = TerminalPanelTest.fx(() -> {
            var code = new CodeEditor("", CodeSyntax.Language.GERBER, false);
            code.loadFile(Path.of("target", "missing-source-for-editor-test.gbr"), message -> failed.countDown()); return code;
        });
        try {
            assertTrue(failed.await(10, TimeUnit.SECONDS));
            TerminalPanelTest.fx(() -> { assertFalse(editor.isLoading()); assertFalse(editor.area().isEditable()); return null; });
        } finally { TerminalPanelTest.fx(() -> { editor.close(); return null; }); }
    }
    @Test void literalSearchWrapsInBothDirectionsAndTreatsRegexCharactersLiterally() {
        assertEquals(6, CodeEditor.findLiteral("X1 X2 X1", "X1", 2, false));
        assertEquals(0, CodeEditor.findLiteral("X1 X2 X1", "X1", 8, false));
        assertEquals(6, CodeEditor.findLiteral("X1 X2 X1", "X1", -1, true));
        assertEquals(0, CodeEditor.findLiteral("X1 X2 X1", "X1", 5, true));
        assertEquals(0, CodeEditor.findLiteral("(.*) X", "(.*)", 0, false));
        assertEquals(-1, CodeEditor.findLiteral("G1", "g1", 0, false));
    }
    @Test void searchShortcutRunsAsynchronouslyAndUndoRedoKeyboardStayInsideTheEditor() throws Exception {
        CodeEditor editor = TerminalPanelTest.fx(() -> {
            var code = new CodeEditor("G21\nG01 X1\nG01 X2\n", CodeSyntax.Language.MACHINE, true);
            var scene = new Scene(code,600,300); ThemeOption.CLASSIC_DARK.applyTo(scene); code.resize(600,300);code.applyCss();code.layout();
            Event.fireEvent(code.area(),key(KeyCode.F,true));
            var field=(javafx.scene.control.TextField)code.lookup("#code-search"); assertTrue(field.isVisible()); field.setText("X2");
            Event.fireEvent(field,key(KeyCode.ENTER,false)); return code;
        });
        try {
            await(() -> TerminalPanelTest.fx(() -> editor.area().getSelectedText().equals("X2")));
            TerminalPanelTest.fx(() -> {
                editor.area().appendText("M5\n");
                Event.fireEvent(editor.area(),key(KeyCode.Z,true)); assertFalse(editor.getText().contains("M5"));
                Event.fireEvent(editor.area(),key(KeyCode.Y,true)); assertTrue(editor.getText().contains("M5"));
                Event.fireEvent(editor.area(),key(KeyCode.ESCAPE,false));
                assertFalse(editor.lookup("#code-search").getParent().isVisible());
                assertNotNull(editor.area().getContextMenu()); return null;
            });
        } finally { TerminalPanelTest.fx(() -> {editor.close();return null;}); }
    }
    private static KeyEvent key(KeyCode code,boolean control) {
        return new KeyEvent(KeyEvent.KEY_PRESSED,"","",code,false,control,false,false);
    }
    @Test void generatedWktIsBrokenIntoVirtualizableLinesWithoutChangingGeometry() throws Exception {
        TerminalPanelTest.fx(() -> {
            String wkt="LINESTRING (0 0, 1 2, 3 4)";
            try (CodeEditor editor=new CodeEditor(wkt,CodeSyntax.Language.GEOMETRY,false)) {
                assertEquals(3,editor.area().getParagraphs().size());
                var reader=new org.locationtech.jts.io.WKTReader();
                assertTrue(reader.read(wkt).equalsExact(reader.read(editor.getText())));
            } return null;
        });
    }
    private interface Check { boolean get() throws Exception; }
    private static void await(Check check) throws Exception {
        long end = System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
        while (!check.get() && System.nanoTime()<end) Thread.sleep(30);
        assertTrue(check.get(), "asynchronous editor operation timed out");
    }
    private static void snapshot(javafx.scene.Node node, String name) throws Exception {
        var image=node.snapshot(null,null);
        var png=new java.awt.image.BufferedImage((int)image.getWidth(),(int)image.getHeight(),java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<png.getHeight();y++) for(int x=0;x<png.getWidth();x++) png.setRGB(x,y,image.getPixelReader().getArgb(x,y));
        javax.imageio.ImageIO.write(png,"png",Path.of("target",name).toFile());
    }
}
