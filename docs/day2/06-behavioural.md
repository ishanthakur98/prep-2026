# Behavioural — STAR Story Prep

## Part 1 — Concept: the STAR structure

| Letter | Meaning | What to include |
|---|---|---|
| **S**ituation | Context | Where/when, what system, what was normal before things went wrong |
| **T**ask | Your responsibility | What were *you* specifically expected to do (not the whole team) |
| **A**ction | What you actually did | Concrete steps, decisions, and *why* you chose them over alternatives |
| **R**esult | Outcome | Quantified impact if possible (downtime avoided, time saved, users affected), plus what you learned/changed afterward |

**Common mistakes to avoid**:
- Spending 80% of the story on "Situation" and rushing "Action" — the interviewer cares most about *what you personally did* and *why*.
- Vague actions ("I looked into it and fixed it") — be specific: what did you check first, what did the logs/metrics show, what was the actual root cause, what was the fix.
- No result / no learning — always close with impact and, ideally, a process change that prevents recurrence (this signals seniority).
- Using "we" the whole time — the interviewer needs to know your individual contribution. Use "I" for your actions, "we" only for genuinely shared decisions.

---

## Part 2 — Prepare: "Tell me about a difficult production issue you solved"

Pick **one** real incident from your own experience — Spring Batch, CI/CD, APIM, or Docker/OpenShift are all listed as candidate domains. Fill in this template with your actual details before the interview (this doc can't know your specific incident, so use it as scaffolding):

### Template
```
SITUATION:
- System/service: ____________________
- What was the symptom? (alert fired, users reported X, job failed, etc.)
- Business impact / urgency: ____________________

TASK:
- What was your specific role? (on-call, owner of the service, first responder, etc.)
- What was expected of you in the moment?

ACTION (be specific — this is 60% of the story):
1. First thing you checked: ____________________
2. What the logs/metrics/dashboard showed: ____________________
3. Hypothesis you formed, and how you validated/ruled it out: ____________________
4. The actual root cause: ____________________
5. The fix you applied — and any immediate mitigation vs the real fix if they differed:
   ____________________
6. Who you communicated with during this (manager, other teams) and how:
   ____________________

RESULT:
- How long did it take to resolve? ____________________
- Quantified impact: ____________________ (e.g., "reduced downtime from X to Y",
  "prevented recurrence for N months since")
- What changed afterward? (monitoring added, runbook written, code fix, process change)
- What did you personally learn?
```

### Worked example (generic — adapt the specifics to your real incident)

> **Situation**: A Spring Batch job responsible for nightly reconciliation between two systems started silently failing partway through, leaving downstream reports incomplete. It wasn't caught until a business user flagged a discrepancy the next morning.
>
> **Task**: As the engineer who owned that batch pipeline, I was asked to find the root cause and prevent recurrence, not just rerun the job.
>
> **Action**: I first checked the job's execution logs in Spring Batch's `JobRepository` metadata tables and found the job step had thrown an exception partway through processing a chunk, but the overall job status still showed `COMPLETED` because the failure was being swallowed by a broad `catch (Exception e) { log.error(...) }` inside an `ItemProcessor`. I traced the actual exception to a null field from an upstream API response that a new record type had introduced. As an immediate mitigation, I manually reran the reconciliation for the affected date range after patching the null check. For the real fix, I removed the swallow-and-continue behavior so a processing failure now fails the step (and the job) loudly, added a targeted null-safety check for the new field, and added a Spring Batch `JobExecutionListener` that posts to our alerting channel on any non-`COMPLETED` job status — closing the exact gap that let this run silently.
>
> **Result**: The immediate data discrepancy was corrected within a few hours. More importantly, the alerting gap that let a broken job report as "successful" for a full day was closed — no similar silent failure has recurred since. It also became a small case study I shared with the team on why swallowing exceptions in batch processors is dangerous specifically because it defeats a job's own success/failure signal.

Swap in your **actual** incident details (Spring Batch / CI/CD / APIM / Docker–OpenShift) using the same shape: symptom → what you checked → root cause → fix → measurable result → process change.

---

## Part 3 — Quick delivery tips
- Keep it under ~2 minutes when spoken. Practice out loud, not just in your head — written prep and spoken delivery feel very different.
- Lead with a one-sentence headline ("A silently-failing batch job caused a day of bad reconciliation data") before diving into STAR — gives the interviewer a frame to hang the details on.
- Have a second story ready as backup, from a different domain than your first, in case of a follow-up "tell me about another one."
