package org.flatcam.fx;

import java.util.ArrayDeque;
import org.flatcam.cam.excellon.ExcellonImage;

/** Stores scalar edits only; an unrelated image replacement invalidates this object's history. */
final class ExcellonDiameterHistory {
    record Change(int toolId, double before, double after) { }
    enum Action { EDIT, UNDO, REDO }
    private final ArrayDeque<Change> undo=new ArrayDeque<>(), redo=new ArrayDeque<>();
    private ExcellonImage current;
    void align(ExcellonImage image) {
        if (current != image) { undo.clear(); redo.clear(); current=image; }
    }
    Change undo() { return undo.peekLast(); }
    Change redo() { return redo.peekLast(); }
    void applied(ExcellonImage result, Change change, Action action) {
        switch (action) {
            case EDIT -> { undo.addLast(change); if (undo.size()>100) undo.removeFirst(); redo.clear(); }
            case UNDO -> { if (!change.equals(undo.removeLast())) throw new IllegalStateException("Stale diameter undo"); redo.addLast(change); }
            case REDO -> { if (!change.equals(redo.removeLast())) throw new IllegalStateException("Stale diameter redo"); undo.addLast(change); }
        }
        current=result;
    }
}
