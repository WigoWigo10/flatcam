package org.flatcam.fx;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Objects;

/** Cheap version/style stamps, not geometry serialization or an extra retained copy of the project. */
final class ProjectChangeTracker {
    record Item(Object key,String name,Object version,Object appearance) { }
    record State(long epoch,List<Item> items,Object settings) { State { items=List.copyOf(items); } }
    private record SavedItem(WeakReference<Object> key,String name,WeakReference<Object> version,Object appearance) { }
    private List<SavedItem> items; private long epoch; private Object settings;
    boolean initialized() { return items!=null; }
    void saved(State state) {
        items=state.items().stream().map(i -> new SavedItem(new WeakReference<>(i.key()),i.name(),new WeakReference<>(i.version()),i.appearance())).toList();
        epoch=state.epoch(); settings=state.settings();
    }
    boolean changed(State current) {
        if(items==null)return !current.items().isEmpty();
        if(epoch!=current.epoch() || items.size()!=current.items().size() || !Objects.equals(settings,current.settings()))return true;
        for(int i=0;i<items.size();i++) {
            var before=items.get(i); var now=current.items().get(i);
            if(before.key().get()!=now.key() || before.version().get()!=now.version() || !Objects.equals(before.name(),now.name()) || !Objects.equals(before.appearance(),now.appearance()))return true;
        }
        return false;
    }
}
