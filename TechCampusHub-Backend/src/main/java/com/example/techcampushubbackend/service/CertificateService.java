package com.example.techcampushubbackend.service;

import com.example.techcampushubbackend.dto.ContentDtos.CertificateRequest;
import com.example.techcampushubbackend.dto.ContentDtos.CertificateResponse;
import com.example.techcampushubbackend.entity.Certificate;
import com.example.techcampushubbackend.entity.User;
import com.example.techcampushubbackend.exception.ConflictException;
import com.example.techcampushubbackend.exception.ResourceNotFoundException;
import com.example.techcampushubbackend.repository.CertificateRepository;
import com.example.techcampushubbackend.repository.CourseRepository;
import com.example.techcampushubbackend.repository.UserRepository;
import com.example.techcampushubbackend.utils.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

/**
 * Certificates: students see only their own, admins manage everything.
 * The certificate number defaults to {@code TCH-<id>} when not supplied.
 */
@Service
@RequiredArgsConstructor
public class CertificateService {

    private final CertificateRepository certificateRepository;
    private final CourseRepository courseRepository;
    private final UserRepository userRepository;
    private final CurrentUserService currentUserService;

    @Transactional(readOnly = true)
    public List<CertificateResponse> listMine() {
        User user = currentUserService.requireUser();
        return certificateRepository.findByUserIdOrderByCreatedAtDesc(user.getId()).stream()
                .map(CertificateService::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CertificateResponse> listAll() {
        return certificateRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(CertificateService::toResponse)
                .toList();
    }

    /**
     * Certificates visible to the logged-in faculty: those issued for the faculty's own
     * courses or to students enrolled in those courses. Never exposes other faculty's data.
     */
    @Transactional(readOnly = true)
    public List<CertificateResponse> listVisibleToMe() {
        User user = currentUserService.requireUser();
        return certificateRepository.findVisibleToFaculty(user.getId()).stream()
                .map(CertificateService::toResponse)
                .toList();
    }

    @Transactional
    public CertificateResponse create(CertificateRequest request) {
        User owner = null;
        if (request.userId() != null) {
            owner = userRepository.findById(request.userId())
                    .orElseThrow(() -> new ResourceNotFoundException("User", request.userId()));
        }
        var course = request.courseId() == null ? null
                : courseRepository.findById(request.courseId())
                .orElseThrow(() -> new ResourceNotFoundException("Course", request.courseId()));

        Certificate certificate = Certificate.builder()
                .certificateNo(generateNumber(request.certificateNo()))
                .title(request.title().trim())
                .user(owner)
                .course(course)
                .issuedOn(request.issuedOn() == null ? LocalDate.now() : request.issuedOn())
                .fileUrl(request.fileUrl())
                .build();
        return toResponse(certificateRepository.save(certificate));
    }

    @Transactional
    public CertificateResponse update(Long id, CertificateRequest request) {
        Certificate certificate = require(id);
        if (request.certificateNo() != null && !request.certificateNo().isBlank()) {
            String requested = request.certificateNo().trim();
            certificateRepository.findByCertificateNo(requested)
                    .filter(existing -> !existing.getId().equals(id))
                    .ifPresent(existing -> {
                        throw new ConflictException("Certificate number already exists: " + requested);
                    });
            certificate.setCertificateNo(requested);
        }
        certificate.setTitle(request.title().trim());
        if (request.userId() != null) {
            certificate.setUser(userRepository.findById(request.userId())
                    .orElseThrow(() -> new ResourceNotFoundException("User", request.userId())));
        }
        if (request.courseId() != null) {
            certificate.setCourse(courseRepository.findById(request.courseId())
                    .orElseThrow(() -> new ResourceNotFoundException("Course", request.courseId())));
        }
        if (request.issuedOn() != null) certificate.setIssuedOn(request.issuedOn());
        if (request.fileUrl() != null) certificate.setFileUrl(request.fileUrl());
        return toResponse(certificateRepository.save(certificate));
    }

    @Transactional
    public void delete(Long id) {
        certificateRepository.delete(require(id));
    }

    private String generateNumber(String requested) {
        if (requested != null && !requested.isBlank()) {
            String number = requested.trim().toUpperCase(Locale.ROOT);
            certificateRepository.findByCertificateNo(number).ifPresent(existing -> {
                throw new ConflictException("Certificate number already exists: " + number);
            });
            return number;
        }
        return "TCH-" + System.currentTimeMillis();
    }

    private Certificate require(Long id) {
        return certificateRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Certificate", id));
    }

    public static CertificateResponse toResponse(Certificate c) {
        return new CertificateResponse(
                c.getId(), c.getCertificateNo(), c.getTitle(),
                c.getUser() == null ? null : c.getUser().getId(),
                c.getUser() == null ? null : c.getUser().getName(),
                c.getCourse() == null ? null : c.getCourse().getId(),
                c.getCourse() == null ? null : c.getCourse().getTitle(),
                c.getIssuedOn(), c.getFileUrl(), c.getCreatedAt());
    }
}
