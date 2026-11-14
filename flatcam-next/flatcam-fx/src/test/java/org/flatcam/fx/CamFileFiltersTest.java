package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import javafx.stage.FileChooser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CamFileFiltersTest {
    @ParameterizedTest @ValueSource(strings={"gko","gto","gdl","gts","gbs","gbo","gtp","gbp","gbr","grb","ger","art","pho","cmp","sol","g1","g32"})
    void listsGerberLayersFromTheReportAndCommonOtherProducers(String suffix){
        assertTrue(CamFileFilters.gerber().getExtensions().contains("*."+suffix));
        assertEquals("module."+suffix,CamFileFilters.gerberSaveName("module."+suffix));
    }
    @ParameterizedTest @ValueSource(strings={"gko","GTO","GDL","gts","GBS"})
    void preservesTheGerberLayerSuffixAfterEditorRenaming(String suffix){
        assertEquals("module_edit."+suffix,CamFileFilters.gerberSaveName("module."+suffix+"_edit"));
        assertEquals("module_edit_2."+suffix,CamFileFilters.gerberSaveName("module."+suffix+"_edit_2"));
    }
    @Test void offersAnExplicitAllFilesFallbackWhileKeepingThePrimaryFilterFirst(){
        var chooser=new FileChooser();var primary=CamFileFilters.gerber();CamFileFilters.configureOpen(chooser,primary);
        assertSame(primary,chooser.getExtensionFilters().getFirst());assertEquals(2,chooser.getExtensionFilters().size());
        assertEquals(List.of("*"),chooser.getExtensionFilters().getLast().getExtensions());
        assertEquals("module.gbr",CamFileFilters.gerberSaveName("module"));
    }
}
