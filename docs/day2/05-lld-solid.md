# LLD — SOLID Principles

## Part 1 — Concepts (read this first)

SOLID is five design principles aimed at making code easier to change without breaking it. The prompt asked to go especially deep on the first three.

### S — Single Responsibility Principle (SRP)
**"A class should have only one reason to change."**

Not "a class should do one thing" in a trivial sense — it's about **who asks for the change**. If a `Employee` class has both `calculatePay()` and `saveToDatabase()`, it now changes for two unrelated reasons: payroll rules changing, *and* database schema/technology changing. Those are different stakeholders/reasons — split them.

```java
// Violates SRP: business logic + persistence + reporting all in one class
class Employee {
    double calculatePay() { ... }
    void saveToDatabase() { ... }
    String generateReport() { ... }
}

// Follows SRP: each class has one axis of change
class Employee { /* just data + core behavior */ }
class PayCalculator { double calculate(Employee e) { ... } }
class EmployeeRepository { void save(Employee e) { ... } }
class EmployeeReportGenerator { String generate(Employee e) { ... } }
```
**Why it matters**: a change to how you persist employees (switch from JPA to MongoDB) shouldn't risk breaking pay calculation logic — they're now in files that never touch each other.

### O — Open/Closed Principle (OCP)
**"Open for extension, closed for modification."** Add new behavior by adding new code, not by editing existing, already-tested code.

```java
// Violates OCP: every new discount type means editing this method
class DiscountCalculator {
    double calculate(String type, double price) {
        if (type.equals("STUDENT")) return price * 0.9;
        if (type.equals("SENIOR")) return price * 0.8;
        // adding "EMPLOYEE" discount means touching this method again
        return price;
    }
}

// Follows OCP: new discount = new class, zero changes to existing code
interface DiscountStrategy {
    double apply(double price);
}
class StudentDiscount implements DiscountStrategy {
    public double apply(double price) { return price * 0.9; }
}
class SeniorDiscount implements DiscountStrategy {
    public double apply(double price) { return price * 0.8; }
}
// Adding EmployeeDiscount later touches NOTHING above — just adds a new file.
```
**Why it matters**: every edit to already-working, already-tested code risks a regression. Polymorphism (interfaces/strategy pattern) lets you extend behavior via new classes instead of re-editing a growing `if/else` chain.

### L — Liskov Substitution Principle (LSP)
**"Subtypes must be substitutable for their base type without breaking correctness."** If code works with a `Bird`, it must keep working when handed any subclass of `Bird` — no surprises, no weakened guarantees.

```java
// Classic LSP violation
class Bird {
    void fly() { ... }
}
class Ostrich extends Bird {
    void fly() { throw new UnsupportedOperationException(); } // breaks the contract!
}

void letItFly(Bird b) {
    b.fly(); // works for every Bird... except it crashes for Ostrich
}
```
The fix isn't "make `fly()` do nothing" (still misleading) — it's to **not model `Ostrich` as a `Bird` that flies** in the first place:
```java
interface Bird { }
interface FlyingBird extends Bird {
    void fly();
}
class Sparrow implements FlyingBird {
    public void fly() { ... }
}
class Ostrich implements Bird { /* no fly() at all — and that's correct */ }
```
**Why it matters**: LSP violations are usually inheritance used to model "is mostly like" instead of "is substitutable for". They cause exactly the kind of runtime surprise (`UnsupportedOperationException`, unexpected `null`, silently weaker preconditions) that's hard to catch in code review and easy to hit in production.

### I — Interface Segregation Principle (ISP) *(brief)*
**"No client should be forced to depend on methods it doesn't use."** Prefer several small, focused interfaces over one fat interface. E.g., split a bloated `Worker` interface (with `work()`, `eat()`, `sleep()`) into `Workable`, `Eatable`, `Sleepable` so a `RobotWorker` only implements `Workable`, instead of being forced to stub out `eat()`/`sleep()`.

### D — Dependency Inversion Principle (DIP) *(brief)*
**"Depend on abstractions, not concretions."** High-level modules (business logic) shouldn't depend directly on low-level modules (a specific database driver, a specific payment gateway) — both should depend on an interface. This is exactly what Spring's DI container operationalizes: your `OrderService` depends on the `PaymentGateway` interface, not `StripeGateway` directly (see [architecture-di-beans.md](../springboot/architecture-di-beans.md)).

---

## Part 2 — Find these in your own codebase

Before the interview, walk through your own recent code and try to answer:
- **SRP**: pick a service class you wrote — list every reason it could change. If you find two unrelated reasons (e.g., "changes if the API contract changes" AND "changes if the DB schema changes"), that's a split candidate.
- **OCP**: find an `if/else` or `switch` on a "type" field. Ask: if a new type is added next sprint, how many existing files get touched? If more than zero (besides a factory/registry), it's an OCP candidate for a strategy/polymorphism refactor.
- **LSP**: find a subclass that overrides a method to throw, return `null` unexpectedly, or do nothing when the base class implied it would do something — that's your LSP violation to cite.

Having one real example ready for SRP and OCP from your own code is worth more in an interview than reciting the definitions — it shows you actually apply these day-to-day, not just memorized them.
