# TechCampus Hub — Backend

Production-ready REST API for the TechCampus Hub learning portal.

| Layer | Choice |
|---|---|
| Framework | Java 25 · Spring Boot **4.1.1** (Spring MVC, Spring Security 7) |
| Database | PostgreSQL + Spring Data JPA (Hibernate) |
| Auth | Stateless **JWT** (HS256) · **BCrypt** password hashing · role-based access (`ADMIN`, `FACULTY`, `STUDENT`) |
| Files | **Cloudinary** (only the secure URL is stored in PostgreSQL) |
| Build | Maven (wrapper included) |
| Docs | Swagger UI · Markdown docs in [`docs/`](docs/) |

## Features

- **Auth:** register, login (JWT), e-mail verification, forgot/reset password, change password, profile update
- **Roles:** `ADMIN` (everything under `/api/admin/**`) · `FACULTY` (own-content workspace under `/api/faculty/**`: create/list/update notes & courses, upload files, **no deletes**) · `STUDENT`; enforced in the filter chain *and* with `@PreAuthorize`
- **Admin:** CRUD courses / notes / PYQs / TNP notes / timetables / certificates, approve·reject purchases, extend course validity, send notifications, upload files to Cloudinary
- **Student:** browse & purchase courses, view purchased courses, read notes/PYQs/TNP/timetables (bundle-gated content requires an active purchase), manage favorites, submit feedback, read notifications, view certificates
- **Quality:** bean validation on every request body, global JSON error handler with correct HTTP status codes, CORS restricted to configured origins

## Project structure

```
src/main/java/com/example/techcampushubbackend/
├── controller/   REST endpoints (student + /api/admin)
├── service/      Business logic, access checks, transactions
├── repository/   Spring Data JPA repositories
├── entity/       JPA entities + enums
├── dto/          Request/response records with validation
├── config/       AppProperties, Cloudinary, OpenAPI, data seeder
├── security/     JWT service/filter, SecurityConfig, principal
├── exception/    ApiException hierarchy + GlobalExceptionHandler
└── utils/        CurrentUserService, TokenUtils
```

## Quick start

Prerequisites: **JDK 25+**, **PostgreSQL 14+**.

```bash
# 1. Create the database
createdb techcampushub_db          # or: psql -c "CREATE DATABASE techcampushub_db;"

# 2. Configure (optional - defaults work for local dev)
export DB_USERNAME=postgres
export DB_PASSWORD=postgres
export JWT_SECRET="$(openssl rand -base64 48)"
export ADMIN_EMAIL=admin@techcampushub.com
export ADMIN_PASSWORD=Admin@1234

# 3. Run
cd TechCampusHub-Backend
./mvnw spring-boot:run             # Windows: mvnw.cmd spring-boot:run
```

- API root: <http://localhost:8080/api/health>
- Swagger UI: <http://localhost:8080/swagger-ui.html>

The admin account is created automatically on first start (see `ADMIN_*` variables).

### Run the tests

```bash
./mvnw test        # 87 tests (unit + integration); integration runs on in-memory H2 (PostgreSQL mode), no external services
```

### Build a jar

```bash
./mvnw -DskipTests package
java -jar target/TechCampusHub-Backend-0.0.1-SNAPSHOT.jar
```

## Documentation

| Doc | Contents |
|---|---|
| [docs/API.md](docs/API.md) | Every endpoint, payloads, status codes |
| [docs/schema.sql](docs/schema.sql) | Full PostgreSQL DDL |
| [docs/ER_DIAGRAM.md](docs/ER_DIAGRAM.md) | Mermaid ER diagram + relationship notes |
| [docs/ENVIRONMENT.md](docs/ENVIRONMENT.md) | All environment variables + production checklist |
| [docs/sample-data.sql](docs/sample-data.sql) | Demo users, courses, notes, purchases |
| Swagger UI | Live, interactive (`/swagger-ui.html`) |

## Typical flow

```bash
# 1. Login as the seeded admin
TOKEN=$(curl -s localhost:8080/api/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"admin@techcampushub.com","password":"Admin@1234"}' \
  | jq -r '.data.token')

# 2. Create a course
curl -s localhost:8080/api/admin/courses -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"title":"MCA S1","priceInr":499,"validityDays":180}'

# 3. Upload a PDF to Cloudinary, then save a note with the returned URL
curl -s localhost:8080/api/admin/files -H "Authorization: Bearer $TOKEN" \
  -F file=@notes.pdf -F folder=notes
```

See [docs/API.md](docs/API.md) for the complete list.

## Design notes

- **Only Cloudinary URLs are persisted** — the API never stores file binaries.
- **Course deletion is a soft delete** (`active=false`) so existing purchases keep working.
- **Bundle-gated content returns 404**, not 403, so paid material's existence isn't leaked.
- **Forgot-password always returns 200**, whether or not the e-mail exists.
- **Without SMTP configured**, verification/reset links are logged to the console, so the auth flows work in development and CI.
