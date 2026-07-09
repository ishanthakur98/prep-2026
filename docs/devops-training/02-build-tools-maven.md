# Module 2 — Why Build Tools Exist, and Maven

*Precedes Exercise 2: set up the Maven project locally and build it.*

## Start here: ask the room
"Once you have the source code on your laptop (from Exercise 1), what actually has to happen to turn `.java` files into something that runs?" Let people struggle a bit — compile, resolve libraries the code depends on, package it into the right shape (a WAR/JAR), maybe run tests. That whole list is what a build tool automates.

## Life before build tools (say this, it lands well)
Imagine your project depends on 40 external libraries. Without a build tool, you'd have to:
- Know exactly which 40 libraries, and which versions.
- Download each one by hand from wherever it lives.
- Know which *other* libraries each of those 40 depends on (transitive dependencies) — this cascades fast.
- Put them all in the right folder, in the right order, and hope nothing conflicts.
- Redo all of this on every teammate's machine, and again on every server.

This was genuinely how Java projects worked in the early 2000s. It was miserable and inconsistent — exactly the "works on my machine" problem, just at the dependency layer instead of the OS layer.

## What Maven actually does
Maven solves two problems at once:

1. **Dependency management** — you declare *what* you need (name + version) in one file, `pom.xml`. Maven downloads it, and everything it transitively needs, from a shared repository (Maven Central, or your org's private repository/Nexus/Artifactory if you have one). Everyone building the project gets the *exact same* versions.
2. **A standard build lifecycle** — a fixed, predictable sequence of steps every Maven project follows, so "how do I build this" has the same answer across every Java project you'll ever touch, not a bespoke script per repo.

## The Maven lifecycle (the phases people will actually run)
Walk through these in order — each phase runs everything before it too:

| Phase | What happens |
|---|---|
| `validate` | Checks the project is structured correctly |
| `compile` | Turns `.java` source into `.class` bytecode |
| `test` | Runs unit tests (module 4 will come back to *why* this matters for CI) |
| `package` | Bundles compiled code into the distributable form — for a web app like SailPoint IIQ, typically a `.war` file |
| `verify` | Runs any additional checks on the packaged artifact |
| `install` | Copies the packaged artifact into your *local* Maven repository, so other local projects can depend on it |
| `deploy` | Copies it to a *remote/shared* repository for others — not to be confused with "deploying to a server," different meaning of the word |

Point out explicitly: **`mvn package`** is the command most people will actually run today — it gets you compiled, tested, packaged output without pushing anywhere.

## `pom.xml` — the one file that matters
Open the project's `pom.xml` on screen and point out just three things, don't go deep:
- `<dependencies>` — the list of libraries this project needs (and what version).
- `<build>` / plugins — anything special about *how* to package this project (e.g., WAR packaging for a web app).
- The `<parent>` block if there is one — inherited config from a shared parent POM (common in larger orgs).

## Why this matters for everything later today (the important connective tissue)
This is the line to say explicitly, because it's the actual "aha" of the whole module:

> "The exact same `mvn package` command you just ran on your laptop is going to run *again*, unchanged, inside a Docker container in a few hours, and *again*, unchanged, inside GitHub's CI runner this afternoon. The whole point of a build tool is that the build isn't tied to your machine — it's a reproducible, scriptable step that anyone (or anything — a robot, a CI server) can run and get the identical result."

That's the seed for containerization (module 3) and CI (module 4): if the build can only run "on my machine because I set it up a special way," none of the rest of the day works.

## Common friction points to warn people about before the exercise
- **JDK version mismatches** — Maven needs a specific Java version; mismatches produce confusing errors. Check `java -version` before starting.
- **First build is slow** — Maven downloads every dependency from the internet the first time; it caches locally after that (`~/.m2/repository`). Don't panic if it takes a few minutes.
- **Corporate proxy/VPN** issues blocking access to Maven Central, if applicable to your network — have a fallback (e.g., a pre-warmed `.m2` cache) ready if you've hit this before.

## Pause & ask the room
"If I handed this exact project folder to someone with no internet access, could they build it?" (Answer: not the *first* time — Maven needs to fetch dependencies — but after that first build, yes, from the local `.m2` cache. This is a good bridge into why Docker images "bake in" dependencies so runtime doesn't need internet either.)

## Transition line into Exercise 2
"Let's go get `mvn package` actually running on your machine, and look at what comes out the other end."
