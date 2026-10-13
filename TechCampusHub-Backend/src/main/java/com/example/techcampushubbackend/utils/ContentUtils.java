package com.example.techcampushubbackend.utils;

import com.example.techcampushubbackend.exception.BadRequestException;

/**
 * Small shared helpers for the content collections (notes, PYQs, TNP notes,
 * timetables).
 */
public final class ContentUtils {

    private static final String UNTITLED = "Untitled";

    private ContentUtils() {
    }

    /**
     * The admin upload form leaves the title optional, so the first non-blank
     * value wins: the title itself, then any of the supplied fallbacks
     * (subject, topic, course...).
     */
    public static String resolveTitle(String title, String... fallbacks) {
        if (title != null && !title.isBlank()) {
            return title.trim();
        }
        for (String fallback : fallbacks) {
            if (fallback != null && !fallback.isBlank()) {
                return fallback.trim();
            }
        }
        return UNTITLED;
    }

    /**
     * A record without a file URL would be unusable, so creating one requires a
     * link. The DTO keeps {@code fileUrl} optional because partial PUTs only
     * send the fields being changed - this check runs on create only.
     */
    public static String requireFileUrl(String fileUrl) {
        if (fileUrl == null || fileUrl.isBlank()) {
            throw new BadRequestException("fileUrl is required");
        }
        return fileUrl;
    }
}
