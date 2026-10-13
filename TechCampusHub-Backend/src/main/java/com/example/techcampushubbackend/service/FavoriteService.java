package com.example.techcampushubbackend.service;

import com.example.techcampushubbackend.dto.EngagementDtos.FavoriteRequest;
import com.example.techcampushubbackend.dto.EngagementDtos.FavoriteResponse;
import com.example.techcampushubbackend.entity.Favorite;
import com.example.techcampushubbackend.entity.User;
import com.example.techcampushubbackend.exception.BadRequestException;
import com.example.techcampushubbackend.exception.ResourceNotFoundException;
import com.example.techcampushubbackend.repository.CertificateRepository;
import com.example.techcampushubbackend.repository.FavoriteRepository;
import com.example.techcampushubbackend.repository.NoteRepository;
import com.example.techcampushubbackend.repository.PyqRepository;
import com.example.techcampushubbackend.repository.TimetableRepository;
import com.example.techcampushubbackend.repository.TnpNoteRepository;
import com.example.techcampushubbackend.repository.CourseRepository;
import com.example.techcampushubbackend.utils.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Per-user favorites. Items are validated against their target table so a
 * favorite can never point at a deleted resource.
 */
@Service
@RequiredArgsConstructor
public class FavoriteService {

    private final FavoriteRepository favoriteRepository;
    private final CourseRepository courseRepository;
    private final NoteRepository noteRepository;
    private final PyqRepository pyqRepository;
    private final TnpNoteRepository tnpNoteRepository;
    private final TimetableRepository timetableRepository;
    private final CertificateRepository certificateRepository;
    private final CurrentUserService currentUserService;

    @Transactional(readOnly = true)
    public List<FavoriteResponse> listMine(String itemType) {
        User user = currentUserService.requireUser();
        List<Favorite> favorites = (itemType == null || itemType.isBlank())
                ? favoriteRepository.findByUserIdOrderByCreatedAtDesc(user.getId())
                : favoriteRepository.findByUserIdAndItemTypeOrderByCreatedAtDesc(
                        user.getId(), parseType(itemType));
        return favorites.stream().map(this::toResponse).toList();
    }

    @Transactional
    public FavoriteResponse add(FavoriteRequest request) {
        User user = currentUserService.requireUser();
        Favorite.ItemType type = parseType(request.itemType());
        validateItemExists(type, request.itemId());

        var existing = favoriteRepository
                .findByUserIdAndItemTypeAndItemId(user.getId(), type, request.itemId());
        if (existing.isPresent()) {
            return toResponse(existing.get()); // idempotent
        }
        Favorite favorite = Favorite.builder()
                .user(user)
                .itemType(type)
                .itemId(request.itemId())
                .build();
        return toResponse(favoriteRepository.save(favorite));
    }

    @Transactional
    public void remove(String itemType, Long itemId) {
        User user = currentUserService.requireUser();
        Favorite.ItemType type = parseType(itemType);
        favoriteRepository.deleteByUserIdAndItemTypeAndItemId(user.getId(), type, itemId);
    }

    private void validateItemExists(Favorite.ItemType type, Long itemId) {
        boolean exists = switch (type) {
            case COURSE -> courseRepository.existsById(itemId);
            case NOTE -> noteRepository.existsById(itemId);
            case PYQ -> pyqRepository.existsById(itemId);
            case TNP_NOTE -> tnpNoteRepository.existsById(itemId);
            case TIMETABLE -> timetableRepository.existsById(itemId);
            case CERTIFICATE -> certificateRepository.existsById(itemId);
        };
        if (!exists) {
            throw new ResourceNotFoundException(type + " item", itemId);
        }
    }

    /** Resolves a display title for list responses. */
    private String resolveTitle(Favorite.ItemType type, Long itemId) {
        return switch (type) {
            case COURSE -> courseRepository.findById(itemId).map(c -> c.getTitle()).orElse(null);
            case NOTE -> noteRepository.findById(itemId).map(n -> n.getTitle()).orElse(null);
            case PYQ -> pyqRepository.findById(itemId).map(p -> p.getTitle()).orElse(null);
            case TNP_NOTE -> tnpNoteRepository.findById(itemId).map(n -> n.getTitle()).orElse(null);
            case TIMETABLE -> timetableRepository.findById(itemId).map(t -> t.getTitle()).orElse(null);
            case CERTIFICATE -> certificateRepository.findById(itemId).map(c -> c.getTitle()).orElse(null);
        };
    }

    private FavoriteResponse toResponse(Favorite favorite) {
        return new FavoriteResponse(
                favorite.getId(),
                favorite.getItemType().name(),
                favorite.getItemId(),
                resolveTitle(favorite.getItemType(), favorite.getItemId()),
                favorite.getCreatedAt());
    }

    private static Favorite.ItemType parseType(String raw) {
        try {
            return Favorite.ItemType.valueOf(raw.trim().toUpperCase().replace('-', '_'));
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Invalid itemType: " + raw
                    + " (expected COURSE, NOTE, PYQ, TNP_NOTE, TIMETABLE or CERTIFICATE)");
        }
    }
}
