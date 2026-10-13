package com.example.techcampushubbackend.service;

import com.example.techcampushubbackend.dto.ContentDtos.NoteRequest;
import com.example.techcampushubbackend.dto.ContentDtos.NoteResponse;
import com.example.techcampushubbackend.entity.Note;
import com.example.techcampushubbackend.entity.User;
import com.example.techcampushubbackend.exception.ResourceNotFoundException;
import com.example.techcampushubbackend.repository.CourseRepository;
import com.example.techcampushubbackend.repository.NoteRepository;
import com.example.techcampushubbackend.utils.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.example.techcampushubbackend.utils.ContentUtils.requireFileUrl;
import static com.example.techcampushubbackend.utils.ContentUtils.resolveTitle;

/**
 * Notes: students see free notes plus bundle-gated notes they have purchased;
 * admins manage the full collection (create/edit/delete).
 */
@Service
@RequiredArgsConstructor
public class NoteService {

    private final NoteRepository noteRepository;
    private final CourseRepository courseRepository;
    private final PurchaseService purchaseService;
    private final CurrentUserService currentUserService;

    /** Student listing - filters out paid bundles the caller cannot access. */
    @Transactional(readOnly = true)
    public List<NoteResponse> listVisible() {
        User user = currentUserService.requireUser();
        return noteRepository.findAllByOrderByCreatedAtDesc().stream()
                .filter(note -> canAccess(note, user))
                .map(NoteService::toResponse)
                .toList();
    }

    /** Admin listing - everything. */
    @Transactional(readOnly = true)
    public List<NoteResponse> listAll() {
        return noteRepository.findAllByOrderByCreatedAtDesc().stream()
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

    @Transactional
    public NoteResponse create(NoteRequest request) {
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

    private com.example.techcampushubbackend.entity.Course loadBundle(Long courseId) {
        return courseId == null ? null : courseRepository.findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Course", courseId));
    }

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
        if (user.getRole() == com.example.techcampushubbackend.entity.Role.ADMIN) {
            return true;
        }
        return purchaseService.hasActiveAccess(user.getId(), note.getCourseBundle().getId());
    }

    public static NoteResponse toResponse(Note note) {
        return new NoteResponse(
                note.getId(), note.getTitle(), note.getSubject(), note.getCourse(),
                note.getBranch(), note.getYear(), note.getSemester(), note.getDescription(),
                note.getFileUrl(), note.getStoragePath(),
                note.getCourseBundle() == null ? null : note.getCourseBundle().getId(),
                note.getCourseBundle() == null ? null : note.getCourseBundle().getTitle(),
                note.getCreatedAt());
    }
}
