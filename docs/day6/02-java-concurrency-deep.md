# Java — Concurrency, Deeper

Builds directly on [../day4/02-java-concurrency.md](../day4/02-java-concurrency.md), which covered threads, `ExecutorService`, `volatile`, `synchronized`, deadlock, and race conditions. Today goes one level deeper on the actual **locking toolkit** (`ReentrantLock`, `ReadWriteLock`), **thread communication** (`wait()`/`notify()`, the mechanism `BlockingQueue` hides from you), the **concurrent collections** built on top of these primitives, and **CAS-based atomics** as the lock-free alternative.

## Part 1 — Concepts (read this first)

### `synchronized` recap, and its limits
[Day 4](../day4/02-java-concurrency.md#synchronized) covered `synchronized` for mutual exclusion + visibility. Its limits, which motivate everything else in this doc:
- No way to **try** acquiring a lock without blocking forever (no `tryLock`).
- No way to **interrupt** a thread that's blocked waiting to enter a `synchronized` block.
- No **fairness** control (which waiting thread gets the lock next is unspecified).
- Exactly **one** implicit condition per object (`wait()`/`notify()` on that one monitor) — no way to have separate wait-queues for separate conditions on the same lock.
- No **timeout** on waiting to acquire.

### `ReentrantLock`
`java.util.concurrent.locks.Lock` implementation providing everything `synchronized` doesn't:
```java
private final ReentrantLock lock = new ReentrantLock();

public void update() {
    lock.lock();
    try {
        // critical section
    } finally {
        lock.unlock(); // MUST be in finally -- unlike synchronized, nothing releases it for you
    }
}
```
- **Reentrant** (like `synchronized`): a thread already holding the lock can acquire it again without deadlocking itself (e.g. a synchronized method calling another synchronized method on the same object) — an internal hold-count tracks how many times `unlock()` must be called to fully release.
- `tryLock()` / `tryLock(timeout, unit)` — attempt to acquire without blocking forever; back off and do something else (retry, fail fast) if unavailable.
- `lockInterruptibly()` — a thread blocked waiting for the lock can be interrupted out of the wait, unlike blocking to enter a `synchronized` block.
- `new ReentrantLock(true)` — **fair mode**, grants the lock to the longest-waiting thread first (FIFO), at a real throughput cost (the default, unfair mode allows barging, which is faster in the common case but can starve a waiting thread under sustained contention).
- **Cost of the flexibility**: you must remember `unlock()` in a `finally` block yourself — `synchronized` releases automatically even if an exception is thrown or the method returns early; `Lock` gives you none of that for free.

### `ReadWriteLock`
Splits one lock into two: a **read lock** (shared — any number of readers can hold it simultaneously) and a **write lock** (exclusive — one writer at a time, and no readers while a writer holds it).
```java
private final ReadWriteLock rwLock = new ReentrantReadWriteLock();
private final Lock readLock = rwLock.readLock();
private final Lock writeLock = rwLock.writeLock();

public String read(String key) {
    readLock.lock();
    try { return cache.get(key); } finally { readLock.unlock(); }
}

public void write(String key, String value) {
    writeLock.lock();
    try { cache.put(key, value); } finally { writeLock.unlock(); }
}
```
**When it's worth it**: a plain `ReentrantLock`/`synchronized` serializes *everyone*, readers included, even though concurrent reads of unchanging data are perfectly safe. `ReadWriteLock` is a real win specifically for **read-heavy, write-light** workloads (e.g. a rarely-updated config cache read on every request) — for write-heavy or roughly-balanced workloads, the extra bookkeeping overhead of managing two lock states can make it *slower* than a single lock, so it's a deliberate choice based on measured access pattern, not a default upgrade.

### Thread communication — `wait()` / `notify()` / `notifyAll()`
Methods on `Object` (not `Thread`) — every object has one implicit monitor and one implicit wait-set. Must be called from **inside a `synchronized` block on that object**, or they throw `IllegalMonitorStateException`.
- **`wait()`** — releases the monitor and suspends the calling thread until another thread calls `notify()`/`notifyAll()` on the same object (or the wait times out, for the timed overload). Crucially, `wait()` is the *only* standard way a thread can voluntarily give up a lock it holds while waiting for a condition, without busy-spinning a CPU core checking a flag in a loop.
- **`notify()`** — wakes **one** arbitrary thread from the object's wait-set; which one is unspecified.
- **`notifyAll()`** — wakes **every** thread in the wait-set; each re-acquires the monitor one at a time and re-checks its condition. Almost always the safer default over `notify()` — `notify()` can wake the "wrong" waiter (one whose condition still isn't actually satisfiable), leaving a thread that *could* have proceeded stuck forever (a form of lost/missed signal).

**Must be a `while` loop, never `if`** — the canonical bug:
```java
synchronized (lock) {
    while (!conditionIsMet()) {   // NOT if -- must re-check after waking
        lock.wait();
    }
    // proceed
}
```
Why: (1) **spurious wakeups** — the JVM spec explicitly permits a thread to wake from `wait()` without any `notify()` having happened at all; (2) even after a real `notify()`, by the time the woken thread re-acquires the monitor, *another* thread may have already run and changed the condition back to false. An `if` re-checks nothing after waking and proceeds on a possibly-stale assumption; a `while` re-verifies the actual condition every time control returns, which is the only correct way to use these primitives.

**Why `BlockingQueue` exists**: it implements exactly this `while`-loop-plus-`wait()`/`notify()` pattern correctly, internally, so `put()`/`take()` just work — see [`ProducerConsumerDemo.java`](../../src/main/java/org/prep/day6/ProducerConsumerDemo.java) in Part 2, and [Day 4's producer-consumer example](../day4/02-java-concurrency.md#part-2--coding-producer-consumer-with-blockingqueue) for the full comparison against hand-rolling this yourself.

### Concurrent Collections
| Collection | What it replaces | How it stays safe |
|---|---|---|
| `ConcurrentHashMap` | `Hashtable` / synchronized `HashMap` | Fine-grained locking (CAS on empty bins, `synchronized` per non-empty bin as of Java 8+) — see [day3's HashMap internals doc](../day3/02-java-hashmap-internals.md) for the full bucket-locking mechanism. No `null` keys/values (ambiguous under concurrency — can't distinguish "absent" from "mapped to null" when another thread might be concurrently mutating). |
| `CopyOnWriteArrayList` | Synchronized `ArrayList` for read-heavy lists | Every **write** (`add`/`remove`/`set`) copies the entire backing array; reads never lock and never see a torn/partial state, because they're always looking at a snapshot array that's never mutated in place. |
| `BlockingQueue` (`ArrayBlockingQueue`, `LinkedBlockingQueue`) | Hand-rolled `wait()`/`notify()` queues | `put()` blocks when full, `take()` blocks when empty — correct backpressure built in (see Part 2). |

**`CopyOnWriteArrayList`'s tradeoff, precisely**: reads are essentially free (no locking, no contention, ever) and iterators never throw `ConcurrentModificationException` (they iterate a fixed snapshot). The cost is that every write is `O(n)` (copy the whole array) — this is only a good trade when reads vastly outnumber writes (e.g. a list of event listeners, registered rarely, fired constantly). Using it for a write-heavy list is a real performance bug, not a style choice.

### Atomic Classes and CAS
**`AtomicInteger`/`AtomicLong`/`AtomicBoolean`/`AtomicReference`** — provide atomic, **lock-free** read-modify-write operations (`incrementAndGet()`, `compareAndSet()`, `getAndAdd()`) for a single variable, without `synchronized`.

**CAS (Compare-And-Swap)** is the hardware primitive underneath: an atomic instruction taking `(memoryLocation, expectedValue, newValue)` that sets `memoryLocation = newValue` **only if** its current value still equals `expectedValue`, and reports success/failure — all as one indivisible CPU-level operation. `incrementAndGet()`'s actual loop:
```java
int current;
int next;
do {
    current = get();
    next = current + 1;
} while (!compareAndSet(current, next)); // retry if another thread changed it first
```
If another thread mutated the value between the read and the `compareAndSet`, the CAS fails, and the loop just **retries** with the fresh value — no thread ever blocks waiting for a lock.

**Why this beats `synchronized` under contention**: `synchronized` **blocks** losing threads (they park, get descheduled, and need an OS-level wakeup later — real context-switch cost). CAS-based retry keeps threads **spinning and retrying**, which is cheaper *when contention is low-to-moderate and the critical section is tiny* (like a single increment) — no thread ever sleeps or needs rescheduling. Under **very high** contention (many threads hammering the same atomic simultaneously), CAS retry storms can actually burn more CPU than a lock would have — this is a real, known tradeoff, not "CAS always wins."

---

## Part 2 — Coding

### Producer-Consumer using `BlockingQueue`
Full runnable version: [`ProducerConsumerDemo.java`](../../src/main/java/org/prep/day6/ProducerConsumerDemo.java). Same structure as [Day 4's version](../day4/02-java-concurrency.md#part-2--coding-producer-consumer-with-blockingqueue) — `put()`/`take()` handle all the blocking/backpressure that Part 1's `wait()`/`while` pattern would otherwise require hand-writing correctly.
```java
BlockingQueue<Integer> queue = new ArrayBlockingQueue<>(5);
int poisonPill = -1;

Runnable producer = () -> {
    for (int i = 1; i <= 10; i++) {
        queue.put(i);           // blocks if full -- this IS the backpressure
    }
    queue.put(poisonPill);
};

Runnable consumer = () -> {
    while (true) {
        int item = queue.take(); // blocks if empty
        if (item == poisonPill) break;
        // process item
    }
};
```

### Thread-safe counter using `AtomicInteger`
Full runnable version, including a side-by-side unsafe counter to make the race visible: [`AtomicCounterDemo.java`](../../src/main/java/org/prep/day6/AtomicCounterDemo.java).
```java
private static int unsafeCounter = 0;
private static final AtomicInteger safeCounter = new AtomicInteger(0);

// from 10 threads x 10,000 increments each (100,000 total):
unsafeCounter++;                // read-modify-write race -- final count is LESS than 100,000
safeCounter.incrementAndGet();  // CAS loop -- final count is ALWAYS exactly 100,000
```
Running it (output varies run to run for the unsafe counter, that's the point — it's a genuine race, not a deterministic bug):
```
Expected:     100000
unsafeCounter: 42152 (usually less than expected)
safeCounter:   100000 (always equals expected)
```

---

## Part 3 — Interview Questions (today's round)

**Q: `synchronized` vs `ReentrantLock` — when would you actually reach for `ReentrantLock`?**
**A:** `synchronized` is simpler and safer by default (automatic release, even on exception) and is fine for the common case of one straightforward critical section. Reach for `ReentrantLock` specifically when you need something `synchronized` structurally cannot do: `tryLock()` to fail fast instead of blocking forever, `lockInterruptibly()` to let a blocked thread be cancelled, a fairness policy, a timeout on acquisition, or multiple independent `Condition`s on one lock (e.g. a bounded buffer needing separate "not full" and "not empty" wait-queues instead of one shared monitor). If none of those are needed, `synchronized` is the better default — less to get wrong.

**Q: Why must a `wait()` call be inside a `while` loop checking the condition, not an `if`?**
**A:** Two independent reasons converge: the JVM permits **spurious wakeups** (a thread can return from `wait()` with no `notify()` having happened at all), and even a legitimate `notify()`/`notifyAll()` only guarantees the condition was true at signal time — by the time the woken thread actually re-acquires the monitor and resumes, another thread may have already run and invalidated it again. An `if` proceeds on whatever it last checked before waiting; a `while` re-verifies the real condition every time control returns, which is the only version that's actually correct.

**Q: Why is `ConcurrentHashMap` faster than a synchronized `HashMap` or `Hashtable` under concurrent access?**
**A:** `Hashtable`/a synchronized wrapper use **one lock for the entire table** — every operation, on any bucket, serializes behind that single lock, so throughput doesn't scale with more threads at all. `ConcurrentHashMap` locks at a much finer grain (per-bin, only when a bin already has a colliding entry; CAS for the common case of inserting into an empty bin) — two threads writing to *different* bins proceed fully in parallel with zero contention between them. Reads are largely lock-free entirely. The cost of that fine-grained scheme is why it disallows `null` keys/values: with per-bin locking, `map.get(key) == null` is ambiguous between "no mapping" and "mapped to null" in a way a single-lock structure could resolve unambiguously (though `HashMap` also just disallows it for other historical reasons); `ConcurrentHashMap` can't tolerate that ambiguity given its concurrent-access contract.

**Q: Explain CAS (Compare-And-Swap) and how `AtomicInteger` uses it.**
**A:** CAS is a single hardware-level atomic instruction: given a memory location, an expected current value, and a new value, it sets the location to the new value **only if** it still holds the expected value, reporting success or failure atomically. `AtomicInteger.incrementAndGet()` loops: read the current value, compute `current + 1`, call `compareAndSet(current, current + 1)`; if another thread changed the value in between (CAS fails), it just retries with the fresh value instead of ever blocking. This makes it **lock-free** — no thread ever parks waiting for a lock — which is cheaper than `synchronized` under light-to-moderate contention on a small critical section like a single increment, though under very heavy contention the retry storms can cost more than a lock would have.

**Q: Difference between `AtomicInteger` and `synchronized` for a counter — is one strictly better?**
**A:** Not strictly — different tradeoffs. `AtomicInteger` is lock-free (CAS retry, no blocking/context-switching, generally faster under low-to-moderate contention for a single variable's simple operations) but only covers **one variable's** atomicity — it can't atomically coordinate updates across multiple related fields. `synchronized` can protect an arbitrary multi-statement critical section spanning several fields/objects at once, at the cost of blocking losing threads (real context-switch overhead) and, under very high contention, potentially worse throughput than a well-behaved CAS retry loop for something as small as an increment. Rule of thumb: single counter/flag/reference → atomic class; multi-field invariant that must change together → a lock.

**Q: What is thread starvation, and how does it differ from deadlock?**
**A:** **Starvation** is a thread that's technically able to eventually proceed but keeps getting passed over indefinitely — e.g. an unfair `ReentrantLock` repeatedly granting the lock to newer arrivals (barging) while one waiting thread never gets picked, or a low-priority thread that never gets CPU time because higher-priority threads keep preempting it. **Deadlock** is threads that can **never** proceed, structurally — each holds a resource the other needs, in a circular wait, so there's no scheduling decision that could ever unblock them. Starvation is a fairness/scheduling problem (fixable with a fair lock policy or priority adjustments); deadlock is a resource-ordering problem (fixable only by breaking the circular-wait condition, e.g. consistent lock ordering — see [Day 4's deadlock section](../day4/02-java-concurrency.md#deadlock)).
