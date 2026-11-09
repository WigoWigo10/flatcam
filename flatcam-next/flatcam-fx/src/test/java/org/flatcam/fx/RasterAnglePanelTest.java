package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;
import java.util.concurrent.atomic.AtomicReference;
import javafx.scene.control.*;
import javafx.scene.input.*;
import org.flatcam.cam.ncc.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.condition.*;

@EnabledOnOs(OS.WINDOWS)
class RasterAnglePanelTest {
    @ParameterizedTest @ValueSource(strings={"MM","IN"})
    void nccAngleBelongsToEachToolAndRawDraftSurvivesUndoAndCodec(String units)throws Exception {
        TerminalPanelTest.fx(()->{
            var output=new AtomicReference<Object>();
            var root=CamNumericInputTest.form("NCC",units,output);
            var table=(TableView<?>)root.lookup("#ncc-tools");table.getSelectionModel().clearAndSelect(0);
            var method=(ComboBox<NccMethod>)root.lookup("#ncc-method");
            var automatic=(CheckBox)root.lookup("#ncc-angle-auto");var degrees=(TextField)root.lookup("#ncc-angle-degrees");
            var generate=(Button)root.lookup("#ncc-generate");var angle=(RasterAngleInput)root.lookup("#ncc-angle");
            assertTrue(angle.isDisabled());method.setValue(NccMethod.LINES);assertFalse(angle.isDisabled());
            assertTrue(degrees.isDisabled());automatic.setSelected(false);assertFalse(degrees.isDisabled());degrees.setText("37,5123456");
            var state=CamToolState.of(root).capture().get();double diameter=state.getJSONArray("tools").getJSONObject(0).getDouble("diameter");
            generate.fire();assertNotNull(output.get());assertEquals(37.5123456,((NccToolPanel.Result)output.get()).parameters().settingsFor(diameter).lineAngleDegrees());
            table.getSelectionModel().clearAndSelect(1);assertTrue(automatic.isSelected());
            table.getSelectionModel().clearAndSelect(0);assertFalse(automatic.isSelected());assertEquals("37,5123456",degrees.getText());
            degrees.setText("unfinished");output.set(null);generate.fire();assertNull(output.get());
            var codec=CamToolState.of(root);var invalidDraft=codec.capture().get();
            var reopened=CamNumericInputTest.form("NCC",units,new AtomicReference<>());CamToolState.of(reopened).restore().accept(invalidDraft);
            assertEquals("unfinished",((TextField)reopened.lookup("#ncc-angle-degrees")).getText());
            assertFalse(((CheckBox)reopened.lookup("#ncc-angle-auto")).isSelected());
            key(table,KeyCode.Z);assertEquals("37,5123456",degrees.getText());key(table,KeyCode.Y);assertEquals("unfinished",degrees.getText());
            method.setValue(NccMethod.STANDARD);assertTrue(angle.isDisabled());generate.fire();assertNotNull(output.get(),"an unused draft must not block a different method");
            // A Beta5 draft has no angle fields and must still reopen in Automatic mode.
            var rows=state.getJSONArray("tools");for(int i=0;i<rows.length();i++){rows.getJSONObject(i).remove("angleAutomatic");rows.getJSONObject(i).remove("lineAngle");}
            codec.restore().accept(state);assertTrue(automatic.isSelected());
            assertThrows(IllegalArgumentException.class,()->RasterAngleInput.value(false,"180"));
            assertThrows(IllegalArgumentException.class,()->RasterAngleInput.value(false,"NaN"));
            assertNull(RasterAngleInput.value(true,"unfinished"));return null;
        });
    }
    static void key(TableView<?> table,KeyCode code){table.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED,"","",code,false,true,false,false));}
}
