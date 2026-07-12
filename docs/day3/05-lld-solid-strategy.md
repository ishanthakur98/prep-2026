# LLD — SOLID Deep Dive + Strategy Pattern (Notification System)

> [../day2/05-lld-solid.md](../day2/05-lld-solid.md) already covers SRP, OCP, LSP in depth with worked examples, plus brief ISP/DIP. This file adds the two pieces today's prompt calls out specifically — **Dependency Injection** and **Composition over Inheritance** — then applies all of it to a concrete build: a multi-channel notification system using the Strategy pattern.

## Part 1 — Concepts (read this first, not definitions — examples)

### Dependency Injection, revisited as an LLD principle (not just a Spring feature)
DI is often introduced as "a Spring thing," but it's a plain OOP design principle Spring happens to automate. The idea, with no framework at all:

```java
// Without DI: NotificationService decides for itself how to send email —
// tightly coupled to one concrete implementation, forever.
class NotificationService {
    private EmailSender sender = new SmtpEmailSender(); // hard-coded
    void notify(String msg) { sender.send(msg); }
}

// With DI: NotificationService only knows about an abstraction.
// Something else (a factory, a container, or literally the caller) decides
// which concrete sender to hand it.
class NotificationService {
    private final Sender sender;
    NotificationService(Sender sender) { this.sender = sender; } // injected
    void notify(String msg) { sender.send(msg); }
}
```
**Why it matters at the design level, independent of any framework**: `NotificationService` becomes testable with a fake `Sender` (no real SMTP call in a unit test), swappable (email today, push tomorrow, no change to `NotificationService` itself), and it stops needing to know *anything* about how sending actually happens. Spring's `@Autowired` is just automating "who calls `new NotificationService(someSender)`" — the design benefit exists with or without the framework.

### Composition over Inheritance
**Prefer "has-a" (a field holding another object) over "is-a" (extends) when you want to reuse or vary behavior**, because inheritance couples a subclass to its parent's implementation, forces exactly one axis of variation (Java has no multiple inheritance of classes), and behavior can only be fixed at compile time. Composition lets you assemble behavior from independent, swappable parts, at runtime.

```java
// Inheritance: trying to reuse "flying" and "quacking" behavior by extending
class Duck { void fly() {...} void quack() {...} }
class RubberDuck extends Duck {
    @Override void fly() { throw new UnsupportedOperationException(); } // it can't fly!
    @Override void quack() { /* squeaky, not real quack */ }
}
// Every new duck type risks another override-to-break-the-contract (an LSP violation,
// see day2/05-lld-solid.md) because "is-a Duck" pulled in behavior this subtype doesn't want.

// Composition: assemble behavior from small, swappable, independently-testable parts
interface FlyBehavior { void fly(); }
interface QuackBehavior { void quack(); }

class FlyWithWings implements FlyBehavior { public void fly() { /* real flying */ } }
class CannotFly implements FlyBehavior { public void fly() { /* no-op, honestly, correctly */ } }

class Duck {
    private final FlyBehavior flyBehavior;
    private final QuackBehavior quackBehavior;
    Duck(FlyBehavior f, QuackBehavior q) { this.flyBehavior = f; this.quackBehavior = q; }
    void performFly() { flyBehavior.fly(); }
    void performQuack() { quackBehavior.quack(); }
}
class RubberDuck extends Duck {
    RubberDuck() { super(new CannotFly(), new SqueakQuack()); } // composed, not forced to override-and-break
}
```
**Why it matters**: `RubberDuck` never has to override a method to throw or lie about its behavior — it simply composes with the `FlyBehavior` that's actually true for it. New behaviors (a `FlyWithRocket`) are new classes, not new subclasses threading through an inheritance hierarchy — this is the exact same OCP benefit from day2's SOLID doc, arrived at through composition. This is also literally the classic Strategy pattern, which is what the notification system below builds on directly.

---

## Part 2 — Implement: Notification System (Email / SMS / Push) using Strategy Pattern

### Design
```
                  ┌────────────────────┐
                  │ NotificationSender  │  ← Strategy interface
                  │  + send(String to,  │
                  │         String msg) │
                  └─────────▲──────────┘
             ┌──────────────┼───────────────┐
             │               │                │
  ┌──────────┴──────┐ ┌──────┴───────┐ ┌──────┴───────┐
  │  EmailSender     │ │  SmsSender   │ │ PushSender   │  ← concrete strategies
  └──────────────────┘ └──────────────┘ └──────────────┘

  ┌──────────────────────────────┐
  │ NotificationService           │  ← Context: holds a NotificationSender,
  │  - sender: NotificationSender │    doesn't know or care which one
  │  + notify(to, msg)            │
  └───────────────────────────────┘
```

```java
// --- Strategy interface ---
public interface NotificationSender {
    void send(String recipient, String message);
}

// --- Concrete strategies ---
public class EmailSender implements NotificationSender {
    @Override
    public void send(String recipient, String message) {
        System.out.println("Sending EMAIL to " + recipient + ": " + message);
        // real implementation: SMTP client call
    }
}

public class SmsSender implements NotificationSender {
    @Override
    public void send(String recipient, String message) {
        System.out.println("Sending SMS to " + recipient + ": " + message);
        // real implementation: Twilio/SNS API call
    }
}

public class PushSender implements NotificationSender {
    @Override
    public void send(String recipient, String message) {
        System.out.println("Sending PUSH to " + recipient + ": " + message);
        // real implementation: FCM/APNs call
    }
}

// --- Context: composes a strategy, doesn't know which concrete one ---
public class NotificationService {
    private final NotificationSender sender; // DI: injected, not `new`'d here

    public NotificationService(NotificationSender sender) {
        this.sender = sender;
    }

    public void notify(String recipient, String message) {
        sender.send(recipient, message); // delegates — NotificationService
                                          // never branches on "what kind" of sender
    }
}

// --- Usage: the strategy is chosen (and can be swapped) at the call site ---
public class Demo {
    public static void main(String[] args) {
        NotificationService emailService = new NotificationService(new EmailSender());
        NotificationService smsService   = new NotificationService(new SmsSender());
        NotificationService pushService  = new NotificationService(new PushSender());

        emailService.notify("user@example.com", "Your order has shipped");
        smsService.notify("+1-555-0100", "Your OTP is 482913");
        pushService.notify("device-token-xyz", "You have a new message");
    }
}
```

### Mapping this back to SOLID (say this explicitly in an interview)
- **SRP**: each `*Sender` has exactly one reason to change — how that one channel sends a message. `NotificationService` has exactly one reason to change — how notifications are orchestrated, not how any specific channel works.
- **OCP**: adding a `SlackSender` or `WhatsAppSender` tomorrow means writing one new class implementing `NotificationSender` — **zero changes** to `NotificationService` or any existing sender. Compare this to the anti-pattern this replaces:
  ```java
  // What Strategy avoids — the OCP-violating version:
  void notify(String type, String to, String msg) {
      if (type.equals("EMAIL")) { /* smtp logic inline */ }
      else if (type.equals("SMS")) { /* twilio logic inline */ }
      else if (type.equals("PUSH")) { /* fcm logic inline */ }
      // adding WhatsApp means editing this method again, risking every existing branch
  }
  ```
- **LSP**: any `NotificationSender` implementation is fully substitutable wherever the interface is expected — `NotificationService` behaves correctly regardless of which concrete sender it was constructed with, because none of them weaken the `send(recipient, message)` contract.
- **DIP**: `NotificationService` (high-level orchestration) depends on the `NotificationSender` **interface** (abstraction), not on `EmailSender`/`SmsSender`/`PushSender` (concrete, low-level implementations) — the dependency points *toward* the abstraction from both directions, which is the actual definition of "inversion."
- **Composition over inheritance**: `NotificationService` **has-a** `NotificationSender` rather than `EmailNotificationService extends NotificationService` / `SmsNotificationService extends NotificationService` — avoiding an inheritance hierarchy that would need to be extended (and recompiled/redeployed alongside the base class) for every new channel.
- **Dependency Injection**: the concrete sender is passed into `NotificationService`'s constructor rather than instantiated inside it — in a Spring context, this is exactly `@Service class NotificationService { NotificationService(NotificationSender sender) {...} }` with each `*Sender` annotated `@Component`/`@Qualifier`-selected; the plain-Java version above is the same design with the framework wiring removed, to make clear the pattern doesn't depend on Spring at all.

### Extending it: choosing a strategy dynamically
A realistic follow-up: "what if the channel isn't known until runtime (e.g. user preference in the DB)?" Register strategies in a map instead of hard-wiring one per `NotificationService` instance:
```java
public class NotificationDispatcher {
    private final Map<String, NotificationSender> senders;

    public NotificationDispatcher(Map<String, NotificationSender> senders) {
        this.senders = senders; // e.g. {"EMAIL": emailSender, "SMS": smsSender, ...}
    }

    public void dispatch(String channel, String to, String msg) {
        NotificationSender sender = senders.get(channel);
        if (sender == null) throw new IllegalArgumentException("Unknown channel: " + channel);
        sender.send(to, msg);
    }
}
```
This still respects OCP — a new channel is a new map entry (often auto-wired in Spring via `Map<String, NotificationSender>` injection, where Spring populates the map with every `NotificationSender` bean keyed by bean name) — no `if/else` chain reappears anywhere.

---

## Part 3 — Interview Questions

**Q: Why Strategy pattern here instead of a simple `if/else` on notification type?**

**A:** The `if/else` version violates OCP directly — every new channel means editing an existing, already-tested method, risking regressions in every other branch. It also violates SRP for that method, since it now has one reason to change per channel instead of one reason overall. Strategy fixes both: each channel is an independent class (SRP), and adding a channel is purely additive (OCP) — the existing dispatch logic (`sender.send(...)`) never changes.

**Q: Isn't this over-engineering for three notification types?**

**A:** Fair pushback if the set of channels is truly fixed forever and this is a one-off script — a straight `if/else` is fine for something that will never grow. But "notification channel" is a textbook example of a dimension that *does* grow in real systems (email → SMS → push → Slack → WhatsApp → in-app), and the Strategy version costs almost nothing extra upfront (one interface, small classes) while avoiding a growing, increasingly risky conditional later. The judgment call is: is this dimension likely to vary? If yes, pay the small abstraction cost now.
