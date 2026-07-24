# Low-Level Design — Factory, Builder, Strategy

Strategy already exists in this repo from Day 4 ([`NotificationService`/`NotificationSender`](../../src/main/java/org/prep/designPattern/strategy/NotificationService.java)) — today extends that same pattern with a second, independent example (a payment system) specifically to build fluency recognizing the *shape* of the problem Strategy solves, not just recall one memorized example. Factory and Builder are new today, concept-only (no code committed, same convention as other concept-only topics — type them out yourself to internalize the mechanics).

## Part 1 — Concepts (read this first)

### Factory Pattern
**Problem it solves**: calling code needs an object of some interface/base type, but *which concrete implementation* to construct depends on runtime information (a type string, a config value, an enum) — without a Factory, that decision (a big `if`/`switch` picking a concrete class and `new`-ing it) ends up duplicated at every call site that needs one of these objects.
```java
public interface Shape { double area(); }
public class Circle implements Shape { /* ... */ }
public class Square implements Shape { /* ... */ }

public class ShapeFactory {
    public static Shape create(String type, double size) {
        return switch (type) {
            case "circle" -> new Circle(size);
            case "square" -> new Square(size);
            default -> throw new IllegalArgumentException("Unknown shape: " + type);
        };
    }
}
// caller:
Shape s = ShapeFactory.create(userInput, 5.0); // caller never names a concrete class
```
**What it actually buys you**: the `new Circle(...)` / `new Square(...)` decision lives in exactly **one place**. Every caller depends only on the `Shape` interface and the factory method — adding a new shape type means touching the factory, not hunting down every call site that ever constructed a shape. This is the same "centralize a decision instead of duplicating it everywhere" idea behind [Day 6's `@ControllerAdvice`](../day6/07-mini-project-plan.md#global-exception-handling--controlleradvice) (centralize error-mapping) and [today's proxy-based `@Transactional`](03-springboot-transactions.md#the-proxy-mechanism--how-transactional-is-actually-implemented) (centralize transaction boilerplate) — a recurring theme across today's material, not a coincidence.

**Factory Method vs. Abstract Factory** (worth knowing the name distinction, briefly): what's shown above is a **Simple Factory** (not officially one of the GoF 23, but the version everyone means colloquially by "the Factory pattern"). **Factory Method** formalizes it as a method overridden by *subclasses*, each subclass deciding what concrete product to create. **Abstract Factory** goes one level further — a factory that creates a whole *family* of related objects (e.g. `GuiFactory` producing a matched `Button` + `Checkbox` for either a Windows or Mac look), guaranteeing the family's pieces are always used together consistently. Simple Factory is the one that comes up constantly in interviews; the other two are worth being able to name and distinguish if asked directly.

### Builder Pattern
**Problem it solves**: constructing an object with **many optional fields** via constructors alone forces either a "telescoping constructor" (a constructor overload for every combination of optional params — combinatorially unmanageable) or one giant constructor call where positional args are unreadable and error-prone (`new Employee("Alice", null, 50000, null, true, false, null, ...)` — what does the 4th `null` mean?).
```java
public class Employee {
    private final String name;      // required
    private final String title;     // required
    private final Long deptId;      // optional
    private final BigDecimal bonus; // optional
    private final boolean remote;   // optional, defaults to false

    private Employee(Builder b) {
        this.name = b.name; this.title = b.title;
        this.deptId = b.deptId; this.bonus = b.bonus; this.remote = b.remote;
    }

    public static class Builder {
        private final String name, title; // required, passed to Builder's own constructor
        private Long deptId;
        private BigDecimal bonus;
        private boolean remote = false;

        public Builder(String name, String title) { this.name = name; this.title = title; }
        public Builder deptId(Long deptId) { this.deptId = deptId; return this; }
        public Builder bonus(BigDecimal bonus) { this.bonus = bonus; return this; }
        public Builder remote(boolean remote) { this.remote = remote; return this; }
        public Employee build() { return new Employee(this); }
    }
}
// caller:
Employee e = new Employee.Builder("Alice", "Engineer")
        .deptId(3L)
        .remote(true)
        .build(); // bonus left unset -- no null-juggling, no 7-argument constructor call
```
**What it actually buys you**: every optional field is set by **name** (`.remote(true)`, self-documenting at the call site) instead of by position, unset fields simply aren't called (no `null` placeholders needed to "skip" a middle constructor argument), and the object is still **immutable** once built (`Employee`'s fields are all `final`, only ever set once, inside `build()`) — the Builder itself is the only mutable, throwaway intermediate object. Java records with compact constructors and a handful of required fields often don't need this at all — Builder earns its complexity specifically once the optional-field count gets large enough that telescoping constructors or a long positional call become genuinely hard to read or misuse safely. Lombok's `@Builder` annotation generates exactly the boilerplate shown above — worth naming as the practical, real-world way this pattern is usually reached for rather than hand-written.

### Strategy Pattern
**Problem it solves**: a family of interchangeable **algorithms/behaviors** exist for accomplishing the same task (different ways to notify a user, different ways to pay), and the calling code needs to plug in *whichever one applies right now* without hardcoding that decision as a branch. Full code: [`PaymentStrategy.java`](../../src/main/java/org/prep/designPattern/strategy/PaymentStrategy.java), [`CreditCardPayment.java`](../../src/main/java/org/prep/designPattern/strategy/CreditCardPayment.java), [`UpiPayment.java`](../../src/main/java/org/prep/designPattern/strategy/UpiPayment.java), [`NetBankingPayment.java`](../../src/main/java/org/prep/designPattern/strategy/NetBankingPayment.java), [`PaymentService.java`](../../src/main/java/org/prep/designPattern/strategy/PaymentService.java) — the exact same shape as Day 4's `NotificationSender`/`EmailNotification`/`SmsNotification`/`NotificationService`, with `pay(amount)` standing in for `send(to, message)`:
```java
public interface PaymentStrategy {
    void pay(double amount);
}
public class CreditCardPayment implements PaymentStrategy { /* pay() charges a card */ }
public class UpiPayment implements PaymentStrategy { /* pay() debits via UPI */ }
public class NetBankingPayment implements PaymentStrategy { /* pay() transfers via net banking */ }

public class PaymentService {
    private PaymentStrategy paymentStrategy; // held as a field, injected -- never hardcoded

    public PaymentService(PaymentStrategy paymentStrategy) { this.paymentStrategy = paymentStrategy; }
    public void setPaymentStrategy(PaymentStrategy paymentStrategy) { this.paymentStrategy = paymentStrategy; } // swappable at runtime
    public void checkout(double amount) { paymentStrategy.pay(amount); }
}
```
```java
PaymentService checkout = new PaymentService(new CreditCardPayment("4111111111111234"));
checkout.checkout(250.00);

checkout.setPaymentStrategy(new UpiPayment("ishan@upi"));  // swap the algorithm, same PaymentService instance
checkout.checkout(499.99);
```
**The mechanism, stated precisely**: `PaymentService` depends only on the `PaymentStrategy` **interface**, never on any concrete implementation — this is composition over inheritance, and it's plain dependency injection applied to *behavior* rather than to a collaborator object. The concrete strategy is decided by *whoever constructs (or later calls `setPaymentStrategy` on) the `PaymentService`* — e.g. driven by which payment method the user picked in a UI — not by `PaymentService` itself branching on a type code.

---

## Part 2 — Why is Strategy better than a long if/else chain?

The alternative that Strategy replaces:
```java
public void checkout(String method, double amount) {
    if (method.equals("credit_card")) {
        // charge card logic, inline
    } else if (method.equals("upi")) {
        // UPI logic, inline
    } else if (method.equals("net_banking")) {
        // net banking logic, inline
    } else {
        throw new IllegalArgumentException("Unknown payment method: " + method);
    }
}
```
Four concrete problems with this, each directly fixed by Strategy:

1. **Open/Closed Principle violation** — adding a new payment method means **editing** `checkout()` itself (a new `else if` branch), touching code that's already written, tested, and working, just to add something new alongside it. With Strategy, adding `NetBankingPayment` means writing one **new** class implementing `PaymentStrategy` — `PaymentService` itself is never touched, never re-tested for this change. This is the literal definition of "open for extension, closed for modification."
2. **Testability** — testing the if/else version means exercising `checkout()` with every possible `method` string to hit every branch, and any new payment logic added inline makes the surrounding test setup bigger. Each `PaymentStrategy` implementation is a small, independently unit-testable class with a single `pay()` method and no branching of its own to cover.
3. **Single Responsibility** — the if/else version makes `checkout()` responsible for *both* the checkout orchestration *and* the full implementation detail of every payment method's logic, all tangled in one method body. Strategy separates "orchestrate a checkout" (`PaymentService.checkout()`) from "how a credit card payment specifically works" (`CreditCardPayment.pay()`) into genuinely separate classes, each changeable independently.
4. **Runtime flexibility** — the if/else version needs a fresh `method` string on every call, re-deciding from scratch each time. Strategy lets the chosen behavior be **held as state** (`PaymentService`'s `paymentStrategy` field) and swapped explicitly (`setPaymentStrategy(...)`) — useful when the same service instance needs to remember "which strategy is currently active" across multiple calls, not just decide fresh per call.

**When the if/else version is actually fine, stated honestly**: a small, genuinely fixed, unlikely-to-grow set of cases (2–3 branches that will very plausibly never need a 4th) doesn't necessarily justify the extra interface-plus-N-classes ceremony — Strategy is worth reaching for specifically when the branch count is expected to **grow over time**, when each branch's logic is substantial enough to deserve its own tested unit, or when the "current strategy" genuinely needs to be held as swappable state rather than decided fresh each call. Introducing it preemptively for two branches that will never change is over-engineering in the other direction.
