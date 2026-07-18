package org.prep.day6;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Thread-safe counter using AtomicInteger (CAS loop internally) vs a plain int,
 * which loses increments under real concurrency. See docs/day6/02-java-concurrency-deep.md.
 */
public class AtomicCounterDemo {

    private static int unsafeCounter = 0;
    private static final AtomicInteger safeCounter = new AtomicInteger(0);

    public static void main(String[] args) throws InterruptedException {
        int threads = 10;
        int incrementsPerThread = 10_000;

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        for (int t = 0; t < threads; t++) {
            executor.submit(() -> {
                for (int i = 0; i < incrementsPerThread; i++) {
                    unsafeCounter++;                 // read-modify-write race, loses increments
                    safeCounter.incrementAndGet();    // atomic CAS loop, never loses one
                }
            });
        }

        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        int expected = threads * incrementsPerThread;
        System.out.println("Expected:     " + expected);
        System.out.println("unsafeCounter: " + unsafeCounter + " (usually less than expected)");
        System.out.println("safeCounter:   " + safeCounter.get() + " (always equals expected)");
    }
}
