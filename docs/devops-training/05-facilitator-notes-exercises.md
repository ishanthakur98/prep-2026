# Facilitator Notes — The 4 Exercises

You already have the detailed lab steps for each exercise written up separately. This file is what to **say before** each one starts, what "done" looks like, and what tends to go wrong — so you're not troubleshooting cold when 15 people raise their hands at once.

---

## Exercise 1 — Fork the repo + set up a PAT

**Frame it (30 seconds, before people start):**
"You're about to get your own copy of the SailPoint IIQ training repo, and set up a credential so Git actually lets you push changes to it. This is the foundation everything else today builds on — nothing later works without this."

**What "done" looks like:**
- Attendee has their own fork visible under their GitHub account.
- Attendee has cloned *their fork* (not the original) locally.
- A PAT exists, scoped appropriately, and `git push`/`git pull` works against their fork using it.

**Common failures & fast fixes:**
- Cloned the original repo instead of their fork → wrong remote, pushes will fail with a permissions error. Fix: check `git remote -v` output, should point to their own username.
- PAT scope too narrow (e.g., missing `repo` scope) → push fails with 403. Fix: regenerate with correct scope.
- Using the PAT as the *username* instead of the password field, or vice versa, in credential prompts — a very common mixup. Have the exact expected prompt behavior for your Git client (CLI vs. GitHub Desktop vs. IDE) written down.
- Old cached credentials override the new PAT → confusing "wrong password" errors. Fix: clear the credential manager/cache entry for github.com.

---

## Exercise 2 — Set up the Maven project locally and build it

**Frame it:**
"Now that you have the code, let's turn it into something runnable — this is the exact same command that'll run inside Docker and inside CI later today, so getting comfortable with it now pays off twice more."

**What "done" looks like:**
- `mvn -version` runs without error and shows a compatible JDK.
- `mvn package` (or whatever the project's actual goal is) completes with `BUILD SUCCESS`.
- A `.war` (or relevant artifact) exists under `target/`.

**Common failures & fast fixes:**
- Wrong JDK version on PATH → cryptic compiler errors. Fix: check `java -version` matches what the `pom.xml` expects before diagnosing anything else.
- First build hangs — it's downloading the internet's worth of dependencies. Fix: reassure people this is normal, have a pre-warmed `.m2` cache on a USB/shared drive as a fallback if venue Wi-Fi is weak.
- `BUILD FAILURE` from a failing test — good teaching moment, not just a blocker: point out this is CI's `test` gate happening locally, exactly as module 4 will describe.
- Corporate proxy blocks Maven Central — have `settings.xml` proxy config ready if this is a known issue on your network.

---

## Exercise 3 — Run it locally with Docker + Docker Compose (app + MySQL)

**Frame it:**
"You've got a built artifact. Now let's wrap it and a database together so it runs identically for everyone in this room, without anyone installing MySQL by hand."

**What "done" looks like:**
- `docker compose up` brings up both `app` and `mysql` containers without errors.
- The app is reachable in a browser at `localhost:8080` (or whatever port is configured).
- The app can actually reach the database (login page loads, not a DB connection error).

**Common failures & fast fixes:**
- Port already in use (8080 or 3306) — common on laptops with existing local dev tools. Fix: change the host-side port mapping in `docker-compose.yml`, e.g. `8081:8080`.
- App container starts before MySQL is actually ready to accept connections (not just "started") → connection refused on first boot. Fix: either a retry/wait-for-it pattern in the app's startup, or just restart the `app` service once MySQL is confirmed healthy — good moment to mention this is a known Compose gotcha (`depends_on` ≠ "wait until ready").
- Out of disk space or memory in Docker Desktop → containers crash unpredictably. Fix: check Docker Desktop resource settings.
- Stale image cached from an earlier attempt → confusing "my fix isn't showing up" reports. Fix: `docker compose build --no-cache` or `docker compose down -v` to reset volumes too.

---

## Exercise 4 — GitHub Action deploys to EKS, then access the app

**Frame it:**
"This is everything from today, automated. Push a commit, and a machine will build it with Maven, containerize it with Docker, push the image to ECR, and deploy it to our EKS cluster via Helm — the same chart structure module 4 described. Then we'll open the actual running app in a browser."

**What "done" looks like:**
- The GitHub Actions workflow run shows green across all steps (build, push to ECR, deploy).
- `kubectl get pods` (or the equivalent your facilitator team runs) shows the new pod(s) `Running`, not `CrashLoopBackOff` or `ImagePullBackOff`.
- The app is reachable via the cluster's LoadBalancer/Ingress URL in a browser, and can reach RDS (login page loads, not a DB connection error — mirrors the Exercise 3 checkpoint, just in the cloud now).

**Common failures & fast fixes:**
- AWS auth failure in the workflow (IAM role/OIDC misconfigured, or expired/missing secret) → fails at the "log in to ECR" or "deploy" step. This is the single most common failure point — check this first, always.
- Image tag mismatch between what CI pushed and what the Helm values reference → `ImagePullBackOff`. Fix: confirm the tag in `values-<env>.yaml` matches what was actually pushed to ECR (`aws ecr list-images` or the ECR console).
- Pod stuck `Pending` → usually insufficient node capacity in the shared training cluster if many attendees deploy simultaneously. Have a plan (dedicated namespace/resource limits per attendee, or stagger deploys) decided *before* the session.
- Pod `Running` but app unreachable → check Service/Ingress is correctly pointing at the pod's labels, and that the LoadBalancer has finished provisioning (can take 1-2 minutes on first creation).
- App loads but errors on login/DB action → RDS endpoint or credentials wrong in the Helm values/secret for that environment. Fix: `kubectl describe pod` and `kubectl logs` on the app pod to see the actual connection error.

**Since this is likely a shared training AWS account/cluster:** decide in advance whether each attendee deploys to their own namespace (recommended, avoids collisions) or a shared one, and have that convention written into the exercise instructions, not decided live.

---

## General facilitation tips for the day
- Keep a "parking lot" list (physical whiteboard or shared doc) for good questions that go deeper than the day allows — Kubernetes networking details, Helm hooks, multi-stage Dockerfiles, blue/green deploys — and address them at the very end if time allows, or offer to follow up.
- When something breaks live (it will), narrate your debugging process out loud — "here's how I'd figure out what's wrong" is often more valuable to a DevOps newcomer than the fix itself.
- Have one working, known-good environment (your own) to demo from if an attendee's environment is stuck and you don't want to block the room while fixing one person's laptop.
