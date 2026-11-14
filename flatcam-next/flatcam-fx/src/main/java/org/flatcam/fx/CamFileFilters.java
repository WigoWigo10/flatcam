package org.flatcam.fx;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javafx.stage.FileChooser;

/** One extension inventory for Gerber open, export and source-save dialogs. Extension is not content validation. */
final class CamFileFilters {
    private static final Set<String> SUFFIXES=suffixes();
    private static final String SUFFIX_PATTERN=SUFFIXES.stream().map(Pattern::quote).collect(Collectors.joining("|"));
    private static final Pattern EDITED=Pattern.compile("(?i)\\.("+SUFFIX_PATTERN+")(_edit(?:_\\d+)?)$");
    private static Set<String> suffixes(){
        var extensions=new LinkedHashSet<>(List.of("gbr","ger","grb","gbx","cmp","sol","gtl","gbl","gto","gbo","gts","gbs","gtp","gbp","gko","gml","gdl","gdo","gbd","art","pho","bot","bsm","crc","crs","mil","outline","plc","pls","smb","smt","spb","spt","ssb","sst","stc","sts","top","tsm","txt"));
        for(int n=0;n<=9;n++)extensions.add("gb"+n);
        for(int n=0;n<=32;n++){extensions.add("gm"+n);if(n>0){extensions.add("g"+n);extensions.add("ly"+n);}}
        return Collections.unmodifiableSet(extensions);
    }
    static FileChooser.ExtensionFilter gerber(){
        var patterns=new ArrayList<String>();for(String suffix:SUFFIXES){patterns.add("*."+suffix);patterns.add("*."+suffix.toUpperCase(Locale.ROOT));}
        return new FileChooser.ExtensionFilter("Gerber",patterns);
    }
    static void configureOpen(FileChooser chooser,FileChooser.ExtensionFilter primary){
        chooser.getExtensionFilters().addAll(primary,new FileChooser.ExtensionFilter(I18n.text("ui.0d4623424b45"),"*"));
        chooser.setSelectedExtensionFilter(primary);
    }
    static String gerberSaveName(String name){
        String result=EDITED.matcher(name).replaceFirst("$2.$1");
        int dot=result.lastIndexOf('.');return dot>=0&&SUFFIXES.contains(result.substring(dot+1).toLowerCase(Locale.ROOT))?result:result+".gbr";
    }
    private CamFileFilters(){ }
}
