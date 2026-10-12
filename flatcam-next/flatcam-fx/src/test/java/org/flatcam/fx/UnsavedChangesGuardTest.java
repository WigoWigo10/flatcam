package org.flatcam.fx;
import static org.junit.jupiter.api.Assertions.*;
import java.util.concurrent.atomic.*;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class UnsavedChangesGuardTest {
    @Test void cleanCancelAndDiscardDecisionsDoNotSave() {
        var guard=new UnsavedChangesGuard(); var count=new AtomicInteger();
        guard.protect(() -> false,() -> { fail("clean projects must not prompt"); return null; },done -> fail("no save"),count::incrementAndGet);
        guard.protect(() -> true,() -> UnsavedChangesGuard.Choice.CANCEL,done -> fail("no save"),count::incrementAndGet);
        assertEquals(1,count.get());
        guard.protect(() -> true,() -> UnsavedChangesGuard.Choice.DISCARD,done -> fail("no save"),count::incrementAndGet); assertEquals(2,count.get());
    }
    @Test void saveWaitsForCompletionAndRejectsFailureCancellationAndLaterEdits() {
        var guard=new UnsavedChangesGuard(); var changed=new AtomicBoolean(true); var count=new AtomicInteger();
        var callback=new AtomicReference<Consumer<Boolean>>();
        guard.protect(changed::get,() -> UnsavedChangesGuard.Choice.SAVE,callback::set,count::incrementAndGet);
        assertEquals(0,count.get());
        guard.protect(changed::get,() -> { fail("pending save must not prompt twice"); return null; },done -> {},count::incrementAndGet);
        callback.get().accept(false); assertEquals(0,count.get());
        guard.protect(changed::get,() -> UnsavedChangesGuard.Choice.SAVE,callback::set,count::incrementAndGet);
        callback.get().accept(true); assertEquals(0,count.get(),"a later edit must block continuation");
        guard.protect(changed::get,() -> UnsavedChangesGuard.Choice.SAVE,callback::set,count::incrementAndGet);
        changed.set(false); callback.get().accept(true); assertEquals(1,count.get());
    }
    @Test void aDuplicateLateCallbackCannotCompleteANewerSaveAttempt() {
        var guard=new UnsavedChangesGuard(); var changed=new AtomicBoolean(true); var count=new AtomicInteger();
        var callback=new AtomicReference<Consumer<Boolean>>();
        guard.protect(changed::get,() -> UnsavedChangesGuard.Choice.SAVE,callback::set,count::incrementAndGet);
        Consumer<Boolean> old=callback.get(); old.accept(false);
        guard.protect(changed::get,() -> UnsavedChangesGuard.Choice.SAVE,callback::set,count::incrementAndGet);
        changed.set(false); old.accept(true); assertEquals(0,count.get());
        callback.get().accept(true); assertEquals(1,count.get());
    }
}
