# DevOps Automation Training — Speaker Guide & Materials

## What this is built for
- **Audience**: developers who are new to DevOps — comfortable coding, not comfortable with CI/CD, Docker, or Kubernetes yet.
- **Duration**: full-day session (~8 hrs including breaks, lunch, and 4 hands-on exercises).
- **Stack**: SailPoint IdentityIQ (IIQ) source on GitHub → build with Maven → containerize with Docker (+ MySQL via Docker Compose for local dev) → GitHub Actions CI builds the image and pushes to ECR → separate CD repo holds Helm charts per environment → GitHub Actions CD deploys to EKS → app connects to RDS in real environments.
- **Two-repo split**: a **CI repo** (app source, Dockerfile, build workflow) and a **CD repo** (Helm charts per env, deploy workflow) — this decouples "build once" from "deploy per environment."

## The 4 exercises (yours, as given)
1. **Fork the GitHub repo + set up a Personal Access Token (PAT)**
2. **Set up the Maven project locally and build it**
3. **Run it locally with Docker, using Docker Compose (app + MySQL)**
4. **Use a GitHub Action to deploy to EKS, then access the running app**

These already exist as hands-on labs on your side — the files below are the *concept explanations* that go immediately before each one, plus a facilitator note per exercise (what to watch for, what "done" looks like, common failure points). They don't duplicate your lab steps.

## How to use this material
Each numbered file is written so you can **read/speak it almost verbatim** — it explains the *why* before the *how*, uses analogies, and calls out "pause here, ask the room" moments. Have it open on a second screen or print it.

| # | File | Covers | Suggested time |
|---|------|--------|------|
| 1 | [01-github-version-control.md](01-github-version-control.md) | Git vs GitHub, forking, branches, PAT tokens & why they exist | 45 min |
| 2 | [02-build-tools-maven.md](02-build-tools-maven.md) | Why build tools exist, dependency management, Maven lifecycle | 45 min |
| 3 | [03-containerization-docker.md](03-containerization-docker.md) | "Works on my machine," images vs containers, Dockerfile, Docker Compose for app+MySQL | 60 min |
| 4 | [04-cicd-pipeline-aws.md](04-cicd-pipeline-aws.md) | CI vs CD, testing in the pipeline, GitHub Actions, ECR, EKS/Kubernetes, Helm per-env, RDS | 75 min |
| 5 | [05-facilitator-notes-exercises.md](05-facilitator-notes-exercises.md) | Per-exercise framing, success checklist, common failures | throughout |
| 6 | [06-glossary-cheatsheet.md](06-glossary-cheatsheet.md) | One-page term reference for Q&A / handout | reference |

## Suggested flow for the day
```
09:00  Kickoff — the opening hook (below)                  [10 min]
09:10  01-github-version-control.md                        [45 min]
09:55  Exercise 1 — fork repo + set up PAT                 [30 min]
10:25  Break                                                [15 min]
10:40  02-build-tools-maven.md                              [45 min]
11:25  Exercise 2 — Maven local build                       [30 min]
11:55  Lunch                                                [60 min]
13:00  03-containerization-docker.md                        [60 min]
14:00  Exercise 3 — Docker Compose (app + MySQL) locally    [45 min]
14:45  Break                                                [15 min]
15:00  04-cicd-pipeline-aws.md                               [75 min]
16:15  Exercise 4 — GitHub Actions deploy to EKS + access   [60 min]
17:15  Wrap-up, Q&A, recap                                   [15 min]
17:30  End
```
Trim the "deep dive" callouts in each file first if you're running behind — they're marked and safe to skip or defer to Q&A.

## The opening hook (say this first, before any slide)
> "Right now, if I want to ship a change to SailPoint IIQ, what has to happen? Someone builds it, someone copies files to a server, someone restarts a service, someone hopes the DB connection still works, and if it breaks at 6pm on a Friday, someone gets paged. Today we're going to replace all of that judgment-call, tribal-knowledge process with something that's *written down, versioned, and repeatable* — a pipeline. By the end of today, pushing code will *mean* something specific and predictable happens, every time, the same way, whether it's me running it, you running it, or nobody touching a keyboard at all."

This framing — **manual toil → codified, repeatable process** — is the thread that ties every section together. Come back to it whenever you introduce a new tool: "why does this exist?" → "because we don't want a human doing this by hand, inconsistently, at 2am."

Notice the shape of the whole day mirrors this: each exercise removes one more manual step.
- Exercise 1 (Git/GitHub) — removes "who has the latest code, and how do changes get shared safely."
- Exercise 2 (Maven) — removes "did you remember every manual compile/dependency step in the right order."
- Exercise 3 (Docker/Compose) — removes "works on my machine but not yours."
- Exercise 4 (GitHub Actions → EKS) — removes the human from the deploy step entirely.

Say this connecting sentence out loud between modules — it's what makes the day feel like one story instead of four unrelated tool demos.
