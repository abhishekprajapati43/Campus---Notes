package com.example.techcampushubbackend.service;

import com.example.techcampushubbackend.dto.FacultyDtos.ResourceRequest;
import com.example.techcampushubbackend.dto.FacultyDtos.ResourceResponse;
import com.example.techcampushubbackend.entity.FacultyResource;
import com.example.techcampushubbackend.entity.FacultyResource.Kind;
import com.example.techcampushubbackend.entity.Role;
import com.example.techcampushubbackend.entity.User;
import com.example.techcampushubbackend.exception.BadRequestException;
import com.example.techcampushubbackend.exception.ResourceNotFoundException;
import com.example.techcampushubbackend.repository.FacultyResourceRepository;
import com.example.techcampushubbackend.utils.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

/**
 * Extra faculty uploads (videos, assignments, other files). Same contract as
 * the other content collections: faculty create/read/update their own records
 * only, admins see and edit everything, and <b>deletion stays admin-only</b> -
 * there is deliberately no DELETE route under {@code /api/faculty}.
 */
@Service
@RequiredArgsConstructor
public class FacultyResourceService {

    private final FacultyResourceRepository facultyResourceRepository;
    private final CourseService courseService;
    private final CurrentUserService currentUserService;

    /** Faculty workspace listing - own resources only (admins see all). */
    @Transactional(readOnly = true)
    public List<ResourceResponse> listOwn() {
        User user = currentUserService.requireUser();
        List<FacultyResource> rows = user.getRole() == Role.ADMIN
                ? facultyResourceRepository.findAll().stream()
                        .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                        .toList()
                : facultyResourceRepository.findAllByCreatedByOrderByCreatedAtDesc(user.getId());
        return rows.stream().map(FacultyResourceService::toResponse).toList();
    }

    /** Faculty/admin create stamped with the uploader; bundle must be owned. */
    @Transactional
    public ResourceResponse createAsOwner(ResourceRequest request, Long ownerId) {
        FacultyResource resource = FacultyResource.builder()
                .title(request.title().trim())
                .description(trimToNull(request.description()))
                .kind(parseKind(request.kind()))
                .fileUrl(requireFileUrl(request.fileUrl()))
                .storagePath(trimToNull(request.storagePath()))
                .fileName(trimToNull(request.fileName()))
                .courseBundle(courseService.requireOwnedBundle(request.courseId()))
                .createdBy(ownerId)
                .build();
        return toResponse(facultyResourceRepository.save(resource));
    }

    /**
     * Faculty workspace update: only the uploader may edit (admins may edit
     * any); the optional course bundle must also be one of the caller's courses.
     */
    @Transactional
    public ResourceResponse updateOwned(Long id, ResourceRequest request) {
        User user = currentUserService.requireUser();
        FacultyResource resource = require(id);
        if (user.getRole() != Role.ADMIN
                && (resource.getCreatedBy() == null
                    || !resource.getCreatedBy().equals(user.getId()))) {
            throw new AccessDeniedException("You can only edit resources you uploaded");
        }
        if (request.title() != null && !request.title().isBlank()) {
            resource.setTitle(request.title().trim());
        }
        if (request.description() != null) {
            resource.setDescription(trimToNull(request.description()));
        }
        if (request.kind() != null && !request.kind().isBlank()) {
            resource.setKind(parseKind(request.kind()));
        }
        if (request.fileUrl() != null && !request.fileUrl().isBlank()) {
            resource.setFileUrl(request.fileUrl().trim());
        }
        if (request.storagePath() != null) {
            resource.setStoragePath(trimToNull(request.storagePath()));
        }
        if (request.fileName() != null) {
            resource.setFileName(trimToNull(request.fileName()));
        }
        if (request.courseId() != null) {
            resource.setCourseBundle(courseService.requireOwnedBundle(request.courseId()));
        }
        return toResponse(facultyResourceRepository.save(resource));
    }

    private FacultyResource require(Long id) {
        return facultyResourceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Resource", id));
    }

    private static Kind parseKind(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BadRequestException("kind is required (VIDEO, ASSIGNMENT or OTHER)");
        }
        try {
            return Kind.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Invalid kind: " + raw
                    + " (expected VIDEO, ASSIGNMENT or OTHER)");
        }
    }

    private static String requireFileUrl(String fileUrl) {
        if (fileUrl == null || fileUrl.isBlank()) {
            throw new BadRequestException("fileUrl is required");
        }
        return fileUrl.trim();
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public static ResourceResponse toResponse(FacultyResource r) {
        return new ResourceResponse(
                r.getId(), r.getTitle(), r.getDescription(),
                r.getKind() == null ? null : r.getKind().name(),
                r.getFileUrl(), r.getStoragePath(), r.getFileName(),
                r.getCourseBundle() == null ? null : r.getCourseBundle().getId(),
                r.getCourseBundle() == null ? null : r.getCourseBundle().getTitle(),
                r.getCreatedAt());
    }
}
