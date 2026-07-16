# Reading — REST API Design Best Practices

Directly relevant to [07-mini-project-plan.md](07-mini-project-plan.md) Phase 3 (validation & error handling) and every controller written from here on.

## Resource naming
- URLs name **resources** (nouns), not actions — `/employees`, not `/getEmployees` or `/createEmployee`. The HTTP method already conveys the action (below).
- Plural nouns for collections: `/employees` (collection) and `/employees/{id}` (single resource) — consistent pluralization, not a mix of `/employee/{id}` and `/employees`.
- Nesting reflects real ownership/hierarchy, not arbitrary grouping: `/departments/{deptId}/employees` (employees *belonging to* a department) is reasonable; avoid nesting more than one or two levels deep, or querying (`/employees?deptId=3`) usually reads cleaner than deep nesting.
- No verbs, no file extensions, lowercase with hyphens for multi-word paths (`/employee-records`, not `/employeeRecords` or `/Employee_Records`).

## HTTP methods and idempotency
| Method | Purpose | Idempotent? | Safe (no side effects)? |
|---|---|---|---|
| `GET` | Read a resource/collection | Yes | Yes |
| `POST` | Create a new resource (or a non-idempotent action) | No | No |
| `PUT` | Replace a resource entirely | Yes | No |
| `PATCH` | Partially update a resource | Not guaranteed (depends on the patch semantics) | No |
| `DELETE` | Remove a resource | Yes | No |

**Idempotent** means calling it once has the same end-state effect as calling it many times with the same input — important because clients (and proxies) retry failed requests, and it's only safe to blindly retry an idempotent request. `PUT /employees/5` with the same full body twice leaves the employee in the same final state either time — idempotent. `POST /employees` called twice creates **two** employees — not idempotent, which is exactly why `POST` is the wrong choice for "create if not exists, otherwise no-op" semantics (that's a `PUT`-shaped operation instead: replace-or-create at a known URL). `PATCH` idempotency depends entirely on what the patch actually says — `{"salary": 90000}` (set to an absolute value) is idempotent; `{"salary": "+5000"}` (a relative delta) is not, since applying it twice compounds.

## Status codes — the ones worth knowing cold
| Code | Meaning | When |
|---|---|---|
| `200 OK` | Success, response has a body | `GET`, successful `PUT`/`PATCH` |
| `201 Created` | Resource created | Successful `POST`, include a `Location` header pointing at the new resource |
| `204 No Content` | Success, no body | Successful `DELETE`, or a `PUT`/`PATCH` that returns nothing |
| `400 Bad Request` | Client sent malformed/invalid data | Failed `@Valid` validation, malformed JSON |
| `401 Unauthorized` | No/invalid authentication | Missing or invalid JWT |
| `403 Forbidden` | Authenticated, but not allowed | Valid JWT, wrong role (RBAC denial — [07-mini-project-plan.md](07-mini-project-plan.md) Phase 5) |
| `404 Not Found` | Resource doesn't exist | `GET/PUT/DELETE /employees/999` where 999 doesn't exist |
| `409 Conflict` | Request conflicts with current state | Duplicate unique field (e.g. email already registered) |
| `422 Unprocessable Entity` | Well-formed but semantically invalid | Some APIs use this instead of 400 for validation failures — pick one convention and be consistent |
| `500 Internal Server Error` | Unhandled server-side failure | Should be rare — anything expected should map to a more specific 4xx via `@ControllerAdvice` |

**`401` vs `403`, the distinction worth stating precisely**: `401` means "I don't know who you are" (no valid credentials at all); `403` means "I know who you are, and you're not allowed to do this" (valid credentials, insufficient permission). Returning `401` for an RBAC denial on an authenticated-but-under-privileged user is a common, easy-to-catch mistake.

## Idempotency in practice
Beyond the HTTP-method-level guarantee above, real systems sometimes need **application-level idempotency** for `POST` — e.g. a client's request times out and it retries "create employee," risking a duplicate. The common pattern: the client sends an `Idempotency-Key` header (a UUID it generates once per logical operation); the server records which keys it's already processed and, on a repeat, returns the original response instead of creating a second resource. Worth naming as a concept even if the mini project doesn't implement it initially — it's a frequent "how would you handle..." follow-up on any `POST`-heavy API.

## Pagination basics
Never return an entire table as one response once it can grow unbounded — two common approaches:
- **Offset-based** (`GET /employees?page=2&size=20`): simple, supports "jump to page N," but can skip/duplicate rows if data changes between requests (an insert before the current offset shifts everything), and gets slower on large offsets (the DB still has to scan/skip the earlier rows).
- **Cursor-based** (`GET /employees?after=<last-seen-id>&size=20`): stable under concurrent writes (each page is defined relative to a specific row, not a shifting numeric offset) and doesn't degrade on deep pagination, at the cost of not supporting "jump directly to page 50."

Response should include enough metadata for the client to know if there's more: a `hasNext`/`nextCursor` field, or a total count if offset-based and the count is cheap to compute. Spring Data's `Pageable`/`Page<T>` (used in [07-mini-project-plan.md](07-mini-project-plan.md) Phase 8) implements the offset-based approach out of the box.

---

## Interview-style self-check
- Why is `POST /employees` not idempotent, but `PUT /employees/5` is? (Different final-state guarantee: repeating `POST` creates N resources; repeating `PUT` with the same body leaves one resource in the same state.)
- Why does a duplicate-email registration attempt deserve `409`, not `400`? (`400` is for the request itself being malformed; `409` is for a well-formed request that conflicts with existing server state.)
- Why is cursor-based pagination more robust than offset-based under concurrent writes? (Offset is a shifting numeric position that concurrent inserts/deletes can invalidate; a cursor anchors to a specific row's identity, which doesn't shift.)
