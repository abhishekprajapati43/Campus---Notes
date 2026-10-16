package com.example.techcampushubbackend.service;

import com.example.techcampushubbackend.dto.ContentDtos.TimetableRequest;
import com.example.techcampushubbackend.dto.ContentDtos.TimetableResponse;
import com.example.techcampushubbackend.entity.Role;
import com.example.techcampushubbackend.entity.Timetable;
import com.example.techcampushubbackend.entity.User;
import com.example.techcampushubbackend.exception.ResourceNotFoundException;
import com.example.techcampushubbackend.repository.CourseRepository;
import com.example.techcampushubbackend.repository.TimetableRepository;
import com.example.techcampushubbackend.utils.CurrentUserService;
import lombok.RequiredArgsConstructor;
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
    private final CourseRepository courseRepository;
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
                .build();
        return toResponse(timetableRepository.save(timetable));
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

    private com.example.techcampushubbackend.entity.Course loadBundle(Long courseId) {
        return courseId == null ? null : courseRepository.findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Course", courseId));
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
