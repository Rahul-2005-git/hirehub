# HireHub — API Reference

Base URL (local): `http://localhost:8080`
Interactive docs: `http://localhost:8080/swagger-ui.html` (OpenAPI JSON at `/v3/api-docs`)

Status legend: ✅ implemented · ⏳ planned (phase number in brackets)

---

## Conventions

**Authentication.** All endpoints except those marked *public* require
`Authorization: Bearer <token>`.

**Success envelope**

```json
{
  "success": true,
  "message": "Job created successfully",
  "data": { }
}
```

**Error envelope**

```json
{
  "timestamp": "2026-03-14T10:22:31.482Z",
  "status": 404,
  "error": "RESOURCE_NOT_FOUND",
  "message": "Job not found with id 123",
  "path": "/api/jobs/123"
}
```

Validation failures add a `fieldErrors` object mapping field name → message.

**Paged responses** carry `content`, `page`, `size`, `totalElements`, `totalPages`, `last`.

**Status codes**

| Code | Used for |
|---|---|
| 200 / 201 / 204 | success / created / no content |
| 400 | malformed body, failed Bean Validation, a path/query parameter of the wrong type, an unrecognized enum value |
| 401 | missing, expired or invalid JWT |
| 403 | authenticated but wrong role, or not the owner of the resource |
| 404 | resource does not exist (or, for a DRAFT job/application, exists but you're not allowed to know that) |
| 405 | the HTTP method isn't supported on that path (e.g. `PATCH /api/companies/{id}`, which only accepts `GET`/`PUT`) |
| 409 | duplicate application, duplicate email, an already-decided approval, an invalid status transition, optimistic-lock conflict |
| 500 | unexpected error (generic message only — never a stack trace) |

---

## Health

| Method | Path | Auth | Status |
|---|---|---|---|
| GET | `/api/health` | public | ✅ |
| GET | `/actuator/health` | public | ✅ |

```bash
curl http://localhost:8080/api/health
# {"success":true,"message":"Service is healthy",
#  "data":{"status":"UP","application":"HireHub","version":"0.1.0-SNAPSHOT"}}
```

---

## Authentication ✅

| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/api/auth/register` | public | Register as CANDIDATE or RECRUITER. ADMIN cannot be self-registered. |
| POST | `/api/auth/login` | public | Exchange credentials for a JWT |
| GET | `/api/auth/me` | any authenticated user | Current authenticated principal, re-read from the database on every call |

```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"jane@example.com","password":"Password123","fullName":"Jane Doe","role":"CANDIDATE"}'

curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"jane@example.com","password":"Password123"}'

curl http://localhost:8080/api/auth/me -H "Authorization: Bearer <accessToken>"
```

---

## Candidate profile ✅

| Method | Path | Role |
|---|---|---|
| GET | `/api/candidates/me` | CANDIDATE |
| PUT | `/api/candidates/me` | CANDIDATE |
| GET | `/api/candidates/me/skills` | CANDIDATE |
| POST | `/api/candidates/me/skills` | CANDIDATE |
| DELETE | `/api/candidates/me/skills/{skillId}` | CANDIDATE |
| GET | `/api/candidates/{id}` | RECRUITER, ADMIN |

Resume data (`resumeUrl`, `resumeFileName`) is part of `PUT /api/candidates/me`, not a
separate endpoint — there is no file-storage integration in this project's scope, so
"uploading a resume" is recording a link to one, which the general profile update already
does for every other field. A dedicated endpoint for the same one write would document a
distinction that doesn't exist in the implementation.

```bash
curl -X PUT http://localhost:8080/api/candidates/me \
  -H "Authorization: Bearer <token>" -H "Content-Type: application/json" \
  -d '{"headline":"Senior Backend Engineer","experienceYears":6,"currentLocation":"Pune"}'

curl -X POST http://localhost:8080/api/candidates/me/skills \
  -H "Authorization: Bearer <token>" -H "Content-Type: application/json" \
  -d '{"skillName":"kubernetes","proficiency":"ADVANCED","yearsOfExperience":3}'
```

---

## Company & recruiter ✅

| Method | Path | Role |
|---|---|---|
| POST | `/api/companies` | RECRUITER |
| GET | `/api/companies/{id}` | public |
| PUT | `/api/companies/{id}` | RECRUITER (owner) |
| GET | `/api/recruiters/me` | RECRUITER |
| PUT | `/api/recruiters/me` | RECRUITER |
| GET | `/api/admin/companies?status=PENDING` | ADMIN |
| PATCH | `/api/admin/companies/{id}/approval` | ADMIN |

`GET /api/companies/{id}` is intentionally public — no token required — the same way a
job-board's company pages are browsable without an account. `POST`/`PUT` still require a
recruiter's token. Every other endpoint below `/api/admin/**` is gated at the URL level to
ADMIN only, not per-method — see `SecurityConfig`.

A new company always starts `PENDING`; only an admin decision moves it to `APPROVED` or
`REJECTED`, and only once — deciding an already-decided company returns 409 `INVALID_STATE`.

```bash
curl -X POST http://localhost:8080/api/companies -H "Authorization: Bearer <recruiterToken>" \
  -H "Content-Type: application/json" -d '{"name":"Acme Corp","website":"https://acme.example.com"}'

curl http://localhost:8080/api/companies/1   # no auth needed

curl -X PATCH http://localhost:8080/api/admin/companies/1/approval \
  -H "Authorization: Bearer <adminToken>" -H "Content-Type: application/json" \
  -d '{"decision":"APPROVED"}'
```

---

## Jobs ✅ (CRUD + lifecycle in Phase 8; search/filtering in Phase 9)

| Method | Path | Role |
|---|---|---|
| POST | `/api/jobs` | RECRUITER (must belong to a company) |
| GET | `/api/jobs` | public — search, filter, sort, paginate |
| GET | `/api/jobs/{id}` | public for PUBLISHED/CLOSED/EXPIRED; DRAFT visible only to the owning company's recruiters or an admin |
| PUT | `/api/jobs/{id}` | RECRUITER (owner) — allowed while DRAFT or PUBLISHED |
| PATCH | `/api/jobs/{id}/publish` | RECRUITER (owner) — requires the company to be APPROVED |
| PATCH | `/api/jobs/{id}/close` | RECRUITER (owner) |
| DELETE | `/api/jobs/{id}` | RECRUITER (owner, DRAFT only) |
| GET | `/api/recruiters/me/jobs` | RECRUITER — jobs this recruiter personally created |

**Ownership is company-wide, not creator-only:** any recruiter belonging to a job's company
may update, publish, close or delete it, not only whoever originally created it — see
`JobService`'s Javadoc. `GET /api/recruiters/me/jobs` is the one place that narrows to
personal authorship specifically, using `jobs.created_by`.

**Search parameters** (all optional, combined with AND): `keyword` (matches the title,
case-insensitively, via the `pg_trgm` index), `location` (exact, case-insensitive),
`employmentType`, `experienceLevel`, `minSalary` (matched against the job's `salaryMax` —
"could this pay at least what I want"), `maxSalary` (matched against `salaryMin` — "within my
budget"), `skill` (jobs requiring a given skill by name). Standard pagination applies
(`page`, `size`, `sort`); `sort` is restricted to an allow-list (`createdAt`, `title`,
`salaryMin`, `salaryMax`) and any other field is silently ignored rather than erroring.
`GET /api/jobs` only ever returns `PUBLISHED` jobs — no filter combination can surface a
`DRAFT` posting through this endpoint.

```bash
curl "http://localhost:8080/api/jobs?keyword=java&location=Pune&employmentType=FULL_TIME&minSalary=800000&page=0&size=10&sort=createdAt,desc"

curl -X POST http://localhost:8080/api/jobs -H "Authorization: Bearer <recruiterToken>" \
  -H "Content-Type: application/json" -d '{
    "title":"Backend Engineer","description":"Build HireHub.","location":"Pune",
    "employmentType":"FULL_TIME","experienceLevel":"SENIOR_LEVEL",
    "salaryMin":1500000,"salaryMax":2500000,
    "skills":[{"skillName":"java","mandatory":true},{"skillName":"postgresql","mandatory":false}]
  }'

curl -X PATCH http://localhost:8080/api/jobs/1/publish -H "Authorization: Bearer <recruiterToken>"
```

---

## Applications ✅

| Method | Path | Role |
|---|---|---|
| POST | `/api/jobs/{jobId}/applications` | CANDIDATE — requires a complete profile and a PUBLISHED, non-expired job |
| GET | `/api/candidates/me/applications` | CANDIDATE — optionally `?status=` |
| GET | `/api/applications/{id}` | the owning CANDIDATE, a RECRUITER from the job's company, or ADMIN |
| PATCH | `/api/applications/{id}/withdraw` | CANDIDATE (owner) |
| GET | `/api/jobs/{jobId}/applications` | RECRUITER (any recruiter from the job's company) — optionally `?status=` |
| PATCH | `/api/applications/{id}/status` | RECRUITER (any recruiter from the job's company) |
| GET | `/api/applications/{id}/history` | same visibility as viewing the application itself |

**Status transitions:** `APPLIED → UNDER_REVIEW → SHORTLISTED → INTERVIEW → SELECTED`, with
`REJECTED` reachable from any active stage and `WITHDRAWN` available to the candidate only
(a recruiter attempting to set it gets 409, same as any other disallowed transition — this
corrects an earlier draft of this doc, which had said 422; the app's convention throughout
is 409 `INVALID_STATE` for "well-formed request, wrong state to do this", not 422).

**Ownership is company-wide**, matching jobs: any recruiter belonging to the job's company —
not only whoever posted it — can view and manage its applicants.

```bash
curl -X POST http://localhost:8080/api/jobs/1/applications -H "Authorization: Bearer <candidateToken>" \
  -H "Content-Type: application/json" -d '{"coverLetter":"I would love to join the team."}'

curl -X PATCH http://localhost:8080/api/applications/1/status -H "Authorization: Bearer <recruiterToken>" \
  -H "Content-Type: application/json" -d '{"status":"SHORTLISTED","note":"Strong portfolio"}'

curl http://localhost:8080/api/applications/1/history -H "Authorization: Bearer <token>"
```

---

## Admin ⏳ (company moderation ✅; user/job/statistics management still pending)

| Method | Path | Status |
|---|---|---|
| GET | `/api/admin/companies?status=PENDING` | ✅ |
| PATCH | `/api/admin/companies/{id}/approval` | ✅ |
| GET | `/api/admin/users` | ⏳ |
| PATCH | `/api/admin/users/{id}/status` | ⏳ |
| GET | `/api/admin/jobs` | ⏳ (Phase 8) |
| GET | `/api/admin/statistics` | ⏳ |

All under `/api/admin/**`, restricted to ADMIN by a single URL-level security rule in
`SecurityConfig` rather than a `@PreAuthorize` repeated on every admin controller method.
