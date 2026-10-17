package com.example.techcampushubbackend.utils;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Page-wise text extraction for the smart search index (Apache PDFBox).
 * Runs exactly once per document - at upload time - so searches never
 * touch the stored file.
 */
public final class PdfTextExtractor {

    /** Safety caps for absurd documents (scanned/image PDFs yield blanks). */
    private static final int MAX_PAGES = 2000;
    private static final int MAX_CHARS_PER_PAGE = 20000;

    private PdfTextExtractor() {
    }

    /**
     * Extracts the text of every page (index 0 = PDF page 1). Pages without
     * a text layer come back blank; the caller decides whether to store them.
     */
    public static List<String> extractPageTexts(byte[] pdfBytes) throws IOException {
        List<String> pages = new ArrayList<>();

        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            int pageCount = Math.min(document.getNumberOfPages(), MAX_PAGES);

            for (int pageNumber = 1; pageNumber <= pageCount; pageNumber++) {
                PDFTextStripper stripper = new PDFTextStripper();
                stripper.setStartPage(pageNumber);
                stripper.setEndPage(pageNumber);
                stripper.setSortByPosition(true);

                String text = stripper.getText(document);
                pages.add(normalize(text));
            }
        }

        return pages;
    }

    /** Collapses PDF whitespace noise into single spaces per line block. */
    private static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        String collapsed = raw.replace('\r', ' ')
                .replaceAll("[ \\t\\f\\u00A0]+", " ")
                .replaceAll("\\n{2,}", "\n")
                .trim();

        if (collapsed.length() > MAX_CHARS_PER_PAGE) {
            collapsed = collapsed.substring(0, MAX_CHARS_PER_PAGE);
        }
        return collapsed;
    }
}
