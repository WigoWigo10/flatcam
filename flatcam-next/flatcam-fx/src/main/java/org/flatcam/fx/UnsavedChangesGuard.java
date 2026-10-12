package org.flatcam.fx;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Save continuation runs only after a successful, still-current save. Cancellation never continues. */
final class UnsavedChangesGuard {
    enum Choice { SAVE, DISCARD, CANCEL }
    private boolean pending; private long generation;
    void protect(BooleanSupplier changed,Supplier<Choice> choose,Consumer<Consumer<Boolean>> save,Runnable proceed) {
        if(pending)return;
        if(!changed.getAsBoolean()) { proceed.run(); return; }
        Choice choice=choose.get();
        if(choice==Choice.CANCEL)return;
        if(choice==Choice.DISCARD) { proceed.run(); return; }
        pending=true; long ticket=++generation;
        try { save.accept(ok -> {
            if(!pending || ticket!=generation)return; pending=false;
            if(ok && !changed.getAsBoolean())proceed.run();
        }); } catch(RuntimeException error) { pending=false; throw error; }
    }
}
