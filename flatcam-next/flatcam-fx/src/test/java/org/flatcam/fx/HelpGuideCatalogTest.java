package org.flatcam.fx;

import static org.junit.jupiter.api.Assertions.*;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;

class HelpGuideCatalogTest {
    @Test void everyArticleHasTranslatedInstructionsExistingIconsAndReachableRelatedTopics() {
        var ids=new HashSet<String>();
        for(var topic:HelpGuideCatalog.TOPICS) {
            assertTrue(ids.add(topic.id()),topic.id());
            assertNotNull(Icons.class.getResource("icons/"+topic.icon()),topic.icon());
            assertNotNull(Icons.class.getResource("icons/dark/"+topic.icon()),topic.icon());
            for(var language:UiLanguage.values()) {
                var catalog=I18n.catalog(language);
                assertFalse(catalog.get("guide.group."+topic.group()).isBlank());
                for(String part:HelpGuideCatalog.PARTS) {
                    String body=catalog.get(topic.key(part));assertNotNull(body,topic.key(part));assertFalse(body.isBlank());
                    assertEquals(0,(body.length()-body.replace("**","").length())/2%2,topic.key(part));
                    if(part.equals("steps"))assertTrue(body.split("\n").length>=4,topic.id());
                }
            }
            assertFalse(topic.related().isEmpty());
            for(String related:topic.related()) {
                assertNotNull(HelpGuideCatalog.find(related),related);assertNotEquals(topic.id(),related);
            }
        }
        for(String main:List.of("workflow","import","outline","panelize","isolation","ncc","cutout","drilling","cnc","projects"))
            assertTrue(ids.contains(main),main);
        var reachable=new HashSet<String>();visit("workflow",reachable);assertEquals(ids,reachable);
    }

    private static void visit(String id,HashSet<String> visited) {
        if(visited.add(id))HelpGuideCatalog.find(id).related().forEach(next->visit(next,visited));
    }

    @Test void searchIncludesBodyAndIgnoresAccentsWithoutSearchingOnlyTitles() {
        assertEquals(HelpGuideCatalog.TOPICS,HelpGuideCatalog.search(" "));
        assertTrue(HelpGuideCatalog.search("furacao").stream().anyMatch(t->t.id().equals("drilling")));
        assertTrue(HelpGuideCatalog.search("dessincronizar passo").stream().anyMatch(t->t.id().equals("panelize")));
        assertTrue(HelpGuideCatalog.search("Connect Rest").stream().anyMatch(t->t.id().equals("ncc")));
        assertTrue(HelpGuideCatalog.search("zzzz-no-topic").isEmpty());
        assertNull(HelpGuideCatalog.find("missing"));
    }
}
