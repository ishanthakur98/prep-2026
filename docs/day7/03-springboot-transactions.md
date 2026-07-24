# Spring Boot — Transactions

Builds on [Day 3's bean lifecycle doc](../day3/03-springboot-bean-lifecycle.md#beanfactory), which already named — but didn't unpack — that `ApplicationContext` auto-registers `BeanPostProcessor`s that create proxies for `@Transactional`. Today unpacks exactly that mechanism, plus the ACID/propagation/isolation theory behind it. Also builds on [Day 5's persistence-context doc](../day5/03-springboot-jpa-persistence-context.md#persistence-context--the-first-level-cache) — the persistence context's lifetime and a `@Transactional` method's lifetime are the same thing, which is why dirty-checked auto-`UPDATE`s only happen inside one.

## Part 1 — Concepts (read this first)

### ACID
The four guarantees a transaction gives you — worth being able to name and explain each on the spot:
- **Atomicity** — a transaction's operations either **all** succeed or **all** roll back; there's no partial-completion state visible to anything outside it. (`@Transactional`'s entire reason to exist.)
- **Consistency** — a transaction moves the database from one valid state to another, never violating declared constraints (foreign keys, unique constraints, check constraints) along the way. This is largely the *database's* job (enforcing constraints), with the application responsible for not writing logic that's internally inconsistent even when each individual constraint passes.
- **Isolation** — concurrent transactions don't see each other's uncommitted intermediate state — governed by the **isolation level** (below), which is a *tunable spectrum*, not a single fixed guarantee.
- **Durability** — once a transaction commits, its effects survive a crash immediately after (written to durable storage, typically via a write-ahead log flushed before the commit is acknowledged).

### `@Transactional` — what it actually wraps
```java
@Service
public class EmployeeService {

    @Transactional
    public void transferManager(Long employeeId, Long newManagerId) {
        Employee e = employeeRepository.findById(employeeId).orElseThrow();
        e.setManagerId(newManagerId);
        auditLogRepository.save(new AuditEntry("manager changed", employeeId));
        // if either write throws, BOTH are rolled back -- this method is one atomic unit
    }
}
```
Everything inside the method runs as **one database transaction**: a connection is checked out and begun before the method body runs, and committed (or rolled back, see rollback rules below) when the method returns (or throws) — exactly matching the persistence context's lifetime from [Day 5's doc](../day5/03-springboot-jpa-persistence-context.md#persistence-context--the-first-level-cache), which is why dirty-checking's automatic `UPDATE`-on-setter behavior only works *inside* a `@Transactional` boundary.

### Propagation — what happens when a `@Transactional` method calls another `@Transactional` method
Propagation decides whether the *inner* call joins the *outer* call's existing transaction, or does something else. The three from today's list, plus the two most commonly asked about alongside them:

| Propagation | Behavior |
|---|---|
| **`REQUIRED`** (default) | Join the existing transaction if one is active; otherwise start a new one. The overwhelmingly common default — most methods should just participate in whatever transaction is already in progress. |
| **`REQUIRES_NEW`** | **Suspend** any existing transaction and start a genuinely independent new one, with its own commit/rollback outcome. Used when a sub-operation's outcome must be committed (or rolled back) **independently** of the caller — e.g. an audit-log write that should persist even if the calling business operation later fails and rolls back. |
| **`SUPPORTS`** | Join the existing transaction if one is active; if none exists, run **non-transactionally** rather than starting a new one. Used for read-only helper methods that are fine either way, and shouldn't force a transaction to exist just to be called. |
| `MANDATORY` | Must join an existing transaction; **throws** if none is active. Used to enforce "this method must never be called outside a transaction" as a hard contract. |
| `NEVER` | Must run non-transactionally; **throws** if a transaction is active. Rare — used to guard against a method that specifically must not be wrapped (e.g. it does something incompatible with an open transaction/connection). |

**Concrete `REQUIRES_NEW` example** — the audit-log case named above:
```java
@Transactional
public void placeOrder(Order order) {
    orderRepository.save(order);
    auditService.logOrderAttempt(order); // REQUIRES_NEW -- commits independently
    paymentService.charge(order);         // if this throws, the order rolls back...
}                                          // ...but the audit log entry, already committed, survives

@Service
public class AuditService {
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logOrderAttempt(Order order) { auditRepository.save(new AuditEntry(order)); }
}
```

### Isolation levels
Govern what a transaction can see of *other, concurrently running* transactions' uncommitted or in-progress changes — a spectrum trading correctness guarantees against concurrency/throughput:

| Level | Prevents | Allows | Cost |
|---|---|---|---|
| `READ_UNCOMMITTED` | nothing | dirty reads (seeing another transaction's uncommitted, possibly-about-to-be-rolled-back changes) | cheapest, rarely used |
| `READ_COMMITTED` | dirty reads | non-repeatable reads (re-reading the same row twice in one transaction can see different committed values if another transaction committed in between) | Postgres's **default** |
| `REPEATABLE_READ` | dirty reads, non-repeatable reads | phantom reads (a repeated *range* query can see new rows another transaction committed in between) | MySQL/InnoDB's **default** |
| `SERIALIZABLE` | all of the above | nothing — transactions behave as if run one at a time | most expensive, most contended |

**Practical default**: the DB's own default (`READ_COMMITTED` for Postgres) is correct for the overwhelming majority of application code; only reach for a stricter level (usually `SERIALIZABLE`, or an explicit row lock) on the *specific* operation with a genuine race condition to prevent (e.g. two concurrent requests both reading then writing a bank balance) — applying a stricter level globally is a real, easy-to-make throughput mistake, since stricter isolation directly means more lock contention and more transaction retries under load.

### Rollback rules
```java
@Transactional
public void riskyOperation() {
    // ...
    throw new IllegalStateException("bad state"); // unchecked -- rolls back automatically
}

@Transactional(rollbackFor = InsufficientFundsException.class) // checked -- must opt in explicitly
public void withdraw(...) throws InsufficientFundsException {
    if (balance < amount) throw new InsufficientFundsException();
}
```
**The default that trips people up**: Spring's `@Transactional` only rolls back automatically on an **unchecked** exception (`RuntimeException` or `Error`) — a **checked** exception, by default, does **not** trigger a rollback; the transaction commits anyway, with the checked exception simply propagating up as if nothing DB-related happened. This is a deliberate default rooted in the assumption that a checked exception represents an *expected*, recoverable business outcome, not necessarily a reason to discard the transaction's work so far — but it's routinely the wrong assumption in practice, and the reason `rollbackFor = SomeCheckedException.class` (or the inverse, `noRollbackFor`) exists as an explicit override. This default is exactly why "unchecked exceptions for anything that should roll back a transaction" has become the practical convention in most Spring codebases, sidestepping the need to remember `rollbackFor` on every method.

### The proxy mechanism — how `@Transactional` is actually implemented
`@Transactional` does **nothing** on its own as a plain annotation — it's read by a `BeanPostProcessor` at bean-creation time (named in [Day 3's doc](../day3/03-springboot-bean-lifecycle.md#beanfactory)), which wraps the actual bean in a **proxy**: either a **JDK dynamic proxy** (if the bean implements an interface — the proxy implements that same interface) or a **CGLIB proxy** (a runtime-generated subclass, used when there's no interface). Every call into the bean from **outside** actually hits the proxy first, which does, conceptually:
```
proxy.someTransactionalMethod():
    begin transaction (or join existing one, per propagation)
    try:
        result = realBean.someTransactionalMethod()   // the actual call, delegated
        commit transaction
        return result
    catch (RuntimeException e):
        rollback transaction
        rethrow e
```
The bean reference every other Spring-managed bean is injected with (via `@Autowired`, constructor injection, etc.) is **the proxy**, not the raw object — this single fact is the root cause of both interview traps below.

---

## Part 2 — Interview Questions (today's round)

**Q: Why doesn't `@Transactional` work on private methods?**
**A:** Because the entire mechanism is proxy-based — the proxy needs to be able to intercept a call to the annotated method *from outside the bean*, which requires either implementing the same interface method (JDK dynamic proxy) or overriding the method in a generated subclass (CGLIB proxy). A `private` method is neither implementable via an interface nor overridable in a subclass — it's fundamentally invisible to the proxy layer, so there's no interception point for the proxy to wrap. The annotation is silently ignored, no error thrown, which makes this a genuinely dangerous mistake to make (no rollback where you assumed one existed).

**Q: Why doesn't self-invocation trigger a transaction (or trigger the *new* propagation behavior on a `REQUIRES_NEW` call)?**
**A:** Self-invocation means calling another `@Transactional` method **on `this`** from inside the same bean (`this.otherMethod()`, or just `otherMethod()` implicitly) — this call goes directly to the real object's method, entirely bypassing the proxy, because `this` inside a bean refers to the raw object, never the proxy wrapping it. Since the proxy is the only thing that actually begins/joins/commits a transaction, a self-invoked call gets **none** of that behavior — no new transaction if none exists, and critically, a `REQUIRES_NEW` method called this way silently just continues in whatever transaction (or lack of one) already exists, rather than actually suspending and starting fresh. **Fix**: inject the bean into itself (via `@Lazy` to avoid a circular-construction error) and call through that injected reference instead of `this`, or extract the inner method into a separate bean and call it through *that* bean's proxy — either way, the call must go through a proxy reference to get transactional behavior at all.

**Q: Why does Spring use proxies for `@Transactional` instead of some other mechanism?**
**A:** It's the only way to inject transaction begin/commit/rollback logic **around** a method call without the method's own code having to manually call `TransactionManager` APIs itself — the proxy transparently wraps every external call with "begin, delegate, commit-or-rollback," keeping the business logic in `transferManager()` (or wherever) completely free of transaction-management boilerplate, which is the whole point of declarative (`@Transactional`) versus programmatic transaction management. The tradeoff, and the reason both interview traps above exist, is that this approach is fundamentally **external interception** — it only works for calls that actually go through the proxy, which is precisely `private` methods and self-invocation's blind spot. AspectJ compile-time/load-time weaving is the alternative Spring supports that avoids this specific limitation (it rewrites bytecode directly rather than wrapping with a proxy, so self-invocation *does* work) — but it's meaningfully more complex to set up and much less commonly used than the default proxy approach in practice.
