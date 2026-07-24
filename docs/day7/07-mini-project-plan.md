# Mini Project — JWT Authentication, BCrypt, Role-Based Authorization (Plan)

Still **plan-only**, same convention as [Day 5's](../day5/07-mini-project-plan.md) and [Day 6's](../day6/07-mini-project-plan.md) mini-project docs — no code goes into `src/` yet; this repo is still a plain Java/JUnit Maven project (see `pom.xml`), with real Spring Boot scaffolding not yet started. Today's instructions (JWT auth, a login endpoint, BCrypt password hashing, `USER`/`ADMIN` RBAC, securing the employee CRUD endpoints) map directly onto **Phase 5 (Authentication & RBAC)** of [Day 5's 13-phase plan](../day5/07-mini-project-plan.md#part-3--phased-build-out-still-incremental--dont-skip-ahead), and are the concrete implementation of everything [Day 6's Spring Security + JWT doc](../day6/03-springboot-security-jwt.md) already covered conceptually (filter chain, token structure, `SecurityContext`, revocation tradeoffs) — that doc is assumed background for everything below and isn't re-explained here.

## Part 1 — `User` entity, roles, and `PasswordEncoder`

```java
@Entity
public class User {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(nullable = false)
    private String passwordHash; // never store or log the raw password, ever

    @Enumerated(EnumType.STRING)
    private Role role; // USER or ADMIN -- a single role per user is enough for this project's scope
}

public enum Role { USER, ADMIN }
```
```java
@Configuration
public class PasswordConfig {
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(); // work factor 10 by default -- deliberately slow, see below
    }
}
```
**Why BCrypt specifically, not a plain hash (`SHA-256`, `MD5`)**: BCrypt is deliberately, *tunably* **slow** (its "work factor"/cost parameter controls how many internal rounds it runs) — a fast general-purpose hash like `SHA-256` is built for speed, which is exactly the wrong property for password storage, since it lets an attacker who steals the password table brute-force/rainbow-table it at billions of guesses per second on commodity hardware. BCrypt's deliberate slowness (and, unlike a fast hash, it doesn't get dramatically cheaper to brute-force as hardware improves, since the cost factor can simply be raised) makes large-scale offline guessing genuinely impractical instead of merely inconvenient. It also **automatically salts** every hash (a random salt baked into its own output string, `$2a$10$<salt><hash>`), so two users with the same password never produce the same stored hash — defeating precomputed rainbow-table attacks without the application needing to manage salts itself.
```java
String hash = passwordEncoder.encode(rawPassword);          // at registration
boolean matches = passwordEncoder.matches(rawPassword, hash); // at login -- never decode/compare hashes directly
```
`matches()`, not manual re-hashing and `.equals()`, because BCrypt's own comparison correctly extracts the salt embedded in the stored hash and re-runs the same algorithm — implementing this by hand is an easy way to introduce a timing side-channel or a salt-handling bug.

## Part 2 — Login endpoint and JWT issuance

```java
public record LoginRequest(@NotBlank String username, @NotBlank String password) {}
public record LoginResponse(String token, String username, Role role) {}
```
```java
@PostMapping("/auth/login")
public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
    User user = userRepository.findByUsername(request.username())
            .orElseThrow(() -> new BadCredentialsException("Invalid username or password"));

    if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
        throw new BadCredentialsException("Invalid username or password"); // same message as "user not found" -- see below
    }

    String token = jwtProvider.generateToken(user.getUsername(), user.getRole());
    return ResponseEntity.ok(ApiResponse.success(new LoginResponse(token, user.getUsername(), user.getRole())));
}
```
**Why the exact same error message and status for "no such user" and "wrong password"**: returning a different message for each (`"user not found"` vs `"wrong password"`) leaks which usernames actually exist in the system to an unauthenticated caller — a **username enumeration** vulnerability, letting an attacker script through a list of candidate usernames and learn which ones are valid accounts before even attempting to guess a password. A single generic `401` + `"Invalid username or password"` for both cases closes that leak; this is caught by [`GlobalExceptionHandler`](../day6/07-mini-project-plan.md#global-exception-handling--controlleradvice) mapping `BadCredentialsException` to `401`.

**Token generation**:
```java
@Component
public class JwtProvider {
    @Value("${jwt.secret}")           // never hardcode -- injected from config/environment
    private String secret;
    @Value("${jwt.expiration-ms:900000}") // 15 minutes default -- short-lived, see the revocation tradeoff
    private long expirationMs;

    public String generateToken(String username, Role role) {
        Date now = new Date();
        return Jwts.builder()
                .setSubject(username)
                .claim("role", role.name())
                .setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + expirationMs))
                .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS256)
                .compact();
    }
}
```
Directly implements the token structure [Day 6's doc](../day6/03-springboot-security-jwt.md#jwt-structure-header-payload-signature) described: `sub` = username, a custom `role` claim (what the RBAC checks below actually read), `iat`/`exp` for issued-at/expiration, HMAC-SHA256 signed with a secret injected via config — never a literal string in source, since anyone with the secret can forge arbitrarily-privileged tokens. **Short expiration (15 min) by design**: this project's concrete instance of the short-expiry-plus-refresh-token mitigation [Day 6's doc named](../day6/03-springboot-security-jwt.md#q-jwts-are-stateless-and-cant-be-looked-up-server-side--so-how-do-you-revoke-one-before-it-expires-eg-on-logout-or-a-compromised-account) for JWT's revocation weakness — a refresh-token endpoint is a reasonable Phase 5 stretch goal but not required for the core flow to be interview-demonstrable.

## Part 3 — RBAC: securing the employee CRUD endpoints

```java
@Configuration
@EnableMethodSecurity // enables @PreAuthorize below
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtAuthFilter jwtAuthFilter) throws Exception {
        return http
            .csrf(AbstractHttpConfigurer::disable) // stateless token API -- no cookie-based session to protect against CSRF
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/auth/**").permitAll()          // login/register: no token required yet
                .requestMatchers(HttpMethod.GET, "/employees/**").authenticated()   // any logged-in user can read
                .anyRequest().hasRole("ADMIN")                    // create/update/delete: ADMIN only
            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class) // the filter Day 6 described
            .build();
    }
}
```
```java
@RestController
@RequestMapping("/employees")
public class EmployeeController {

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<EmployeeResponse>> get(@PathVariable Long id) { /* any authenticated user */ }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')") // defense in depth -- redundant with the filter-chain rule above, deliberately
    public ResponseEntity<ApiResponse<EmployeeResponse>> create(@Valid @RequestBody CreateEmployeeRequest request) { ... }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) { ... }
}
```
**Read vs. write policy, decided explicitly (per Day 5's plan's instruction to justify this, not default into it)**: any authenticated user (`USER` or `ADMIN`) can **read** employee data — viewing the directory is a legitimate need for any logged-in employee — but only `ADMIN` can **create/update/delete**, since those are HR-administrative actions with real consequences (payroll-adjacent data, org-chart changes). This mirrors a very common real-world RBAC shape: broad read access, narrow write access.

**Why both the filter-chain rule (`anyRequest().hasRole("ADMIN")`) *and* `@PreAuthorize` on the same endpoints, rather than picking one**: the `SecurityFilterChain` rule is coarse and URL-pattern-based, evaluated early (before `DispatcherServlet`, per [Day 6's filter-chain diagram](../day6/03-springboot-security-jwt.md#the-spring-security-filter-chain)) — cheap, and a strong first line of defense. `@PreAuthorize` is method-level and evaluated via AOP proxy (same proxy mechanism as [today's `@Transactional` doc](03-springboot-transactions.md#the-proxy-mechanism--how-transactional-is-actually-implemented)), giving finer-grained control (can express conditions the URL pattern alone can't, e.g. `hasRole('ADMIN') or #id == authentication.principal.id`) and, critically, **still protects the method if it's ever called from other Java code that bypasses the HTTP layer entirely** (an internal service call, a future batch job) — the filter-chain rule only ever applies to requests that actually go through the servlet filter chain in the first place.

## Part 4 — What this doesn't change

Still the design for **Phase 5 of Day 5's already-decided target architecture** — layered `controller/service/repository/entity/dto/exception/security` packages ([Day 5's Part 4](../day5/07-mini-project-plan.md#part-4--package-structure-for-phase-1s-scaffold) already reserved a `security` package for exactly this), Postgres via Spring Data JPA. Nothing here revises that; it's the concrete shape Phase 5 takes once Phases 1–4 (scaffold, CRUD, validation, OpenAPI) exist to secure.

## Part 5 — Weekend assignment tie-in

This weekend's assignment items are each a later phase of the same 13-phase plan, worth naming explicitly so they aren't mistaken for new, unplanned scope:
- **Pagination + sorting + search-by-name** → Phase 8 (`Pageable` list endpoints, `Specification`/`@Query`-driven filtering) — layers directly on top of the now-secured `GET /employees` endpoint from Part 3 above.
- **Connect the React frontend via Axios** → Phase 9 — the [Employee Details page built today](05-react-lifecycle.md#part-2--build-employee-details-page-with-react-router) and the [Day 5 dashboard](../day5/05-react-hooks.md#part-2--build-employee-dashboard-with-data-loading) both currently call a *mock* `api.js`; swapping `fetch`/mock promises for real Axios calls against this week's (still-to-be-scaffolded) Spring Boot backend is the same component tree, a different data layer — including sending the JWT from Part 2 as an `Authorization: Bearer` header on every request, and handling a `401` by redirecting to a login screen.
- **Dockerize both frontend and backend with Docker Compose** → Phase 11 (`docker compose up` bringing up the full stack), pulled forward here specifically for the frontend+backend pair, ahead of Postgres/Redis/Kafka joining the same Compose file once those phases exist.

None of this requires new design decisions — it's the same target architecture, tackled in the order the phased plan already lays out.
