package org.prep.day7;

import java.lang.ref.PhantomReference;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;

/**
 * Strong / Weak / Soft / Phantom references, side by side.
 * See docs/day7/02-java-jvm-memory-leaks-performance.md for the full write-up
 * of when each one is actually used (caches, listener registries, WeakHashMap,
 * post-mortem cleanup replacing finalize()).
 * System.gc() is only a *hint* to the JVM (no reference type's collection is
 * contractually guaranteed by calling it), which is exactly why Soft references
 * below are NOT reliably reclaimed here -- there's no real memory pressure in
 * this demo, and Soft references are specifically defined to survive until the
 * JVM is close to an OutOfMemoryError, not merely because gc() was called.
 */
public class ReferenceTypesDemo {

    public static void main(String[] args) throws InterruptedException {
        // --- Strong reference ---
        // As long as `strong` is on the stack (or reachable from a GC root through
        // it), the object it points to can NEVER be collected, no matter how much
        // memory pressure exists. This is the default and the source of every
        // "memory leak" in Java: an unintentionally long-lived strong reference.
        Object strong = new Object();
        System.out.println("Strong reference held: " + (strong != null));

        // --- Weak reference ---
        // Does NOT keep the object alive. The moment the only remaining references
        // are weak, the object becomes eligible for collection at the *next* GC
        // cycle, even under no memory pressure at all. Backs WeakHashMap and is
        // the standard fix for listener-registry leaks (see the doc).
        Object weakTarget = new Object();
        WeakReference<Object> weakRef = new WeakReference<>(weakTarget);
        weakTarget = null; // drop the only strong reference
        System.gc();
        Thread.sleep(100); // give the collector a moment to actually run
        System.out.println("Weak reference cleared after gc(): " + (weakRef.get() == null));

        // --- Soft reference ---
        // Also does not keep the object alive, but the JVM is specifically allowed
        // to hold onto soft-reachable objects until it's under real memory pressure
        // (close to throwing OutOfMemoryError) before clearing them -- a built-in
        // "clear this cache before you crash, not before" policy. This is why
        // SoftReference is the standard building block for a memory-sensitive cache.
        Object softTarget = new Object();
        SoftReference<Object> softRef = new SoftReference<>(softTarget);
        softTarget = null;
        System.gc();
        Thread.sleep(100);
        System.out.println("Soft reference still alive (expected, no memory pressure): "
                + (softRef.get() != null));

        // --- Phantom reference ---
        // get() ALWAYS returns null -- a phantom reference can never be used to
        // resurrect or even read the object. Its only purpose is the ReferenceQueue:
        // once the object is finalized and about to be reclaimed, the JVM enqueues
        // this reference, letting code run cleanup (closing a native handle, e.g.)
        // reliably, after the object is truly unreachable -- the modern, safe
        // replacement for the deprecated Object.finalize().
        ReferenceQueue<Object> queue = new ReferenceQueue<>();
        Object phantomTarget = new Object();
        PhantomReference<Object> phantomRef = new PhantomReference<>(phantomTarget, queue);
        System.out.println("phantomRef.get() is always null: " + (phantomRef.get() == null));

        phantomTarget = null;
        System.gc();
        Thread.sleep(100);
        Reference<?> enqueued = queue.poll();
        System.out.println("Phantom reference enqueued after gc(): " + (enqueued != null));
    }
}
