-- ---------------------------------------------------------------------------
-- Startup index hardening (PostgreSQL).
--
-- Executed by Spring's SQL init AFTER Hibernate's own DDL
-- (spring.jpa.defer-datasource-initialization=true), so the tables this file
-- references always exist. Every statement is idempotent, so the script may run
-- on every start. It is skipped in tests (spring.sql.init.mode=never) because
-- H2 has no partial indexes.
--
-- These two guarantees cannot be expressed in JPA annotations:
--   * a partial index (WHERE clause) for "one PENDING purchase per student and
--     course" - a plain unique constraint would wrongly forbid a re-request
--     after a rejection or an expired enrolment,
--   * a belt-and-braces duplicate of uk_feedback_user_course in case Hibernate's
--     schema update skipped it on an existing table.
--
-- If either CREATE fails because duplicate rows already exist, clean them up
-- first (keep the newest row) and start again:
--
--   DELETE FROM feedback a USING feedback b
--     WHERE a.user_id = b.user_id AND a.course_id = b.course_id
--       AND a.course_id IS NOT NULL AND a.id < b.id;
--
--   DELETE FROM purchases a USING purchases b
--     WHERE a.user_id = b.user_id AND a.course_id = b.course_id
--       AND a.status = 'PENDING' AND b.status = 'PENDING'
--       AND a.id < b.id;
-- ---------------------------------------------------------------------------

-- One course review per purchaser (website feedback keeps course_id NULL and is
-- unaffected: unique indexes treat NULLs as distinct).
CREATE UNIQUE INDEX IF NOT EXISTS uk_feedback_user_course
    ON feedback (user_id, course_id);

-- At most one awaiting-review purchase per student and course. Partial on
-- purpose: rejected/expired/approved history must stay, so a student can always
-- request the same course again later.
CREATE UNIQUE INDEX IF NOT EXISTS uq_purchases_user_course_pending
    ON purchases (user_id, course_id)
    WHERE status = 'PENDING';
