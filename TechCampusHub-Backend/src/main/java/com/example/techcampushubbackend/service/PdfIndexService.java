package com.example.techcampushubbackend.service;

import com.example.techcampushubbackend.entity.PdfPageIndex;
import com.example.techcampushubbackend.repository.PdfPageIndexRepository;
import com.example.techcampushubbackend.utils.PdfTextExtractor;
import com.example.techcampushubbackend.utils.ProgramFilter;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/**
 * Builds and maintains the page-wise text index behind the smart search.
 *
 * <p>Indexing happens exactly once per document - right after the upload
 * (or re-upload) metadata lands in the database. The PDF bytes are
 * downloaded server-side from the stored Cloudinary URL (allowlisted
 * host), run through PDFBox, and one row per page is written to
 * {@code pdf_page_index}. Searches never read the file.
 *
 * <p>All heavy work runs on a small background pool so a faculty upload
 * is never blocked by extraction, and a broken/missing file can never
 * fail the upload itself - it is logged and skipped.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PdfIndexService {

    public static final String CAT_NOTES = "notes";
    public static final String CAT_PYQ = "pyq";
    public static final String CAT_TNP = "tnp";
    public static final String CAT_TIMETABLE = "timetable";
    public static final String CAT_RESOURCES = "resources";

    private final PdfPageIndexRepository repository;
    private final ContentStreamService contentStreamService;
    private final PlatformTransactionManager transactionManager;

    private ExecutorService executor = Executors.newFixedThreadPool(2, task -> {
        Thread thread = new Thread(task, "pdf-indexer");
        thread.setDaemon(true);
        return thread;
    });

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }

    /**
     * Queues a re-index; never throws into the caller's transaction.
     *
     * <p>When called inside an open transaction the work is deferred until
     * after that transaction commits, so the async thread never re-reads a
     * row the caller has not committed yet. Outside a transaction (e.g. the
     * startup backfill) it runs immediately.</p>
     */
    public void reindexAsync(String category, Long contentId, String fileUrl,
            String title, String subject, Long courseId, String courseLabel,
            String program) {
        afterCommit(() -> indexNow(category, contentId, fileUrl, title, subject,
                courseId, courseLabel, program));
    }

    /**
     * Queues the index cleanup after a document was deleted.
     *
     * <p>Defers until after the caller's transaction commits, mirroring
     * {@link #reindexAsync}.</p>
     */
    public void removeIndexAsync(String category, Long contentId) {
        afterCommit(() -> runInTransaction(() ->
                repository.deleteByCategoryAndContentId(category, contentId)));
    }

    /**
     * Runs {@code task} once the surrounding transaction has committed. If no
     * transaction is active the task runs at once.
     */
    private void afterCommit(Runnable task) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            submit(task);
                        }
                    });
        } else {
            submit(task);
        }
    }

    /**
     * Synchronous re-index: drops the document's old rows, downloads the
     * PDF, extracts every page and stores one row per non-empty page.
     * Runs inside its own transaction (safe from any thread).
     */
    public void indexNow(String category, Long contentId, String fileUrl,
            String title, String subject, Long courseId, String courseLabel,
            String program) {
        runInTransaction(() -> repository.deleteByCategoryAndContentId(category, contentId));

        if (fileUrl == null || fileUrl.isBlank()) {
            return;
        }

        byte[] bytes;
        try {
            bytes = contentStreamService.fetchBytes(fileUrl);
        } catch (Exception e) {
            log.warn("PDF indexing skipped for {} {} ({}): {}",
                    category, contentId, title, e.getMessage());
            return;
        }

        List<String> pageTexts;
        try {
            pageTexts = PdfTextExtractor.extractPageTexts(bytes);
        } catch (Exception e) {
            log.warn("PDF indexing skipped for {} {} ({}): not a readable PDF - {}",
                    category, contentId, title, e.getMessage());
            return;
        }

        List<PdfPageIndex> rows = new ArrayList<>();
        for (int index = 0; index < pageTexts.size(); index++) {
            String text = pageTexts.get(index);
            if (text == null || text.isBlank()) {
                continue; // image-only page - nothing searchable
            }
            rows.add(PdfPageIndex.builder()
                    .category(category)
                    .contentId(contentId)
                    .pdfTitle(truncate(title, 300))
                    .courseId(courseId)
                    .courseLabel(truncate(courseLabel, 160))
                    .program(ProgramFilter.normalize(program))
                    .subject(truncate(subject, 200))
                    .pageNumber(index + 1)
                    .content(text)
                    .build());
        }

        if (rows.isEmpty()) {
            log.info("PDF {} {} ({}) has no extractable text", category, contentId, title);
            return;
        }

        runInTransaction(() -> {
            repository.saveAll(rows);
            return null;
        });
        log.info("Indexed {} {} ({}): {} searchable pages",
                category, contentId, title, rows.size());
    }

    private void submit(Runnable task) {
        try {
            executor.submit(() -> {
                try {
                    task.run();
                } catch (Exception e) {
                    log.error("PDF indexing task failed: {}", e.getMessage());
                }
            });
        } catch (RejectedExecutionException ignored) {
            // Shutting down - nothing to do.
        }
    }

    private <T> T runInTransaction(java.util.function.Supplier<T> work) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        return template.execute(status -> work.get());
    }

    private void runInTransaction(Runnable work) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.executeWithoutResult(status -> work.run());
    }

    private static String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }
}
