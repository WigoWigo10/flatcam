package org.flatcam.fx;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/** Bounded history for confirmed Plot Area moves; unrelated edits invalidate it. */
final class PlotMoveHistory<T> {

    record Move<T>(List<T> targets, double dx, double dy) {
        Move {
            targets = List.copyOf(targets);
            if (targets.isEmpty() || !Double.isFinite(dx) || !Double.isFinite(dy)) {
                throw new IllegalArgumentException("A move needs targets and a finite offset");
            }
        }
    }

    private static final int LIMIT = 100;
    private final Deque<Move<T>> undo = new ArrayDeque<>();
    private final Deque<Move<T>> redo = new ArrayDeque<>();

    void record(List<T> targets, double dx, double dy) {
        undo.addLast(new Move<>(targets, dx, dy));
        if (undo.size() > LIMIT) {
            undo.removeFirst();
        }
        redo.clear();
    }

    Move<T> undo() {
        if (undo.isEmpty()) {
            return null;
        }
        Move<T> move = undo.removeLast();
        redo.addLast(move);
        return move;
    }

    Move<T> redo() {
        if (redo.isEmpty()) {
            return null;
        }
        Move<T> move = redo.removeLast();
        undo.addLast(move);
        return move;
    }

    void clear() {
        undo.clear();
        redo.clear();
    }
}
