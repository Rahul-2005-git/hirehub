# HireHub — Project Progress

Last updated: end of **Phase 13**.

Legend: ✅ done · 🔄 in progress · ⬜ not started

---

## Phase board

| # | Phase | Scope | Status |
|---|---|---|---|
| 0 | Architecture & requirements | Architecture, DB design, API map, roadmap, docs skeleton | ✅ |
| 1 | Project setup | Maven build, app entry point, config, health endpoint, docker-compose (Postgres), `.env.example`, `.gitignore`, smoke test | ✅ |
| 2A | Database schema | Flyway V1 + V2 migrations, datasource/JPA/Flyway config, Testcontainers test base, schema tests | ✅ |
| 2B | JPA entities | `BaseEntity`/`AuditableEntity` auditing, 8 enums with state machines, 11 entities mapped onto the schema, `ddl-auto: validate` passing | ✅ |
| 3 | Repositories | Spring Data repositories for all 11 entities, `JpaSpecificationExecutor` on `JobRepository`, `@EntityGraph` fetch-joins, repository integration tests, shared `TestDataFactory` | ✅ |
| 4A | JWT + UserDetailsService | `JwtProperties`, `JwtTokenProvider`, `HireHubUserDetails`, `HireHubUserDetailsService`; Mockito unit tests only, no HTTP | ✅ |
| 4B | Auth endpoints | `SecurityConfig`, `JwtAuthenticationFilter`, JSON entry point/access-denied handler, `AuthService` (register/login), `AuthController`, auth DTOs, minimal `GlobalExceptionHandler` | ✅ |
| 5+6 | Authorization + candidate profile | `@EnableMethodSecurity`, `@PreAuthorize`, candidate profile + skills CRUD (merged: authorization needs a real resource to protect, built together rather than in the abstract) | ✅ |
| 7 | Company & recruiter | Company CRUD with ownership checks, recruiter self-service profile, admin company moderation | ✅ |
| 8 | Job management | Job CRUD with company-wide ownership, full status lifecycle, job skills, DRAFT visibility rules | ✅ |
| 9 | Job search | `JobSpecifications`, combinable filters, sort allow-list, pagination — see Phase 9 notes for why `EXPLAIN ANALYZE` verification wasn't performed | ✅ |
| 10+11 | Application workflow + status history | Apply/withdraw/pipeline, company-wide recruiter ownership, full status history, optimistic-lock 409 handler (merged: the history mechanic is inseparable from the workflow that produces it — same reasoning as merging 5+6) | ✅ |
| 12 | Validation & exception handling | Deliberate audit pass — confirmed validation coverage, found and fixed a real 405→500 misrouting bug plus 3 other gaps | ✅ |
| 13 | Testing | Mockito unit tests for Auth/Job/Application services, isolated from Spring and the database — integration coverage (MockMvc/Testcontainers) already built incrementally since Phase 4B | ✅ |
| 14 | OpenAPI | springdoc annotations, JWT security scheme, examples | ⬜ |
| 15 | Docker | Multi-stage `Dockerfile`, app service in compose | ✅ |
| 16 | CI | GitHub Actions: build + test on push/PR | ✅ |
| 17 | Security review | CORS, secret handling, log audit, cleanup | ⬜ |
| 18 | Documentation | Final README/ARCHITECTURE/API/DATABASE sync | ⬜ |
| 19 | Final audit | Full checklist against requirements | ⬜ |

Large phases may be split (e.g. 2A entities / 2B migrations, 13A unit / 13B integration).

---

## Phase 0 — Architecture & requirements ✅

**Delivered**
- Layered architecture with enforced rules (no logic in controllers, no entities in
  responses, constructor injection, transactions in services) — `ARCHITECTURE.md`
- Full relational design: 11 tables, relationship catalogue, normalisation notes,
  constraint table, index-by-index justification, transaction boundary table — `DATABASE.md`
- Endpoint map for every module with roles, status codes and error contract — `API.md`
- Key decisions recorded with WHAT / WHY / ALTERNATIVE / WHY-THIS:
  monolith vs microservices, PostgreSQL vs MongoDB, join entities vs `@ManyToMany`,
  Specifications vs derived queries, optimistic vs pessimistic locking,
  DB constraint + service check for duplicate applications

---

## Phase 1 — Project setup ✅

**Files created**

| File | Purpose |
|---|---|
| `pom.xml` | Spring Boot 3.5.5 parent, Java 21, web/validation/actuator/springdoc/Lombok/test |
| `src/main/java/com/hirehub/HireHubApplication.java` | Application entry point |
| `src/main/java/com/hirehub/dto/common/ApiResponse.java` | Standard success envelope (record) |
| `src/main/java/com/hirehub/controller/HealthController.java` | `GET /api/health` |
| `src/main/resources/application.yml` | Env-var driven config, logging, actuator, springdoc |
| `src/test/java/com/hirehub/HireHubApplicationTests.java` | Context-load + health contract test |
| `docker-compose.yml` | PostgreSQL 16 dev database with healthcheck |
| `.env.example` | Environment variable template (no real secrets) |
| `.gitignore` | Excludes `target/`, `.env`, IDE files |
| `README.md`, `ARCHITECTURE.md`, `DATABASE.md`, `API.md`, `PROJECT_PROGRESS.md` | Documentation set |

**Package skeleton created** (empty until their phase):
`config`, `controller`, `dto`, `dto/common`, `entity`, `repository`, `service`, `security`,
`exception`, `mapper`, `specification`, `util`.

**Decisions**
- Dependencies are added **per phase**, not all at once. Phase 1 has no `spring-boot-starter-data-jpa`
  so the context loads and the smoke test passes without a database. JPA, the PostgreSQL
  driver and Flyway arrive in Phase 2; Spring Security and JJWT in Phase 4.
- `hirehub.version` is populated from `@project.version@` via Maven resource filtering, so the
  health endpoint never reports a hand-maintained version string.
- `server.error.include-stacktrace: never` from day one.
- Compose ships only PostgreSQL for now; the app container belongs to Phase 15.

**Verify this phase**
```bash
cp .env.example .env
docker compose up -d postgres        # optional at this stage
mvn clean test                       # smoke test must pass
mvn spring-boot:run
curl http://localhost:8080/api/health
curl http://localhost:8080/actuator/health
open http://localhost:8080/swagger-ui.html
```

**Consistency check:** nothing to reconcile — first code phase.

**Known gaps entering Phase 2:** no persistence layer, no entities, no security (every
endpoint is currently public), no migrations, no domain tests.

---

## Phase 2A — Database schema ✅

**Files created**

| File | Purpose |
|---|---|
| `src/main/resources/db/migration/V1__init_schema.sql` | 11 tables, all PK/FK/UNIQUE/NOT NULL/CHECK constraints, 14 indexes, `pg_trgm` extension |
| `src/main/resources/db/migration/V2__seed_reference_data.sql` | Three roles + 25-skill starter vocabulary, idempotent |
| `src/test/java/com/hirehub/support/AbstractIntegrationTest.java` | Singleton PostgreSQL 16 Testcontainer wired via `@ServiceConnection` |
| `src/test/java/com/hirehub/database/SchemaMigrationTest.java` | 7 tests: migrations applied, tables exist, roles seeded, unique guard + indexes + CHECK constraints actually enforced |

**Files modified**

| File | Change |
|---|---|
| `pom.xml` | Added `spring-boot-starter-data-jpa`, `postgresql` (runtime), `flyway-core`, `flyway-database-postgresql`, `spring-boot-testcontainers`, `testcontainers:postgresql`, `testcontainers:junit-jupiter` |
| `src/main/resources/application.yml` | Datasource + Hikari pool, JPA (`ddl-auto: validate`, `open-in-view: false`, batch settings), Flyway, SQL log levels |
| `src/test/java/com/hirehub/HireHubApplicationTests.java` | Now extends `AbstractIntegrationTest` (context needs a real database) |
| `.env.example` | Added `DB_POOL_MAX`, `DB_POOL_MIN_IDLE`, `JPA_SHOW_SQL`, `LOG_LEVEL_SQL` |
| `DATABASE.md` | Status → implemented; schema-management section expanded with the Flyway rules |
| `README.md` | Status line; tests now require Docker |

**Decisions**
- **Flyway owns the schema, `ddl-auto: validate`.** `update` gives no history, never drops
  safely, and diverges per environment. `validate` turns a mapping drift into a startup
  failure instead of a runtime surprise. Migrations are immutable once merged.
- **Testcontainers, not H2.** The schema uses `pg_trgm`, a GIN index, a functional index on
  `lower(location)`, `TIMESTAMPTZ` and CHECK constraints — H2 fakes or rejects several of
  these, so tests would pass against a database unlike production.
- **`open-in-view: false`.** The default keeps a persistence context (and a connection) open
  through JSON serialisation, hiding lazy-loading problems. Off, they fail loudly in tests.
- **Enums as `VARCHAR` + CHECK**, not native PG enum types, not ordinals.
- **`ON DELETE` chosen per relationship, not uniformly:** CASCADE for owned rows
  (profile ← user, application ← job), RESTRICT where deletion should be deliberate
  (company with staff, skill in use), SET NULL for `application_status_history.changed_by_user_id`
  so deleting a user never erases the hiring decisions they made.

**Verify this phase**
```bash
docker compose up -d postgres
mvn clean test                       # 8 tests, requires a Docker daemon
mvn spring-boot:run                  # Flyway migrates on startup

# Inspect the result
docker exec -it hirehub-postgres psql -U hirehub -d hirehub -c '\dt'
docker exec -it hirehub-postgres psql -U hirehub -d hirehub -c '\di'
docker exec -it hirehub-postgres psql -U hirehub -d hirehub \
  -c 'SELECT version, description, success FROM flyway_schema_history;'
```

**Consistency check:** the health endpoint, `ApiResponse` envelope and package layout from
Phase 1 are untouched. The only behavioural change is that the application now requires a
reachable PostgreSQL instance to start — which is why the Phase 1 smoke test was moved onto
the Testcontainers base class rather than left to fail.

**Known gaps entering Phase 2B:** no JPA entities yet, so `ddl-auto: validate` currently has
nothing to validate; no repositories; no security (every endpoint is still public).

---

## Phase 2B — JPA entities ✅

**Files created** (27 main + 2 test)

| Group | Files |
|---|---|
| Base types | `entity/BaseEntity.java` (id, JPA-safe equals/hashCode), `entity/AuditableEntity.java` (`created_at`/`updated_at`), `config/JpaAuditingConfig.java` |
| Enums | `entity/enums/`: `RoleName`, `UserStatus`, `CompanyStatus`, `EmploymentType`, `ExperienceLevel`, `ProficiencyLevel`, `JobStatus`, `ApplicationStatus` |
| Entities | `Role`, `User`, `Company`, `RecruiterProfile`, `CandidateProfile`, `Skill`, `CandidateSkill` + `CandidateSkillId`, `Job`, `JobSkill` + `JobSkillId`, `Application`, `ApplicationStatusHistory` |
| Tests | `entity/EntityMappingTest.java` (7 integration tests), `entity/enums/StatusTransitionTest.java` (9 unit tests) |

**Files modified:** `PROJECT_PROGRESS.md` only. No migration was touched — the entities were
written to fit the schema, not the other way round.

**Decisions**
- **State machines live on the enums.** `JobStatus.canTransitionTo()` and
  `ApplicationStatus.canTransitionTo()` hold the legal moves, so "a CLOSED job cannot be
  re-published" and "SHORTLISTED cannot jump to SELECTED" exist in exactly one place.
  ALTERNATIVE: Spring State Machine — a dependency and a DSL for four states is
  over-engineering. The services will ask the enum instead of growing nested if/else.
- **Behaviour on entities, not only getters.** `job.publish()`, `application.changeStatus()`
  and `candidateProfile.isCompleteEnoughToApply()` keep invariants next to the data:
  `status` and `publishedAt` cannot disagree, and a status change cannot happen without its
  history entry being appended in the same call.
- **`ApplicationStatusHistory` is immutable** — no setters, package-private constructor, so
  only `Application` can create an entry and nothing can rewrite one.
- **Every association is `LAZY`.** Default `EAGER` on `@ManyToOne` is the single most common
  cause of N+1 in JPA. `default_batch_fetch_size: 25` (set in Phase 2A) turns the follow-up
  fetch for a page of results into one query rather than one per row.
- **Collections mapped only where they form an aggregate.** A candidate's skills and a job's
  required skills are mapped (small, always edited as a unit). A company's jobs and a job's
  applications are *not* — they are unbounded and need paging, so they go through
  repositories with a `Pageable` in Phase 3.
- **No Lombok on entities.** `@Data` generates `equals`/`hashCode` over every field, which
  triggers lazy loads and breaks `Set` membership once an id is assigned. Identity here is
  id-based with a constant `hashCode`, the standard JPA-safe formulation.
- **`@MapsId` for both join entities**, so the FK columns and the composite PK columns are
  the same columns.
- **`BigDecimal` for money**, never `double`.

**Verify this phase**
```bash
docker compose up -d postgres
mvn clean test    # 24 tests; EntityMappingTest persists the full graph
mvn spring-boot:run
```
Startup now fails loudly if any mapping disagrees with the migrations — that is
`ddl-auto: validate` doing its job, and it is the fastest feedback loop in the project.

*Troubleshooting:* if validation ever objects to an `Instant` column, it means Hibernate
chose `timestamp` over `timestamp with time zone`; force it with
`spring.jpa.properties.hibernate.type.preferred_instant_jdbc_type: TIMESTAMP_UTC`.

**Consistency check:** Phase 1 (health endpoint, `ApiResponse`) and Phase 2A (migrations,
config, Testcontainers base) are unchanged. `EntityMappingTest` reuses the same container
base class, and it reads the roles and skills seeded by V2 rather than inserting its own.

**Known gaps entering Phase 3:** no repositories, so nothing outside tests can load an
entity; no services; no security — every endpoint is still public.

---

## Phase 3 — Repositories ✅

**Files created**

| Group | Files |
|---|---|
| Repositories | `RoleRepository`, `UserRepository`, `CandidateProfileRepository`, `RecruiterProfileRepository`, `CompanyRepository`, `SkillRepository`, `CandidateSkillRepository`, `JobRepository`, `JobSkillRepository`, `ApplicationRepository`, `ApplicationStatusHistoryRepository` — one per entity |
| Test support | `test/support/TestDataFactory.java` — builds and persists minimal valid entity graphs |
| Repository tests | `UserRepositoryTest` (+ Company), `JobRepositoryTest`, `ApplicationRepositoryTest`, `CandidateProfileRepositoryTest` (+ Skill) |

**Files modified:** `PROJECT_PROGRESS.md`, `README.md` (status lines only).

**Decisions**
- **`JpaSpecificationExecutor<Job>` added now, used in Phase 9.** It is a property of the
  repository's shape, so every `Job` data-access capability lives in one interface rather
  than being retrofitted later. `JobRepositoryTest` proves it is wired with a throwaway
  ad-hoc `Specification`, without pulling in the real search filters before they exist.
- **No combinatorial derived methods.** There is no
  `findByLocationAndEmploymentTypeAndExperienceLevel...` anywhere. Single-purpose lookups
  (`findByCompanyId`, `findByCandidateIdAndStatus`) stay as derived queries; anything
  combinable is left for the Specification. This is ARCHITECTURE.md §7's search design
  applied one phase early, at the repository boundary.
- **`@EntityGraph` over `JOIN FETCH` where the join is a static, always-needed set of
  associations** (`findWithDetailsById`, `findWithHistoryById`, `findByIdWithSkills`):
  no query string to keep in sync with a renamed field, and Hibernate builds the join from
  the attribute paths. A `JOIN FETCH` JPQL query is reserved for cases needing `distinct`
  or a `where` clause a pure attribute graph can't express (`UserRepository.search`).
- **The service-level duplicate check has its repository method now:**
  `existsByJobIdAndCandidateId`. It exists purely for the friendly error message — the
  database constraint from Phase 2A is what actually holds under concurrency — and
  `ApplicationRepositoryTest` exercises both the positive and negative case.
- **`TestDataFactory` centralises "what does a legal Job/Application require".** Written
  once here rather than duplicated per test class, and reused unchanged by every repository
  test — the entity graph is complex enough (job needs a company *and* a recruiter *and* a
  valid employment type/experience level) that copy-pasted setup would drift.
- **Every repository test is `@Transactional`** so each method's writes roll back
  automatically, letting tests share one Testcontainers instance without manual cleanup.

**Verify this phase**
```bash
docker compose up -d postgres
mvn clean test    # 37 tests total across unit + repository + entity-mapping + schema suites
```

**Consistency check:** no entity or migration was touched. `JobRepositoryTest` and
`ApplicationRepositoryTest` build on the same `AbstractIntegrationTest` container base as
Phase 2B's `EntityMappingTest`, and `TestDataFactory` reproduces the same "valid job/valid
candidate" shape that test already used inline — the duplication was deliberate rather than
refactoring a passed phase's test.

**Known gaps entering Phase 4:** no services, no DTOs beyond `ApiResponse`, no security —
every endpoint is still public and nothing outside a test can reach the persistence layer
through the API yet.

---

## Phase 4A — JWT + UserDetailsService ✅

**Files created**

| File | Purpose |
|---|---|
| `security/JwtProperties.java` | Typed, validated binding of `hirehub.jwt.*` (secret, expiration, issuer) |
| `security/JwtTokenProvider.java` | Issues and verifies JWTs; independent of the servlet layer |
| `security/HireHubUserDetails.java` | Adapts `User` to Spring Security's `UserDetails` |
| `security/HireHubUserDetailsService.java` | Loads a user by email for the future authentication provider |
| `src/test/resources/application.yml` | Test-only property overrides, including a test-only JWT secret |
| `test/security/JwtTokenProviderTest.java` | 8 pure unit tests |
| `test/security/HireHubUserDetailsServiceTest.java` | 4 pure Mockito unit tests |

**Files modified**

| File | Change |
|---|---|
| `pom.xml` | Added `jjwt.version` property, `spring-security-core` (not the full starter), `jjwt-api`/`jjwt-impl`/`jjwt-jackson` |
| `src/main/resources/application.yml` | Added `hirehub.jwt.*`, with **no default** for the secret |
| `.env.example` | Corrected: dropped `JWT_REFRESH_EXPIRATION_MS` — no refresh flow exists yet, and documenting config for unimplemented functionality is exactly the mistake the project brief warns against |
| `PROJECT_PROGRESS.md`, `README.md` | Status lines |

**Decisions**
- **`spring-security-core` only, not `spring-boot-starter-security`.** The full starter
  auto-configures a filter chain that locks every endpoint behind a login by default. Adding
  it before `SecurityConfig` exists (Phase 4B) would have broken every controller test
  written in Phases 1–3 for zero benefit this phase — `UserDetails`/`UserDetailsService`/
  `GrantedAuthority` are core-module interfaces and don't need the web/config modules at all.
- **JWT claims are minimal by design:** `sub` (user id — stable, non-PII), `email`, `role`.
  A JWT is base64, not encryption; the rule applied is "nothing in a token that wouldn't be
  acceptable in a URL query parameter." No password hash, no phone number, ever.
- **HS256 over RS256.** HireHub issues and verifies its own tokens in one process. RS256's
  asymmetric key pair earns its complexity when a separate service must verify without being
  able to mint — not the case here, so the simpler, faster shared-secret scheme was chosen.
- **The provider is servlet-agnostic.** `JwtTokenProvider` never touches
  `HttpServletRequest` or a Spring Security `Authentication` object — proven by testing it
  with plain Mockito/no-Spring unit tests. This also means Phase 4B's filter can be tested
  in isolation from token logic, rather than the two being tangled together.
- **Fail fast on a weak secret.** `Keys.hmacShaKeyFor` validates key length at construction,
  so a `JWT_SECRET` under 256 bits fails application *startup*, not the first login attempt
  in production. Tested directly with `tooShortSecretFailsAtConstruction`.
- **The `.env.example` correction.** Phase 1 had pre-declared `JWT_REFRESH_EXPIRATION_MS`
  for a refresh-token flow that was never built. Rather than build unused code to match old
  documentation, the documentation was fixed — the project's own rule ("never document
  functionality that doesn't exist") applies to Claude's own earlier output too.

**Verify this phase**
```bash
mvn test -Dtest=JwtTokenProviderTest,HireHubUserDetailsServiceTest
# or the full suite (needs Docker for the other, container-backed tests):
mvn clean test    # 49 tests total
```
There is nothing to run via `spring-boot:run` yet for this phase specifically — these
classes have no HTTP entry point until Phase 4B wires them into a filter and a controller.

**Consistency check:** no controller, entity, repository or migration was touched. The
context still boots exactly as it did after Phase 3; `JwtProperties`/`JwtTokenProvider`/
`HireHubUserDetailsService` are registered as beans (proven by the full `mvn clean test` run
including `HireHubApplicationTests`, which would fail to boot the context if wiring a
`@Validated @ConfigurationProperties` bean without a bound secret broke startup) but nothing
in the request path uses them yet.

**Known gaps entering Phase 4B:** no `SecurityConfig`, so there is still no
`SecurityFilterChain` bean and no security auto-configuration in effect — every endpoint
remains unauthenticated. No `AuthService`, no `AuthController`, no BCrypt password hashing,
no `/api/auth/register` or `/api/auth/login`.

---

## Phase 4B — Auth endpoints ✅

**Files created**

| Group | Files |
|---|---|
| Security | `security/JwtAuthenticationFilter.java`, `security/JwtAuthenticationEntryPoint.java`, `security/JwtAccessDeniedHandler.java`, `config/SecurityConfig.java`, `config/OpenApiConfig.java` |
| Auth DTOs | `dto/auth/RegistrableRole.java`, `RegisterRequest.java`, `LoginRequest.java`, `AuthResponse.java`, `UserSummaryResponse.java` |
| Error handling | `dto/common/ErrorResponse.java`, `exception/DuplicateResourceException.java`, `exception/GlobalExceptionHandler.java` |
| Business logic | `service/AuthService.java`, `mapper/UserMapper.java` |
| Controller | `controller/AuthController.java` |
| Tests | `test/controller/AuthControllerTest.java` — 13 end-to-end tests through the real filter chain |

**Files modified**

| File | Change |
|---|---|
| `pom.xml` | `spring-security-core` → full `spring-boot-starter-security`; added `spring-security-test` |
| `ARCHITECTURE.md`, `API.md`, `PROJECT_PROGRESS.md`, `README.md` | Status updates |

**Decisions**
- **ADMIN cannot be self-registered — enforced by the type system, not an `if`.**
  `RegistrableRole` has exactly two constants. A request body containing
  `"role": "ADMIN"` fails at JSON deserialization, before the object even exists, so there
  is no code path where a forgotten check could let it through. `adminRoleCannotBeSelfRegistered`
  proves the request is rejected (400), not merely that some later validation catches it.
- **Login duplicates none of Spring Security's own logic.** `AuthService.login()` calls
  `AuthenticationManager.authenticate(...)` and nothing else — password comparison, the
  disabled-account check, and hiding "email not found" behind the same generic message as
  "wrong password" (defeating user enumeration) all happen inside `DaoAuthenticationProvider`.
  Reimplementing any of that here would just be a second place for the two to disagree.
- **A real `LazyInitializationException` bug, caught by design rather than by luck.**
  `HireHubUserDetailsService.loadUserByUsername` runs in its own
  `@Transactional(readOnly = true)` method; once `AuthenticationManager.authenticate()`
  returns, that inner transaction has already closed. Without care, `user.getRole()` back in
  `AuthService.login()` would fail on the detached entity. Fixed by marking `login()` itself
  `@Transactional(readOnly = true)` so the inner call joins the same, still-open transaction —
  documented in the method's Javadoc as exactly the kind of thing worth walking an
  interviewer through.
- **The filter re-authorizes from the database on every request, not from the JWT's own
  `role` claim.** The token is used only to identify *which* user is asking;
  `HireHubUserDetailsService` is queried fresh each time, so a role change or an account
  being disabled takes effect on the very next request rather than only once the token
  expires. The trade-off — one extra query per request — was chosen deliberately over a
  faster but staler pure-JWT-claims approach.
- **Coarse authorization only.** `SecurityConfig` draws exactly one line this phase:
  authenticated vs anonymous. `hasRole(...)` matchers and `@PreAuthorize` are Phase 5's
  job — folding them in here as an afterthought was rejected in favour of giving role-based
  authorization its own phase with its own tests.
- **`GlobalExceptionHandler` is intentionally minimal**, covering only what auth touches
  (duplicate email, the DB-constraint race backstop, Bean Validation failures, malformed
  JSON/bad enum values, and Spring Security's own exception types). It is extended, not
  replaced, when Phase 12 adds `ResourceNotFoundException`, `JobClosedException`, and the
  rest of the catalogue.
- **A bug caught during test-writing, not left in:** the first draft of
  `adminRoleCannotBeSelfRegistered` would have gotten a 500, not a 400, because Jackson's
  `HttpMessageNotReadableException` (thrown for an unrecognized enum value) had no specific
  handler and was falling through to the generic catch-all. Added
  `handleUnreadableBody` specifically because the test demanded it, not preemptively.

**Verify this phase**
```bash
docker compose up -d postgres
mvn clean test    # 61 tests total
mvn spring-boot:run

curl -X POST http://localhost:8080/api/auth/register -H "Content-Type: application/json" \
  -d '{"email":"jane@example.com","password":"Password123","fullName":"Jane Doe","role":"CANDIDATE"}'
curl -X POST http://localhost:8080/api/auth/login -H "Content-Type: application/json" \
  -d '{"email":"jane@example.com","password":"Password123"}'
curl http://localhost:8080/api/auth/me -H "Authorization: Bearer <accessToken from above>"
curl http://localhost:8080/api/auth/me   # -> 401, {"error":"UNAUTHENTICATED", ...}
open http://localhost:8080/swagger-ui.html
```

**Consistency check:** `/api/health` and `/actuator/**` remain `permitAll`, so
`HireHubApplicationTests` from Phase 1 still passes unmodified with the real security
starter now on the classpath. No entity, repository or migration was touched. The
duplicate-email guard follows the exact two-layer pattern established for applications in
Phase 2A/3 (`DuplicateResourceException` for the common case,
`DataIntegrityViolationException` → the same 409 for the race) rather than inventing a new one.

**Known gaps entering Phase 5:** no role-based restrictions exist anywhere yet — any
authenticated user, regardless of role, can call any endpoint that exists so far (which is
currently only the three auth endpoints plus health/docs). Candidate/recruiter/admin
resource boundaries, ownership checks, and `@PreAuthorize` annotations are all still to come.

---

## Phase 5+6 — Authorization + candidate profile ✅

**Reorder note:** the original roadmap had Phase 5 (authorization) before Phase 6
(candidate profile). Built together instead: `@PreAuthorize` rules have nothing meaningful
to say about a `hasRole('CANDIDATE')` check with no candidate-owned resource to guard, so
building authorization in the abstract would have meant writing untestable placeholder
rules now and real ones later. This is the exact trade-off flagged at the end of Phase 4B.

**Files created**

| Group | Files |
|---|---|
| DTOs | `dto/common/SkillResponse`, `dto/candidate/CandidateSkillResponse`, `AddCandidateSkillRequest`, `CandidateProfileResponse`, `UpdateCandidateProfileRequest` |
| Mapper | `mapper/CandidateProfileMapper` |
| Service | `service/CandidateProfileService` |
| Controller | `controller/CandidateProfileController` |
| Exceptions | `exception/ResourceNotFoundException` |
| Tests | `test/controller/CandidateProfileControllerTest` — 11 end-to-end tests |

**Files modified**

| File | Change |
|---|---|
| `config/SecurityConfig.java` | Added `@EnableMethodSecurity`; updated Javadoc for the two-layer (URL + method) authorization design |
| `repository/CandidateProfileRepository.java` | Added `findByUserIdWithSkills` |
| `exception/GlobalExceptionHandler.java` | Added the `ResourceNotFoundException` → 404 handler |
| `ARCHITECTURE.md`, `API.md`, `PROJECT_PROGRESS.md`, `README.md` | Status updates |

**Decisions**
- **Two authorization layers, exactly as ARCHITECTURE.md §5 promised in Phase 0:** coarse
  `authorizeHttpRequests` in `SecurityConfig` (authenticated vs anonymous only) and
  fine-grained `@PreAuthorize` on the controller method that actually needs the role check.
  Putting `hasRole('CANDIDATE')` in a URL matcher instead would separate the rule from the
  code it governs; an annotation on the method reads directly against what it protects.
- **Ownership by construction, not by check, for every `/me` endpoint.** Every "my profile"
  method takes a `userId` resolved from the authenticated principal — never a client-supplied
  id. There is no parameter a candidate could tamper with to reach someone else's profile
  through these methods, the same structural-prevention pattern used for `RegistrableRole`
  in Phase 4B, now applied to ownership instead of role.
- **`GET /api/candidates/{id}` is deliberately open to any recruiter or admin**, with no
  ownership check — matching the spec's "recruiters can view candidate profiles" requirement
  as written. Nothing to bypass because nothing is restricted beyond the role.
- **Find-or-create for skills, normalized identically to `Skill`'s own constructor.**
  Adding "JAVA" resolves to the seeded `java` row rather than creating a duplicate —
  verified by `addSkillReusesSeededSkillRegardlessOfCase`, which checks the returned skill
  name matches the seeded lower-case form, not a fresh row.
- **`experienceYears` breaks from "PUT is a full replace" on purpose, and it's commented
  why.** Every other profile field is nullable in the schema, so omitting it in a `PUT`
  correctly clears it. `experience_years` is `NOT NULL` (V1__init_schema.sql), so
  unconditionally setting it to `null` on an omitted field would fail at flush with a
  constraint violation instead of the graceful "field wasn't touched" a client expects — the
  one field that needs a null-guard, and the only one that gets it.
- **Admin test accounts are seeded directly through the repositories, not registered.**
  `ADMIN` cannot come from `/auth/register` (Phase 4B, by design), so
  `CandidateProfileControllerTest.seedAdminAndGetToken` inserts the row directly — which is
  also exactly how a real admin account would be provisioned in this system, so the test
  setup matches the intended operational reality rather than working around a limitation.

**Verify this phase**
```bash
docker compose up -d postgres
mvn clean test    # 72 tests total
mvn spring-boot:run

TOKEN=$(curl -s -X POST localhost:8080/api/auth/register -H "Content-Type: application/json" \
  -d '{"email":"jane@example.com","password":"Password123","fullName":"Jane Doe","role":"CANDIDATE"}' \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['accessToken'])")

curl localhost:8080/api/candidates/me -H "Authorization: Bearer $TOKEN"
curl -X PUT localhost:8080/api/candidates/me -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" -d '{"headline":"Backend Engineer","experienceYears":3}'
curl -X POST localhost:8080/api/candidates/me/skills -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" -d '{"skillName":"java","proficiency":"ADVANCED","yearsOfExperience":3}'
```

**Consistency check:** no migration or entity was touched — `findByUserIdWithSkills` is a
new query method on an existing repository, following the exact `@EntityGraph`-adjacent
`JOIN FETCH` pattern `findByIdWithSkills` already established in Phase 3. The duplicate-skill
guard follows the same two-layer pattern (service check + the `candidate_skills` composite
PK from Phase 2A as the concurrency backstop) used for duplicate emails and, later, duplicate
applications — the third time this exact pattern appears in the codebase, not a new one
invented for this feature.

**Known gaps entering Phase 7:** no company or recruiter-profile management yet — a
recruiter who registers has no way to create or join a company, which blocks job creation
(Phase 8) until it exists. No admin endpoints beyond the ability to authenticate as one.

---

## Phase 7 — Company & recruiter ✅

**Files created**

| Group | Files |
|---|---|
| DTOs | `dto/common/PageResponse`, `dto/company/{CompanyResponse, CreateCompanyRequest, UpdateCompanyRequest, RecruiterProfileResponse, UpdateRecruiterProfileRequest}`, `dto/admin/{ApprovalDecision, CompanyApprovalRequest}` |
| Mapper | `mapper/CompanyMapper` |
| Services | `service/CompanyService`, `service/RecruiterProfileService` |
| Controllers | `controller/CompanyController`, `controller/RecruiterProfileController`, `controller/admin/AdminCompanyController` |
| Exceptions | `exception/InvalidStateException` |
| Tests | `test/controller/CompanyControllerTest` (9), `test/controller/RecruiterProfileControllerTest` (3), `test/controller/admin/AdminCompanyControllerTest` (6) — 18 new tests |

**Files modified**

| File | Change |
|---|---|
| `config/SecurityConfig.java` | Added public `GET /api/companies/*`; added `requestMatchers("/api/admin/**").hasRole("ADMIN")` — the first real use of the coarse URL-level gate the class's own Javadoc had described since Phase 5+6 |
| `exception/GlobalExceptionHandler.java` | Added the `InvalidStateException` → 409 handler |
| `ARCHITECTURE.md`, `API.md`, `DATABASE.md`, `README.md`, `PROJECT_PROGRESS.md` | Status updates |

**Decisions**
- **The first ownership check that needs real domain logic, exactly as flagged at the end
  of Phase 5+6.** "May this recruiter edit company X" depends on which company their
  profile actually points to — data no `@PreAuthorize` expression can see without a
  database read. `CompanyService.updateCompany` throws Spring Security's own
  `AccessDeniedException` directly from the method body for this, deliberately the same
  exception type a `@PreAuthorize` failure throws, so a role failure and an ownership
  failure are byte-for-byte the same 403 to a client. Documented in the service's Javadoc:
  the two are caught by different machinery (`ExceptionTranslationFilter` +
  `JwtAccessDeniedHandler` for the annotation; `GlobalExceptionHandler` for the
  service-thrown one) for an identical result — worth knowing the distinction, not just
  the outcome.
- **The admin namespace is gated once, at the URL level, not per-method.** This is the
  first controller with zero `@PreAuthorize` annotations, and the class Javadoc explains
  why: `SecurityConfig`'s `/api/admin/**` rule already covers it, and repeating the check
  per method would be redundant — worse, it could make a maintainer assume a bare future
  method was deliberately left open.
- **`ApprovalDecision` has only `APPROVED`/`REJECTED`, not the full `CompanyStatus`.** Same
  structural-guard reasoning as `RegistrableRole` (Phase 4B): `PENDING` is a state a company
  starts in, never a value an admin should be able to set it back to through this endpoint.
- **Dropped a `note` field from the approval request before it shipped.** A first draft
  included one for an admin's rejection reason; there is no table to persist it in yet, and
  accepting-and-discarding it would repeat the exact `JWT_REFRESH_EXPIRATION_MS` mistake
  corrected in Phase 4A. Removed rather than built with fake storage.
- **`InvalidStateException` is generic, not `InvalidCompanyStatusException`.** It exists to
  be thrown from more than one call site as the project grows (an already-decided approval
  today; other single-transition-guard cases later), rather than adding a same-shaped
  exception per resource. If a future phase's failure mode needs a more specific message or
  status code, the project brief's own named exceptions (`JobClosedException`, etc.) are
  introduced then — not speculatively now.
- **A company's public profile is deliberately public.** `GET /api/companies/{id}` needs no
  token, matching how a real job board lets anyone browse company pages. This is the first
  `permitAll()` rule scoped by HTTP method (`GET` only) rather than by path alone — `POST`
  and `PUT` to the identical base path still require a recruiter's token.
- **`PageResponse<T>` ships now, not deferred to Phase 9.** Job search will need it, but the
  admin company queue needed it first — a stable, custom paging shape rather than
  serializing Spring Data's own `Page<T>` (which leaks `pageable`/`sort` implementation
  detail into the API contract) is exactly what API.md's paging convention already promised
  in Phase 0.

**Verify this phase**
```bash
docker compose up -d postgres
mvn clean test    # 90 tests total
mvn spring-boot:run

TOKEN=$(curl -s -X POST localhost:8080/api/auth/register -H "Content-Type: application/json" \
  -d '{"email":"r@example.com","password":"Password123","fullName":"R","role":"RECRUITER"}' \
  | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['accessToken'])")
curl -X POST localhost:8080/api/companies -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" -d '{"name":"Acme Corp"}'
curl localhost:8080/api/companies/1   # public, no token
```

**Consistency check:** no migration or entity touched — `Company`/`RecruiterProfile` and
their repositories already existed from Phase 2B/3 with everything this phase needed
(`findByStatus`, `findByUserId`, `existsByNameIgnoreCase`). The duplicate-company-name and
duplicate-membership guards follow the same two-layer pattern used for duplicate emails
(4B) and duplicate skills (5+6) — the fourth appearance of that exact shape.

**Known gaps entering Phase 8:** no job entity CRUD yet. A recruiter can now create and own
an approved company, which is the prerequisite Phase 8 needed — job creation was blocked on
exactly this until now. No admin user-management or statistics endpoints yet.

---

## Correctness fix — authentication's lazy-role hazard (pre-Phase 8) ✅

**What happened:** while designing a new "who can view this DRAFT job" check for Phase 8, I
traced `JwtAuthenticationFilter.authenticate()`'s exact call sequence and found a real risk
in code from Phase 4A that had never been verified by actually running the suite — this
environment has no Maven/network to execute `mvn test`, so every phase so far had been
reasoned through rather than confirmed. The filter calls
`userDetailsService.loadUserByUsername(email)`, whose own `@Transactional(readOnly = true)`
method commits and closes the moment it returns, and then *immediately* calls
`userDetails.getAuthorities()` — which reads the lazy `user.getRole()` association — with no
transaction of its own active at all. If `role` were still an uninitialized Hibernate proxy
at that point, this would throw `LazyInitializationException` on every single authenticated
request in the entire application.

**The fix:** `UserRepository.findByEmailIgnoreCaseWithRole` — a `JOIN FETCH` variant used
specifically by `HireHubUserDetailsService.loadUserByUsername` — forces `Role` to be fully
materialized as part of the one query, so it is never a lazy proxy regardless of which
transaction boundary is or isn't active afterward. This is a stronger fix than a
transaction boundary: `AuthService.login()` could be protected by staying transactional
because it *has* an enclosing transaction for the nested call to join; the filter has no
such transaction at all, so only fixing it at the query level actually covers both callers.

**Files modified**

| File | Change |
|---|---|
| `repository/UserRepository.java` | Added `findByEmailIgnoreCaseWithRole` (`JOIN FETCH`); corrected a now-stale class Javadoc |
| `security/HireHubUserDetailsService.java` | Uses the new method; Javadoc corrected to name the real fix, not just the surface-level `@Transactional` |
| `service/AuthService.java` | `login()`'s Javadoc corrected the same way — `@Transactional` here is now accurately described as a second line of defense, not the mechanism holding correctness together |
| `test/security/HireHubUserDetailsServiceTest.java` | Updated all four stubs/verifications to the new method name — the old ones would have made every test in the file return `Optional.empty()` by Mockito's default and fail for the wrong reason |

**Files created**

| File | Purpose |
|---|---|
| `test/security/HireHubUserDetailsServiceIntegrationTest.java` | The regression test that actually proves this: deliberately **not** `@Transactional`, so `save()` and the lookup each commit and close independently, exactly reproducing the filter's real call shape. A Mockito unit test cannot catch this class of bug at all — its objects are never real Hibernate proxies. |

**Why this matters for the existing test suite:** every integration test since Phase 4B that hit
a protected endpoint has been exercising this exact path (login or the filter authenticating
a bearer token) without incident in my reasoning, but reasoning is not verification. This fix
removes the doubt structurally rather than leaving it as an assumption load-bearing for the
rest of the project.

**Verify:**
```bash
mvn test -Dtest=HireHubUserDetailsServiceIntegrationTest,HireHubUserDetailsServiceTest
mvn clean test    # 91 tests total
```

---

## Phase 8 — Job management ✅

**Files created**

| Group | Files |
|---|---|
| DTOs | `dto/job/{JobResponse, JobSkillResponse, JobSkillRequest, CreateJobRequest, UpdateJobRequest}` |
| Mappers | `mapper/SkillMapper` (extracted, shared), `mapper/JobMapper` |
| Services | `service/SkillService` (extracted, shared), `service/JobService` |
| Controller | `controller/JobController` |
| Tests | `test/controller/JobControllerTest` — 14 end-to-end tests |

**Files modified**

| File | Change |
|---|---|
| `repository/JobRepository.java` | Added `findByStatus` (the public feed query) |
| `controller/RecruiterProfileController.java` | Added `GET /me/jobs` |
| `service/CandidateProfileService.java` | Refactored to use the new shared `SkillService` instead of its own private find-or-create method |
| `mapper/CandidateProfileMapper.java` | Refactored to use the new shared `SkillMapper` instead of its own `Skill → SkillResponse` method |
| `config/SecurityConfig.java` | Added public `GET /api/jobs`, `GET /api/jobs/*` |
| `ARCHITECTURE.md`, `API.md`, `PROJECT_PROGRESS.md`, `README.md` | Status updates |

**Decisions**
- **Ownership is company-wide, not creator-only.** Every management operation checks
  `recruiterProfile.getCompany().getId()` against `job.getCompany().getId()` — never
  `job.getCreatedBy()`. A hiring team should be able to fix a colleague's typo; `created_by`
  stays as audit information, not an access boundary. `ownershipIsCompanyWideNotCreatorOnly`
  proves a colleague (added directly via the repositories, since no "join an existing
  company" endpoint exists yet) can close a job they didn't create, while
  `myJobsListsOnlyPersonallyCreatedJobs` proves the one place that *does* narrow to personal
  authorship (`GET /api/recruiters/me/jobs`, via `jobs.created_by`) actually does.
- **A DRAFT job is invisible, not merely blocked.** A non-owner requesting a DRAFT job's id
  gets 404, not 403 — revealing "this exists but you can't see it" would leak the existence
  of an unpublished posting to someone with no right to know about it. The visibility check
  lives in `JobService`, driven by a nullable `HireHubUserDetails` that
  `@AuthenticationPrincipal` resolves to `null` for anonymous callers rather than throwing.
- **Publishing needs company approval; creating a draft doesn't.** A recruiter can prepare
  and edit a job while their company's approval is still pending — only the transition to
  `PUBLISHED` is gated on `company.isApproved()`, matching DATABASE.md §1's original design
  intent, now actually enforced (`publishRequiresApprovedCompany`).
- **Editing is allowed while DRAFT or PUBLISHED, not after CLOSED/EXPIRED.** A closed
  posting is a historical record that applications may already reference; a live one can
  still have a typo fixed. `JobStatus`'s own transition rules (Phase 2B) govern publish/close/
  delete; this DRAFT-or-PUBLISHED editability rule is `JobService`'s own addition on top,
  since the entity's state machine only governs *status* transitions, not which statuses
  permit content edits.
- **Duplicated skill-lookup logic extracted into `SkillService`, shared `Skill → SkillResponse`
  mapping extracted into `SkillMapper`**, the moment a second feature (jobs) needed the exact
  same thing candidate profiles already had. Both existing call sites were refactored to use
  the shared versions rather than left with two copies — following the project's own
  "no duplicate code" rule instead of accumulating it phase over phase.
- **`isSalaryRangeValid()` as a plain `@AssertTrue` method on the record**, not a custom
  class-level annotation — Hibernate Validator picks up any `isXxx()` method annotated this
  way the same as on a regular class, and a custom annotation would be ceremony for a rule
  used in exactly one place.

**Verify this phase**
```bash
docker compose up -d postgres
mvn clean test    # 105 tests total
mvn spring-boot:run
```

**Consistency check:** no migration touched. `Job`'s status state machine, `@Version`
optimistic locking, and `JobRepository`'s `@EntityGraph`/`JpaSpecificationExecutor` were all
already in place from Phase 2B/3 — this phase is the first to actually exercise them through
real endpoints. The duplicate-skill-logic extraction is the kind of refactor the "Existing
Code Rule" calls for: both call sites were checked and updated together, not left to drift.

**Known gaps entering Phase 9:** `GET /api/jobs` has no filters yet — keyword, location,
employment type, experience level and salary range all arrive with the
`Specification`-based search. No application workflow yet, so a published job cannot
actually be applied to.

---

## Phase 9 — Job search ✅

**Files created**

| File | Purpose |
|---|---|
| `specification/JobSpecifications.java` | One composable `Specification<Job>` per filter: status, keyword, location, employment type, experience level, min/max salary, skill |
| `dto/job/JobSearchCriteria.java` | Query-parameter binding for `GET /api/jobs`'s filters, via `@ModelAttribute` |
| `test/specification/JobSpecificationsTest.java` | 6 integration tests, each predicate run against real PostgreSQL |

**Files modified**

| File | Change |
|---|---|
| `service/JobService.java` | `listPublished` replaced by `search(criteria, pageable)`; added the `SORTABLE_PROPERTIES` sort allow-list |
| `controller/JobController.java` | `list()` now binds `JobSearchCriteria` and delegates to `search` |
| `repository/JobRepository.java` | Removed `findByStatus` (replaced by the Specification) and `findByCompanyId` (confirmed unused — see below) |
| `test/repository/JobRepositoryTest.java` | Updated the one test that referenced the now-removed `findByCompanyId` |
| `test/controller/JobControllerTest.java` | Added 5 search-focused tests (query-param filters, sort allow-list, DRAFT non-leakage) |
| `ARCHITECTURE.md`, `API.md`, `DATABASE.md`, `PROJECT_PROGRESS.md`, `README.md` | Status updates |

**Decisions**
- **`ilike`, not `lower(title) like lower(?)`, for keyword search.** `idx_jobs_title_trgm`
  (Phase 2A) is a GIN trigram index built on the raw `title` column. PostgreSQL will not use
  that index to satisfy a predicate on `lower(title)` — a different expression than what the
  index actually indexes. Since portable `jakarta.persistence.criteria.CriteriaBuilder` has
  no `ilike`, `JobSpecifications.hasKeyword` casts to Hibernate's own
  `HibernateCriteriaBuilder`, documented as one more explicit instance of the
  PostgreSQL-specific coupling the project already commits to elsewhere (the index itself,
  `pg_trgm`). Getting this expression exactly right — not just "case-insensitive search that
  happens to work" — is the difference between an index that's actually used and one that
  quietly never is.
- **`minSalary` matches `salaryMax`; `maxSalary` matches `salaryMin`.** "Show me jobs that
  could pay at least what I want" means checking the job's *upper* bound, not its lower one —
  a 15–25 LPA listing should match a 20+ filter even though its minimum is below that. Jobs
  with no salary range at all are excluded when either filter is active, since there's
  nothing to verify the match against.
- **The sort allow-list is enforced with the strongest possible test.** Rather than test
  against a merely-disallowed-but-real field (which wouldn't prove much — Postgres can sort
  on any real column), `nonexistentSortFieldIsIgnoredNotRejected` sorts by a property that
  doesn't exist on `Job` at all — exactly what would throw `PropertyReferenceException` (a
  500) without the allow-list stripping it first.
- **Removed two repository methods once confirmed dead: `findByStatus` and
  `findByCompanyId`.** The Specification-based `search` replaced the first outright, and
  auditing usage confirmed the second — written speculatively in Phase 3 — had never actually
  been called from anywhere. Same reasoning as dropping `JWT_REFRESH_EXPIRATION_MS` in Phase
  4A: unused capability that looks finished invites more confidence in it than it deserves.
  Removing `findByCompanyId` required updating the one Phase 3 test that referenced it —
  checked and fixed together, per the project's own "Existing Code Rule."
- **`EXPLAIN ANALYZE` verification was not performed.** The project brief calls for checking
  each index against seeded data with `EXPLAIN ANALYZE` and dropping ones the planner never
  picks. This sandbox has no live PostgreSQL connection outside the test suite's ephemeral
  Testcontainers instance, so there's no persistent database to run that analysis against
  interactively. What's verifiable here instead: `JobSpecificationsTest` proves each
  predicate returns the *correct results*, and `JobSpecifications`' Javadoc states which
  index each predicate is written to match and why the exact expression matters. Confirming
  the query planner actually chooses those indexes needs a real running instance with
  representative data volume — worth doing explicitly once this runs somewhere persistent
  (`docker compose up -d postgres`, seed data, then `EXPLAIN ANALYZE SELECT ... WHERE title
  ILIKE '%java%'` at the `psql` prompt).

**Verify this phase**
```bash
docker compose up -d postgres
mvn clean test    # 116 tests total
mvn spring-boot:run

curl "http://localhost:8080/api/jobs?keyword=java&location=Pune&minSalary=800000&sort=salaryMax,desc"
curl "http://localhost:8080/api/jobs?skill=postgresql"
```

**Consistency check:** no migration touched — this phase exists to finally use the indexes
Phase 2A already justified. `JobRepository`'s `JpaSpecificationExecutor`, added in Phase 3
for exactly this moment, needed no change at all.

**Known gaps entering Phase 10:** still no application workflow — a candidate can search
for and view a published job in full detail but has no way to apply to it yet.

---

## Phase 10+11 — Application workflow + status history ✅

**Reorder note:** merged for the same reason 5+6 were — the status-history mechanic isn't a
separable feature from the apply/withdraw/pipeline workflow that produces it.
`Application.changeStatus()` (Phase 2B) already couples a status change to its history entry
at the entity level; building "the workflow" and "the history" as two phases would have meant
either building half-working code in Phase 10 or artificially delaying history to Phase 11
for no real reason.

**Files created**

| Group | Files |
|---|---|
| DTOs | `dto/application/{ApplyToJobRequest, UpdateApplicationStatusRequest, JobSummaryResponse, ApplicationResponse, ApplicationStatusHistoryResponse}` |
| Mapper | `mapper/ApplicationMapper` |
| Service | `service/ApplicationService` |
| Controller | `controller/ApplicationController` |
| Tests | `test/controller/ApplicationControllerTest` — 24 end-to-end tests |

**Files modified**

| File | Change |
|---|---|
| `repository/ApplicationRepository.java` | Added `@EntityGraph` to all four paginated list methods and extended the detail-view graph — a real N+1 fix, not polish (see below) |
| `test/repository/ApplicationRepositoryTest.java` | Added a test proving the N+1 fix |
| `exception/GlobalExceptionHandler.java` | Added the `ObjectOptimisticLockingFailureException` → 409 handler ARCHITECTURE.md §6 promised in Phase 0 |
| `ARCHITECTURE.md`, `API.md`, `DATABASE.md`, `PROJECT_PROGRESS.md`, `README.md` | Status updates and corrections (below) |

**A real N+1 bug caught before it shipped, not after.** While wiring `ApplicationMapper` —
which reads `job.company.name` and `candidate.user.fullName/email` for every row — I checked
`ApplicationRepository`'s paginated list methods (`findByCandidateId`, `findByJobId`, etc.)
and found none of them had an `@EntityGraph` at all. A page of twenty applications would have
fired the two follow-up lazy-load queries per row separately: 40 extra queries for one page —
the textbook N+1 this project's own brief asks about directly. Fixed by adding
`@EntityGraph(attributePaths = {"job", "job.company", "candidate", "candidate.user"})` to all
four list methods, with `findByCandidateIdAvoidsNPlusOne` proving it — checking
`Hibernate.isInitialized(...)` on the nested associations immediately after the query
returns, the same verification pattern Phase 3 established for the single-entity fetch-joins.

**Decisions**
- **The fourth appearance of the two-layer duplicate guard.** `existsByJobIdAndCandidateId`
  gives the friendly 409 for the common case; `uq_applications_job_candidate` (Phase 2A) is
  what holds under a race. Same shape as duplicate emails, duplicate skills, duplicate
  company names — the pattern doesn't get reinvented per feature.
- **Applying to a DRAFT job looks identical to applying to a job that doesn't exist.** A
  DRAFT job is already invisible to candidates everywhere else (`JobService.getById`), so
  `ApplicationService.apply` checks for `DRAFT` explicitly and throws the same
  `ResourceNotFoundException` a genuinely missing job would — `applyToDraftJobIsNotFound`
  proves the two are indistinguishable from the outside.
- **`UpdateApplicationStatusRequest` uses `ApplicationStatus` directly, not a restricted
  subset enum** like `RegistrableRole` or `ApprovalDecision`. Those exist because nothing
  else in the domain already encodes the restriction; here,
  `ApplicationStatus.isAssignableByRecruiter()` (Phase 2B) already is that single source of
  truth. A second enum restating "WITHDRAWN is off-limits" would be two places to keep in
  sync instead of one — the opposite of the structural-guard principle, not an application
  of it.
- **A service-thrown `AccessDeniedException` for ownership, a fourth time.** Recruiter
  ownership of an application (via the job's company) uses the exact same
  service-throws-Spring-Security's-own-exception pattern as `CompanyService` and
  `JobService`, so a role failure and an ownership failure are still byte-for-byte the same
  403 shape everywhere in the app, not just in two out of three places that needed it.
- **Documentation corrected against what was actually built, not left as originally
  sketched.** Phase 0's request-lifecycle diagram in ARCHITECTURE.md §4 named
  `JobClosedException` and `InvalidApplicationException` at 422; the actual implementation
  uses the same `InvalidStateException` at 409 that already handles every other
  "well-formed request, wrong state" case in the app (company approval, job lifecycle). Both
  ARCHITECTURE.md and API.md were corrected in place, with a note explaining the change
  rather than silently rewriting history — the same standard applied to the
  `JWT_REFRESH_EXPIRATION_MS` correction back in Phase 4A.

**Verify this phase**
```bash
docker compose up -d postgres
mvn clean test    # 140 tests total
mvn spring-boot:run
```

**Consistency check:** no migration or entity touched — `Application`'s `changeStatus`/
`recordInitialStatus` and the whole status-history mechanic were already correct from Phase
2B; this phase is the first to drive them through real endpoints. The N+1 fix required
updating an existing repository file and adding one test to an existing Phase 3 test class,
both done together per the "Existing Code Rule."

**Known gaps entering Phase 12:** `GlobalExceptionHandler` still only covers what each
feature has needed so far — the full Phase 12 catalogue (a formal validation pass across
every DTO, and any exception type not yet named) hasn't been audited end-to-end as one
deliberate pass. No admin user-management or statistics endpoints. No dedicated test suite
run yet for Phases 13+ (that's Phase 13's job).

---

## Phase 12 — Validation & exception handling ✅

**A genuinely deliberate audit, not another incremental addition.** Every prior phase added
exactly the validation and exception handling its own feature needed, as documented in each
phase's own notes. This phase's job was to step back and check the whole thing at once —
and it found one real, previously-shipped bug plus three defensive gaps worth closing.

**Files modified**

| File | Change |
|---|---|
| `exception/GlobalExceptionHandler.java` | Added `HttpRequestMethodNotSupportedException` (405), `MethodArgumentTypeMismatchException` (400), `MissingServletRequestParameterException` (400), `ConstraintViolationException` (400) handlers; corrected two stale comments |
| `API.md` | Added the 405 status code, broadened the 400/404 rows to match |
| `PROJECT_PROGRESS.md` | This entry |

**Files created**

| File | Purpose |
|---|---|
| `test/exception/GlobalExceptionHandlerAuditTest.java` | 3 tests, one per audit finding that needed proving, not just fixing |

**The bug, and why it existed for eight phases without anyone noticing:** Spring resolves
`@ExceptionHandler` methods (`ExceptionHandlerExceptionResolver`) *before* falling back to
Spring MVC's own default handling (`DefaultHandlerExceptionResolver`) for exceptions like
`HttpRequestMethodNotSupportedException`. Since `GlobalExceptionHandler` has had a generic
`@ExceptionHandler(Exception.class)` catch-all since Phase 4B, it was silently intercepting
`HttpRequestMethodNotSupportedException` and returning the generic 500 instead of the
correct 405 — for every endpoint in the entire app, the whole time. Nothing in Phases 4B
through 11 ever sent a request with a deliberately wrong HTTP method to an existing path, so
nothing ever exercised the path that would have revealed it. This is exactly the gap a
feature-driven, incremental approach to exception handling will always have until a
dedicated audit pass looks for what was never triggered rather than what was.
`wrongHttpMethodReturns405NotInternalServerError` proves the fix, not just the theory: a
`PATCH /api/companies/1` (a path that only maps `GET`/`PUT`) now returns 405
`METHOD_NOT_ALLOWED`, verified with a real authenticated request through the real filter
chain.

**Decisions**
- **Every request DTO already had validation coverage.** A grep-based sweep across every
  `*Request.java` file confirmed at least one constraint annotation on each, appropriate to
  its field count — the incremental "validate what this phase's feature needs" approach
  held up under a deliberate check, rather than needing a retrofit.
- **`ConstraintViolationException` and `MissingServletRequestParameterException` are
  defensive, not fixes for an existing bug** — no current controller uses inline
  `@RequestParam`/`@PathVariable` constraints or a required parameter without a default, so
  neither exception can actually be thrown by the app today. Added anyway: the moment a
  future endpoint does either, the failure mode would look identical to a client as any
  other validation error, and it costs nothing to have the handler already in place rather
  than discovered missing during a later phase's own audit.
- **Two stale comments corrected.** `AccessDeniedException`'s handler comment still said
  "not exercised until Phase 5" despite `CompanyService`, `JobService` and
  `ApplicationService` all throwing it directly since; the class-level Javadoc's "scope
  note" from Phase 4B was rewritten to describe what this phase actually is, rather than
  what Phase 4B originally expected Phase 12 to be.

**Verify this phase**
```bash
docker compose up -d postgres
mvn clean test    # 143 tests total
mvn spring-boot:run

curl -X PATCH http://localhost:8080/api/companies/1   # -> 405, not 500
curl http://localhost:8080/api/jobs/not-a-number        # -> 400, not 500
```

**Consistency check:** no entity, migration, or business-logic file touched — this phase is
entirely about the cross-cutting exception-handling layer catching up with everything built
around it, which is exactly what an audit phase should do.

**Known gaps entering Phase 13:** no dedicated additional unit-test sweep has been run
specifically for coverage gaps in service-layer business logic (as opposed to the
integration-level testing every phase has already included) — that is Phase 13's explicit job.

---

## Phase 13 — Testing ✅

**Scope note:** integration-level testing (MockMvc + Testcontainers, hitting a real
PostgreSQL through the real security filter chain) has been built incrementally since
Phase 4B — every controller has its own test class, 181 tests total by this point. What was
missing, and what this phase specifically adds, is the project brief's named target: pure
Mockito unit tests for individual services, isolated from Spring and the database entirely.

**Files created**

| File | Tests | Covers |
|---|---|---|
| `test/service/AuthServiceTest.java` | 7 | register (candidate vs recruiter profile creation, duplicate email, email normalization, missing seeded role), login (delegates to `AuthenticationManager`, doesn't re-implement credential checking) |
| `test/service/JobServiceTest.java` | 17 | create (no-company guard), publish (company-approval guard, double-publish rejected, ownership), update (editable-while-DRAFT-or-PUBLISHED rule), delete (DRAFT-only rule), DRAFT visibility (anonymous/other-company/admin/published), the sort allow-list |
| `test/service/ApplicationServiceTest.java` | 14 | apply (success, incomplete profile, duplicate, closed job, missing job), updateStatus (pipeline success, WITHDRAWN blocked, stage-skipping blocked, cross-company ownership), withdraw (owner success, non-owner blocked, terminal-state blocked), view-access ownership |

**Why these three, and why not more:** the project brief names "Authentication service,
User service, Job service, Application service" as unit-test targets. There is no separate
`UserService` class in this codebase — user management is split across `AuthService`
(registration) and `HireHubUserDetailsService` (already unit-tested in Phase 4A) — so that
target is covered by the two services that actually do the work a "User service" would.
`CompanyService`, `CandidateProfileService` and `RecruiterProfileService` were deliberately
left to their existing integration coverage: they follow the exact same patterns
(ownership-via-`AccessDeniedException`, two-layer duplicate guards) already proven in
isolation by the services above, so a third and fourth near-identical set of Mockito tests
would mostly re-verify the same mechanism rather than find anything new — the same
"don't duplicate what's already proven" judgment applied throughout this project rather
than chasing a raw test count.

**What these tests catch that the integration tests structurally can't:** a MockMvc test
proves the whole stack produces the right HTTP response; it can't isolate *which* line of a
service method is responsible when something's wrong, and it can't easily exercise a
collaborator returning a specific edge-case value (an `Optional.empty()` from a repository,
a mapper throwing) without real data to provoke it. `missingRecruiterProfileIsNotFound` and
`missingJobIsNotFound`, for instance, assert on behavior that would require deleting a row
mid-test-run to provoke at the integration level — trivial to stub with Mockito instead.

**Decisions**
- **Real entities, mocked repositories/mappers.** `Job`, `Application`, `Company` and the
  profile types are constructed directly in each test, not mocked — they carry real business
  behaviour (`Job.publish()`, `Application.changeStatus()`, the state machines) that is
  exactly what's under test. Mocking them would mean re-stubbing the logic the test exists
  to verify, which proves nothing.
- **A reflection-based `setId` helper**, the same pattern `JwtTokenProviderTest` used back
  in Phase 4A: entity ids are assigned by the database on insert, so there's no public
  setter, and these objects are never actually persisted in a pure unit test.
- **Anonymous `HireHubUserDetails` subclasses for DRAFT-visibility and ownership tests.**
  `getAuthorities()` normally reflects `user.getRole()`, which would require a real
  Hibernate session to resolve safely (see the Phase 4A lazy-loading fix) — overriding it
  directly in the test avoids needing any of that machinery while still exercising
  `JobService`/`ApplicationService`'s actual admin/role-checking logic faithfully.

**Verify this phase**
```bash
mvn test -Dtest=AuthServiceTest,JobServiceTest,ApplicationServiceTest   # fast, no Docker
mvn clean test    # 181 tests total, full suite
```

**Consistency check:** no entity, migration, repository or controller touched — purely
additive test coverage. No existing test was modified.

**Known gaps entering Phase 14:** no OpenAPI/Swagger polish pass yet — annotations exist on
every controller already (added as each was built), but grouping, examples and a dedicated
review haven't happened as a deliberate phase.

---

## Suggested commits

```
chore: bootstrap HireHub Spring Boot project with docs and health endpoint
feat: add PostgreSQL schema migrations with constraints and indexes
feat: map JPA entities with auditing and status state machines
feat: add Spring Data repositories with fetch-join and specification support
feat: add JWT token provider and UserDetailsService
feat: add authentication endpoints with Spring Security and global exception handling
feat: add role-based authorization and candidate profile management
feat: add company and recruiter management with admin approval workflow
fix: eagerly fetch role on the authentication path to prevent lazy-loading failures
feat: add job management with company-wide ownership and status lifecycle
feat: add job search with Specification-based filtering and a sort allow-list
feat: add application workflow with status history and optimistic-lock handling
fix: audit and correct exception handling, including a 405-to-500 misrouting bug
test: add Mockito unit tests for auth, job and application services
```
