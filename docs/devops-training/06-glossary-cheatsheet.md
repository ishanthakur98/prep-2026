# Glossary / One-Page Cheat Sheet

Hand this out at the start or end — useful for attendees to skim during Q&A or take away afterward.

## Version control
- **Git** — tool that tracks changes to code over time, runs locally.
- **GitHub** — hosted service built on Git; adds collaboration (pull requests, code review) and automation (Actions).
- **Fork** — your own copy of someone else's repo, under your account.
- **Clone** — downloading a copy of a repo to your machine.
- **PAT (Personal Access Token)** — a scoped, revocable credential used instead of a password for Git/API operations.
- **Pull Request (PR)** — a proposed set of changes, reviewed before merging into the main codebase.

## Build tools
- **Maven** — a Java build tool: manages dependencies and runs a standard build lifecycle.
- **`pom.xml`** — Maven's project config file: dependencies, plugins, build settings.
- **Build lifecycle** — the ordered phases Maven runs: validate → compile → test → package → verify → install → deploy.
- **Dependency** — an external library your code needs; Maven resolves and downloads these automatically.
- **Artifact** — the packaged output of a build (e.g., a `.war` or `.jar` file).

## Containerization
- **Container** — a lightweight, isolated unit that packages an app with everything it needs to run, sharing the host OS kernel.
- **Image** — the read-only, versioned template a container is started from.
- **Dockerfile** — the text file defining how to build an image, step by step.
- **Docker Compose** — a tool/YAML format for running multiple related containers together (e.g., app + database) with shared networking.
- **Layer** — a cached step in building a Docker image; unchanged layers are reused for faster rebuilds.

## CI/CD
- **CI (Continuous Integration)** — automatically building and testing code every time it's pushed.
- **CD (Continuous Delivery/Deployment)** — automatically preparing (Delivery) or actually shipping (Deployment) code that has passed CI into an environment.
- **GitHub Actions** — GitHub's built-in automation engine; workflows defined in YAML, triggered by repo events.
- **Workflow** — a YAML file in `.github/workflows/` defining jobs/steps to run automatically.
- **Secret** — an encrypted credential stored in GitHub, used by workflows without exposing it in logs or code.
- **OIDC (OpenID Connect)** — a way for GitHub Actions to prove its identity to AWS and get short-lived credentials, without storing long-lived secrets at all.

## AWS / Kubernetes
- **ECR (Elastic Container Registry)** — AWS's private storage for Docker images.
- **EKS (Elastic Kubernetes Service)** — AWS's managed Kubernetes; AWS runs the control plane, you manage what runs on it.
- **Kubernetes** — an orchestrator: decides where containers run, restarts failed ones, handles rolling updates and traffic routing.
- **Pod** — the smallest deployable unit in Kubernetes; usually one running container.
- **Deployment** — a Kubernetes object declaring "run N copies of this pod using this image."
- **Service** — a stable network address routing to healthy pods, even as they're replaced.
- **Ingress / LoadBalancer** — how external traffic reaches a Service from outside the cluster.
- **Helm** — a templating/packaging tool for Kubernetes manifests; a "chart" plus per-environment "values" files.
- **Chart** — a Helm package of templated Kubernetes YAML.
- **Values file** — environment-specific settings (image tag, replica count, DB endpoint, etc.) filled into a Helm chart.
- **RDS (Relational Database Service)** — AWS's managed database service; handles backups, patching, failover outside the Kubernetes cluster's lifecycle.

## The pipeline, in one sentence
Push code (**GitHub**) → **CI** builds it (**Maven**) and packages it (**Docker**) and pushes the image to **ECR** → **CD** deploys that image via **Helm** to **EKS**, per environment → the running app connects to **RDS**.

## The two-repo pattern
- **CI repo** — app source, Dockerfile, build workflow → answers "what code, built how."
- **CD repo** — Helm charts, per-env values, deploy workflow → answers "which image, running where, configured how."
