package com.example.techcampushubbackend.service;

import com.example.techcampushubbackend.dto.ContentDtos.TnpNoteRequest;
import com.example.techcampushubbackend.dto.ContentDtos.TnpNoteResponse;
import com.example.techcampushubbackend.entity.Role;
import com.example.techcampushubbackend.entity.TnpNote;
import com.example.techcampushubbackend.entity.User;
import com.example.techcampushubbackend.exception.ResourceNotFoundException;
import com.example.techcampushubbackend.repository.CourseRepository;
import com.example.techcampushubbackend.repository.TnpNoteRepository;
import com.example.techcampushubbackend.utils.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.example.techcampushubbackend.utils.ContentUtils.requireFileUrl;
import static com.example.techcampushubbackend.utils.ContentUtils.resolveTitle;

/** Training &amp; placement notes: same visibility rules as notes. */
@Service
@RequiredArgsConstructor
public class TnpNoteService {

    private final TnpNoteRepository tnpNoteRepository;
    private final CourseRepository courseRepository;
    private final PurchaseService purchaseService;
    private final CurrentUserService currentUserService;

    @Transactional(readOnly = true)
    public List<TnpNoteResponse> listVisible() {
        User user = currentUserService.requireUser();
        return tnpNoteRepository.findAllByOrderByCreatedAtDesc().stream()
                .filter(note -> canAccess(note, user))
                .map(TnpNoteService::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TnpNoteResponse> listAll() {
        return tnpNoteRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(TnpNoteService::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public TnpNoteResponse getVisible(Long id) {
        User user = currentUserService.requireUser();
        TnpNote note = require(id);
        if (!canAccess(note, user)) {
            throw new ResourceNotFoundException("TNP note", id);
        }
        return toResponse(note);
    }

    @Transactional
    public TnpNoteResponse create(TnpNoteRequest request) {
        TnpNote note = TnpNote.builder()
                .title(resolveTitle(request.title(), request.topic()))
                .topic(request.topic())
                .description(request.description())
                .fileUrl(requireFileUrl(request.fileUrl()))
                .storagePath(request.storagePath())
                .courseBundle(loadBundle(request.courseId()))
                .build();
        return toResponse(tnpNoteRepository.save(note));
    }

    @Transactional
    public TnpNoteResponse update(Long id, TnpNoteRequest request) {
        TnpNote note = require(id);
        note.setTitle(request.title() == null || request.title().isBlank()
                ? note.getTitle()
                : request.title().trim());
        if (request.topic() != null) note.setTopic(request.topic());
        if (request.description() != null) note.setDescription(request.description());
        if (request.fileUrl() != null) note.setFileUrl(request.fileUrl());
        if (request.storagePath() != null) note.setStoragePath(request.storagePath());
        if (request.courseId() != null) note.setCourseBundle(loadBundle(request.courseId()));
        return toResponse(tnpNoteRepository.save(note));
    }

    @Transactional
    public void delete(Long id) {
        tnpNoteRepository.delete(require(id));
    }

    private com.example.techcampushubbackend.entity.Course loadBundle(Long courseId) {
        return courseId == null ? null : courseRepository.findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Course", courseId));
    }

    private TnpNote require(Long id) {
        return tnpNoteRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("TNP note", id));
    }

    private boolean canAccess(TnpNote note, User user) {
        if (note.getCourseBundle() == null || user.getRole() == Role.ADMIN) {
            return true;
        }
        return purchaseService.hasActiveAccess(user.getId(), note.getCourseBundle().getId());
    }

    public static TnpNoteResponse toResponse(TnpNote note) {
        return new TnpNoteResponse(
                note.getId(), note.getTitle(), note.getTopic(), note.getDescription(),
                note.getFileUrl(), note.getStoragePath(),
                note.getCourseBundle() == null ? null : note.getCourseBundle().getId(),
                note.getCourseBundle() == null ? null : note.getCourseBundle().getTitle(),
                note.getCreatedAt());
    }
}
