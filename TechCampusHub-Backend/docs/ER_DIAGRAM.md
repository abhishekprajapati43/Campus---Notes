# Entity Relationship Diagram

```mermaid
erDiagram
    USERS ||--o{ PURCHASES        : "buys courses"
    USERS ||--o{ FAVORITES        : "marks favorite"
    USERS ||--o{ FEEDBACK         : "submits"
    USERS ||--o{ NOTIFICATIONS    : "receives (direct)"
    USERS ||--o{ CERTIFICATES     : "is issued"
    USERS ||--o{ PURCHASES        : "reviews (admin)"
    USERS ||--o{ NOTES            : "uploads (faculty)"
    USERS ||--o{ PYQS             : "uploads (faculty)"
    USERS ||--o{ TNP_NOTES        : "uploads (faculty)"
    USERS ||--o{ TIMETABLES       : "uploads (faculty)"
    USERS ||--o{ COURSES          : "creates (faculty)"

    COURSES ||--o{ PURCHASES      : "sold as"
    COURSES ||--o{ NOTES          : "bundles (optional)"
    COURSES ||--o{ PYQS           : "bundles (optional)"
    COURSES ||--o{ TNP_NOTES      : "bundles (optional)"
    COURSES ||--o{ TIMETABLES     : "bundles (optional)"
    COURSES ||--o{ CERTIFICATES   : "issues"
    COURSES ||--o{ FEEDBACK       : "about"

    USERS {
        bigserial id PK
        varchar name
        varchar email UK
        varchar password "BCrypt hash"
        varchar phone
        varchar role "ADMIN | FACULTY | STUDENT"
        varchar aadhaar_number "SHA-256 hash, 64 chars - never exposed"
        text address "faculty postal address"
        varchar gender "optional - profile page"
        varchar enrollment_number "student roll / enrollment number"
        varchar department "faculty home department"
        varchar qualification "faculty highest qualification"
        varchar experience "faculty teaching/industry experience"
        varchar course "student academic profile"
        varchar branch "student branch/specialisation"
        int "year"
        int semester
        boolean declaration_accepted "faculty sign-up consent"
        varchar registration_status "PENDING|APPROVED|REJECTED - faculty start PENDING"
        boolean email_verified
        varchar verification_token
        varchar reset_token
        timestamp reset_token_expires_at
        boolean enabled
        timestamp created_at
    }

    COURSES {
        bigserial id PK
        varchar title
        varchar code UK "optional"
        varchar category
        text description
        numeric price_inr
        numeric mrp_inr "discount anchor - NULL = no discount"
        varchar thumbnail_url "Cloudinary"
        int validity_days
        boolean is_active
        bigint created_by "users.id - faculty/admin creator (soft ref)"
        timestamp created_at
    }

    PURCHASES {
        bigserial id PK
        bigint user_id FK
        bigint course_id FK
        numeric amount_inr
        varchar utr "UPI ref"
        varchar status "PENDING|APPROVED|REJECTED"
        timestamp valid_from
        timestamp valid_until
        timestamp reviewed_at
        bigint reviewed_by FK "admin user"
        varchar rejection_reason
        timestamp created_at
    }

    NOTES {
        bigserial id PK
        varchar title
        varchar subject
        varchar course
        int semester
        varchar description
        varchar file_url "Cloudinary"
        bigint course_id FK "NULL = free"
        bigint created_by "users.id - faculty/admin uploader (soft ref)"
        timestamp created_at
    }

    PYQS {
        bigserial id PK
        varchar title
        varchar subject
        varchar course
        int semester
        int exam_year
        varchar file_url "Cloudinary"
        bigint course_id FK "NULL = free"
        bigint created_by "users.id - faculty/admin uploader (soft ref)"
        timestamp created_at
    }

    TNP_NOTES {
        bigserial id PK
        varchar title
        varchar topic
        varchar description
        varchar file_url "Cloudinary"
        bigint course_id FK "NULL = free"
        bigint created_by "users.id - faculty/admin uploader (soft ref)"
        timestamp created_at
    }

    TIMETABLES {
        bigserial id PK
        varchar title
        varchar course
        int semester
        varchar exam_type "e.g. Mid Sem"
        int exam_year
        varchar day_of_week
        date effective_from
        varchar file_url "Cloudinary"
        varchar file_name "original upload name"
        bigint course_id FK "NULL = free"
        bigint created_by "users.id - faculty/admin uploader (soft ref)"
        timestamp created_at
    }

    CERTIFICATES {
        bigserial id PK
        varchar certificate_no UK
        varchar title
        bigint user_id FK "NULL = template"
        bigint course_id FK
        date issued_on
        varchar file_url "Cloudinary"
        timestamp created_at
    }

    NOTIFICATIONS {
        bigserial id PK
        varchar title
        text message
        varchar kind "INFO|UPDATE|OFFER|ALERT"
        bigint user_id FK "NULL = broadcast"
        timestamp created_at
    }

    FAVORITES {
        bigserial id PK
        bigint user_id FK
        varchar item_type "COURSE|NOTE|PYQ|TNP_NOTE|TIMETABLE|CERTIFICATE"
        bigint item_id "polymorphic reference"
        timestamp created_at
    }

    FEEDBACK {
        bigserial id PK
        bigint user_id FK
        bigint course_id FK
        int rating "1-5"
        text message
        timestamp created_at
    }
```

## Reading the diagram

| Relationship | Meaning |
|---|---|
| `purchases.user_id` → `users` | Who bought the course |
| `purchases.course_id` → `courses` | Which course was bought |
| `purchases.reviewed_by` → `users` | Which admin approved/rejected it |
| `notes.course_id` (nullable) | `NULL` = free note; set = only buyers of that course (plus admins/faculty) can read it. Same rule for `pyqs`, `tnp_notes`, `timetables` |
| `notes.created_by`, `courses.created_by`, `pyqs.created_by`, `tnp_notes.created_by`, `timetables.created_by` (nullable) | `users.id` of the faculty/admin who uploaded it; used for faculty ownership (`GET/PUT /api/faculty/...`) and admin moderation. `NULL` = legacy/admin-seeded content |
| `users.registration_status` | Registration moderation: self-registered faculty are created `PENDING` and cannot log in until an admin moves them to `APPROVED` (Manage Faculty panel). `REJECTED` keeps the application on file but blocks access. Students and seeded admins are always `APPROVED` |
| `notifications.user_id` (nullable) | `NULL` = broadcast to everyone; set = direct message |
| `favorites.item_type + item_id` | Polymorphic pointer: one table can favorite any content type; uniqueness is `(user_id, item_type, item_id)` |
| `certificates.user_id` (nullable) | Set = issued to that student; `NULL` = reusable template |
