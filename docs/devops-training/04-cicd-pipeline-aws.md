# Module 4 — CI/CD, GitHub Actions, and the AWS Pieces (ECR, EKS, Helm, RDS)

*Precedes Exercise 4: use a GitHub Action to deploy to EKS, then access the running app.*

This is the longest module — it's where every earlier piece (Git, Maven, Docker) gets wired into one automated pipeline. Take a breath before starting; this is the payoff of the whole day.

## Start here: ask the room
"This morning, you ran `mvn package` by hand. After lunch, you ran `docker compose up` by hand. If you had to do both of those, correctly, in the right order, every single time someone pushed a code change — forever — what would go wrong eventually?" Let them answer: someone forgets a step, someone's laptop has a slightly different setup, someone's tired on a Friday. That's the case for automating it.

## What CI actually is (say the acronym expansion, then ignore it)
Continuous Integration. Ignore the jargon — here's what it means in practice:

> Every time someone pushes code, a machine automatically checks out that code, builds it, and runs the tests — with no human triggering it by hand.

**Why this matters**: the whole point is *catching problems the moment they're introduced*, not days later when three more people have built on top of the broken change. "Integration" refers to integrating everyone's changes together constantly and verifying it still works, rather than everyone working in isolation for weeks and discovering conflicts at the end.

### Where testing fits in
Recall from module 2 that Maven's lifecycle includes a `test` phase. CI is what makes that phase *matter*:
- Locally, nothing stops you from skipping tests or not noticing a failure.
- In CI, a failing test **fails the pipeline** — the build goes red, the team sees it immediately, and (depending on how it's configured) a broken build can be blocked from going any further, including blocked from being deployed.

Say explicitly: "Testing isn't a separate topic from CI/CD — it's the gate *inside* CI that decides whether anything downstream (building an image, deploying it) is even allowed to happen."

## What CD actually is
Continuous Delivery/Deployment. Also ignore the jargon:

> Once code has passed CI, automatically get it into a deployable shape (a container image, in our case) and automatically (or with one click) get it running in an environment — dev, staging, or production.

The distinction some people care about:
- **Continuous Delivery** — every change that passes CI is *ready* to deploy, but a human clicks "go."
- **Continuous Deployment** — every change that passes CI deploys *automatically*, no human click.

Don't over-index on this distinction for the exercise — just be able to name it if asked.

## GitHub Actions — the automation engine
This is the tool that makes CI/CD *actually happen* on GitHub. A workflow is a YAML file living in `.github/workflows/` in the repo, triggered by an event (a push, a pull request, a manual button).

```yaml
name: CI - Build and Push Image

on:
  push:
    branches: [main]

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK
        uses: actions/setup-java@v4
        with:
          java-version: '11'
          distribution: 'temurin'

      - name: Build with Maven
        run: mvn package

      - name: Build Docker image
        run: docker build -t identityiq:${{ github.sha }} .

      - name: Log in to ECR
        run: aws ecr get-login-password | docker login --username AWS --password-stdin <account-id>.dkr.ecr.<region>.amazonaws.com

      - name: Push image to ECR
        run: |
          docker tag identityiq:${{ github.sha }} <account-id>.dkr.ecr.<region>.amazonaws.com/identityiq:${{ github.sha }}
          docker push <account-id>.dkr.ecr.<region>.amazonaws.com/identityiq:${{ github.sha }}
```

Walk through the anatomy, connecting every step back to something they already did by hand today:
- **`on: push`** — the trigger. No human ran this; a `git push` did.
- **`runs-on: ubuntu-latest`** — GitHub hands you a brand-new, clean virtual machine for every run. This is *why* the build has to be fully scripted — there's no human there to fix a missing setting.
- **`mvn package`** — the exact same command from module 2. Nothing new here — CI didn't invent a new way to build the app, it just runs the same command a human would.
- **`docker build`** — the exact same Dockerfile from module 3.
- **The tag `${{ github.sha }}`** — every image is tagged with the exact Git commit it was built from. This is traceability: given a running container, you can always answer "what code, exactly, is this?"

### Secrets — tie this back to the PAT conversation
GitHub Actions needs AWS credentials to push to ECR and deploy to EKS. These are stored as **GitHub Secrets** (encrypted, never shown in logs) or — better practice — via **OIDC**, where GitHub proves its identity to AWS and gets short-lived, scoped credentials with nothing long-lived stored at all. Either way, say this line:

> "This is the exact same principle as the PAT from this morning — scoped, not-a-plaintext-password credentials, used by a machine instead of a human. If your org uses OIDC instead of stored secrets, that's this same idea taken one step further: no stored credential at all, just a trust relationship."

## The AWS pieces, explained simply

### ECR — Elastic Container Registry
A private, managed place to store Docker images — think of it as "GitHub, but for images instead of code." The CI workflow pushes the image here after building it. Kubernetes will later *pull* the image from here when deploying.

### EKS — Elastic Kubernetes Service
First, what problem Kubernetes solves at all: once you have more than one container, running on more than one machine, you need something to decide *where* containers run, restart them if they crash, roll out new versions without downtime, and route traffic to the right one. That's an **orchestrator**. **Kubernetes** is the (now industry-standard) orchestrator. **EKS** is AWS running the complicated, stateful "control plane" part of Kubernetes for you, so your team doesn't have to operate that themselves — you still manage the worker nodes and what runs on them, but the hardest, most failure-prone part is AWS's problem.

Introduce just enough Kubernetes vocabulary to make Helm charts legible, no more:
- **Pod** — the smallest deployable unit; usually one running container (or a couple tightly coupled ones).
- **Deployment** — a declaration of "I want N copies of this pod running, using this image" — Kubernetes continuously works to make reality match that declaration.
- **Service** — a stable network address that routes to whichever pods are currently healthy, even as pods are replaced.
- **Ingress / LoadBalancer** — how traffic from outside the cluster (you, in a browser) reaches a Service.

### Helm — why templating instead of hand-written YAML per environment
Raw Kubernetes YAML for a Deployment + Service + config is verbose, and you need *slightly different values* per environment — different replica counts, different resource limits, different database endpoint, different domain name for dev vs staging vs prod. Copy-pasting near-identical YAML per environment is exactly the kind of manual, error-prone repetition this whole day has been arguing against.

**Helm** is a templating and packaging tool for Kubernetes manifests:
- A **chart** is a templated set of Kubernetes YAML files.
- A **values file** per environment (`values-dev.yaml`, `values-staging.yaml`, `values-prod.yaml`) fills in the environment-specific bits — image tag to deploy, replica count, the RDS endpoint to connect to, resource sizing.
- `helm upgrade --install myapp ./chart -f values-prod.yaml` renders the templates with prod's values and applies them to the cluster.

This is exactly your **CD repo**: same chart structure, different values file per environment, so promoting a build from dev → staging → prod means changing *which values file* (and which image tag) is used — not rewriting infrastructure.

### The two-repo pattern — say this explicitly, it's the architectural point of the day
- **CI repo** — application source + Dockerfile + the workflow that builds and pushes an image to ECR. Answers: "what code, built how."
- **CD repo** — Helm charts + per-environment values + the workflow that deploys a given image tag to a given environment on EKS. Answers: "which built image, running where, configured how."

Why split them at all?
- **Separation of concerns** — app developers work in the CI repo; changes to how/where things deploy don't require touching app code, and vice versa.
- **One image, many environments** — the *same* image tag that passed CI can be promoted through dev → staging → prod without rebuilding, which is exactly the guarantee you want ("we tested this exact artifact, not a rebuild of it that might differ").
- **Different access control** — production deploy approval can require different reviewers than application code changes.

### RDS — why the database isn't just another container in production
Callback to the question posed in module 3. Now answer it:
- **Durability & backups** — RDS handles automated backups, point-in-time recovery, and replication without you building that yourself.
- **Lifecycle independence** — pods are meant to be disposable and get replaced constantly (new deploys, node replacements, autoscaling). A database is exactly the thing you *don't* want disposable — RDS lives outside the cluster's churn, on its own managed lifecycle.
- **Managed operations** — patching, scaling storage, failover — AWS operates it, similar to how EKS means AWS operates the Kubernetes control plane.

The app pods running in EKS connect to RDS the same conceptual way the app connected to the `mysql` container in Docker Compose this afternoon — just swap a container hostname for an RDS endpoint, typically injected via a Kubernetes Secret referenced in the Helm values.

## The full picture — say this straight through once, slowly
> "Push code → GitHub Actions in the CI repo checks it out, builds it with Maven, packages it into a Docker image, pushes that image to ECR. Separately, the CD repo's workflow — triggered manually or automatically — tells Helm to deploy that specific image tag, using the values for a specific environment, to the EKS cluster. Kubernetes schedules pods running that image, a Service exposes them, and those pods connect out to RDS for the database — the same database role MySQL played locally, just managed by AWS instead of running in a container next to the app."

## Pause & ask the room
"Which parts of this did you already do by hand today, earlier?" Get them to name it out loud: Maven build (Exercise 2), Docker image (Exercise 3 built the pieces conceptually). "Right — Exercise 4 is not new concepts. It's taking every manual step from today and letting a machine do it, triggered by a `git push`."

## Common friction points to warn people about before the exercise
- IAM/credentials errors are the most common failure — if the workflow can't auth to AWS, nothing after it will work; have the exact error checklist ready.
- Image pull errors on EKS usually mean either the tag doesn't exist in ECR yet, or the cluster's node role lacks ECR pull permission.
- Helm `values-<env>.yaml` mistakes (wrong image tag, wrong RDS endpoint) are the most common "it deployed but doesn't work" cause — show people how to read `helm get values` and `kubectl describe pod` to debug this live.
- Accessing the app afterward may take a minute or two for the LoadBalancer/Ingress to provision a reachable address — don't panic if the URL isn't live instantly.

## Transition line into Exercise 4
"Let's push a commit and watch a machine do everything you did by hand today, end to end, and then open the app in a browser running on AWS."
