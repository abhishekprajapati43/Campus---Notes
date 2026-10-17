package com.example.techcampushubbackend.service;

import com.example.techcampushubbackend.dto.ContentDtos.TimetableRequest;
import com.example.techcampushubbackend.dto.ContentDtos.TimetableResponse;
import com.example.techcampushubbackend.entity.Role;
import com.example.techcampushubbackend.entity.Timetable;
import com.example.techcampushubbackend.entity.User;
import com.example.techcampushubbackend.exception.ResourceNotFoundException;
import com.example.techcampushubbackend.repository.TimetableRepository;
import com.example.techcampushubbackend.utils.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.example.techcampushubbackend.utils.ContentUtils.requireFileUrl;
import static com.example.techcampushubbackend.utils.ContentUtils.resolveTitle;

/** Timetables: same visibility rules as notes. */
@Service
@RequiredArgsConstructor
public class TimetableService {

    private final TimetableRepository timetableRepository;
    private final CourseService courseService;
    private final PurchaseService purchaseService;
    private final CurrentUserService currentUserService;

    @Transactional(readOnly = true)
    public List<TimetableResponse> listVisible() {
        User user = currentUserService.requireUser();
        return timetableRepository.findAllByOrderByCreatedAtDesc().stream()
                .filter(t -> canAccess(t, user))
                .map(TimetableService::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TimetableResponse> listAll() {
        return timetableRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(TimetableService::toResponse)
                .toList();
    }

    /**
     * Faculty workspace listing - only the caller's own timetables; admins see
     * everything (including legacy rows with no recorded owner).
     */
    @Transactional(readOnly = true)
    public List<TimetableResponse> listOwn() {
        User user = currentUserService.requireUser();
        return timetableRepository.findAllByOrderByCreatedAtDesc().stream()
                .filter(t -> user.getRole() == Role.ADMIN
                        || (t.getCreatedBy() != null
                            && t.getCreatedBy().equals(user.getId())))
                .map(TimetableService::toResponse)
                .toList();
    }

    /** Every timetable linked to a course bundle (ownership checked by the caller). */
    @Transactional(readOnly = true)
    public List<TimetableResponse> listByCourse(Long courseId) {
        if (courseId == null) {
            return List.of();
        }
        return timetableRepository.findAllByOrderByCreatedAtDesc().stream()
                .filter(t -> t.getCourseBundle() != null
                        && courseId.equals(t.getCourseBundle().getId()))
                .map(TimetableService::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public TimetableResponse getVisible(Long id) {
        User user = currentUserService.requireUser();
        Timetable timetable = require(id);
        if (!canAccess(timetable, user)) {
            throw new ResourceNotFoundException("Timetable", id);
        }
        return toResponse(timetable);
    }

    @Transactional
    public TimetableResponse create(TimetableRequest request) {
        return doCreate(request, null);
    }

    /** Faculty/admin create that stamps the uploader's user id as the owner. */
    @Transactional
    public TimetableResponse createAsOwner(TimetableRequest request, Long ownerId) {
        return doCreate(request, ownerId);
    }

    private TimetableResponse doCreate(TimetableRequest request, Long ownerId) {
        Timetable timetable = Timetable.builder()
                .title(resolveTitle(request.title(), examLabel(request), request.course()))
                .course(request.course())
                .semester(request.semester() == null ? 0 : request.semester())
                .examType(request.examType())
                .examYear(request.examYear())
                .dayOfWeek(request.dayOfWeek())
                .effectiveFrom(request.effectiveFrom())
                .fileUrl(requireFileUrl(request.fileUrl()))
                .storagePath(request.storagePath())
                .fileName(request.fileName())
                .courseBundle(loadBundle(request.courseId()))
                .createdBy(ownerId)
                .build();
        return toResponse(timetableRepository.save(timetable));
    }

    /**
     * Faculty workspace update: a faculty member may only edit a timetable they
     * own ({@code createdBy} == caller id); admins may edit any. Attempting to
     * edit someone else's schedule raises Spring's AccessDeniedException → 403.
     */
    @Transactional
    public TimetableResponse updateOwned(Long id, TimetableRequest request) {
        User user = currentUserService.requireUser();
        Timetable timetable = require(id);
        if (user.getRole() != Role.ADMIN
                && (timetable.getCreatedBy() == null
                    || !timetable.getCreatedBy().equals(user.getId()))) {
            throw new AccessDeniedException("You can only edit timetables you uploaded");
        }
        return update(id, request);
    }

    @Transactional
    public TimetableResponse update(Long id, TimetableRequest request) {
        Timetable timetable = require(id);
        timetable.setTitle(request.title() == null || request.title().isBlank()
                ? timetable.getTitle()
                : request.title().trim());
        if (request.course() != null) timetable.setCourse(request.course());
        if (request.semester() != null) timetable.setSemester(request.semester());
        if (request.examType() != null) timetable.setExamType(request.examType());
        if (request.examYear() != null) timetable.setExamYear(request.examYear());
        if (request.dayOfWeek() != null) timetable.setDayOfWeek(request.dayOfWeek());
        if (request.effectiveFrom() != null) timetable.setEffectiveFrom(request.effectiveFrom());
        if (request.fileUrl() != null) timetable.setFileUrl(request.fileUrl());
        if (request.storagePath() != null) timetable.setStoragePath(request.storagePath());
        if (request.fileName() != null) timetable.setFileName(request.fileName());
        if (request.courseId() != null) timetable.setCourseBundle(loadBundle(request.courseId()));
        return toResponse(timetableRepository.save(timetable));
    }

    @Transactional
    public void delete(Long id) {
        timetableRepository.delete(require(id));
    }

    /**
     * Bundle resolution enforces ownership: faculty may only link content to
     * courses they launched; admins may link to any course (403 otherwise).
     */
    private com.example.techcampushubbackend.entity.Course loadBundle(Long courseId) {
        return courseService.requireOwnedBundle(courseId);
    }

    private Timetable require(Long id) {
        return timetableRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Timetable", id));
    }

    private boolean canAccess(Timetable timetable, User user) {
        // ADMIN and FACULTY staff read all content regardless of purchases.
        if (timetable.getCourseBundle() == null
                || user.getRole() == Role.ADMIN || user.getRole() == Role.FACULTY) {
            return true;
        }
        return purchaseService.hasActiveAccess(user.getId(), timetable.getCourseBundle().getId());
    }

    public static TimetableResponse toResponse(Timetable t) {
        return new TimetableResponse(
                t.getId(), t.getTitle(), t.getCourse(), t.getSemester(),
                t.getExamType(), t.getExamYear(), t.getDayOfWeek(), t.getEffectiveFrom(),
                t.getFileUrl(), t.getStoragePath(), t.getFileName(),
                t.getCourseBundle() == null ? null : t.getCourseBundle().getId(),
                t.getCourseBundle() == null ? null : t.getCourseBundle().getTitle(),
                t.getCreatedAt());
    }

    /**
     * The timetable upload form has no title input, so one is composed from the
     * session the admin picked, e.g. "B.Tech Mid Sem".
     */
    private static String examLabel(TimetableRequest request) {
        StringBuilder label = new StringBuilder();
        if (request.course() != null && !request.course().isBlank()) {
            label.append(request.course().trim());
        }
        if (request.examType() != null && !request.examType().isBlank()) {
            if (label.length() > 0) {
                label.append(' ');
            }
            label.append(request.examType().trim());
        }
        if (request.examYear() != null) {
            if (label.length() > 0) {
                label.append(' ');
            }
            label.append(request.examYear());
        }
        return label.length() == 0 ? null : label.toString();
    }
}
