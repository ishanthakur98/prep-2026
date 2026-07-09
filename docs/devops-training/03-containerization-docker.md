# Module 3 — Containerization, Docker & Docker Compose

*Precedes Exercise 3: run the app locally with Docker Compose (app + MySQL).*

## Start here: ask the room
"You just got `mvn package` working on your own laptop. Raise your hand if you're 100% sure this would also work, identically, on the laptop next to you, or on a server in AWS." Someone will hesitate — different Java version, different OS, some config file that only exists on their machine, MySQL installed differently. That gap is exactly what containers close.

## The "works on my machine" problem, made concrete
Even after Maven fixed *dependency* consistency, you still have:
- Different OS between your laptop, your teammate's laptop, and the production server.
- A specific Java runtime version that needs to be installed and on the PATH.
- A database (MySQL) that needs to exist, at a specific version, reachable at a specific address, with the schema already there.
- Environment-specific configuration files, environment variables, ports.

Historically the answer was: write a very long "how to set up your dev environment" wiki page, and it goes stale within a month. Containers replace that wiki page with an executable file.

## VM vs container — the analogy that sticks
- A **virtual machine** packages an entire operating system, including its own kernel — heavy, slow to start (minutes), but fully isolated.
- A **container** packages *just the application and everything it needs above the OS kernel* (runtime, libraries, config) and shares the host machine's kernel — lightweight, starts in seconds, but still isolated from other containers.

Analogy: a VM is like shipping an entire house to move one piece of furniture. A container is like a shipping container — standardized, stackable, and it only holds what actually needs to move, but it's sealed so nothing inside is affected by what's next to it on the ship.

## Image vs container — the distinction people mix up
- A **Docker image** is a *read-only template* — the packaged application plus its runtime, dependencies, and filesystem, frozen at build time. Think of it like a class in OOP, or a recipe.
- A **container** is a *running instance* of an image. Think of it like an object instantiated from that class, or a dish actually cooked from the recipe.

You can run the same image many times, as many independent containers, each isolated from the others.

## The Dockerfile — how an image gets built
A Dockerfile is a plain-text, versioned (checked into Git!) recipe for building an image, step by step. For a Java web app like SailPoint IIQ, conceptually it looks like:

```dockerfile
# Start from a base image that already has the Java runtime + Tomcat
FROM tomcat:9-jdk11

# Copy the .war file produced by `mvn package` into Tomcat's deploy folder
COPY target/identityiq.war /usr/local/tomcat/webapps/identityiq.war

# Document which port the app listens on
EXPOSE 8080

# The command that runs when a container starts from this image
CMD ["catalina.sh", "run"]
```

Walk through this line by line and connect it back to module 2: "That `target/identityiq.war` file is literally the output of the `mvn package` step you just ran. Docker doesn't rebuild your app — it takes the *already-built artifact* and wraps it with everything needed to run it, consistently, anywhere."

### Layers — worth one sentence
Each instruction in a Dockerfile creates a cached layer. Change only the last line, and Docker reuses everything above it instead of rebuilding from scratch — this is why well-ordered Dockerfiles (stable stuff first, frequently-changing stuff last) build much faster.

## Why not just containerize the app and call it done? Enter Docker Compose
The app doesn't run alone — it needs a database. In production that's RDS (a managed AWS service, module 4). Locally, you don't want everyone installing and configuring MySQL by hand — same "works on my machine" problem, just moved to the database.

**Docker Compose** lets you describe a *set* of containers that should run together, and how they connect, in one YAML file:

```yaml
services:
  app:
    build: .
    ports:
      - "8080:8080"
    environment:
      DB_HOST: mysql
      DB_USER: iiq
      DB_PASSWORD: examplepassword
    depends_on:
      - mysql

  mysql:
    image: mysql:8
    environment:
      MYSQL_ROOT_PASSWORD: examplepassword
      MYSQL_DATABASE: identityiq
    ports:
      - "3306:3306"
```

Point out three things on screen:
- **`services:`** — each block is one container; here, `app` and `mysql`.
- **`depends_on`** — Compose starts `mysql` before `app` (though note: this controls *start order*, not "wait until MySQL is actually ready to accept connections" — a common gotcha worth a one-line warning).
- **Networking** — Compose automatically creates a shared network where `app` can reach the database simply by hostname `mysql` — no IP addresses, no manual network config. This is the same *idea* (service discovery by name) that Kubernetes uses at production scale in module 4, just simpler.

One command, `docker compose up`, brings up both containers, wired together, in the same state every time — on your machine, your neighbor's, or a CI runner.

## The line that ties this to the rest of the day
> "Notice what just happened: you didn't install MySQL. You didn't configure Tomcat. You didn't figure out how the app finds the database. All of that is now *written down* in two files — a Dockerfile and a docker-compose.yml — checked into Git, versioned, reviewable in a pull request just like code. Environment setup stopped being tribal knowledge and became source code."

## Pause & ask the room
"In production, we don't run MySQL in a container next to the app — we use RDS instead. Any guesses why?" Let them guess (durability, backups, scaling, patching, not wanting your database to disappear if a container restarts). Tell them: "Hold that thought — that's exactly what module 4 covers."

## Common friction points to warn people about before the exercise
- Port conflicts if something else on their laptop is already using 8080 or 3306.
- Docker Desktop resource limits (memory) if the app + MySQL together need more RAM than allocated.
- First `docker compose up` will be slow (pulling the MySQL base image) — same "first run downloads everything" pattern as Maven's first build.

## Transition line into Exercise 3
"Let's bring both containers up together and hit the app in a browser, backed by a real, running MySQL — no local install required."
