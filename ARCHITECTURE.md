# HireHub — Architecture

> Status: living document. Sections marked **(planned)** describe work not yet implemented.
> See `PROJECT_PROGRESS.md` for what exists today.

---

## 1. System overview

HireHub is a backend-first recruitment management system. It exposes a stateless JSON REST
API consumed by any client (web SPA, mobile app, Postman, curl). There is no server-rendered
UI; Swagger UI acts as the interactive client during development.

Three actors share one API surface, separated by role:

| Actor | Core job |
|---|---|
| **Candidate** | build a profile, search jobs, apply, track applications |
| **Recruiter** | own a company, publish jobs, move applicants through stages |
| **Admin** | approve companies, moderate users and jobs, read system statistics |

---

## 2. Runtime architecture

```
            ┌──────────────────────────────────────────────┐
  Client    │  SPA / mobile / curl / Swagger UI            │
            └───────────────────┬──────────────────────────┘
                                │ HTTPS, JSON, Bearer <JWT>
            ┌───────────────────▼──────────────────────────┐
            │            Spring Boot application            │
            │                                               │
            │  ┌─────────────────────────────────────────┐  │
            │  │ Security filter chain                   │  │
            │  │  CORS → JwtAuthenticationFilter →       │  │
            │  │  Authorization (@PreAuthorize / matchers)│ │
            │  └───────────────────┬─────────────────────┘  │
            │                      ▼                        │
            │  ┌─────────────────────────────────────────┐  │
            │  │ Controller layer   (REST, DTO in/out)   │  │
            │  │  - no business logic                    │  │
            │  │  - Bean Validation on request bodies    │  │
            │  └───────────────────┬─────────────────────┘  │
            │                      ▼                        │
            │  ┌─────────────────────────────────────────┐  │
            │  │ Service layer      (business rules)     │  │
            │  │  - transaction boundaries (@Transactional)│ │
            │  │  - ownership & state-machine checks     │  │
            │  │  - throws domain exceptions             │  │
            │  └───────────────────┬─────────────────────┘  │
            │                      ▼                        │
            │  ┌─────────────────────────────────────────┐  │
            │  │ Repository layer   (Spring Data JPA)    │  │
            │  │  - derived queries, @Query, Specifications│ │
            │  └───────────────────┬─────────────────────┘  │
            │                      │                        │
            │  ┌───────────────────┼─────────────────────┐  │
            │  │ Cross-cutting: @RestControllerAdvice,   │  │
            │  │ mappers, logging, OpenAPI                │  │
            │  └─────────────────────────────────────────┘  │
            └──────────────────────┼───────────────────────┘
                                   ▼ JDBC (HikariCP pool)
            ┌──────────────────────────────────────────────┐
            │                PostgreSQL 16                  │
            └──────────────────────────────────────────────┘
```

### Why a layered monolith?

**What:** a single deployable Spring Boot application with strict internal layering.

**Why:** the domain is small and highly relational (a job application touches jobs,
candidates, companies and history rows in one transaction). A monolith gives ACID
transactions for free.

**Alternative:** microservices per bounded context (auth, jobs, applications).

**Why not:** distributed transactions, network failure handling and per-service databases
would add far more operational complexity than this system's load justifies. The layering
here keeps the option open — services communicate only through interfaces, so extracting a
module later is mechanical rather than a rewrite.

---

## 3. Package structure and responsibilities

```
com.hirehub
├── HireHubApplication.java     application entry point
├── config/                     Spring @Configuration beans (OpenAPI, CORS, Jackson, audit)
├── controller/                 REST endpoints; DTO ↔ service calls only
├── dto/                        request/response records, grouped by feature
│   └── common/                 ApiResponse, ErrorResponse, PageResponse
├── entity/                     JPA entities + enums; the persistence model
├── repository/                 Spring Data JPA interfaces
├── service/                    business rules, transaction boundaries
├── security/                   JWT provider/filter, UserDetails, SecurityConfig
├── exception/                  custom exceptions + GlobalExceptionHandler
├── mapper/                     entity ↔ DTO conversion
├── specification/              dynamic JPA Specifications for search/filter
└── util/                       small stateless helpers
```

### Rules enforced across the codebase

1. **Controllers contain no business logic.** They validate input, call one service method,
   and wrap the result. If an `if` in a controller decides a business outcome, it belongs in
   a service.
2. **Entities never cross the controller boundary.** Every endpoint accepts and returns DTOs.
   This prevents accidental exposure of `passwordHash`, avoids `LazyInitializationException`
   during serialization, and decouples the API contract from the schema.
3. **Constructor injection only.** No `@Autowired` fields: dependencies are explicit, fields
   are `final`, and classes are unit-testable without a Spring context.
4. **Repositories are dumb.** They fetch and persist. Anything that decides *whether* an
   operation is allowed lives in a service.
5. **Transaction boundaries live in services**, never in controllers or repositories.

---

## 4. Request lifecycle (example: candidate applies to a job)

> Implemented in Phase 10. Corrected from this section's original Phase 0 sketch below:
> the actual code uses `InvalidStateException` (409) throughout for "well-formed request,
> wrong state to act on it", rather than the `JobClosedException`/`InvalidApplicationException`
> (422) named speculatively here in Phase 0 — see ARCHITECTURE.md's own running theme of
> adding a named exception only once something throws it, not up front. The DRAFT-visibility
> check (step 2 below) wasn't anticipated in Phase 0 either; it exists because a DRAFT job is
> invisible to candidates everywhere else in the app (`JobService.getById`), so applying to
> one has to look identical to applying to a job that doesn't exist.

```
POST /api/jobs/42/applications          Authorization: Bearer <jwt>
  │
  ├─ JwtAuthenticationFilter
  │     validates signature + expiry, loads user, populates SecurityContext
  │
  ├─ SecurityConfig / @PreAuthorize("hasRole('CANDIDATE')")
  │     rejects recruiters and admins with 403
  │
  ├─ ApplicationController
  │     @Valid on ApplyToJobRequest → 400 with field errors if invalid
  │
  ├─ ApplicationService.apply(userId, jobId, request)   @Transactional
  │     1. candidate profile complete? → 409 InvalidStateException
  │     2. load job, and is it DRAFT?  → 404 ResourceNotFoundException either way
  │     3. job accepting applications (PUBLISHED and not past its deadline)?
  │                                    → 409 InvalidStateException
  │     4. already applied?           → 409 DuplicateResourceException
  │        (also guarded by a UNIQUE constraint — see §6)
  │     5. insert application (status APPLIED)
  │     6. insert application_status_history row
  │        ── steps 5 and 6 commit together or not at all ──
  │
  ├─ ApplicationMapper → ApplicationResponse
  │
  └─ 201 Created  { "success": true, "message": "...", "data": { ... } }
```

Any exception thrown on this path is translated by `GlobalExceptionHandler`
(`@RestControllerAdvice`) into the standard error body. No stack trace ever reaches a client.

---

## 5. Security architecture **(Phases 4–5)**

> Status: authentication (Phase 4B) and the first slice of role-based authorization are
> both implemented. `@PreAuthorize` is live on `CandidateProfileController` — the pattern
> described below is no longer aspirational for that resource, and is applied to each new
> resource as it's built (jobs, applications, admin) rather than declared for all of them
> up front before they exist.

- **Stateless sessions.** `SessionCreationPolicy.STATELESS`; no server-side session store,
  so any instance can serve any request. Horizontal scaling needs no sticky sessions.
- **Passwords** hashed with BCrypt (adaptive work factor, per-password salt). Plain text is
  never stored or logged.
- **JWT claims:** `sub` (user id), `email`, `role`, `iat`, `exp`, `iss`. No PII beyond email,
  never the password hash. Signed with HS256 using a secret from `JWT_SECRET`.
- **Authorization in two layers:** coarse-grained URL rules in `SecurityConfig`
  (`/api/admin/**` → ADMIN), fine-grained `@PreAuthorize` on service/controller methods, plus
  **ownership checks inside services** — a recruiter may only touch applications belonging to
  their own company's jobs. Role alone is never sufficient for row-level access.

---

## 6. Data integrity strategy

Business rules are enforced **twice** where correctness matters:

| Rule | Application-level | Database-level |
|---|---|---|
| One application per candidate per job | service check + friendly 409 | `UNIQUE (job_id, candidate_profile_id)` |
| Unique email | service check | `UNIQUE (email)` |
| `salary_max >= salary_min` | Bean Validation | `CHECK` constraint |
| No orphan applications | cascade rules | `FOREIGN KEY` |

**Why both:** the service check gives a good error message; the constraint is the only thing
that holds under concurrency. Two simultaneous requests can both pass the "already applied?"
read before either writes. The unique index makes the second insert fail, and the service
catches `DataIntegrityViolationException` and returns the same 409. This is the standard
answer to *"how did you prevent duplicate applications?"*.

**Concurrent edits** (two recruiters updating one job, or two reviewers changing one
application status) are handled with **optimistic locking**: a `@Version` column on `Job` and
`Application`. The losing writer gets `OptimisticLockingFailureException`, translated to
`409 Conflict`. Pessimistic locking was rejected — conflicts are rare here, and row locks
would hurt read throughput.

---

## 7. Search architecture ✅

> Status: implemented. `JobSpecifications` composes the filters described below;
> `JobRepository`'s `JpaSpecificationExecutor` (added in Phase 3, specifically for this) is
> what `JobService.search` calls. The sort allow-list mentioned below is `JobService.SORTABLE_PROPERTIES`.

Job search accepts many optional filters. Two rejected approaches:

- *Dozens of derived repository methods* (`findByLocationAndEmploymentType...`) —
  combinatorial explosion, unmaintainable.
- *String-concatenated SQL* — SQL injection risk and no type safety.

**Chosen:** Spring Data JPA **Specifications** (`JpaSpecificationExecutor`). Each filter is a
small composable `Specification<Job>` predicate; only the non-null ones are `and`-ed together.
The generated SQL uses bound parameters, so injection is impossible by construction.
Pagination and sorting come from `Pageable`, which produces `LIMIT`/`OFFSET`/`ORDER BY`,
plus a `COUNT` query for total pages. Sortable fields are **allow-listed** to stop clients
sorting on unindexed or private columns.

---

## 8. Cross-cutting concerns

| Concern | Approach |
|---|---|
| Error handling | one `@RestControllerAdvice`, typed exceptions → HTTP codes |
| Validation | Jakarta Bean Validation on DTOs; `MethodArgumentNotValidException` → field-level 400 |
| Auditing | `@CreatedDate` / `@LastModifiedDate` via `@EnableJpaAuditing` on a `BaseEntity` |
| Logging | SLF4J; INFO for state changes, WARN for rejected business ops, ERROR for failures. Never logs passwords, tokens or secrets |
| Config | 12-factor: every environment-specific value comes from an env var; `.env.example` documents them |
| API docs | springdoc-openapi generates OpenAPI 3 from annotations, served at `/swagger-ui.html` |

---

## 9. Scaling path (not implemented — discussion only)

1. **Now:** single instance + PostgreSQL. Correct, simple, easily understood.
2. **~10× traffic:** run several stateless instances behind a load balancer (possible only
   because JWT auth is stateless); add read replicas for job search.
3. **~100× traffic:** Redis cache for hot job listings and Specification-heavy searches
   (cache-aside, invalidated on job update); full-text search moved to PostgreSQL GIN/trigram
   indexes or an external search engine; application events pushed to a queue for email
   notifications.
4. **Beyond:** extract the search read-model, then split services along the seams that
   already exist between packages.
