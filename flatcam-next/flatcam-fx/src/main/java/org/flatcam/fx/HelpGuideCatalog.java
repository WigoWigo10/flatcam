package org.flatcam.fx;

import java.util.List;

/** Authored, offline instructions for the implemented FX workflows. IDs are independent of language. */
final class HelpGuideCatalog {
    record Topic(String id, String group, String icon, List<String> related) {
        Topic { related=List.copyOf(related); }
        String text(String part) { return I18n.text(key(part)); }
        String key(String part) { return "guide.topic."+id+"."+part; }
        String title() { return text("title"); }
        String category() { return I18n.text("guide.group."+group); }
        boolean matches(String query) {
            return PreferencesSearch.matches(query, title(),category(),text("summary"),text("before"),
                    text("steps"),text("check"),text("note"),text("keywords"));
        }
    }
    static final List<String> PARTS=List.of("title","summary","before","steps","check","note","keywords");
    static final List<Topic> TOPICS=List.of(
            new Topic("workflow","start","help.png",List.of("import","panelize","cnc")),
            new Topic("import","start","folder32.png",List.of("outline","panelize","sided","projects")),
            new Topic("outline","prepare","geometry32.png",List.of("panelize","ncc","cutout")),
            new Topic("panelize","prepare","panelize32.png",List.of("outline","isolation","ncc","cutout","drilling")),
            new Topic("sided","prepare","doubleside32.png",List.of("panelize","drilling","cnc")),
            new Topic("isolation","cam","iso_16.png",List.of("ncc","cnc")),
            new Topic("ncc","cam","ncc16.png",List.of("isolation","cnc","troubleshooting")),
            new Topic("cutout","cam","cut16_bis.png",List.of("cnc","panelize")),
            new Topic("drilling","cam","drill32.png",List.of("panelize","cnc")),
            new Topic("cnc","finish","cnc32.png",List.of("code","projects","troubleshooting")),
            new Topic("code","finish","code_editor32.png",List.of("cnc","projects")),
            new Topic("projects","finish","project_save32.png",List.of("workflow","cnc")),
            new Topic("troubleshooting","start","bug32.png",List.of("import","panelize","ncc","cnc")));
    private HelpGuideCatalog() { }
    static Topic find(String id) { return TOPICS.stream().filter(t->t.id().equals(id)).findFirst().orElse(null); }
    static List<Topic> search(String query) { return TOPICS.stream().filter(t->t.matches(query)).toList(); }
}
