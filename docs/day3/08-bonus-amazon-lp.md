# Bonus — Amazon Leadership Principles (Today: Customer Obsession, Ownership)

## Part 1 — What these two principles actually probe for

Amazon's Leadership Principle interviews are behavioral, but they're graded against a specific bar per principle — knowing what the interviewer is actually listening for matters more than knowing the principle's tagline.

### Customer Obsession
**Official framing**: "Leaders start with the customer and work backwards. They work vigorously to earn and keep customer trust."

**What the interviewer is actually listening for**:
- Did you identify a *real* customer need (not an assumption, not "what I thought was cool to build")?
- Did you make a decision that cost *you* or your team something (time, a simpler path, short-term metrics) in favor of the customer's actual experience?
- Is "customer" concrete in your story — an end user, an internal team consuming your API, a support team dealing with tickets — not an abstract placeholder?
- Bad signal: a story where "customer obsession" is really just "I did my job competently." The bar is a moment where customer need and something *else* (deadline, convenience, a stakeholder's preference) were in tension, and you chose the customer.

### Ownership
**Official framing**: "Leaders are owners. They think long term and don't sacrifice long-term value for short-term results. They act on behalf of the entire company, beyond just their own team. They never say 'that's not my job.'"

**What the interviewer is actually listening for**:
- Did you take on something **outside your explicit scope/ticket/role** because it needed doing?
- Did you think beyond the immediate fix — e.g. root-causing instead of patching, or considering the next team that inherits your code?
- Bad signal: a story that's really about individual technical skill with no "I went beyond what was asked" moment — Ownership needs a boundary you crossed voluntarily, not just a task you completed well.

---

## Part 2 — Prepare one STAR story per principle

Use the same STAR structure from [../day2/06-behavioural.md](../day2/06-behavioural.md) (Situation / Task / Action / Result), but tailor the emphasis to what each principle is actually graded on.

### Customer Obsession — template
```
SITUATION:
- Who was the customer? (end user / internal team / support / another engineering team)
- What was their actual pain, in their words if possible — not your interpretation of it?

TASK:
- What were you asked to do, and how did that differ from what the customer actually needed?

ACTION (the pivot moment matters most):
- What tradeoff did you face — e.g. ship the simpler thing on time vs. the thing that
  actually solves the customer's problem?
- What did you do to actually understand the customer need (talked to support tickets,
  shadowed a user, read the actual complaint) rather than assume it?
- What did you choose, and what did it cost you (time, complexity, pushback from someone)?

RESULT:
- Concrete outcome for the customer (fewer tickets, faster resolution, adoption number).
- Did it change how the team approaches similar decisions afterward?
```

**Worked example (generic — replace with your real incident):**
> **Situation**: An internal API my team owned was technically meeting its SLA, but the consuming team kept filing tickets about "unpredictable" response times.
> **Task**: I was asked to close out the tickets by confirming the SLA was met.
> **Action**: Instead of just replying "SLA is met, closing ticket," I looked at the consuming team's actual usage pattern and found our p50 was fine but our p99 spiked hard during their peak batch window — technically within SLA on average, but genuinely unpredictable from their side, exactly what they were reporting. I pushed back on my own team's plan to just close the tickets, and instead proposed and implemented a dedicated rate-limit lane for batch-window traffic so their p99 stopped spiking, even though this wasn't in the original ask and added a sprint of work.
> **Result**: Their p99 dropped from ~4s to ~400ms during peak windows, tickets stopped, and we adopted "check p99 for known consumer traffic patterns, not just average SLA" as a standing review step for API changes afterward.

### Ownership — template
```
SITUATION:
- What was the immediate problem, and whose "job" was it technically, if anyone's?

TASK:
- What was explicitly asked of you (if anything) vs. what you noticed needed doing?

ACTION:
- What did you do that went beyond your ticket/role/team boundary?
- What long-term thinking did you apply instead of a quick patch (root cause vs.
  band-aid, considering the next person who touches this)?
- Who did you have to convince, or what resistance did you face, taking this on?

RESULT:
- What changed, measurably?
- Did you leave something better than you found it for whoever comes next
  (docs, monitoring, a process)?
```

**Worked example (generic — replace with your real incident):**
> **Situation**: A recurring CI flakiness issue was being worked around by individual engineers re-running failed builds, costing the team real time every week, but it lived in a shared pipeline nobody explicitly owned.
> **Task**: Nobody assigned this to me — my actual ticket queue was unrelated feature work.
> **Action**: I tracked the flaky failures for a week, found they clustered around a specific integration test with a race condition against a shared test database, and instead of just fixing my own team's usage of it, I root-caused the race condition in the shared test fixture itself and fixed it at the source, then wrote up a short runbook so the next team hitting similar flakiness had a documented diagnosis path instead of rediscovering it from scratch.
> **Result**: CI flake rate for that suite dropped by roughly 80%, saving the team (and at least two other teams sharing that pipeline) re-run time every week, and the runbook has been referenced by another team since.

---

## Part 3 — Delivery tips specific to LP interviews
- Amazon interviewers often ask a **follow-up "dive deep"** on your story — be ready to go two levels deeper on any specific decision you mention ("why did you choose that fix over X", "what would you have done if leadership disagreed"). Don't over-polish the surface story at the expense of being able to go deep on it.
- Have a **different story per principle** — reusing the same incident for both Customer Obsession and Ownership is a common weak signal (it suggests a shallow story bank, and the interviewer will often ask "tell me about a different time").
- Quantify the result wherever honestly possible — a number is far more convincing than "it went well."
