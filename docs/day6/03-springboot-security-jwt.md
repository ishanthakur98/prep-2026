# Spring Boot — Spring Security + JWT

Builds on [../day2/03-springboot-core.md](../day2/03-springboot-core.md), which already placed the **Servlet Filter Chain** — explicitly naming "Spring Security filter chain" as one of the filters — *before* `DispatcherServlet` in the request pipeline, and drew the Filter-vs-Interceptor distinction (filters are container-level and run outside Spring MVC entirely; Security's filter chain is exactly why authentication must happen before a `DispatcherServlet`/controller is ever reached). Today opens up what's actually inside that filter chain, and how JWT fits into it.

## Part 1 — Concepts (read this first)

### Authentication vs Authorization
Two distinct questions, handled by two distinct mechanisms — a common interview trap is conflating them:
- **Authentication ("who are you?")** — verifying identity. Login with credentials, or presenting a token that proves "I am the user who previously logged in."
- **Authorization ("what are you allowed to do?")** — given a *known* identity, deciding whether this specific request is permitted (`@PreAuthorize("hasRole('ADMIN')")`, `hasAuthority(...)`, endpoint-level `authorizeHttpRequests` rules).

Authentication always happens first and produces an identity; authorization is a decision made *using* that identity. A `401 Unauthorized` response means authentication failed/is missing; a `403 Forbidden` means authentication succeeded but the authenticated identity isn't allowed to do this specific thing — mixing these two status codes up is a genuine, checkable bug, not just style.

### The Spring Security Filter Chain
Spring Security registers itself as **one Servlet Filter** in the container's chain (`DelegatingFilterProxy` / `FilterChainProxy`, bridging the Servlet spec's filter mechanism into Spring-managed beans) — but that one filter internally delegates through its **own ordered chain of security filters**, each with one job:
```
Request
  │
  ▼
┌───────────────────────────────────────────────────────────┐
│ Security Filter Chain (conceptually, in order)              │
│  1. CorsFilter                 -- CORS preflight/headers      │
│  2. CsrfFilter                 -- CSRF token validation        │
│  3. (Custom) JWT Auth Filter    -- extracts + validates the token│
│  4. UsernamePasswordAuthenticationFilter -- form-login path (n/a for pure JWT APIs) │
│  5. ExceptionTranslationFilter  -- converts security exceptions to 401/403 │
│  6. FilterSecurityInterceptor / AuthorizationFilter -- the actual authorize-this-request decision │
└─────────────┬─────────────────────────────────────────────┘
              ▼
      DispatcherServlet -> Controller
```
For a stateless JWT API, a **custom filter** (extending `OncePerRequestFilter`) is inserted into this chain — typically positioned *before* `UsernamePasswordAuthenticationFilter` — whose only job is: pull the token out of the `Authorization` header, validate it, and if valid, populate the `SecurityContext` (below) so every filter/interceptor/controller downstream can see "this request is authenticated as user X" without re-checking credentials.

### `SecurityContext` and `SecurityContextHolder`
`SecurityContext` holds the current `Authentication` object (principal/identity, granted authorities, and — for JWT — usually the token itself or its claims). `SecurityContextHolder` is the static access point that stores it, **per-thread by default** (a `ThreadLocal`) — which is exactly why it must be populated fresh on **every request** for a stateless API: nothing persists it across requests, unlike a session.
```java
Authentication auth = new UsernamePasswordAuthenticationToken(username, null, authorities);
SecurityContextHolder.getContext().setAuthentication(auth);
```
Once set, `@PreAuthorize`, `SecurityContextHolder.getContext().getAuthentication()` inside a controller, and `Principal` method-argument injection all read from this same place — it's the single source of truth for "who is making this request" for the remainder of that request's processing.

### JWT structure: Header, Payload, Signature
A JWT is three Base64URL-encoded segments joined by dots: `header.payload.signature`.
```
eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhbGljZSIsInJvbGUiOiJBRE1JTiJ9.dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk
└──────── header ────────┘└──────────── payload ────────────┘└──────────── signature ────────────┘
```
- **Header** — algorithm + token type, e.g. `{"alg": "HS256", "typ": "JWT"}`. Tells the verifier which algorithm to use when checking the signature.
- **Payload** — the **claims**: arbitrary key/value data about the subject — standard claims (`sub` subject, `exp` expiration, `iat` issued-at, `iss` issuer) plus app-specific ones (`role`, `email`). **Not encrypted, only encoded** — anyone holding the token can decode and read the payload (paste it into jwt.io) — this is a load-bearing fact, see below.
- **Signature** — `HMACSHA256(base64UrlEncode(header) + "." + base64UrlEncode(payload), secretKey)` (for the common symmetric case) — or an RSA/ECDSA private-key signature for asymmetric algorithms. Proves the header+payload haven't been tampered with **since** whoever holds the secret/private key signed them.

**Base64 encoding is not encryption**: this is the single most common JWT misconception. The payload is fully readable by anyone with the token (browser devtools, a proxy, a curious user) — never put secrets (passwords, raw card numbers) in a JWT payload. The signature protects **integrity** (tamper-detection), not **confidentiality**.

### Stateless authentication
Session-based auth requires the server to keep per-user session state (typically in memory or a shared store like Redis) that every subsequent request looks up by session ID. JWT-based auth is **stateless**: the token itself carries everything needed to verify identity, so the server does zero lookup — it just re-verifies the signature and reads the claims, on every request, independently. This is *why* JWT scales horizontally trivially (any server instance can validate any token without shared session storage) at the cost of harder **revocation** (see the questions below).

---

## Part 2 — Questions

**Q: Why JWT instead of sessions?**
**A:** Session-based auth needs server-side state — a session store (in-memory or shared, e.g. Redis) that every request looks up by session ID, and that store must be shared/synced across every server instance behind a load balancer (sticky sessions, or a shared session store, both add operational complexity). JWT is **self-contained and stateless**: the token itself carries the identity and claims, cryptographically signed, so *any* server instance can validate it independently with zero shared state or lookup — this is what makes JWT a natural fit for horizontally-scaled, multi-instance, or multi-service (microservices) architectures where you don't want every service round-tripping to a central session store on every request. The tradeoff (see below) is that this same statelessness makes **revoking** a single token before it naturally expires genuinely harder than just deleting a server-side session row.

**Q: What happens when a JWT arrives with a request?**
**A:** A filter positioned early in the Spring Security filter chain (a custom `OncePerRequestFilter`, before the authorization decision is made) intercepts the request, pulls the token from the `Authorization: Bearer <token>` header, and: (1) verifies the **signature** using the server's secret/public key — reject immediately (`401`) if it doesn't match, since that means either tampering or a forged token; (2) checks the **`exp`** claim against the current time — reject if expired; (3) if valid, extracts the claims (subject, roles) and builds an `Authentication` object, storing it in `SecurityContextHolder` for the rest of the request's processing. Only after that does the request continue down the chain to the authorization decision (does *this* identity have permission for *this* endpoint) and finally the controller. If the token is missing, malformed, or fails any check, the chain short-circuits to a `401` before the controller is ever reached — the controller code never even runs for an unauthenticated request.

**Q: Why is the JWT signature important?**
**A:** Without it, anyone could hand-craft a payload claiming to be any user with any role (`{"sub": "admin", "role": "ADMIN"}`) and the server would have no way to distinguish it from a legitimate token — remember, the payload is just Base64-encoded, not encrypted, so it's trivially readable *and* trivially editable by anyone. The signature is what makes the token **tamper-evident**: it's computed over the header+payload using a secret only the server (and, for symmetric HMAC, anyone else who's supposed to verify tokens) possesses, so any edit to the payload after signing produces a signature mismatch on verification. This is the entire trust mechanism of a stateless token — the server doesn't need to look anything up to trust the claims inside, it only needs to confirm the signature is valid, which is only possible if the payload is exactly what was originally signed.

**Q: JWTs are stateless and can't be looked up server-side — so how do you revoke one before it expires (e.g. on logout, or a compromised account)?**
**A:** This is the real, known weakness of pure stateless JWT and worth naming proactively. Common mitigations, each a real tradeoff: (1) **short expiration + refresh tokens** — issue a short-lived access token (minutes) plus a longer-lived refresh token that *is* checked against a server-side store, so a compromised access token has a small blast-radius window; (2) a server-side **blacklist/denylist** of revoked token IDs (`jti` claim), checked on every request — this reintroduces a stateful lookup, partially undoing JWT's statelessness benefit, but only for a small denylist rather than every session; Redis (this week's caching topic) is a natural fit for this, with TTL matching the token's own expiration. There's no free lunch here — true instant revocation and true statelessness are in direct tension, and picking the mitigation is a deliberate tradeoff decision, not a solved problem.

---

## See also
- [../day2/03-springboot-core.md](../day2/03-springboot-core.md) for where the Servlet Filter Chain (and Spring Security's place in it) sits relative to `DispatcherServlet` and Interceptors.
- [09-system-design-caching.md](09-system-design-caching.md) for Redis, referenced above as the natural store for a token denylist and for refresh-token/session-adjacent state.
- [07-mini-project-plan.md](07-mini-project-plan.md) and [../day5/07-mini-project-plan.md](../day5/07-mini-project-plan.md#part-1--stack-and-the-one-blocking-version-note), where JWT + RBAC is Phase 5 of the mini project's target stack.
