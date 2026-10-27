package org.flatcam.review;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.prefs.*;

/** Launch-time isolation for REAL JavaFX GUI review: no registry or user-preference files. */
public final class ReviewPreferencesFactory implements PreferencesFactory {
    private static final Preferences USER=new Memory(null,""), SYSTEM=new Memory(null,"");
    @Override public Preferences userRoot() { return USER; }
    @Override public Preferences systemRoot() { return SYSTEM; }
    public static void main(String[] args) throws Exception {
        Preferences node=Preferences.userRoot().node("flatcam-review-probe");
        if(node.get("marker",null)!=null) throw new AssertionError("Review store was not fresh");
        node.put("marker","isolated"); node.flush();
        if(!"isolated".equals(node.get("marker",null))) throw new AssertionError("Review store cannot round-trip");
        if(!(Preferences.userRoot() instanceof Memory)) throw new AssertionError("Real preferences factory selected");
        System.out.println("PASS: java.util.prefs isolated in memory");
    }
    private static final class Memory extends AbstractPreferences {
        private final Map<String,String> values=new LinkedHashMap<>();
        private final Map<String,Memory> children=new LinkedHashMap<>();
        Memory(Memory parent,String name) { super(parent,name); }
        protected void putSpi(String key,String value) { values.put(key,value); }
        protected String getSpi(String key) { return values.get(key); }
        protected void removeSpi(String key) { values.remove(key); }
        protected void removeNodeSpi() { if(parent() instanceof Memory p) p.children.remove(name()); values.clear(); children.clear(); }
        protected String[] keysSpi() { return values.keySet().toArray(String[]::new); }
        protected String[] childrenNamesSpi() { return children.keySet().toArray(String[]::new); }
        protected AbstractPreferences childSpi(String name) { return children.computeIfAbsent(name,k->new Memory(this,k)); }
        protected void syncSpi() { }
        protected void flushSpi() { }
    }
}
