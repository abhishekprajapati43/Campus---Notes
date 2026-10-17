package com.example.techcampushubbackend.service;

import com.example.techcampushubbackend.dto.FacultyDtos.FacultyStatsResponse;
import com.example.techcampushubbackend.dto.FacultyDtos.FacultyStudentResponse;
import com.example.techcampushubbackend.dto.PurchaseDtos.PurchaseResponse;
import com.example.techcampushubbackend.entity.Purchase;
import com.example.techcampushubbackend.entity.PurchaseStatus;
import com.example.techcampushubbackend.entity.User;
import com.example.techcampushubbackend.repository.CourseRepository;
import com.example.techcampushubbackend.repository.FacultyResourceRepository;
import com.example.techcampushubbackend.repository.NoteRepository;
import com.example.techcampushubbackend.repository.PurchaseRepository;
import com.example.techcampushubbackend.repository.PyqRepository;
import com.example.techcampushubbackend.repository.TimetableRepository;
import com.example.techcampushubbackend.repository.TnpNoteRepository;
import com.example.techcampushubbackend.utils.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Faculty dashboard queries: summary statistics and the scoped Users table.
 * Every number and row is limited to the courses the logged-in faculty member
 * launched - other people's courses and students are never visible here.
 */
@Service
@RequiredArgsConstructor
public class FacultyDashboardService {

    /** Purchases newer than this count as "recent" on the dashboard card. */
    private static final int RECENT_WINDOW_DAYS = 30;
    private static final int RECENT_LIST_LIMIT = 6;

    private final CourseRepository courseRepository;
    private final PurchaseRepository purchaseRepository;
    private final NoteRepository noteRepository;
    private final PyqRepository pyqRepository;
    private final TnpNoteRepository tnpNoteRepository;
    private final TimetableRepository timetableRepository;
    private final FacultyResourceRepository facultyResourceRepository;
    private final CurrentUserService currentUserService;

    @Transactional(readOnly = true)
    public FacultyStatsResponse stats() {
        User user = currentUserService.requireUser();
        Long facultyId = user.getId();

        long totalCourses = courseRepository.countByCreatedBy(facultyId);
        long totalStudents = purchaseRepository.countDistinctStudentsByFaculty(facultyId);
        long totalContent = noteRepository.countByCreatedBy(facultyId)
                + pyqRepository.countByCreatedBy(facultyId)
                + tnpNoteRepository.countByCreatedBy(facultyId)
                + timetableRepository.countByCreatedBy(facultyId)
                + facultyResourceRepository.countByCreatedBy(facultyId);

        List<Purchase> purchases = purchaseRepository.findFacultyPurchases(facultyId);
        LocalDateTime cutoff = LocalDateTime.now().minusDays(RECENT_WINDOW_DAYS);
        long recentPurchases = purchases.stream()
                .filter(p -> p.getCreatedAt() != null && p.getCreatedAt().isAfter(cutoff))
                .count();

        List<PurchaseResponse> recentList = purchases.stream()
                .limit(RECENT_LIST_LIMIT)
                .map(PurchaseService::toResponse)
                .toList();

        return new FacultyStatsResponse(totalCourses, totalStudents, totalContent,
                recentPurchases, recentList);
    }

    /**
     * The faculty Users table: one row per purchase of the caller's courses
     * (read-only - no mutation route exists for these rows).
     */
    @Transactional(readOnly = true)
    public List<FacultyStudentResponse> listStudents() {
        User user = currentUserService.requireUser();
        return purchaseRepository.findFacultyPurchases(user.getId()).stream()
                .map(FacultyDashboardService::toStudentResponse)
                .toList();
    }

    private static FacultyStudentResponse toStudentResponse(Purchase p) {
        return new FacultyStudentResponse(
                p.getId(),
                p.getUser().getId(),
                p.getUser().getName(),
                p.getUser().getEmail(),
                p.getUser().getPhone(),
                p.getCourse().getId(),
                p.getCourse().getTitle(),
                p.getAmountInr(),
                p.getCreatedAt(),
                p.getStatus().name(),
                accessStatus(p),
                p.getProgress(),
                p.getValidUntil());
    }

    /** Display status derived from the purchase lifecycle + validity window. */
    private static String accessStatus(Purchase p) {
        if (p.getStatus() == PurchaseStatus.PENDING) {
            return "Pending";
        }
        if (p.getStatus() == PurchaseStatus.REJECTED) {
            return "Rejected";
        }
        // APPROVED
        if (p.getValidUntil() != null && p.getValidUntil().isBefore(LocalDateTime.now())) {
            return "Expired";
        }
        return "Active";
    }
}
