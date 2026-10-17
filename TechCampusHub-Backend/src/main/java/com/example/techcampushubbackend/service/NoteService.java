package com.example.techcampushubbackend.service;

import com.example.techcampushubbackend.dto.ContentDtos.NoteRequest;
import com.example.techcampushubbackend.dto.ContentDtos.NoteResponse;
import com.example.techcampushubbackend.entity.Note;
import com.example.techcampushubbackend.entity.Role;
import com.example.techcampushubbackend.entity.User;
import com.example.techcampushubbackend.exception.ResourceNotFoundException;
import com.example.techcampushubbackend.repository.NoteRepository;
import com.example.techcampushubbackend.repository.UserRepository;
import com.example.techcampushubbackend.utils.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static com.example.techcampushubbackend.utils.ContentUtils.requireFileUrl;
import static com.example.techcampushubbackend.utils.ContentUtils.resolveTitle;

/**
 * Notes: students see free notes plus bundle-gated notes they have purchased;
 * admins manage the full collection (create/edit/delete); faculty create, read
 * and edit only the notes they uploaded - deletion is intentionally admin-only.
 */
@Service
@RequiredArgsConstructor
public class NoteService {

    private final NoteRepository noteRepository;
    private final CourseService courseService;
    private final PurchaseService purchaseService;
    private final CurrentUserService currentUserService;
    private final UserRepository userRepository;

    /** Student listing - filters out paid bundles the caller cannot access. */
    @Transactional(readOnly = true)
    public List<NoteResponse> listVisible() {
        User user = currentUserService.requireUser();
        return noteRepository.findAllByOrderByCreatedAtDesc().stream()
                .filter(note -> canAccess(note, user))
                .map(NoteService::toResponse)
                .toList();
    }

    /**
     * Admin listing - everything, enriched with the uploader's name/role in a
     * single batched query so admins can audit faculty content (no N+1).
     */
    @Transactional(readOnly = true)
    public List<NoteResponse> listAll() {
        return withOwners(noteRepository.findAllByOrderByCreatedAtDesc());
    }

    /**
     * Faculty workspace listing - only the caller's own notes; admins see all
     * notes (including legacy ones with no recorded owner).
     */
    @Transactional(readOnly = true)
    public List<NoteResponse> listOwn() {
        User user = currentUserService.requireUser();
        return noteRepository.findAllByOrderByCreatedAtDesc().stream()
                .filter(note -> user.getRole() == Role.ADMIN
                        || (note.getCreatedBy() != null
                            && note.getCreatedBy().equals(user.getId())))
                .map(NoteService::toResponse)
                .toList();
    }

    /**
     * Every note linked to a course bundle, used by the faculty "Course"
     * detail view. Course ownership is checked by the caller - this only
     * filters by bundle id.
     */
    @Transactional(readOnly = true)
    public List<NoteResponse> listByCourse(Long courseId) {
        if (courseId == null) {
            return List.of();
        }
        return noteRepository.findAllByOrderByCreatedAtDesc().stream()
                .filter(note -> note.getCourseBundle() != null
                        && courseId.equals(note.getCourseBundle().getId()))
                .map(NoteService::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public NoteResponse getVisible(Long id) {
        User user = currentUserService.requireUser();
        Note note = require(id);
        if (!canAccess(note, user)) {
            throw new ResourceNotFoundException("Note", id);
        }
        return toResponse(note);
    }

    /** Admin create - identical to before (no owner stamped). */
    @Transactional
    public NoteResponse create(NoteRequest request) {
        return doCreate(request, null);
    }

    /**
     * Faculty/admin create that stamps the uploader's user id as the owner, so
     * the faculty workspace can list it and admins can trace it during moderation.
     */
    @Transactional
    public NoteResponse createAsOwner(NoteRequest request, Long ownerId) {
        return doCreate(request, ownerId);
    }

    private NoteResponse doCreate(NoteRequest request, Long ownerId) {
        Note note = Note.builder()
                .title(resolveTitle(request.title(), request.subject(), request.course()))
                .subject(request.subject())
                .course(request.course())
                .branch(request.branch())
                .year(request.year())
                .semester(request.semester() == null ? 0 : request.semester())
                .description(request.description())
                .fileUrl(requireFileUrl(request.fileUrl()))
                .storagePath(request.storagePath())
                .courseBundle(loadBundle(request.courseId()))
                .createdBy(ownerId)
                .build();
        return toResponse(noteRepository.save(note));
    }

    @Transactional
    public NoteResponse update(Long id, NoteRequest request) {
        Note note = require(id);
        note.setTitle(request.title() == null || request.title().isBlank()
                ? note.getTitle()
                : request.title().trim());
        if (request.subject() != null) note.setSubject(request.subject());
        if (request.course() != null) note.setCourse(request.course());
        if (request.branch() != null) note.setBranch(request.branch());
        if (request.year() != null) note.setYear(request.year());
        if (request.semester() != null) note.setSemester(request.semester());
        if (request.description() != null) note.setDescription(request.description());
        if (request.fileUrl() != null) note.setFileUrl(request.fileUrl());
        if (request.storagePath() != null) note.setStoragePath(request.storagePath());
        if (request.courseId() != null) {
            note.setCourseBundle(loadBundle(request.courseId()));
        }
        return toResponse(noteRepository.save(note));
    }

    /**
     * Faculty workspace update: a faculty member may only edit a note they own
     * ({@code createdBy} == caller id); admins may edit any note. Attempting to
     * edit someone else's note raises Spring's AccessDeniedException → 403.
     */
    @Transactional
    public NoteResponse updateOwned(Long id, NoteRequest request) {
        User user = currentUserService.requireUser();
        Note note = require(id);
        if (user.getRole() != Role.ADMIN
                && (note.getCreatedBy() == null || !note.getCreatedBy().equals(user.getId()))) {
            throw new AccessDeniedException("You can only edit notes you uploaded");
        }
        return update(id, request);
    }

    /**
     * Bundle resolution enforces ownership: faculty may only link content to
     * courses they launched; admins may link to any course (403 otherwise).
     */
    private com.example.techcampushubbackend.entity.Course loadBundle(Long courseId) {
        return courseService.requireOwnedBundle(courseId);
    }

    /**
     * Admin-only delete: enforced twice over - the {@code @PreAuthorize} on the
     * controller method AND the {@code /api/admin/**} matcher in SecurityConfig.
     * Faculty deletion is intentionally forbidden; there is no delete route
     * under /api/faculty.
     */
    @Transactional
    public void delete(Long id) {
        noteRepository.delete(require(id));
    }

    private Note require(Long id) {
        return noteRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Note", id));
    }

    private boolean canAccess(Note note, User user) {
        if (note.getCourseBundle() == null) {
            return true; // free note
        }
        // ADMIN and FACULTY staff read all content regardless of purchases.
        if (user.getRole() == Role.ADMIN || user.getRole() == Role.FACULTY) {
            return true;
        }
        return purchaseService.hasActiveAccess(user.getId(), note.getCourseBundle().getId());
    }

    /** Base response without uploader info - keeps the student payload unchanged. */
    public static NoteResponse toResponse(Note note) {
        return new NoteResponse(
                note.getId(), note.getTitle(), note.getSubject(), note.getCourse(),
                note.getBranch(), note.getYear(), note.getSemester(), note.getDescription(),
                note.getFileUrl(), note.getStoragePath(),
                note.getCourseBundle() == null ? null : note.getCourseBundle().getId(),
                note.getCourseBundle() == null ? null : note.getCourseBundle().getTitle(),
                null,
                null,
                note.getCreatedAt());
    }

    /** Admin-list enrichment: resolves every uploader in one batched query (no N+1). */
    private List<NoteResponse> withOwners(List<Note> notes) {
        Set<Long> ownerIds = notes.stream()
                .map(Note::getCreatedBy)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        Map<Long, User> owners = ownerIds.isEmpty() ? Map.of()
                : userRepository.findAllById(ownerIds).stream()
                        .collect(java.util.stream.Collectors.toMap(User::getId, owner -> owner));
        return notes.stream()
                .map(note -> {
                    NoteResponse base = toResponse(note);
                    User owner = note.getCreatedBy() == null ? null : owners.get(note.getCreatedBy());
                    if (owner == null) {
                        return base;
                    }
                    return new NoteResponse(
                            base.id(), base.title(), base.subject(), base.course(),
                            base.branch(), base.year(), base.semester(), base.description(),
                            base.fileUrl(), base.storagePath(), base.courseId(), base.courseTitle(),
                            owner.getName(), owner.getRole().name(), base.createdAt());
                })
                .toList();
    }
}
