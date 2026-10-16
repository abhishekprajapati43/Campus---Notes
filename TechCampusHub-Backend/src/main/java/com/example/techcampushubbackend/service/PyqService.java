package com.example.techcampushubbackend.service;

import com.example.techcampushubbackend.dto.ContentDtos.PyqRequest;
import com.example.techcampushubbackend.dto.ContentDtos.PyqResponse;
import com.example.techcampushubbackend.entity.Pyq;
import com.example.techcampushubbackend.entity.Role;
import com.example.techcampushubbackend.entity.User;
import com.example.techcampushubbackend.exception.ResourceNotFoundException;
import com.example.techcampushubbackend.repository.CourseRepository;
import com.example.techcampushubbackend.repository.PyqRepository;
import com.example.techcampushubbackend.utils.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.example.techcampushubbackend.utils.ContentUtils.requireFileUrl;
import static com.example.techcampushubbackend.utils.ContentUtils.resolveTitle;

/** Previous year papers: same visibility rules as notes. */
@Service
@RequiredArgsConstructor
public class PyqService {

    private final PyqRepository pyqRepository;
    private final CourseRepository courseRepository;
    private final PurchaseService purchaseService;
    private final CurrentUserService currentUserService;

    @Transactional(readOnly = true)
    public List<PyqResponse> listVisible() {
        User user = currentUserService.requireUser();
        return pyqRepository.findAllByOrderByCreatedAtDesc().stream()
                .filter(pyq -> canAccess(pyq, user))
                .map(PyqService::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PyqResponse> listAll() {
        return pyqRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(PyqService::toResponse)
                .toList();
    }

    /**
     * Faculty workspace listing - only the caller's own PYQs; admins see
     * everything (including legacy rows with no recorded owner).
     */
    @Transactional(readOnly = true)
    public List<PyqResponse> listOwn() {
        User user = currentUserService.requireUser();
        return pyqRepository.findAllByOrderByCreatedAtDesc().stream()
                .filter(pyq -> user.getRole() == Role.ADMIN
                        || (pyq.getCreatedBy() != null
                            && pyq.getCreatedBy().equals(user.getId())))
                .map(PyqService::toResponse)
                .toList();
    }

    /** Every PYQ linked to a course bundle (ownership checked by the caller). */
    @Transactional(readOnly = true)
    public List<PyqResponse> listByCourse(Long courseId) {
        if (courseId == null) {
            return List.of();
        }
        return pyqRepository.findAllByOrderByCreatedAtDesc().stream()
                .filter(pyq -> pyq.getCourseBundle() != null
                        && courseId.equals(pyq.getCourseBundle().getId()))
                .map(PyqService::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public PyqResponse getVisible(Long id) {
        User user = currentUserService.requireUser();
        Pyq pyq = require(id);
        if (!canAccess(pyq, user)) {
            throw new ResourceNotFoundException("PYQ", id);
        }
        return toResponse(pyq);
    }

    @Transactional
    public PyqResponse create(PyqRequest request) {
        return doCreate(request, null);
    }

    /** Faculty/admin create that stamps the uploader's user id as the owner. */
    @Transactional
    public PyqResponse createAsOwner(PyqRequest request, Long ownerId) {
        return doCreate(request, ownerId);
    }

    private PyqResponse doCreate(PyqRequest request, Long ownerId) {
        Pyq pyq = Pyq.builder()
                .title(resolveTitle(request.title(), request.subject(), request.course()))
                .subject(request.subject())
                .course(request.course())
                .branch(request.branch())
                .semester(request.semester() == null ? 0 : request.semester())
                .examYear(request.examYear())
                .description(request.description())
                .fileUrl(requireFileUrl(request.fileUrl()))
                .storagePath(request.storagePath())
                .courseBundle(loadBundle(request.courseId()))
                .createdBy(ownerId)
                .build();
        return toResponse(pyqRepository.save(pyq));
    }

    /**
     * Faculty workspace update: a faculty member may only edit a PYQ they own
     * ({@code createdBy} == caller id); admins may edit any. Attempting to
     * edit someone else's paper raises Spring's AccessDeniedException → 403.
     */
    @Transactional
    public PyqResponse updateOwned(Long id, PyqRequest request) {
        User user = currentUserService.requireUser();
        Pyq pyq = require(id);
        if (user.getRole() != Role.ADMIN
                && (pyq.getCreatedBy() == null || !pyq.getCreatedBy().equals(user.getId()))) {
            throw new AccessDeniedException("You can only edit PYQs you uploaded");
        }
        return update(id, request);
    }

    @Transactional
    public PyqResponse update(Long id, PyqRequest request) {
        Pyq pyq = require(id);
        pyq.setTitle(request.title() == null || request.title().isBlank()
                ? pyq.getTitle()
                : request.title().trim());
        if (request.subject() != null) pyq.setSubject(request.subject());
        if (request.course() != null) pyq.setCourse(request.course());
        if (request.branch() != null) pyq.setBranch(request.branch());
        if (request.semester() != null) pyq.setSemester(request.semester());
        if (request.examYear() != null) pyq.setExamYear(request.examYear());
        if (request.description() != null) pyq.setDescription(request.description());
        if (request.fileUrl() != null) pyq.setFileUrl(request.fileUrl());
        if (request.storagePath() != null) pyq.setStoragePath(request.storagePath());
        if (request.courseId() != null) pyq.setCourseBundle(loadBundle(request.courseId()));
        return toResponse(pyqRepository.save(pyq));
    }

    @Transactional
    public void delete(Long id) {
        pyqRepository.delete(require(id));
    }

    private com.example.techcampushubbackend.entity.Course loadBundle(Long courseId) {
        return courseId == null ? null : courseRepository.findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Course", courseId));
    }

    private Pyq require(Long id) {
        return pyqRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PYQ", id));
    }

    private boolean canAccess(Pyq pyq, User user) {
        // ADMIN and FACULTY staff read all content regardless of purchases.
        if (pyq.getCourseBundle() == null
                || user.getRole() == Role.ADMIN || user.getRole() == Role.FACULTY) {
            return true;
        }
        return purchaseService.hasActiveAccess(user.getId(), pyq.getCourseBundle().getId());
    }

    public static PyqResponse toResponse(Pyq pyq) {
        return new PyqResponse(
                pyq.getId(), pyq.getTitle(), pyq.getSubject(), pyq.getCourse(),
                pyq.getBranch(), pyq.getSemester(), pyq.getExamYear(), pyq.getDescription(),
                pyq.getFileUrl(), pyq.getStoragePath(),
                pyq.getCourseBundle() == null ? null : pyq.getCourseBundle().getId(),
                pyq.getCourseBundle() == null ? null : pyq.getCourseBundle().getTitle(),
                pyq.getCreatedAt());
    }
}
