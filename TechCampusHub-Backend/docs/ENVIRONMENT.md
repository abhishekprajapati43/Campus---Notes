# Environment Variables

Every setting below is optional for local development — sensible defaults are
built in. **In production, override at least `JWT_SECRET`, the `DB_*` values,
`ADMIN_PASSWORD` and the `CLOUDINARY_*` triple.**

| Variable | Default | Purpose |
|---|---|---|
| `SERVER_PORT` | `8080` | HTTP port |
| **Database** | | |
| `DB_URL` | `jdbc:postgresql://localhost:5432/techcampushub_db` | JDBC URL |
| `PG_CONNECTION_URL` | *(empty)* | libpq connection string (Render's `connectionString`); auto-converted to JDBC URL + user/password at startup. Ignored when `DB_URL` is set. |
| `DATABASE_URL` | *(empty)* | Same, generic PaaS variant (Railway); tried after `PG_CONNECTION_URL`. |
| `DB_USERNAME` | `postgres` | DB user |
| `DB_PASSWORD` | `postgres` | DB password |
| `DB_POOL_SIZE` | `10` | HikariCP max connections |
| `DB_DDL_AUTO` | `update` | Hibernate DDL mode (`update`, `validate`, `create-drop`, `none`) |
| `DB_SHOW_SQL` | `false` | Log SQL statements |
| `DB_FORMAT_SQL` | `false` | Pretty-print logged SQL |
| **JWT** | | |
| `JWT_SECRET` | dev-only value ⚠️ | HS256 secret, **min 32 chars, must be random in production**. When unset/blank/unchanged from the repository default, the app logs a warning and signs with a random per-process key instead (restarts log everyone out) - never with the public default. |
| `JWT_EXPIRATION_MS` | `86400000` (24 h) | Access-token lifetime |
| **Application** | | |
| `APP_BASE_URL` | `http://localhost:8080` | Public base URL used in verification/reset links |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5500,http://localhost:3000,http://localhost:5173` | Comma-separated allowed origins |
| `REQUIRE_EMAIL_VERIFICATION` | `false` | When `true`, login fails with `403` until verified |
| **Bootstrap admin** | | |
| `ADMIN_EMAIL` | `admin@techcampushub.com` | First admin, created at startup if absent |
| `ADMIN_PASSWORD` | `Admin@1234` | ⚠️ Change in production - startup logs a warning while the default is configured |
| `ADMIN_NAME` | `Administrator` | Display name |
| **Mail** | | |
| `MAIL_HOST` | *(empty)* | SMTP host; empty = e-mails are logged to console |
| `MAIL_PORT` | `587` | SMTP port |
| `MAIL_USERNAME` | *(empty)* | SMTP user |
| `MAIL_PASSWORD` | *(empty)* | SMTP password |
| `MAIL_SMTP_AUTH` | `false` | Enable SMTP auth |
| `MAIL_SMTP_STARTTLS` | `true` | Enable STARTTLS |
| `MAIL_FROM` | `no-reply@techcampushub.com` | From address |
| **Cloudinary** | | |
| `CLOUDINARY_CLOUD_NAME` | *(empty)* | Cloud name |
| `CLOUDINARY_API_KEY` | *(empty)* | API key |
| `CLOUDINARY_API_SECRET` | *(empty)* | API secret |
| `CLOUDINARY_FOLDER` | `techcampushub` | Root folder in the Cloudinary media library |
| **Uploads** | | |
| `MAX_FILE_SIZE` | `15MB` | Multipart per-file limit |
| `MAX_REQUEST_SIZE` | `20MB` | Multipart per-request limit |

## Production checklist

```bash
export JWT_SECRET="$(openssl rand -base64 48)"
export DB_URL="jdbc:postgresql://db-host:5432/techcampushub_db"
export DB_USERNAME="techcampus"
export DB_PASSWORD="<strong-password>"
export DB_DDL_AUTO="validate"          # schema managed by docs/schema.sql
export ADMIN_PASSWORD="<strong-password>"
export APP_BASE_URL="https://api.yourdomain.com"
export CORS_ALLOWED_ORIGINS="https://yourdomain.com"
export REQUIRE_EMAIL_VERIFICATION="true"
export MAIL_HOST="smtp.yourdomain.com"
export MAIL_USERNAME="no-reply@yourdomain.com"
export MAIL_PASSWORD="<smtp-password>"
export MAIL_SMTP_AUTH="true"
export CLOUDINARY_CLOUD_NAME="..."
export CLOUDINARY_API_KEY="..."
export CLOUDINARY_API_SECRET="..."
```
