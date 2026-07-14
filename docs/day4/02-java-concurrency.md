# Java — Concurrency

## Part 1 — Concepts (read this first)

### Process vs Thread
| | Process | Thread |
|---|---|---|
| Memory | Own isolated address space | Shares heap/method area with other threads in the same process |
| Communication | IPC (sockets, pipes, shared files) — expensive | Shared memory (fields, objects) — cheap, but needs synchronization |
| Creation cost | Heavy (new address space, OS bookkeeping) | Light (shares the process's memory) |
| Failure isolation | One process crashing doesn't (usually) take down another | An uncaught error in one thread can corrupt shared state visible to all threads in the process |

A JVM process can host many threads. Each thread gets its **own stack** (see [../day2/07-jvm-memory.md](../day2/07-jvm-memory.md#stack)) but they all share the **same heap and metaspace** — that sharing is exactly why concurrency bugs exist: multiple threads can read/write the same object at the same time.

### Thread lifecycle
```
NEW -> RUNNABLE -> (BLOCKED / WAITING / TIMED_WAITING) -> TERMINATED
```
- **NEW**: `Thread` object created (`new Thread(...)`), but `start()` not yet called.
- **RUNNABLE**: eligible to run; the OS scheduler decides when it actually gets CPU time (Java doesn't distinguish "ready" from "running" as separate states).
- **BLOCKED**: waiting to acquire a lock held by another thread (e.g. stuck entering a `synchronized` block).
- **WAITING** / **TIMED_WAITING**: parked voluntarily — `Object.wait()`, `Thread.join()`, `LockSupport.park()` (untimed) or their timed variants / `Thread.sleep(ms)` (timed) — until notified, joined, or the timeout elapses.
- **TERMINATED**: `run()` returned or threw uncaught — the thread cannot be restarted (`start()` on a terminated thread throws `IllegalThreadStateException`).

### Runnable vs Callable
| | `Runnable` | `Callable<V>` |
|---|---|---|
| Method | `void run()` | `V call() throws Exception` |
| Return value | None | Returns `V` |
| Checked exceptions | Cannot throw checked exceptions | Can throw checked exceptions |
| Used with | `Thread`, `ExecutorService.execute()` | `ExecutorService.submit()` → returns a `Future<V>` |

`Runnable` predates generics/exceptions-in-lambdas convenience; `Callable` exists specifically so a background task can hand back a **result** (or a failure) instead of just running and disappearing.

### Future
The handle you get back from `executorService.submit(callable)` — represents a result that **may not exist yet**. `future.get()` blocks the calling thread until the task completes, then returns the value (or rethrows the task's exception wrapped in `ExecutionException`). `future.isDone()`, `future.cancel(boolean mayInterruptIfRunning)` let you poll/cancel without blocking. Limitation `Future` has that `CompletableFuture` (Java 8+) fixes: you can't chain callbacks (`thenApply`, `thenCompose`) or combine multiple futures without blocking — plain `Future` only gives you a blocking `get()`.

### ExecutorService
The standard abstraction over "run this task on some thread, I don't want to manage `Thread` objects myself." Decouples **task submission** from **thread management** — you submit `Runnable`/`Callable` instances; the executor decides which thread pool thread runs it and when. Always call `shutdown()` (let running/queued tasks finish, reject new ones) or `shutdownNow()` (attempt to interrupt running tasks, drain the queue) when done — an executor with non-daemon threads left running will keep the JVM alive indefinitely.

### Thread Pool
A fixed (or bounded) set of reusable worker threads that pull tasks off a shared queue, instead of spawning a brand-new `Thread` per task. Common factory methods on `Executors` (know their tradeoffs, not just the names):
| Factory | Pool shape | Risk |
|---|---|---|
| `newFixedThreadPool(n)` | Exactly `n` threads, unbounded `LinkedBlockingQueue` | Queue can grow unbounded under sustained overload → `OutOfMemoryError`, not backpressure |
| `newCachedThreadPool()` | Unbounded threads, created on demand, idle ones reaped after 60s | Unbounded thread creation under load can exhaust resources / thrash the scheduler |
| `newSingleThreadExecutor()` | 1 thread | Serializes all tasks; one thread dying (rare, wrapped) and being replaced is transparent |
| `newScheduledThreadPool(n)` | Fixed pool + delayed/periodic scheduling | Same unbounded-queue-style risk as fixed |

In production code, prefer constructing `ThreadPoolExecutor` directly with an explicit **bounded** queue and a rejection policy (`CallerRunsPolicy`, etc.) — the `Executors` factories' unbounded queues/threads are a well-known footgun (this is literally why many static-analysis linters flag `Executors.newFixedThreadPool` in review).

### `volatile`
A field modifier that guarantees:
1. **Visibility** — a write by one thread is immediately visible to reads by other threads (without it, each thread may cache the value in a CPU register/core-local cache and never see another thread's update — the classic infinite-loop-that-should-have-stopped bug).
2. **Ordering** — establishes a happens-before relationship: writes before a `volatile` write, and the write itself, are visible in order to any thread that subsequently reads that `volatile` field.

`volatile` does **not** provide atomicity for compound operations (see the interview question below on `i++`) and does **not** provide mutual exclusion — it only fixes *visibility*, not *races on read-modify-write sequences*.

### `synchronized`
Provides both **mutual exclusion** (only one thread can hold a given monitor/lock at a time) and **visibility** (entering/exiting a synchronized block establishes the same happens-before guarantee as `volatile`, for everything touched inside it). Can be applied to:
- an instance method (locks on `this`),
- a static method (locks on the `Class` object),
- a block (`synchronized(someObject) { ... }`, locks on `someObject` — the most controllable form since you choose the lock granularity).

Cost: threads contending for the same lock **block** (not spin) — this is throughput-safe but can become a bottleneck under high contention, which is exactly why `ConcurrentHashMap` (see [../day3/02-java-hashmap-internals.md](../day3/02-java-hashmap-internals.md)) uses much finer-grained locking than "one lock for the whole structure."

### Deadlock
Two (or more) threads each hold a lock the other needs, and each is waiting for the other to release — neither ever proceeds. Classic recipe: Thread A locks `lock1` then tries to lock `lock2`; Thread B locks `lock2` then tries to lock `lock1`, at the same time.
```
Thread A: synchronized(lock1) { ... synchronized(lock2) { ... } }
Thread B: synchronized(lock2) { ... synchronized(lock1) { ... } }
```
**Fix**: always acquire multiple locks in a **consistent global order** across every thread (e.g. always `lock1` before `lock2`, everywhere in the codebase) — this breaks the "circular wait" condition, one of the four necessary conditions for deadlock (mutual exclusion, hold-and-wait, no preemption, circular wait). Alternatively, use `tryLock(timeout)` (from `java.util.concurrent.locks.Lock`) to back off and retry instead of blocking forever.

### Race condition
A bug where the correctness of the result depends on the unpredictable **timing/interleaving** of concurrent threads — the code happens to work when operations don't overlap a certain way, and produces wrong results when they do. The most common shape is a **read-modify-write** on shared state without synchronization (`count++` where `count` is a shared field: read, increment, write are three separate steps another thread can interleave with). Not every race condition is visible in testing — they can pass thousands of test runs and still fail in production under different timing/load, which is what makes them notoriously hard to reproduce and debug.

---

## Part 2 — Coding: Producer-Consumer with `BlockingQueue`

`BlockingQueue` already solves the classic producer-consumer coordination problem internally — `put()` blocks the producer when the queue is full, `take()` blocks the consumer when the queue is empty — so you get correct backpressure without hand-rolling `wait()`/`notify()`.

```java
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class ProducerConsumerDemo {

    public static void main(String[] args) throws InterruptedException {
        BlockingQueue<Integer> queue = new ArrayBlockingQueue<>(5); // bounded capacity=5
        Object poisonPill = -1; // sentinel to signal "no more work"

        Runnable producer = () -> {
            try {
                for (int i = 1; i <= 10; i++) {
                    queue.put(i);                         // blocks if queue is full
                    System.out.println("Produced: " + i);
                }
                queue.put(-1);                            // poison pill: tells consumer to stop
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();        // restore interrupt status, don't swallow it
            }
        };

        Runnable consumer = () -> {
            try {
                while (true) {
                    int item = queue.take();               // blocks if queue is empty
                    if (item == -1) break;                 // poison pill received, stop
                    System.out.println("Consumed: " + item);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };

        Thread producerThread = new Thread(producer);
        Thread consumerThread = new Thread(consumer);
        producerThread.start();
        consumerThread.start();

        producerThread.join();
        consumerThread.join();
    }
}
```

**Why `BlockingQueue` instead of hand-rolled `wait()`/`notify()`**: `wait()`/`notify()` require you to correctly manage a `synchronized` block, a condition check in a `while` loop (never `if`, to guard against spurious wakeups), and manual `notify()`/`notifyAll()` calls at every state change — easy to get subtly wrong (missed signals, notifying the wrong waiters). `ArrayBlockingQueue`/`LinkedBlockingQueue` implement all of that correctly internally using `Lock`/`Condition`, so `put()`/`take()` just work.

**Why a poison pill instead of an external `stop` flag**: a plain boolean flag checked by the consumer's loop condition isn't reliably visible without `volatile`, and even with `volatile` there's a race between "producer sets the flag" and "consumer is blocked inside `take()` and never re-checks the flag until the next item arrives." Sending the stop signal *through the same queue* as a real message guarantees the consumer sees it in order, with no separate synchronization needed.

**Complexity/behavior**: bounded queue capacity provides automatic **backpressure** — if the consumer is slower than the producer, `put()` blocks the producer instead of the queue growing unbounded (contrast with an unbounded queue, which trades memory blowup for never blocking the producer — this is the same tradeoff as the `Executors.newFixedThreadPool` unbounded-queue risk mentioned in Part 1).

---

## Part 3 — Interview Questions (today's round)

**Q: Why do we use thread pools instead of creating a new `Thread` for every task?**
**A:** Creating an OS thread is expensive (stack allocation, kernel scheduling registration) and unbounded thread creation under load can exhaust memory or thrash the CPU scheduler with excessive context switching. A thread pool creates a **bounded, reusable** set of worker threads once, then hands tasks to them via a queue — amortizing thread-creation cost across many tasks and giving you a hard cap on concurrency, which is what actually keeps a service stable under load (unbounded concurrency is a classic way to turn a slow dependency into a full outage).

**Q: Why is `volatile` not enough for an increment operation like `count++`?**
**A:** `volatile` only guarantees **visibility** of the latest written value across threads — it does not make a **compound** operation atomic. `count++` is actually three separate steps: read `count`, compute `count + 1`, write it back. Two threads can both read the same value before either writes back, so one increment is lost (`count` ends up 1 higher than expected instead of 2). Fixing this needs either `synchronized` around the whole read-modify-write, or a purpose-built atomic type like `AtomicInteger` (`incrementAndGet()`), which uses a CAS (compare-and-swap) loop internally to make the whole read-modify-write a single indivisible hardware-level operation.

**Q: What's the difference between `wait()`/`notify()` and `Lock`/`Condition`?**
**A:** `wait()`/`notify()` are intrinsic-lock (monitor) primitives tied to `synchronized` — every object has exactly one implicit condition, `wait()` can only be called while holding that object's monitor, and there's no way to interrupt a `wait()` selectively or use `tryLock`-style timeouts cleanly. `java.util.concurrent.locks.Lock` (e.g. `ReentrantLock`) with `Condition` decouples locking from the language keyword, supports multiple independent conditions per lock (`newCondition()` called more than once), fair-locking policies, `tryLock(timeout)`, and interruptible lock acquisition — more flexible, at the cost of needing an explicit `try/finally { lock.unlock() }` since there's no compiler-enforced block scoping like `synchronized` gives you for free.

**Q: `ExecutorService.execute()` vs `submit()` — what's the practical difference?**
**A:** `execute(Runnable)` returns nothing — if the task throws, the exception propagates to the thread's uncaught exception handler (often silently logged or lost depending on setup). `submit(...)` (accepting `Runnable` or `Callable`) returns a `Future` — a thrown exception is instead **captured** inside the `Future` and only surfaces when you call `future.get()` (wrapped in `ExecutionException`). This is a common production gotcha: fire-and-forget code using `execute()` for a task that can throw will silently swallow failures unless you're deliberately consuming the `Future` from `submit()`.
