package com.example.techcampushubbackend.utils;

import java.util.Locale;

/**
 * Program (department) matching for student content visibility.
 *
 * <p>PYQ and timetable rows carry a program label (e.g. "MCA", "B.Tech")
 * on their {@code course} column, and students carry their own program on
 * their profile. A student must only ever see rows uploaded for their own
 * program - an MCA paper must never surface on a B.Tech student's PYQ
 * page, in search hits, or through a direct download link.
 *
 * <p>Labels are compared after normalisation (lowercased, non-alphanumerics
 * stripped) so "B.Tech", "BTech" and "b tech" all read as the same program.
 * A row with no program at all is treated as general content and stays
 * visible to every student; a student with no program on their profile
 * therefore only ever sees general rows, never another program's content.
 */
public final class ProgramFilter {

    private ProgramFilter() {
    }

    /**
     * @return the comparison key for a program label ("B.Tech" -> "btech"),
     *         or {@code null} when the label is blank.
     */
    public static String normalize(String program) {
        if (program == null || program.isBlank()) {
            return null;
        }
        String key = program.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        return key.isEmpty() ? null : key;
    }

    /**
     * @return true when a document carrying {@code recordProgram} may be
     *         shown to a student of {@code studentProgram}: general rows
     *         (no program) always match, otherwise the normalised labels
     *         must be equal.
     */
    public static boolean matches(String recordProgram, String studentProgram) {
        String recordKey = normalize(recordProgram);
        if (recordKey == null) {
            return true; // general content - not tied to any program
        }
        return recordKey.equals(normalize(studentProgram));
    }
}
