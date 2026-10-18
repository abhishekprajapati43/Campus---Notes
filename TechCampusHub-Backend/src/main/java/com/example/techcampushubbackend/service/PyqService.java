package com.example.techcampushubbackend.service;

import com.example.techcampushubbackend.dto.ContentDtos.PyqRequest;
import com.example.techcampushubbackend.dto.ContentDtos.PyqResponse;
import com.example.techcampushubbackend.entity.Pyq;
import com.example.techcampushubbackend.entity.Role;
import com.example.techcampushubbackend.entity.User;
import com.example.techcampushubbackend.exception.ApiException;
import com.example.techcampushubbackend.exception.ResourceNotFoundException;
import com.example.techcampushubbackend.repository.PyqRepository;
import com.example.techcampushubbackend.utils.CurrentUserService;
import com.example.techcampushubbackend.utils.ProgramFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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
    private final CourseService courseService;
    private final PurchaseService purchaseService;
    private final CurrentUserService currentUserService;
    private final PdfIndexService pdfIndexService;

    @Transactional(readOnly = true)
    public List<PyqResponse> listVisible() {
        User user = currentUserService.requireUser();
        boolean staff = user.getRole() == Role.ADMIN || user.getRole() == Role.FACULTY;
        return pyqRepository.findAllByOrderByCreatedAtDesc().stream()
                .filter(pyq -> canAccess(pyq, user))
                // Students only ever see papers of their own program
                // (MCA vs B.Tech); general rows stay visible to all.
                .filter(pyq -> staff
                        || ProgramFilter.matches(pyq.getCourse(), user.getCourse()))
                .map(pyq -> toVisibleResponse(pyq, user))
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
        if (!canAccess(pyq, user) || !canSeeProgram(pyq, user)) {
            throw new ResourceNotFoundException("PYQ", id);
        }
        return toVisibleResponse(pyq, user);
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
        // The program decides which students can open the paper (MCA vs
        // B.Tech), so an upload without one would be invisible-by-accident
        // or leak everywhere - the admin/faculty form makes it mandatory.
        if (request.course() == null || request.course().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Program (course) is required for a PYQ upload");
        }
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
        Pyq saved = pyqRepository.save(pyq);
        reindexForSearch(saved);
        return toResponse(saved);
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
        Pyq saved = pyqRepository.save(pyq);
        reindexForSearch(saved);
        return toResponse(saved);
    }

    @Transactional
    public void delete(Long id) {
        pyqRepository.delete(require(id));
        pdfIndexService.removeIndexAsync(PdfIndexService.CAT_PYQ, id);
    }

    /** Keeps the smart-search index in sync after every write. */
    private void reindexForSearch(Pyq pyq) {
        pdfIndexService.reindexAsync(PdfIndexService.CAT_PYQ, pyq.getId(),
                pyq.getFileUrl(), pyq.getTitle(), pyq.getSubject(),
                pyq.getCourseBundle() == null ? null : pyq.getCourseBundle().getId(),
                pyq.getCourseBundle() == null ? null : pyq.getCourseBundle().getTitle(),
                pyq.getCourse());
    }

    /**
     * Bundle resolution enforces ownership: faculty may only link content to
     * courses they launched; admins may link to any course (403 otherwise).
     */
    private com.example.techcampushubbackend.entity.Course loadBundle(Long courseId) {
        return courseService.requireOwnedBundle(courseId);
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

    /**
     * Program scoping for students: an MCA paper must never be readable
     * (or even discoverable by id) by a B.Tech student. Staff manage all
     * programs and bypass this check.
     */
    private boolean canSeeProgram(Pyq pyq, User user) {
        if (user.getRole() == Role.ADMIN || user.getRole() == Role.FACULTY) {
            return true;
        }
        return ProgramFilter.matches(pyq.getCourse(), user.getCourse());
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

    /**
     * Per-role payload: students get the storage URL withheld (they read the
     * bytes through /api/content/**), staff keep the real URL because their
     * management UIs (edit/replace/delete) depend on it.
     */
    private static PyqResponse toVisibleResponse(Pyq pyq, User user) {
        boolean staff = user.getRole() == Role.ADMIN || user.getRole() == Role.FACULTY;
        return staff ? toResponse(pyq) : toStudentResponse(pyq);
    }

    /** Student payload: storage URLs withheld - bytes stream via /api/content/**. */
    public static PyqResponse toStudentResponse(Pyq pyq) {
        PyqResponse base = toResponse(pyq);
        return new PyqResponse(
                base.id(), base.title(), base.subject(), base.course(),
                base.branch(), base.semester(), base.examYear(), base.description(),
                null,
                null,
                base.courseId(), base.courseTitle(),
                base.createdAt());
    }

    /**
     * Access-checked server-side resolution of the storage URL for the secure
     * streaming routes (in-app viewer relay + /download). Mirrors the
     * visibility rules of {@link #getVisible(Long)}; the URL is only ever
     * relayed as bytes, never serialized into a response body.
     */
    @Transactional(readOnly = true)
    public String requireVisibleFileUrl(Long id) {
        User user = currentUserService.requireUser();
        Pyq pyq = require(id);
        if (!canAccess(pyq, user) || !canSeeProgram(pyq, user)) {
            throw new ResourceNotFoundException("PYQ", id);
        }
        if (pyq.getFileUrl() == null || pyq.getFileUrl().isBlank()) {
            throw new ResourceNotFoundException("PYQ file", id);
        }
        return pyq.getFileUrl();
    }
}
