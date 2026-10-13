# TechCampus Hub — API Documentation

Base URL: `http://localhost:8080`

Interactive docs (Swagger UI): `http://localhost:8080/swagger-ui.html`

## Response envelope

Success:

```json
{ "success": true, "message": "optional message", "data": { } }
```

Error:

```json
{
  "timestamp": "2026-10-13T12:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "path": "/api/auth/register",
  "fieldErrors": { "email": "email must be valid" }
}
```

## Authentication

Send the JWT obtained from login as:

```
Authorization: Bearer <token>
```

| Code | Meaning |
|---|---|
| `200` | OK |
| `201` | Created |
| `400` | Validation / semantic error (see `fieldErrors`) |
| `401` | Missing/invalid/expired token, or wrong credentials |
| `403` | Authenticated but not allowed (role rules) |
| `404` | Resource not found (also used to hide gated content) |
| `409` | Conflict (duplicate e-mail, duplicate certificate no.) |
| `503` | File storage not configured (Cloudinary env vars missing) |

---

## Auth — `/api/auth` (public unless noted)

| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/api/auth/register` | public | Register a STUDENT account; sends verification e-mail |
| POST | `/api/auth/login` | public | Returns `{token, tokenType:"Bearer", expiresInMs, user}` |
| POST | `/api/auth/verify-email` | public | Body `{"token":"..."}` or `?token=...` |
| GET | `/api/auth/verify-email?token=` | public | Same, for the link clicked in the e-mail |
| POST | `/api/auth/resend-verification` | public | Body `{"email":"..."}` |
| POST | `/api/auth/forgot-password` | public | Always `200` (never leaks account existence) |
| POST | `/api/auth/reset-password` | public | Body `{"token":"...","newPassword":"..."}`; token valid 30 min, single use |
| GET | `/api/auth/me` | token | Current profile |
| PUT | `/api/auth/profile` | token | Update `name` / `phone` |
| PUT | `/api/auth/change-password` | token | `{"currentPassword","newPassword"}` |

### Register

```http
POST /api/auth/register
Content-Type: application/json

{ "name": "Asha Kumar", "email": "asha@example.com", "password": "Secret@123", "phone": "+91 9876543210" }
```

`201` → `data` is the user (`role: "STUDENT"`, `emailVerified: false`).
`409` if the e-mail already exists. Password rules: 8–72 characters.

### Login

```http
POST /api/auth/login
{ "email": "asha@example.com", "password": "Secret@123" }
```

`200` → `{ "token": "eyJ...", "tokenType": "Bearer", "expiresInMs": 86400000, "user": {...} }`

`401` for wrong e-mail/password (identical message for both, no account probing).
`403` when `REQUIRE_EMAIL_VERIFICATION=true` and the address is unverified.

---

## Courses — `/api/courses` (public read)

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/api/courses` | public | Active courses only |
| GET | `/api/courses/{id}` | public | Single course |
| GET | `/api/admin/courses` | ADMIN | All courses incl. inactive |
| POST | `/api/admin/courses` | ADMIN | Create |
| PUT | `/api/admin/courses/{id}` | ADMIN | Full update (soft delete via `active:false`) |
| DELETE | `/api/admin/courses/{id}` | ADMIN | Soft delete (`active=false`) |

```json
// CourseRequest
{ "title": "MCA Semester 1", "code": "MCA-S1", "category": "MCA",
  "description": "Complete bundle", "priceInr": 499.00,
  "thumbnailUrl": "https://res.cloudinary.com/.../thumb.png",
  "validityDays": 180, "active": true }
```

---

## Purchases — `/api/purchases`

| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/api/purchases` | token | Submit `{"courseId":1,"utr":"UTR..."}` → `201` PENDING |
| GET | `/api/purchases/mine` | token | Full history |
| GET | `/api/purchases/my-courses` | token | Approved + unexpired only |
| GET | `/api/admin/purchases?status=PENDING` | ADMIN | `status` optional: PENDING/APPROVED/REJECTED/all |
| POST | `/api/admin/purchases/{id}/approve` | ADMIN | Sets `validFrom=now`, `validUntil=now+course.validityDays` |
| POST | `/api/admin/purchases/{id}/reject` | ADMIN | Body `{"reason":"..."}` optional |
| POST | `/api/admin/purchases/{id}/extend-validity` | ADMIN | `{"days":30}` extends from `validUntil` (or now if expired) |

`400` on: duplicate pending request, already-active access, approving twice.

---

## Content — notes / PYQs / TNP notes / timetables

Student endpoints return only what the caller may see: free items always,
bundle items only with an active purchase (admins see everything).

| Method | Path | Auth |
|---|---|---|
| GET | `/api/notes` · `/api/pyqs` · `/api/tnp-notes` · `/api/timetables` | token |
| GET | `/api/notes/{id}` (same pattern) | token |
| GET | `/api/admin/notes` · `/api/admin/pyqs` · `/api/admin/tnp-notes` · `/api/admin/timetables` | ADMIN |
| POST | `/api/admin/notes` (same pattern) | ADMIN |
| PUT | `/api/admin/notes/{id}` (same pattern) | ADMIN |
| DELETE | `/api/admin/notes/{id}` (same pattern) | ADMIN |

Gated items return `404` to users without access (existence is not leaked).

```json
// NoteRequest — fileUrl comes from POST /api/admin/files
{ "title": "Unit 3 Notes", "subject": "DBMS", "course": "MCA",
  "semester": 1, "description": "Normalization deep dive",
  "fileUrl": "https://res.cloudinary.com/demo/raw/upload/v1/techcampushub/notes/x.pdf",
  "courseId": 1 }   // null/omitted = free; set = purchase-gated
```

**Downloading PDFs:** use the dedicated download endpoint, which enforces the
same access rules and responds `302` with a `Location` header pointing at the
Cloudinary secure URL (the binary never passes through this API):

```http
GET /api/notes/{id}/download
GET /api/pyqs/{id}/download
GET /api/tnp-notes/{id}/download
GET /api/timetables/{id}/download
```

Requires a token; returns `401` anonymous and `404` for content the caller
cannot access. The same URL is also exposed as `fileUrl` in each item response,
so clients may link directly if preferred.

---

## Certificates

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/api/certificates/mine` | token | Certificates issued to the caller |
| GET | `/api/admin/certificates` | ADMIN | All |
| POST | `/api/admin/certificates` | ADMIN | Issue; `certificateNo` auto-generated if blank |
| PUT | `/api/admin/certificates/{id}` | ADMIN | Update |
| DELETE | `/api/admin/certificates/{id}` | ADMIN | Delete |

`409` if `certificateNo` already exists.

---

## Files — `/api/admin/files` (ADMIN)

```http
POST /api/admin/files
Content-Type: multipart/form-data

file=@paper.pdf&folder=notes
```

→ `200 { "url": "https://res.cloudinary.com/...", "publicId": "...", "originalName": "paper.pdf", "sizeBytes": 123456 }`

Allowed: PDF, PNG, JPEG, WEBP; max 15 MB. Store the returned `url` in the
content record. `503` when Cloudinary credentials are not configured.

---

## Favorites — `/api/favorites` (token)

| Method | Path | Description |
|---|---|---|
| GET | `/api/favorites?itemType=NOTE` | Optional filter |
| POST | `/api/favorites` | `{"itemType":"COURSE","itemId":1}` — idempotent `201` |
| DELETE | `/api/favorites/{itemType}/{itemId}` | `200` |

`itemType`: `COURSE`, `NOTE`, `PYQ`, `TNP_NOTE`, `TIMETABLE`, `CERTIFICATE`.
`404` if the referenced item does not exist.

---

## Feedback — `/api/feedback`

| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/api/feedback` | token | `{"courseId":1,"rating":5,"message":"Great!"}` |
| GET | `/api/feedback/mine` | token | Own submissions |
| GET | `/api/admin/feedback` | ADMIN | All submissions |

`400` when `rating` is outside 1–5.

---

## Notifications — `/api/notifications`

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/api/notifications` | token | Broadcasts + messages addressed to me |
| GET | `/api/admin/notifications` | ADMIN | All |
| POST | `/api/admin/notifications` | ADMIN | Send; omit `userId` to broadcast |
| DELETE | `/api/admin/notifications/{id}` | ADMIN | Delete |

```json
{ "title": "Exam schedule out", "message": "Mid-terms start Monday",
  "kind": "ALERT", "userId": null }
```

`kind`: `INFO` (default), `UPDATE`, `OFFER`, `ALERT`. Unknown kind → `400`.

---

## Health

`GET /api/health` (public) → `{ "status": "UP", "service": "TechCampusHub-Backend", ... }`
