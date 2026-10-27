package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

/** Guards the HotSpot mixed UTF16/Latin1 search path used by review/search/tooltips. */
class JvmStringSearchTest {
    private static boolean find(String text,String needle) { return text.contains(needle); }
    @Test void mixedEncodingSubstringRemainsCorrectAfterWarmup() {
        String needle=new String(new char[]{'3','.','0','0','2','5'});
        String[] texts={"3.00125 \u2192 3.0025 mm","3.00125 \u2192 3.0025 in","native value 3.0025",
                "no such value here","125 \u2192 126","3.00125 \u2192 3.0025 mm /min"};
        for(int i=0;i<200000;i++) {
            int n=i%texts.length;
            assertEquals(n==0||n==1||n==2||n==5,find(texts[n],needle),"iteration="+i+" value="+texts[n]);
        }
    }
}
