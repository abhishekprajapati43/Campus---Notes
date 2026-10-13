package com.example.techcampushubbackend.service;

import com.example.techcampushubbackend.dto.ContentDtos.CertificateLinkRequest;
import com.example.techcampushubbackend.dto.ContentDtos.CertificateLinkResponse;
import com.example.techcampushubbackend.entity.CertificateLink;
import com.example.techcampushubbackend.entity.Role;
import com.example.techcampushubbackend.entity.User;
import com.example.techcampushubbackend.exception.BadRequestException;
import com.example.techcampushubbackend.exception.ResourceNotFoundException;
import com.example.techcampushubbackend.repository.CertificateLinkRepository;
import com.example.techcampushubbackend.utils.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Certificate links: admin-published external links (Drive, portals, ...)
 * shown on certificates.html. Students only ever see active links; admins
 * also see (and manage) hidden ones.
 */
@Service
@RequiredArgsConstructor
public class CertificateLinkService {

    private final CertificateLinkRepository certificateLinkRepository;
    private final CurrentUserService currentUserService;

    /** Active links for students; everything for admins. */
    @Transactional(readOnly = true)
    public List<CertificateLinkResponse> listVisible() {
        User user = currentUserService.requireUser();
        List<CertificateLink> links = user.getRole() == Role.ADMIN
                ? certificateLinkRepository.findAllByOrderBySortOrderAscCreatedAtAsc()
                : certificateLinkRepository.findByActiveTrueOrderBySortOrderAscCreatedAtAsc();
        return links.stream().map(CertificateLinkService::toResponse).toList();
    }

    @Transactional
    public CertificateLinkResponse create(CertificateLinkRequest request) {
        String title = requireText("title", request.title(), 200);
        String url = requireText("url", request.url(), 600);

        CertificateLink link = CertificateLink.builder()
                .title(title)
                .url(url)
                .description(trimTo(request.description(), 600))
                .sortOrder(request.sortOrder() == null ? 0 : request.sortOrder())
                .active(request.isActive() == null || request.isActive())
                .build();
        return toResponse(certificateLinkRepository.save(link));
    }

    /** Partial update: only the fields the admin actually edited are applied. */
    @Transactional
    public CertificateLinkResponse update(Long id, CertificateLinkRequest request) {
        CertificateLink link = require(id);

        if (request.title() != null) {
            link.setTitle(requireText("title", request.title(), 200));
        }
        if (request.url() != null) {
            link.setUrl(requireText("url", request.url(), 600));
        }
        if (request.description() != null) {
            link.setDescription(trimTo(request.description(), 600));
        }
        if (request.sortOrder() != null) {
            link.setSortOrder(request.sortOrder());
        }
        if (request.isActive() != null) {
            link.setActive(request.isActive());
        }
        return toResponse(certificateLinkRepository.save(link));
    }

    @Transactional
    public void delete(Long id) {
        certificateLinkRepository.delete(require(id));
    }

    private CertificateLink require(Long id) {
        return certificateLinkRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Certificate link", id));
    }

    private static String requireText(String field, String value, int max) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty()) {
            throw new BadRequestException(field + " is required");
        }
        return trimmed.length() > max ? trimmed.substring(0, max) : trimmed;
    }

    private static String trimTo(String value, int max) {
        String trimmed = value == null ? "" : value.trim();
        return trimmed.length() > max ? trimmed.substring(0, max) : trimmed;
    }

    public static CertificateLinkResponse toResponse(CertificateLink link) {
        return new CertificateLinkResponse(
                link.getId(),
                link.getTitle(),
                link.getUrl(),
                link.getDescription(),
                link.getSortOrder(),
                link.isActive(),
                link.getCreatedAt());
    }
}
