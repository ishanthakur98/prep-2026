# Module 1 — Git, GitHub, Forking & PAT Tokens

*Precedes Exercise 1: fork the repo + set up a PAT.*

## Start here: ask the room
"Before today, if two of you edited the same file at the same time, what happened?" Let people answer — usually some version of "we emailed it around" or "we overwrote each other." That's the problem Git exists to solve.

## Git vs GitHub — say this distinction explicitly
People conflate these constantly, so separate them clearly:

- **Git** is a tool that runs *on your laptop*. It tracks every change to every file over time, lets you branch (work on something without touching the main code), and merge (bring work back together). It works with zero internet connection.
- **GitHub** is a *website/service* built on top of Git. It hosts a copy of your Git repository in the cloud, adds collaboration features (pull requests, code review, issues), and — critically for today — lets you attach **automation** to events in the repo (that's GitHub Actions, module 4).

Analogy: Git is like a word processor's "track changes" and version history, running locally. GitHub is like Google Docs — the shared, hosted place where everyone's copy lives and where you can see who changed what.

## Why version control matters for what we're building today
Every later step depends on there being one agreed-upon, traceable copy of the source code:
- CI (module 4) triggers *because* code was pushed to GitHub — no GitHub, no automatic trigger.
- The Docker image we build (module 3) is a snapshot of *exactly* the code at one commit — traceability from a running container back to a line of code is the whole point.
- If a deployment breaks, "what changed?" is answered by `git log`, not by asking around.

## Forking — why we use it in this exercise
A **fork** is your own personal copy of someone else's repository, on your own GitHub account. You get:
- A sandbox to make changes without needing write access to the original repo.
- A clean way to later propose your changes back via a **pull request**, if that repo owner wants them.

For today: you're forking the SailPoint IIQ training repo so each of you has your own copy to build, containerize, and deploy — you won't be stepping on each other's changes or waiting on shared write access.

Contrast quickly:
- **Clone** = download a copy of a repo to your machine (you'll do this too, after forking).
- **Fork** = copy the repo to *your own GitHub account* first, then clone *that*.

## Personal Access Tokens (PAT) — why not just a username/password?
This is usually the part people find fiddly, so spend real time on the *why*.

- GitHub (like most services now) doesn't let you authenticate Git operations or API calls with a plain password anymore — for good reason.
- A password is one credential that unlocks *everything* on your account, forever, until you change it.
- A **PAT** is a scoped, revocable credential:
  - **Scoped** — you choose what it can do (e.g., only read/write repo contents, not touch billing or delete the account).
  - **Revocable** — you can kill just that one token without changing your password everywhere else.
  - **Expirable** — you can set it to auto-expire in 30/60/90 days, so a leaked old token stops being useful.

Say this out loud: "This is the same principle behind almost every credential you'll touch today — the AWS credentials GitHub Actions uses later, the database password the app uses to reach RDS. Least privilege, scoped, revocable. A PAT is your first hands-on example of it."

### Practical rules to state before the exercise
- Never commit a PAT into a file in the repo — treat it like a password, because it is one.
- Give it only the scopes you actually need for the exercise.
- If you're not sure whether a token leaked, revoke it — it costs nothing and takes 10 seconds.

## Pause & ask the room
"Where do you think this PAT is going to get used later today?" — let them guess. (Answer: it authenticates your local `git push`/`git pull`, and conceptually is the same idea as the credentials GitHub Actions will use to talk to AWS in Exercise 4.)

## Transition line into Exercise 1
"Let's stop talking about it and go get your own copy of the code, with your own credentials to push changes to it."
