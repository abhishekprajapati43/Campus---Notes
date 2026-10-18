package com.example.techcampushubbackend.config;

import com.example.techcampushubbackend.entity.Note;
import com.example.techcampushubbackend.entity.Pyq;
import com.example.techcampushubbackend.entity.Timetable;
import com.example.techcampushubbackend.entity.TnpNote;
import com.example.techcampushubbackend.repository.CourseRepository;
import com.example.techcampushubbackend.repository.FacultyResourceRepository;
import com.example.techcampushubbackend.repository.NoteRepository;
import com.example.techcampushubbackend.repository.PdfPageIndexRepository;
import com.example.techcampushubbackend.repository.PyqRepository;
import com.example.techcampushubbackend.repository.TimetableRepository;
import com.example.techcampushubbackend.repository.TnpNoteRepository;
import com.example.techcampushubbackend.service.PdfIndexService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * One-time catch-up for documents uploaded before the smart search index
 * existed: walks every content table on startup (in the background) and
 * indexes whatever has no index rows yet. Uploads after this point are
 * indexed by {@link PdfIndexService} directly, so the runner only ever
 * fills the gaps - cheap and idempotent.
 *
 * <p>Skipped on non-Postgres URLs (H2 test profile).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PdfIndexBackfillRunner implements ApplicationRunner {

    private final PdfIndexService pdfIndexService;
    private final PdfPageIndexRepository pdfPageIndexRepository;
    private final NoteRepository noteRepository;
    private final PyqRepository pyqRepository;
    private final TnpNoteRepository tnpNoteRepository;
    private final TimetableRepository timetableRepository;
    private final FacultyResourceRepository facultyResourceRepository;
    private final CourseRepository courseRepository;
    private final Environment environment;

    @Override
    public void run(ApplicationArguments args) {
        String url = environment.getProperty("spring.datasource.url", "");
        if (!url.contains("postgresql")) {
            return;
        }

        Thread.startVirtualThread(() -> {
            try {
                // Let the schema bootstrap + first requests settle first.
                Thread.sleep(5_000);
                backfill();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.warn("PDF index backfill failed: {}", e.getMessage());
            }
        });
    }

    private void backfill() {
        Set<String> indexed = new HashSet<>(
                pdfPageIndexRepository.findIndexedDocumentKeys());

        // Course titles are read OUTSIDE any JPA session here - resolve
        // them once up front so lazy proxies never blow up ("no session").
        Map<Long, String> courseTitles = new HashMap<>();
        for (var course : courseRepository.findAll()) {
            courseTitles.put(course.getId(), course.getTitle());
        }

        int total = 0;

        for (Note row : noteRepository.findAll()) {
            if (needsIndex(PdfIndexService.CAT_NOTES, row.getId(), row.getFileUrl(), indexed)) {
                total += tryIndex(PdfIndexService.CAT_NOTES, row.getId(),
                        row.getFileUrl(), row.getTitle(), row.getSubject(),
                        courseIdOf(row.getCourseBundle()), courseTitles, row.getCourse());
            }
        }

        for (Pyq row : pyqRepository.findAll()) {
            if (needsIndex(PdfIndexService.CAT_PYQ, row.getId(), row.getFileUrl(), indexed)) {
                total += tryIndex(PdfIndexService.CAT_PYQ, row.getId(),
                        row.getFileUrl(), row.getTitle(), row.getSubject(),
                        courseIdOf(row.getCourseBundle()), courseTitles, row.getCourse());
            }
        }

        for (TnpNote row : tnpNoteRepository.findAll()) {
            if (needsIndex(PdfIndexService.CAT_TNP, row.getId(), row.getFileUrl(), indexed)) {
                total += tryIndex(PdfIndexService.CAT_TNP, row.getId(),
                        row.getFileUrl(), row.getTitle(), row.getTopic(),
                        courseIdOf(row.getCourseBundle()), courseTitles, null);
            }
        }

        for (Timetable row : timetableRepository.findAll()) {
            if (needsIndex(PdfIndexService.CAT_TIMETABLE, row.getId(), row.getFileUrl(), indexed)) {
                total += tryIndex(PdfIndexService.CAT_TIMETABLE, row.getId(),
                        row.getFileUrl(), row.getTitle(), row.getExamType(),
                        courseIdOf(row.getCourseBundle()), courseTitles, row.getCourse());
            }
        }

        for (var row : facultyResourceRepository.findAll()) {
            if (needsIndex(PdfIndexService.CAT_RESOURCES, row.getId(), row.getFileUrl(), indexed)) {
                total += tryIndex(PdfIndexService.CAT_RESOURCES, row.getId(),
                        row.getFileUrl(), row.getTitle(), null,
                        courseIdOf(row.getCourseBundle()), courseTitles, null);
            }
        }

        log.info("PDF index backfill complete ({} documents indexed)", total);
    }

    /**
     * One failing document must never abort the whole walk - a broken
     * URL or an unreadable file is logged and skipped.
     */
    private int tryIndex(String category, Long id, String fileUrl, String title,
            String subject, Long courseId, Map<Long, String> courseTitles,
            String program) {
        try {
            pdfIndexService.indexNow(category, id, fileUrl, title, subject,
                    courseId, courseTitles.get(courseId), program);
            return 1;
        } catch (Exception e) {
            log.warn("PDF backfill skipped {} {} ({}): {}",
                    category, id, title, e.getMessage());
            return 0;
        }
    }

    private static boolean needsIndex(String category, Long id, String fileUrl,
            Set<String> indexed) {
        return fileUrl != null && !fileUrl.isBlank()
                && !indexed.contains(category + ":" + id);
    }

    /** FK id is readable on a lazy proxy without touching the database. */
    private static Long courseIdOf(
            com.example.techcampushubbackend.entity.Course bundle) {
        return bundle == null ? null : bundle.getId();
    }
}
