# TechCampus Hub

A college learning portal where students buy and read course notes, PYQs,
timetables and TNP resources, faculty manage their own content, and admins run
the whole platform — all through a secure, PDF-first web app.

**Courses:** MCA (2 years / 4 semesters) · B.Tech (4 years / 8 semesters) ·
BCA (3 years / 6 semesters) · TNP resources shared across every course.

## Features

- **Auth** — register / login with JWT, role-based access (`ADMIN`, `FACULTY`, `STUDENT`)
- **Students** — browse & purchase courses, read notes/PYQs/timetables (bundle-gated),
  favourites with one-click toggle, in-browser secure PDF viewer, search with
  autocomplete, notifications, certificates, feedback
- **Faculty** — workspace for their own notes & courses (create/update, no deletes)
- **Admin** — full CRUD for courses/notes/PYQs/TNP/timetables, purchase
  approvals, validity extensions, notifications, file uploads
- **Program scoping** — notes/PYQs/timetables are scoped per program
  (MCA / B.Tech / BCA); TNP is shared; search and suggestions respect the same rules

## Stack

| Layer | Choice |
|---|---|
| Backend | Java 25 · Spring Boot 4.1.1 · Spring Security 7 · PostgreSQL · JWT |
| Frontend | Plain HTML/CSS/JS (ES modules) · pdf.js viewer · no build step |
| Files | Cloudinary (only secure URLs stored in the DB) |
| Deploy | Docker · Render Blueprint (`render.yaml`) |

## Repository layout

```
TechCampus-Hub/
├── TechCampusHub-Backend/     Spring Boot API (Maven wrapper, Dockerfile, docs/)
├── TechCampusHub-Frontend/    Static frontend (HTML/JS/CSS, pdf.js vendor)
├── render.yaml                Render Blueprint: backend + Postgres + static site
├── start-project.bat          Local dev launcher (backend + live server)
├── sql/                       SQL scripts
└── presentation/              Project presentation
```

## Quick start (local development)

Prerequisites: **JDK 25+**, **PostgreSQL 14+**, any static file server
(Live Server extension works fine).

```bash
# Backend (defaults: localhost:5432 / techcampushub_db / postgres)
cd TechCampusHub-Backend
./mvnw spring-boot:run          # Windows: mvnw.cmd spring-boot:run

# Frontend — serve the TechCampusHub-Frontend folder on :5500, e.g.
#   VS Code Live Server, or just run start-project.bat (does both)
```

- API health: <http://localhost:8080/api/health>
- Swagger: <http://localhost:8080/swagger-ui.html>
- Frontend: <http://127.0.0.1:5500>
- The admin account is seeded on first start from `ADMIN_EMAIL` / `ADMIN_PASSWORD`
  (see [TechCampusHub-Backend/docs/ENVIRONMENT.md](TechCampusHub-Backend/docs/ENVIRONMENT.md)).

```bash
# Tests (87: unit + integration; integration runs on in-memory H2 — no external services)
cd TechCampusHub-Backend
./mvnw test
```

The frontend automatically talks to `localhost:8080` when opened from
`localhost`/`127.0.0.1`, and to the deployed backend everywhere else
(configured in `js/env.js`, overridable with `?api=<url>`).

## Deployment

One-click via Render Blueprint — see **[DEPLOYMENT.md](DEPLOYMENT.md)** for the
step-by-step walkthrough (backend + managed Postgres + static frontend,
all defined in [`render.yaml`](render.yaml)).

## Documentation

| Doc | Contents |
|---|---|
| [DEPLOYMENT.md](DEPLOYMENT.md) | Deploy the whole project on Render, free tier |
| [TechCampusHub-Backend/README.md](TechCampusHub-Backend/README.md) | API overview, structure, design notes |
| [TechCampusHub-Backend/docs/API.md](TechCampusHub-Backend/docs/API.md) | Every endpoint, payloads, status codes |
| [TechCampusHub-Backend/docs/ENVIRONMENT.md](TechCampusHub-Backend/docs/ENVIRONMENT.md) | All environment variables |
| [TechCampusHub-Backend/docs/ER_DIAGRAM.md](TechCampusHub-Backend/docs/ER_DIAGRAM.md) | Database ER diagram |
