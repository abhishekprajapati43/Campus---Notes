-- =====================================================================
-- TechCampus Hub - Sample data
-- Run AFTER docs/schema.sql (or after the app has created the schema).
-- Account credentials in this file:
--   admin@techcampushub.com  /  Admin@1234
--   asha@example.com         /  Student@123
--   rahul@example.com        /  Student@123
-- The password column holds BCrypt hashes (verified with
-- BCryptPasswordEncoder.matches). Regenerate them if you change a password:
--
--   jshell --class-path <spring-security-crypto.jar> <<'EOF'
--   import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
--   System.out.println(new BCryptPasswordEncoder().encode("NewP@ssword"));
--   EOF
--
-- The primary admin is also created automatically at startup from
-- ADMIN_EMAIL / ADMIN_PASSWORD (see docs/ENVIRONMENT.md).
-- =====================================================================

-- ---------------------------------------------------------------------
-- Users (1 admin + 2 students)
-- Hash verified for "Admin@1234"
INSERT INTO users (name, email, password, phone, role, email_verified, enabled, created_at)
SELECT 'Administrator', 'admin@techcampushub.com',
       '$2a$10$sBWt4KYhsRO/XuLacFovGeGcz/HMuTikKHIpVKBQo83mpIfcNb/i2',
       NULL, 'ADMIN', TRUE, TRUE, NOW()
WHERE NOT EXISTS (SELECT 1 FROM users WHERE email = 'admin@techcampushub.com');

-- Hash verified for "Student@123"
INSERT INTO users (name, email, password, phone, role, email_verified, enabled, created_at)
SELECT 'Asha Kumar', 'asha@example.com',
       '$2a$10$v1aqzZyGEypxO244n6XqIetXXDpfTkDv.g2LLyuD3DGL3XsgBVXTm',
       '+91 9876543210', 'STUDENT', TRUE, TRUE, NOW()
WHERE NOT EXISTS (SELECT 1 FROM users WHERE email = 'asha@example.com');

INSERT INTO users (name, email, password, phone, role, email_verified, enabled, created_at)
SELECT 'Rahul Verma', 'rahul@example.com',
       '$2a$10$v1aqzZyGEypxO244n6XqIetXXDpfTkDv.g2LLyuD3DGL3XsgBVXTm',
       '+91 9123456780', 'STUDENT', FALSE, TRUE, NOW()
WHERE NOT EXISTS (SELECT 1 FROM users WHERE email = 'rahul@example.com');

-- ---------------------------------------------------------------------
-- Courses
INSERT INTO courses (title, code, category, description, price_inr,
                     thumbnail_url, validity_days, is_active, created_at)
VALUES ('MCA Semester 1 Bundle', 'MCA-S1', 'MCA',
        'All notes, PYQs and timetables for MCA semester 1',
        499.00, 'https://res.cloudinary.com/demo/image/upload/mca_s1.png',
        180, TRUE, NOW()),

       ('MCA Semester 2 Bundle', 'MCA-S2', 'MCA',
        'Complete semester 2 study material',
        599.00, 'https://res.cloudinary.com/demo/image/upload/mca_s2.png',
        180, TRUE, NOW()),

       ('TNP Placement Prep', 'TNP-101', 'Placement',
        'Aptitude, interviews, resume and mock tests',
        299.00, 'https://res.cloudinary.com/demo/image/upload/tnp.png',
        90, TRUE, NOW())
ON CONFLICT DO NOTHING;

-- ---------------------------------------------------------------------
-- Purchases (pending + approved)
INSERT INTO purchases (user_id, course_id, amount_inr, utr, status,
                       valid_from, valid_until, created_at)
VALUES ((SELECT id FROM users WHERE email='asha@example.com'),
        (SELECT id FROM courses WHERE code='MCA-S1'),
        499.00, 'UTR2938475610', 'APPROVED',
        NOW(), NOW() + INTERVAL '180' DAY, NOW()),

       ((SELECT id FROM users WHERE email='rahul@example.com'),
        (SELECT id FROM courses WHERE code='MCA-S2'),
        599.00, 'UTR5647382910', 'PENDING',
        NULL, NULL, NOW());

-- ---------------------------------------------------------------------
-- Notes (2 free, 1 bundled)
INSERT INTO notes (title, subject, course, semester, description, file_url, course_id, created_at)
VALUES ('DBMS Unit 1 Notes', 'DBMS', 'MCA', 1,
        'ER models, normalization basics',
        'https://res.cloudinary.com/demo/raw/upload/v1/techcampushub/notes/dbms_unit1.pdf',
        NULL, NOW()),

       ('Free Aptitude Cheat Sheet', 'Aptitude', 'MCA', 1,
        'Quick formulas for placement prep',
        'https://res.cloudinary.com/demo/raw/upload/v1/techcampushub/notes/aptitude.pdf',
        NULL, NOW()),

       ('MCA S1 Complete Notes Pack', 'All subjects', 'MCA', 1,
        'Only for students who purchased the MCA-S1 bundle',
        'https://res.cloudinary.com/demo/raw/upload/v1/techcampushub/notes/mca_s1_pack.pdf',
        (SELECT id FROM courses WHERE code='MCA-S1'), NOW());

-- ---------------------------------------------------------------------
-- PYQs
INSERT INTO pyqs (title, subject, course, semester, exam_year, description, file_url, course_id, created_at)
VALUES ('DBMS End Semester 2024', 'DBMS', 'MCA', 1, 2024,
        'University paper with solution hints',
        'https://res.cloudinary.com/demo/raw/upload/v1/techcampushub/pyqs/dbms_2024.pdf',
        (SELECT id FROM courses WHERE code='MCA-S1'), NOW()),
       ('OS End Semester 2023', 'Operating Systems', 'MCA', 1, 2023,
        'Previous year paper',
        'https://res.cloudinary.com/demo/raw/upload/v1/techcampushub/pyqs/os_2023.pdf',
        NULL, NOW());

-- ---------------------------------------------------------------------
-- TNP notes
INSERT INTO tnp_notes (title, topic, description, file_url, course_id, created_at)
VALUES ('Top 50 Interview Questions', 'Interviews',
        'Frequently asked technical interview questions',
        'https://res.cloudinary.com/demo/raw/upload/v1/techcampushub/tnp/interview_qa.pdf',
        (SELECT id FROM courses WHERE code='TNP-101'), NOW()),
       ('Resume Template for Freshers', 'Resume',
        'ATS-friendly resume template',
        'https://res.cloudinary.com/demo/raw/upload/v1/techcampushub/tnp/resume.pdf',
        NULL, NOW());

-- ---------------------------------------------------------------------
-- Timetables
INSERT INTO timetables (title, course, semester, day_of_week, effective_from, file_url, course_id, created_at)
VALUES ('MCA Sem 1 Weekly Timetable', 'MCA', 1, 'Mon-Fri', CURRENT_DATE,
        'https://res.cloudinary.com/demo/raw/upload/v1/techcampushub/timetables/mca_s1.pdf',
        NULL, NOW());

-- ---------------------------------------------------------------------
-- Certificates
INSERT INTO certificates (certificate_no, title, user_id, course_id, issued_on, file_url, created_at)
VALUES ('TCH-2026-0001', 'MCA Semester 1 Completion',
        (SELECT id FROM users WHERE email='asha@example.com'),
        (SELECT id FROM courses WHERE code='MCA-S1'), CURRENT_DATE,
        'https://res.cloudinary.com/demo/image/upload/v1/techcampushub/certificates/c1.png',
        NOW());

-- ---------------------------------------------------------------------
-- Notifications (1 broadcast, 1 direct)
INSERT INTO notifications (title, message, kind, user_id, created_at)
VALUES ('Exam schedule published', 'Mid-terms start next Monday. Check your timetable.',
        'ALERT', NULL, NOW()),
       ('Your purchase was approved', 'You now have access to MCA Semester 1 Bundle.',
        'INFO', (SELECT id FROM users WHERE email='asha@example.com'), NOW());

-- ---------------------------------------------------------------------
-- Favorites
INSERT INTO favorites (user_id, item_type, item_id, created_at)
VALUES ((SELECT id FROM users WHERE email='asha@example.com'),
        'COURSE', (SELECT id FROM courses WHERE code='TNP-101'), NOW()),
       ((SELECT id FROM users WHERE email='asha@example.com'),
        'NOTE', (SELECT id FROM notes WHERE title='Free Aptitude Cheat Sheet'), NOW());

-- ---------------------------------------------------------------------
-- Feedback
INSERT INTO feedback (user_id, course_id, rating, message, created_at)
VALUES ((SELECT id FROM users WHERE email='asha@example.com'),
        (SELECT id FROM courses WHERE code='MCA-S1'),
        5, 'Great bundle, notes were very helpful!', NOW());
