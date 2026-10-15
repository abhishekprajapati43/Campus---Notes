# Deploying TechCampusHub-Backend on Railway.app

The backend is 12-factor configured: **every setting below already reads from an
environment variable** (`application.properties` uses `${VAR:default}`), so local
development keeps working with zero changes while production only needs env vars.

Stack: Spring Boot **4.1.1**, Java **25**, PostgreSQL, Cloudinary, JWT (jjwt).

---

## 1. Service setup

| Item | Value |
|---|---|
| Build command | `./mvnw clean package -DskipTests` |
| Start command | `java -jar target/TechCampusHub-Backend-0.0.1-SNAPSHOT.jar` |
| Port | Railway injects `PORT` — the app honors it (`server.port=${SERVER_PORT:${PORT:8080}}`). Set your Railway service port to **5050** in **Settings → Port**. |
| Health check | `GET /api/health` |
| Postgres | Add Railway's **Postgres** plugin to the project and link it to the service — its variables are auto-injected (see DB section). |

> `spring-boot-devtools` is excluded from the packaged jar automatically, so no
> extra step is needed for it.

---

## 2. Environment variables

### Required

| Variable | Example / notes |
|---|---|
| `DB_URL` | `jdbc:postgresql://host:5432/railway`. **Optional if Postgres is linked** — when unset, the app falls back to Railway's automatic `PGHOST` / `PGPORT` / `PGDATABASE`, so linking the plugin is enough. |
| `DB_USERNAME` / `DB_PASSWORD` | Optional if linked — falls back to `PGUSER` / `PGPASSWORD` automatically. |
| `JWT_SECRET` | **Mandatory in production.** Minimum 32 characters, long & random. Generate: `[Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(48))` (PowerShell) or `openssl rand -base64 48`. The built-in default is dev-only. |
| `CORS_ALLOWED_ORIGINS` | Frontend origin(s), comma separated — e.g. `https://your-site.netlify.app`. Without it the browser blocks all API calls from the deployed frontend. |
| `APP_BASE_URL` | Public backend URL, e.g. `https://your-service.up.railway.app` — used to build e-mail verification / password-reset links. |
| `CLOUDINARY_CLOUD_NAME`, `CLOUDINARY_API_KEY`, `CLOUDINARY_API_SECRET` | Required for PDF/image uploads (only the resulting secure URL is stored in the DB). |

### Strongly recommended

| Variable | Notes |
|---|---|
| `ADMIN_EMAIL`, `ADMIN_PASSWORD`, `ADMIN_NAME` | Bootstrap admin created on startup. **Change `ADMIN_PASSWORD` — the default `Admin@1234` must never exist in production.** |
| `PORT` | Injected by Railway (set service port 5050). Only set manually for other platforms. |
| `SPRING_DEVTOOLS_RESTART_ENABLED` | `false` (belt-and-braces when running from source instead of the jar). |

### Optional / tunable

| Variable | Default | Notes |
|---|---|---|
| `DB_DDL_AUTO` | `update` | Schema auto-update from JPA entities. Fine for a fresh Railway Postgres; for long-lived data consider `validate` + migrations later. |
| `DB_POOL_SIZE` | `10` | Hikari pool size — keep ≤ Railway plan connections. |
| `DB_SHOW_SQL` / `DB_FORMAT_SQL` | `false` | Keep `false` in production. |
| `JWT_EXPIRATION_MS` | `86400000` | Token lifetime (default 24 h). |
| `REQUIRE_EMAIL_VERIFICATION` | `false` | `true` blocks login until e-mail verified — needs SMTP below. |
| `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_SMTP_AUTH`, `MAIL_SMTP_STARTTLS`, `MAIL_FROM` | empty | No host configured ⇒ verification/reset links are **logged to console** instead of sent. |
| `MAX_FILE_SIZE` / `MAX_REQUEST_SIZE` | `15MB` / `20MB` | Multipart upload limits. |
| `CLOUDINARY_FOLDER` | `techcampushub` | Upload folder in Cloudinary. |

---

## 3. Deploy checklist

1. Push the repo to GitHub and create a Railway project from it.
2. Add the **Postgres** plugin and link it to the service (DB vars auto-inject).
3. Set the service port to **5050** (Settings → Port).
4. Add the **Required** + **Strongly recommended** variables above.
5. Deploy, then verify: `https://<service>.up.railway.app/api/health` → `{"status":"UP"}`.
6. Update the frontend's `TechCampusHub-Frontend/js/env.js` to point at the new
   backend origin and redeploy the frontend (see below).

## 4. Frontend ↔ backend URL

The frontend reads its API base URL from exactly one place:

```
TechCampusHub-Frontend/js/env.js   →   window.__API_BASE_URL__ = "http://localhost:8080";
```

`js/config.js` picks it up (`window.__API_BASE_URL__ || "http://localhost:8080"`).
For production change that single line to e.g.

```js
window.__API_BASE_URL__ = "https://your-service.up.railway.app";
```

No other file hardcodes the backend host (verified by repo scan), and remember to
list the frontend origin in `CORS_ALLOWED_ORIGINS`.
